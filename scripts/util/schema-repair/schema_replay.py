# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with this
# work for additional information regarding copyright ownership. The ASF
# licenses this file to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance with the License.
# You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 .
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations.
"""Inspect the installed migration SQL and apply verifiable repairs."""
import argparse
import collections
import datetime
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile

from mysql_client import Mysql, SqlError, qi, qt, services_stopped, backup
from schema_ddl import Schema, GuardError, DDL_PROCS, table_name, index_spec
from schema_insert import guarded_insert
from sql_source import SourceError, parse, discover, call, canonical, split_items

HERE=Path(__file__).resolve().parent
VM_RENAME_ASSIGNMENT = canonical(
    "SET @sql := (SELECT IF(COUNT(*)>0,'RENAME TABLE `cloud`.`user_vm_details` TO `cloud`.`vm_instance_details`',"
    "'SELECT 1') FROM information_schema.TABLES WHERE TABLE_SCHEMA='cloud' AND TABLE_NAME='user_vm_details')"
)


def sql_directory(explicit):
    if explicit:return explicit.expanduser().resolve(strict=True)
    installed=HERE/'sql'
    if installed.is_dir():return installed
    for parent in HERE.parents:
        source=parent/'engine/schema/src/main/resources/META-INF/db'
        if source.is_dir():return source
    raise SourceError('SQL directory not found; use --sql-dir /path/to/current/sql')


def raw_kind(sql):
    if re.match(r'(?:CREATE|DROP)\s+PROCEDURE\b',sql,re.I):return 'ROUTINE'
    if re.match(r'(?:CREATE|DROP)\s+TEMPORARY\s+TABLE\b',sql,re.I):return 'TEMP'
    data=call(sql)
    if data:return 'DDL' if data[1] in DDL_PROCS else 'CALL'
    word=sql.split(None,1)[0].upper()
    if word in ('SET','PREPARE','DEALLOCATE','USE'):return 'SESSION'
    if word in ('UPDATE','DELETE','INSERT'):return word
    if word in ('CREATE','ALTER','DROP','RENAME'):return 'DDL'
    if word=='EXECUTE':return 'EXECUTE'
    if re.fullmatch(r'SELECT\s+1',sql,re.I):return 'SESSION'
    raise SourceError('Unsupported statement type (not executed): '+word)


def proc_name(sql):
    h=re.search(r'PROCEDURE\s+(?:IF EXISTS\s+)?(?:(`?\w+`?)\.)?`?(\w+)`?',sql,re.I)
    if not h:return None
    return (h[1] or 'cloud').strip('`')+'.'+h[2].upper()


def validate_data_helper(name,body):
    begin=re.search(r'\bBEGIN\b',body,re.I)
    if not begin:raise SourceError('Procedure body missing: '+name)
    tokens=canonical(body[begin.end():])
    # An implicit commit inside CALL would detach the DML from its journal write.
    if re.search(r'(?:^|\x1f)(?:ALTER|CREATE|DROP|TRUNCATE|COMMIT|ROLLBACK|RENAME|PREPARE|EXECUTE|CALL|GRANT|REVOKE|LOCK|UNLOCK|AUTOCOMMIT|START)(?:\x1f|$)',tokens,re.I):
        raise SourceError('Data helper contains DDL, transaction control or an indirect call: '+name)


def read_sources(directory,minimum):
    files=discover(directory,minimum);sources={};all_statements=[];helpers={};definitions={};bodies={};variables={};prepared={};temps={};assignments={}
    procdir=directory/'procedures'
    if procdir.is_dir():
        for p in sorted(procdir.glob('*.sql')):
            for s in parse(p.read_bytes().decode('utf-8'),p.name):
                if re.match(r'CREATE\s+PROCEDURE\b',s.sql,re.I):helpers[proc_name(s.sql)]=s.sql
    for p in files:
        text=p.read_bytes().decode('utf-8');sources[p.name]=text
        for s in parse(text,p.name):
            kind=raw_kind(s.sql);context=[]
            if s.repair:
                s.repair = repair_rule(s, kind)
            if kind=='ROUTINE' and re.match(r'CREATE',s.sql,re.I):
                definitions[proc_name(s.sql)]=s.digest;bodies[proc_name(s.sql)]=s.sql
            if kind=='SESSION':
                if re.match(r'SET\s+@',s.sql,re.I):
                    h=re.match(r'SET\s+(@\w+)\s*(?::=|=)',s.sql,re.I)
                    if not h:raise SourceError('Unsupported session assignment: '+s.sql[:100])
                    dependencies=sorted({variables[v.lower()] for v in re.findall(r'@\w+',s.sql[h.end():]) if v.lower() in variables})
                    assignments[h[1].lower()]=s.sql
                    variables[h[1].lower()]=hashlib.sha256((s.digest+'|'+','.join(dependencies)).encode()).hexdigest() if dependencies else s.digest
                elif re.match(r'PREPARE',s.sql,re.I):
                    h=re.fullmatch(r'PREPARE\s+(\w+)\s+FROM\s+(@\w+)',s.sql,re.I)
                    if not h:raise SourceError('PREPARE requires a session variable')
                    prepared[h[1].lower()]=(h[2].lower(),variables.get(h[2].lower(),''))
                elif not re.match(r'(?:DEALLOCATE PREPARE|USE|SELECT 1)\b',s.sql,re.I):raise SourceError('Only local @variable assignments are supported; no global/session overrides')
                if re.match(r'USE\b',s.sql,re.I) and not re.fullmatch(r'USE\s+`?cloud`?',s.sql,re.I):raise SourceError('Default schema must stay cloud; qualify cloud_usage table names explicitly')
            if kind=='EXECUTE':
                h=re.fullmatch(r'EXECUTE\s+(\w+)',s.sql,re.I)
                if not h or h[1].lower() not in prepared:raise SourceError('EXECUTE has no corresponding PREPARE')
                variable,fingerprint=prepared[h[1].lower()];context.append('prepared:'+variable+':'+fingerprint)
                s.prepared_assignment=assignments.get(variable,'')
            data=call(s.sql)
            if data and kind=='CALL':
                key=data[0]+'.'+data[1]
                if key not in definitions and key not in helpers:
                    raise SourceError('Missing procedure definition: '+key)
                body=helpers.get(key,'')
                validate_data_helper(key,bodies.get(key,body))
                context.append('procedure:'+key+':'+definitions.get(key,hashlib.sha256(canonical(body).encode()).hexdigest()))
            for variable in re.findall(r'@\w+',s.sql):
                if variable.lower() in variables:context.append('variable:'+variable.lower()+':'+variables[variable.lower()])
            temp=re.match(r'CREATE TEMPORARY TABLE(?: IF NOT EXISTS)?\s+([^\s(]+)',s.sql,re.I)
            if temp:temps[table_name(temp[1])]=[s.digest]
            dependencies=[]
            for tempname,hashes in temps.items():
                if re.search(r'\b'+re.escape(tempname.split('.')[1])+r'\b',s.sql,re.I):
                    context.append('temporary:'+tempname+':'+','.join(hashes));dependencies.append(tempname)
            target=re.match(r'INSERT\s+(?:IGNORE\s+)?INTO\s+([^\s(]+)',s.sql,re.I)
            s.temporary_insert=bool(target and table_name(target[1]) in temps)
            if s.temporary_insert:temps[table_name(target[1])].append(s.digest)
            s.context='|'.join(sorted(set(context)));s.kind=kind;all_statements.append(s)
            drop=re.match(r'DROP TEMPORARY TABLE(?: IF EXISTS)?\s+([^\s]+)',s.sql,re.I)
            if drop:temps.pop(table_name(drop[1]),None)
    return sources,all_statements,helpers


def rewrite_tables(sql,schema):
    # Preserve string literals; resolve only identifier tokens for historical table names.
    parts=re.split(r"('(?:\\.|''|[^'\\])*'|\"(?:\\.|\"\"|[^\"\\])*\")",sql)
    for old,new in schema.aliases.items():
        if schema.resolve(old)==old:continue
        old_schema,old_name=old.split('.');new_schema,new_name=new.split('.')
        for i in range(0,len(parts),2):
            parts[i]=re.sub(r'(?<![\w])`?'+re.escape(old_schema)+r'`?\s*\.\s*`?'+re.escape(old_name)+r'`?(?![\w])',qt(new),parts[i],flags=re.I)
            parts[i]=re.sub(r'(?<![\w])`?'+re.escape(old_name)+r'`?(?![\w])',qi(new_name),parts[i],flags=re.I)
    return ''.join(parts)


def repair_rule(statement, kind):
    location = f'{statement.file}:{statement.line}'
    directives = statement.repair
    targets = directives.get('target', [])
    target = re.fullmatch(r'(\S+)\s+([a-f0-9]{64})', targets[0]) if len(targets) == 1 else None
    if not target or kind not in ('UPDATE', 'DELETE') or 'probe' not in directives:
        raise SourceError(f'{location}: repair requires a target, source digest and probe for an UPDATE or DELETE')
    table = table_name(target[1])
    rule = {'table': table, 'apply': statement.sql}
    for key in ('probe', 'review'):
        if key in directives:
            sql = parse('\n'.join(directives[key]) + ';', location)
            if len(sql) != 1 or not re.match(r'^SELECT\s+EXISTS\s*\(', sql[0].sql, re.I):
                raise SourceError(f'{location}: repair {key} must be a single SELECT EXISTS query')
            rule[key] = sql[0].sql
    if 'apply' in directives:
        sql = parse('\n'.join(directives['apply']) + ';', location)
        if len(sql) != 1 or raw_kind(sql[0].sql) != kind:
            raise SourceError(f'{location}: repair apply statement must match the original SQL type')
        rule['apply'] = sql[0].sql
    elif kind == 'DELETE':
        raise SourceError(f'{location}: a DELETE requires an explicit guarded repair apply statement')
    # A source edit must not silently inherit an approval for the previous SQL.
    return rule if target[2] == statement.digest else {}


class Repair:
    """Plan from actual metadata/rows. Delay backup and mutation until a verified repair."""

    def __init__(self, args, work, db, statements, policies):
        self.args = args
        self.work = work
        self.db = db
        self.statements = statements
        self.schema = Schema(db, statements)
        self.policies = policies
        self.count = collections.Counter()
        self.backed_up = False
        self.removed_columns = set()
        self.removed_tables = set()
        self.removed_indexes = set()
        self.log = (work / 'actions.jsonl').open('w', encoding='utf-8')
        self.executed = (work / 'executed.sql').open('w', encoding='utf-8')
        for statement in statements:
            data = call(statement.sql)
            if data and data[1] == 'IDEMPOTENT_DROP_COLUMN':
                self.removed_columns.add((table_name(data[2][0], data[0]), data[2][1].lower()))
            if data and data[1] in ('IDEMPOTENT_DROP_INDEX', 'IDEMPOTENT_DROP_UNIQUE_KEY'):
                schema, name, args = data
                table, index = args[::-1] if name == 'IDEMPOTENT_DROP_INDEX' else args
                self.removed_indexes.add((table_name(table, schema), index.lower()))
            drop_column = re.fullmatch(r'ALTER TABLE\s+(\S+)\s+DROP COLUMN\s+`?(\w+)`?', statement.sql, re.I)
            if not drop_column and statement.kind == 'EXECUTE':
                drop_column = re.search(r"['\"]ALTER TABLE\s+([^\s'\"]+)\s+DROP COLUMN\s+`?(\w+)`?['\"]",
                                        statement.prepared_assignment, re.I)
            if drop_column:
                self.removed_columns.add((table_name(drop_column[1]), drop_column[2].lower()))
            drop_table = re.fullmatch(r'DROP TABLE(?: IF EXISTS)?\s+(\S+)', statement.sql, re.I)
            if drop_table:
                self.removed_tables.add(table_name(drop_table[1]))
            drop_index = re.fullmatch(r'ALTER TABLE\s+(\S+)\s+DROP (?:INDEX|KEY)\s+`?(\w+)`?', statement.sql, re.I)
            if drop_index:
                self.removed_indexes.add((table_name(drop_index[1]), drop_index[2].lower()))

    def event(self, status, statement, detail=''):
        self.count[status] += 1
        self.log.write(json.dumps(dict(status=status, file=statement.file, line=statement.line,
            kind=statement.kind, statement_id=statement.identity, detail=detail), ensure_ascii=False) + '\n')
        self.log.flush()
        if status in ('MISSING', 'FAILED'):
            print(f'[{status}] {statement.file}:{statement.line} {detail}', flush=True)

    def before_write(self):
        if self.backed_up:
            return
        services_stopped()
        backup(self.args, self.work)
        self.backed_up = True

    def apply(self, statement, sql, data_change=False, verify=None):
        if not self.args.apply:
            self.event('MISSING', statement, ' '.join(statement.sql.split())[:140])
            return
        self.before_write()
        self.executed.write(f'-- {statement.file}:{statement.line}\n{sql};\n')
        self.executed.flush()
        if data_change:
            self.db.query('START TRANSACTION')
        try:
            self.db.query(sql)
            if verify and self.db.scalar(verify) == '1':
                raise RuntimeError('Rows still match the repair condition after execution')
            if data_change:
                self.db.query('COMMIT')
        except BaseException:
            if data_change and self.db.p.poll() is None:
                try:
                    self.db.query('ROLLBACK')
                except Exception:
                    pass
            raise
        self.event('APPLIED', statement)

    def rename(self, old, new):
        has_old = self.schema.exists(old)
        has_new = self.schema.exists(new)
        if has_old and has_new:
            raise GuardError(f'Both {old} and {new} exist')
        if not has_old:
            if not has_new:
                raise GuardError(f'Neither {old} nor {new} exists')
            return None
        return f'RENAME TABLE {qt(old)} TO {qt(new)}'

    def add_column(self, table, column, definition):
        table = self.schema.resolve(table)
        columns = self.schema.columns(table)
        if column.lower() in columns:
            return None
        if (table, column.lower()) in self.removed_columns:
            raise GuardError(f'{table}.{column} is also dropped by a migration')
        new_column = self.schema.column_aliases.get((table, column))
        if new_column and new_column.lower() in columns:
            return None
        if not columns:
            raise GuardError(f'Missing prerequisite table {table}')
        if len(split_items(definition)) != 1:
            raise GuardError('Multiple ALTER clauses need a reviewed repair')
        tokens = canonical(definition).upper().split('\x1f')
        not_null = any(tokens[i:i + 2] == ['NOT', 'NULL'] for i in range(len(tokens) - 1))
        if not_null and 'DEFAULT' not in tokens and self.db.scalar(f'SELECT EXISTS (SELECT 1 FROM {qt(table)})') == '1':
            raise GuardError(f'{table}.{column} needs a reviewed backfill before adding NOT NULL')
        return f'ALTER TABLE {qt(table)} ADD COLUMN {qi(column)} {definition}'

    def add_index(self, table, name, definition):
        table = self.schema.resolve(table)
        if not self.schema.exists(table):
            raise GuardError(f'Missing prerequisite table {table}')
        actual = self.schema.indexes(table).get(name)
        if actual:
            if self.schema.actual_index(actual) != index_spec(definition):
                raise GuardError(f'Existing index {table}.{name} has different columns or uniqueness')
            return None
        if (table, name.lower()) in self.removed_indexes:
            raise GuardError(f'{table}.{name} is also dropped by a migration')
        return f'ALTER TABLE {qt(table)} ADD {definition}'

    def add_foreign_key(self, table, name, columns, reference):
        table = self.schema.resolve(table)
        parent = table_name(reference.split('(', 1)[0], table.split('.')[0])
        if not self.schema.exists(table) or not self.schema.exists(parent):
            raise GuardError(f'Missing prerequisite for foreign key {table}.{name}')
        definition = f'CONSTRAINT {qi(name)} FOREIGN KEY {columns} REFERENCES {reference} ON DELETE CASCADE'
        # Ask the existing FK guard to compare the full constraint, without replacing it.
        return self.schema.plan_fk_addition(table, name, definition)

    def additive_ddl(self, sql):
        data = call(sql)
        if data:
            schema, name, args = data
            if name in ('IDEMPOTENT_ADD_COLUMN', 'ADD_RUNTIME_CATALOG_COLUMN'):
                return self.add_column(table_name(args[0], schema), args[1], args[2])
            if name == 'IDEMPOTENT_ADD_KEY':
                return self.add_index(table_name(args[1], schema), args[0], f'KEY {qi(args[0])} {args[2]}')
            if name in ('IDEMPOTENT_ADD_UNIQUE_KEY', 'IDEMPOTENT_ADD_UNIQUE_INDEX'):
                return self.add_index(table_name(args[0], schema), args[1], f'UNIQUE KEY {qi(args[1])} {args[2]}')
            if name == 'IDEMPOTENT_ADD_FOREIGN_KEY':
                return self.add_foreign_key(table_name(args[0], schema), *args[1:])
            raise GuardError(f'{name} needs a reviewed repair')

        create = re.match(r'CREATE TABLE(?: IF NOT EXISTS)?\s+([^\s(]+)\s*\(', sql, re.I)
        if create:
            table = table_name(create[1])
            if table in self.removed_tables:
                raise GuardError(f'{table} is also dropped by a migration')
            if self.schema.exists(self.schema.resolve(table)):
                return None
            if 'SELECT' in canonical(sql).upper().split('\x1f'):
                raise GuardError('CREATE TABLE with a data query needs a reviewed repair')
            engine = re.search(r'\bENGINE\s*=\s*(\w+)', sql, re.I)
            if engine and engine[1].lower() != 'innodb':
                raise GuardError('New tables must use InnoDB')
            return sql

        rename = re.fullmatch(r'RENAME TABLE\s+(\S+)\s+TO\s+(\S+)', sql, re.I)
        if rename:
            return self.rename(table_name(rename[1]), table_name(rename[2]))

        alter = re.fullmatch(r'ALTER TABLE\s+(\S+)\s+(ADD\s+.+)', sql, re.I | re.S)
        if alter and len(split_items(alter[2])) == 1:
            table = table_name(alter[1])
            index = re.fullmatch(r'ADD\s+((?:UNIQUE\s+)?(?:INDEX|KEY)\s+`?(\w+)`?\s*\(.*\))', alter[2], re.I | re.S)
            if index:
                return self.add_index(table, index[2], index[1])
            col = re.fullmatch(r'ADD\s+(?:COLUMN\s+)?`?(\w+)`?\s+(.+)', alter[2], re.I | re.S)
            if col and col[1].upper() not in ('INDEX', 'KEY', 'CONSTRAINT', 'UNIQUE', 'PRIMARY', 'FOREIGN', 'CHECK', 'PARTITION'):
                return self.add_column(table, col[1], col[2])
        raise GuardError('Destructive, replacement, or unsupported DDL needs a reviewed repair')

    def prepared_ddl(self, statement):
        # This historical statement is a metadata test around a single table rename.
        source = statement.prepared_assignment
        if statement.file == 'schema-42040to42100.sql' and VM_RENAME_ASSIGNMENT == canonical(source):
            return self.rename('cloud.user_vm_details', 'cloud.vm_instance_details')
        raise GuardError('Dynamic SQL needs a reviewed repair')

    def inspect(self, statement):
        if statement.kind in ('DDL', 'EXECUTE'):
            sql = self.prepared_ddl(statement) if statement.kind == 'EXECUTE' else self.additive_ddl(statement.sql)
            if sql:
                self.apply(statement, sql)
            else:
                self.event('ALREADY_PRESENT', statement)
            return

        if statement.kind == 'INSERT' and not statement.temporary_insert:
            sql = rewrite_tables(statement.sql, self.schema)
            tokens = canonical(sql).upper().split('\x1f')
            if '@' in tokens:
                raise GuardError('INSERT depends on a session value')
            target, probe, write = guarded_insert(sql, self.schema, self.policies)
        else:
            rule = statement.repair
            if not rule:
                raise GuardError('No verified missing-data condition for this historical operation')
            target, probe, write = rule['table'], rule['probe'], rule['apply']
        if not self.has_candidates(probe):
            if statement.kind == 'DELETE' and rule.get('review') and self.has_candidates(rule['review']):
                raise GuardError('Rows remain whose replacement value could not be verified')
            self.event('ALREADY_PRESENT', statement)
            return
        self.require_transactional_target(target)
        self.apply(statement, write, data_change=True, verify=probe)
        if self.args.apply and statement.kind == 'DELETE' and rule.get('review') and self.has_candidates(rule['review']):
            self.event('REVIEW_REQUIRED', statement, 'Remaining rows do not have a verified replacement value')

    def has_candidates(self, probe):
        try:
            result = self.db.scalar(probe)
        except SqlError as exc:
            if re.match(r'^(1054|1146):', str(exc)):
                raise GuardError('Data repair prerequisite missing: ' + str(exc)) from exc
            raise
        if result not in ('0', '1'):
            raise GuardError('Probe did not return a boolean result')
        return result == '1'

    def require_transactional_target(self, table):
        # Triggers may write to other tables, so ensure both schemas can roll back.
        count = self.db.scalar("SELECT COUNT(*) FROM information_schema.TABLES "
            "WHERE TABLE_SCHEMA IN ('cloud','cloud_usage') AND TABLE_TYPE='BASE TABLE' AND ENGINE <> 'InnoDB'")
        if count != '0':
            raise GuardError(f'Data repairs require all cloud and cloud_usage tables to use InnoDB ({table})')

    def execute(self):
        if not self.args.apply:
            self.db.query('SET SESSION TRANSACTION READ ONLY')
        for statement in self.statements:
            if statement.kind in ('SESSION', 'ROUTINE', 'TEMP') or statement.temporary_insert:
                continue
            try:
                self.inspect(statement)
            except (GuardError, SourceError) as exc:
                self.event('REVIEW_REQUIRED', statement, str(exc))
            except Exception as exc:
                self.event('FAILED', statement, str(exc))
                raise
        if self.count['REVIEW_REQUIRED']:
            print(f"{self.count['REVIEW_REQUIRED']} statement(s) need review. See actions.jsonl.", flush=True)

    def finish(self, error=None):
        summary = dict(mode='APPLY' if self.args.apply else 'PLAN', sql_directory=str(self.args.sql_dir),
            counts=dict(self.count), error=error, mysql_version=self.db.version,
            backup=str(self.work / 'backup.sql.gz') if self.backed_up else None,
            finished_at=datetime.datetime.now().astimezone().isoformat())
        (self.work / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
        self.log.close()
        self.executed.close()
        print('\nResults:', self.work, '\nCounts:', dict(self.count), flush=True)
        return 1 if error else 2 if self.count['MISSING'] else 0


def main():
    parser=argparse.ArgumentParser(prog='mold-schema-repair.sh',description='Inspect current migration SQL and apply verified missing changes. Default: read-only plan.')
    mode=parser.add_mutually_exclusive_group();mode.add_argument('--apply',action='store_true');mode.add_argument('--plan',action='store_true');mode.add_argument('--list',action='store_true')
    parser.add_argument('--sql-dir',type=Path);parser.add_argument('--minimum-version',type=int,default=41810)
    parser.add_argument('--insert-keys',type=Path,default=HERE/'insert-keys.json')
    parser.add_argument('--defaults-extra-file',dest='config',type=Path)
    parser.add_argument('--host',default='localhost');parser.add_argument('--port',type=int,default=3306);parser.add_argument('--user',default='root');parser.add_argument('--socket')
    parser.add_argument('--mysql',default='mysql');parser.add_argument('--mysqldump',default='mysqldump');parser.add_argument('--timeout',type=int,default=600)
    parser.add_argument('--work-dir',type=Path)
    args=parser.parse_args();os.umask(0o077);args.sql_dir=sql_directory(args.sql_dir)
    sources,statements,helpers=read_sources(args.sql_dir,args.minimum_version)
    if args.list:
        for name,text in sources.items():print(name,hashlib.sha256(text.encode()).hexdigest(),sum(s.file==name for s in statements))
        print('Files:',len(sources),'Statements:',len(statements));return 0
    if not shutil.which(args.mysql):raise RuntimeError('mysql CLI not found')
    policies=json.loads(args.insert_keys.read_text(encoding='utf-8'))
    if args.work_dir:
        work=args.work_dir.expanduser().absolute();work.mkdir(mode=0o700,parents=True,exist_ok=False)
    else:work=Path(tempfile.mkdtemp(prefix='mold-schema-replay-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'-',dir='/root' if os.geteuid()==0 and Path('/root').is_dir() else os.getcwd()))
    print('Current SQL directory:',args.sql_dir,'\nResults:',work,flush=True)
    (work/'sources').mkdir()
    for name,text in sources.items():(work/'sources'/name).write_text(text,encoding='utf-8')
    (work/'sources/procedures').mkdir()
    for name,body in helpers.items():(work/'sources/procedures'/(name+'.sql')).write_text('DELIMITER $$\n'+body+'$$\nDELIMITER ;\n',encoding='utf-8')
    (work/'source-manifest.json').write_text(json.dumps(dict(sql_directory=str(args.sql_dir),sources_sha256={n:hashlib.sha256(t.encode()).hexdigest() for n,t in sources.items()},helper_definitions_sha256={n:hashlib.sha256(t.encode()).hexdigest() for n,t in helpers.items()}),indent=2)+'\n')
    shutil.copy2(args.insert_keys,work/'insert-keys.json')
    db=None;replay=None
    with tempfile.TemporaryDirectory(prefix='mold-replay-auth-') as auth:
        if args.config:args.config=args.config.expanduser().resolve(strict=True)
        else:
            password=getpass.getpass('MySQL '+args.user+' password: ')
            escaped=lambda value:'"'+str(value).replace('\\','\\\\').replace('"','\\"').replace('\n','\\n').replace('\r','\\r')+'"'
            config=Path(auth)/'client.cnf';config.write_text('[client]\nuser='+escaped(args.user)+'\npassword='+escaped(password)+'\nhost='+escaped(args.host)+'\nport='+str(args.port)+'\n'+('socket='+escaped(args.socket)+'\n' if args.socket else ''));config.chmod(0o600);args.config=config;del password
        try:
            db=Mysql(args,work)
            if db.scalar("SELECT GET_LOCK('mold_schema_replay_v1',0)")!='1':raise RuntimeError('Another replay is running')
            if db.scalar("SELECT GET_LOCK('mold_missing_schema_repair_v1',0)")!='1':raise RuntimeError('The older schema repair tool is running')
            if args.apply and db.scalar("SELECT GET_LOCK('DatabaseUpgrade',0)") != '1':
                raise RuntimeError('A Mold database upgrade is running')
            if db.scalar("SELECT EXISTS(SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='cloud_usage')")!='1':raise RuntimeError('Existing cloud and cloud_usage databases are required')
            replay=Repair(args,work,db,statements,policies)
            try:replay.execute()
            except BaseException as exc:
                replay.finish(str(exc));raise
            return replay.finish()
        finally:
            if db:db.close()


if __name__=='__main__':
    try:sys.exit(main())
    except KeyboardInterrupt:print('Interrupted. Review the journal and results before retry.',file=sys.stderr);sys.exit(130)
    except Exception as exc:print('ERROR: '+str(exc),file=sys.stderr);sys.exit(1)

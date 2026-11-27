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
"""Idempotency checks around DDL read from the current SQL source files."""
import hashlib
import re
from mysql_client import qi,qt,lit
from sql_source import call,split_items


class GuardError(RuntimeError):
    pass


def table_name(value,default='cloud'):
    parts=value.strip().replace('`','').split('.')
    if len(parts)==1:parts.insert(0,default)
    if len(parts)!=2 or parts[0] not in ('cloud','cloud_usage'):raise GuardError('Only cloud / cloud_usage tables are supported: '+value)
    for p in parts:qi(p)
    return '.'.join(parts)


def index_spec(definition):
    parts=split_items(definition[definition.index('(')+1:definition.rindex(')')]);columns=[]
    for part in parts:
        hit=re.fullmatch(r'`?(\w+)`?(?:\s*\(\s*(\d+)\s*\))?(?:\s+(ASC|DESC))?',part,re.I)
        if not hit:raise GuardError('Unsupported index expression: '+part)
        columns.append((hit[1].lower(),int(hit[2]) if hit[2] else None,'D' if (hit[3] or '').upper()=='DESC' else 'A'))
    return bool(re.match(r'(UNIQUE|PRIMARY)',definition,re.I)),columns


DDL_PROCS={
 'IDEMPOTENT_ADD_COLUMN','IDEMPOTENT_CHANGE_COLUMN','IDEMPOTENT_MODIFY_COLUMN_CHAR_SET',
 'ADD_RUNTIME_CATALOG_COLUMN',
 'IDEMPOTENT_ADD_KEY','IDEMPOTENT_ADD_UNIQUE_KEY','IDEMPOTENT_ADD_UNIQUE_INDEX',
 'IDEMPOTENT_DROP_COLUMN','IDEMPOTENT_DROP_INDEX','IDEMPOTENT_DROP_UNIQUE_KEY',
 'IDEMPOTENT_ADD_FOREIGN_KEY','IDEMPOTENT_DROP_FOREIGN_KEY',
}


class Schema:
    def __init__(self, db, statements):
        self.db = db
        self.aliases = {}
        self.column_aliases = {}
        for statement in statements:
            for old, new in re.findall(r'RENAME TABLE\s+([^\s]+)\s+TO\s+([^\s\'\"]+)', statement.sql, re.I):
                self.aliases[table_name(old)] = table_name(new)
            data = call(statement.sql)
            if data and data[1] == 'IDEMPOTENT_CHANGE_COLUMN':
                table, old, new, _ = data[2]
                if old != new:
                    self.column_aliases[(table_name(table, data[0]), old)] = new

    def exists(self,t):
        schema,name=t.split('.')
        return self.db.scalar('SELECT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA='+lit(schema)+' AND TABLE_NAME='+lit(name)+')')=='1'

    def resolve(self,t):
        return self.aliases[t] if t in self.aliases and not self.exists(t) and self.exists(self.aliases[t]) else t

    def columns(self,t):
        s,n=t.split('.')
        rows=self.db.objects('information_schema.COLUMNS',['COLUMN_NAME','COLUMN_TYPE','IS_NULLABLE','COLUMN_DEFAULT','EXTRA','CHARACTER_SET_NAME','COLLATION_NAME'], 'TABLE_SCHEMA='+lit(s)+' AND TABLE_NAME='+lit(n))
        return {r['COLUMN_NAME'].lower():r for r in rows}

    def indexes(self,t):
        s,n=t.split('.')
        rows=self.db.objects('information_schema.STATISTICS',['INDEX_NAME','NON_UNIQUE','SEQ_IN_INDEX','COLUMN_NAME','SUB_PART','COLLATION'], 'TABLE_SCHEMA='+lit(s)+' AND TABLE_NAME='+lit(n))
        result={}
        for r in sorted(rows,key=lambda x:x['SEQ_IN_INDEX']):result.setdefault(r['INDEX_NAME'],[]).append(r)
        return result

    def fk(self,t,name):
        s,n=t.split('.')
        rows=self.db.query("SELECT JSON_OBJECT('column',k.COLUMN_NAME,'ref_schema',k.REFERENCED_TABLE_SCHEMA,'ref_table',k.REFERENCED_TABLE_NAME,'ref_column',k.REFERENCED_COLUMN_NAME,'pos',k.ORDINAL_POSITION,'delete',r.DELETE_RULE,'update',r.UPDATE_RULE) FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.TABLE_NAME=k.TABLE_NAME AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.TABLE_SCHEMA="+lit(s)+' AND k.TABLE_NAME='+lit(n)+' AND k.CONSTRAINT_NAME='+lit(name)+' ORDER BY k.ORDINAL_POSITION')
        import json
        return [json.loads(x) for x in rows]

    @staticmethod
    def actual_index(rows):
        return (not bool(rows[0]['NON_UNIQUE']),[(r['COLUMN_NAME'].lower(),r['SUB_PART'],r['COLLATION']) for r in rows])

    def plan_fk_addition(self,t,name,definition):
        hit=re.search(r'FOREIGN KEY(?:\s+`\w+`)?\s*\(([^)]+)\)\s+REFERENCES\s+([^\s(]+)\s*\(([^)]+)\)',definition,re.I)
        if not hit:raise GuardError('Unsupported FK definition: '+definition)
        cols=[x.strip().strip('`').lower() for x in hit[1].split(',')]
        ref=table_name(hit[2],t.split('.')[0]);rcols=[x.strip().strip('`').lower() for x in hit[3].split(',')]
        def rule(word):
            h=re.search(r'ON '+word+r'\s+(CASCADE|RESTRICT|NO ACTION|SET NULL)',definition,re.I)
            value=h[1].upper() if h else 'RESTRICT';return 'RESTRICT' if value=='NO ACTION' else value
        wanted=(cols,ref,rcols,rule('DELETE'),rule('UPDATE'))
        def spec(rows):
            norm=lambda r:'RESTRICT' if r=='NO ACTION' else r
            return ([r['column'].lower() for r in rows],rows[0]['ref_schema']+'.'+rows[0]['ref_table'],[r['ref_column'].lower() for r in rows],norm(rows[0]['delete']),norm(rows[0]['update']))
        stage='mold_replay_fk_'+hashlib.sha256((t+name+definition).encode()).hexdigest()[:24]
        current=self.fk(t,name);staged=self.fk(t,stage)
        if staged or (current and spec(current) != wanted):
            raise GuardError('Foreign key needs a reviewed replacement: '+t+'.'+name)
        return None if current else 'ALTER TABLE '+qt(t)+' ADD '+definition

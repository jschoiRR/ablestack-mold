# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
"""Dedicated UD-03 fixtures. Credentials are read only from environment variables."""
import base64
import importlib.util
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent
PREFIX = 'UD1384-31-20261016'
spec = importlib.util.spec_from_file_location('ud01', ROOT.parent / 'userdata-1382-20261015' / 'verify_api.py')
ud = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ud)

def save(name, data):
    (ROOT / name).write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

def ok(api, command, **params):
    result = api.call(command, **params)
    assert 'errorcode' not in result, (command, result)
    return result


def all_rows(api, command, key):
    rows = []
    page = 1
    while True:
        result = ok(api, command, listall='true', page=page, pagesize=500)
        current = result.get(key, [])
        rows.extend(current)
        if len(rows) >= result.get('count', len(rows)) or not current:
            return rows
        page += 1

def inventory(api):
    original = ud.inventory(api)
    save('baseline.private.json', dict(userdata=original))
    user = ok(api, 'listUsers', username='admin')['user'][0]
    data = {
        'root_domain_id': user['domainid'],
        'zones': ok(api, 'listZones').get('zone', []),
        'templates': ok(api, 'listTemplates', templatefilter='executable', isready='true', page=1, pagesize=200).get('template', []),
        'offerings': ok(api, 'listServiceOfferings', page=1, pagesize=200).get('serviceoffering', []),
        'networks': ok(api, 'listNetworks', listall='true', page=1, pagesize=200).get('network', []),
        'autoscale': ok(api, 'listAutoScaleVmGroups', listall='true', page=1, pagesize=200).get('autoscalevmgroup', [])
    }
    save('inventory.private.json', data)
    print(json.dumps({
        'baseline_userdata_count': len(original),
        'templates': [{k:r.get(k) for k in ['id','name','isready','hypervisor','size','userdataid']} for r in data['templates']],
        'offerings': [{k:r.get(k) for k in ['id','name','cpunumber','memory','iscustomized']} for r in data['offerings']],
        'networks': [{k:r.get(k) for k in ['id','name','type','networkofferingid','account','domainid','supportsvmautoscaling']} for r in data['networks']],
        'autoscale': [{k:r.get(k) for k in ['id','name','state','account','domainid','vmprofileid']} for r in data['autoscale']]
    }, ensure_ascii=False))

def prepare(api):
    inv = json.loads((ROOT / 'inventory.private.json').read_text(encoding='utf-8'))
    state = json.loads((ROOT / 'runtime-state.json').read_text(encoding='utf-8')) if (ROOT / 'runtime-state.json').exists() else dict(prefix=PREFIX, userdata=[], accounts=[], root_domain_id=inv['root_domain_id'])
    assert state['prefix'] == PREFIX
    if not state.get('domain_id'):
        domain = ok(api, 'createDomain', name=PREFIX.lower())['domain']
        state.update(domain_id=domain['id'], domain_path=domain['path'])
        save('runtime-state.json', state)
    domain = dict(id=state['domain_id'])
    for suffix, accounttype in [('user',0),('domainadmin',2),('empty',0)]:
        name = PREFIX.lower()+'-'+suffix
        if any(row['role'] == suffix for row in state['accounts']):
            continue
        account = ok(api, 'createAccount', username=name, password=os.environ['MOLD_TEST_PASSWORD'], firstname='UD1384', lastname='QA', email=suffix+'@example.invalid', account=name, accounttype=accounttype, domainid=domain['id'])['account']
        state['accounts'].append(dict(id=account['id'], username=name, role=suffix))
        save('runtime-state.json', state)
    if not state.get('project_id'):
        matches = ok(api, 'listProjects', name=PREFIX+'-project', listall='true').get('project', [])
        project = matches[0] if matches else api.job('createProject', name=PREFIX+'-project', displaytext=PREFIX, account=state['accounts'][0]['username'], domainid=domain['id'])['project']
        state['project_id'] = project['id']
    save('runtime-state.json', state)
    targets = [('admin',99,dict(account='admin',domainid=inv['root_domain_id'])),
               ('user',11,dict(account=state['accounts'][0]['username'],domainid=domain['id'])),
               ('domainadmin',30,dict(account=state['accounts'][1]['username'],domainid=domain['id'])),
               ('project',11,dict(projectid=state['project_id']))]
    for suffix, count, scope in targets:
        for number in range(1,count+1):
            name = PREFIX+'-'+suffix+'-'+str(number).zfill(3)
            if any(row['name'] == name for row in state['userdata']):
                continue
            content = '#cloud-config\n# '+name+'\n# variable: VAR1\n'
            row = ok(api, 'registerUserData', name=name, userdata=base64.b64encode(content.encode()).decode(), params='VAR1', **scope)['userdata']
            state['userdata'].append(dict(id=row['id'], name=row['name'], scope=suffix))
            save('runtime-state.json', state)
        print(suffix, count, flush=True)
    role = ok(api, 'createRole', name=PREFIX+'-no-list-role', type='User', ispublic='false')['role']
    state['role_id'] = role['id']; save('runtime-state.json', state)
    for rule, permission in [('listUserData','deny'),('list*','allow'),('get*','allow'),('query*','allow'),('find*','allow'),('deployVirtualMachine','allow'),('logout','allow'),('*','deny')]:
        ok(api, 'createRolePermission', roleid=role['id'], rule=rule, permission=permission)
    name = PREFIX.lower()+'-denied'
    account = ok(api, 'createAccount', username=name, password=os.environ['MOLD_TEST_PASSWORD'], firstname='UD1384', lastname='QA', email='denied@example.invalid', account=name, domainid=domain['id'], roleid=role['id'])['account']
    state['accounts'].append(dict(id=account['id'], username=name, role='denied')); save('runtime-state.json', state)
    print('Prepared fixtures without creating a VM')

def verify(api):
    state = json.loads((ROOT / 'runtime-state.json').read_text(encoding='utf-8'))
    results = []
    def snapshot(label, client, scope, expected):
        rows = []
        pages = []
        for page in range(1, max(1, (expected+9)//10)+1):
            result = ok(client, 'listUserData', page=page, pagesize=10, **scope)
            current = result.get('userdata', [])
            assert result.get('count', 0) == expected
            rows.extend(current)
            pages.append(dict(page=page, total=result.get('count', 0), rows=[{k:r.get(k) for k in ['id','name','account','domainid','projectid','params']} for r in current]))
        assert len(rows) == len({r['id'] for r in rows}) == expected
        results.append(dict(scope=label, count=expected, pages=pages, all_pages_unique=True))
        return rows
    root = snapshot('admin', api, dict(account='admin', domainid=state['root_domain_id']), 100)
    result = ok(api, 'listUserData', page=1, pagesize=100, account='admin', domainid=state['root_domain_id'])
    assert {r['id'] for r in root} == {r['id'] for r in result['userdata']}
    for suffix, count in [('user',11),('domainadmin',30),('empty',0)]:
        account = next(a for a in state['accounts'] if a['role'] == suffix)
        client = ud.Mold(account['username'], domain='/'+PREFIX.lower()+'/')
        snapshot(suffix, client, dict(account=account['username'], domainid=state['domain_id']), count)
    snapshot('project', api, dict(projectid=state['project_id']), 11)
    denied = next(a for a in state['accounts'] if a['role'] == 'denied')
    result = ud.Mold(denied['username'], domain='/'+PREFIX.lower()+'/').call('listUserData')
    assert result.get('errorcode') == 432
    name = PREFIX+'-admin-001'
    result = ok(api, 'listUserData', account='admin', domainid=state['root_domain_id'], keyword=name, page=1, pagesize=10)
    assert result['count'] == 1 and result['userdata'][0]['name'] == name
    save('api-pages.json', dict(scopes=results, pagesize100_same_ids=True, denied_errorcode=432, exact_keyword_count=1))
    print('API owner/page/keyword/permission qualification PASS')

def verify_vm(api, suffix='vm'):
    state = json.loads((ROOT / 'runtime-state.json').read_text(encoding='utf-8'))
    if suffix == 'vm':
        receipt = json.loads((ROOT/'deploy-request.json').read_text(encoding='utf-8'))
        for attempt in range(30):
            job = ok(api,'queryAsyncJobResult',jobid=receipt['jobid'])
            if job.get('jobstatus') == 1:
                break
            assert job.get('jobstatus') != 2, job
            ud.time.sleep(1)
        assert job.get('jobstatus') == 1
        save('deploy-job-result.json',dict(jobid=receipt['jobid'],jobstatus=job['jobstatus'],vm_id=receipt['id']))
    rows = ok(api, 'listVirtualMachines', name=PREFIX+'-'+suffix, listall='true').get('virtualmachine', [])
    rows = [r for r in rows if r['name'] == PREFIX+'-'+suffix]
    assert len(rows) == 1, [(r['id'],r['name']) for r in rows]
    vm = rows[0]
    if suffix == 'vm': assert vm['id'] == receipt['id']
    assert any(nic.get('networkid') == 'ae274fff-3d8e-4285-ac73-26a767c1fea7' for nic in vm.get('nic',[]))
    state[suffix+'_vm_id'] = vm['id']; save('runtime-state.json', state)
    data = {k:vm.get(k) for k in ['id','name','displayname','state','account','domainid','templateid','userdataid','userdataname','userdataparams','userdatadetails','userdatapolicy']}
    assert data['state'] == 'Stopped' and data['userdataname'].startswith(PREFIX+'-admin-')
    expected_name = PREFIX+'-admin-'+('002' if suffix == 'reset' else '001')
    assert data['userdataname'] == expected_name, data
    assert data['userdataid'] == next(r['id'] for r in state['userdata'] if r['name'] == expected_name), data
    assert data['userdatadetails'] in ({'VAR1': 'UD1384 한글 값'}, '{VAR1=UD1384 한글 값}'), data
    label = data['userdataname'].rsplit('-',1)[1]
    save(suffix+'-connected-'+label+'.json', data)
    print(json.dumps(data,ensure_ascii=False))

def prepare_vm(api):
    state = json.loads((ROOT / 'runtime-state.json').read_text(encoding='utf-8'))
    inv = json.loads((ROOT / 'inventory.private.json').read_text(encoding='utf-8'))
    assert not state.get('reset_vm_id')
    template = next(r for r in inv['templates'] if r['name'] == 'rocky-9-4-minimal.qcow22')
    offering = next(r for r in inv['offerings'] if r['name'] == '2Core 2GB')
    network = next(r for r in inv['networks'] if r['name'] == 'E1335-VM-Origin-L2' and r['account'] == 'admin')
    assert any(s['name'] == 'UserData' and any(p['name'] == 'ConfigDrive' for p in s.get('provider',[])) for s in network['service'])
    userdata = next(r for r in state['userdata'] if r['name'] == PREFIX+'-admin-001')
    params = dict(zoneid=inv['zones'][0]['id'],templateid=template['id'],serviceofferingid=offering['id'],networkids=network['id'],name=PREFIX+'-reset',displayname=PREFIX+'-reset',startvm='false',userdataid=userdata['id'])
    params['userdatadetails[0].VAR1'] = 'UD1384 한글 값'
    vm = api.job('deployVirtualMachine', **params)['virtualmachine']
    state['reset_vm_id'] = vm['id']; save('runtime-state.json',state)
    save('reset-vm-fixture.json',{k:vm.get(k) for k in ['id','name','state','userdataid','userdataname','userdatadetails']})
    assert vm['state'] == 'Stopped'
    print('Dedicated stopped ConfigDrive VM prepared:',vm['id'])

def cleanup(api):
    state = json.loads((ROOT / 'runtime-state.json').read_text(encoding='utf-8'))
    vms = ok(api, 'listVirtualMachines', listall='true', keyword=PREFIX).get('virtualmachine', [])
    for vm in vms:
        assert vm['name'].startswith(PREFIX+'-')
        api.job('destroyVirtualMachine', id=vm['id'], expunge='true')
    assert not ok(api, 'listVirtualMachines', listall='true', keyword=PREFIX).get('virtualmachine', [])
    removed = []
    projects = [r for r in all_rows(api,'listProjects','project') if r['id'] == state['project_id']]
    for scope in ([{},dict(projectid=state['project_id'])] if projects else [{}]):
        for row in ud.inventory(api) if not scope else ok(api,'listUserData',**scope,page=1,pagesize=100).get('userdata',[]):
            if row['name'].startswith(PREFIX+'-'):
                ok(api,'deleteUserData',id=row['id']); removed.append(row['id'])
    if projects: api.job('deleteProject',id=state['project_id'])
    accounts = all_rows(api,'listAccounts','account')
    for account in state['accounts']:
        if any(r['id'] == account['id'] for r in accounts): api.job('deleteAccount',id=account['id'])
    if state.get('role_id') and any(r['id'] == state['role_id'] for r in ok(api,'listRoles').get('role',[])): ok(api,'deleteRole',id=state['role_id'])
    if any(r['id'] == state['domain_id'] for r in all_rows(api,'listDomains','domain')): api.job('deleteDomain',id=state['domain_id'])
    remaining = ud.inventory(api)
    original = json.loads((ROOT/'baseline.private.json').read_text(encoding='utf-8'))['userdata']
    assert remaining == original, 'Original library changed'
    checks = dict(userdata_test_residuals=sum(r['name'].startswith(PREFIX) for r in remaining),
                  vm_test_residuals=len(ok(api,'listVirtualMachines',listall='true',keyword=PREFIX).get('virtualmachine',[])),
                  account_test_residuals=sum(r['name'].startswith(PREFIX.lower()) for r in all_rows(api,'listAccounts','account')),
                  domain_test_residuals=sum(r['name'].startswith(PREFIX.lower()) for r in all_rows(api,'listDomains','domain')),
                  project_test_residuals=len(ok(api,'listProjects',name=PREFIX+'-project',listall='true').get('project',[])),
                  role_test_residuals=sum(r['name'].startswith(PREFIX) for r in ok(api,'listRoles').get('role',[])),
                  original_userdata_exact_match=True, original_userdata_count=len(remaining), deleted_userdata_this_pass=len(removed),
                  created_userdata=len(state['userdata']), deleted_during_validation=len(state.get('deleted_for_validation',[])))
    assert all(value == 0 for key,value in checks.items() if key.endswith('_residuals')), checks
    state['cleaned'] = True; save('runtime-state.json',state); save('cleanup-summary.json',checks)
    print(json.dumps(checks))

def drop_user_oldest(api, suffix='user-001'):
    state = json.loads((ROOT / 'runtime-state.json').read_text(encoding='utf-8'))
    assert suffix in ('user-001','admin-099')
    row = next(r for r in state['userdata'] if r['name'] == PREFIX+'-'+suffix)
    found = ok(api,'listUserData',id=row['id'],listall='true').get('userdata',[])
    assert len(found) == 1 and found[0]['name'] == row['name']
    ok(api,'deleteUserData',id=row['id'])
    state.setdefault('deleted_for_validation',[]).append(row['id']); save('runtime-state.json',state)
    print('Deleted only dedicated '+suffix+' for selection qualification')

if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    {'inventory': inventory, 'prepare': prepare, 'verify': verify, 'prepare-vm': prepare_vm, 'verify-vm': verify_vm, 'verify-reset': lambda api: verify_vm(api,'reset'), 'drop-user-oldest': drop_user_oldest, 'drop-selected-admin': lambda api: drop_user_oldest(api,'admin-099'), 'cleanup': cleanup}[sys.argv[1]](ud.Mold())

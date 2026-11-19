# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
"""Dedicated UD-02 fixtures; password is provided through the environment."""
import base64
import importlib.util
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('ud01', ROOT.parent / 'userdata-1382-20261015' / 'verify_api.py')
ud = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ud)
ud.ROOT = ROOT
ud.PREFIX = 'UD1383-31-20261015'

def cni_inventory(api):
    rows = []
    page = 1
    while True:
        response = api.call('listCniConfiguration', listall='true', page=page, pagesize=100)
        assert 'errorcode' not in response, response
        rows.extend(response.get('cniconfig', []))
        if len(rows) >= response.get('count', 0):
            return sorted(rows, key=lambda row: row['id'])
        assert response.get('cniconfig')
        page += 1

def baseline(api):
    ud.baseline(api)
    saved = json.loads((ROOT / 'baseline.private.json').read_text(encoding='utf-8'))
    saved['cni'] = cni_inventory(api)
    ud.save('baseline.private.json', saved)

def readonly(api):
    import os
    state = json.loads((ROOT / 'runtime-state.json').read_text(encoding='utf-8'))
    role = api.call('createRole', name=ud.PREFIX+'-read-role', type='User', ispublic='false')['role']
    state['readonly_role_id'] = role['id']
    ud.save('runtime-state.json', state)
    for rule in ['list*', 'get*', 'query*', 'find*', 'logout']:
        assert 'errorcode' not in api.call('createRolePermission', roleid=role['id'], rule=rule, permission='allow')
    assert 'errorcode' not in api.call('createRolePermission', roleid=role['id'], rule='*', permission='deny')
    name = ud.PREFIX.lower() + '-readonly'
    existing = next((row for row in state['accounts'] if row['role'] == 'readonly'), None)
    if existing:
        assert 'errorcode' not in api.call('updateAccount', id=existing['id'], roleid=role['id'])
    else:
        account = api.call('createAccount', username=name, password=os.environ['MOLD_TEST_PASSWORD'], firstname='UD1383', lastname='UI', email='ud1383@example.invalid', account=name, domainid=state['domain_id'], roleid=role['id'])['account']
        state['accounts'].append(dict(id=account['id'], username=name, role='readonly'))
    ud.save('runtime-state.json', state)
    client = ud.Mold(name, domain=state['domain_path'])
    denial = client.call('registerUserData', name=ud.PREFIX+'-denied', userdata=ud.content('UD1383 denied'))
    assert denial.get('errorcode') == 432, denial
    assert 'errorcode' not in client.call('listUserData')
    ud.save('permission-api.json', dict(role=role['name'], username=name, listUserDataAllowed=True, errorcode=denial['errorcode'], errortext=denial['errortext']))
    print('Read-only fixture prepared; direct registration denied')

def verify(api):
    state = json.loads((ROOT / 'runtime-state.json').read_text(encoding='utf-8'))
    rows = [row for row in ud.inventory(api) if row['name'].startswith(ud.PREFIX)]
    rows += api.call('listUserData', projectid=state['project_id']).get('userdata', [])
    rows += [row for row in cni_inventory(api) if row['name'].startswith(ud.PREFIX)]
    rows = list({row['id']: row for row in rows}.values())
    result = []
    expected = {
        'admin': ('# UD1383 관리자 등록\n', 'VAR1', 'admin'),
        'recovery': ('# UD1383 복구 등록\n', 'VAR2,VAR3', 'admin'),
        'dark-recovery': ('# UD1383 dark recovery', 'VAR2', 'admin'),
        'user': ('# UD1383 ordinary user UI', None, ud.PREFIX.lower()+'-user'),
        'domainadmin': ('# UD1383 domain admin UI', None, ud.PREFIX.lower()+'-domainadmin'),
        'project': ('# UD1383 project UI', None, None),
        'cni': ('{"cniVersion":"0.3.1","name":"UD1383-cni","type":"bridge"}', None, 'admin')
    }
    assert {row['name'] for row in rows} == {ud.PREFIX+'-'+suffix for suffix in expected}
    for row in rows:
        payload = base64.b64decode(row['userdata']).decode()
        suffix = row['name'][len(ud.PREFIX)+1:]
        text, params, account = expected[suffix]
        assert (payload, row.get('params'), row.get('account')) == (text, params, account), row['name']
        assert row.get('projectid') == (state['project_id'] if suffix == 'project' else None)
        if suffix in ('user', 'domainadmin'):
            assert row['domainid'] == state['domain_id']
        result.append(dict(id=row['id'], name=row['name'], account=row.get('account'), domain=row.get('domain'), projectid=row.get('projectid'), decoded=payload, params=row.get('params'), payload_exact_match=True, owner_verified=True))
    ud.save('ui-api-results.json', result)
    print(json.dumps(result, ensure_ascii=False))

def cleanup(api):
    state = json.loads((ROOT / 'runtime-state.json').read_text(encoding='utf-8'))
    for row in cni_inventory(api):
        if row['name'].startswith(ud.PREFIX):
            assert 'errorcode' not in api.call('deleteCniConfiguration', id=row['id'])
    ud.cleanup_ui(api)
    if state.get('readonly_role_id'):
        assert 'errorcode' not in api.call('deleteRole', id=state['readonly_role_id'])
    summary = json.loads((ROOT / 'cleanup-summary.json').read_text(encoding='utf-8'))
    remaining_cni = cni_inventory(api)
    summary['cni_test_residuals'] = sum(row['name'].startswith(ud.PREFIX) for row in remaining_cni)
    summary['original_cni_exact_match'] = remaining_cni == json.loads((ROOT / 'baseline.private.json').read_text(encoding='utf-8'))['cni']
    summary['original_cni_count'] = len(remaining_cni)
    summary['role_test_residuals'] = sum(row['name'].startswith(ud.PREFIX) for row in api.call('listRoles').get('role', []))
    assert summary['cni_test_residuals'] == 0
    assert summary['role_test_residuals'] == 0
    assert summary['original_cni_exact_match']
    ud.save('cleanup-summary.json', summary)

if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    api = ud.Mold()
    {'baseline': baseline, 'prepare': ud.prepare_ui, 'readonly': readonly, 'verify': verify, 'cleanup': cleanup}[sys.argv[1]](api)

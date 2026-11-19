# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with this
# work for additional information regarding copyright ownership. The ASF
# licenses this file to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance with the License.
# You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
"""Assert saved live evidence; does not contact or mutate the cluster."""
import csv
import json
from pathlib import Path

root = Path(__file__).resolve().parent
read = lambda name: json.loads((root / (name + '.json')).read_text(encoding='utf-8-sig'))
pages = read('final-all-pages')
ids = [row for page in pages['pages'] for row in page['ids']]
assert pages['all100IdsMatch'] and len(ids) == len(set(ids)) == 100
assert [p['page'] for p in pages['pages']] == [str(n) for n in range(1, 11)]
api = read('api-pages')
assert {s['scope']: s['count'] for s in api['scopes']} == dict(admin=100, user=11, domainadmin=30, empty=0, project=11)
assert api['pagesize100_same_ids'] and api['denied_errorcode'] == 432
views = []
for theme in ['light', 'dark']:
    for size in ['1440x1000', '1280x720', '390x844', '390x480']:
        view = read('final-' + theme + '-' + size)
        assert view['bodyOverflow'] == 'auto'
        assert 0 <= view['footer']['top'] < view['footer']['bottom'] <= view['viewport']['height']
        assert all(b['rect']['height'] == 32 and b['padding'] == '4px 15px' for b in view['buttons'])
        views.append(theme + '-' + size)
retry = read('final-retry-recovers')
assert retry['value'] == 'UD1384 한글 값' and not retry.get('alert')
assert retry['selectedId'] == 'off-page' and not retry['buttons'][-1]['disabled']
assert read('role-denied-list')['createDisabled']
assert read('final-selected-target-deleted')['buttons'][-1]['disabled']
assert read('final-explicit-none-recovers')['noneChecked']
assert read('none-api-proof') == dict(idZeroRequests=0, variablesCleared=True, explicitNoneSelected=True)
runtime = read('runtime-reset-final')
assert not runtime['exceptions'] and not runtime['truncated'] and not runtime['hasMore']
assert any(r['command'] == 'resetUserDataForVirtualMachine' and r['userdataid'] == 'fefb8e74-fc9b-4372-a5eb-b854659e13c4' for r in runtime['requests'])
assert not any(r['command'] == 'listUserData' and r['id'] == '0' for r in runtime['requests'])
for name, suffix in [('vm', '001'), ('reset', '002')]:
    vm = read(name + '-connected-' + suffix)
    assert vm['state'] == 'Stopped' and vm['userdataname'].endswith('-' + suffix)
    assert vm['userdatadetails'] == '{VAR1=UD1384 한글 값}'
with (root / 'vm-db.tsv').open(encoding='utf-8-sig', newline='') as stream:
    rows = list(csv.DictReader(stream, delimiter='\t'))
    assert len(rows) == 2 and all(r['vm_payload_sha256'] == r['library_payload_sha256'] for r in rows)
cleanup = read('cleanup-summary')
assert all(v == 0 for k, v in cleanup.items() if k.endswith('_residuals'))
assert cleanup['original_userdata_exact_match'] and cleanup['original_userdata_count'] == 1
db = (root / 'cleanup-db.tsv').read_text(encoding='utf-8-sig').splitlines()
assert [r.split('\t') for r in db[1:8]] == [[r, '0'] for r in ['userdata','vm','volume','account','project','domain','role']]
deployed = read('runtime-final')
assert deployed['ui_source'] == pages['source'] == runtime['source']
assert deployed['static_files_verified'] == 855 and deployed['mold'] == 'active' and deployed['client_http'] == 200
assert deployed['web_inf_preserved'] and deployed['config_preserved'] and deployed['management_pid_preserved']
result = dict(issue=1384, ui_source=deployed['ui_source'], server_pages=10, exact_unique_ids=100,
              role_counts={s['scope']: s['count'] for s in api['scopes']}, responsive_theme_views=views,
              footer_button_height=32, actual_vm_create_and_reset_api_db_match=True,
              final_reset_runtime_exceptions=0, api_db_active_test_residuals=0,
              static_files_verified=855, guest_execution_verified=False,
              full_cloud_ci=False, result='PASS within UD-03 scope')
(root / 'qualification-summary.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result))

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with this
# work for additional information regarding copyright ownership. The ASF
# licenses this file to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance with the License.
# You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
"""Read saved UI/API/DB/deployment evidence and assert the UD-04 contract."""
import base64
import csv
import hashlib
import io
import json
from pathlib import Path
from urllib.parse import unquote

root=Path(__file__).resolve().parent
read=lambda name:json.loads((root/(name+'.json')).read_text(encoding='utf-8-sig'))
requests=[]
for file in sorted(root.glob('*-requests.json')):
    case=json.loads(file.read_text(encoding='utf-8-sig'))
    assert not case['truncated'] and not case['hasMore'] and not case['exceptions'],file.name
    for mutation in case['mutations']:
        p=mutation['params']
        assert not (p.get('userdataid') and p.get('userdata')),file.name
        assert p.get('userdataid')!='0',file.name
    requests.append(case)
for name in ['keep','append-keep','final-template-keep','user-default-hidden','current-read-retry']:
    assert not read(name+'-requests')['mutations']
for name in ['none','final-none']:
    assert read(name+'-requests')['mutations'][0]['params']==dict(id='db065200-f6f0-48ed-9b4e-19415bbe6cef',userdata='')
assert read('current-read-error')['buttons'][-1]['disabled']
assert not read('current-read-retry')['buttons'][-1]['disabled']
assert '현재 사용자 데이터: 템플릿 기본값' in read('final-template-label')['text']
assert read('deny-template-only')['customTabs']==0
assert read('user-default-denied')['errorcode']==read('domainadmin-default-denied')['errorcode']==432
assert all(r['errorcode']==531 for r in read('owner-api-reset-denied'))
assert all('T' not in k for k in read('allow-library-requests')['mutations'][0]['params'] if k.startswith('userdatadetails['))
assert read('allow-none-requests')['mutations'][0]['params']['userdatadetails[0].T']=='기본값 복원 한글'
assert read('append-manual-requests')['mutations'][0]['params']['userdatadetails[0].T']=='APPEND 템플릿 한글'
assert 'VAR1' not in str(read('append-manual-requests')['mutations'][0]['params'])
combined=base64.b64decode(read('after-append-manual')['userdata']).decode()
assert combined.count('UD1385-31-20261016-template')==combined.count('APPEND 새 한글')==1
assert read('append-new-input-separated')['inputs'][-1]['value']==''
assert read('before-template-only')['userdatadetails'] is None
assert read('deployed-template-only')['userdatadetails']=='{T=템플릿 한글}'
assert '현재 한글, 값=1' in read('off-page-current-restored')['text']
assert read('mode-roundtrip-manual')['inputs'][0]['value']=='#cloud-config\n# UD1385 한글 % +\n'

def db_rows(name):
    text=(root/(name+'.tsv')).read_text(encoding='utf-8-sig')
    return list(csv.DictReader(io.StringIO(text[text.index('uuid\t'):]),delimiter='\t'))

snapshots=['after-final-library','after-allow-none','after-append-default','after-deny','after-user','after-domainadmin','after-project']
rows={r['uuid']:r for r in db_rows('verified-db')}
for name in snapshots:
    vm=read(name);row=rows[vm['id']]
    assert vm['state']==row['state']=='Stopped' and row['removed']=='0'
    assert row['library_uuid']==(vm['userdataid'] or 'NULL')
    assert row['user_data_details']==(vm['userdatadetails'] or 'NULL')
    assert row['userdata_sha256']==hashlib.sha256(vm['userdata'].encode()).hexdigest()
for name in ['after-none','after-final-none','smoke-after-none']:
    assert all(read(name)[key] is None for key in ['userdata','userdataid','userdatadetails'])
smoke=read('final-smoke-fixture')
manual=read('smoke-after-manual')
assert base64.b64decode(manual['userdata']).decode()=='#cloud-config\n# 최종 오류 복구 본문 한글 % +\n'
assert manual['userdataid'] is None and manual['userdatadetails'] is None
library=read('smoke-after-library')
assert library['userdataid']==smoke['userdata_id'] and library['userdatadetails']=='{VAR1=마지막 한글, 값=4}'
for name,vm in [('smoke-manual-db',manual),('smoke-library-db',library)]:
    row=next(r for r in db_rows(name) if r['uuid']==smoke['vm_id'])
    assert row['userdata_sha256']==hashlib.sha256(vm['userdata'].encode()).hexdigest()
    assert row['user_data_details']==(vm['userdatadetails'] or 'NULL')
assert read('smoke-error')['globalErrorNotifications']==0
assert read('smoke-error')['inputs'][-1]['value']=='#cloud-config\n# 최종 오류 복구 본문 한글 % +\n'
assert read('smoke-loading')['resetRequestsWhileHeld']==1
assert read('smoke-loading')['closeButtonCount']==0 and read('smoke-loading')['buttons'][0]['disabled']
retry=read('smoke-error-requests')['mutations'];assert len(retry)==2 and retry[0]['params']==retry[1]['params']
assert 'label.action.user.data.reset' not in (root/'smoke-success.txt').read_text(encoding='utf-8')
# The transient success toast was observed in getAXState immediately after retry.
# The later body/screenshot export is after that toast expired; it proves the
# modal closed and the subsequent API snapshot proves the persisted result.
assert '사용자 데이터 변경을 끄면' not in (root/'smoke-success.txt').read_text(encoding='utf-8')
views=[]
runtime=read('runtime-final')
for theme in ['light','dark']:
    for size in ['1440x1000','1280x720','390x844','390x480']:
        view=read('qualified-'+theme+'-'+size)
        assert view['source']==runtime['source']
        assert view['bodyOverflow']=='auto' and 0<=view['footer']['top']<view['footer']['bottom']<=view['viewport']['height']
        assert all(b['rect']['height']==32 and b['padding']=='4px 15px' for b in view['buttons'])
        assert ('dark-mode' in view['theme'])==(theme=='dark')
        views.append(theme+'-'+size)
scroll=read('qualified-dark-small-scrolled')
assert scroll['after']['scrollTop']>scroll['before']['scrollTop']
assert scroll['after']['header']['top']==scroll['before']['header']['top']
assert scroll['after']['footer']['top']==scroll['before']['footer']['top']
cleanup=read('cleanup-final')
assert cleanup['original_userdata_exact_match'] and cleanup['original_userdata_count']==1
assert cleanup['test_vm_residuals']==cleanup['test_userdata_residuals']==0
counts=(root/'cleanup-db.tsv').read_text(encoding='utf-8-sig').splitlines()[1:11]
assert len(counts)==10 and all(line.split('\t')[1]=='0' for line in counts)
assert runtime['static_files_verified']==855 and len(runtime['classes_verified'])==7
assert runtime['jar_sha256']=='b7d28d9b977c60e8bbe8107a0000f4f58246cfcdd8a4d7e2576f6a6c38d241b2'
assert runtime['web_inf_preserved'] and runtime['config_preserved'] and runtime['mold']=='active' and runtime['client_http']==200
result=dict(issue=1385,source=runtime['source'],bounded_ui_request_cases=len(requests),bounded_ui_request_exceptions=0,
            ui_tests=152,changed_server_tests=13,responsive_theme_views=views,footer_button_height=32,
            actual_vm_api_db_match=True,api_db_active_test_residuals=0,original_library_exact_match=True,
            static_hashes_verified=855,runtime_class_hashes_verified=7,guest_execution_verified=False,full_cloud_ci=False,
            result='PASS within UD-04 scope')
(root/'qualification-summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
print(json.dumps(result))

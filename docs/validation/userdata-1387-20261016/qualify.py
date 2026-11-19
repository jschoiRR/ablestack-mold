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
"""Evidence qualification for UD-06; no API mutations."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parent
read=lambda name:json.loads((ROOT/(name+'.json')).read_text(encoding='utf-8'))
source='1a9553261a7d45a3acf079f21d0ba7db360492f6'
expected={'cancel-qualified':0,'bulk-first-attempt':1,'bulk-retry-success':1,'mixed-failure':2,'light-used-block':1,'used-retry-success':1,'lookup-blocked':0,'lookup-retry-success':1,'permission-failed':1,'permission-retry-success':1,'lost-response-unknown':1,'lost-response-confirmed':0,'replacement-protected':0,'single-detail-delete':1,'user-bulk-success':2,'project-bulk-success':2,'domainadmin-bulk-success':2,'readonly-selected':0}
requests=[]
for name,count in expected.items():
 d=read(name+'-requests');assert not d['truncated'] and not d['hasMore'] and not d['exceptions'],name
 mutations=[r for r in d['requests'] if r['command']=='deleteUserData'];assert len(mutations)==count,(name,len(mutations))
 requests.extend(mutations)
assert len(requests)==17
prepared=read('prepared');known={v['id']:v for v in prepared['userdata'].values()}
for req in requests:
 p=req['params'];target=known[p['id']]
 if target.get('projectid'):assert p.get('projectid')==target['projectid'] and 'account' not in p and 'domainid' not in p
 else:assert p.get('account')==target['account'] and p.get('domainid')==target['domainid'] and 'projectid' not in p
for name,keys in [('user-bulk-success',['user-bulk-A','user-bulk-B']),('domainadmin-bulk-success',['domainadmin-bulk-A','domainadmin-bulk-B']),('project-bulk-success',['project-bulk-A','project-bulk-B'])]:
 ids={r['params']['id'] for r in read(name+'-requests')['requests'] if r['command']=='deleteUserData'}
 assert ids=={prepared['userdata'][k]['id'] for k in keys}
for name,states in [('bulk-success',['success','success']),('mixed-failure',['failed','success']),('used-retry-success',['success']),('lookup-blocked',['blocked']),('lookup-retry-success',['success']),('permission-failed',['failed']),('permission-retry-success',['success']),('lost-response-unknown',['unknown']),('lost-response-confirmed',['success']),('replacement-protected',['absent']),('single-success',['success']),('user-bulk-success',['success','success']),('domainadmin-bulk-success',['success','success']),('project-bulk-success',['success','success'])]:
 d=read(name);assert d['source']==source and sorted(d['dialog']['states'])==sorted(states),name
assert '현재 항목이 존재합니다' in (ROOT/'permission-failed.txt').read_text(encoding='utf-8')
assert read('lost-response-fault')['postResponseStatus']==200
replacement=read('replaced-before-submit');assert replacement['original_id']!=replacement['replacement_id']
assert replacement['replacement_id'] in (ROOT/'replacement-remains-list.txt').read_text(encoding='utf-8')
assert '1개 선택' in (ROOT/'mixed-retained-list.txt').read_text(encoding='utf-8')
assert 'root-mixed-free' not in (ROOT/'mixed-retained-list.txt').read_text(encoding='utf-8')
assert all(x['response']['errorcode']==531 for x in read('acl-denied'))
readonly=read('readonly-role');assert readonly['list_userdata_available'] and not readonly['delete_userdata_available'] and readonly['delete_response']['errorcode']==432
assert '사용자 데이터 제거' not in (ROOT/'readonly-selected.txt').read_text(encoding='utf-8')
views=sorted(ROOT.glob('qualified-*.json'));assert len(views)==8
for path in views:
 d=json.loads(path.read_text(encoding='utf-8'));assert d['source']==source
 assert ('dark-mode' in d['theme'])==('qualified-dark-' in path.name)
 assert d['dialog']['bodyOverflow']=='auto'
 for b in d['dialog']['buttons']:
  assert b['rect']['height']==32 and b['padding']=='4px 15px' and not b['disabled'],path.name
  assert 0<=b['rect']['left']<b['rect']['right']<=d['viewport']['width']
  assert b['rect']['bottom']<=d['viewport']['height']
 assert any('js/app.028b9b59.js' in s for s in d['scripts'])
for name in ['bulk-confirm','single-confirm','user-bulk-confirm','domainadmin-bulk-confirm','project-bulk-confirm']:
 for b in read(name)['dialog']['buttons']:assert b['rect']['height']==32 and b['padding']=='4px 15px',name
mobile=read('mobile-error-scrolled');initial=read('qualified-light-390x480');assert mobile['dialog']['bodyRect']==initial['dialog']['bodyRect'];assert [b['rect'] for b in mobile['dialog']['buttons']]==[b['rect'] for b in initial['dialog']['buttons']]
phase=read('after-functional-db');assert phase['active_counts']['userdata']==3 and phase['active_counts']['vm']==1
present={row[0] for row in phase['libraries'] if row[4]=='0'}
ui_ids={r['params']['id'] for r in requests};assert len(ui_ids)==14 and not (ui_ids & present)
for key in ['root-bulk-A','root-bulk-B','root-mixed-used','root-mixed-free','root-read-fail','root-denied','root-lost-response','root-single','user-bulk-A','user-bulk-B','domainadmin-bulk-A','domainadmin-bulk-B','project-bulk-A','project-bulk-B','root-deleted-before']:assert prepared['userdata'][key]['id'] not in present,key
assert replacement['replacement_id'] in present
assert phase['vms'][0][5]=='NULL' and read('unreference')['userdataid'] is None
cleanup=read('cleanup');assert cleanup['original_library_exact_match'] and cleanup['original_library_count']==1
assert all(v==0 for v in read('cleanup-db')['active_counts'].values())
ui=read('cleanup-ui');assert ui['user'] and 'dark-mode' in ui['theme'] and ui['dialogs']==0 and ui['userdataLinks']==[{'name':'AS-PROD-31-AS01-HTTP','id':'a28893ee-4662-45cd-8f30-79527368c809'}]
runtime=read('runtime-final');assert runtime['source']==source and runtime['static_files_verified']==858 and len(runtime['classes_verified'])==9 and runtime['management_pid']=='1236420' and runtime['management_pid_preserved'] and runtime['client_http']==200 and runtime['mold']=='active'
build=read('build-results');assert build['ui_unique_tests']==292 and build['production_build_pass'] and build['lint_pass']
summary=dict(issue=1387,source=source,result='PASS within UD-06 scope',ui_unique_tests=292,ui_suites=9,final_delete_recheck=29,bounded_request_cases=len(expected),bounded_javascript_exceptions=0,delete_requests=17,actual_unique_ui_deletions=len(ui_ids),owner_scope_payloads_match=True,actual_user_domainadmin_project_bulk_deletions=True,used_reference_blocked_then_retried=True,lookup_failure_blocks_delete=True,lost_response_requires_read_confirmation=True,same_name_replacement_protected=True,readonly_ui_hidden_and_api_denied=True,responsive_theme_views=8,button_height=32,static_hashes_verified=858,preserved_class_hashes=9,management_pid_preserved=True,active_test_residuals=0,original_library_exact_match=True,full_cloud_ci=False)
(ROOT/'qualification-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
print(json.dumps(summary,ensure_ascii=False,indent=2))

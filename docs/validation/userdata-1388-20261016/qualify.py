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
"""Disposable cluster-31 UI deletion fixtures; private state is never published."""
from pathlib import Path
import json,re,sys
sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parent
read=lambda name:json.loads((ROOT/(name+'.json')).read_text(encoding='utf-8'))
checks=[]
def check(name,condition):
 assert condition,name
 checks.append(name)
def rows(name):return read(name)['rows']
uuid=r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
api=read('functional-final-api');ids={v['id'] for v in api['listVirtualMachines']['virtualmachine']}
check('VM API count and page union',len(ids)==12 and len(rows('vms-page-1'))==10 and len(rows('vms-page-2'))==2 and {re.search(uuid,row).group() for row in rows('vms-page-1')+rows('vms-page-2')}==ids)
check('search 1 and 0',len(rows('vms-search-one'))==1 and len(rows('vms-search-zero'))==0 and '이 범위에 연결된 개체가 없습니다.' in read('vms-search-zero')['usageText'])
check('VM detail and browser back', '/vm/'+read('ui-create-api')['id'] in read('vm-link-detail')['url'] and 'vmskeyword=UD1388' in read('vm-link-back')['url'] and len(rows('vm-link-back'))==1)
check('dedicated tabs and template ISO policies',len(rows('linked-templates'))==2 and len(rows('linked-isos-A-one'))==1 and len(rows('linked-isos-B-one'))==1 and 'APPEND' in read('linked-isos-A-one')['usageText'])
check('template and ISO UI unlink updates',len(rows('template-unlinked-back-one'))==1 and len(rows('iso-unlinked-back-zero'))==0 and api['listTemplates']['count']==api['listIsos']['count']==2)
check('pending and failed refresh retain rows',len(rows('refresh-pending-retained'))==len(rows('refresh-error-retained'))==len(rows('refresh-error-retry'))==12 and '이전 조회 결과' in read('refresh-error-retained')['usageText'])
check('permission is not empty success',len(rows('references-forbidden'))==0 and '권한' in read('references-forbidden')['usageText'] and '이 범위에 연결된 개체가 없습니다.' not in read('references-forbidden')['usageText'] and len(rows('references-forbidden-retry'))==12)
check('empty refresh retention and late response discard',len(rows('empty-refresh-pending'))==0 and len(rows('late-empty-response-discarded'))==12)
check('user domainadmin project scope',all(len(rows(x))==1 for x in ['user-using-one','domainadmin-using-one','project-using-one']) and 'user-vm-01' in rows('user-using-one')[0] and 'project-vm-01' in rows('project-using-one')[0])
check('actual role API denial and unavailable state',read('restricted-role')['actual_denial']['errorcode']==432 and '확인할 수 없습니다' in read('role-reference-unavailable')['usageText'])
check('preflight reference counts',all(x in read('delete-references-light')['dialog']['text'] for x in ['사용 가상머신: 12','연결 템플릿: 2','연결 ISO: 2']))
check('reference lookup blocks deletion while pending',any(x['text']=='삭제' and x['disabled'] for x in read('delete-reference-pending')['buttons']))
check('incomplete lookup is not zero', '0개로 판단할 수 없습니다' in read('delete-reference-incomplete')['dialog']['text'])
check('actual deletion race rejected and refreshed',read('delete-race-blocked')['dialog']['states']==['failed'] and read('delete-race-new-reference')['api']['count']==1 and '사용 가상머신: 1' in read('delete-race-reference-refreshed')['dialog']['text'])
check('race resolution through UI VM unlink',len(rows('delete-race-related-vm'))==1 and len(rows('delete-race-vm-unlinked-zero'))==0 and read('delete-race-resolved-success')['dialog']['states']==['success'] and read('delete-race-api-after')['userdataid'] is None)
check('busy controls and final template guard',read('delete-busy-reference-blocked')['dialog']['states']==['deleting'] and all(x['disabled'] for x in read('delete-busy-reference-blocked')['buttons'] if x['text'] in ['취소','삭제']) and read('delete-template-guard')['dialog']['states']==['failed'])
for theme in ['light','dark']:
 for size in ['1440x1000','1280x720','390x844','390x480']:
  listing=read('using-vms-'+theme+'-'+size);dialog=read('delete-'+theme+'-'+size);geometry=dialog['dialog'];buttons=[b for b in dialog['buttons'] if b['text'] in ['취소','삭제']]
  check(theme+' '+size+' common layout',len(listing['rows'])==12 and listing['documentWidth']<=listing['width'] and len(buttons)==2 and all(b['height']==32 and b['padding']=='4px 15px' for b in buttons) and geometry['header']['top']>=0 and geometry['footer']['bottom']<=dialog['height'])
  if size=='390x480':check(theme+' fixed header/footer body scroll',geometry['body']['scrollHeight']>geometry['body']['clientHeight'])
check('theme styles inherit different common tokens',read('using-vms-light-1440x1000')['notes'][0]['color']!=read('using-vms-dark-1440x1000')['notes'][0]['color'])
requests=[];intervals=[];limited=[];observed=[]
for p in ROOT.glob('*-requests.json'):
 data=json.loads(p.read_text(encoding='utf-8'))
 if isinstance(data,dict):
  events=data['events']
  if data['truncated'] or data['hasMore']:limited.append(p.name)
  else:
   check(p.stem+' complete trace',True);intervals.append(p.name)
   if any(e['type']=='exception' for e in events):
    probe=read('dashboard-cancellation-isolation')
    check(p.stem+' common cancellation isolated and tracked',p.name=='user-project-acl-requests.json' and probe['usageComponents']==0 and not probe['truncated'] and any(frame['functionName']=='cancel' for e in probe['exceptions'] for frame in e['stack']['callFrames']))
    observed.append(p.name)
   else:check(p.stem+' no JS exceptions',True)
 else:events=data
 requests.extend(e for e in events if e.get('type')=='request')
check('server pagination actually requested',any(e['command']=='listVirtualMachines' and e['params'].get('page')=='2' and e['params'].get('pagesize')=='10' and e['params'].get('userdataid') for e in requests))
check('API matrix',len(read('api-results'))==11 and all(x['passed'] for x in read('api-results')))
for name in ['template-inheritance','iso-inheritance']:check(name+' has content but no UUID',bool(read(name)['userdata']) and read(name)['userdataid'] is None)
cleanup=read('cleanup');db=read('cleanup-db')
check('cleanup API and exact original library',cleanup['original_userdata_exact_match'] and cleanup['active_vms']==cleanup['active_networks']==0)
check('cleanup ten DB resource kinds',len(db['active_counts'])==10 and all(v==0 for v in db['active_counts'].values()))
runtime=read('runtime-final');check('runtime source assets classes webapp',runtime['source']=='f6396f8cf4360e899677f21b7be64381d09447b3' and runtime['static_files_verified']==861 and len(runtime['classes_verified'])==12 and runtime['mold']=='active' and runtime['client_http']==200 and runtime['web_inf_preserved'] and runtime['config_preserved'])
result=dict(issue=1388,cluster=31,passed=True,checks=checks,count=len(checks),ui_tests=44,server_tests=3,api_matrix=11,complete_browser_intervals=intervals,limited_browser_intervals=limited,observed_common_exception_intervals=observed,known_open_findings=[dict(issue=1392,summary='Common AutogenView route cancellation can reject without a handler; reproduced with zero usage components')],full_ui_standard_complete=False,scope='UD-07 reference tabs and deletion visibility; full Cloud CI and final whole-menu UI review are separate')
(ROOT/'qualification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n');print('PASS',len(checks),'evidence checks')

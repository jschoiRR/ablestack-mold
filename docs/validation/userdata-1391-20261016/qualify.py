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
"""Qualify deployed browser geometry, actual clone outcomes, provenance and cleanup."""
from pathlib import Path
import json,base64,hashlib,sys
ROOT=Path(__file__).resolve().parent
checks=[]
def load(name):return json.loads((ROOT/(name+'.json')).read_text(encoding='utf-8'))
def check(name,value,evidence=None):
 checks.append(dict(name=name,passed=bool(value),evidence=evidence));assert value,(name,evidence)
for mode in ['light','dark']:
 for w,h in [(1440,1000),(1280,720),(390,844),(390,480)]:
  name=f'clone-{mode}-{w}x{h}';v=load(name);m=v['modal']
  check(name+' centered',abs(m['x']+m['width']/2-w/2)<=1 and abs(m['y']+m['height']/2-h/2)<=1,m)
  check(name+' viewport bounds',v['pageWidth']<=w and m['x']>=0 and m['y']>=0 and m['x']+m['width']<=w and m['y']+m['height']<=h)
  # Closed native details retain layout boxes in Chromium; identify the footer actions by their unique labels and verify their bounds.
  buttons=[b for b in v['buttons'] if b['text'] in ['취소','새 버전 등록']]
  check(name+' VM footer buttons',len(buttons)==2 and [b['text'] for b in buttons]==['취소','새 버전 등록'] and all(b['rect']['height']==32 and b['rect']['y']>=v['footer']['y'] and b['rect']['y']+b['rect']['height']<=v['footer']['y']+v['footer']['height'] for b in buttons),buttons)
  check(name+' source field retained',next(x for x in v['inputs'] if x['id'].endswith('_userdata'))['value']==load('prepared')['userdata']['admin']['userdata'])
  check(name+' rendered source escaped',v['contentScripts']==0)
  t=load(f'versions-{mode}-{w}x{h}')
  check(name+' matching version tab',t['pageWidth']<=w and any(x['text']=='버전·변경 정책' and x['selected']=='true' for x in t['tabLabels']))
for name in ['scroll-before','scroll-after']:
 check(name+' snapshot',bool(load(name)['modal']))
a=load('scroll-before');b=load('scroll-after')
check('body alone scrolls',b['body']['scroll']>a['body']['scroll'])
for area in ['header','footer']:
 check(area+' fixed during body scroll',all(abs(a[area][key]-b[area][key])<=1 for key in ['x','y','width','height']))
v=load('references-after-clone');check('all existing references preserved',v['all_original_references_preserved'])
check('explicit VM rollback exact',load('switch-rollback')['original_vm_restored_exact'])
check('BOM contract and actual APPEND control',load('append-policy')['bom_preserved'] and load('append-policy')['bom_rejection_created_vms']==0 and bool(load('append-policy')['bom_free_created_vm']))
runtime=load('runtime-final')
check('qualified UI runtime',runtime['static_files_verified']==870 and runtime['mold']=='active' and runtime['client_http']==200 and runtime['web_inf_preserved'] and runtime['config_preserved'])
check('backend classes preserved',len(runtime['classes_verified'])==12)
api=load('api-initial');check('real same-body variable-only rejection',len(api['cases'])==7 and all(x['same_body_different_params_denial']['errorcode']==431 for x in api['cases']))
role=load('restricted-role');check('dynamic denied API and real 432',role['actual_denial']['errorcode']==432 and role['registration_still_available'] and role['add_annotation_absent'])
for name in ['clone-duplicate','clone-name-conflict','clone-lost-error','clone-note-failed','clone-note-recovered','source-deleted','role-user-created','role-domainadmin-created','role-project-created','role-denied','binary-created']:
 check(name+' UI evidence',(ROOT/(name+'.png')).exists() and bool(load(name)))
exports=load('exports')
check('copy and download preserve all source bytes',exports['clipboard']['exact'] and exports['download']['exact'] and exports['expected']['bytes']==12012)
clones=load('clones-final')['clones'];db=load('db-runtime')
check('six actual UI versions',len(clones)==6)
byid={row[0]:row for row in db['content_bytes']}
check('six API body hashes match DB stored bytes',all(item['id'] in byid and item['sha256']==byid[item['id']][2] and item['bytes']==int(byid[item['id']][1]) for item in clones))
check('all persisted source notes fit API limit',all(item['note_length']<=255 for item in clones))
check('DB has one source note per UI version',len(db['source_notes'])==6 and {row[1] for row in db['source_notes']}=={item['id'] for item in clones})
check('user variable declarations reused and extended',next(item for item in clones if item['case']=='user-v2')['params']=='SOURCE_VAR,NEW_VAR')
check('actual create remains single after lost response',load('lost-response-server')['count']==1)
check('failed source note does not repeat registration',load('note-failure-server')['version_count']==1 and load('note-failure-server')['notes_count']==0)
check('pending link remains in original dialog',load('busy-link-guard')['dialogs']==1 and load('prepared')['userdata']['admin']['id'] in load('busy-link-guard')['route'])
check('result link closes dialog',load('version-v3-detail')['modal'] is None and 'userdata.versions' in load('version-v3-detail')['url'])
check('reference link closes dialog and opens matching VM tab',load('source-usage-navigation')['modal'] is None and 'userdata.usage.vms' in load('source-usage-navigation')['url'])
for item in load('contrast')['measurements']:
 check(item['mode']+' idle text and placeholder contrast',item['body']>=4.5 and all(x['text']>=4.5 and x['placeholder']>=4.5 for x in item['inputs']) and all(x['text']>=4.5 for x in item['footer']))
 check(item['mode']+' measured input border/focus contrast',all(x['border']>=3 for x in item['inputs']))
network=[]
for p in ROOT.glob('network-*.json'):
 v=json.loads(p.read_text(encoding='utf-8'));complete=not v['truncated'] and not v['hasMore'];network.append(dict(name=p.stem,complete=complete,exceptions=[e for e in v['events'] if e['method']=='Runtime.exceptionThrown']))
check('bounded network intervals complete',network and all(x['complete'] for x in network))
if '--before-cleanup' not in sys.argv:
 check('no active DB resources or notes',all(n==0 for n in load('db-after')['active_counts'].values()),load('db-after')['active_counts'])
 check('operating libraries exact match',load('cleanup')['original_userdata_exact_match'])
 check('no active API VM/network',load('cleanup')['active_vms']==load('cleanup')['active_networks']==0)
result=dict(issue=1391,cluster=31,checks=checks,total=len(checks),passed=all(x['passed'] for x in checks),network=network)
name='qualification-before-cleanup' if '--before-cleanup' in sys.argv else 'qualification'
with (ROOT/(name+'.json')).open('w',encoding='utf-8',newline='\n') as out:out.write(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(len(checks),'qualification checks passed; network intervals:',len(network))

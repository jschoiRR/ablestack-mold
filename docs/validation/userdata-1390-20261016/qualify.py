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
"""Verify final deployment, browser byte equality, scoped faults and cleanup."""
from pathlib import Path
import base64,hashlib,json,re,sys
ROOT=Path(__file__).resolve().parent
load=lambda name:json.loads((ROOT/name).read_text(encoding='utf-8-sig'))
checks=[]
def save(name,value):
 with (ROOT/name).open('w',encoding='utf-8',newline='\n') as stream:
  json.dump(value,stream,ensure_ascii=False,indent=2);stream.write('\n')
def check(name,condition,**details):
 checks.append(dict(check=name,passed=bool(condition),**details));assert condition,(name,details)
def sha(value):return hashlib.sha256(value).hexdigest()
def contrast(a,b):
 def lum(text):
  rgb=[float(x)/255 for x in re.findall(r'[0-9.]+',text)[:3]]
  return sum(v*c for v,c in zip([x/12.92 if x<=0.04045 else ((x+0.055)/1.055)**2.4 for x in rgb],[.2126,.7152,.0722]))
 a,b=sorted([lum(a),lum(b)]);return (b+.05)/(a+.05)
runtime=load('runtime-final.json');deployment=load('deployment.json');matrix=load('api-matrix.json');db=load('db-before.json')
check('qualified static files and unchanged backend',runtime['static_files_verified']==867 and len(runtime['classes_verified'])==12 and runtime['jar_sha256']==load('backend-baseline.json')['jar_sha256'])
check('webapp/config/process and HTTP preserved',runtime['web_inf_preserved'] and runtime['config_preserved'] and runtime['management_pid']==deployment['before']['management_pid'] and runtime['mold']=='active' and runtime['client_http']==200)
raw=next(v for v in matrix['cases'] if v['case']=='admin');expected=base64.b64decode(raw['stored']['userdata']);byid={row[0]:row for row in db['vm_body_hashes']}
for case in matrix['cases']:
 row=byid[case['vm']['id']];check('API DB body '+case['case'],(0 if row[1]=='NULL' else int(row[1]))==case['bytes'] and (row[2]==case['sha256'] or row[2]=='NULL' and case['bytes']==0))
for name in ['clipboard-raw.bin','download-raw.bin']:check(name+' exact bytes',(ROOT/name).read_bytes()==expected,sha256=sha((ROOT/name).read_bytes()))
for name in ['clipboard-base64.bin','download-base64.bin']:check(name+' canonical Base64',(ROOT/name).read_bytes()==raw['stored']['userdata'].encode('ascii'))
for theme in ['light','dark']:
 for w,h in [(1440,1000),(1280,720),(390,844),(390,480)]:
  name=f'{theme}-{w}x{h}';v=load(name+'.json');parts={p['class']:p for p in v['dialog']};modal=parts['ant-modal-content']['rect'];body=parts['ant-modal-body']['rect']
  check(name+' viewport and center',v['viewport']==dict(w=w,h=h) and abs(modal['x']+modal['w']/2-w/2)<=1 and abs(modal['y']+modal['h']/2-h/2)<=1)
  check(name+' bounded modal and body scrolling',modal['x']>=0 and modal['y']>=0 and modal['x']+modal['w']<=w+1 and modal['y']+modal['h']<=h+1 and body['scrollWidth']<=body['clientWidth']+1 and parts['ant-modal-body']['style']['overflow']=='auto')
  check(name+' 32px buttons',all(abs(b['rect']['h']-32)<=1 for b in v['buttons'] if b['aria']!='Close'))
  check(name+' exact stored text',v['code'].encode('utf-8')==expected)
  check(name+' final bundle',all(any(script.endswith('/'+key) for script in v['scripts']) for key in runtime['app_files']))
  code=parts['content-code']['style'];ratio=contrast(code['color'],code['background']);check(name+' code contrast',ratio>=4.5,ratio=ratio)
 for cls in ['ant-modal-header','ant-modal-footer']:
  a=next(p['rect'] for p in load(theme+'-390x480.json')['dialog'] if p['class']==cls);b=next(p['rect'] for p in load(theme+'-mobile-scrolled.json')['dialog'] if p['class']==cls)
  check(theme+' fixed '+cls,all(abs(a[k]-b[k])<=1 for k in ['x','y','w','h']))
for key in ['append','empty','inline','binary']:
 v=load('case-'+key+'.json');c=next(c for c in matrix['cases'] if c['case']==key)
 expected_body=c['stored'].get('userdata','');shown=v.get('code','')
 check('body case '+key,shown==expected_body if key=='binary' else shown.encode('utf-8')==base64.b64decode(expected_body))
 check('comparison case '+key,any({'append':'다릅니다','empty':'직접 연결된','inline':'직접 연결된','binary':'바이트 단위로 같습니다'}[key] in text for text in v['alerts']))
check('VM variable values correctly labeled','변수 값 (VM 메타데이터)' in load('case-append.json')['summary'])
for key in ['user','domainadmin','project']:
 v=load('role-'+key+'.json');c=next(c for c in matrix['cases'] if c['case']==key);check('role '+key,v['code'].encode('utf-8')==base64.b64decode(c['stored']['userdata']))
v=load('role-source-denied.json');check('source denial preserves independent VM access',bool(v.get('code')) and any('비교할 수 없습니다' in t for t in v['alerts']))
check('API denied action hidden','VM 저장 사용자 데이터 조회' not in (ROOT/'role-get-denied.txt').read_text(encoding='utf-8'))
for name in ['error-503-light','error-503-dark']:
 v=load(name+'.json');check(name+' stale body exports disabled',v.get('code') is not None and all(b['disabled'] for b in v['buttons'] if b['text'] in ['클립보드에 복사','다운로드']) and any('이전 조회 본문' in t for t in v['alerts']))
for name in ['error-403-dark','invalid-body-light','actual-vm-changed','actual-vm-deleted']:
 v=load(name+'.json');check(name+' clears body',v.get('code') is None and all(b['disabled'] for b in v['buttons'] if b['text'] in ['클립보드에 복사','다운로드']))
check('late response stays in new VM','인라인' in load('late-new-vm.json').get('code',''))
check('escape closes and restores action focus',load('keyboard-close-focus.json')['dialogs']==0 and '작업' in load('keyboard-close-focus.json')['focus'])
intervals=[];exceptions=[]
for p in sorted(ROOT.glob('network-*.json')):
 v=load(p.name);check(p.name+' complete event interval',not v['truncated']);intervals.append(dict(file=p.name,exceptions=v['exceptions']))
 for e in v['events']:
  if e['method']=='Runtime.exceptionThrown':
   d=e['details'];props={p['name']:p.get('value') for p in d.get('exception',{}).get('preview',{}).get('properties',[])};frames=d.get('stackTrace',{}).get('callFrames',[])
   check(p.name+' known route cancellation',props.get('code')=='ERR_CANCELED' and any(f['functionName']=='beforeRouteLeave' for f in frames));exceptions.append(dict(file=p.name,code=props['code'],frames=frames))
check('shared library body regression',load('library-body-regression-data.json')['code'].encode('utf-8')==expected and load('library-body-regression-data.json')['vmSummary']==0)
check('keyboard remains inside dialog',load('keyboard-trap.json')['insideDialog'])
focus=load('focus-code-detail.json');check('code keyboard focus visible',focus['tag']=='PRE' and focus['outlineWidth']=='2px' and focus['outlineStyle']=='solid' and contrast(focus['outlineColor'],focus['background'])>=3)
if '--pre-cleanup' in sys.argv:
 save('qualification-before-cleanup.json',dict(passed=True,complete=False,check_count=len(checks),checks=checks));print('PASS',len(checks),'checks before cleanup');sys.exit(0)
restored=load('browser-restored.json');check('browser admin default project dark restored',restored['admin'] and restored['defaultProject'] and 'dark' in restored['theme'])
cleanup=load('cleanup.json');after=load('db-after.json');check('original library exact and zero test resources',cleanup['original_userdata_exact_match'] and all(n==0 for n in after['active_counts'].values()))
report=dict(passed=True,check_count=len(checks),checks=checks,network_intervals=intervals,known_route_cancellations=exceptions,full_cloud_build=False)
save('qualification.json',report);print('PASS',len(checks),'checks;',len(intervals),'complete intervals;',len(exceptions),'known route cancellations')

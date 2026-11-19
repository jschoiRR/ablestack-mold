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
import json,hashlib,re,subprocess
ROOT=Path(__file__).resolve().parent
load=lambda name:json.loads((ROOT/(name+'.json')).read_text(encoding='utf-8'))
checks=[]
def check(name,condition,**details):
 assert condition,(name,details)
 checks.append(dict(name=name,passed=True,**details))
runtime=load('runtime-final');prior=json.loads((ROOT.parent/'userdata-1388-20261016/runtime-final.json').read_text(encoding='utf-8'))
check('runtime-static-backend-preserved',runtime['issue']==1389 and runtime['cluster']==31 and runtime['static_files_verified']>=864 and runtime['classes_verified']==prior['classes_verified'] and runtime['jar_sha256']==prior['jar_sha256'] and runtime['management_pid']==prior['management_pid'] and runtime['mold']=='active' and runtime['client_http']==200)
check('focused-tests-lint-build','77 passed, 77 total' in (ROOT/'unit-final.txt').read_text(encoding='utf-8') and '3 passed, 3 total' in (ROOT/'unit-final.txt').read_text(encoding='utf-8') and 'No lint errors found' in (ROOT/'lint-final.txt').read_text(encoding='utf-8') and 'Build complete' in (ROOT/'build-final.txt').read_text(encoding='utf-8'))
check('API-contract',load('api-matrix')['passed']==14)
export=load('content-exports')
for name,item in export['checks'].items():check('export-'+name,item['equal'] and item['actual_sha256']==item['expected_sha256'],bytes=item['bytes'])
for name in export['checks']:
 path=ROOT/(name+'.bin');relative=path.relative_to(ROOT.parents[2]).as_posix()
 check('git-bytes-'+name,subprocess.check_output(['git','show',':'+relative],cwd=ROOT.parents[2])==path.read_bytes())
raw=(ROOT/'raw-download.bin').read_bytes()
for theme in ['light','dark']:
 for width,height in [(1440,1000),(1280,720),(390,844),(390,480)]:
  suffix=f'{theme}-{width}x{height}'
  body=load('body-'+suffix);table=load('list-'+suffix);meta=load('metadata-'+suffix);refs=load('vms-'+suffix);error=load('error-'+suffix)
  for kind,value in [('body',body),('list',table),('metadata',meta),('vms',refs),('error',error)]:
   check(kind+'-bounds-'+suffix,value['docWidth']<=width and value['width']==width and value['height']==height and ('dark-mode' in value['bodyClass'])==(theme=='dark'))
  expected_app=next(iter(runtime['app_files']))
  check('bundle-'+suffix,all(any(url.endswith('/'+expected_app) for url in value['scripts']) for value in [body,table,meta,refs]))
  check('tab-order-'+suffix,[x['text'] for x in body['tabs']]==['상세','사용자 데이터 본문','사용 가상머신','연결 템플릿','연결 ISO','코멘트'])
  check('body-bytes-'+suffix,len(body['code'])==1 and body['code'][0].encode('utf-8')==raw)
  buttons=[x for x in body['buttons'] if x.get('classes','').find('content-refresh')>=0 or '클립보드에 복사' in x['text'] or x['text']=='다운로드']
  check('body-buttons-'+suffix,len(buttons)==3 and all(x['height']==32 for x in buttons))
  refresh=next(x for x in buttons if 'content-refresh' in x.get('classes',''))
  check('refresh-icon-'+suffix,refresh['width']==32 and refresh['padding']=='0px' and any(x['width']>0 and x['height']>0 for x in refresh.get('svg',[])))
  check('tabs-body-'+suffix,[x['text'] for x in body['tabs'] if x['selected']=='true']==['사용자 데이터 본문'] and 'tab=userdata.content' in body['url'])
  check('vms-UUID-'+suffix,len(refs['rows'])==1 and 'f7912d0f-d093-4677-9df4-ded9de010e85' in refs['rows'][0])
  check('list-page-'+suffix,len(table['rows'])==20 and any('현재 페이지' in x['text'] for x in table['content']))
  check('metadata-'+suffix,any('동적 파라미터 키' in x['text'] for x in meta['content']) and not meta['code'])
  check('stale-error-'+suffix,len(error['code'])==1 and any('이전 조회 본문' in x['text'] for x in error['content']) and any('클립보드에 복사' in x['text'] and x['disabled'] for x in error['buttons']))
for kind in ['empty','forbidden','missing','invalid','mismatch','binary']:
 value=load('final-state-'+kind)
 check('state-'+kind,not value['code'] if kind!='binary' else value['code']==['/wABAv4='])
for name in ['templates','isos']:
 value=load('final-tab-'+name);check('reference-'+name,len(value['rows'])==1)
for name,tab in [('back','연결 ISO'),('forward','코멘트'),('reload','코멘트')]:
 value=load('final-history-'+name);check('history-'+name,[x['text'] for x in value['tabs'] if x['selected']=='true']==[tab])
check('late-response',load('final-late-response')['passed'])
check('roles',load('final-roles')['passed'])
check('list-search-page-selection',all(load('final-list-contract').values()))
check('cleanup-API',load('cleanup')['original_userdata_exact_match'] and load('cleanup')['active_vms']==0 and load('cleanup')['active_networks']==0)
check('cleanup-DB',all(v==0 for v in load('db-after')['active_counts'].values()) and not load('db-after')['content_bytes'])
network=[]
for p in sorted(ROOT.glob('network-final-*.json')):
 data=json.loads(p.read_text(encoding='utf-8'));check('complete-'+p.stem,not data['truncated'] and data['cursor']>=data['start']);network.extend(data['events'])
exceptions=[e for e in network if e['method']=='Runtime.exceptionThrown']
for e in exceptions:
 detail=e['params']['exceptionDetails'];properties={p['name']:p.get('value') for p in detail['exception']['preview']['properties']};frames=detail['stackTrace']['callFrames']
 check('known-common-cancellation-'+str(detail['exceptionId']),properties.get('code')=='ERR_CANCELED' and {'cancel','beforeRouteLeave'}.issubset({f['functionName'] for f in frames}))
for name in ['final-registration-dark-desktop','final-registration-dark-mobile']:
 buttons=[v for v in load(name)['buttons'] if v['text'] in ['취소','등록']]
 check('dialog-footer-'+name,len(buttons)==2 and all(v['height']==32 and v['padding']=='4px 15px' for v in buttons))
check('actual-deleted-target',not load('final-actual-deleted-target')['code'] and load('actual-delete')['result']['success'])
check('base64-decoding',export['decodedBase64MatchesOriginal'])
check('browser-restored',load('browser-restoration')['viewportOverrideReset'] and load('browser-restoration')['originalLibraryRows']==1)

with (ROOT/'qualification.json').open('w',encoding='utf-8',newline='\n') as output:output.write(json.dumps(dict(issue=1389,source=runtime['source'],passed=len(checks),checks=checks,complete_network_intervals=len(list(ROOT.glob('network-final-*.json'))),runtime_exceptions=exceptions),ensure_ascii=False,indent=2)+'\n')
print('Qualified assertions:',len(checks),'complete intervals:',len(list(ROOT.glob('network-final-*.json'))),'exceptions:',len(exceptions))

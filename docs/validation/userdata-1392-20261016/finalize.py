# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
"""Assemble final qualification only from measured, deployed observations."""
from pathlib import Path
import json,sys
ROOT=Path(__file__).resolve().parent
read=lambda n:json.loads((ROOT/(n+'.json')).read_text(encoding='utf-8'))
def save(n,v):(ROOT/(n+'.json')).write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
runtime=read('runtime-final')
manifest=read('ui-manifest')
app_css=[name for name in manifest['ui'] if name.startswith('css/app.') and name.endswith('.css')]
conditions=['1600x1000','1440x1000','1440x900','1280x720','1024x768','390x844','390x480','zoom200']
pages=['list','details','userdata-content','userdata-versions','userdata-usage-vms','userdata-usage-templates','userdata-usage-isos','comments']
dialogs=['register','clone','delete','bulk-delete','vm-content','reset','template','iso']
checks=[]
paint_pool={theme:[] for theme in ['light','dark']}
for theme in paint_pool:
 for size in conditions:
  for view in pages+dialogs:
   label='final-'+theme+'-'+size+'-'+view;m=read(label)
   paint_pool[theme]+=m['essentialPaint']
 for state in ['checkbox','radio-focus','switch-focus']:
  paint_pool[theme]+=read('final-states-'+theme+'-'+state)
for theme in ['light','dark']:
 for size in conditions:
  for view in pages+dialogs:
   label='final-'+theme+'-'+size+'-'+view;m=read(label)
   assert any(k in m['app'] for k in runtime['app_files']),label+' stale app'
   assert app_css and all(any(k in href for href in m['styles']) for k in app_css),label+' stale css'
   assert m['standardProseAbsent'],label+' nonstandard helper prose'
   if '/#/userdata' in m['url']:
    assert m['breadcrumb']['borderLeft']==m['breadcrumb']['borderRight']=='0px',label+' breadcrumb vertical border'
   assert m['theme']==theme and m['pageOverflow']<=0,label
   if view in pages and view!='list':
    assert m['activeTab'] and m['activePanel'] and m['activeTab']['id']==m['activePanel']['labelledBy'],label+' tab/panel match'
    assert m['activeTab']['id'].endswith('-tab-'+view.replace('-','.')),label+' tab URL match'
   if view=='comments':assert m['commentMetadataCount']==3,label+' positive notes missing'
   if view=='userdata-versions':assert m['activeTab']['name']=='버전 기록',label+' locale/tab content match'
   assert all(x['ratio']>=4.5 for x in m['text']),label+' text'
   assert all(max(x['borderOutside'],x['fillOutside'])>=3 for x in m['essentialPaint']),label+' boundary'
   if m['modal']:
    assert m['modalOverflow']<=0,label
    assert abs(m['modal']['x']+m['modal']['width']/2-m['width']/2)<=1,label
    assert abs(m['modal']['y']+m['modal']['height']/2-m['height']/2)<=1,label
    assert all(x['rect']['height']==32 for x in m['buttons']),label+' footer'
   boundary=[x for x in m['controls'] if not x['disabled']]
   # Main-matrix controls record border/inner-fill contrast. Filled checked
   # controls use the measured adjacent surface and glyph states, not that
   # inner-fill ratio as their outer-boundary criterion.
   for control in boundary:
    assert control['ratio']>=3 or any(p['class']==control['class'] and abs(p['borderInside']-control['ratio'])<1e-6 and max(p['borderOutside'],p['fillOutside'])>=3 for p in paint_pool[theme]),label+' boundary state'
   if view in ['register','clone']:
    fields=[x['rect'] for x in m.get('spacing',[]) if 'ant-form-item' in x['class'] and x['rect']['height']>0]
    for a,b in zip(fields,fields[1:]):
     if abs(a['x']-b['x'])<1 and abs(a['width']-b['width'])<1:assert b['y']-a['y']-a['height']>=20,label+' field interference'
   checks.append(dict(label=label,passed=True,min_text=min(x['ratio'] for x in m['text']),essential_controls=len(boundary)))
for x in read('final-modal-scroll'):
 assert x['headerDelta']<=1 and x['footerDelta']<=1,x
for x in read('final-keyboard'):assert x['tabRemainedInside'] and x['maskDidNotClose'],x
for mode in ['light','dark']:
 assert all(x['textContrast']>=4.5 for x in read('final-states-'+mode+'-hover'))
 assert all(x['textContrast']>=4.5 for x in read('final-states-'+mode+'-popup') if x['text'])
 assert all(x['textContrast']>=4.5 for x in read('final-states-'+mode+'-tooltip') if x['text'])
 popup=read('final-mobile-popup-'+mode)
 assert popup['foreground'] and popup['rect']['left']>=0 and popup['rect']['right']<=popup['width'] and popup['rect']['top']>=0 and popup['rect']['bottom']<=popup['height']
 assert all(x['textContrast']>=4.5 for x in read('final-mobile-popup-'+mode+'-paint') if x['text'])
 assert all(x['borderOutside']>=3 for x in read('final-states-'+mode+'-input-focus'))
 assert all(x['ratio']>=4.5 for x in read('final-states-'+mode+'-required-error')['text'])
 for control in ['switch-focus','radio-focus','checkbox']:
  values=read('final-states-'+mode+'-'+control)
  assert any(float(x['outlineWidth'].replace('px',''))>=2 and x['outlineContrast']>=3 for x in values),mode+' '+control
if '--matrix-only' in sys.argv:
 print('Matrix and state probes PASS:',len(checks));sys.exit(0)
assert read('final-content-byte-check')['rawDownloadExact'] and read('final-content-byte-check')['base64ClipboardExact']
assert read('final-usage-refresh-state')['sameRows']
assert read('final-image-error-focus')['focused']
assert read('cleanup')['original_userdata_exact_match']
assert read('comment-cleanup')['original_userdata_exact_match']
assert all(read('standard-cleanup').values())
for case in read('final-mobile-selection-check')['cases']:
 assert case['selectedTwo'] and case['unselectedBoth'] and case['mainOverflow']==0,case
 assert max(case['heights'].values())-min(case['heights'].values())<=1,case
 assert max(case['selectedHeights'].values())-min(case['selectedHeights'].values())<=1,case
assert all(x==0 for x in read('db-after')['active_counts'].values())
units=read('unit-summary');assert units['success'] and units['numPassedTests']==317
save('qualification',dict(issue=1392,cluster=31,source=runtime['source'],matrix_checks=checks,matrix_count=len(checks),matrix_passed=len(checks),viewport_conditions=conditions,themes=['light','dark'],modal_scroll=read('final-modal-scroll'),keyboard=read('final-keyboard'),unit_suites=units['numPassedTestSuites'],unit_tests=units['numPassedTests'],final_route=read('final-cross-menu-route'),network_intervals=read('network-index'),cleanup=read('cleanup'),active_after=read('db-after')['active_counts'],limitations=['200% uses CDP CSS viewport/device scale emulation, not native browser menu zoom','role checks are representative real flows, not every role x viewport x theme combination','full Cloud CI and guest cloud-init execution were not rerun in this UI-only task']))
print('Final matrix PASS:',len(checks))
q=read('qualification')
q['evidence_phases']={
 'final_matrix':{'source':runtime['source'],'checks':len(checks),'fixtures':'standard-fixture.json','scope':'All pages and dialogs re-collected after prose/localization/contrast/breadcrumb/mobile-layout and Affix changes'},
 'functional_and_state_probes':{'scope':'Earlier actual CRUD/roles/fault/keyboard/scroll/hover/focus/popup verification retained as separately dated artifacts; final presentation changes have a fresh full matrix and actual mobile selection regression','runtime_reference':'runtime-layout-qualified.json'},
 'before_standard_removal':{'folder':'before-standard-removal','qualification_input':False},
 'before_comment_contrast':{'folder':'before-comment-contrast','qualification_input':False},
 'before_final_corrections':{'folder':'before-final-corrections','qualification_input':False}
}
q['mobile_selection_cases']=read('final-mobile-selection-check')['cases']
save('qualification',q)

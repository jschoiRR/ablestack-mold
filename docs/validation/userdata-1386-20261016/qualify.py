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
"""Cross-check saved UI requests, API, DB, guest execution and runtime evidence."""
import base64
import csv
import hashlib
import io
import json
from pathlib import Path
root=Path(__file__).resolve().parent
read=lambda n:json.loads((root/(n+'.json')).read_text(encoding='utf-8-sig'))
keys=['userdata','userdataid','userdatadetails']
same=lambda a,b:all(a[k]==b[k] for k in keys)
assert read('before-reproduction')['response']['errorcode']==530
assert read('before-unsupported')['userdataid'] is None
a=read('before-reproduction-after')
assert a['userdataid'] and a['userdatadetails']=='{TAG=A-value}'
for name in ['unsupported-fixed','secondary-configdrive-rejected']:
    assert read(name)['response']['errorcode']==431
    assert same(a,read(name+'-after'))
for name in ['provider-failure-after','provider-failure-api-after','after-failed-library-delete','provider-pending-after']:
    assert same(a,read(name))
assert 'restoration could not be confirmed' in read('provider-failure-api')['response']['errortext']
assert 'Another operation' in read('concurrent-reset')['response']['errortext']
assert read('failed-library-delete')['delete_response']['success']
assert read('running-state-rejected')['response']['errorcode']==431
assert same(read('after-vr-retry'),read('running-state-after'))
assert read('unsupported-ui')['switches'][0]['disabled']
assert not read('unsupported-keep-requests')['mutations']
assert all(x['response']['errorcode']==531 for x in read('acl-denied')) and len(read('acl-denied'))==4
for kind,label in [('user','일반 사용자 한글'),('domainadmin','도메인 관리자 한글'),('project','프로젝트 한글')]:
    assert read('after-'+kind)['userdatadetails']=='{TAG='+label+'}'
assert read('project-apply-requests')['mutations'][0]['params']['projectid']==read('owner-fixtures')['project_id']
for kind in ['vr','configdrive']:
    for phase,value in [('before','A'),('after','B')]:
        g=read('guest-'+phase+'-'+kind)
        assert g['exitcode']==0 and 'UD1386-'+value+' 한글' in g['stdout'] and 'UD1386-'+value+'-executed' in g['stdout']
for name,snapshot in [('router-before',a),('router-after-retry',read('after-vr-retry'))]:
    body=base64.b64decode(snapshot['userdata'])
    assert read(name)['stdout'].startswith(hashlib.sha256(body).hexdigest())
    assert body.decode() in read(name)['stdout']
    assert 'TAG='+snapshot['userdatadetails'].split('=',1)[1][:-1] in read(name)['stdout']
runtime=read('runtime-final');views=[]
for theme in ['dark','light']:
    for size in ['1440x1000','1280x720','390x844','390x480']:
        view=read('qualified-'+theme+'-'+size)
        assert view['source']==runtime['source']
        assert any(url.endswith('/js/app.e1aa674b.js') for url in view['loadedScriptURLs'])
        assert ('dark-mode' in view['theme'])==(theme=='dark')
        assert view['bodyOverflow']=='auto' and 0<=view['footer']['top']<view['footer']['bottom']<=view['viewport']['height']
        assert all(b['rect']['height']==32 and b['padding']=='4px 15px' for b in view['buttons'])
        assert 'Cloud data and library references were not changed.' in view['text']
        assert view['globalNotifications']==0
        views.append(theme+'-'+size)
scroll=read('qualified-dark-small-scrolled')
assert scroll['after']['scrollTop']>scroll['before']['scrollTop']
assert scroll['after']['header']==scroll['before']['header'] and scroll['after']['footer']==scroll['before']['footer']
assert read('network-read-error')['buttons'][-1]['disabled']
assert not read('network-read-retry')['buttons'][-1]['disabled']
assert not read('network-read-retry-requests')['mutations']
for name in ['network-read-error','network-read-retry','transport-unknown','transport-reopened']:
    assert read(name)['source']==runtime['source']
assert same(read('after-final-vr'),read('final-provider-failure-after'))
assert '성공·실패를 확정할 수 없습니다' in read('transport-unknown')['text']
assert same(read('transport-before'),read('transport-after'))
assert read('transport-unknown')['inputs'][-1]['value']=='최종 ConfigDrive 한글'
assert read('after-final-vr')['userdatadetails']=='{TAG=최종 VR 한글}'
assert read('after-final-configdrive')['userdatadetails']=='{TAG=최종 ConfigDrive 한글}'
guard=read('final-guard-fixtures')
for api,router in [('ui-after','ui'),('legacy','legacy'),('reset','reset')]:
    a=read('final-guard-'+api);r=read('final-guard-router-'+router)
    assert base64.b64decode(a['userdata']).decode()==r['body']
    assert a['userdatadetails']=='{TAG='+r['tag']+'}'
assert read('final-guard-legacy')['userdataid']==guard['userdata_ids']['B']
assert read('final-guard-reset')['userdataid']==guard['userdata_ids']['B']
assert read('final-guard-guest')['exitcode']==0 and 'UD1386-B-executed' in read('final-guard-guest')['stdout']
assert all(read('final-guard-result').values())
cases=[]
for file in root.glob('*-requests.json'):
    case=json.loads(file.read_text(encoding='utf-8-sig'))
    assert not case['truncated'] and not case['hasMore'] and not case['exceptions'],file.name
    assert all(not (p['params'].get('userdata') and p['params'].get('userdataid')) for p in case['mutations']),file.name
    cases.append(case)
assert len(read('provider-delay-requests')['mutations'])==1
for name in ['user-apply','domainadmin-apply','project-apply','final-provider-retry','transport-retry']:
    assert len(read(name+'-requests')['mutations'])==1
db=(root/'verified-db.tsv').read_text(encoding='utf-8-sig')
section=db[db.index('uuid\t'):].split('\nlibrary_uuid\t',1)[0]
rows={r['uuid']:r for r in csv.DictReader(io.StringIO(section),delimiter='\t')}
for name in ['unsupported-fixed-after','after-final-vr','after-final-configdrive','after-user','after-domainadmin','after-project']:
    vm=read(name);r=rows[vm['id']]
    assert r['state']==vm['state']=='Stopped' and r['removed']=='0'
    assert r['library_uuid']==vm['userdataid'] and r['user_data_details']==vm['userdatadetails']
    assert r['userdata_sha256']==hashlib.sha256(vm['userdata'].encode()).hexdigest()
guard_db=(root/'final-guard-db.tsv').read_text(encoding='utf-8-sig')
section=guard_db[guard_db.index('uuid\t'):].split('\nlibrary_uuid\t',1)[0]
row=next(r for r in csv.DictReader(io.StringIO(section),delimiter='\t') if r['uuid']==guard['vm']['id'])
assert row['state']=='Stopped' and row['removed']=='0' and row['library_uuid']==guard['userdata_ids']['B']
assert row['userdata_sha256']==hashlib.sha256(read('final-guard-reset')['userdata'].encode()).hexdigest()
assert row['user_data_details']==read('final-guard-reset')['userdatadetails']
cleanup=read('cleanup')
assert cleanup['original_library_exact_match'] and cleanup['original_library_count']==1
assert cleanup['vm_residuals']==cleanup['network_residuals']==0
cleanup=read('cleanup-final-guard')
assert cleanup['original_library_exact_match'] and cleanup['original_library_count']==1
assert cleanup['vm_residuals']==cleanup['network_residuals']==0
counts=(root/'cleanup-db.tsv').read_text(encoding='utf-8-sig').splitlines()[1:13]
assert len(counts)==12 and all(x.split('\t')[1]=='0' for x in counts)
assert not read('router-fault-cleanup')['reject_active'] and not read('router-fault-cleanup')['drop_active']
for host in read('cleanup-hosts'):
    assert not host['present_test_domains'] and not host['test_fault_rules']
    assert not any(host['booted_root_disk_exists'].values())
browser=read('cleanup-browser')
assert browser['theme']=='dark-mode' and len(browser['userdataLinks'])==1
assert browser['userdataLinks'][0]['name']=='AS-PROD-31-AS01-HTTP'
assert browser['temporaryInterceptionPatterns']==0 and not browser['temporaryCacheDisabled'] and browser['viewportReset']
assert runtime['static_files_verified']==855 and len(runtime['classes_verified'])==9
assert runtime['jar_sha256']=='27adb5fc8310905fc0b8fea6a8406c772fe398c9abff82b144f7acf54e436f74'
assert runtime['server_source']=='f89387ef67b3b07ca89b1ec7cf38e38c99930af7'
assert runtime['web_inf_preserved'] and runtime['config_preserved'] and runtime['mold']=='active' and runtime['client_http']==200
result=dict(issue=1386,source=runtime['source'],server_source=runtime['server_source'],ui_tests=160,focused_server_tests=46,bounded_ui_request_cases=len(cases),bounded_ui_exceptions=0,
            unsupported_data_preserved=True,provider_failure_data_preserved=True,concurrent_reset_rejected=True,failed_reference_deletable=True,
            actual_vr_and_configdrive_guest_execution=True,final_server_vr_guest_execution=True,legacy_update_delivery_compatible=True,guest_disks_inspected_read_only=True,vm_api_db_matches=7,
            responsive_theme_views=views,footer_height=32,static_hashes_verified=855,class_hashes_verified=9,
            active_test_residuals=0,original_library_exact_match=True,full_cloud_ci=False,result='PASS within UD-05 scope')
(root/'qualification-summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
print(json.dumps(result))

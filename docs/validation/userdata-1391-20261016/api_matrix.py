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
"""Capture immutable source references, real duplicate policy and role capability evidence."""
import fixtures as f
import hashlib,base64
s=f.state();a=f.ud.Mold();checks=[]
for key,item in s['userdata'].items():
 owner=dict(projectid=item['projectid']) if item.get('projectid') else dict(account=item['account'],domainid=item['domainid'])
 row=f.ok(a,'listUserData',id=item['id'],listall='true',**owner)['userdata'][0]
 assert row==item
 duplicate=a.call('registerUserData',name=f.PREFIX+'-duplicate-'+key,userdata=item['userdata'],params='CHANGED_ONLY',**owner)
 assert duplicate.get('errorcode')==431,duplicate
 checks.append(dict(case=key,id=item['id'],owner=owner,sha256=hashlib.sha256(base64.b64decode(item['userdata'])).hexdigest(),same_body_different_params_denial=duplicate))
roles=[]
for acc in s['accounts'][:2]:
 c=f.ud.Mold(acc['username'],domain='/'+f.PREFIX.lower());apis=f.ok(c,'listApis')['api'];names={p['name'] for p in apis}
 required=['listUserData','registerUserData','listAnnotations','addAnnotation'];assert set(required)<=names
 own=f.ok(c,'listUserData',id=s['userdata'][acc['suffix']]['id'])
 denied=c.call('listUserData',id=s['userdata']['admin']['id'],account='admin',domainid=s['userdata']['admin']['domainid'],listall='true')
 assert denied.get('errorcode') in [431,432,531] or denied.get('count',0)==0
 roles.append(dict(role=acc['suffix'],clone_apis=required,own_visible=own['count']==1,cross_owner_read=denied))
f.save('api-initial',dict(cases=checks,roles=roles))
baseline={}
for key,id in s['cases'].items():
 owner=dict(projectid=s['project_id']) if key=='project' else {}
 vm=f.ok(a,'listVirtualMachines',id=id,listall='true',userdata='true',details='all',**owner)['virtualmachine'][0]
 body=f.ok(a,'getVirtualMachineUserData',virtualmachineid=id)['virtualmachineuserdata']
 baseline[key]=dict(id=id,userdataid=vm.get('userdataid'),userdatadetails=vm.get('userdatadetails'),userdata=body.get('userdata',''),sha256=hashlib.sha256(base64.b64decode(body.get('userdata',''))).hexdigest())
f.save('references-before',dict(vms=baseline,templates=[f.ok(a,'listTemplates',id=id,templatefilter='all')['template'][0] for id in s['templates']]))
print('7 source libraries, 5 VM references and real duplicate/role policy captured')

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
"""Verify UI-created versions preserve references; explicitly switch and roll back disposable resources."""
import fixtures as f
import json,base64,hashlib
s=f.state();a=f.ud.Mold()
def library(name):
 rows=f.ok(a,'listUserData',name=name,listall='true',page=1,pagesize=500).get('userdata',[])
 rows=[x for x in rows if x['name']==name];assert len(rows)==1,rows
 return rows[0]
def snapshot(id,project=None):
 vm=f.ok(a,'listVirtualMachines',id=id,listall='true',userdata='true',details='all',**(dict(projectid=project) if project else {}))['virtualmachine'][0]
 body=f.ok(a,'getVirtualMachineUserData',virtualmachineid=id)['virtualmachineuserdata'].get('userdata','')
 return dict(id=id,userdataid=vm.get('userdataid'),userdatadetails=vm.get('userdatadetails'),userdata=body,sha256=hashlib.sha256(base64.b64decode(body)).hexdigest())
old=s['userdata']['admin'];v2=library(f.PREFIX+'-admin-v2');v3=library(f.PREFIX+'-admin-v3')
before=json.loads((f.ROOT/'references-before.json').read_text(encoding='utf-8'))
checks={key:snapshot(id,s['project_id'] if key=='project' else None) for key,id in s['cases'].items()}
assert checks==before['vms'],checks
image=f.ok(a,'listTemplates',id=s['templates'][0],templatefilter='all')['template'][0]
assert image['userdataid']==old['id'] and image['userdatapolicy']=='ALLOWOVERRIDE'
f.save('references-after-clone',dict(vms=checks,template=image,all_original_references_preserved=True))
for label,item in [('new',v2),('rollback',old)]:
 f.ok(a,'resetUserDataForVirtualMachine',id=s['cases']['admin'],userdataid=item['id'])
 current=snapshot(s['cases']['admin']);assert current['userdataid']==item['id'] and current['userdata']==item['userdata']
 f.save('vm-explicit-'+label,current)
for label,item in [('new',v2),('rollback',old)]:
 f.ok(a,'linkUserDataToTemplate',templateid=image['id'],userdataid=item['id'],userdatapolicy='ALLOWOVERRIDE')
 current=f.ok(a,'listTemplates',id=image['id'],templatefilter='all')['template'][0]
 assert current['userdataid']==item['id'] and current['userdatapolicy']=='ALLOWOVERRIDE'
 f.save('template-explicit-'+label,current)
# APPEND is verified on the new BOM-free immutable versions; originals remain byte-exact.
f.ok(a,'linkUserDataToTemplate',templateid=image['id'],userdataid=v2['id'],userdatapolicy='APPEND')
common=dict(zoneid=s['zone_id'],templateid=image['id'],serviceofferingid='cd928fb0-6e2b-4ec8-b02b-023aed55b2b1',networkids=s['networks'][0],startvm='false')
failed=a.call('deployVirtualMachine',name=f.PREFIX+'-bom-rejected',displayname=f.PREFIX+'-bom-rejected',userdataid=old['id'],**common)
assert failed.get('errorcode')==530,failed
assert f.ok(a,'listVirtualMachines',keyword=f.PREFIX+'-bom-rejected',listall='true').get('count',0)==0
vm=f.job(a,'deployVirtualMachine',name=f.PREFIX+'-append-control',displayname=f.PREFIX+'-append-control',userdataid=v3['id'],**common)['virtualmachine'];s['vms'].append(vm['id']);s['cases']['append-control']=vm['id'];f.save('runtime-state',s)
merged=snapshot(vm['id']);assert merged['userdataid']==v3['id'] and merged['userdata'] not in [v2['userdata'],v3['userdata']]
f.save('append-policy',dict(bom_preserved=base64.b64decode(old['userdata']).startswith(bytes([239,187,191])),bom_rejection=failed,bom_rejection_created_vms=0,bom_free_created_vm=vm['id'],merged=merged))
f.ok(a,'linkUserDataToTemplate',templateid=image['id'],userdataid=old['id'],userdatapolicy='ALLOWOVERRIDE')
assert snapshot(s['cases']['admin'])==before['vms']['admin']
f.save('switch-rollback',dict(original_vm_restored_exact=True,template_restored=dict(id=image['id'],userdataid=old['id'],policy='ALLOWOVERRIDE'),new_versions=[{k:v[k] for k in ['id','name','account','domainid','userdata']} for v in [v2,v3]]))
print('Original references preserved, explicit VM/template switch and rollback, and APPEND policy verified')

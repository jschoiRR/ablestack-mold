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
"""Cross-check saved VM body, original source and API ACL without guest execution."""
import fixtures as f
import base64,hashlib,urllib.parse
s=f.state();a=f.ud.Mold();checks=[]
for key,id in s['cases'].items():
 body=f.ok(a,'getVirtualMachineUserData',virtualmachineid=id)['virtualmachineuserdata'];vm=f.ok(a,'listVirtualMachines',id=id,listall='true',userdata='true',details='all',**(dict(projectid=s['project_id']) if key=='project' else {}))['virtualmachine'][0]
 encoded=body.get('userdata','');assert urllib.parse.unquote(vm.get('userdata',''))==encoded
 library=next((v for v in s['userdata'].values() if v['id']==vm.get('userdataid')),None)
 checks.append(dict(case=key,vm=vm,stored=body,bytes=len(base64.b64decode(encoded)),sha256=hashlib.sha256(base64.b64decode(encoded)).hexdigest(),comparison='unlinked' if not library else 'same' if library['userdata']==encoded else 'different'))
roles=[]
for acc in s['accounts'][:2]:
 client=f.ud.Mold(acc['username'],domain='/'+f.PREFIX.lower());apis=f.ok(client,'listApis')['api'];cmd=next((p for p in apis if p['name']=='getVirtualMachineUserData'),None);r=client.call('getVirtualMachineUserData',virtualmachineid=s['cases'][acc['suffix']]);assert cmd and 'errorcode' not in r
 denied=client.call('getVirtualMachineUserData',virtualmachineid=s['cases']['admin']);assert denied.get('errorcode') in [431,432,531]
 roles.append(dict(role=acc['suffix'],api=cmd,own_vm_allowed=True,cross_owner_denial=denied))
f.save('api-matrix',dict(cases=checks,roles=roles));print('10 stored bodies and owner ACL verified')

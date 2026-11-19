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
"""Template merge and binary fixtures, using dedicated stopped VMs only."""
import fixtures as f
s=f.state();a=f.ud.Mold()
original=f.ok(a,'listTemplates',id='8d692729-7565-4e92-965b-ebaadc1efcb0',templatefilter='all')['template'][0]
t={'id':s['templates'][0]}
for key,template,encoded in [('append',t['id'],None),('binary',original['id'],bytes([255,0,1,254]))]:
 if encoded is not None:
  data=f.ok(a,'registerUserData',name=f.PREFIX+'-'+key,userdata=f.ud.content(encoded))['userdata'];s['userdata'][key]=data;f.save('runtime-state',s)
 else:
  data=f.ok(a,'registerUserData',name=f.PREFIX+'-append-custom',userdata=f.ud.content('#cloud-config\n# custom append 한글\n'))['userdata'];s['userdata']['append-custom']=data;f.save('runtime-state',s)
 vm=f.job(a,'deployVirtualMachine',zoneid=s['zone_id'],templateid=template,serviceofferingid='cd928fb0-6e2b-4ec8-b02b-023aed55b2b1',networkids=s['networks'][0],name=f.PREFIX+'-'+key,displayname=f.PREFIX+'-'+key,startvm='false',userdataid=data['id'],**({'userdatadetails[0].QA_KEY':'APPENDED'} if key=='append' else {}))['virtualmachine']
 s['cases'][key]=vm['id'];s['vms'].append(vm['id']);f.save('runtime-state',s)
f.save('prepared',s)
print('Extra cases prepared')

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
"""Keep VM stored-body access while denying library source reads."""
import fixtures as f
s=f.state();a=f.ud.Mold();acc=s['accounts'][2]
role=f.ok(a,'createRole',name=f.PREFIX+'-source-denied',type='User',ispublic='false')['role'];s['roles'].append(role['id']);f.save('runtime-state',s)
for rule,permission in [('listUserData','deny'),('list*','allow'),('get*','allow'),('logout','allow'),('*','deny')]:f.ok(a,'createRolePermission',roleid=role['id'],rule=rule,permission=permission)
f.ok(a,'updateAccount',id=acc['id'],roleid=role['id'])
client=f.ud.Mold(acc['username'],domain='/'+f.PREFIX.lower());apis=f.ok(client,'listApis')['api'];body=f.ok(client,'getVirtualMachineUserData',virtualmachineid=s['cases']['outsider']);denied=client.call('listUserData',id=s['userdata']['outsider']['id']);assert denied.get('errorcode')==432 and all(x['name']!='listUserData' for x in apis)
f.save('source-denied-role',dict(roleid=role['id'],vm_body_allowed=True,library_api_absent=True,body=body,actual_library_denial=denied));print('VM access kept; source access denied')

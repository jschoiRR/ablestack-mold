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
import fixtures as f
import sys
a=f.ud.Mold();s=f.state();acc=s['accounts'][0]
if sys.argv[1]=='restrict':
 s['original_role']=f.ok(a,'listAccounts',id=acc['id'],listall='true')['account'][0]['roleid']
 role=f.ok(a,'createRole',name=f.PREFIX+'-reference-denied',type='User',ispublic='false')['role'];s['roles']=[role['id']];f.save('runtime-state',s)
 for rule,permission in [('listVirtualMachines','deny'),('list*','allow'),('get*','allow'),('logout','allow'),('*','deny')]:f.ok(a,'createRolePermission',roleid=role['id'],rule=rule,permission=permission)
 f.ok(a,'updateAccount',id=acc['id'],roleid=role['id'])
 client=f.ud.Mold(acc['username'],domain='/'+f.PREFIX.lower());apis=f.ok(client,'listApis')['api'];denial=client.call('listVirtualMachines',userdataid=s['userdata']['user']['id']);assert denial.get('errorcode')==432 and all(x['name']!='listVirtualMachines' for x in apis)
 f.save('restricted-role',dict(roleid=role['id'],original_roleid=s['original_role'],list_userdata_present=any(x['name']=='listUserData' for x in apis),list_vm_absent=True,actual_denial=denial));print('Reference-denied role applied')
else:f.ok(a,'updateAccount',id=acc['id'],roleid=s['original_role']);print('Original role restored')

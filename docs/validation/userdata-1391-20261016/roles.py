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
"""Restrict only the disposable outsider account to verify capability-driven clone visibility."""
import fixtures as f
import sys
s=f.state();a=f.ud.Mold();acc=s['accounts'][2]
if sys.argv[1]=='restrict':
 s['original_role']=f.ok(a,'listAccounts',id=acc['id'],listall='true')['account'][0]['roleid']
 role=f.ok(a,'createRole',name=f.PREFIX+'-clone-note-denied',type='User',ispublic='false')['role'];s['roles']=[role['id']];f.save('runtime-state',s)
 for rule,permission in [('addAnnotation','deny'),('listAnnotations','deny'),('*','allow')]:f.ok(a,'createRolePermission',roleid=role['id'],rule=rule,permission=permission)
 f.ok(a,'updateAccount',id=acc['id'],roleid=role['id'])
 c=f.ud.Mold(acc['username'],domain='/'+f.PREFIX.lower());apis={x['name'] for x in f.ok(c,'listApis')['api']}
 denial=c.call('addAnnotation',entityid=s['userdata']['outsider']['id'],entitytype='USER_DATA',annotation='must be denied')
 assert denial.get('errorcode')==432 and 'addAnnotation' not in apis and 'registerUserData' in apis
 f.save('restricted-role',dict(roleid=role['id'],add_annotation_absent=True,list_annotations_absent='listAnnotations' not in apis,registration_still_available=True,actual_denial=denial))
 print('Disposable restricted role applied')
else:
 f.ok(a,'updateAccount',id=acc['id'],roleid=s['original_role']);print('Original disposable role restored')

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
import base64
s=f.state();a=f.ud.Mold();checks=[]
def record(name,condition,**details):assert condition,(name,details);checks.append(dict(name=name,passed=True,**details))
if not s.get('paging_prepared'):
 for i in range(17):
  key='page'+str(i)
  if key in s['userdata']:continue
  s['userdata'][key]=f.ok(a,'registerUserData',name=f.PREFIX+'-'+key,userdata=base64.b64encode(('#cloud-config\n# '+f.PREFIX+' '+key+'\n').encode()).decode())['userdata'];f.save('runtime-state',s)
 s['paging_prepared']=True;f.save('runtime-state',s)
main=s['userdata']['main'];user=s['accounts'][0]
for name,params in [('UUID',dict(id=main['id'])),('exact-name',dict(name=main['name'])),('partial-name',dict(keyword='1389-31-20261016-main')),('owner-domain',dict(account=main['account'],domainid=main['domainid'],keyword=main['name']))]:
 rows=f.ok(a,'listUserData',listall='true',ignoreproject='true',**params).get('userdata',[]);record(name,[v['id'] for v in rows]==[main['id']])
r=f.ok(a,'listUserData',keyword='UD1389-no-match',listall='true');record('zero-result',not r.get('userdata') and not r.get('count'))
r1=f.ok(a,'listUserData',keyword=f.PREFIX,listall='true',page=1,pagesize=10);r2=f.ok(a,'listUserData',keyword=f.PREFIX,listall='true',page=2,pagesize=10);record('server-count-page',r1['count']==r2['count']==20 and len(r1['userdata'])==10 and len(r2['userdata'])==10 and not set(x['id'] for x in r1['userdata'])&set(x['id'] for x in r2['userdata']),count=r1['count'],pages=[10,10])
for suffix in ['user','domainadmin','outsider']:
 account=next(x for x in s['accounts'] if x['suffix']==suffix);client=f.ud.Mold(username=account['username'],domain='/'+f.PREFIX.lower())
 row=f.ok(client,'listUserData',listall='true').get('userdata',[])
 record(suffix+'-scope',set(v['id'] for v in row)==({s['userdata']['user']['id']} if suffix in ['user','domainadmin'] else set()),ids=[v['id'] for v in row])
 denial=client.call('listUserData',id=main['id'],listall='true');record(suffix+'-cross-domain-denied',denial.get('errorcode') in [431,432,531] or (not denial.get('userdata') and not denial.get('count')),response=denial)
 if suffix=='user':
  p=f.ok(client,'listUserData',projectid=s['project_id'],listall='true');record('project-member',p['count']==1 and p['userdata'][0]['id']==s['userdata']['project']['id'])
p=f.ok(a,'listUserData',projectid=s['project_id'],listall='true');record('admin-project',p['count']==1 and p['userdata'][0]['projectid']==s['project_id'])
f.save('api-matrix',dict(checks=checks,passed=len(checks)))
print('API assertions',len(checks))

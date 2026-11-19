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
import json
from pathlib import Path
a=f.ud.Mold();s=f.state();results=[]
def record(label,**data):
 results.append(dict(test=label,passed=True,**data));f.save('api-results',results);print(label,'PASS',flush=True)
def query(client,kind,uid,**params):
 c,field,filterkey={'vms':('listVirtualMachines','virtualmachine',None),'templates':('listTemplates','template','templatefilter'),'isos':('listIsos','iso','isofilter')}[kind]
 opts=dict(userdataid=uid,listall='true',page=1,pagesize=10)
 if filterkey:opts.update({filterkey:'all' if client is a else 'executable','showunique':'true'})
 opts.update(params);v=f.ok(client,c,**opts);rows=v.get(field,[])
 assert all(x.get('userdataid')==uid for x in rows),v
 return v.get('count',0),rows
for kind in ['vms','templates','isos']:
 uid=s['userdata']['A']['id'];count,rows=query(a,kind,uid);expected=12 if kind=='vms' else 2;assert count==expected,(kind,count)
 ids=[]
 for page in range(1,expected+1):
  total,part=query(a,kind,uid,page=page,pagesize=1);assert total==expected and len(part)==1;ids.append(part[0]['id'])
 assert len(set(ids))==expected
 zero,_=query(a,kind,s['userdata']['empty']['id']);assert zero==0
 keyword=rows[0].get('name');n,part=query(a,kind,uid,keyword=keyword);assert n>=1 and all(keyword.lower() in (x.get('name','')+' '+x.get('displayname','')+' '+x.get('displaytext','')).lower() for x in part)
 record(kind+'-uuid-count-page-search-zero',count=count,ids=ids,keyword=keyword)
 if kind!='vms':
  _,none=query(a,kind,s['userdata']['B']['id'],id=rows[0]['id']);assert none==[];record(kind+'-explicit-id-and-uuid-filter')
for suffix in ['user','domainadmin','outsider']:
 acc=next(x for x in s['accounts'] if x['suffix']==suffix);client=f.ud.Mold(acc['username'],domain='/'+f.PREFIX.lower())
 for kind in ['vms','templates','isos']:
  total,rows=query(client,kind,s['userdata']['A']['id']);assert total==0,(suffix,kind,total)
 if suffix!='outsider':
  total,rows=query(client,'vms',s['userdata']['user']['id']);assert total==1
 else:
  total,rows=query(client,'vms',s['userdata']['user']['id']);assert total==0
 project=client.call('listVirtualMachines',userdataid=s['userdata']['project']['id'],projectid=s['project_id'],listall='true')
 if suffix=='user':assert project.get('count')==1,project
 if suffix=='outsider':assert project.get('errorcode') in [431,432] or project.get('count',0)==0,project
 record(suffix+'-acl',project_response=project)
 f.ok(client,'logout')
# Root-owned library may legally be associated by admin with a VM owned by another account.
user_vm=f.ok(a,'listVirtualMachines',userdataid=s['userdata']['user']['id'],listall='true')['virtualmachine'][0]
f.ok(a,'resetUserDataForVirtualMachine',id=user_vm['id'],userdataid=s['userdata']['A']['id'])
total,rows=query(a,'vms',s['userdata']['A']['id'],pagesize=100);assert total==13 and user_vm['id'] in [v['id'] for v in rows]
f.ok(a,'resetUserDataForVirtualMachine',id=user_vm['id'],userdataid=s['userdata']['user']['id']);record('admin-cross-owner-reference-preserved',vm_id=user_vm['id'])
for api in ['listVirtualMachines','listTemplates','listIsos']:
 meta=f.ok(a,'listApis',name=api)['api'][0];assert any(p['name']=='userdataid' for p in meta['params'])
record('api-metadata-advertises-uuid-filter')
inherited=json.loads((f.ROOT/'template-inheritance.json').read_text(encoding='utf-8'))
assert not inherited.get('userdataid') and inherited['userdata']==s['userdata']['A']['userdata']
record('template-inheritance-has-no-uuid-and-is-excluded',evidence=inherited)

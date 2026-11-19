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
import importlib.util,json,os,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parent
PREFIX='UD1387-31-20261016'
spec=importlib.util.spec_from_file_location('ud',ROOT.parent/'userdata-1382-20261015/verify_api.py');ud=importlib.util.module_from_spec(spec);spec.loader.exec_module(ud)
def save(name,data): (ROOT/(name+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
def state():return json.loads((ROOT/'runtime-state.json').read_text(encoding='utf-8'))
def ok(a,c,**p):
 r=a.call(c,**p);assert 'errorcode' not in r,(c,r);return r
def job(a,c,**p):
 r=ok(a,c,**p)
 if not r.get('jobid'):return r
 jid=r['jobid']
 for n in range(180):
  r=ok(a,'queryAsyncJobResult',jobid=jid)
  if r.get('jobstatus')==1:return r.get('jobresult',{})
  assert r.get('jobstatus')!=2,(c,r)
  if n%30==0:print(c,'pending',n,flush=True)
  time.sleep(1)
 raise RuntimeError(c+' timed out')
def prepare(a):
 assert not (ROOT/'runtime-state.json').exists()
 save('baseline.private',dict(userdata=ud.inventory(a)))
 s=dict(prefix=PREFIX,userdata={},accounts=[],vms=[],networks=[]);save('runtime-state',s)
 domain=ok(a,'createDomain',name=PREFIX.lower())['domain'];s['domain_id']=domain['id'];save('runtime-state',s)
 for suffix,kind in [('user',0),('domainadmin',2)]:
  name=PREFIX.lower()+'-'+suffix
  acc=ok(a,'createAccount',username=name,password=os.environ['MOLD_TEST_PASSWORD'],firstname='UD1387',lastname='QA',email=suffix+'@example.invalid',account=name,accounttype=kind,domainid=s['domain_id'])['account']
  s['accounts'].append(dict(id=acc['id'],username=name,suffix=suffix));save('runtime-state',s)
 s['project_id']=job(a,'createProject',name=PREFIX+'-project',displaytext=PREFIX,account=s['accounts'][0]['username'],domainid=s['domain_id'])['project']['id'];save('runtime-state',s)
 for kind in ['root','user','domainadmin','project']:
  scope={} if kind=='root' else dict(projectid=s['project_id']) if kind=='project' else dict(account=next(x['username'] for x in s['accounts'] if x['suffix']==kind),domainid=s['domain_id'])
  names=['bulk-A','bulk-B','mixed-free','mixed-used','read-fail','denied','lost-response','deleted-before','replacement','single'] if kind=='root' else ['bulk-A','bulk-B']
  for suffix in names:
   key=kind+'-'+suffix
   s['userdata'][key]=ok(a,'registerUserData',name=PREFIX+'-'+key,userdata=ud.content('#cloud-config\n# '+PREFIX+' '+key+' 한글\n'),**scope)['userdata'];save('runtime-state',s)
 zone=ok(a,'listZones')['zone'][0]['id']
 n=ok(a,'createNetwork',name=PREFIX+'-cd',displaytext=PREFIX,networkofferingid='bc740904-060d-4e13-a850-3609e588116a',zoneid=zone,vlan=3984)['network'];s['networks'].append(n['id']);save('runtime-state',s)
 v=job(a,'deployVirtualMachine',zoneid=zone,templateid='8d692729-7565-4e92-965b-ebaadc1efcb0',serviceofferingid='cd928fb0-6e2b-4ec8-b02b-023aed55b2b1',networkids=n['id'],name=PREFIX+'-ref',displayname=PREFIX+'-ref',startvm='false',userdataid=s['userdata']['root-mixed-used']['id'])['virtualmachine'];s['vms'].append(v['id']);save('runtime-state',s)
 save('prepared',dict(prefix=PREFIX,userdata={k:{f:v.get(f) for f in ['id','name','account','domainid','domain','projectid','project']} for k,v in s['userdata'].items()},accounts=s['accounts'],domain_id=s['domain_id'],project_id=s['project_id'],vms=s['vms'],networks=s['networks']))
 print('Prepared',len(s['userdata']),'libraries and one stopped reference VM')
def inventory_with_test_project(a):
 rows=ud.inventory(a);s=state()
 project=ok(a,'listUserData',projectid=s['project_id'],listall='true',page=1,pagesize=500)
 rows.extend(project.get('userdata',[]));return sorted({v['id']:v for v in rows}.values(),key=lambda v:v['id'])
def check(a,label):
 s=state();rows=inventory_with_test_project(a);save(label,dict(libraries=[{k:v.get(k) for k in ['id','name','account','domainid','projectid']} for v in rows if v['name'].startswith(PREFIX)],count=sum(v['name'].startswith(PREFIX) for v in rows)))
 print(label,'remaining',sum(v['name'].startswith(PREFIX) for v in rows))
def cleanup(a):
 s=state()
 for v in s['vms']:
  result=a.call('listVirtualMachines',id=v,listall='true')
  if result.get('virtualmachine'):job(a,'destroyVirtualMachine',id=v,expunge='true')
 for v in inventory_with_test_project(a):
  if v['name'].startswith(PREFIX):ok(a,'deleteUserData',id=v['id'],**({'projectid':v['projectid']} if v.get('projectid') else {'account':v['account'],'domainid':v['domainid']}))
 for n in s['networks']:job(a,'deleteNetwork',id=n)
 job(a,'deleteProject',id=s['project_id'])
 for account in s['accounts']:job(a,'deleteAccount',id=account['id'])
 for role in s.get('roles',[]):ok(a,'deleteRole',id=role)
 job(a,'deleteDomain',id=s['domain_id'])
 current=ud.inventory(a);original=json.loads((ROOT/'baseline.private.json').read_text(encoding='utf-8'))['userdata'];assert current==original
 save('cleanup',dict(original_library_exact_match=True,original_library_count=len(current),active_userdata=0,active_vms=len(ok(a,'listVirtualMachines',keyword=PREFIX,listall='true').get('virtualmachine',[])),active_networks=len(ok(a,'listNetworks',keyword=PREFIX,listall='true').get('network',[]))))
 s['cleaned']=True;save('runtime-state',s)
if __name__=='__main__':
 sys.stdout.reconfigure(encoding='utf-8');a=ud.Mold();mode=sys.argv[1]
 if mode=='prepare':prepare(a)
 elif mode=='check':check(a,sys.argv[2])
 elif mode=='unreference':
  s=state();r=ok(a,'resetUserDataForVirtualMachine',id=s['vms'][0],userdata=ud.content('#cloud-config\n# UD1387 reference cleared\n'));v=ok(a,'listVirtualMachines',id=s['vms'][0],listall='true')['virtualmachine'][0];assert not v.get('userdataid');save('unreference',dict(vm_id=v['id'],state=v['state'],userdataid=v.get('userdataid')));print('Reference cleared')
 elif mode=='replace-before':
  s=state();old=s['userdata']['root-deleted-before'];ok(a,'deleteUserData',id=old['id'],account=old['account'],domainid=old['domainid']);new=ok(a,'registerUserData',name=old['name'],userdata=ud.content('#cloud-config\n# UD1387 replacement different UUID\n'))['userdata'];s['userdata']['root-replacement-new']=new;save('runtime-state',s);save('replaced-before-submit',dict(original_id=old['id'],replacement_id=new['id'],name=old['name']));print('Original deleted and same-name replacement registered')
 elif mode=='readonly':
  s=state();acc=s['accounts'][0];info=ok(a,'listAccounts',id=acc['id'],listall='true')['account'][0];s['original_user_role']=info['roleid']
  role=ok(a,'createRole',name=PREFIX+'-readonly',type='User',ispublic='false')['role'];s['roles']=[role['id']];save('runtime-state',s)
  for rule in ['list*','get*','logout']:ok(a,'createRolePermission',roleid=role['id'],rule=rule,permission='allow')
  ok(a,'createRolePermission',roleid=role['id'],rule='*',permission='deny');ok(a,'updateAccount',id=acc['id'],roleid=role['id'])
  s['userdata']['user-readonly']=ok(a,'registerUserData',name=PREFIX+'-user-readonly',userdata=ud.content('#cloud-config\n# UD1387 readonly\n'),account=acc['username'],domainid=s['domain_id'])['userdata'];save('runtime-state',s)
  client=ud.Mold(acc['username'],domain='/'+PREFIX.lower());cap=ok(client,'listApis');apis=[x['name'] for x in cap.get('api',[])];denied=client.call('deleteUserData',id=s['userdata']['user-readonly']['id'],account=acc['username'],domainid=s['domain_id']);assert denied.get('errorcode')==432,denied;ok(client,'logout')
  save('readonly-role',dict(account=acc['username'],role_id=role['id'],original_role_id=s['original_user_role'],list_userdata_available='listUserData' in apis,delete_userdata_available='deleteUserData' in apis,delete_response=denied))
  print('Temporary user read-only role applied')
 elif mode=='restore-role':
  s=state();ok(a,'updateAccount',id=s['accounts'][0]['id'],roleid=s['original_user_role']);print('Original user role restored')
 elif mode=='cleanup':cleanup(a)

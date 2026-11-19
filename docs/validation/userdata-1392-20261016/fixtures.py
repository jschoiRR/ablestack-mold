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
PREFIX='UD1392-31-20261016'
spec=importlib.util.spec_from_file_location('ud',ROOT.parent/'userdata-1382-20261015/verify_api.py');ud=importlib.util.module_from_spec(spec);spec.loader.exec_module(ud)
def save(name,data): (ROOT/(name+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
def state():return json.loads((ROOT/'runtime-state.json').read_text(encoding='utf-8'))
def ok(a,c,**p):
 v=a.call(c,**p);assert 'errorcode' not in v,(c,v);return v
def job(a,c,**p):return a.job(c,**p)
def prepare(a):
 assert not (ROOT/'runtime-state.json').exists()
 save('baseline.private',dict(userdata=ud.inventory(a)))
 s=dict(prefix=PREFIX,userdata={},accounts=[],vms=[],networks=[],templates=[],isos=[]);save('runtime-state',s)
 s['domain_id']=ok(a,'createDomain',name=PREFIX.lower())['domain']['id'];save('runtime-state',s)
 for suffix,kind in [('user',0),('domainadmin',2),('outsider',0)]:
  name=PREFIX.lower()+'-'+suffix
  acc=ok(a,'createAccount',username=name,password=os.environ['MOLD_TEST_PASSWORD'],firstname='UD1392',lastname='QA',email=suffix+'@example.invalid',account=name,accounttype=kind,domainid=s['domain_id'])['account']
  s['accounts'].append(dict(id=acc['id'],username=name,suffix=suffix));save('runtime-state',s)
 user=s['accounts'][0]
 s['project_id']=job(a,'createProject',name=PREFIX+'-project',displaytext=PREFIX,account=user['username'],domainid=s['domain_id'])['project']['id'];save('runtime-state',s)
 for key,scope in [('A',{}),('B',{}),('empty',{}),('user',dict(account=user['username'],domainid=s['domain_id'])),('domainadmin',dict(account=s['accounts'][1]['username'],domainid=s['domain_id'])),('project',dict(projectid=s['project_id']))]:
  s['userdata'][key]=ok(a,'registerUserData',name=PREFIX+'-'+key,userdata=ud.content('#cloud-config\n# '+PREFIX+' '+key+'\n'),**scope)['userdata'];save('runtime-state',s)
 zone=ok(a,'listZones')['zone'][0]['id'];s['zone_id']=zone
 original=ok(a,'listTemplates',id='8d692729-7565-4e92-965b-ebaadc1efcb0',templatefilter='all')['template'][0]
 originaliso=next(x for x in ok(a,'listIsos',isofilter='all',showunique='true',page=1,pagesize=500)['iso'] if x.get('url') and x.get('isready'))
 for kind,api in [('templates','registerTemplate'),('isos','registerIso')]:
  for i in range(2):
   name=PREFIX+'-'+kind+'-'+str(i);params=dict(name=name,displaytext=name,url=original['url'] if kind=='templates' else originaliso['url'],zoneid=zone,ispublic='false',directdownload='true',ostypeid=original['ostypeid'] if kind=='templates' else originaliso['ostypeid'])
   if kind=='templates':params.update(format='QCOW2',hypervisor='KVM',requireshvm='true')
   else:params.update(bootable='true')
   result=ok(a,api,**params);item=result['template' if kind=='templates' else 'iso'][0];s[kind].append(item['id']);save('runtime-state',s)
   ok(a,'linkUserDataToTemplate',templateid=item['id'],userdataid=s['userdata']['A']['id'],userdatapolicy='ALLOWOVERRIDE' if i==0 else 'APPEND')
 for key,scope,vlan,total in [('A',{},3984,11),('user',dict(account=user['username'],domainid=s['domain_id']),3985,1),('project',dict(projectid=s['project_id']),3986,1)]:
  net=ok(a,'createNetwork',name=PREFIX+'-'+key+'-cd',displaytext=PREFIX,networkofferingid='bc740904-060d-4e13-a850-3609e588116a',zoneid=zone,vlan=vlan,**scope)['network'];s['networks'].append(net['id']);s[key+'_network']=net['id'];save('runtime-state',s)
  for i in range(total):
   name=PREFIX+'-'+key+'-vm-'+str(i+1).zfill(2)
   vm=job(a,'deployVirtualMachine',zoneid=zone,templateid=original['id'],serviceofferingid='cd928fb0-6e2b-4ec8-b02b-023aed55b2b1',networkids=net['id'],name=name,displayname=name,startvm='false',userdataid=s['userdata'][key]['id'],**scope)['virtualmachine'];s['vms'].append(vm['id']);save('runtime-state',s);print('Created stopped',name,flush=True)
 save('prepared',{k:v for k,v in s.items() if k!='cleaned'})
def cleanup(a):
 s=state()
 # Discover UI-created libraries and VMs in both test scopes.
 for scope in [{},dict(projectid=s['project_id'])]:
  for item in ok(a,'listUserData',listall='true',page=1,pagesize=500,**scope).get('userdata',[]):
   if item['name'].startswith(PREFIX):s['userdata'][item['id']]=item
  for item in ok(a,'listVirtualMachines',listall='true',keyword=PREFIX,page=1,pagesize=500,**scope).get('virtualmachine',[]):
   if item['displayname'].startswith(PREFIX) and item['id'] not in s['vms']:s['vms'].append(item['id'])
 save('runtime-state',s)
 for kind,ids in [('USER_DATA',{x['id'] for x in s['userdata'].values()}),('VM',s['vms']),('TEMPLATE',s['templates']),('ISO',s['isos'])]:
  for id in ids:
   result=a.call('listAnnotations',entityid=id,entitytype=kind,annotationfilter='all',page=1,pagesize=500)
   if result.get('errorcode')==431:continue
   assert 'errorcode' not in result,result
   for note in result.get('annotation',[]):ok(a,'removeAnnotation',id=note['id'])

 for kind,api in [('templates','deleteTemplate'),('isos','deleteIso')]:
  for id in s[kind]:
   ok(a,'linkUserDataToTemplate',templateid=id)
 for vm in ok(a,'listVirtualMachines',keyword=PREFIX,listall='true',page=1,pagesize=500).get('virtualmachine',[])+ok(a,'listVirtualMachines',keyword=PREFIX,listall='true',projectid=s['project_id'],page=1,pagesize=500).get('virtualmachine',[]):job(a,'destroyVirtualMachine',id=vm['id'],expunge='true')
 for kind,api in [('templates','deleteTemplate'),('isos','deleteIso')]:
  for id in s[kind]:job(a,api,id=id)
 for scope in [{},dict(projectid=s['project_id'])]:
  for item in ok(a,'listUserData',listall='true',page=1,pagesize=500,**scope).get('userdata',[]):
   if item['name'].startswith(PREFIX):s['userdata'][item['id']]=item
 save('runtime-state',s)
 for data in s['userdata'].values():
  result=a.call('deleteUserData',id=data['id'],**(dict(projectid=data['projectid']) if data.get('projectid') else dict(account=data['account'],domainid=data['domainid'])));assert result.get('success') or result.get('errorcode')==431,result
 for id in s['networks']:job(a,'deleteNetwork',id=id)
 job(a,'deleteProject',id=s['project_id'])
 for acc in s['accounts']:job(a,'deleteAccount',id=acc['id'])
 for role in s.get('roles',[]):ok(a,'deleteRole',id=role)
 job(a,'deleteDomain',id=s['domain_id'])
 assert ud.inventory(a)==json.loads((ROOT/'baseline.private.json').read_text(encoding='utf-8'))['userdata']
 save('cleanup',dict(original_userdata_exact_match=True,active_vms=ok(a,'listVirtualMachines',keyword=PREFIX,listall='true').get('count',0),active_networks=ok(a,'listNetworks',keyword=PREFIX,listall='true').get('count',0)));s['cleaned']=True;save('runtime-state',s)
if __name__=='__main__':
 sys.stdout.reconfigure(encoding='utf-8');a=ud.Mold();globals()[sys.argv[1]](a)

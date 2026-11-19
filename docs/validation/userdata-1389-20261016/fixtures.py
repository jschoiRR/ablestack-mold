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
import importlib.util,json,os,sys,base64,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parent
PREFIX='UD1389-31-20261016'
spec=importlib.util.spec_from_file_location('ud',ROOT.parent/'userdata-1382-20261015/verify_api.py');ud=importlib.util.module_from_spec(spec);spec.loader.exec_module(ud)
def save(name,data): (ROOT/(name+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def state():return json.loads((ROOT/'runtime-state.json').read_text(encoding='utf-8'))
def ok(a,c,**p):
 v=a.call(c,**p);assert 'errorcode' not in v,(c,v);return v
def job(a,c,**p):return a.job(c,**p)
def text():return '\ufeff#cloud-config\r\n# '+PREFIX+' 한글·UTF-8·😀\r\n# <script>본문은 실행되지 않습니다</script>\r\n# '+('긴행-가나다라'*500)+'\r\n'+''.join('# 줄 '+str(i)+' 한글 본문\r\n' for i in range(100))+'\r\n'
def prepare(a):
 assert not (ROOT/'runtime-state.json').exists()
 save('baseline.private',dict(userdata=ud.inventory(a)))
 s=dict(prefix=PREFIX,userdata={},accounts=[],vms=[],networks=[],templates=[],isos=[]);save('runtime-state',s)
 s['domain_id']=ok(a,'createDomain',name=PREFIX.lower())['domain']['id'];save('runtime-state',s)
 for suffix,kind in [('user',0),('domainadmin',2),('outsider',0)]:
  name=PREFIX.lower()+'-'+suffix
  acc=ok(a,'createAccount',username=name,password=os.environ['MOLD_TEST_PASSWORD'],firstname='UD1389',lastname=suffix,email=suffix+'@example.invalid',account=name,accounttype=kind,domainid=s['domain_id'])['account']
  s['accounts'].append(dict(id=acc['id'],username=name,suffix=suffix));save('runtime-state',s)
 user=s['accounts'][0]
 s['project_id']=job(a,'createProject',name=PREFIX+'-project',displaytext=PREFIX,account=user['username'],domainid=s['domain_id'])['project']['id'];save('runtime-state',s)
 for key,scope,body in [('main',{},text().encode()),('binary',{},bytes([255,0,1,2,254])),('user',dict(account=user['username'],domainid=s['domain_id']),b'#cloud-config\n# user UD1389\n'),('project',dict(projectid=s['project_id']),'#cloud-config\n# 프로젝트 UD1389\n'.encode())]:
  s['userdata'][key]=ok(a,'registerUserData',name=PREFIX+'-'+key,userdata=base64.b64encode(body).decode(),params='hostname,QA_KEY' if key=='main' else '',**scope)['userdata'];save('runtime-state',s)
 zone=ok(a,'listZones')['zone'][0]['id'];s['zone_id']=zone
 original=ok(a,'listTemplates',id='8d692729-7565-4e92-965b-ebaadc1efcb0',templatefilter='all')['template'][0]
 originaliso=next(x for x in ok(a,'listIsos',isofilter='all',showunique='true',page=1,pagesize=500)['iso'] if x.get('url') and x.get('isready'))
 for kind,api in [('templates','registerTemplate'),('isos','registerIso')]:
  name=PREFIX+'-'+kind;params=dict(name=name,displaytext=name,url=original['url'] if kind=='templates' else originaliso['url'],zoneid=zone,ispublic='false',directdownload='true',ostypeid=original['ostypeid'] if kind=='templates' else originaliso['ostypeid'])
  if kind=='templates':params.update(format='QCOW2',hypervisor='KVM',requireshvm='true')
  else:params.update(bootable='true')
  result=ok(a,api,**params);item=result['template' if kind=='templates' else 'iso'][0];s[kind].append(item['id']);save('runtime-state',s)
  ok(a,'linkUserDataToTemplate',templateid=item['id'],userdataid=s['userdata']['main']['id'],userdatapolicy='ALLOWOVERRIDE')
 net=ok(a,'createNetwork',name=PREFIX+'-cd',displaytext=PREFIX,networkofferingid='bc740904-060d-4e13-a850-3609e588116a',zoneid=zone,vlan=3984)['network'];s['networks'].append(net['id']);save('runtime-state',s)
 vm=job(a,'deployVirtualMachine',zoneid=zone,templateid=original['id'],serviceofferingid='cd928fb0-6e2b-4ec8-b02b-023aed55b2b1',networkids=net['id'],name=PREFIX+'-vm',displayname=PREFIX+'-vm',startvm='false',userdataid=s['userdata']['main']['id'])['virtualmachine'];s['vms'].append(vm['id']);save('runtime-state',s)
 save('prepared',s);verify(a)
def verify(a):
 s=state();checks=[]
 for key,item in s['userdata'].items():
  scope=dict(projectid=item['projectid']) if item.get('projectid') else dict(ignoreproject='true',account=item['account'],domainid=item['domainid'])
  row=ok(a,'listUserData',id=item['id'],listall='true',**scope)['userdata'][0];assert row==item,(key,row,item)
  checks.append(dict(key=key,id=row['id'],bytes=len(base64.b64decode(row['userdata'])),sha256=hashlib.sha256(base64.b64decode(row['userdata'])).hexdigest(),scope=scope))
 refs={}
 for api,key,extra in [('listVirtualMachines','virtualmachine',{}),('listTemplates','template',dict(templatefilter='all',showunique='true')),('listIsos','iso',dict(isofilter='all',showunique='true'))]:
  r=ok(a,api,userdataid=s['userdata']['main']['id'],listall='true',page=1,pagesize=20,**extra);assert r['count']==1 and len(r[key])==1 and r[key][0]['userdataid']==s['userdata']['main']['id'];refs[api]=dict(count=r['count'],ids=[v['id'] for v in r[key]])
 save('api-content',dict(checks=checks,references=refs))
def cleanup(a):
 s=state()
 for kind in ['templates','isos']:
  for id in s[kind]:ok(a,'linkUserDataToTemplate',templateid=id)
 for id in s['vms']:job(a,'destroyVirtualMachine',id=id,expunge='true')
 for kind,api in [('templates','deleteTemplate'),('isos','deleteIso')]:
  for id in s[kind]:job(a,api,id=id)
 for item in s['userdata'].values():
  result=a.call('deleteUserData',id=item['id'],**(dict(projectid=item['projectid']) if item.get('projectid') else dict(account=item['account'],domainid=item['domainid'])));assert result.get('success') or result.get('errorcode')==431,result
 for id in s['networks']:job(a,'deleteNetwork',id=id)
 job(a,'deleteProject',id=s['project_id'])
 for acc in s['accounts']:job(a,'deleteAccount',id=acc['id'])
 for role in s.get('roles',[]):ok(a,'deleteRole',id=role)
 job(a,'deleteDomain',id=s['domain_id'])
 assert ud.inventory(a)==json.loads((ROOT/'baseline.private.json').read_text(encoding='utf-8'))['userdata']
 save('cleanup',dict(original_userdata_exact_match=True,active_vms=ok(a,'listVirtualMachines',keyword=PREFIX,listall='true').get('count',0),active_networks=ok(a,'listNetworks',keyword=PREFIX,listall='true').get('count',0)));s['cleaned']=True;save('runtime-state',s)
if __name__=='__main__':
 sys.stdout.reconfigure(encoding='utf-8');a=ud.Mold();globals()[sys.argv[1]](a)

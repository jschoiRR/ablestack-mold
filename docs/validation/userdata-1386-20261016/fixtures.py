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
"""Issue-1386 disposable cluster-31 fixtures and API/cleanup evidence."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import time

ROOT=Path(__file__).resolve().parent
PREFIX='UD1386-31-20261016'
spec=importlib.util.spec_from_file_location('ud',ROOT.parent/'userdata-1382-20261015/verify_api.py')
ud=importlib.util.module_from_spec(spec);spec.loader.exec_module(ud)
def save(name,data): (ROOT/(name+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
def state():return json.loads((ROOT/'runtime-state.json').read_text(encoding='utf-8'))
def ok(a,c,**p):
    r=a.call(c,**p);assert 'errorcode' not in r,(c,r);return r
def job(a,c,**p):
    r=ok(a,c,**p)
    if not r.get('jobid'):return r
    for n in range(240):
        if n==0:jid=r['jobid']
        r=ok(a,'queryAsyncJobResult',jobid=jid)
        if r.get('jobstatus')==1:return r.get('jobresult',{})
        assert r.get('jobstatus')!=2,(c,r)
        if n%15==0:print(c,'pending',n,flush=True)
        time.sleep(1)
    raise RuntimeError(c+' timed out')
def snapshot(a,label,kind):
    s=state();v=ok(a,'listVirtualMachines',id=s['vms'][kind]['id'],userdata='true',listall='true')['virtualmachine'][0]
    keys=['id','name','instancename','state','hostid','hostname','userdata','userdataid','userdatadetails','nic']
    r={k:v.get(k) for k in keys};save(label,r);return r
def prepare(a):
    assert not (ROOT/'runtime-state.json').exists()
    save('baseline.private',dict(userdata=ud.inventory(a)))
    s=dict(prefix=PREFIX,userdata={},networks={},vms={});save('runtime-state',s)
    zone=ok(a,'listZones')['zone'][0]['id']
    for kind,offering,extra in [
        ('unsupported','79ec5c18-b71c-4f7a-9b5d-8694b16b8a54',dict(vlan=3984)),
        ('configdrive','bc740904-060d-4e13-a850-3609e588116a',dict(vlan=3985)),
        ('vr','46a69931-ebbf-4aee-9d3d-12d03315a949',dict(gateway='10.138.6.1',netmask='255.255.255.0'))]:
        s['networks'][kind]=ok(a,'createNetwork',name=PREFIX+'-'+kind,displaytext=PREFIX+'-'+kind,networkofferingid=offering,zoneid=zone,**extra)['network'];save('runtime-state',s)
    for suffix in ['A','B']:
        body='#cloud-config\nwrite_files:\n  - path: /var/tmp/ud1386-proof\n    permissions: "0644"\n    content: "UD1386-'+suffix+' 한글\\n"\nruncmd:\n  - [sh, -c, "printf UD1386-'+suffix+'-executed > /var/tmp/ud1386-executed"]\n'
        s['userdata'][suffix]=ok(a,'registerUserData',name=PREFIX+'-'+suffix,userdata=ud.content(body),params='TAG')['userdata'];save('runtime-state',s)
    prepare_vms(a)
def prepare_vms(a):
    s=state();zone=ok(a,'listZones')['zone'][0]['id']
    for kind in ['unsupported','configdrive','vr']:
        if kind in s['vms']:continue
        params=dict(zoneid=zone,templateid='8d692729-7565-4e92-965b-ebaadc1efcb0',serviceofferingid='cd928fb0-6e2b-4ec8-b02b-023aed55b2b1',networkids=s['networks'][kind]['id'],name=PREFIX+'-'+kind,displayname=PREFIX+'-'+kind,startvm='false')
        if kind!='unsupported':params.update(userdataid=s['userdata']['A']['id'],**{'userdatadetails[0].TAG':'A-value'})
        v=job(a,'deployVirtualMachine',**params)['virtualmachine'];s['vms'][kind]={k:v[k] for k in ['id','name']};save('runtime-state',s);snapshot(a,'before-'+kind,kind)
        print(kind,v['id'],flush=True)
    save('prepared',dict(userdata={k:v['id'] for k,v in s['userdata'].items()},networks={k:v['id'] for k,v in s['networks'].items()},vms=s['vms']))
def boot(a,kind):
    v=job(a,'startVirtualMachine',id=state()['vms'][kind]['id'])['virtualmachine'];save('boot-'+kind,{k:v.get(k) for k in ['id','state','hostid','hostname','instancename','nic']});print(json.dumps({k:v.get(k) for k in ['id','state','hostname','instancename']}))
def reset(a,kind,key,label):
    s=state();p=dict(id=s['vms'][kind]['id'],userdataid=s['userdata'][key]['id']);p['userdatadetails[0].TAG']=key+'-value'
    r=a.call('resetUserDataForVirtualMachine',**p);save(label,dict(request=p,response=r));save(label+'-after',snapshot(a,label+'-after',kind));print(json.dumps(r,ensure_ascii=False))
def prepare_owners(a):
    s=state();assert not s.get('domain_id')
    domain=ok(a,'createDomain',name=PREFIX.lower())['domain'];s.update(domain_id=domain['id'],accounts=[],roles=[]);save('runtime-state',s)
    zone=ok(a,'listZones')['zone'][0]['id']
    for suffix,kind,role_type in [('user',0,'User'),('domainadmin',2,'DomainAdmin')]:
        name=PREFIX.lower()+'-'+suffix
        acc=ok(a,'createAccount',username=name,password=os.environ['MOLD_TEST_PASSWORD'],firstname='UD1386',lastname='QA',email=suffix+'@example.invalid',account=name,accounttype=kind,domainid=domain['id'])['account']
        s['accounts'].append(dict(id=acc['id'],username=name,suffix=suffix));save('runtime-state',s)
        role=ok(a,'createRole',name=PREFIX+'-'+suffix+'-reset',type=role_type,ispublic='false')['role'];s['roles'].append(role['id']);save('runtime-state',s)
        for rule in ['list*','get*','query*','resetUserDataForVirtualMachine','logout']:ok(a,'createRolePermission',roleid=role['id'],rule=rule,permission='allow')
        ok(a,'createRolePermission',roleid=role['id'],rule='*',permission='deny')
        ok(a,'updateAccount',id=acc['id'],roleid=role['id'])
    project=job(a,'createProject',name=PREFIX+'-project',displaytext=PREFIX,account=s['accounts'][0]['username'],domainid=domain['id'])['project']
    s['project_id']=project['id'];save('runtime-state',s)
    for n,(suffix,scope) in enumerate([(x['suffix'],dict(account=x['username'],domainid=domain['id'])) for x in s['accounts']]+[('project',dict(projectid=project['id']))]):
        body=ud.content('#cloud-config\n# '+PREFIX+'-'+suffix+'\n')
        s['userdata'][suffix]=ok(a,'registerUserData',name=PREFIX+'-'+suffix,userdata=body,params='TAG',**scope)['userdata'];save('runtime-state',s)
        s['networks'][suffix]=ok(a,'createNetwork',name=PREFIX+'-'+suffix,displaytext=PREFIX,networkofferingid='bc740904-060d-4e13-a850-3609e588116a',zoneid=zone,vlan=3986+n,**scope)['network'];save('runtime-state',s)
        v=job(a,'deployVirtualMachine',zoneid=zone,templateid='8d692729-7565-4e92-965b-ebaadc1efcb0',serviceofferingid='cd928fb0-6e2b-4ec8-b02b-023aed55b2b1',networkids=s['networks'][suffix]['id'],name=PREFIX+'-'+suffix,displayname=PREFIX+'-'+suffix,startvm='false',userdataid=s['userdata'][suffix]['id'],**{'userdatadetails[0].TAG':suffix+'-initial'},**scope)['virtualmachine']
        s['vms'][suffix]={k:v[k] for k in ['id','name']};save('runtime-state',s);print(suffix,v['id'],flush=True)
    save('owner-fixtures',dict(domain_id=s['domain_id'],accounts=s['accounts'],project_id=s['project_id'],roles=s['roles'],vms={k:v for k,v in s['vms'].items() if k in ['user','domainadmin','project']}))
def cleanup(a):
    s=state()
    for v in s['vms'].values():job(a,'destroyVirtualMachine',id=v['id'],expunge='true')
    for v in s['userdata'].values():ok(a,'deleteUserData',id=v['id'])
    for n in s['networks'].values():job(a,'deleteNetwork',id=n['id'])
    if s.get('project_id'):job(a,'deleteProject',id=s['project_id'])
    for account in s.get('accounts',[]):job(a,'deleteAccount',id=account['id'])
    for role in s.get('roles',[]):ok(a,'deleteRole',id=role)
    if s.get('domain_id'):job(a,'deleteDomain',id=s['domain_id'])
    remaining=ud.inventory(a);assert remaining==json.loads((ROOT/'baseline.private.json').read_text(encoding='utf-8'))['userdata']
    save('cleanup',dict(original_library_exact_match=True,original_library_count=len(remaining),vm_residuals=len(ok(a,'listVirtualMachines',keyword=PREFIX,listall='true').get('virtualmachine',[])),network_residuals=len(ok(a,'listNetworks',keyword=PREFIX,listall='true').get('network',[]))))
    s['cleaned']=True;save('runtime-state',s)
if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');a=ud.Mold();mode=sys.argv[1]
    if mode=='prepare':prepare(a)
    elif mode=='prepare-vms':prepare_vms(a)
    elif mode=='prepare-owners':prepare_owners(a)
    elif mode=='boot':boot(a,sys.argv[2])
    elif mode=='stop':print(job(a,'stopVirtualMachine',id=state()['vms'][sys.argv[2]]['id']))
    elif mode=='snapshot':print(json.dumps(snapshot(a,sys.argv[2],sys.argv[3]),ensure_ascii=False))
    elif mode=='reset':reset(a,*sys.argv[2:5])
    elif mode=='cleanup':cleanup(a)

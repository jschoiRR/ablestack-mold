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
"""Disposable cluster-31 UD-04 fixtures. No credentials are written to disk."""
import base64
import importlib.util
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent
PREFIX = 'UD1385-31-20261016'
spec = importlib.util.spec_from_file_location('ud01', ROOT.parent/'userdata-1382-20261015/verify_api.py')
ud = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ud)

def save(name, data):
    (ROOT/name).write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n',encoding='utf-8',newline='\n')

def state(): return json.loads((ROOT/'runtime-state.json').read_text(encoding='utf-8'))

def ok(api, command, **params):
    result=api.call(command, **params)
    assert 'errorcode' not in result, (command,result)
    return result

def prepare(api):
    assert not (ROOT/'runtime-state.json').exists(), 'Do not overwrite active fixtures or baseline'
    save('baseline.private.json',dict(userdata=ud.inventory(api)))
    s=dict(prefix=PREFIX,userdata=[],templates=[],vms=[])
    root=ok(api,'listUsers',username='admin')['user'][0]['domainid']
    zone=ok(api,'listZones')['zone'][0]['id']
    original=ok(api,'listTemplates',templatefilter='executable',id='e3d8677e-70f0-44bd-917e-6068636b97a8')['template'][0]
    s.update(root_domain_id=root,zoneid=zone)
    save('runtime-state.json',s)
    for suffix,keys in [('A','VAR1'),('B','NEW'),('template','T')]+[('page-'+str(n),'') for n in range(1,12)]:
        row=ok(api,'registerUserData',name=PREFIX+'-'+suffix,userdata=ud.content('#cloud-config\n# '+PREFIX+'-'+suffix+'\n'),**({'params':keys} if keys else {}))['userdata']
        s['userdata'].append(row); save('runtime-state.json',s)
    td=next(r for r in s['userdata'] if r['name'].endswith('-template'))
    for policy in ['ALLOWOVERRIDE','APPEND','DENYOVERRIDE']:
        name=PREFIX+'-'+policy
        result=ok(api,'registerTemplate',name=name,displaytext=name,format='QCOW2',hypervisor='KVM',ostypeid=original['ostypeid'],zoneid=zone,ispublic='false',directdownload='true',requireshvm='true',url='http://10.10.0.203:81/OS-Template/Mold%20Cloud%20Images/Rocky-Linux-9-5-x86-64-Server-Cloud-Image.qcow2')
        template=result['template'][0]
        s['templates'].append(dict(id=template['id'],name=name,policy=policy)); save('runtime-state.json',s)
        ok(api,'linkUserDataToTemplate',templateid=template['id'],userdataid=td['id'],userdatapolicy=policy)
    a=next(r for r in s['userdata'] if r['name'].endswith('-A'))
    for suffix,templateid in [('base',original['id'])]+[(t['policy'],t['id']) for t in s['templates']]:
        params=dict(zoneid=zone,templateid=templateid,serviceofferingid='cd928fb0-6e2b-4ec8-b02b-023aed55b2b1',networkids='ae274fff-3d8e-4285-ac73-26a767c1fea7',name=PREFIX+'-'+suffix,displayname=PREFIX+'-'+suffix,startvm='false')
        if suffix=='base' or suffix=='APPEND':
            params['userdataid']=a['id'];params['userdatadetails[0].VAR1']='현재 한글, 값=1'
        if suffix!='base': params['userdatadetails[1].T']='템플릿 한글'
        vm=api.job('deployVirtualMachine',**params)['virtualmachine']
        assert vm['state']=='Stopped'
        s['vms'].append(dict(id=vm['id'],name=vm['name'],suffix=suffix));save('runtime-state.json',s)
        print(suffix,vm['id'],flush=True)
    save('prepared.json',dict(prefix=PREFIX,userdata_count=len(s['userdata']),templates=s['templates'],vms=s['vms']))

def snapshot(api,label,suffix='base'):
    s=state(); target=next(v for v in s['vms'] if v['suffix']==suffix)
    vm=ok(api,'listVirtualMachines',id=target['id'],listall='true',userdata='true',**target.get('scope',{}))['virtualmachine'][0]
    keys=['id','name','state','userdata','userdataid','userdataname','userdatadetails','userdatapolicy','templateid']
    result={k:vm.get(k) for k in keys}
    save(label+'.json',result)
    print(json.dumps(result,ensure_ascii=False),flush=True)
    return result

def prepare_owners(api):
    s=state()
    if not s.get('domain_id'):
        domain=ok(api,'createDomain',name=PREFIX.lower())['domain']
        s.update(domain_id=domain['id'],accounts=[],networks=[]);save('runtime-state.json',s)
    domain=dict(id=s['domain_id'])
    for suffix,kind in [('user',0),('domainadmin',2)]:
        if any(a['suffix']==suffix for a in s['accounts']):continue
        name=PREFIX.lower()+'-'+suffix
        account=ok(api,'createAccount',username=name,password=os.environ['MOLD_TEST_PASSWORD'],firstname='UD1385',lastname='QA',email=suffix+'@example.invalid',account=name,accounttype=kind,domainid=domain['id'])['account']
        s['accounts'].append(dict(id=account['id'],username=name,suffix=suffix));save('runtime-state.json',s)
    if not s.get('project_id'):
        project=api.job('createProject',name=PREFIX+'-project',displaytext=PREFIX,account=s['accounts'][0]['username'],domainid=domain['id'])['project']
        s['project_id']=project['id'];save('runtime-state.json',s)
    project=dict(id=s['project_id'])
    for suffix,scope in [(a['suffix'],dict(account=a['username'],domainid=domain['id'])) for a in s['accounts']]+[('project',dict(projectid=project['id']))]:
        network=ok(api,'createNetwork',name=PREFIX+'-'+suffix,displaytext=PREFIX,networkofferingid='bc740904-060d-4e13-a850-3609e588116a',zoneid=s['zoneid'],vlan={'user':3991,'domainadmin':3992,'project':3993}[suffix],physicalnetworkid='51cdd7ed-ab33-419d-98fc-8e7eabea1194',**scope)['network']
        s['networks'].append(dict(id=network['id'],scope=scope));save('runtime-state.json',s)
        data=ok(api,'registerUserData',name=PREFIX+'-'+suffix,userdata=ud.content('#cloud-config\n# '+PREFIX+'-'+suffix+'\n'),params='VAR1',**scope)['userdata']
        s['userdata'].append(data);save('runtime-state.json',s)
        params=dict(zoneid=s['zoneid'],templateid='e3d8677e-70f0-44bd-917e-6068636b97a8',serviceofferingid='cd928fb0-6e2b-4ec8-b02b-023aed55b2b1',networkids=network['id'],name=PREFIX+'-'+suffix,displayname=PREFIX+'-'+suffix,startvm='false',userdataid=data['id'],**scope)
        params['userdatadetails[0].VAR1']='소유자 한글'
        vm=api.job('deployVirtualMachine',**params)['virtualmachine']
        s['vms'].append(dict(id=vm['id'],name=vm['name'],suffix=suffix,scope=scope));save('runtime-state.json',s)
        print(suffix,vm['id'],flush=True)
    save('owner-fixtures.json',dict(accounts=s['accounts'],project_id=s['project_id'],domain_id=s['domain_id'],vms=[v for v in s['vms'] if v.get('scope')]))

def cleanup(api):
    s=state()
    scopes=[{}]+([dict(projectid=s['project_id'])] if s.get('project_id') else [])
    for scope in scopes:
        for vm in ok(api,'listVirtualMachines',keyword=PREFIX,listall='true',**scope).get('virtualmachine',[]):
            assert vm['name'].startswith(PREFIX+'-');api.job('destroyVirtualMachine',id=vm['id'],expunge='true')
    for t in s['templates']:
        rows=ok(api,'listTemplates',templatefilter='all',id=t['id']).get('template',[])
        if rows:
            ok(api,'linkUserDataToTemplate',templateid=t['id'])
            api.job('deleteTemplate',id=t['id'],zoneid=s['zoneid'])
    for scope in scopes:
        for data in ok(api,'listUserData',listall='true',page=1,pagesize=100,**scope).get('userdata',[]):
            if data['name'].startswith(PREFIX+'-'): ok(api,'deleteUserData',id=data['id'])
    for n in s.get('networks',[]):api.job('deleteNetwork',id=n['id'])
    if s.get('project_id'):api.job('deleteProject',id=s['project_id'])
    for a in s.get('accounts',[]):api.job('deleteAccount',id=a['id'])
    for id in s.get('roles',[]):ok(api,'deleteRole',id=id)
    if s.get('domain_id'):api.job('deleteDomain',id=s['domain_id'])
    remaining=ud.inventory(api)
    assert remaining==json.loads((ROOT/'baseline.private.json').read_text(encoding='utf-8'))['userdata']
    result=dict(userdata_residuals=sum(r['name'].startswith(PREFIX) for r in remaining),vm_residuals=len(ok(api,'listVirtualMachines',keyword=PREFIX,listall='true').get('virtualmachine',[])),template_residuals=sum(t['name'].startswith(PREFIX) for t in ok(api,'listTemplates',templatefilter='all',keyword=PREFIX).get('template',[])),role_residuals=sum(r['name'].startswith(PREFIX) for r in ok(api,'listRoles').get('role',[])),original_userdata_exact_match=True)
    assert all(v==0 for k,v in result.items() if k.endswith('residuals'))
    save('cleanup-summary.json',result);s['cleaned']=True;save('runtime-state.json',s);print(json.dumps(result))

def enable_reset_roles(api):
    s=state();assert not s.get('roles'), 'Roles already prepared'
    s['roles']=[]
    results=[]
    for account,kind in zip(s['accounts'],['User','DomainAdmin']):
        caller=ud.Mold(account['username'],domain='/'+PREFIX.lower()+'/')
        vm=next(v for v in s['vms'] if v['suffix']==account['suffix'])
        denied=caller.call('resetUserDataForVirtualMachine',id=vm['id'],userdata='')
        assert denied.get('errorcode')==432
        role=ok(api,'createRole',name=PREFIX+'-'+kind+'-reset',type=kind,ispublic='false')['role']
        s['roles'].append(role['id']);save('runtime-state.json',s)
        for rule in ['list*','get*','query*','resetUserDataForVirtualMachine','logout']:ok(api,'createRolePermission',roleid=role['id'],rule=rule,permission='allow')
        ok(api,'createRolePermission',roleid=role['id'],rule='*',permission='deny')
        ok(api,'updateAccount',id=account['id'],roleid=role['id'])
        results.append(dict(type=kind,default_errorcode=432,test_role_id=role['id']))
    save('role-fixtures.json',results)

def final_smoke_prepare(api):
    s=state();assert s.get('cleaned') and not s.get('final_smoke')
    data=ok(api,'registerUserData',name=PREFIX+'-final-smoke',userdata=ud.content('#cloud-config\n# '+PREFIX+'-final-smoke\n'),params='VAR1')['userdata']
    s['final_smoke']=dict(userdata_id=data['id']);save('runtime-state.json',s)
    params=dict(zoneid=s['zoneid'],templateid='e3d8677e-70f0-44bd-917e-6068636b97a8',serviceofferingid='cd928fb0-6e2b-4ec8-b02b-023aed55b2b1',networkids='ae274fff-3d8e-4285-ac73-26a767c1fea7',name=PREFIX+'-final-smoke',displayname=PREFIX+'-final-smoke',startvm='false',userdataid=data['id'])
    params['userdatadetails[0].VAR1']='마지막 한글, 값=4'
    vm=api.job('deployVirtualMachine',**params)['virtualmachine'];s['final_smoke']['vm_id']=vm['id'];save('runtime-state.json',s)
    save('final-smoke-fixture.json',s['final_smoke']);print(json.dumps(s['final_smoke']))

def final_smoke_cleanup(api):
    s=state();target=s['final_smoke']
    api.job('destroyVirtualMachine',id=target['vm_id'],expunge='true')
    ok(api,'deleteUserData',id=target['userdata_id'])
    remaining=ud.inventory(api)
    assert remaining==json.loads((ROOT/'baseline.private.json').read_text(encoding='utf-8'))['userdata']
    save('cleanup-final.json',dict(test_vm_residuals=len(ok(api,'listVirtualMachines',keyword=PREFIX,listall='true').get('virtualmachine',[])),test_userdata_residuals=sum(r['name'].startswith(PREFIX) for r in remaining),original_userdata_exact_match=True,original_userdata_count=len(remaining)))
    target['cleaned']=True;save('runtime-state.json',s);print('Final smoke resources cleaned')

def final_smoke_snapshot(api,label):
    vm=ok(api,'listVirtualMachines',id=state()['final_smoke']['vm_id'],userdata='true')['virtualmachine'][0]
    data={k:vm.get(k) for k in ['id','name','state','userdata','userdataid','userdataname','userdatadetails','templateid']}
    assert data['state']=='Stopped';save(label+'.json',data);print(json.dumps(data,ensure_ascii=False))

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    api=ud.Mold()
    if sys.argv[1]=='prepare':prepare(api)
    elif sys.argv[1]=='prepare-owners':prepare_owners(api)
    elif sys.argv[1]=='enable-reset-roles':enable_reset_roles(api)
    elif sys.argv[1]=='final-smoke-prepare':final_smoke_prepare(api)
    elif sys.argv[1]=='final-smoke-cleanup':final_smoke_cleanup(api)
    elif sys.argv[1]=='snapshot-final':final_smoke_snapshot(api,sys.argv[2])
    elif sys.argv[1]=='snapshot':snapshot(api,sys.argv[2],sys.argv[3] if len(sys.argv)>3 else 'base')
    elif sys.argv[1]=='cleanup':cleanup(api)

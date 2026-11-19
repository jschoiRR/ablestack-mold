from qa import *
import hashlib
out=json.loads(pathlib.Path('cleanup-actions.json').read_text()) if pathlib.Path('cleanup-actions.json').exists() else []
def done(cmd,**kw):
 r=wait(api(cmd,**kw));out.append(dict(command=cmd,params=kw,result=r));pathlib.Path('cleanup-actions.json').write_text(json.dumps(out,indent=2));print(cmd,next(iter(kw.values())),flush=True)
for v in f['vms']:
 assert v['name'].startswith('qa-ag07-')
 r=api('listVirtualMachines',id=v['id'],listall=True).get('virtualmachine',[])
 if r:done('destroyVirtualMachine',id=v['id'],expunge=True)
existingGroups={g['id'] for g in api('listAffinityGroups',listall=True,projectid='-1',keyword='qa-ag07-',page=1,pagesize=500).get('affinitygroup',[])}
for g in f['groups'].values():
 assert g['name'].startswith('qa-ag07-')
 if g['id'] in existingGroups:done('deleteAffinityGroup',id=g['id'])
for n in f['networks'].values():
 assert n['name'].startswith('qa-ag07-')
 if api('listNetworks',id=n['id'],listall=True,**(dict(projectid=f['project']['id']) if n['id']==f['networks']['project']['id'] else {})).get('network'):done('deleteNetwork',id=n['id'])
if api('listProjects',id=f['project']['id'],listall=True).get('project'):done('deleteProject',id=f['project']['id'])
for a in f['accounts'].values():
 assert a['name'].startswith('qa-ag07-')
 if api('listAccounts',id=a['id'],listall=True).get('account'):done('deleteAccount',id=a['id'])
if api('listRoles',id=f['role']['id']).get('role'):done('deleteRole',id=f['role']['id'])
for key in ['child','peer','parent']:
 d=f['domains'][key];assert d['name'].startswith('qa-ag07-')
 if api('listDomains',id=d['id'],listall=True).get('domain'):done('deleteDomain',id=d['id'],cleanup=True)
b=json.loads(pathlib.Path('baseline.json').read_text());a=dict(groups=api('listAffinityGroups',listall=True,projectid='-1',page=1,pagesize=500),vms=api('listVirtualMachines',listall=True,projectid='-1',page=1,pagesize=500,details='min,affgrp'))
def project(x):
 return dict(groups=sorted([dict(id=g['id'],name=g['name'],type=g['type'],vm=sorted(g.get('virtualmachineIds',[]))) for g in x['groups'].get('affinitygroup',[])],key=lambda z:z['id']),vms=sorted([dict(id=v['id'],name=v['name'],groups=sorted(g['id'] for g in v.get('affinitygroup',[]))) for v in x['vms'].get('virtualmachine',[])],key=lambda z:z['id']))
bp,ap=project(b),project(a);assert bp==ap, 'operational affinity projection mismatch'
sha=lambda x:hashlib.sha256(json.dumps(x,sort_keys=True).encode()).hexdigest()
result=dict(groups=len(ap['groups']),vms=len(ap['vms']),before=sha(bp),after=sha(ap),unchanged=True,actions=len(out))
pathlib.Path('cleanup-baseline.json').write_text(json.dumps(result,indent=2));print(json.dumps(result),flush=True)

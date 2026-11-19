from qa import *
import sys
phase=sys.argv[1]
if phase=='bootstrap':
 if not pathlib.Path('baseline.json').exists():
  b=dict(groups=api('listAffinityGroups',listall=True,projectid='-1',page=1,pagesize=500),vms=api('listVirtualMachines',listall=True,projectid='-1',page=1,pagesize=500,details='min,affgrp'))
  assert not any(g['name'].startswith('qa-ag07-') for g in b['groups'].get('affinitygroup',[]))
  assert not any(v['name'].startswith('qa-ag07-') for v in b['vms'].get('virtualmachine',[]))
  pathlib.Path('baseline.json').write_text(json.dumps(b,ensure_ascii=False,indent=2));print('Baseline',len(b['groups'].get('affinitygroup',[])),len(b['vms'].get('virtualmachine',[])),flush=True)
 parent=domain('parent',root)
 for key,typ in [('user-a',0),('user-b',0),('domainadmin',2)]:account(key,'parent',typ);group(key,account='qa-ag07-'+key,domainid=parent)
 group('shared',typ='ExplicitDedication',domainid=parent)
 cases={}
 for who in ['domainadmin','user-a']:
  with role(who):
   cases[who]=[]
   for page,size in [(1,500),(1,2),(2,2),(3,2),(1,1),(2,1)]:
    r=api('listAffinityGroups',listall=True,page=page,pagesize=size);rows=r.get('affinitygroup',[])
    cases[who].append(dict(page=page,pagesize=size,count=r.get('count',0),rows=len(rows),ids=[g['id'] for g in rows],names=[g['name'] for g in rows]))
 assert cases['domainadmin'][0]['count']==5 and cases['domainadmin'][0]['rows']==4,cases
 assert cases['domainadmin'][3]['count']==5 and cases['domainadmin'][3]['rows']==0
 assert cases['user-a'][4]['rows']>1,cases['user-a'][4]
 pathlib.Path('reproduce-original.json').write_text(json.dumps(cases,ensure_ascii=False,indent=2));print('Reproduced domain-admin count=5/unique=4 and normal-user page-size overflow',flush=True)
elif phase=='expand':
 parent=f['domains']['parent']['id'];child=domain('child',parent);peer=domain('peer',root)
 account('child','child');account('peer','peer')
 group('child',account='qa-ag07-child',domainid=child)
 group('peer',account='qa-ag07-peer',domainid=peer)
 group('peer-shared',typ='ExplicitDedication',domainid=peer)
 for n in range(1,19):group('page-%02d'%n,account='qa-ag07-domainadmin',domainid=parent)
 if 'role' not in f:
  f['role']=api('createRole',name='qa-ag07-readonly',type='User',description='AG07 disposable list-only qualification role')['role'];save()
  for rule in ['list*','queryAsyncJobResult']:api('createRolePermission',roleid=f['role']['id'],rule=rule,permission='allow')
 account('restricted','parent',roleid=f['role']['id']);group('restricted',account='qa-ag07-restricted',domainid=parent)
 if 'project' not in f:f['project']=wait(api('createProject',name='qa-ag07-project',displaytext='AG07 disposable query qualification',account='qa-ag07-user-a',domainid=parent))['project'];save()
 group('project',projectid=f['project']['id'])
 for key,owner,vlan in [('user',dict(account='qa-ag07-user-a',domainid=parent),3195),('project',dict(projectid=f['project']['id']),3196)]:
  if key not in f['networks']:f['networks'][key]=api('createNetwork',name='qa-ag07-'+key+'-L2',displaytext='AG07 disposable isolated ownership network',networkofferingid='79ec5c18-b71c-4f7a-9b5d-8694b16b8a54',zoneid='7013f5a1-de59-4cea-a596-f4f58815f31c',vlan=vlan,**owner)['network'];save()
  targets=['user-vm-01','user-vm-02'] if key=='user' else ['project-vm']
  for target in targets:
   if any(v['name']=='qa-ag07-'+target for v in f['vms']):continue
   g=f['groups']['user-a' if key=='user' else 'project']
   v=wait(api('deployVirtualMachine',name='qa-ag07-'+target,displayname='qa-ag07-'+target,zoneid='7013f5a1-de59-4cea-a596-f4f58815f31c',templateid='e3d8677e-70f0-44bd-917e-6068636b97a8',serviceofferingid='39c3aa33-5632-4dea-bd84-ba794d292c87',networkids=f['networks'][key]['id'],startvm=False,affinitygroupids=g['id'],**owner))['virtualmachine'];f['vms'].append(v);save();print('VM',v['name'],v['id'],flush=True)
 for key,typ in [('strict-anti','host anti-affinity'),('prefer-aff','non-strict host affinity'),('prefer-anti','non-strict host anti-affinity')]:group(key,typ=typ,account='qa-ag07-user-a',domainid=parent)
 print('Expanded',len(f['groups']),'groups',len(f['vms']),'VMs',flush=True)
else:raise ValueError(phase)

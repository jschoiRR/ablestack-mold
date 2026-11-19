from qa import *
import sys
phase=sys.argv[1]
if phase=='checkpoint':
 rows=[]
 for v in f['vms']:
  r=api('listVirtualMachines',id=v['id'],listall=True,details='min,affgrp')['virtualmachine'][0]
  rows.append(dict(id=r['id'],name=r['name'],state=r['state'],groups=sorted(g['id'] for g in r.get('affinitygroup',[]))))
 pathlib.Path('ui-mapping-'+sys.argv[2]+'.json').write_text(json.dumps(rows,indent=2)); print(json.dumps(rows,indent=2))
elif phase=='negative':
 results=[]
 with role('restricted'):
  try:api('createAffinityGroup',name='qa-ag07-denied',type='host affinity')
  except RuntimeError as e:assert '432' in str(e);results.append(dict(case='readonly-create-denied',errorcode=432))
  else:raise AssertionError('unexpected mutation permission')
 with role('user-a'):
  r=api('listAffinityGroups',id=f['groups']['peer']['id'],listall=True);assert not r.get('affinitygroup');results.append(dict(case='peer-group-hidden',count=r.get('count',0)))
 pathlib.Path('acl-negative.json').write_text(json.dumps(results,indent=2));print(json.dumps(results))

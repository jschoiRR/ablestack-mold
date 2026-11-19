from qa import *
records=[]
for vm in f['vms']:
 own=f['groups']['project' if vm.get('projectid') else 'user-a']['id'];ids=[own,f['groups']['shared']['id']]
 receipt=api('updateVMAffinityGroup',id=vm['id'],affinitygroupids=','.join(ids));result=wait(receipt)
 current=api('listVirtualMachines',id=vm['id'],details='all')['virtualmachine'][0]
 assert current['state']=='Stopped' and {g['id'] for g in current.get('affinitygroup',[])}==set(ids)
 records.append(dict(vm=vm['id'],name=vm['name'],groups=ids,job=receipt.get('jobid'),finalStatus=1))
pathlib.Path('shared-mappings-before.json').write_text(json.dumps(records,indent=2))
r=api('listAffinityGroups',id=f['groups']['shared']['id'],listall=True,projectid='-1')['affinitygroup'][0]
assert set(r['virtualmachineIds'])=={v['id'] for v in f['vms']}
print('Three stopped VMs retain their owner group and share one domain group')

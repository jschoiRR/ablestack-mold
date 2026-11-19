from qa import *
import sys,math
phase=sys.argv[1]
base=dict(listall=True,keyword='qa-ag07-')
cases=[('root-all','admin',dict(projectid='-1')),('root-parent','admin',dict(domainid=f['domains']['parent']['id'],isrecursive=True)),('root-owner','admin',dict(account='qa-ag07-user-a',domainid=f['domains']['parent']['id'])),('root-project','admin',dict(projectid=f['project']['id'])),('domainadmin','domainadmin',{}),('domainadmin-projects','domainadmin',dict(projectid='-1')),('domainadmin-nonrecursive','domainadmin',dict(domainid=f['domains']['parent']['id'],isrecursive=False)),('normal','user-a',{}),('normal-project','user-a',dict(projectid=f['project']['id'])),('other-owner','user-b',{}),('child','child',{}),('restricted','restricted',{}),('peer','peer',{})]
def listing(args,**kw):
 r=api('listAffinityGroups',**{**base,**args,**kw});rows=r.get('affinitygroup',[])
 return dict(count=r.get('count',0),rows=[{k:g.get(k) for k in ['id','name','type','account','domainid','projectid','virtualmachineIds']} for g in rows])
if phase=='before':
 out={}
 for name,who,args in cases:
  with role(who):out[name]=dict(actor=who,args=args,**listing(args,page=1,pagesize=500))
 ids={g['id'] for g in out['root-all']['rows']};assert ids=={g['id'] for g in f['groups'].values()}
 for name in ['normal','other-owner','child','restricted']:
  names={g['name'] for g in out[name]['rows']};assert 'qa-ag07-shared' in names and 'qa-ag07-peer' not in names
  assert all(g['domainid'] in [f['domains']['parent']['id'],f['domains']['child']['id']] for g in out[name]['rows'])
 assert all(g['domainid']!=f['domains']['peer']['id'] for g in out['domainadmin']['rows'])
 pathlib.Path('query-before.json').write_text(json.dumps(out,ensure_ascii=False,indent=2));print([(k,v['count'],len(v['rows'])) for k,v in out.items()],flush=True)
elif phase=='after':
 before=json.load(open('query-before.json'));results=[];order=json.load(open('group-order.json'))
 for name,who,args in cases:
  oracle=before[name]['rows'];expected={g['id'] for g in oracle}
  with role(who):
   r=listing(args,page=1,pagesize=500);assert r['count']==len(expected) and {g['id'] for g in r['rows']}==expected,(name,r,len(expected))
   assert {g['id']:set(g.get('virtualmachineIds') or []) for g in r['rows']}=={g['id']:set(g.get('virtualmachineIds') or []) for g in oracle},name
   for size in [1,2,10,20]:
    collected=[];pages=[]
    for page in range(1,math.ceil(len(expected)/size)+2):
     data=listing(args,page=page,pagesize=size);pageids=[g['id'] for g in data['rows']]
     assert data['count']==len(expected) and len(pageids)<=size,(name,size,page,data)
     assert set(pageids).issubset(expected)
     collected+=pageids;pages.append(dict(page=page,ids=pageids,count=data['count']))
    assert len(collected)==len(set(collected))==len(expected) and set(collected)==expected,(name,size,collected)
    assert collected==sorted(expected,key=lambda gid:order[gid]),(name,size,'global order',collected)
    results.append(dict(case=name,actor=who,pagesize=size,count=len(expected),pages=pages,allUniqueAndComplete=True))
   target=oracle[0]
   filters=[dict(id=target['id']),dict(name=target['name']),dict(type='host affinity'),dict(type='host anti-affinity'),dict(type='non-strict host affinity'),dict(type='non-strict host anti-affinity'),dict(type='ExplicitDedication'),dict(keyword='shared'),dict(keyword='page-'),dict(name='qa-ag07-no-such-group'),dict(keyword='qa-ag07-no-such-group'),dict(name=target['name'],type='impossible-policy')]
   for filterspec in filters:
    expectedFiltered={g['id'] for g in oracle if all((g['id']==v if k=='id' else g['name']==v if k=='name' else g['type']==v if k=='type' else any(v.lower() in str(g.get(p) or '').lower() for p in ['name','type','id'])) for k,v in filterspec.items())}
    rr=listing(args,page=1,pagesize=500,**filterspec);assert rr['count']==len(expectedFiltered) and {g['id'] for g in rr['rows']}==expectedFiltered,(name,filterspec,rr,expectedFiltered)
    results.append(dict(case=name,actor=who,filter=filterspec,count=rr['count'],ids=[g['id'] for g in rr['rows']],matchesScopeOracle=True))
  print('PASS',name,len(expected),'groups',flush=True)
 pathlib.Path('query-after.json').write_text(json.dumps(results,ensure_ascii=False,indent=2));print('Verified',len(results),'pagination/filter cases plus full VM-map preservation',flush=True)
else:raise ValueError(phase)

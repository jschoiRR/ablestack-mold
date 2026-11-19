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
"""Verify explicit reset candidates without changing legacy update delivery."""
import fixtures as f
import json
import sys
def prepare(a):
    s=f.state();assert s.get('cleaned') and not s.get('final_guard')
    g=dict(userdata={});s['final_guard']=g;f.save('runtime-state',s)
    zone=f.ok(a,'listZones')['zone'][0]['id']
    g['network']=f.ok(a,'createNetwork',name=f.PREFIX+'-final-guard',displaytext=f.PREFIX+'-final-guard',networkofferingid='46a69931-ebbf-4aee-9d3d-12d03315a949',zoneid=zone,gateway='10.138.7.1',netmask='255.255.255.0')['network'];f.save('runtime-state',s)
    for key in ['A','B']:
        g['userdata'][key]=f.ok(a,'registerUserData',name=f.PREFIX+'-final-guard-'+key,userdata=s['userdata'][key]['userdata'],params='TAG')['userdata'];f.save('runtime-state',s)
    g['vm']=f.job(a,'deployVirtualMachine',zoneid=zone,templateid='8d692729-7565-4e92-965b-ebaadc1efcb0',serviceofferingid='cd928fb0-6e2b-4ec8-b02b-023aed55b2b1',networkids=g['network']['id'],name=f.PREFIX+'-final-guard',displayname=f.PREFIX+'-final-guard',userdataid=g['userdata']['A']['id'],**{'userdatadetails[0].TAG':'guard-A'})['virtualmachine'];f.save('runtime-state',s)
    g['router']=f.ok(a,'listRouters',networkid=g['network']['id'],listall='true')['router'][0];f.save('runtime-state',s)
    f.save('final-guard-fixtures',dict(vm={k:g['vm'].get(k) for k in ['id','name','instancename','state','hostid','hostname','nic']},network_id=g['network']['id'],userdata_ids={k:v['id'] for k,v in g['userdata'].items()},router={k:g['router'].get(k) for k in ['id','name','hostname','hostid','linklocalip','guestipaddress']}))
    print(json.dumps(dict(vm=g['vm']['id'],router=g['router']['name'])))
def snapshot(a,label):
    g=f.state()['final_guard'];v=f.ok(a,'listVirtualMachines',id=g['vm']['id'],userdata='true',listall='true')['virtualmachine'][0]
    v={k:v.get(k) for k in ['id','name','instancename','state','userdata','userdataid','userdatadetails','nic']};f.save(label,v);print(json.dumps(v,ensure_ascii=False));return v
def cleanup(a):
    s=f.state();g=s['final_guard']
    f.job(a,'destroyVirtualMachine',id=g['vm']['id'],expunge='true')
    for v in g['userdata'].values():f.ok(a,'deleteUserData',id=v['id'])
    f.job(a,'deleteNetwork',id=g['network']['id'])
    remaining=f.ud.inventory(a);assert remaining==json.loads((f.ROOT/'baseline.private.json').read_text(encoding='utf-8'))['userdata']
    f.save('cleanup-final-guard',dict(original_library_exact_match=True,original_library_count=len(remaining),vm_residuals=len(f.ok(a,'listVirtualMachines',keyword=f.PREFIX,listall='true').get('virtualmachine',[])),network_residuals=len(f.ok(a,'listNetworks',keyword=f.PREFIX,listall='true').get('network',[]))))
    g['cleaned']=True;f.save('runtime-state',s)
if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');a=f.ud.Mold();mode=sys.argv[1]
    if mode=='prepare':prepare(a)
    elif mode=='stop':f.job(a,'stopVirtualMachine',id=f.state()['final_guard']['vm']['id'])
    elif mode=='boot':f.job(a,'startVirtualMachine',id=f.state()['final_guard']['vm']['id'])
    elif mode=='snapshot':snapshot(a,sys.argv[2])
    elif mode=='legacy':
        g=f.state()['final_guard'];r=f.ok(a,'updateVirtualMachine',id=g['vm']['id'],userdata=g['userdata']['A']['userdata'],**{'userdatadetails[0].TAG':'guard-legacy'});f.save('final-guard-legacy-response',r);snapshot(a,'final-guard-legacy')
    elif mode=='reset':
        g=f.state()['final_guard'];r=f.ok(a,'resetUserDataForVirtualMachine',id=g['vm']['id'],userdataid=g['userdata']['B']['id'],**{'userdatadetails[0].TAG':'guard-B'});f.save('final-guard-reset-response',r);snapshot(a,'final-guard-reset')
    elif mode=='cleanup':cleanup(a)

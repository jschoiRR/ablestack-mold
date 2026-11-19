# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
"""Minimal disposable presentation regression fixture after removal of helper prose."""
import base64,json,sys
import fixtures as f

def prepare(a):
    assert not (f.ROOT/'standard-fixture.json').exists()
    s={'library':json.loads((f.ROOT/'comment-fixture.json').read_text())['userdata_id']}
    f.save('standard-fixture',s)
    s['second']=f.ok(a,'registerUserData',name=f.PREFIX+'-standard-second',userdata=f.ud.content('#cloud-config\n# standard second\n'))['userdata']['id'];f.save('standard-fixture',s)
    zone=f.ok(a,'listZones')['zone'][0]['id']
    original=f.ok(a,'listTemplates',id='8d692729-7565-4e92-965b-ebaadc1efcb0',templatefilter='all')['template'][0]
    iso=next(x for x in f.ok(a,'listIsos',isofilter='all',showunique='true',page=1,pagesize=500)['iso'] if x.get('url') and x.get('isready'))
    for kind,api,source in [('template','registerTemplate',original),('iso','registerIso',iso)]:
        name=f.PREFIX+'-standard-'+kind
        params=dict(name=name,displaytext=name,url=source['url'],zoneid=zone,ispublic='false',directdownload='true',ostypeid=source['ostypeid'])
        params.update(dict(format='QCOW2',hypervisor='KVM',requireshvm='true') if kind=='template' else dict(bootable='true'))
        s[kind]=f.ok(a,api,**params)[kind][0]['id'];f.save('standard-fixture',s)
        f.ok(a,'linkUserDataToTemplate',templateid=s[kind],userdataid=s['library'],userdatapolicy='ALLOWOVERRIDE')
    s['network']=f.ok(a,'createNetwork',name=f.PREFIX+'-standard-cd',displaytext=f.PREFIX,networkofferingid='bc740904-060d-4e13-a850-3609e588116a',zoneid=zone,vlan=3984)['network']['id'];f.save('standard-fixture',s)
    name=f.PREFIX+'-standard-vm'
    s['vm']=f.job(a,'deployVirtualMachine',zoneid=zone,templateid=original['id'],serviceofferingid='cd928fb0-6e2b-4ec8-b02b-023aed55b2b1',networkids=s['network'],name=name,displayname=name,startvm='false',userdataid=s['library'])['virtualmachine']['id'];f.save('standard-fixture',s)
    source=f.ok(a,'listUserData',id=s['second'],listall='true')['userdata'][0]
    target=f.ok(a,'listUserData',id=s['library'],listall='true')['userdata'][0]
    record='MOLD_USERDATA_VERSION/1\n'+json.dumps(dict(sourceid=source['id'],targetid=target['id'],before=len(base64.b64decode(source['userdata'])),after=len(base64.b64decode(target['userdata']))),separators=(',',':'))
    s['note']=f.ok(a,'addAnnotation',entitytype='USER_DATA',entityid=s['library'],annotation=record,adminsonly='false')['annotation']['id'];f.save('standard-fixture',s)
    print('Prepared minimal final UI regression fixture',flush=True)

def cleanup(a):
    s=json.loads((f.ROOT/'standard-fixture.json').read_text())
    f.ok(a,'removeAnnotation',id=s['note'])
    f.job(a,'destroyVirtualMachine',id=s['vm'],expunge='true')
    for kind,api in [('template','deleteTemplate'),('iso','deleteIso')]:
        f.ok(a,'linkUserDataToTemplate',templateid=s[kind]);f.job(a,api,id=s[kind])
    f.job(a,'deleteNetwork',id=s['network'])
    f.ok(a,'deleteUserData',id=s['second'])
    f.save('standard-cleanup',dict(vm=True,network=True,template=True,iso=True,second_library=True,version_note=True))
    print('Minimal final UI regression fixture cleaned',flush=True)

def verify(a):
    s=json.loads((f.ROOT/'standard-fixture.json').read_text())
    vm=f.ok(a,'listVirtualMachines',id=s['vm'],listall='true')['virtualmachine'][0]
    assert vm['state']=='Stopped' and vm['userdataid']==s['library']
    for kind,api,key,filterkey in [('template','listTemplates','template','templatefilter'),('iso','listIsos','iso','isofilter')]:
        row=f.ok(a,api,id=s[kind],**{filterkey:'all'})[key][0]
        assert row['userdataid']==s['library'] and row['userdatapolicy']=='ALLOWOVERRIDE'
    notes=f.ok(a,'listAnnotations',entityid=s['library'],entitytype='USER_DATA',annotationfilter='all')['annotation']
    assert len(notes)==3 and any(x.get('adminsonly') for x in notes)
    f.save('standard-api-check',dict(vm_state='Stopped',vm_library_matches=True,template_library_matches=True,iso_library_matches=True,annotations=len(notes),private_annotation_present=True))
    print('Final presentation fixtures agree with API',flush=True)

if __name__=='__main__':globals()[sys.argv[1]](f.ud.Mold())

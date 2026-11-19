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
"""Dedicated UD-11 data and role qualification. No operating resource is mutated."""
import base64,sys
import fixtures as f

def seed_pages(a):
    for i in range(8,15):
        f.ok(a,'registerUserData',name=f.PREFIX+'-seed-'+str(i),userdata=f.ud.content('#cloud-config\n# pagination '+str(i)+'\n'))
    f.save('extra-page-seeds',dict(created=7))

def roles(a):
    s=f.state(); result=[]
    for acc in s['accounts'][:2]:
        client=f.ud.Mold(username=acc['username'],domain='/'+f.PREFIX.lower())
        rows=f.ok(client,'listUserData',listall='true',page=1,pagesize=500).get('userdata',[])
        assert rows and all(x['domainid']==s['domain_id'] for x in rows)
        assert not any(x['id']==s['userdata']['A']['id'] for x in rows)
        own=s['userdata'][acc['suffix']]['id']
        assert any(x['id']==own for x in rows)
        result.append(dict(role=acc['suffix'],library_ids=[x['id'] for x in rows],root_library_hidden=True))
    project=f.ok(a,'listUserData',projectid=s['project_id'],listall='true',page=1,pagesize=500).get('userdata',[])
    assert [x['id'] for x in project]==[s['userdata']['project']['id']]
    result.append(dict(role='project',library_ids=[x['id'] for x in project],scope_exact=True))
    f.save('api-final-roles',result)

def verify(a):
    import api_snapshot
    s=f.state(); r=api_snapshot.snapshot(a); f.save('api-final',r)
    created=__import__('json').loads((f.ROOT/'final-created-identities.json').read_text(encoding='utf-8'))
    vm=next(x for x in r['vms'] if x['displayname']==f.PREFIX+'-ui-vm')
    assert vm['state']=='Stopped' and vm['body_bytes']==0 and vm['userdataid'] is None,vm
    assert sum(x['userdataid']==s['userdata']['A']['id'] for x in r['vms'])==11
    assert len([x for x in r['userdata'] if x['id']==created['version']])==1
    annotations=f.ok(a,'listAnnotations',entityid=created['version'],entitytype='USER_DATA',annotationfilter='all').get('annotation',[])
    assert len(annotations)==1,annotations
    f.save('api-final-assertions',dict(vm_ui_create_then_unlink=True,usage_A=11,clone_objects=1,clone_source_notes=1,vm_count=len(r['vms'])))

if __name__=='__main__':
    globals()[sys.argv[1]](f.ud.Mold());print(sys.argv[1], 'PASS')

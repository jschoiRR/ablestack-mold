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
"""Small post-cleanup fixture for the final scoped comment-colour correction."""
import json,sys
import fixtures as f

def prepare(a):
    item=f.ok(a,'registerUserData',name=f.PREFIX+'-final-comments',userdata=f.ud.content('#cloud-config\n# final comment contrast\n'))['userdata']
    state=dict(userdata_id=item['id'],notes=[])
    f.save('comment-fixture',state)
    for private in [False,True]:
        note=f.ok(a,'addAnnotation',entitytype='USER_DATA',entityid=item['id'],annotation=f.PREFIX+(' private note' if private else ' public note'),adminsonly=str(private).lower())['annotation']
        state['notes'].append(note['id']);f.save('comment-fixture',state)
    print('Prepared one library and two notes')

def cleanup(a):
    state=json.loads((f.ROOT/'comment-fixture.json').read_text(encoding='utf-8'))
    for note in state['notes']:f.ok(a,'removeAnnotation',id=note)
    f.ok(a,'deleteUserData',id=state['userdata_id'])
    assert f.ud.inventory(a)==json.loads((f.ROOT/'baseline.private.json').read_text(encoding='utf-8'))['userdata']
    f.save('comment-cleanup',dict(original_userdata_exact_match=True,library_deleted=True,notes_deleted=len(state['notes'])))
    print('Final comment fixture cleaned')

if __name__=='__main__':globals()[sys.argv[1]](f.ud.Mold())

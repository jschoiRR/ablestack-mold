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
"""Verify actual UI registrations, immutable originals and bounded source-note schema."""
import fixtures as f
import json,base64,hashlib
s=f.state();a=f.ud.Mold();results=[]
cases={'admin-v2':'admin','admin-v3':'admin','binary-v2':'binary','user-v2':'user-vars','domainadmin-v2':'domainadmin','project-v2':'project'}
for suffix,key in cases.items():
 source=s['userdata'][key];scope=dict(projectid=source['projectid']) if source.get('projectid') else dict(account=source['account'],domainid=source['domainid'])
 rows=f.ok(a,'listUserData',name=f.PREFIX+'-'+suffix,listall='true',page=1,pagesize=500,**scope).get('userdata',[])
 rows=[x for x in rows if x['name']==f.PREFIX+'-'+suffix];assert len(rows)==1,(suffix,rows)
 target=rows[0];assert target['id']!=source['id'] and target['userdata']!=source['userdata']
 assert target.get('projectid')==source.get('projectid') and target['domainid']==source['domainid'] and target.get('account')==source.get('account')
 notes=f.ok(a,'listAnnotations',entityid=target['id'],entitytype='USER_DATA',annotationfilter='all',page=1,pagesize=500,**(dict(projectid=source['projectid']) if source.get('projectid') else {})).get('annotation',[])
 metadata=[n for n in notes if n['annotation'].startswith('MOLD_USERDATA_VERSION/1\n')];assert len(metadata)==1,(suffix,notes)
 note=metadata[0]['annotation'];record=json.loads(note.split('\n',1)[1]);assert len(note)<=255
 assert record['sourceid']==source['id'] and record['targetid']==target['id']
 assert record['before']==len(base64.b64decode(source['userdata'])) and record['after']==len(base64.b64decode(target['userdata']))
 if suffix=='user-v2':assert target['params']=='SOURCE_VAR,NEW_VAR',target
 results.append(dict(case=suffix,id=target['id'],name=target['name'],account=target.get('account'),domainid=target['domainid'],projectid=target.get('projectid'),params=target.get('params'),bytes=record['after'],sha256=hashlib.sha256(base64.b64decode(target['userdata'])).hexdigest(),note_length=len(note),metadata=record,notes=metadata))
originals=[]
for key,source in s['userdata'].items():
 if key=='delete-source':continue
 scope=dict(projectid=source['projectid']) if source.get('projectid') else dict(account=source['account'],domainid=source['domainid'])
 row=f.ok(a,'listUserData',id=source['id'],listall='true',**scope)['userdata'][0];assert row==source,(key,row)
 originals.append(dict(key=key,id=source['id'],exact_response_preserved=True))
f.save('clones-final',dict(clones=results,originals=originals));print(len(results),'UI-created immutable versions and source notes verified')

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
"""Record only dedicated UD-11 test resources and hashes of their data."""
import fixtures as f
import base64,hashlib,sys

def snapshot(a):
 s=f.state();scopes=[{},dict(projectid=s['project_id'])];result=dict(userdata=[],vms=[],templates=[],isos=[])
 for scope in scopes:
  result['userdata'] += [x for x in f.ok(a,'listUserData',listall='true',page=1,pagesize=500,**scope).get('userdata',[]) if x['name'].startswith(f.PREFIX)]
  rows=f.ok(a,'listVirtualMachines',listall='true',keyword=f.PREFIX,page=1,pagesize=500,**scope).get('virtualmachine',[])
  for x in rows:
   raw=f.ok(a,'getVirtualMachineUserData',virtualmachineid=x['id'])
   encoded=raw.get('virtualmachineuserdata',raw).get('userdata','')
   x={k:x.get(k) for k in ['id','name','displayname','state','userdataid','userdataname','userdatapolicy','userdatadetails','account','domainid','projectid']}
   x['body_bytes']=len(base64.b64decode(encoded));x['body_sha256']=hashlib.sha256(base64.b64decode(encoded)).hexdigest();result['vms'].append(x)
 for kind,api,key,filterkey in [('templates','listTemplates','template','templatefilter'),('isos','listIsos','iso','isofilter')]:
  for id in s[kind]:
   result[kind]+=f.ok(a,api,id=id,**{filterkey:'all'}).get(key,[])
 return result
if __name__=='__main__':f.save(sys.argv[1],snapshot(f.ud.Mold()));print('Dedicated API snapshot:',sys.argv[1])

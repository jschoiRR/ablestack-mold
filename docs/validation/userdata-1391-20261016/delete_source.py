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
"""Remove only the dedicated, unreferenced source after its clone dialog is open."""
import fixtures as f
s=f.state();a=f.ud.Mold();item=s['userdata']['delete-source'];assert item['name']==f.PREFIX+'-delete-source'
current=f.ok(a,'listUserData',id=item['id'],account=item['account'],domainid=item['domainid'],listall='true')['userdata'][0];assert current==item
response=f.ok(a,'deleteUserData',id=item['id'],account=item['account'],domainid=item['domainid']);assert response.get('success')
gone=a.call('listUserData',id=item['id'],account=item['account'],domainid=item['domainid'],listall='true');assert not gone.get('count',0)
f.save('source-deletion',dict(id=item['id'],response=response,gone=gone));print('Dedicated unreferenced clone source removed')

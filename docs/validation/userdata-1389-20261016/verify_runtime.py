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
"""Verify deployed static assets and preserve the existing backend and webapp."""
from pathlib import Path
import hashlib,json,subprocess,sys,urllib.request,zipfile
stage=Path(sys.argv[1]);m=json.loads((stage/'manifest.json').read_text());d=json.loads((stage/'deployment.json').read_text())
assert m['issue']==d['issue']==1389 and m['cluster']==31
web=Path('/usr/share/cloudstack-management/webapp');jar=Path('/usr/share/cloudstack-management/lib/cloudstack-4.23.0.0-ABLESTACK.Mold.jar')
sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(jar.read_bytes())==d['jar_sha256']
with zipfile.ZipFile(jar) as z:assert all(sha(z.read(name))==value for name,value in {**m.get('preserved_classes',{}),**m['classes']}.items())
assert all(sha((web/name).read_bytes())==value for name,value in m['ui'].items())
assert (web/'WEB-INF').stat().st_ino==d['before']['web_inf_inode']
assert (web/'META-INF').exists()==d['before']['meta_inf_present']
assert sha((web/'config.json').read_bytes())==d['before']['config_sha256']
pid=subprocess.check_output(['systemctl','show','mold','-p','MainPID','--value'],text=True).strip()
assert subprocess.check_output(['systemctl','is-active','mold'],text=True).strip()=='active'
assert urllib.request.urlopen('http://127.0.0.1:8080/client/',timeout=10).status==200
print(json.dumps(dict(issue=1389,cluster=31,source=m['source'],static_files_verified=len(m['ui']),classes_verified={**m.get('preserved_classes',{}),**m['classes']},jar_sha256=d['jar_sha256'],app_files={k:v for k,v in m['ui'].items() if k.startswith('js/app.') and k.endswith('.js')},management_pid=pid,web_inf_preserved=True,config_preserved=True,mold='active',client_http=200),indent=2))

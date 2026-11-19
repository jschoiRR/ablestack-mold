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
"""Verify the final server guard and unchanged qualified UI as separate sources."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import urllib.request
import zipfile
server=Path(sys.argv[1]);ui=Path(sys.argv[2])
s=json.loads((server/'manifest.json').read_text());u=json.loads((ui/'manifest.json').read_text());d=json.loads((server/'deployment.json').read_text())
assert s['issue']==u['issue']==1386
sha=lambda data:hashlib.sha256(data).hexdigest()
jar=Path('/usr/share/cloudstack-management/lib/cloudstack-4.23.0.0-ABLESTACK.Mold.jar');web=Path('/usr/share/cloudstack-management/webapp')
assert sha(jar.read_bytes())==d['jar_sha256']
classes={**s['preserved_classes'],**s['classes']}
with zipfile.ZipFile(jar) as z:assert all(sha(z.read(k))==v for k,v in classes.items())
assert all(sha((web/k).read_bytes())==v for k,v in u['ui'].items())
assert (web/'WEB-INF').stat().st_ino==d['before']['web_inf_inode']
assert sha((web/'config.json').read_bytes())==d['before']['config_sha256']
assert (web/'META-INF').exists()==d['before']['meta_inf_present']
assert subprocess.check_output(['systemctl','is-active','mold'],text=True).strip()=='active'
assert urllib.request.urlopen('http://127.0.0.1:8080/client/',timeout=10).status==200
print(json.dumps(dict(issue=1386,cluster=31,source=u['source'],ui_source=u['source'],server_source=s['source'],jar_sha256=d['jar_sha256'],classes_verified=classes,static_files_verified=len(u['ui']),web_inf_preserved=True,config_preserved=True,mold='active',client_http=200,management_pid=subprocess.check_output(['systemctl','show','mold','-p','MainPID','--value'],text=True).strip()),indent=2))

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
"""Read-only runtime audit after changed-module/server-class and UI deployment."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import urllib.request
import zipfile

stage=Path(sys.argv[1]);manifest=json.loads((stage/'manifest.json').read_text());deployment=json.loads((stage/'deployment.json').read_text())
assert manifest['issue']==1386 and manifest['cluster']==31
sha=lambda data:hashlib.sha256(data).hexdigest()
web=Path('/usr/share/cloudstack-management/webapp')
jar=Path('/usr/share/cloudstack-management/lib/cloudstack-4.23.0.0-ABLESTACK.Mold.jar')
assert sha(jar.read_bytes())==deployment['jar_sha256']
classes={**manifest['preserved_classes'],**manifest['classes']}
with zipfile.ZipFile(jar) as archive:
    assert all(sha(archive.read(name))==digest for name,digest in classes.items())
assert all(sha((web/name).read_bytes())==digest for name,digest in manifest['ui'].items())
assert (web/'WEB-INF').stat().st_ino==deployment['before']['web_inf_inode']
assert sha((web/'config.json').read_bytes())==deployment['before']['config_sha256']
assert (web/'META-INF').exists()==deployment['before']['meta_inf_present']
assert subprocess.check_output(['systemctl','is-active','mold'],text=True).strip()=='active'
assert urllib.request.urlopen('http://127.0.0.1:8080/client/',timeout=10).status==200
print(json.dumps(dict(issue=1386,cluster=31,source=manifest['source'],jar_sha256=deployment['jar_sha256'],classes_verified=classes,static_files_verified=len(manifest['ui']),web_inf_preserved=True,config_preserved=True,mold='active',client_http=200,management_pid=subprocess.check_output(['systemctl','show','mold','-p','MainPID','--value'],text=True).strip()),indent=2))

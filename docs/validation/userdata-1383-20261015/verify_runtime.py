# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
"""Read-only final verification; run on management with the deployment stage path."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import urllib.request
import zipfile

stage = Path(sys.argv[1])
manifest = json.loads((stage / 'manifest.json').read_text())
deployment = json.loads((stage / 'deployment.json').read_text())
assert manifest['issue'] in (1383, 1384) and manifest['cluster'] == 31
sha = lambda data: hashlib.sha256(data).hexdigest()
webapp = Path('/usr/share/cloudstack-management/webapp')
jar = Path('/usr/share/cloudstack-management/lib/cloudstack-4.23.0.0-ABLESTACK.Mold.jar')
assert sha(jar.read_bytes()) == manifest['jar_sha256']
with zipfile.ZipFile(jar) as archive:
    assert all(sha(archive.read(name)) == digest for name, digest in manifest['classes'].items())
assert all(sha((webapp / name).read_bytes()) == digest for name, digest in manifest['ui'].items())
before = deployment['before']
assert (webapp / 'WEB-INF').stat().st_ino == before['web_inf_inode']
assert sha((webapp / 'config.json').read_bytes()) == before['config_sha256']
assert (webapp / 'META-INF').exists() == before['meta_inf_present']
pid = subprocess.check_output(['systemctl', 'show', 'mold', '-p', 'MainPID', '--value'], text=True).strip()
assert pid == before['management_pid']
assert subprocess.check_output(['systemctl', 'is-active', 'mold'], text=True).strip() == 'active'
assert urllib.request.urlopen('http://127.0.0.1:8080/client/', timeout=10).status == 200
print(json.dumps(dict(issue=manifest['issue'], cluster=31, ui_source=manifest['source'], jar_sha256=manifest['jar_sha256'],
                      class_hashes_verified=manifest['classes'], static_files_verified=len(manifest['ui']),
                      web_inf_preserved=True, config_preserved=True, management_pid=pid,
                      management_pid_preserved=True, mold='active', client_http=200), indent=2))

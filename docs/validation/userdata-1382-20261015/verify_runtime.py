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
"""Read-only verification of the final cluster-31 runtime after both deployment stages."""
import hashlib
import json
from pathlib import Path
import subprocess
import urllib.request
import zipfile

backend = json.loads(Path('/var/tmp/ud1382-backend-final/manifest.json').read_text())
ui = json.loads(Path('/var/tmp/ud1382-ui/manifest.json').read_text())
initial = json.loads(Path('/var/tmp/ud1382-backend/deployment.json').read_text())
assert backend['issue'] == ui['issue'] == 1382
assert backend['cluster'] == ui['cluster'] == 31
expected = dict(ui['classes'], **backend['classes'])
sha = lambda data: hashlib.sha256(data).hexdigest()
webapp = Path('/usr/share/cloudstack-management/webapp')
jar = Path('/usr/share/cloudstack-management/lib/cloudstack-4.23.0.0-ABLESTACK.Mold.jar')
with zipfile.ZipFile(jar) as archive:
    assert all(sha(archive.read(name)) == digest for name, digest in expected.items())
assert all(sha((webapp / name).read_bytes()) == digest for name, digest in ui['ui'].items())
assert (webapp / 'WEB-INF').stat().st_ino == initial['before']['web_inf_inode']
assert sha((webapp / 'config.json').read_bytes()) == initial['before']['config_sha256']
assert subprocess.check_output(['systemctl', 'is-active', 'mold'], text=True).strip() == 'active'
assert urllib.request.urlopen('http://127.0.0.1:8080/client/', timeout=10).status == 200
print(json.dumps(dict(issue=1382, cluster=31, backend_source=backend['source'], ui_source=ui['source'],
                      class_hashes_verified=expected, static_files_verified=len(ui['ui']),
                      jar_sha256=sha(jar.read_bytes()), web_inf_preserved=True, config_preserved=True,
                      management_pid=subprocess.check_output(['systemctl', 'show', 'mold', '-p', 'MainPID', '--value'], text=True).strip(),
                      mold='active', client_http=200), indent=2))

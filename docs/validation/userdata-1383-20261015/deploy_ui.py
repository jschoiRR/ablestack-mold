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
"""Deploy only static UI assets after the matching issue-1383 backend has been verified."""
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import zipfile

stage = Path(sys.argv[1]).resolve()
manifest = json.loads((stage / 'manifest.json').read_text())
assert manifest['issue'] in (1383, 1384) and manifest['cluster'] == 31
webapp = Path('/usr/share/cloudstack-management/webapp')
jar = Path('/usr/share/cloudstack-management/lib/cloudstack-4.23.0.0-ABLESTACK.Mold.jar')
sha = lambda data: hashlib.sha256(data).hexdigest()
assert sha(jar.read_bytes()) == manifest['jar_sha256']
with zipfile.ZipFile(jar) as archive:
    assert all(sha(archive.read(name)) == digest for name, digest in manifest['classes'].items())
assert (webapp / 'WEB-INF').is_dir()
assert shutil.disk_usage(webapp).free > 2 * 1024**3
before = dict(config_sha256=sha((webapp / 'config.json').read_bytes()),
              web_inf_inode=(webapp / 'WEB-INF').stat().st_ino,
              meta_inf_present=(webapp / 'META-INF').exists(),
              management_pid=subprocess.check_output(['systemctl', 'show', 'mold', '-p', 'MainPID', '--value'], text=True).strip())
backup = Path('/root/ud' + str(manifest['issue']) + '-ui-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
backup.mkdir()
for top in (stage / 'ui').iterdir():
    assert top.name not in ('WEB-INF', 'META-INF', 'config.json')
    dest = webapp / top.name
    if dest.exists():
        if dest.is_dir():
            shutil.copytree(dest, backup / top.name)
        else:
            shutil.copy2(dest, backup / top.name)
    if top.is_dir():
        shutil.copytree(top, dest, dirs_exist_ok=True)
    else:
        shutil.copy2(top, dest)
assert (webapp / 'WEB-INF').stat().st_ino == before['web_inf_inode']
assert sha((webapp / 'config.json').read_bytes()) == before['config_sha256']
assert (webapp / 'META-INF').exists() == before['meta_inf_present']
assert subprocess.check_output(['systemctl', 'show', 'mold', '-p', 'MainPID', '--value'], text=True).strip() == before['management_pid']
assert subprocess.check_output(['systemctl', 'is-active', 'mold'], text=True).strip() == 'active'
assert urllib.request.urlopen('http://127.0.0.1:8080/client/', timeout=10).status == 200
assert all(sha((webapp / name).read_bytes()) == digest for name, digest in manifest['ui'].items())
result = dict(issue=manifest['issue'], cluster=31, source=manifest['source'], backup=str(backup), before=before,
              static_files_verified=len(manifest['ui']), class_hashes_verified=True, jar_sha256=sha(jar.read_bytes()),
              config_preserved=True, web_inf_preserved=True, management_pid_preserved=True, mold='active', client_http=200)
(backup / 'deployment.json').write_text(json.dumps(result, indent=2) + '\n')
(stage / 'deployment.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result))

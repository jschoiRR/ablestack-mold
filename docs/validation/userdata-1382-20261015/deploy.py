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
"""Run as root on the authorized cluster-31 management server with a staged package."""
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile

stage = Path(sys.argv[1]).resolve()
manifest = json.loads((stage / 'manifest.json').read_text())
assert manifest['issue'] == 1382 and manifest['cluster'] == 31
webapp = Path('/usr/share/cloudstack-management/webapp')
jar = Path('/usr/share/cloudstack-management/lib/cloudstack-4.23.0.0-ABLESTACK.Mold.jar')
assert (webapp / 'WEB-INF').is_dir() and jar.is_file()
assert shutil.disk_usage(jar.parent).free > 2 * 1024**3
backup = Path('/root/ud1382-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
backup.mkdir()
hashbytes = lambda data: hashlib.sha256(data).hexdigest()
hashfile = lambda path: hashbytes(path.read_bytes())
before = dict(jar_sha256=hashfile(jar), config_sha256=hashfile(webapp / 'config.json'),
              web_inf_inode=(webapp / 'WEB-INF').stat().st_ino,
              meta_inf_present=(webapp / 'META-INF').exists())
shutil.copy2(jar, backup / jar.name)
patches = {entry: (stage / 'classes' / entry).read_bytes() for entry in manifest['classes']}
assert all(hashbytes(data) == manifest['classes'][entry] for entry, data in patches.items())
replacement = jar.with_suffix('.jar.ud1382.tmp')
with zipfile.ZipFile(jar) as source, zipfile.ZipFile(replacement, 'w') as target:
    source_names = set(source.namelist())
    assert len(source.namelist()) == len(source_names), 'Existing jar contains duplicate entries'
    for info in source.infolist():
        target.writestr(info, patches.get(info.filename, source.read(info.filename)))
    for name, data in patches.items():
        if name not in source_names:
            assert name == 'com/cloud/server/UserDataRegistrationValidation.class'
            target.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
with zipfile.ZipFile(jar) as source, zipfile.ZipFile(replacement) as target:
    changed = [name for name in target.namelist() if name not in source_names or hashbytes(target.read(name)) != hashbytes(source.read(name))]
    assert set(changed) == set(patches), changed
    assert set(source.namelist()).issubset(target.namelist())
for top in (stage / 'ui').iterdir():
    assert top.name not in ('WEB-INF', 'META-INF', 'config.json')
    current = webapp / top.name
    if current.exists():
        dest = backup / 'static' / top.name
        dest.parent.mkdir(parents=True, exist_ok=True)
        if current.is_dir():
            shutil.copytree(current, dest)
        else:
            shutil.copy2(current, dest)
try:
    subprocess.run(['systemctl', 'stop', 'mold'], check=True)
    shutil.copystat(jar, replacement)
    replacement.replace(jar)
    for top in (stage / 'ui').iterdir():
        if top.is_dir():
            shutil.copytree(top, webapp / top.name, dirs_exist_ok=True)
        else:
            shutil.copy2(top, webapp / top.name)
    assert (webapp / 'WEB-INF').stat().st_ino == before['web_inf_inode']
    assert hashfile(webapp / 'config.json') == before['config_sha256']
    assert (webapp / 'META-INF').exists() == before['meta_inf_present']
    subprocess.run(['systemctl', 'start', 'mold'], check=True)
    for attempt in range(60):
        try:
            status = urllib.request.urlopen('http://127.0.0.1:8080/client/', timeout=3).status
            if status == 200:
                break
        except Exception:
            pass
        time.sleep(2)
    else:
        raise RuntimeError('Management HTTP did not recover')
    assert subprocess.check_output(['systemctl', 'is-active', 'mold'], text=True).strip() == 'active'
    for path, digest in manifest['ui'].items():
        assert hashfile(webapp / path) == digest, path
    result = dict(issue=1382, cluster=31, source=manifest['source'], backup=str(backup), before=before,
                  jar_sha256=hashfile(jar), changed_classes=changed, class_hashes=manifest['classes'],
                  unchanged_jar_entries_verified=True, static_files_verified=len(manifest['ui']),
                  web_inf_preserved=True, config_preserved=True, mold='active', client_http=200)
    (backup / 'deployment.json').write_text(json.dumps(result, indent=2) + '\n')
    (stage / 'deployment.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result), flush=True)
except BaseException:
    subprocess.run(['systemctl', 'stop', 'mold'])
    shutil.copy2(backup / jar.name, jar)
    for top in (backup / 'static').iterdir():
        if top.is_dir():
            shutil.copytree(top, webapp / top.name, dirs_exist_ok=True)
        else:
            shutil.copy2(top, webapp / top.name)
    subprocess.run(['systemctl', 'start', 'mold'])
    print('Deployment failed; previous JAR/static entry points restored from ' + str(backup), file=sys.stderr)
    raise

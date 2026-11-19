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
"""Package a successful UI-only WSL ext4 build with the UD-01 runtime contract."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile

repo = Path(sys.argv[1]).resolve()
stage = Path(sys.argv[2]).resolve()
assert str(repo).startswith('/home/ablecloud/work/dhslove/')
issue = int(sys.argv[3]) if len(sys.argv) > 3 else 1383
assert issue in (1383, 1384)
assert str(stage).startswith('/home/ablecloud/work/ud' + str(issue) + '-') and not stage.exists()
source = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()
prior = json.loads((repo / 'docs/validation/userdata-1382-20261015/runtime-final.json').read_text())
shutil.copytree(repo / 'ui/dist', stage / 'ui', ignore=shutil.ignore_patterns('config.json', 'WEB-INF', 'META-INF'))
files = {str(p.relative_to(stage / 'ui')): hashlib.sha256(p.read_bytes()).hexdigest() for p in (stage / 'ui').rglob('*') if p.is_file()}
javascript = ''.join(p.read_text(errors='replace') for p in (stage / 'ui/js').rglob('*.js'))
for marker in ['userdata-registration-form', 'blockingLoadingState', 'fetchSyncProgress', 'extractJobId']:
    assert marker in javascript, marker
if issue == 1384:
    for marker in ['userdata-selection-toolbar', 'userdata-reset-dialog', 'message.userdata.selection.missing']:
        assert marker in javascript, marker
manifest = dict(issue=issue, cluster=31, source=source, classes=prior['class_hashes_verified'], jar_sha256=prior['jar_sha256'], ui=files)
(stage / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
shutil.copy2(Path(__file__).with_name('deploy_ui.py'), stage / 'deploy_ui.py')
with tarfile.open(str(stage) + '.tgz', 'w:gz') as archive:
    for child in stage.iterdir():
        archive.add(child, arcname=child.name)
print(json.dumps(dict(source=source, static_files=len(files), artifact=str(stage) + '.tgz')))

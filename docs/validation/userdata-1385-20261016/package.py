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
"""Package the changed server class and a successful WSL ext4 production UI."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile

repo=Path(sys.argv[1]).resolve();stage=Path(sys.argv[2]).resolve()
assert str(repo).startswith('/home/ablecloud/work/dhslove/')
assert str(stage).startswith('/home/ablecloud/work/ud1385-') and not stage.exists()
prior=json.loads((repo/'docs/validation/userdata-1382-20261015/runtime-final.json').read_text())
shutil.copytree(repo/'ui/dist',stage/'ui',ignore=shutil.ignore_patterns('config.json','WEB-INF','META-INF'))
sha=lambda data:hashlib.sha256(data).hexdigest()
ui={str(p.relative_to(stage/'ui')):sha(p.read_bytes()) for p in (stage/'ui').rglob('*') if p.is_file()}
js=''.join(p.read_text(errors='replace') for p in (stage/'ui/js').rglob('*.js'))
for marker in ['userdata-current-summary','message.userdata.reset.keep','userdata-reset-dialog','blockingLoadingState','fetchSyncProgress','extractJobId']:assert marker in js,marker
name='com/cloud/api/query/dao/UserVmJoinDaoImpl.class'
dest=stage/'classes'/name;dest.parent.mkdir(parents=True);shutil.copy2(repo/'server/target/classes'/name,dest)
manifest=dict(issue=1385,cluster=31,source=subprocess.check_output(['git','-C',str(repo),'rev-parse','HEAD'],text=True).strip(),classes={name:sha(dest.read_bytes())},preserved_classes=prior['class_hashes_verified'],prior_jar_sha256=prior['jar_sha256'],ui=ui)
(stage/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
script=(repo/'docs/validation/userdata-1382-20261015/deploy.py').read_text()
script=script.replace("manifest['issue'] == 1382", "manifest['issue'] == 1385")
script=script.replace('ud1382','ud1385').replace('issue=1382','issue=1385')
script=script.replace("shutil.copy2(jar, backup / jar.name)","assert before['jar_sha256'] == manifest['prior_jar_sha256']\nshutil.copy2(jar, backup / jar.name)")
script=script.replace("assert set(source.namelist()).issubset(target.namelist())", "assert set(source.namelist()).issubset(target.namelist())\n    assert all(hashbytes(target.read(name)) == digest for name,digest in manifest['preserved_classes'].items())")
(stage/'deploy.py').write_text(script)
with tarfile.open(str(stage)+'.tgz','w:gz') as archive:
    for p in stage.iterdir():archive.add(p,arcname=p.name)
print(json.dumps(dict(source=manifest['source'],artifact=str(stage)+'.tgz',static_files=len(ui),classes=manifest['classes'])))

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
"""Package the scoped server compatibility guard without rebuilding the UI."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
repo=Path(sys.argv[1]).resolve();stage=Path(sys.argv[2]).resolve()
assert str(repo).startswith('/home/ablecloud/work/dhslove/')
assert str(stage).startswith('/home/ablecloud/work/ud1386-server-') and not stage.exists()
prior=json.loads(Path('/home/ablecloud/work/ud1386-ui-11a1423/manifest.json').read_text())
classes={};sha=lambda data:hashlib.sha256(data).hexdigest()
(stage/'ui').mkdir(parents=True)
for name in ['com/cloud/vm/UserVmManagerImpl.class','com/cloud/network/rules/UserdataToRouterRules.class']:
    dest=stage/'classes'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(repo/'server/target/classes'/name,dest);classes[name]=sha(dest.read_bytes())
manifest=dict(issue=1386,cluster=31,source=subprocess.check_output(['git','-C',str(repo),'rev-parse','HEAD'],text=True).strip(),ui_source=prior['source'],classes=classes,preserved_classes={k:v for k,v in prior['classes'].items() if k not in classes},prior_jar_sha256=prior['jar_sha256'],ui={})
(stage/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
script=(repo/'docs/validation/userdata-1382-20261015/deploy.py').read_text().replace("manifest['issue'] == 1382","manifest['issue'] == 1386").replace('ud1382','ud1386').replace('issue=1382','issue=1386')
script=script.replace("shutil.copy2(jar, backup / jar.name)","assert before['jar_sha256'] == manifest['prior_jar_sha256']\nshutil.copy2(jar, backup / jar.name)")
script=script.replace("assert set(source.namelist()).issubset(target.namelist())","assert set(source.namelist()).issubset(target.namelist())\n    assert all(hashbytes(target.read(name)) == digest for name,digest in manifest['preserved_classes'].items())")
(stage/'deploy.py').write_text(script)
with tarfile.open(str(stage)+'.tgz','w:gz') as archive:
    for p in stage.iterdir():archive.add(p,arcname=p.name)
print(json.dumps(dict(source=manifest['source'],ui_source=manifest['ui_source'],classes=classes,static_files_changed=0)))

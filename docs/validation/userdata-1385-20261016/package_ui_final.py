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
"""Package the final UI while preserving the already deployed UD-04 server."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile

repo=Path(sys.argv[1]).resolve();stage=Path(sys.argv[2]).resolve();prior=Path(sys.argv[3]).resolve()
assert str(repo).startswith('/home/ablecloud/work/dhslove/')
assert str(stage).startswith('/home/ablecloud/work/ud1385-ui-') and not stage.exists()
old=json.loads((prior/'manifest.json').read_text());deployment=json.loads((prior/'deployment.json').read_text())
assert old['issue']==deployment['issue']==1385
sha=lambda data:hashlib.sha256(data).hexdigest()
shutil.copytree(repo/'ui/dist',stage/'ui',ignore=shutil.ignore_patterns('config.json','WEB-INF','META-INF'))
files={str(p.relative_to(stage/'ui')):sha(p.read_bytes()) for p in (stage/'ui').rglob('*') if p.is_file()}
js=''.join(p.read_text(errors='replace') for p in (stage/'ui/js').rglob('*.js'))
for marker in ['label.userdata.template.default','userdata-current-summary','message.userdata.reset.keep','blockingLoadingState','fetchSyncProgress','extractJobId']:assert marker in js,marker
manifest=dict(issue=1385,cluster=31,source=subprocess.check_output(['git','-C',str(repo),'rev-parse','HEAD'],text=True).strip(),jar_sha256=deployment['jar_sha256'],classes={**old['preserved_classes'],**old['classes']},preserved_classes={},ui=files)
(stage/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
script=(repo/'docs/validation/userdata-1383-20261015/deploy_ui.py').read_text().replace("manifest['issue'] in (1383, 1384)","manifest['issue'] == 1385")
script=script.replace("for top in (stage / 'ui').iterdir():", "for top in sorted((stage / 'ui').iterdir(), key=lambda path: path.name == 'index.html'):")
(stage/'deploy_ui.py').write_text(script)
with tarfile.open(str(stage)+'.tgz','w:gz') as archive:
    for p in stage.iterdir():archive.add(p,arcname=p.name)
print(json.dumps(dict(source=manifest['source'],static_files=len(files),artifact=str(stage)+'.tgz')))

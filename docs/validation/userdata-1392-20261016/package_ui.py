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
"""Package UD-11 static assets while preserving the qualified UD-11 backend runtime."""
import hashlib,json,shutil,subprocess,sys,tarfile
from pathlib import Path
repo=Path(sys.argv[1]).resolve();stage=Path(sys.argv[2]).resolve()
assert str(repo).startswith('/home/ablecloud/work/dhslove/')
assert str(stage).startswith('/home/ablecloud/work/ud1392-ui-') and not stage.exists()
prior=json.loads(Path(sys.argv[3]).read_text())
sha=lambda data:hashlib.sha256(data).hexdigest()
shutil.copytree(repo/'ui/dist',stage/'ui',ignore=shutil.ignore_patterns('config.json','WEB-INF','META-INF'))
files={str(p.relative_to(stage/'ui')):sha(p.read_bytes()) for p in (stage/'ui').rglob('*') if p.is_file()}
js=''.join(p.read_text(errors='replace') for p in (stage/'ui/js').rglob('*.js'))
for marker in ['image-userdata-dialog','userdata-delete-content','userdata-versions','MOLD_USERDATA_VERSION/1','cloneUserData','vm-userdata-dialog','vm-userdata-summary','getVirtualMachineUserData','userdata-content','userdata.content','userdata.usage.vms','userdata.usage.templates','userdata.usage.isos','userdata-delete-dialog','blockingLoadingState','fetchSyncProgress','extractJobId']:assert marker in js,marker
manifest=dict(issue=1392,cluster=31,source=subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip(),jar_sha256=prior['jar_sha256'],classes=prior['classes_verified'],ui=files)
(stage/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
script=(repo/'docs/validation/userdata-1383-20261015/deploy_ui.py').read_text().replace("manifest['issue'] in (1383, 1384)","manifest['issue'] == 1392")
script=script.replace("for top in (stage / 'ui').iterdir():", "for top in sorted((stage / 'ui').iterdir(), key=lambda path: path.name == 'index.html'):")
(stage/'deploy_ui.py').write_text(script)
with tarfile.open(str(stage)+'.tgz','w:gz') as archive:
 for p in stage.iterdir():archive.add(p,arcname=p.name)
print(json.dumps(dict(source=manifest['source'],static_files=len(files),artifact=str(stage)+'.tgz')))

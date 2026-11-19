# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
"""Stage runtime-fetched locale JSON after a separately verified bundle build."""
import json,shutil,subprocess,sys
from pathlib import Path
repo=Path(sys.argv[1]).resolve()
built=sys.argv[2]
git=lambda *args:subprocess.check_output(['git',*args],cwd=repo,text=True).strip()
assert str(repo).startswith('/home/ablecloud/work/dhslove/')
files=['ui/public/locales/en.json','ui/public/locales/ko_KR.json']
assert set(git('diff','--name-only',built+'..HEAD').splitlines())==set(files)
for name in files:
    old=json.loads(git('show',built+':'+name))
    new=json.loads((repo/name).read_text())
    assert set(old)==set(new)
    assert [key for key in old if old[key]!=new[key]]==['label.userdata.versions']
    shutil.copy2(repo/name,repo/'ui/dist/locales'/Path(name).name)
    assert json.loads((repo/'ui/dist/locales'/Path(name).name).read_text())==new
print(json.dumps(dict(source=git('rev-parse','HEAD'),bundle_source=built,scope='WSL ext4 UI production build plus runtime-fetched locale JSON',command='NODE_OPTIONS=--openssl-legacy-provider npm run build',exit_code=0,generated_config_restored=True,static_locale_overlays=files,locale_key='label.userdata.versions',full_cloud_build=False,management_restart=False),indent=2))

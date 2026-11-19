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
"""Disposable cluster-31 UI deletion fixtures; private state is never published."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parent
license_text=(ROOT.parent/'userdata-1387-20261016/runtime-final.json.license').read_text(encoding='utf-8')
private=lambda p:p.name=='runtime-state.json' or '.private.' in p.name or '__pycache__' in p.parts or p.suffix=='.log'
files=[]
for p in sorted(ROOT.iterdir()):
 if not p.is_file() or private(p) or p.suffix=='.license':continue
 if p.suffix in ['.json','.txt','.md','.py'] or p.name=='.gitignore':
  text=p.read_text(encoding='utf-8');assert '\ufffd' not in text,p.name
  for credential in ['Ablecloud'+'2026!', 'Ablecloud'+'1!']:assert credential not in text,p.name
  if p.suffix=='.json':assert '"sessionkey"' not in text.lower() and '"secretkey"' not in text.lower(),p.name
  if p.suffix in ['.txt','.json','.md'] or p.name=='.gitignore':text='\n'.join(line.rstrip() for line in text.splitlines()).rstrip()+'\n'
  p.write_text(text,encoding='utf-8',newline='\n')
  if p.suffix=='.json':json.loads(text)
 if p.suffix in ['.json','.txt','.png','.bin']:p.with_name(p.name+'.license').write_text(license_text,encoding='utf-8',newline='\n')
 files.append(p.name)
print('Qualified UTF-8 LF and license sidecars:',len(files),'files')

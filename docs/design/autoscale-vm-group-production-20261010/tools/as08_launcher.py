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

import json,sys,subprocess,os,time
from pathlib import Path
line=sys.stdin.readline();line=line if line.startswith('{') else sys.stdin.readline();cfg=json.loads(line);p=Path(cfg['output']);p.mkdir(mode=0o700,parents=True,exist_ok=True);os.chmod(p,0o700)
if (p/'collector.pid').exists():
 pid=int((p/'collector.pid').read_text())
 try:os.kill(pid,0);raise RuntimeError('collector already active')
 except ProcessLookupError:pass
log=(p/'runner.log').open('ab',buffering=0);proc=subprocess.Popen(['python3','/home/ablecloud/epic1346-stage/as08-collector.py'],stdin=subprocess.PIPE,stdout=log,stderr=log,start_new_session=True)
proc.stdin.write((json.dumps(cfg)+'\n').encode());proc.stdin.close();(p/'collector.pid').write_text(str(proc.pid));time.sleep(0.2);assert proc.poll() is None,'collector stopped';print(json.dumps({'pid':proc.pid,'output':str(p),'startEpoch':cfg['startEpoch'],'deadlineEpoch':cfg['deadlineEpoch']}))

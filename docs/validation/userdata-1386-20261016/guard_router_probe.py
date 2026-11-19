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
"""Read only the final guard fixture's router-provided body and metadata."""
import shlex
import subprocess
import sys
remote=r'''import json,subprocess,shlex
assert subprocess.check_output(['virsh','domuuid','r-556-VM'],text=True).strip()=='b6b353a7-2a45-4fbf-a69e-573c0fdb7841'
probe="import pathlib,json,hashlib; p=pathlib.Path('/var/www/html/userdata/10.138.7.254/user-data'); t=pathlib.Path('/var/www/html/metadata/10.138.7.254/TAG'); b=p.read_bytes(); print(json.dumps(dict(body=b.decode(),sha256=hashlib.sha256(b).hexdigest(),tag=t.read_text() if t.exists() else None)))"
cmd='python3 -c '+shlex.quote(probe)
r=subprocess.run(['ssh','-o','StrictHostKeyChecking=accept-new','-o','ConnectTimeout=5','-i','/root/.ssh/id_rsa.cloud','-p','3922','root@169.254.241.52',cmd],capture_output=True,text=True,timeout=20)
assert r.returncode==0,r.stderr
print(r.stdout,end='')
'''
r=subprocess.run(['sshpass','-e','ssh','-n','root@10.10.31.3','python3 -c '+shlex.quote(remote)],capture_output=True,text=True,timeout=30)
print(r.stdout,end='');print(r.stderr,file=sys.stderr,end='');sys.exit(r.returncode)

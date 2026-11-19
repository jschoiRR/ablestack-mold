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
"""Read-only evidence from a stopped dedicated UD1386 VM disk."""
import json
import re
import shlex
import subprocess
import sys
host,domain,mode=sys.argv[1:4]
assert host in ['10.10.31.1','10.10.31.2','10.10.31.3']
assert re.fullmatch(r'i-2-(549|550|555)-VM',domain)
assert mode=='inspect'
remote=r'''import json,os,subprocess,sys,xml.etree.ElementTree as ET
vm,mode=sys.argv[1:3]
probe=subprocess.run(['virsh','domstate',vm],capture_output=True,text=True)
state=probe.stdout.strip() if probe.returncode==0 else 'not-defined'
assert state in ['shut off','not-defined'],state
assert vm not in subprocess.check_output(['virsh','list','--name'],text=True).splitlines()
root={'i-2-555-VM':'/mnt/glue-gfs/abd6933a-9ca8-4030-b87a-8cf1dc73b816','i-2-549-VM':'/mnt/glue-gfs/c6c08136-9ea7-4a59-ad7e-84a5daf1d728','i-2-550-VM':'/mnt/glue-gfs/c1399381-55a3-46cc-8245-ffa311c50735'}[vm]
assert os.path.realpath(root).startswith('/mnt/glue-gfs/') and os.path.isfile(root)
media=[]
env=dict(os.environ,LIBGUESTFS_BACKEND='direct')
files=['/var/tmp/ud1386-proof','/var/tmp/ud1386-executed']
cmd=['virt-cat','-a',root,*files]
r=subprocess.run(cmd,capture_output=True,text=True,timeout=120,env=env)
print(json.dumps(dict(domain=vm,state=state,mode=mode,disk=root,media=media,files=files if mode=='inspect' else [],exitcode=r.returncode,stdout=r.stdout,stderr=r.stderr),ensure_ascii=False,indent=2))
assert r.returncode==0
'''
command='python3 -c '+shlex.quote(remote)+' '+shlex.quote(domain)+' '+shlex.quote(mode)
r=subprocess.run(['sshpass','-e','ssh','-n','-o','ConnectTimeout=10','root@'+host,command],capture_output=True,text=True,timeout=140)
print(r.stdout,end='');print(r.stderr,file=sys.stderr,end='');sys.exit(r.returncode)

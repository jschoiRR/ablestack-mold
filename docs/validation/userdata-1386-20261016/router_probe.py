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
"""Observe or fault only the newly created r-551-VM control SSH connection."""
import json
import shlex
import subprocess
import sys
mode=sys.argv[1]
assert mode in ['inspect','reject','drop','clear','status']
remote=r'''import json,subprocess,sys
mode=sys.argv[1]
assert subprocess.check_output(['virsh','domuuid','r-551-VM'],text=True).strip()=='e1c21d04-4212-4f2b-a554-019937fff876'
base=['OUTPUT','-d','169.254.89.218','-p','tcp','--dport','3922','-m','comment','--comment','UD1386-r551']
def rule(target):return base+['-j',target]+(['--reject-with','tcp-reset'] if target=='REJECT' else [])
def exists(target):return subprocess.run(['iptables','-C',*rule(target)],capture_output=True).returncode==0
if mode in ['reject','drop']:
    assert not exists('REJECT') and not exists('DROP'),'Test rule already active'
    subprocess.run(['iptables','-I',*rule(mode.upper())],check=True)
elif mode=='clear':
    for target in ['REJECT','DROP']:
        if exists(target):subprocess.run(['iptables','-D',*rule(target)],check=True)
elif mode=='inspect':
    command='sha256sum /var/www/html/userdata/10.138.6.107/user-data; cat /var/www/html/userdata/10.138.6.107/user-data; printf "\nTAG="; cat /var/www/html/metadata/10.138.6.107/TAG'
    r=subprocess.run(['ssh','-o','StrictHostKeyChecking=accept-new','-o','ConnectTimeout=5','-i','/root/.ssh/id_rsa.cloud','-p','3922','root@169.254.89.218',command],capture_output=True,text=True,timeout=20)
    print(json.dumps(dict(router='r-551-VM',mode=mode,exitcode=r.returncode,stdout=r.stdout,stderr=r.stderr),ensure_ascii=False,indent=2));assert r.returncode==0
    sys.exit(0)
print(json.dumps(dict(router='r-551-VM',mode=mode,reject_active=exists('REJECT'),drop_active=exists('DROP'))))
'''
command='python3 -c '+shlex.quote(remote)+' '+shlex.quote(mode)
r=subprocess.run(['sshpass','-e','ssh','-n','root@10.10.31.1',command],capture_output=True,text=True,timeout=30)
print(r.stdout,end='');print(r.stderr,file=sys.stderr,end='');sys.exit(r.returncode)

#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
"""Lab-only VG-06 API/DB probe. Browser/UI execution is recorded separately.

Requires a freshly provisioned manifest with site, prefix, domain, groups,
vms, accounts.user and project. Secrets are environment variables, never output.
Snapshot is read-only; contract/race mutate only verified disposable targets.
"""
import argparse
import concurrent.futures
import http.cookiejar
import json
import os
from pathlib import Path
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


class Client:
    def __init__(self, site, username, password):
        self.url = f'http://10.10.{site}.10:8080/client/api'
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.key = None
        login = self.call('login', username=username, password=password)
        self.key = login['sessionkey']

    def call(self, command, allow_error=False, **params):
        if self.key:
            params['sessionkey'] = self.key
        if 'pagesize' in params:
            params.setdefault('page', 1)
        body = urllib.parse.urlencode(dict(command=command, response='json', **params)).encode()
        try:
            response = json.load(self.opener.open(urllib.request.Request(self.url, data=body), timeout=45))
        except urllib.error.HTTPError as error:
            response = json.load(error)
        value = next(iter(response.values()))
        if value.get('errorcode') and not allow_error:
            raise RuntimeError(f'{command}: API error {value["errorcode"]}')
        return value

    def wait(self, result):
        until = time.monotonic() + 300
        while result.get('jobid'):
            if time.monotonic() >= until:
                raise TimeoutError('Async job exceeded 300 seconds')
            job = self.call('queryAsyncJobResult', jobid=result['jobid'])
            if job.get('jobstatus') == 1:
                return job.get('jobresult', {})
            if job.get('jobstatus') == 2:
                raise RuntimeError(f'Async job failed: {job.get("jobresultcode")}')
            time.sleep(1)
        return result


class Probe:
    def __init__(self, manifest):
        self.data = manifest
        self.site = str(manifest['site'])
        assert self.site in ['31', '32']
        self.prefix = manifest['prefix']
        assert self.prefix.startswith(f'VG06-{self.site}-')
        self.api = Client(self.site, os.environ.get('CLOUD_ADMIN_USERNAME', 'admin'), os.environ['CLOUD_ADMIN_PASSWORD'])
        for resource in manifest['vms'] + manifest['groups']:
            uuid.UUID(resource['id'])
            assert resource['name'].startswith(self.prefix)
        self.created = []
        self.cases = []

    def scope(self, vm):
        return {'projectid': self.data['project']['id']} if vm['fixture_owner'] == 'project' else {}

    def vm(self, vm):
        rows = self.api.call('listVirtualMachines', id=vm['id'], listall=True, **self.scope(vm)).get('virtualmachine', [])
        assert len(rows) == 1 and rows[0]['name'] == vm['name']
        return rows[0]

    def group(self, owner, suffix):
        group = next(g for g in self.data['groups'] if g['fixture_owner'] == owner and g['fixture_suffix'] == suffix)
        scope = {'projectid': self.data['project']['id']} if owner == 'project' else {}
        current = self.api.call('listInstanceGroups', id=group['id'], listall=True, **scope).get('instancegroup', [])
        assert len(current) == 1 and current[0]['name'] == group['name'] and current[0]['name'].startswith(self.prefix)
        return group

    def change(self, vm, client=None, **selectors):
        return (client or self.api).wait((client or self.api).call('updateVirtualMachine', id=vm['id'], **self.scope(vm), **selectors))

    def db(self):
        cfg = {'ids': [v['id'] for v in self.data['vms']], 'password': os.environ['CLOUD_DB_PASSWORD']}
        # Data are Python literals on stdin, never shell interpolations or argv secrets.
        script = 'cfg=' + repr(cfg) + '\n' + r"""import subprocess,os,json
ids=','.join("'"+x+"'" for x in cfg['ids'])
env=dict(os.environ,MYSQL_PWD=cfg['password'])
def q(sql):return subprocess.check_output(['mysql','-uroot','-N','-B','cloud','-e',sql],env=env,text=True).strip()
sql="SELECT v.uuid,IFNULL(g.uuid,''),IF(m.group_id IS NULL,0,1),IF(m.group_id IS NULL,0,IF(g.id IS NULL OR g.removed IS NOT NULL,1,0)),IF(m.group_id IS NULL,1,v.account_id=g.account_id) FROM vm_instance v LEFT JOIN instance_group_vm_map m ON m.instance_id=v.id LEFT JOIN instance_group g ON g.id=m.group_id WHERE v.uuid IN ("+ids+") ORDER BY v.uuid,m.group_id"
rows=[x.split('\t') for x in q(sql).splitlines()];mapping={x:[] for x in cfg['ids']}
for vm,group,exists,orphan,same_owner in rows:
 assert orphan=='0' and same_owner=='1', 'Orphan or foreign-owner mapping'
 if exists=='1':mapping[vm].append(group)
assert all(len(x)<=1 for x in mapping.values()), 'Duplicate VM mapping'
ftctl=int(q("SELECT COUNT(*) FROM ftctl_protection p JOIN vm_instance v ON v.id=p.primary_vm_id WHERE p.removed IS NULL AND v.uuid IN ("+ids+")"))
details=int(q("SELECT COUNT(*) FROM vm_instance_details d JOIN vm_instance v ON v.id=d.vm_id WHERE d.name LIKE 'ftctl.%' AND v.uuid IN ("+ids+")"))
print(json.dumps({'maps':mapping,'active_ftctl':ftctl,'ftctl_details':details,'duplicate_maps':0,'orphan_maps':0,'foreign_owner_maps':0}))
"""
        env = dict(os.environ, SSHPASS=os.environ['CLOUD_SSH_PASSWORD'])
        result = subprocess.run(['sshpass', '-e', 'ssh', '-o', 'StrictHostKeyChecking=yes', '-p', '22',
                                 f'ablecloud@10.10.{self.site}.10', 'sudo python3 -'], input=script, text=True,
                                capture_output=True, timeout=60, env=env)
        if result.returncode:
            message = result.stderr[-500:]
            for name in ['CLOUD_ADMIN_PASSWORD', 'CLOUD_TEST_PASSWORD', 'CLOUD_DB_PASSWORD', 'CLOUD_SSH_PASSWORD']:
                value = os.environ.get(name)
                if value: message = message.replace(value, '[REDACTED]')
            raise RuntimeError('SSH/DB integrity probe failed: ' + message)
        return json.loads(result.stdout)

    def resources(self):
        fields = ['id', 'name', 'displayname', 'state', 'hostid', 'instancename', 'serviceofferingid', 'templateid',
                  'cpunumber', 'cpuspeed', 'memory', 'haenable', 'deleteprotection', 'rootdeviceid']
        volume_fields = ['id', 'name', 'type', 'state', 'size', 'deviceid', 'poolid', 'storageid', 'storage', 'diskofferingid', 'path', 'virtualmachineid']
        result = {}
        for vm in self.data['vms']:
            current = self.vm(vm)
            volumes = self.api.call('listVolumes', virtualmachineid=vm['id'], listall=True, **self.scope(vm)).get('volume', [])
            assert volumes
            result[vm['id']] = {'vm': {f: current.get(f) for f in fields},
                                'volumes': sorted([{f: v.get(f) for f in volume_fields} for v in volumes], key=lambda x: x['id']),
                                'nics': sorted([{f: n.get(f) for f in ['id', 'networkid', 'macaddress']} for n in current.get('nic', [])], key=lambda x: x['id'])}
        return result

    def record(self, name, **detail):
        db = self.db()
        for vm in self.data['vms']:
            current = self.vm(vm)
            assert db['maps'][vm['id']] == ([current['groupid']] if current.get('groupid') else [])
        self.cases.append({'case': name, 'pass': True, 'db': db, **detail})

    def create_group(self, name):
        assert name.startswith(self.prefix)
        g = self.api.call('createInstanceGroup', name=name, account='admin', domainid=self.data['domain'])['instancegroup']
        self.created.append({'id': g['id'], 'name': g['name']})
        return g

    def contracts(self):
        vm = next(v for v in self.data['vms'] if v['fixture_owner'] == 'admin' and v['name'].endswith('VM2'))
        a, b = self.group('admin', 'A'), self.group('admin', 'B')
        self.change(vm, groupid=a['id']); self.record('UUID link')
        self.change(vm); assert self.vm(vm)['groupid'] == a['id']; self.record('Missing selector preserves mapping')
        for name in ['', '   ', '\u3000']:
            r = self.api.call('updateVirtualMachine', id=vm['id'], group=name, allow_error=True)
            assert r.get('errorcode') and self.vm(vm)['groupid'] == a['id']; self.record('Blank selector rejected', input_units=len(name), errorcode=r['errorcode'])
        foreign = self.group('user', 'A')
        r = self.api.call('updateVirtualMachine', id=vm['id'], groupid=foreign['id'], allow_error=True)
        assert r.get('errorcode') and self.vm(vm)['groupid'] == a['id']; self.record('Admin cannot classify VM under another owner', errorcode=r['errorcode'])
        self.change(vm, groupid=b['id']); self.record('UUID move')
        self.change(vm, cleargroup=True); assert not self.vm(vm).get('groupid'); self.record('Explicit clear')
        user = Client(self.site, self.data['accounts']['user']['name'], os.environ['CLOUD_TEST_PASSWORD'])
        hidden = user.call('listInstanceGroups', id=a['id'], listall=True, allow_error=True)
        assert not hidden.get('instancegroup'); self.record('Foreign group read hidden')
        denied = user.call('updateVirtualMachine', id=vm['id'], cleargroup=True, allow_error=True)
        assert denied.get('errorcode'); self.record('Foreign VM write denied', errorcode=denied['errorcode'])
        for owner in ['user', 'project']:
            own = next(v for v in self.data['vms'] if v['fixture_owner'] == owner)
            target = self.group(owner, 'A'); self.change(own, client=user, groupid=target['id']); self.record(owner+' same-name owner link')
            self.change(own, client=user); assert self.vm(own)['groupid'] == target['id']; self.record(owner+' selector omission')
            self.change(own, client=user, cleargroup=True); self.record(owner+' explicit clear')
        normal = self.create_group(self.prefix+'contract-한글')
        bad = self.api.call('createInstanceGroup', name=' '+normal['name']+' ', account='admin', domainid=self.data['domain'], allow_error=True)
        assert bad.get('errorcode'); self.record('Normalized duplicate rejected', errorcode=bad['errorcode'])
        maximal = self.prefix + '가'*(255-len(self.prefix)); g = self.create_group(maximal); assert len(g['name'])==255
        unchanged = self.api.call('updateInstanceGroup', id=g['id'], name=g['name'])['instancegroup']; assert unchanged['name']==g['name']; self.record('255 units and unchanged rename accepted')
        for name in ['', '   ', self.prefix+'가'*(256-len(self.prefix))]:
            bad = self.api.call('updateInstanceGroup', id=g['id'], name=name, allow_error=True)
            assert bad.get('errorcode'); assert self.api.call('listInstanceGroups',id=g['id'])['instancegroup'][0]['name']==maximal
            self.record('Invalid rename preserves name', input_units=len(name), errorcode=bad['errorcode'])

    def races(self, rounds):
        vm = next(v for v in self.data['vms'] if v['fixture_owner'] == 'admin' and v['name'].endswith('VM2'))
        a, b = self.group('admin', 'A'), self.group('admin', 'B')
        for i in range(rounds):
            self.change(vm, groupid=a['id']); target = self.create_group(self.prefix+f'race-{i}')
            barrier = threading.Barrier(2)
            def assign():
                barrier.wait(); start=time.monotonic(); r=self.api.call('updateVirtualMachine',id=vm['id'],groupid=target['id'],allow_error=True); return {'action':'assign','start':start,'end':time.monotonic(),'errorcode':r.get('errorcode')}
            def delete():
                barrier.wait(); start=time.monotonic(); r=self.api.call('deleteInstanceGroup',id=target['id']); assert r.get('success'); return {'action':'delete','start':start,'end':time.monotonic(),'success':True}
            with concurrent.futures.ThreadPoolExecutor(2) as pool:
                jobs=[pool.submit(assign),pool.submit(delete)]; results=[j.result() for j in jobs]
            assert self.vm(vm).get('groupid') in [None,a['id']]
            self.record('Assign versus target deletion', round=i,requests=results)
            barrier = threading.Barrier(2)
            def move(group):
                barrier.wait(); start=time.monotonic(); self.change(vm,groupid=group['id']);return {'groupid':group['id'],'start':start,'end':time.monotonic()}
            with concurrent.futures.ThreadPoolExecutor(2) as pool:
                jobs=[pool.submit(move,g) for g in [a,b]];results=[j.result() for j in jobs]
            assert self.vm(vm).get('groupid') in [a['id'],b['id']]
            self.record('Two writes to same VM', round=i,requests=results)
        self.change(vm,cleargroup=True)

    def run(self, mode, rounds):
        baseline=self.resources();initial=self.db();original={v['id']:self.vm(v).get('groupid') for v in self.data['vms']}
        if mode=='snapshot':return {'site':self.site,'resources':baseline,'db':initial}
        try:
            self.contracts() if mode=='contract' else self.races(rounds)
            assert self.resources()==baseline,'VM/volume/host/power changed'
        finally:
            for vm in self.data['vms']:
                if self.vm(vm).get('groupid')!=original[vm['id']]:self.change(vm,**({'groupid':original[vm['id']]} if original[vm['id']] else {'cleargroup':True}))
            for group in self.created:
                current=self.api.call('listInstanceGroups',id=group['id']).get('instancegroup',[])
                if current:
                    assert current[0]['name']==group['name'];self.api.call('deleteInstanceGroup',id=group['id'])
        assert self.resources()==baseline and self.db()['maps']==initial['maps']
        return {'site':self.site,'mode':mode,'cases':self.cases,'resources_preserved':True,'mappings_restored':True,'temporary_groups':self.created,'final_db':self.db()}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--mode',choices=['snapshot','contract','race'],default='snapshot')
    parser.add_argument('--race-rounds',type=int,choices=range(1,11),default=3)
    args=parser.parse_args();probe=Probe(json.loads(args.manifest.read_text()))
    try:result=probe.run(args.mode,args.race_rounds)
    except Exception as error:
        args.output.write_text(json.dumps({'site':probe.site,'mode':args.mode,'pass':False,'error':str(error),'cases':probe.cases},ensure_ascii=False,indent=2));raise
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps({'site':probe.site,'mode':args.mode,'pass':True,'cases':len(probe.cases)}))


if __name__=='__main__':main()

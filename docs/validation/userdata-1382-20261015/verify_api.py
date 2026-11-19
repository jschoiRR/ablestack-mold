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
"""Cluster-31 issue-1382 qualification; password is supplied only in the environment."""
import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parent
PREFIX = 'UD1382-31-20261015'
ENDPOINT = 'http://10.10.31.10:8080/client/api'
RESULTS = []

def save(name, value):
    (ROOT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')

class Mold:
    def __init__(self, username='admin', password=None, domain='/'):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.key = None
        login = self.call('login', username=username, password=password or os.environ['MOLD_TEST_PASSWORD'], domain=domain)
        self.key = login['sessionkey']

    def call(self, command, _http_method='POST', **params):
        values = dict(command=command, response='json', **params)
        if self.key:
            values['sessionkey'] = self.key
        encoded = urllib.parse.urlencode(values)
        req = urllib.request.Request(ENDPOINT + '?' + encoded) if _http_method == 'GET' else urllib.request.Request(ENDPOINT, data=encoded.encode())
        try:
            response = self.opener.open(req, timeout=45)
        except urllib.error.HTTPError as error:
            response = error
        return next(iter(json.load(response).values()))

    def job(self, command, **params):
        response = self.call(command, **params)
        assert 'errorcode' not in response, response
        if response.get('jobid'):
            for _ in range(60):
                result = self.call('queryAsyncJobResult', jobid=response['jobid'])
                if result.get('jobstatus') == 1:
                    return result.get('jobresult', {})
                assert result.get('jobstatus') != 2, result
                time.sleep(1)
            raise RuntimeError('Asynchronous job timed out')
        return response

def content(value):
    return base64.b64encode(value if isinstance(value, bytes) else value.encode()).decode()

def record(label, passed, **evidence):
    RESULTS.append(dict(test=label, passed=bool(passed), **evidence))
    save('api-results.json', RESULTS)
    print(label, 'PASS' if passed else 'FAIL', flush=True)
    assert passed, (label, evidence)

def inventory(api):
    rows = []
    page = 1
    while True:
        response = api.call('listUserData', listall='true', page=page, pagesize=100)
        assert 'errorcode' not in response, response
        rows.extend(response.get('userdata', []))
        if len(rows) >= response.get('count', 0):
            return sorted(rows, key=lambda row: row['id'])
        assert response.get('userdata'), 'Incomplete paginated inventory'
        page += 1

def baseline(api):
    data = dict(userdata=inventory(api), limit=api.call('listConfigurations', name='vm.userdata.max.length')['configuration'][0]['value'])
    save('baseline.private.json', data)
    print('Baseline saved; existing libraries:', len(data['userdata']), 'configured limit:', data['limit'])

def matrix(api):
    original = json.loads((ROOT / 'baseline.private.json').read_text(encoding='utf-8'))
    resources = []
    accounts = []
    projects = []
    domains = []
    limit = int(original['limit'])
    def register(label, encoded=None, name=None, client=api, success=True, **kwargs):
        name = name if name is not None else PREFIX + '-' + label
        encoded = encoded if encoded is not None else content('# ' + PREFIX + '-' + label)
        response = client.call('registerUserData', name=name, userdata=encoded, **kwargs)
        row = response.get('userdata')
        if row:
            resources.append((client, row['id']))
        record(label, bool(row) == success and (success or response.get('errorcode') == 431),
               id=row['id'] if row else None, errorcode=response.get('errorcode'), message=response.get('errortext'))
        return row
    try:
        cap = api.call('listCapabilities')['capability']
        record('capability-limit', cap.get('userdatamaxlength') == limit, actual=cap.get('userdatamaxlength'), expected=limit)
        for i, name in enumerate([' ', '\t\n', '\u00a0\u3000', '\ufeff', 'x' * 256]):
            register('invalid-name-' + str(i), name=name, success=False)
        for i, value in enumerate([' ', '', 'IAkK', content('\u00a0\u3000'), content('\ufeff'), '%', '%GG', 'A', 'eA=', 'eA===', 'eA==Yg==', '!!!!']):
            register('invalid-body-' + str(i), encoded=value, success=False)
        canonical = register('canonical', encoded='eA==', name='  ' + PREFIX + '-trimmed  ')
        record('trimmed-name', canonical['name'] == PREFIX + '-trimmed')
        for i, value in enumerate(['eA', 'eA%3D%3D', 'eA\n', ' eA== ']):
            register('canonical-duplicate-' + str(i), encoded=value, success=False)
        register('same-name', name=canonical['name'], success=False)
        register('trimmed-same-name', name=' ' + canonical['name'] + ' ', success=False)
        row = register('case-sensitive-body', encoded='EA==')
        record('case-sensitive-body-roundtrip', row['userdata'] == 'EA==')
        register('exact-among-case-insensitive-candidates', encoded='EA', success=False)
        row = register('binary', encoded='_wAB')
        record('binary-roundtrip', row['userdata'] == '/wAB')
        row = register('non-utf8-whitespace-byte', encoded='oA==')
        record('non-utf8-byte-preserved', row['userdata'] == 'oA==')
        plus = register('plus-percent', encoded='Zm+8/w8%3D')
        record('literal-plus-preserved', plus['userdata'] == 'Zm+8/w8=')
        utf8 = '#cloud-config\n# 한글 입력\n  preserved: true\n'
        row = register('utf8', encoded=content(utf8), params=' USER , user, env.name ,test-key ')
        record('utf8-newline-and-variables', base64.b64decode(row['userdata']).decode() == utf8 and row['params'] == 'USER,user,env.name,test-key')
        for i, params in enumerate([' ', ',x', 'x,', 'x,x', 'x, x ', 'a b', 'x=y', '1x', 'instance-id', ' instance-id ']):
            register('invalid-params-' + str(i), params=params, success=False)
        row = register('limit-exact', encoded=content(b'x' * (limit // 4 * 3)))
        record('canonical-limit-exact', len(row['userdata']) == limit)
        register('limit-over', encoded=content(b'x' * (limit // 4 * 3 + 1)), success=False)
        register('limit-over-unpadded', encoded=content(b'x' * (limit // 4 * 3 + 1)).rstrip('='), success=False)
        # A single changed global setting is restored in finally, including on assertion failure.
        newlimit = 64
        api.call('updateConfiguration', name='vm.userdata.max.length', value=newlimit)
        record('dynamic-limit-visible', api.call('listCapabilities')['capability'].get('userdatamaxlength') == newlimit)
        register('dynamic-limit-exact', encoded=content(b'y' * 48))
        register('dynamic-limit-over', encoded=content(b'y' * 49).rstrip('='), success=False)
        api.call('updateConfiguration', name='vm.userdata.max.length', value=limit)
        record('dynamic-limit-restored', api.call('listCapabilities')['capability'].get('userdatamaxlength') == limit)

        def concurrent(i):
            client = Mold()
            response = client.call('registerUserData', name=PREFIX + '-race-' + str(i), userdata=content('# ' + PREFIX + '-race').rstrip('=') if i % 2 else content('# ' + PREFIX + '-race'))
            return response
        with ThreadPoolExecutor(max_workers=8) as pool:
            responses = list(pool.map(concurrent, range(8)))
        for response in responses:
            if response.get('userdata'):
                resources.append((api, response['userdata']['id']))
        record('concurrent-normalized-content', sum('userdata' in r for r in responses) == 1 and sum(r.get('errorcode') == 431 for r in responses) == 7,
               successes=sum('userdata' in r for r in responses), rejected=sum(r.get('errorcode') == 431 for r in responses))

        rootdomain = api.call('listDomains')['domain'][0]['id']
        domain = api.call('createDomain', name=PREFIX + '-domain', parentdomainid=rootdomain)['domain']
        domains.append(domain['id'])
        password = secrets.token_urlsafe(6) + 'aA1!'
        clients = []
        for accounttype, suffix in [(0, 'user'), (2, 'domainadmin')]:
            username = PREFIX.lower() + '-' + suffix
            account = api.call('createAccount', username=username, password=password, firstname='UD1382', lastname='Validation', email='ud1382@example.invalid', accounttype=accounttype, account=username, domainid=domain['id'])['account']
            accounts.append(account['id'])
            client = Mold(username, password, '/' + domain['name'] + '/')
            clients.append(client)
            record(suffix + '-capability-limit', client.call('listCapabilities')['capability'].get('userdatamaxlength') == limit)
            row = register(suffix + '-same-owner-data', encoded='eA', name=canonical['name'], client=client)
            register(suffix + '-own-duplicate', encoded='eA%3D%3D', client=client, success=False)
            register(suffix + '-blank', encoded=' ', client=client, success=False)
            foreign = client.call('listUserData', id=canonical['id'], listall='true')
            record(suffix + '-foreign-owner-hidden', not foreign.get('userdata'))
        project = api.job('createProject', name=PREFIX + '-project', displaytext=PREFIX + ' project')['project']
        projects.append(project['id'])
        register('project-same-name-and-content', encoded='eA', name=canonical['name'], projectid=project['id'])
        register('project-own-duplicate', encoded='eA==', projectid=project['id'], success=False)
        register('project-blank', encoded=' ', projectid=project['id'], success=False)
        record('project-filter-scope', len(api.call('listUserData', projectid=project['id'], listall='true').get('userdata', [])) == 1)
    finally:
        assert 'errorcode' not in api.call('updateConfiguration', name='vm.userdata.max.length', value=original['limit'])
        for client, uid in reversed(resources):
            result = client.call('deleteUserData', id=uid)
            assert 'errorcode' not in result, result
        for pid in projects:
            api.job('deleteProject', id=pid)
        for aid in accounts:
            api.job('deleteAccount', id=aid)
        for did in domains:
            api.job('deleteDomain', id=did)
    record('original-library-exact-equality', inventory(api) == original['userdata'])
    record('original-config-restored', api.call('listConfigurations', name='vm.userdata.max.length')['configuration'][0]['value'] == original['limit'])
    print('API qualification complete:', len(RESULTS), 'PASS')

def cleanup(api):
    original = json.loads((ROOT / 'baseline.private.json').read_text(encoding='utf-8'))
    for row in inventory(api):
        if row['name'].startswith(PREFIX):
            assert 'errorcode' not in api.call('deleteUserData', id=row['id'])
    final = inventory(api)
    assert final == original['userdata'], 'Existing library changed'
    save('cleanup-summary.json', {'original_library_count': len(final), 'original_library_exact_match': True, 'test_library_residuals': 0,
                                 'original_config_restored': api.call('listConfigurations', name='vm.userdata.max.length')['configuration'][0]['value'] == original['limit'],
                                 'project_residuals': api.call('listProjects', name=PREFIX+'-project', listall='true').get('count', 0),
                                 'domain_residuals': api.call('listDomains', name=PREFIX+'-domain', listall='true').get('count', 0)})
    print('CLEANUP VERIFIED; baseline libraries and configuration preserved')

def prepare_ui(api):
    domain = api.call('createDomain', name=PREFIX + '-domain')['domain']
    state = {'domain_id': domain['id'], 'domain_path': '/' + domain['name'] + '/', 'accounts': []}
    save('runtime-state.json', state)
    for accounttype, suffix in [(0, 'user'), (2, 'domainadmin')]:
        username = PREFIX.lower() + '-' + suffix
        result = api.call('createAccount', username=username, password=os.environ['MOLD_TEST_PASSWORD'], firstname='UD1382', lastname='UI', email='ud1382@example.invalid', accounttype=accounttype, account=username, domainid=domain['id'])
        assert 'account' in result, result
        state['accounts'].append({'id': result['account']['id'], 'username': username, 'role': suffix})
        save('runtime-state.json', state)
    project = api.job('createProject', name=PREFIX + '-project', displaytext=PREFIX + ' UI project')['project']
    state['project_id'] = project['id']
    save('runtime-state.json', state)
    print(json.dumps(state))

def get_boundaries(api):
    RESULTS.extend(json.loads((ROOT / 'api-results.json').read_text(encoding='utf-8')))
    uid = None
    try:
        response = api.call('registerUserData', _http_method='GET', name=PREFIX+'-get-limit', userdata=content(b'g'*3072))
        row = response.get('userdata')
        uid = row['id'] if row else None
        record('http-get-4096-boundary', bool(row) and len(row['userdata']) == 4096, id=uid)
        response = api.call('registerUserData', _http_method='GET', name=PREFIX+'-get-over', userdata=content(b'g'*3073).rstrip('='))
        record('http-get-over-canonical-boundary', response.get('errorcode') == 431, errorcode=response.get('errorcode'), message=response.get('errortext'))
    finally:
        if uid:
            assert 'errorcode' not in api.call('deleteUserData', id=uid)
    record('get-test-resources-cleaned', not api.call('listUserData', keyword=PREFIX+'-get-').get('userdata'))

def cleanup_ui(api):
    state = json.loads((ROOT / 'runtime-state.json').read_text())
    for row in api.call('listUserData', projectid=state['project_id']).get('userdata', []):
        assert row['name'].startswith(PREFIX), 'Unexpected non-test project data'
        assert 'errorcode' not in api.call('deleteUserData', id=row['id'], projectid=state['project_id'])
    for row in inventory(api):
        if row['name'].startswith(PREFIX):
            assert 'errorcode' not in api.call('deleteUserData', id=row['id'], account=row.get('account'), domainid=row['domainid'])
    api.job('deleteProject', id=state['project_id'])
    for account in state['accounts']:
        api.job('deleteAccount', id=account['id'])
    api.job('deleteDomain', id=state['domain_id'])
    cleanup(api)

if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    api = Mold()
    {'baseline': baseline, 'matrix': matrix, 'cleanup': cleanup, 'prepare-ui': prepare_ui, 'cleanup-ui': cleanup_ui, 'get-boundaries': get_boundaries}[sys.argv[1]](api)

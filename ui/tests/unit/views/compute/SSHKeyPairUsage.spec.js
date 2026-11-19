// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

import { flushPromises } from '@vue/test-utils'
import Usage from '@/views/compute/SSHKeyPairUsage'
import ResourceView from '@/components/view/ResourceView'
import { sshKeyPairUsageRows } from '@/utils/sshKeyPairUsage'
import { getAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn() }))
const key = { id: '11111111-1111-4111-8111-111111111111', name: 'MiXeD', fingerprint: 'fp', account: 'owner', domainid: '22222222-2222-4222-8222-222222222222' }
const vm = { id: '33333333-3333-4333-8333-333333333333', name: 'system', displayname: 'USER.LOGIN', state: 'Stopped', account: key.account, domainid: key.domainid }
const identity = row => ({ listsshkeypairsresponse: { count: 1, sshkeypair: [row] } })
const response = (rows = [vm], count = rows.length) => ({ listvirtualmachinesresponse: { count, virtualmachine: rows } })
let c
beforeEach(() => {
  jest.clearAllMocks()
  c = { resource: { ...key }, tab: 'ssh.keypair.usage', resourceActionReady: () => true, $route: { path: '/ssh/' + key.id, query: { account: key.account, domainid: key.domainid } }, $router: { push: jest.fn() }, $store: { getters: { apis: { listVirtualMachines: {} }, project: {}, userInfo: { id: 'session-user' } }, state: { user: { token: 'session' } } }, $t: text => text }
  Object.assign(c, Usage.data.call(c)); Object.entries(Usage.methods).forEach(([name, fn]) => { c[name] = fn.bind(c) }); Object.entries(Usage.computed).forEach(([name, fn]) => { Object.defineProperty(c, name, { get: () => fn.call(c) }) })
  getAPI.mockImplementation(api => Promise.resolve(api === 'listSSHKeyPairs' ? identity(c.resource) : response()))
})
test('queries exact key scope between UUID/fingerprint/owner checks; display names stay raw', async () => {
  await c.fetchData(); expect(c.status).toBe('ready'); expect(c.rows[0].name).toBe('USER.LOGIN'); expect(getAPI.mock.calls.map(call => call[0])).toEqual(['listSSHKeyPairs', 'listVirtualMachines', 'listSSHKeyPairs'])
  expect(getAPI.mock.calls[1][1]).toMatchObject({ keypair: key.name, account: key.account, domainid: key.domainid, ignoreproject: true, listall: true, page: 1, pagesize: 20, details: 'min' })
})
test('project key keeps explicit project despite global context', async () => {
  const projectid = '44444444-4444-4444-8444-444444444444'; c.resource.projectid = projectid; c.$store.getters.project = { id: 'ambient' }; getAPI.mockImplementation(api => Promise.resolve(api === 'listSSHKeyPairs' ? identity(c.resource) : response([{ ...vm, projectid }])))
  await c.fetchData(); expect(c.status).toBe('ready'); expect(getAPI.mock.calls[1][1].projectid).toBe(projectid)
})
test.each([['inactive', v => { v.tab = 'details' }], ['parent unverified', v => { v.resourceActionReady = () => false }], ['invalid key', v => { v.resource.id = '' }]])('%s never makes unfiltered VM requests', async (_, change) => { change(c); await c.fetchData(); expect(getAPI).not.toHaveBeenCalled() })
test('permission loss clears old rows without sending an API request', async () => { await c.fetchData(); delete c.$store.getters.apis.listVirtualMachines; getAPI.mockClear(); await c.fetchData(); expect(c.status).toBe('forbidden'); expect(c.rows).toEqual([]); expect(getAPI).not.toHaveBeenCalled() })
test.each([403, 432, 503])('HTTP %s is not a zero count', async status => { getAPI.mockRejectedValue({ response: { status } }); await c.fetchData(); expect(c.status).toBe(status === 503 ? 'error' : 'forbidden'); expect(c.status).not.toBe('ready') })
test('only valid empty response yields unused; malformed and foreign-owner rows fail closed', () => {
  expect(sshKeyPairUsageRows({ listvirtualmachinesresponse: {} }, key)).toEqual({ state: 'ready', count: 0, rows: [] })
  for (const json of [{}, response([vm], '1'), response([{ ...vm, account: 'foreign' }]), response([{ ...vm, projectid: 'other' }]), response([{ ...vm, id: '' }]), response([vm, vm]), response([{ ...vm, domainid: 'foreign' }])]) expect(sshKeyPairUsageRows(json, key).state).toBe('error')
})
test('refresh preserves rows and pagination while pending and on recoverable error', async () => {
  await c.fetchData(); const old = c.rows; let release; getAPI.mockImplementation(() => new Promise(resolve => { release = resolve })); const pending = c.fetchData(); expect(c.rows).toBe(old); expect(c.pending).toBe(true); release(identity(key)); await flushPromises(); release(response()); await flushPromises(); release(identity(key)); await pending; expect(c.status).toBe('ready')
  getAPI.mockRejectedValue({ response: { status: 503 } }); await c.fetchData(); expect(c.rows).toHaveLength(1); expect(c.status).toBe('error'); expect(c.pending).toBe(false)
})
test.each(['account', 'domainid', 'projectid', 'fingerprint', 'id'])('changed %s during VM lookup discards response', async field => {
  let release; getAPI.mockImplementation(api => api === 'listSSHKeyPairs' ? Promise.resolve(identity(key)) : new Promise(resolve => { release = resolve })); const pending = c.fetchData(); await flushPromises(); c.resource[field] = 'changed'; c.reset(); release(response()); await pending; expect(c.rows).toEqual([])
})
test.each(['project', 'session', 'user', 'tab', 'query'])('%s switch discards previous response', async field => {
  let release; getAPI.mockImplementation(api => api === 'listSSHKeyPairs' ? Promise.resolve(identity(key)) : new Promise(resolve => { release = resolve })); const pending = c.fetchData(); await flushPromises()
  if (field === 'project') c.$store.getters.project = { id: 'new' }; if (field === 'session') c.$store.state.user.token = 'new'; if (field === 'user') c.$store.getters.userInfo.id = 'new'; if (field === 'tab') c.tab = 'details'; if (field === 'query') c.keyword = 'new'
  release(response()); await pending; expect(c.rows).toEqual([])
})
test('post-lookup replacement fingerprint prevents publishing old association', async () => { let reads = 0; getAPI.mockImplementation(api => Promise.resolve(api === 'listSSHKeyPairs' ? identity({ ...key, fingerprint: ++reads > 1 ? 'replacement' : key.fingerprint }) : response())); await c.fetchData(); expect(c.status).toBe('mismatch'); expect(c.rows).toEqual([]) })
test('search/page/sort use router history and preserve owner; page size change starts at one', () => {
  c.search('MiXeD'); expect(c.$router.push.mock.calls[0][0].query).toMatchObject({ account: key.account, usagekeyword: 'MiXeD', usagepage: '1', tab: 'ssh.keypair.usage' })
  c.changePage(3, 10); expect(c.$router.push.mock.calls[1][0].query).toMatchObject({ usagepage: '1', usagepagesize: '10' }); c.sort({}, {}, { field: 'name', order: 'descend' }); expect(c.$router.push.mock.calls[2][0].query).toMatchObject({ usagesort: 'name', usageorder: 'desc' })
})
test('URL restore validates page, size, keyword and current-page sort', () => { c.$route.query = { usagepage: '-1', usagepagesize: '999999', usagekeyword: ['bad'], usagesort: 'bad' }; c.readQuery(); expect([c.page, c.pagesize, c.keyword, c.sortkey]).toEqual([1, 20, '', '']); c.$route.query = { usagepage: '2', usagepagesize: '10', usagekeyword: 'MiXeD', usagesort: 'name', usageorder: 'asc' }; c.readQuery(); expect([c.page, c.pagesize, c.keyword, c.sortkey, c.sortorder]).toEqual([2, 10, 'MiXeD', 'name', 'asc']) })
test('out-of-range page moves to last real page', async () => { c.page = 4; c.pagesize = 10; await c.fetchData(); expect(c.$router.push.mock.calls[0][0].query).toMatchObject({ usagepage: '1', usagepagesize: '10' }) })
test('SSH tab switch uses Vue router, leaving other resource history behavior intact', () => {
  const x = { $route: { path: '/ssh/' + key.id, query: { account: key.account }, meta: { name: 'ssh' } }, $router: { push: jest.fn() }, $emit: jest.fn() }; ResourceView.methods.onTabChange.call(x, 'ssh.keypair.usage'); expect(x.$router.push).toHaveBeenCalledWith({ path: x.$route.path, query: { account: key.account, tab: 'ssh.keypair.usage' } }); expect(x.$route.query.tab).toBeUndefined()
})

test('successful empty results remain visible without table loading during refresh or retry', async () => {
  getAPI.mockImplementation(api => Promise.resolve(api === 'listSSHKeyPairs' ? identity(key) : response([])))
  await c.fetchData(); expect(c.status).toBe('ready'); expect(c.count).toBe(0)
  let release; getAPI.mockImplementation(api => api === 'listSSHKeyPairs' ? Promise.resolve(identity(key)) : new Promise(resolve => { release = resolve }))
  const pending = c.fetchData(); await flushPromises(); expect(c.pending).toBe(true); expect(c.tableLoading).toBe(false); expect(c.status).toBe('ready'); expect(c.rows).toEqual([])
  release(response([])); await pending; expect(c.tableLoading).toBe(false)
  getAPI.mockRejectedValue({ response: { status: 503 } }); await c.fetchData(); expect(c.status).toBe('error'); expect(c.tableLoading).toBe(false)
  getAPI.mockImplementation(() => new Promise(resolve => { release = resolve })); const retry = c.fetchData(); expect(c.pending).toBe(true); expect(c.tableLoading).toBe(false); release(identity({ ...key, fingerprint: 'changed' })); await retry
})
test('first load and changed search still show initial loading until a valid result arrives', async () => {
  let release; getAPI.mockImplementation(() => new Promise(resolve => { release = resolve })); const first = c.fetchData(); expect(c.tableLoading).toBe(true); release(identity({ ...key, fingerprint: 'changed' })); await first
  getAPI.mockImplementation(api => Promise.resolve(api === 'listSSHKeyPairs' ? identity(key) : response([]))); await c.fetchData(); expect(c.tableLoading).toBe(false)
  c.keyword = 'new search'; c.reset(); getAPI.mockImplementation(() => new Promise(resolve => { release = resolve })); const next = c.fetchData(); expect(c.tableLoading).toBe(true); release(identity({ ...key, fingerprint: 'changed' })); await next
})

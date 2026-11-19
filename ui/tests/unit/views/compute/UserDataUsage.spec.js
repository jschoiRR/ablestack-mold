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

import Usage from '@/views/compute/UserDataUsage.vue'
import { getAPI } from '@/api'
import { userDataUsageParams, userDataUsageRows } from '@/utils/userDataUsage'
const flushPromises = () => new Promise(resolve => setImmediate(resolve))
jest.mock('@/api', () => ({ getAPI: jest.fn() }))
const source = { id: '11111111-1111-4111-8111-111111111111', name: 'test', account: 'admin', domainid: '22222222-2222-4222-8222-222222222222' }
const row = { id: '33333333-3333-4333-8333-333333333333', userdataid: source.id, displayname: 'VM', account: 'other', state: 'Stopped' }
const identity = () => ({ listuserdataresponse: { userdata: [source], count: 1 } })
const response = (rows = [row], count = rows.length) => ({ listvirtualmachinesresponse: { virtualmachine: rows, count } })
let c
beforeEach(() => {
  jest.clearAllMocks()
  c = { resource: { ...source }, resourceType: 'vms', tab: 'userdata.usage.vms', resourceActionReady: () => true, $route: { path: '/userdata/' + source.id, query: {} }, $router: { push: jest.fn() }, $store: { getters: { apis: { listVirtualMachines: { params: [{ name: 'userdataid' }] } }, userInfo: { id: 'user', roletype: 'Admin' }, project: {} }, state: { user: { token: 'token' } } }, $t: x => x }
  Object.assign(c, Usage.data.call(c)); Object.entries(Usage.methods).forEach(([key, fn]) => { c[key] = fn.bind(c) }); Object.entries(Usage.computed).forEach(([key, fn]) => { Object.defineProperty(c, key, { get: () => fn.call(c) }) })
  getAPI.mockImplementation(api => Promise.resolve(api === 'listUserData' ? identity() : response()))
})
test('uses UUID server query, ACL permits cross-owner rows, checks identity before and after', async () => { await c.fetchData(); expect(c.status).toBe('ready'); expect(c.rows[0].name).toBe('VM'); expect(getAPI.mock.calls.map(x => x[0])).toEqual(['listUserData', 'listVirtualMachines', 'listUserData']); expect(getAPI.mock.calls[1][1]).toMatchObject({ userdataid: source.id, listall: true, ignoreproject: true, page: 1, pagesize: 20 }); expect(getAPI.mock.calls[1][1].account).toBeUndefined() })
test('project is explicit and library project wins over ambient context', () => { expect(userDataUsageParams({ ...source, projectid: 'own' }, 'templates', { id: 'ambient' }, 'User')).toMatchObject({ userdataid: source.id, projectid: 'own', templatefilter: 'executable', showunique: true }); expect(userDataUsageParams(source, 'isos', { id: 'ambient' }, 'Admin').projectid).toBe('ambient') })
test('unsupported filter never issues an unfiltered request', async () => { c.$store.getters.apis.listVirtualMachines.params = []; await c.fetchData(); expect(c.status).toBe('unsupported'); expect(getAPI).not.toHaveBeenCalled() })
test('malformed counts and wrong UUIDs cannot become a successful empty list', () => { for (const json of [{}, response([row], '1'), response([{ ...row, userdataid: 'other' }]), response([row, row])]) expect(() => userDataUsageRows(json, source, 'vms')).toThrow(); expect(userDataUsageRows(response([]), source, 'vms')).toEqual({ rows: [], count: 0 }) })
test('same scope refresh retains rows and recoverable failures retain stale feedback', async () => { await c.fetchData(); const old = c.rows; getAPI.mockRejectedValue({ response: { status: 503 } }); const p = c.fetchData(); expect(c.tableLoading).toBe(false); await p; expect(c.rows).toBe(old); expect(c.status).toBe('error') })
test('empty successful result remains empty while refresh is pending', async () => { getAPI.mockImplementation(api => Promise.resolve(api === 'listUserData' ? identity() : response([]))); await c.fetchData(); let release; getAPI.mockImplementation(() => new Promise(resolve => { release = resolve })); const p = c.fetchData(); expect(c.tableLoading).toBe(false); expect(c.rows).toEqual([]); release({ listuserdataresponse: {} }); await p })
test.each(['project', 'tab', 'user', 'query'])('%s changes discard late responses', async change => { let release; getAPI.mockImplementation(api => api === 'listUserData' ? Promise.resolve(identity()) : new Promise(resolve => { release = resolve })); const p = c.fetchData(); await flushPromises(); if (change === 'project') c.$store.getters.project = { id: 'next' }; if (change === 'tab') c.tab = 'details'; if (change === 'user') c.$store.getters.userInfo.id = 'next'; if (change === 'query') c.keyword = 'next'; release(response()); await p; expect(c.rows).toEqual([]) })
test('search/page/sort state stays in tab-specific router history', () => { c.search('Find'); expect(c.$router.push.mock.calls[0][0].query).toMatchObject({ tab: 'userdata.usage.vms', vmskeyword: 'Find', vmspage: '1' }); c.changePage(2, 10); expect(c.$router.push.mock.calls[1][0].query).toMatchObject({ vmspage: '1', vmspagesize: '10' }) })
test.each([403, 432, 503])('HTTP %s is not zero references', async code => { getAPI.mockRejectedValue({ response: { status: code } }); await c.fetchData(); expect(c.status).toBe(code === 503 ? 'error' : 'forbidden'); expect(c.status).not.toBe('ready') })

test('invalid source never produces an unfiltered reference query', () => { expect(() => userDataUsageParams({ ...source, id: '' }, 'vms', {}, 'Admin')).toThrow() })

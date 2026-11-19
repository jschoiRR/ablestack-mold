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

import Members from '@/views/compute/InstanceGroupMembers'
import { getAPI, postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
const id = '11111111-1111-1111-1111-111111111111'
const vmid = '22222222-2222-2222-2222-222222222222'
const other = '33333333-3333-3333-3333-333333333333'
const group = { id, name: 'A', account: 'admin', domainid: other }
const vm = { id: vmid, name: 'vm', state: 'Stopped', account: 'admin', domainid: other, groupid: id }
const response = rows => ({ listvirtualmachinesresponse: { virtualmachine: rows, count: rows.length } })
let ctx
beforeEach(() => {
  jest.clearAllMocks(); getAPI.mockResolvedValue(response([vm]))
  ctx = { resource: { ...group }, active: true, $store: { getters: { apis: { listVirtualMachines: {}, updateVirtualMachine: { params: [{ name: 'groupid' }, { name: 'cleargroup' }] } }, userInfo: { id: 'user' }, project: {} }, state: { user: { token: 'session' } } }, $route: { path: '/vmgroup/' + id, query: {} }, $router: { replace: jest.fn() }, $refs: { membersTable: { resetSelection: jest.fn() }, candidateTable: { resetSelection: jest.fn() } }, $nextTick: cb => cb(), resourceActionReady: () => true, parentFetchData: jest.fn() }
  Object.assign(ctx, Members.data.call(ctx)); Object.entries(Members.methods).forEach(([key, fn]) => { ctx[key] = fn.bind(ctx) })
  Object.entries(Members.computed).forEach(([key, fn]) => { Object.defineProperty(ctx, key, { get: () => fn.call(ctx) }) })
})
test('member request has group/owner and no UI-only query', async () => {
  await ctx.fetchData(); expect(ctx.status).toBe('ready'); expect(ctx.rows[0].id).toBe(vmid)
  expect(getAPI).toHaveBeenCalledWith('listVirtualMachines', { account: 'admin', domainid: other, groupid: id, state: 'present', listall: true, page: 1, pagesize: 20 }, { preserveOnFailure: true, timeout: 20000 })
})
test('normal zero differs from first error/forbidden and never grants mutation on a failure', async () => {
  getAPI.mockResolvedValue(response([])); await ctx.fetchData(); expect(ctx.status).toBe('ready'); expect(ctx.actionReason).toBe('')
  getAPI.mockRejectedValue(new Error('private detail')); await ctx.fetchData(); expect(ctx.status).toBe('error'); expect(ctx.actionReason).toBe('message.vm.group.members.refresh')
  getAPI.mockRejectedValue({ response: { status: 403 } }); await ctx.fetchData(); expect(ctx.status).toBe('forbidden'); expect(ctx.rows).toEqual([])
})
test('same-context refresh retains rows until successful completion and retains them after failure', async () => {
  await ctx.fetchData(); const previous = ctx.rows; let done
  getAPI.mockImplementation(() => new Promise(resolve => { done = resolve })); const pending = ctx.fetchData()
  expect(ctx.rows).toBe(previous); expect(ctx.actionReason).toBe('message.vm.group.members.refresh')
  done(response([vm])); await pending; getAPI.mockRejectedValue(new Error('offline')); await ctx.fetchData()
  expect(ctx.rows[0].id).toBe(vmid); expect(ctx.memberActions).toEqual([])
})
test('target/owner changes clear old rows and reject late completion', async () => {
  await ctx.fetchData(); const resolves = []; getAPI.mockImplementation(() => new Promise(resolve => resolves.push(resolve)))
  const old = ctx.fetchData(); ctx.resource = { ...group, id: other }; ctx.resetContext(); const current = ctx.fetchData()
  resolves[1](response([{ ...vm, groupid: other }])); await current; resolves[0](response([vm])); await old
  expect(ctx.rows[0].groupid).toBe(other); expect(ctx.pending).toBe(false)
})
test('search/page/sort update only member URL fields and retain tab and owner URL values', () => {
  ctx.$route.query = { tab: 'members', account: 'admin' }; ctx.searchMembers('한글')
  expect(ctx.$router.replace).toHaveBeenCalledWith({ query: { tab: 'members', account: 'admin', memberkeyword: '한글', memberpage: '1' } })
  ctx.sortMembers({}, {}, { field: 'state', order: 'ascend' }); expect(ctx.$router.replace.mock.calls[1][0].query).toEqual({ tab: 'members', account: 'admin', membersort: 'state', memberorder: 'asc' })
})
test('missing VM-read permission makes no request and does not expose stale members', async () => {
  await ctx.fetchData(); delete ctx.$store.getters.apis.listVirtualMachines; getAPI.mockClear(); await ctx.fetchData()
  expect(ctx.status).toBe('forbidden'); expect(ctx.rows).toEqual([]); expect(getAPI).not.toHaveBeenCalled()
})
test('candidate query race cannot publish previous owner results', async () => {
  ctx.modalVisible = true; ctx.modalSource = group; ctx.modalScope = ctx.scope
  const resolves = []; getAPI.mockImplementation(() => new Promise(resolve => resolves.push(resolve)))
  const old = ctx.fetchCandidates(); ctx.candidateKeyword = 'new'; const current = ctx.fetchCandidates()
  resolves[1](response([{ ...vm, name: 'current' }])); await current; resolves[0](response([{ ...vm, name: 'late' }])); await old
  expect(ctx.candidates[0].name).toBe('current')
})
test('candidate already in target cannot become a confirmed new link', () => {
  ctx.mode = 'link'; ctx.modalSource = group; ctx.candidateStatus = 'ready'; ctx.candidates = [vm]; ctx.candidateSelection = [vmid]
  ctx.confirmSelection(); expect(ctx.stage).toBe('choose'); expect(ctx.targets).toEqual([])
})

test('disposed view rejects delayed reads and remaining membership writes', async () => {
  let done; getAPI.mockImplementation(() => new Promise(resolve => { done = resolve }))
  const request = ctx.fetchData(); Members.beforeUnmount.call(ctx); done(response([vm])); await request
  expect(ctx.rows).toEqual([])
  ctx.status = 'ready'; ctx.pending = false; ctx.modalSource = group; ctx.modalScope = ctx.scope; ctx.targets = [vm]
  await ctx.execute(); expect(postAPI).not.toHaveBeenCalled()
})

test('cancelled row membership action restores the connected table row focus', () => {
  ctx.status = 'ready'; const row = document.createElement('tr'); row.setAttribute('tabindex', '0'); document.body.appendChild(row)
  row.dataset.rowKey = vmid
  ctx.$el = document.createElement('div'); document.body.appendChild(ctx.$el); ctx.$el.appendChild(row)
  ctx.$refs.membersTable.$el = document.createComment('fragment')
  ctx.openAction('clear', [vm]); ctx.closeModal()
  expect(ctx.mode).toBe('clear'); expect(ctx.stage).toBe('confirm')
  expect(document.activeElement).toBe(row); ctx.$el.remove()
})

test('project modal displays the signed-in user when project API omits account', () => {
  ctx.modalSource = { id, name: 'A', projectid: other }
  ctx.$store.getters.userInfo.username = 'project-member'
  expect(ctx.modalAccount).toBe('project-member')
})
test('admin viewing another account keeps the group owner account in the modal', () => {
  ctx.modalSource = { id, name: 'A', account: 'vm-owner' }
  ctx.$store.getters.userInfo.username = 'admin'
  expect(ctx.modalAccount).toBe('vm-owner')
})

test('forbidden member refresh hides stale aggregates until successful recovery', async () => {
  ctx.resource.virtualmachinecount = 2
  ctx.resource.virtualmachinestates = { Running: 1, Stopped: 1 }
  await ctx.fetchData(); expect(ctx.summary.count).toBe(2)
  getAPI.mockRejectedValue({ response: { status: 403 } }); await ctx.fetchData()
  expect(ctx.summary).toBeNull(); expect(ctx.rows).toEqual([]); expect(ctx.memberActions).toEqual([])
  getAPI.mockResolvedValue(response([vm])); await ctx.fetchData(); expect(ctx.summary.count).toBe(2)
  delete ctx.$store.getters.apis.listVirtualMachines; expect(ctx.summary).toBeNull()
})

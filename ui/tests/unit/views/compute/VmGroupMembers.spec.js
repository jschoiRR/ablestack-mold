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

import { vmGroupMemberParams, vmGroupMemberRows, vmGroupMemberScope, vmGroupMemberSummary, vmGroupMemberActionReason, vmGroupMemberSnapshot, updateVmGroupMembers } from '@/utils/vmGroupMembers'
import { vmGroupQuery, vmGroupQueryParams } from '@/utils/vmGroupQuery'

const groupId = '11111111-1111-1111-1111-111111111111'
const vmId = '22222222-2222-2222-2222-222222222222'
const otherId = '33333333-3333-3333-3333-333333333333'
const domainid = '44444444-4444-4444-4444-444444444444'
const group = { id: groupId, name: 'A', account: 'admin', domainid }
const target = { ...group, id: otherId, name: 'B' }
const vm = { id: vmId, name: 'vm', displayname: '한글 VM', state: 'Stopped', account: 'admin', domainid, groupid: groupId, group: 'A' }
const vms = (rows, count = rows.length) => ({ listvirtualmachinesresponse: { virtualmachine: rows, count } })
let models, getAPI, postAPI
beforeEach(() => {
  models = new Map([[vmId, { ...vm }]])
  getAPI = jest.fn(async (command, params) => {
    if (command === 'listVirtualMachines') return vms(models.has(params.id) ? [{ ...models.get(params.id) }] : [])
    if (command === 'listInstanceGroups') return { listinstancegroupsresponse: { instancegroup: [{ ...(params.id === groupId ? group : target) }] } }
    return { queryasyncjobresultresponse: { jobstatus: 1 } }
  })
  postAPI = jest.fn(async (command, params) => {
    const model = models.get(params.id); model.groupid = params.cleargroup ? undefined : params.groupid
    return { updatevirtualmachineresponse: { virtualmachine: { ...model } } }
  })
})
test('member and candidate requests bind owner and present lifecycle, never UI query', () => {
  expect(vmGroupMemberParams(group, { keyword: '한글', page: 2, pagesize: 20 })).toEqual({ account: 'admin', domainid, groupid: groupId, state: 'present', keyword: '한글', page: 2, pagesize: 20, listall: true })
  expect(vmGroupMemberParams({ ...group, projectid: otherId }, {}, false)).toEqual({ projectid: otherId, listall: true, state: 'present', page: 1, pagesize: 20 })
})
test('member UI query survives URL cleaning and is excluded from group API', () => {
  const query = vmGroupQuery({ memberkeyword: '한글', memberpage: '2', memberpagesize: '20', membersort: 'name', memberorder: 'asc', tab: 'members' })
  expect(query.memberkeyword).toBe('한글'); expect(query.memberpage).toBe('2'); expect(vmGroupQueryParams(query)).toEqual({})
})
test('count zero is valid, malformed/foreign/deleted/group-mismatched rows are not', () => {
  expect(vmGroupMemberRows(vms([]), group)).toEqual({ rows: [], count: 0 })
  for (const row of [{ ...vm, id: 'invalid' }, { ...vm, account: 'other' }, { ...vm, projectid: otherId }, { ...vm, groupid: otherId }, { ...vm, state: 'Destroyed' }]) expect(() => vmGroupMemberRows(vms([row]), group)).toThrow()
  expect(() => vmGroupMemberRows(vms([vm], -1), group)).toThrow()
  expect(() => vmGroupMemberRows(vms([vm, vm]), group)).toThrow()
  expect(() => vmGroupMemberRows({ listvirtualmachinesresponse: { virtualmachine: {} } }, group)).toThrow()
})
test('missing summary is unknown while empty and state sums are exact', () => {
  expect(vmGroupMemberSummary(group)).toBeNull()
  expect(vmGroupMemberSummary({ ...group, virtualmachinecount: 0, virtualmachinestates: {} })).toEqual({ count: 0, states: [] })
  expect(vmGroupMemberSummary({ ...group, virtualmachinecount: 3, virtualmachinestates: { Running: 1, Stopped: 2 } }).count).toBe(3)
  expect(vmGroupMemberSummary({ ...group, virtualmachinecount: 1, virtualmachinestates: { Running: 2 } })).toBeNull()
})
test('session/project/owner/target changes separate operation scopes', () => {
  const store = { getters: { userInfo: { id: 'user' }, project: {} }, state: { user: { token: 'token' } } }
  const route = { path: '/vmgroup/' + groupId }; const before = vmGroupMemberScope(group, store, route)
  store.state.user.token = 'other'; expect(vmGroupMemberScope(group, store, route)).not.toBe(before)
  expect(vmGroupMemberScope(target, store, route)).not.toBe(before)
})
test('permission, server contract and freshness are required for writes', () => {
  expect(vmGroupMemberActionReason(group, [vm], {})).toBe('message.vm.group.members.readonly')
  expect(vmGroupMemberActionReason(group, [vm], { updateVirtualMachine: { params: [] } })).toBe('message.vm.group.members.unsupported')
  const apis = { updateVirtualMachine: { params: [{ name: 'groupid' }, { name: 'cleargroup' }] } }
  expect(vmGroupMemberActionReason(group, [vm], apis, false)).toBe('message.vm.group.members.refresh')
  expect(vmGroupMemberActionReason(group, [{ ...vm, account: 'other' }], apis)).toBe('message.vm.group.members.owner')
})
test.each(['link', 'move', 'clear'])('%s uses only an explicit membership payload and verifies the resulting membership', async mode => {
  if (mode === 'link') models.get(vmId).groupid = undefined
  const row = { ...models.get(vmId) }; const snapshot = vmGroupMemberSnapshot([row]); row.id = otherId
  const destination = mode === 'clear' ? null : mode === 'link' ? group : target
  const results = await updateVmGroupMembers(getAPI, postAPI, group, snapshot, destination)
  expect(results[0].status).toBe('success')
  expect(postAPI).toHaveBeenCalledWith('updateVirtualMachine', { id: vmId, ...(destination ? { groupid: destination.id } : { cleargroup: true }) }, { preserveOnFailure: true, timeout: 20000 })
  expect(postAPI.mock.calls.every(([command]) => command === 'updateVirtualMachine')).toBe(true)
  expect(models.get(vmId).state).toBe('Stopped')
})
test('changed membership after confirmation is preserved and not written', async () => {
  const snapshot = vmGroupMemberSnapshot([vm]); models.get(vmId).groupid = otherId
  const result = await updateVmGroupMembers(getAPI, postAPI, group, snapshot, null)
  expect(result[0].status).toBe('failed'); expect(result[0].message).toBe('message.vm.group.members.changed'); expect(postAPI).not.toHaveBeenCalled()
})
test('a disappeared VM or target never receives a write', async () => {
  models.clear(); expect((await updateVmGroupMembers(getAPI, postAPI, group, [vm], target))[0].status).toBe('failed'); expect(postAPI).not.toHaveBeenCalled()
  models.set(vmId, { ...vm }); getAPI.mockImplementation(async command => command === 'listVirtualMachines' ? vms([vm]) : { listinstancegroupsresponse: {} })
  expect((await updateVmGroupMembers(getAPI, postAPI, group, [vm], target))[0].status).toBe('failed'); expect(postAPI).not.toHaveBeenCalled()
})
test('foreign owner results and foreign target cannot move the VM', async () => {
  models.get(vmId).account = 'other'; await updateVmGroupMembers(getAPI, postAPI, group, [vm], target); expect(postAPI).not.toHaveBeenCalled()
  models.get(vmId).account = 'admin'; await updateVmGroupMembers(getAPI, postAPI, group, [vm], { ...target, account: 'other' }); expect(postAPI).not.toHaveBeenCalled()
})
test('clear cannot operate on a VM from another group snapshot', async () => {
  await updateVmGroupMembers(getAPI, postAPI, group, [{ ...vm, groupid: otherId }], null); expect(postAPI).not.toHaveBeenCalled()
})
test('context change after one write stops remaining writes', async () => {
  models.set(otherId, { ...vm, id: otherId }); let current = true
  postAPI.mockImplementation(async (_, params) => { models.get(params.id).groupid = target.id; current = false; return { updatevirtualmachineresponse: {} } })
  await updateVmGroupMembers(getAPI, postAPI, group, [vm, { ...vm, id: otherId }], target, () => current)
  expect(postAPI).toHaveBeenCalledTimes(1)
})
test('partial failure retries failed snapshots and never repeats successful writes', async () => {
  models.set(otherId, { ...vm, id: otherId }); let failed = false
  const success = postAPI.getMockImplementation()
  postAPI.mockImplementation(async (...args) => { if (args[1].id === otherId && !failed) { failed = true; throw Object.assign(new Error('HTTP500'), { response: { status: 500, data: { errorresponse: { errorcode: 530, errortext: 'private detail' } } } }) } return success(...args) })
  const snapshots = [vm, { ...vm, id: otherId }]
  const first = await updateVmGroupMembers(getAPI, postAPI, group, snapshots, target)
  expect(first.map(row => row.status)).toEqual(['success', 'failed']); expect(first[1].message).not.toContain('private')
  const second = await updateVmGroupMembers(getAPI, postAPI, group, snapshots, target, () => true, first)
  expect(second.map(row => row.status)).toEqual(['success', 'success']); expect(postAPI.mock.calls.filter(([, params]) => params.id === vmId)).toHaveLength(1)
})
test('unverifiable post result is unknown rather than success', async () => {
  postAPI.mockResolvedValue({ updatevirtualmachineresponse: {} })
  const results = await updateVmGroupMembers(getAPI, postAPI, group, [vm], target)
  expect(results[0].status).toBe('unknown')
})
test('asynchronous group update waits for job completion before result verification', async () => {
  const success = postAPI.getMockImplementation(); postAPI.mockImplementation(async (...args) => { await success(...args); return { updatevirtualmachineresponse: { jobid: otherId } } })
  expect((await updateVmGroupMembers(getAPI, postAPI, group, [vm], target))[0].status).toBe('success')
  expect(getAPI).toHaveBeenCalledWith('queryAsyncJobResult', { jobid: otherId }, { preserveOnFailure: true, timeout: 20000 })
})

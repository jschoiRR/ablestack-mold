// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import { createVmGroupLookup, vmGroupOwner, vmGroupOwnerKey, vmGroupLookupReady } from '@/utils/vmGroupLookup'
import EditVM from '@/views/compute/EditVM.vue'
import { getAPI, postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))

const owner = { account: 'user-a', domainid: 'domain-a' }
const group = { ...owner, id: 'group-a', name: 'same-name' }
const reply = items => ({ listinstancegroupsresponse: { count: items.length, instancegroup: items } })
function deferred () {
  let resolvePending, rejectPending
  const promise = new Promise((resolve, reject) => { resolvePending = resolve; rejectPending = reject })
  return { promise, resolve: resolvePending, reject: rejectPending }
}
function lookup (api) {
  let state
  const loader = createVmGroupLookup(api, value => { state = value })
  return { loader, state: () => state }
}

it.each([
  ['admin own', { account: 'admin', domainid: 'root' }, { account: 'admin', domainid: 'root' }],
  ['admin other', owner, owner],
  ['regular', owner, owner],
  ['project', { ...owner, projectid: 'project-a' }, { projectid: 'project-a' }],
  ['restricted', owner, owner]
])('derives %s candidates exclusively from the target owner', async (role, target, expected) => {
  expect(vmGroupOwner(target)).toEqual(expected)
  const api = jest.fn().mockResolvedValue(reply([]))
  const task = lookup(api)
  await task.loader.load(target)
  expect(api).toHaveBeenCalledWith('listInstanceGroups', { ...expected, listall: true, page: 1, pagesize: 500 }, expect.objectContaining({ preserveOnFailure: true }))
  expect(task.state().status).toBe('empty')
  expect(vmGroupLookupReady(task.state(), target)).toBe(true)
})
it.each([{}, { account: 'admin' }, { domainid: 'root' }, null])('never falls back to a login owner for incomplete scope %p', async target => {
  const api = jest.fn()
  const task = lookup(api)
  await task.loader.load(target)
  expect(api).not.toHaveBeenCalled()
  expect(task.state().status).toBe('unavailable')
})
it('preserves the exact existing group UUID', async () => {
  const task = lookup(jest.fn().mockResolvedValue(reply([group])))
  await task.loader.load(owner)
  expect(task.state().opts).toEqual([{ id: 'group-a', label: 'same-name', value: 'same-name', groupId: 'group-a' }])
  expect(vmGroupLookupReady(task.state(), { account: 'other', domainid: owner.domainid })).toBe(false)
})
it.each([
  { ...group, account: 'admin' },
  { ...group, domainid: 'other-domain' },
  { ...group, projectid: 'project-a' },
  { id: 'unknown', name: group.name }
])('rejects a same-name foreign or unverifiable candidate %p', async foreign => {
  const task = lookup(jest.fn().mockResolvedValue(reply([group, foreign])))
  await task.loader.load(owner)
  expect(task.state()).toMatchObject({ status: 'error', opts: [] })
})
it('rejects a different project even when the name and domain match', async () => {
  const task = lookup(jest.fn().mockResolvedValue(reply([{ ...group, projectid: 'other' }])))
  await task.loader.load({ projectid: 'target' })
  expect(task.state().status).toBe('error')
})
it('clears old options immediately and ignores old success, failure and completion', async () => {
  const old = deferred(); const next = deferred(); const rejected = deferred()
  const api = jest.fn().mockResolvedValueOnce(reply([group])).mockImplementationOnce(() => old.promise).mockImplementationOnce(() => rejected.promise).mockImplementationOnce(() => next.promise)
  const task = lookup(api)
  await task.loader.load(owner)
  const a = task.loader.load(owner)
  const b = task.loader.load(owner)
  const c = task.loader.load({ projectid: 'project-a' })
  expect(task.state()).toMatchObject({ status: 'loading', opts: [], scopeKey: vmGroupOwnerKey({ projectid: 'project-a' }) })
  old.resolve(reply([group])); rejected.reject({ response: { status: 403 } })
  await Promise.all([a, b])
  expect(task.state().status).toBe('loading')
  next.resolve(reply([{ id: 'p', name: group.name, projectid: 'project-a' }]))
  await c
  expect(task.state().opts[0].groupId).toBe('p')
})
it('does not publish a late result after the editor is closed', async () => {
  const pending = deferred()
  const publish = jest.fn()
  const loader = createVmGroupLookup(() => pending.promise, publish)
  const work = loader.load(owner)
  loader.dispose(); pending.resolve(reply([group])); await work
  expect(publish).toHaveBeenCalledTimes(1)
})
it.each([401, 403, 432, 531])('distinguishes permission failure %i from a legitimate empty list', async code => {
  const api = jest.fn().mockRejectedValue({ response: { data: { errorresponse: { errorcode: code } } } })
  const task = lookup(api)
  await task.loader.load(owner)
  expect(task.state()).toMatchObject({ status: 'forbidden', opts: [] })
  expect(vmGroupLookupReady(task.state(), owner)).toBe(false)
})
it('handles role permission loss without retaining options or making a forbidden query', async () => {
  const api = jest.fn().mockResolvedValue(reply([group]))
  const task = lookup(api)
  await task.loader.load(owner); await task.loader.load(owner, false)
  expect(api).toHaveBeenCalledTimes(1)
  expect(task.state()).toMatchObject({ status: 'forbidden', opts: [] })
})
it.each([new Error('timeout'), {}, { listinstancegroupsresponse: { instancegroup: {} } }])('rejects failed or malformed lookups and permits a successful retry', async failure => {
  const api = jest.fn().mockRejectedValueOnce(failure).mockResolvedValueOnce(reply([]))
  const task = lookup(api)
  await task.loader.load(owner)
  expect(task.state().status).toBe('error')
  await task.loader.load(owner)
  expect(task.state().status).toBe('empty')
})
it('publishes only after the last page and rejects a partial failure', async () => {
  const last = deferred()
  const api = jest.fn().mockResolvedValueOnce({ listinstancegroupsresponse: { count: 2, instancegroup: [group] } }).mockImplementationOnce(() => last.promise)
  const task = lookup(api)
  const work = task.loader.load(owner)
  await Promise.resolve()
  expect(task.state()).toMatchObject({ status: 'loading', opts: [] })
  last.reject(new Error('last page failed')); await work
  expect(task.state()).toMatchObject({ status: 'error', opts: [] })
})
it('fetches and validates multiple pages without duplicate options', async () => {
  const api = jest.fn().mockResolvedValueOnce({ listinstancegroupsresponse: { count: 2, instancegroup: [group] } })
    .mockResolvedValueOnce({ listinstancegroupsresponse: { count: 2, instancegroup: [{ ...group, id: 'second', name: 'B' }] } })
  const task = lookup(api)
  await task.loader.load(owner)
  expect(api.mock.calls[1][1].page).toBe(2)
  expect(task.state().opts).toHaveLength(2)
})
it('rejects repeated pages rather than interpreting an incomplete list as complete', async () => {
  const api = jest.fn().mockResolvedValue({ listinstancegroupsresponse: { count: 2, instancegroup: [group] } })
  const task = lookup(api)
  await task.loader.load(owner)
  expect(api).toHaveBeenCalledTimes(2)
  expect(task.state().status).toBe('error')
})

describe('VM editor auxiliary data and submit guards', () => {
  beforeEach(() => { getAPI.mockReset(); postAPI.mockReset() })
  it('rejects stale editor values before the resource-change watcher has reset the form', async () => {
    const ctx = {
      formRef: { value: { validate: () => Promise.resolve() } },
      form: { group: 'old VM input' },
      initialResourceContextKey: 'before',
      resourceContextKey: 'after',
      $message: { error: jest.fn() },
      $t: key => key
    }
    await EditVM.methods.handleSubmit.call(ctx)
    await Promise.resolve()
    expect(postAPI).not.toHaveBeenCalled()
    expect(ctx.$message.error).toHaveBeenCalledWith('message.vm.group.lookup.blocked')
  })
  it.each([
    ['fetchTemplateData', 'template', 'templateStatus', 'listtemplatesresponse', 'template'],
    ['fetchServiceOfferingData', 'serviceOffering', 'offeringStatus', 'listserviceofferingsresponse', 'serviceoffering']
  ])('handles empty, failed and late %s without an unhandled exception', async (method, target, status, envelope, items) => {
    const ctx = { resource: { templateid: 'template', serviceofferingid: 'offering' }, metadataGeneration: 1 }
    getAPI.mockResolvedValueOnce({ [envelope]: {} })
    await EditVM.methods[method].call(ctx)
    expect(ctx[target]).toEqual({}); expect(ctx[status]).toBe('empty')
    getAPI.mockRejectedValueOnce(new Error('lookup failed'))
    await EditVM.methods[method].call(ctx)
    expect(ctx[target]).toEqual({}); expect(ctx[status]).toBe('error')
    const old = deferred(); getAPI.mockImplementationOnce(() => old.promise)
    const work = EditVM.methods[method].call(ctx)
    ctx.metadataGeneration++; ctx[target] = { id: 'new' }; ctx[status] = 'ready'
    old.resolve({ [envelope]: { [items]: [{ id: 'old' }] } }); await work
    expect(ctx[target]).toEqual({ id: 'new' }); expect(ctx[status]).toBe('ready')
  })
  it('uses the VM owner even when the login user and selected project differ', async () => {
    const ctx = { resource: owner, $store: { getters: { userInfo: { account: 'admin' }, project: { id: 'other' }, apis: { listInstanceGroups: {} } } }, groupLookup: { load: jest.fn() } }
    EditVM.methods.fetchInstanceGroups.call(ctx)
    expect(ctx.groupLookup.load).toHaveBeenCalledWith(owner, true)
  })
  it.each(['loading', 'forbidden', 'error', 'unavailable'])('blocks group mutation while lookup is %s', async status => {
    const ctx = {
      formRef: { value: { validate: () => Promise.resolve() } },
      form: { group: 'New' },
      resource: { group: 'A' },
      groupLookupReady: false,
      groups: { status, opts: [] },
      $message: { error: jest.fn() },
      $t: key => key
    }
    await EditVM.methods.handleSubmit.call(ctx); await Promise.resolve()
    expect(postAPI).not.toHaveBeenCalled(); expect(ctx.form.group).toBe('New')
    expect(ctx.$message.error).toHaveBeenCalledWith('message.vm.group.lookup.blocked')
  })
  it('blocks a programmatic dynamic-scaling change when metadata is absent', async () => {
    const ctx = {
      formRef: { value: { validate: () => Promise.resolve() } },
      form: { isdynamicallyscalable: true },
      initialForm: { isdynamicallyscalable: false },
      resource: {},
      dynamicScalingEditable: false,
      $message: { error: jest.fn() },
      $t: key => key
    }
    await EditVM.methods.handleSubmit.call(ctx); await Promise.resolve()
    expect(postAPI).not.toHaveBeenCalled()
    expect(ctx.$message.error).toHaveBeenCalledWith('message.vm.edit.metadata.unavailable')
  })
})

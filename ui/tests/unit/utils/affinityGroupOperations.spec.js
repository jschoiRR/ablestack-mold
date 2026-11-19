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

import { affinityOwnerParams, affinityOwnerKey, affinityVmCanChange, affinityApiError, clearAffinityOperations, getAffinityOperation } from '@/utils/affinityGroupOperations'
const flush = async () => { for (let i = 0; i < 16; i++) await Promise.resolve() }
const vm = { id: 'vm', domainid: 'domain', account: 'owner', state: 'Stopped', hypervisor: 'KVM' }
const receipt = { updatevirtualmachineresponse: { jobid: 'job' } }
function setup (options = {}) {
  const get = jest.fn().mockResolvedValue({ queryasyncjobresultresponse: { jobstatus: 1 } })
  const post = jest.fn().mockResolvedValue(receipt)
  const op = getAffinityOperation('scope', 'vm', { get, post, ...options })
  return { op, get, post }
}
beforeEach(() => { clearAffinityOperations(); jest.useFakeTimers() })
afterEach(() => { clearAffinityOperations(); jest.useRealTimers() })

test('account uses supported name/domain and project scope never inherits caller account', () => {
  expect(affinityOwnerParams(vm)).toEqual({ domainid: 'domain', account: 'owner' })
  expect(affinityOwnerParams({ ...vm, projectid: 'project' })).toEqual({ domainid: 'domain', projectid: 'project' })
  expect(affinityOwnerKey({ ...vm, account: 'other' })).not.toBe(affinityOwnerKey(vm))
})
test.each([{ accountid: 'id', domainid: 'domain' }, { account: 'owner' }, {}])('incomplete owner %p never broadens the query', owner => {
  expect(() => affinityOwnerParams(owner)).toThrow('message.affinity.owner.unavailable')
})
test.each(['Running', 'Starting', 'Stopping', 'Destroyed'])('%s VM cannot be changed', state => {
  expect(affinityVmCanChange({ ...vm, state })).toBe(false)
})
test.each([{ hypervisor: 'External' }, { vmtype: 'sharedfsvm' }, { vmtype: 'cksnode' }])('unsupported VM %p cannot be changed', unsupported => {
  expect(affinityVmCanChange({ ...vm, ...unsupported })).toBe(false)
})
test('server error envelope is parsed without exposing transport config', () => {
  expect(affinityApiError({ response: { data: { updatevirtualmachineresponse: { errorcode: 530, errortext: 'Owner rejected' } } } })).toBe('Owner rejected')
  expect(affinityApiError({ response: { data: { errorresponse: { errorcode: 431 } } } })).toBe('message.affinity.operation.failed')
  expect(affinityApiError({ config: { data: 'private' } })).toBe('')
})
test('client receipt context is correlated without being sent to the API', async () => {
  const { op, post } = setup()
  const params = { id: 'vm', affinitygroupids: 'a' }
  await op.run('updateVMAffinityGroup', params, async () => {}, 'group:1')
  expect(op.snapshot().context).toBe('group:1')
  expect(post).toHaveBeenCalledWith('updateVMAffinityGroup', params, expect.any(Object))
  expect(post.mock.calls[0][1]).not.toHaveProperty('context')
  op.acknowledge(); expect(op.snapshot()).toMatchObject({ status: 'idle', context: '' })
})
test('a receipt is pending until jobstatus=1; repeat submissions are deduplicated', async () => {
  const { op, get, post } = setup()
  get.mockResolvedValueOnce({ queryasyncjobresultresponse: { jobstatus: 0 } })
  const p = op.run('updateVMAffinityGroup', { id: 'vm', affinitygroupids: 'a,b' })
  await flush(); expect(op.snapshot()).toMatchObject({ status: 'pending', jobId: 'job', ids: ['a', 'b'] })
  op.run('updateVMAffinityGroup', { id: 'vm', affinitygroupids: 'different' })
  expect(post).toHaveBeenCalledTimes(1)
  jest.advanceTimersByTime(1500); await flush()
  expect((await p).status).toBe('succeeded')
  expect(get).toHaveBeenCalledWith('queryAsyncJobResult', { jobid: 'job' }, expect.objectContaining({ backgroundJob: true, timeout: 15000 }))
})
test('jobstatus=2 preserves actual failure and desired selection, then permits explicit retry', async () => {
  const { op, get, post } = setup()
  get.mockResolvedValueOnce({ queryasyncjobresultresponse: { jobstatus: 2, jobresult: { errortext: 'Different accounts' } } })
  expect(await op.run('updateVMAffinityGroup', { id: 'vm', affinitygroupids: 'a' })).toMatchObject({ status: 'failed', error: 'Different accounts', ids: ['a'] })
  post.mockResolvedValueOnce({ updatevirtualmachineresponse: { jobid: 'retry-job' } })
  expect((await op.run('updateVMAffinityGroup', { id: 'vm', affinitygroupids: '' })).status).toBe('succeeded')
  expect(post).toHaveBeenLastCalledWith('updateVMAffinityGroup', { id: 'vm', affinitygroupids: '' }, expect.objectContaining({ preserveOnFailure: true }))
})
test('a repeated terminal receipt is rendered as its known result instead of pending forever', async () => {
  const { op, get } = setup()
  get.mockResolvedValue({ queryasyncjobresultresponse: { jobstatus: 2, jobresult: { errortext: 'Denied' } } })
  await op.run('updateVMAffinityGroup', { id: 'vm' })
  expect((await op.run('updateVMAffinityGroup', { id: 'vm' })).status).toBe('failed')
  expect(get).toHaveBeenCalledTimes(1)
})
test('transient query failure retries GET and never repeats POST', async () => {
  const { op, get, post } = setup()
  get.mockRejectedValueOnce(new Error('offline'))
  const p = op.run('updateVMAffinityGroup', { id: 'vm', affinitygroupids: 'a' })
  await flush(); expect(op.snapshot().status).toBe('retrying')
  jest.advanceTimersByTime(1500); await flush()
  expect((await p).status).toBe('succeeded'); expect(post).toHaveBeenCalledTimes(1)
})
test('exhausted queries preserve the receipt and explicit recheck queries the same job only', async () => {
  const { op, get, post } = setup({ maxRetries: 0 })
  get.mockRejectedValueOnce(new Error('offline'))
  expect((await op.run('updateVMAffinityGroup', { id: 'vm' })).status).toBe('unknown')
  op.run('updateVMAffinityGroup', { id: 'vm' }); expect(post).toHaveBeenCalledTimes(1)
  expect((await op.recheck()).status).toBe('succeeded')
  expect(post).toHaveBeenCalledTimes(1)
})
test('bounded timeout can be rechecked without resubmission', async () => {
  const { op, get, post } = setup({ maxDuration: 100 })
  get.mockResolvedValueOnce({ queryasyncjobresultresponse: { jobstatus: 0 } })
  const p = op.run('updateVMAffinityGroup', { id: 'vm' })
  await flush(); jest.advanceTimersByTime(100); await flush()
  expect((await p).status).toBe('timeout')
  expect((await op.recheck()).status).toBe('succeeded'); expect(post).toHaveBeenCalledTimes(1)
})
test.each([{}, { updatevirtualmachineresponse: {} }])('missing receipt %p is unknown, never successful or automatically resubmitted', async response => {
  const { op, post } = setup(); post.mockResolvedValue(response)
  expect((await op.run('updateVMAffinityGroup', { id: 'vm' })).status).toBe('unknown')
  await op.run('updateVMAffinityGroup', { id: 'vm' }); await op.recheck()
  expect(post).toHaveBeenCalledTimes(1)
})
test('POST transport failure is unknown, server rejection is an explicit failure', async () => {
  const { op, post } = setup(); post.mockRejectedValueOnce(new Error('lost response'))
  expect((await op.run('updateVMAffinityGroup', { id: 'vm' })).status).toBe('unknown')
  clearAffinityOperations()
  const other = setup(); other.post.mockRejectedValue({ response: { data: { errorresponse: { errorcode: 531, errortext: 'Denied' } } } })
  expect((await other.op.run('updateVMAffinityGroup', { id: 'vm' })).status).toBe('failed')
})
test('failed preflight sends no mutation and a security change during preflight sends no mutation', async () => {
  const { op, post } = setup()
  await op.run('updateVMAffinityGroup', { id: 'vm' }, async () => { throw new Error('Not stopped') })
  expect(op.snapshot()).toMatchObject({ status: 'failed', error: 'Not stopped' }); expect(post).not.toHaveBeenCalled()
  let scope = 'scope'; clearAffinityOperations()
  const other = setup({ getScope: () => scope })
  await other.op.run('updateVMAffinityGroup', { id: 'vm' }, async () => { scope = 'new' })
  expect(other.post).not.toHaveBeenCalled()
})
test('closing an observer keeps a single receipt and reopening observes the same operation', async () => {
  const { op, get } = setup()
  get.mockResolvedValueOnce({ queryasyncjobresultresponse: { jobstatus: 0 } })
  const listener = jest.fn(); const unsubscribe = op.subscribe(listener)
  const p = op.run('updateVMAffinityGroup', { id: 'vm', affinitygroupids: 'a' }); await flush()
  unsubscribe(); const calls = listener.mock.calls.length
  const reopened = getAffinityOperation('scope', 'vm', { get, post: jest.fn() })
  expect(reopened).toBe(op)
  jest.advanceTimersByTime(1500); await flush(); await p
  expect(listener).toHaveBeenCalledTimes(calls)
  expect(reopened.snapshot().status).toBe('succeeded')
})
test('changing security scope invalidates old observations and ignores a late POST receipt', async () => {
  let respond; const { op, post, get } = setup()
  post.mockImplementation(() => new Promise(resolve => { respond = resolve }))
  const observer = jest.fn(); op.subscribe(observer)
  const p = op.run('updateVMAffinityGroup', { id: 'vm' }); await flush()
  getAffinityOperation('new-user', 'vm', { get, post }); const calls = observer.mock.calls.length
  respond(receipt); await p
  expect(observer).toHaveBeenCalledTimes(calls); expect(get).not.toHaveBeenCalled()
})
test('background tracking checks current security scope before any next query', async () => {
  let scope = 'scope'; const { op, get } = setup({ getScope: () => scope })
  get.mockResolvedValueOnce({ queryasyncjobresultresponse: { jobstatus: 0 } })
  const p = op.run('updateVMAffinityGroup', { id: 'vm' }); await flush()
  scope = 'other'; jest.advanceTimersByTime(1500); await flush(); await p
  expect(get).toHaveBeenCalledTimes(1)
})

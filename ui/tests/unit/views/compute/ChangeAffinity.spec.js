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

import { shallowMount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { Alert, Button } from 'ant-design-vue'
import ChangeAffinity from '@/views/compute/ChangeAffinity.vue'
import { getAPI, postAPI } from '@/api'
import { clearAffinityOperations } from '@/utils/affinityGroupOperations'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
const flush = async () => { for (let i = 0; i < 24; i++) { await Promise.resolve(); await nextTick() } }
const resource = { id: 'vm', account: 'owner', domainid: 'domain', domain: 'ROOT', state: 'Stopped', hypervisor: 'KVM', affinitygroup: [] }
let wrappers
function mount (row = {}) {
  const vm = { ...resource, ...row }
  const store = reactive({ state: { user: { token: 'session' } }, getters: { userInfo: { id: 'user' }, project: {} } })
  const success = jest.fn(); const refresh = jest.fn()
  const wrapper = shallowMount(ChangeAffinity, {
    props: { resource: vm },
    global: {
      renderStubDefaultSlot: true,
      provide: { parentFetchData: refresh },
      components: { AAlert: Alert, AButton: Button },
      mocks: { $store: store, $t: key => key, $notification: { success } },
      stubs: { AAlert: false, AButton: false, 'a-form': { template: '<form><slot/></form>' } }
    }
  })
  wrappers.push(wrapper)
  return { wrapper, store, success, refresh }
}
beforeEach(() => {
  clearAffinityOperations(); jest.useFakeTimers(); wrappers = []; jest.clearAllMocks()
  getAPI.mockImplementation((cmd, params) => {
    if (cmd === 'listVirtualMachines') return Promise.resolve({ listvirtualmachinesresponse: { virtualmachine: [resource] } })
    if (cmd === 'queryAsyncJobResult') return Promise.resolve({ queryasyncjobresultresponse: { jobstatus: 1 } })
    return Promise.resolve({ listaffinitygroupsresponse: { count: 1, affinitygroup: [{ id: params.id || 'group', account: 'owner', name: 'eligible' }] } })
  })
  postAPI.mockResolvedValue({ updatevirtualmachineresponse: { jobid: 'job' } })
})
afterEach(() => { wrappers.forEach(w => { if (w.exists()) w.unmount() }); clearAffinityOperations(); jest.useRealTimers() })

test('a group-centered preflight failure without replacement IDs cannot clear the VM dialog selection', async () => {
  const first = mount({ affinitygroup: [{ id: 'kept' }] }); await flush()
  await first.wrapper.vm.affinityOperation.run('updateVMAffinityGroup', { id: 'vm' }, async () => { throw new Error('message.affinity.members.changed') })
  first.wrapper.unmount()
  const again = mount({ affinitygroup: [{ id: 'kept' }, { id: 'external' }] }); await flush()
  expect(again.wrapper.vm.selectedRowKeys).toEqual(['kept', 'external'])
  expect(postAPI).not.toHaveBeenCalled()
})

test('a historical successful group operation does not replace fresh memberships when starting a VM edit', async () => {
  const first = mount({ affinitygroup: [{ id: 'kept' }] }); await flush()
  await first.wrapper.vm.affinityOperation.run('updateVMAffinityGroup', { id: 'vm', affinitygroupids: 'kept' }, async () => {}, 'group:1')
  first.wrapper.unmount()
  const again = mount({ affinitygroup: [{ id: 'kept' }, { id: 'external' }] }); await flush()
  expect(again.wrapper.vm.selectedRowKeys).toEqual(['kept', 'external'])
  expect(again.wrapper.vm.operation.status).toBe('idle')
})

test('a definitive group-centered failure starts a fresh VM edit without importing another workflow draft', async () => {
  const first = mount({ affinitygroup: [{ id: 'kept' }] }); await flush()
  postAPI.mockRejectedValueOnce(Object.assign(new Error('denied'), { response: { data: { errorresponse: { errorcode: 431, errortext: 'denied' } } } }))
  await first.wrapper.vm.affinityOperation.run('updateVMAffinityGroup', { id: 'vm', affinitygroupids: 'kept,target' }, async () => {}, 'group:1')
  first.wrapper.unmount()
  const again = mount({ affinitygroup: [{ id: 'kept' }, { id: 'external' }] }); await flush()
  expect(again.wrapper.vm.selectedRowKeys).toEqual(['kept', 'external'])
  expect(again.wrapper.vm.operation.status).toBe('idle')
})

test('owner-scoped lookup uses account/domain, including server-returned domain shared candidates', async () => {
  getAPI.mockResolvedValue({ listaffinitygroupsresponse: { count: 2, affinitygroup: [{ id: 'owned', account: 'owner' }, { id: 'shared', account: 'system' }] } })
  const { wrapper } = mount(); await flush()
  expect(getAPI).toHaveBeenCalledWith('listAffinityGroups', expect.objectContaining({ account: 'owner', domainid: 'domain' }), expect.any(Object))
  expect(getAPI.mock.calls[0][1]).not.toHaveProperty('accountid')
  expect(wrapper.vm.items.map(g => g.id)).toEqual(['owned', 'shared'])
})
test('project lookup does not borrow the current admin account', async () => {
  const { wrapper } = mount({ projectid: 'project', account: undefined }); await flush()
  expect(getAPI.mock.calls[0][1]).toMatchObject({ projectid: 'project', domainid: 'domain' })
  expect(getAPI.mock.calls[0][1]).not.toHaveProperty('account')
  expect(wrapper.vm.loaded).toBe(true)
})
test('missing owner information fails closed without broad lookup or POST', async () => {
  const { wrapper } = mount({ account: undefined }); await flush()
  expect(getAPI).not.toHaveBeenCalled(); expect(wrapper.vm.canSubmit).toBe(false)
  await wrapper.vm.handleSubmit(); expect(postAPI).not.toHaveBeenCalled()
})
test('lookup failure retains prior rows and selection and offers safe retry', async () => {
  const { wrapper } = mount({ affinitygroup: [{ id: 'existing' }] }); await flush()
  const previous = [...wrapper.vm.items]
  getAPI.mockRejectedValueOnce(new Error('offline'))
  await wrapper.vm.fetchData()
  expect(wrapper.vm.items).toEqual(previous); expect(wrapper.vm.selectedRowKeys).toEqual(['existing'])
  expect(wrapper.vm.canSubmit).toBe(false)
  expect(wrapper.findAll('button').some(button => button.text() === 'label.retry')).toBe(true)
  await wrapper.vm.fetchData(); expect(wrapper.vm.canSubmit).toBe(true)
})
test('receipt alone never announces success; success and refresh occur after final status only', async () => {
  let queryResolve
  getAPI.mockImplementation((cmd, params) => {
    if (cmd === 'listVirtualMachines') return Promise.resolve({ listvirtualmachinesresponse: { virtualmachine: [resource] } })
    if (cmd === 'queryAsyncJobResult') return new Promise(resolve => { queryResolve = resolve })
    return Promise.resolve({ listaffinitygroupsresponse: { count: 1, affinitygroup: [{ id: params.id || 'group' }] } })
  })
  const { wrapper, success, refresh } = mount(); await flush(); wrapper.vm.selectedRowKeys = ['group']
  const p = wrapper.vm.handleSubmit(); await flush()
  expect(wrapper.vm.operation.status).toBe('pending'); expect(success).not.toHaveBeenCalled(); expect(refresh).not.toHaveBeenCalled()
  await wrapper.vm.handleSubmit(); expect(postAPI).toHaveBeenCalledTimes(1)
  queryResolve({ queryasyncjobresultresponse: { jobstatus: 1 } }); await p; await flush()
  expect(success).toHaveBeenCalledTimes(1); expect(refresh).toHaveBeenCalledTimes(1); expect(wrapper.emitted('close-action')).toHaveLength(1)
})
test('actual failed job preserves selection, error and dialog instead of reporting success', async () => {
  const { wrapper, success, refresh } = mount(); await flush(); wrapper.vm.selectedRowKeys = ['group']
  getAPI.mockImplementation((cmd, params) => {
    if (cmd === 'listVirtualMachines') return Promise.resolve({ listvirtualmachinesresponse: { virtualmachine: [resource] } })
    if (cmd === 'queryAsyncJobResult') return Promise.resolve({ queryasyncjobresultresponse: { jobstatus: 2, jobresult: { errorcode: 530, errortext: 'Different accounts' } } })
    return Promise.resolve({ listaffinitygroupsresponse: { affinitygroup: [{ id: params.id }] } })
  })
  await wrapper.vm.handleSubmit(); await flush()
  expect(wrapper.vm.operation).toMatchObject({ status: 'failed', error: 'Different accounts' })
  expect(wrapper.vm.selectedRowKeys).toEqual(['group'])
  expect(success).not.toHaveBeenCalled(); expect(refresh).not.toHaveBeenCalled(); expect(wrapper.emitted('close-action')).toBeFalsy()
  expect(wrapper.text()).toContain('Different accounts')
})
test('an injected foreign selection is rejected by owner-scoped preflight without POST', async () => {
  const { wrapper } = mount(); await flush(); wrapper.vm.selectedRowKeys = ['foreign']
  getAPI.mockImplementation(cmd => Promise.resolve(cmd === 'listVirtualMachines' ? { listvirtualmachinesresponse: { virtualmachine: [resource] } } : { listaffinitygroupsresponse: {} }))
  await wrapper.vm.handleSubmit()
  expect(postAPI).not.toHaveBeenCalled(); expect(wrapper.vm.operation.error).toBe('message.affinity.selection.unavailable')
})
test.each([{ state: 'Running' }, { account: 'new-owner' }, { projectid: 'new-project' }])('fresh VM change %p blocks mutation', async changed => {
  const { wrapper } = mount(); await flush()
  getAPI.mockResolvedValue({ listvirtualmachinesresponse: { virtualmachine: [{ ...resource, ...changed }] } })
  await wrapper.vm.handleSubmit(); expect(postAPI).not.toHaveBeenCalled()
  expect(wrapper.vm.operation.status).toBe('failed')
})
test('empty selection is an explicit clear operation, not an omitted parameter', async () => {
  const { wrapper } = mount({ affinitygroup: [{ id: 'group' }] }); await flush(); wrapper.vm.selectedRowKeys = []
  await wrapper.vm.handleSubmit()
  expect(postAPI.mock.calls[0][1]).toEqual({ id: 'vm', affinitygroupids: '' })
})
test('unknown POST outcome blocks another submit and survives close/reopen', async () => {
  const { wrapper, success } = mount(); await flush(); wrapper.vm.selectedRowKeys = ['group']
  postAPI.mockRejectedValueOnce(new Error('lost response'))
  await wrapper.vm.handleSubmit()
  expect(wrapper.vm.operation.status).toBe('unknown'); expect(wrapper.vm.canSubmit).toBe(false)
  expect(success).not.toHaveBeenCalled(); wrapper.vm.closeAction(); wrapper.unmount()
  const reopened = mount(); await flush()
  expect(reopened.wrapper.vm.operation.status).toBe('unknown'); expect(reopened.wrapper.vm.selectedRowKeys).toEqual(['group'])
  await reopened.wrapper.vm.handleSubmit(); expect(postAPI).toHaveBeenCalledTimes(1)
})
test('real Alert renders recheck after query exhaustion; the button queries the same receipt without another POST', async () => {
  const { wrapper, success } = mount(); await flush()
  getAPI.mockImplementation(cmd => {
    if (cmd === 'queryAsyncJobResult') return Promise.reject(new Error('offline'))
    return Promise.resolve({ listvirtualmachinesresponse: { virtualmachine: [resource] } })
  })
  const pending = wrapper.vm.handleSubmit(); await flush()
  for (const delay of [1500, 3000, 6000]) { jest.advanceTimersByTime(delay); await flush() }
  await pending; await flush()
  expect(wrapper.vm.operation.status).toBe('unknown')
  const recheck = wrapper.findAll('button').find(button => button.text() === 'label.affinity.recheck')
  expect(recheck).toBeDefined(); expect(recheck.attributes('disabled')).toBeUndefined()
  expect(success).not.toHaveBeenCalled()
  getAPI.mockResolvedValue({ queryasyncjobresultresponse: { jobstatus: 1 } })
  await recheck.trigger('click'); await flush()
  expect(success).toHaveBeenCalledTimes(1); expect(postAPI).toHaveBeenCalledTimes(1)
})
test('close/unmount while pending suppresses late component success and refresh callbacks', async () => {
  let queryResolve
  const { wrapper, success, refresh } = mount(); await flush(); wrapper.vm.selectedRowKeys = ['group']
  getAPI.mockImplementation(cmd => cmd === 'queryAsyncJobResult' ? new Promise(resolve => { queryResolve = resolve }) : Promise.resolve(cmd === 'listVirtualMachines' ? { listvirtualmachinesresponse: { virtualmachine: [resource] } } : { listaffinitygroupsresponse: { count: 1, affinitygroup: [{ id: 'group' }] } }))
  const p = wrapper.vm.handleSubmit(); await flush()
  wrapper.vm.closeAction(); wrapper.unmount()
  queryResolve({ queryasyncjobresultresponse: { jobstatus: 1 } }); await p
  expect(success).not.toHaveBeenCalled(); expect(refresh).not.toHaveBeenCalled()
  const reopened = mount(); await flush()
  expect(reopened.wrapper.vm.selectedRowKeys).toEqual(['group'])
  expect(reopened.wrapper.vm.operation.status).toBe('idle')
  expect(reopened.success).not.toHaveBeenCalled(); expect(postAPI).toHaveBeenCalledTimes(1)
})
test('security scope change closes the dialog and suppresses late mutation receipt', async () => {
  let respond
  const { wrapper, store, success } = mount(); await flush()
  postAPI.mockImplementation(() => new Promise(resolve => { respond = resolve }))
  const p = wrapper.vm.handleSubmit(); await flush()
  store.state.user.token = 'new-session'; await flush()
  respond({ updatevirtualmachineresponse: { jobid: 'old-job' } }); await p
  expect(wrapper.emitted('close-action')).toBeTruthy(); expect(success).not.toHaveBeenCalled()
  expect(getAPI.mock.calls.some(([cmd]) => cmd === 'queryAsyncJobResult')).toBe(false)
})

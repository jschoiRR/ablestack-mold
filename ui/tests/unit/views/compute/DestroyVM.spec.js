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

import { shallowMount, flushPromises } from '@vue/test-utils'
import DestroyVM from '@/views/compute/DestroyVM.vue'
import { getAPI, postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))

const selectedItems = [{ id: 'vm-a', name: 'A' }, { id: 'vm-b', name: 'B' }]
let jobs, refresh, notify
function mount () {
  return shallowMount(DestroyVM, {
    props: {
      resource: {},
      selectedRowKeys: ['vm-a', 'vm-b'],
      selectedItems,
      chosenColumns: [{ key: 'name' }],
      action: { currentAction: { label: 'Delete', message: 'Confirm deletion' } }
    },
    global: {
      provide: { parentFetchData: refresh },
      directives: { focus: {}, 'ctrl-enter': {} },
      mocks: {
        $t: key => key,
        $getApiParams: () => ({ expunge: { description: 'Expunge' } }),
        $store: { getters: { apis: { expungeVirtualMachine: {} }, userInfo: { roletype: 'Admin' }, features: {} } },
        $message: { info: jest.fn() },
        $notifyError: notify,
        $pollJob: options => { jobs.push(options) }
      }
    }
  })
}
beforeEach(() => {
  jest.clearAllMocks()
  jobs = []; refresh = jest.fn(); notify = jest.fn()
  getAPI.mockResolvedValue({ listvolumesresponse: { volume: [] } })
  postAPI.mockImplementation((api, params) => Promise.resolve({ destroyvirtualmachineresponse: { jobid: params.id } }))
})
test('list removal cannot turn a submitted bulk expunge into a single delete form', async () => {
  const wrapper = mount()
  await flushPromises()
  wrapper.vm.expunge = true
  wrapper.vm.handleSubmit({ preventDefault: jest.fn() })
  await flushPromises()
  await wrapper.setProps({ selectedRowKeys: [], selectedItems: [] })
  jobs.forEach(job => job.successMethod())
  await flushPromises()
  expect(wrapper.vm.operationKeys).toEqual(['vm-a', 'vm-b'])
  expect(wrapper.vm.showGroupActionModal).toBe(true)
  expect(wrapper.findAll('a-form-stub')).toHaveLength(0)
  expect(wrapper.vm.selectedItemsProgress.map(item => item.status)).toEqual(['success', 'success'])
  expect(refresh).toHaveBeenCalledTimes(1)
  wrapper.vm.handleSubmit({ preventDefault: jest.fn() })
  wrapper.vm.destroyGroupVMs()
  expect(postAPI.mock.calls).toEqual([
    ['destroyVirtualMachine', { id: 'vm-a', expunge: true }],
    ['destroyVirtualMachine', { id: 'vm-b', expunge: true }]
  ])
  wrapper.vm.handleCancel()
  expect(wrapper.emitted('close-action')).toHaveLength(1)
  expect(wrapper.emitted('cancel-bulk-action')).toHaveLength(1)
  expect(wrapper.vm.actionClosed).toBe(true)
  wrapper.unmount()
})
test('partial async failure settles without a form validation exception', async () => {
  const wrapper = mount()
  await flushPromises()
  wrapper.vm.destroyGroupVMs()
  await flushPromises()
  jobs[0].successMethod()
  jobs[1].errorMethod()
  await flushPromises()
  expect(wrapper.vm.loading).toBe(false)
  expect(wrapper.vm.selectedItemsProgress.map(item => item.status)).toEqual(['success', 'failed'])
  expect(refresh).toHaveBeenCalledTimes(1)
  wrapper.unmount()
})
test('API rejection is settled and all other jobs can finish', async () => {
  postAPI.mockRejectedValueOnce(new Error('request rejected'))
  const wrapper = mount()
  await flushPromises()
  wrapper.vm.destroyGroupVMs()
  await flushPromises()
  jobs[0].successMethod()
  await flushPromises()
  expect(wrapper.vm.loading).toBe(false)
  expect(wrapper.vm.selectedItemsProgress.map(item => item.status)).toEqual(['failed', 'success'])
  expect(notify).toHaveBeenCalledTimes(1)
  wrapper.unmount()
})
test('closing pending work never refreshes or resubmits the old operation', async () => {
  const wrapper = mount()
  await flushPromises()
  wrapper.vm.destroyGroupVMs()
  await flushPromises()
  wrapper.vm.handleCancel()
  jobs.forEach(job => job.successMethod())
  await flushPromises()
  wrapper.vm.handleSubmit()
  expect(refresh).not.toHaveBeenCalled()
  expect(postAPI).toHaveBeenCalledTimes(2)
  wrapper.unmount()
})
test('a fresh operation starts with expunge disabled and current targets', async () => {
  const wrapper = mount()
  await flushPromises()
  expect(wrapper.vm.expunge).toBe(false)
  expect(wrapper.vm.operationStarted).toBe(false)
  wrapper.vm.destroyGroupVMs()
  await flushPromises()
  expect(postAPI.mock.calls.every(([, params]) => !('expunge' in params))).toBe(true)
  jobs.forEach(job => job.successMethod())
  await flushPromises()
  wrapper.unmount()
})

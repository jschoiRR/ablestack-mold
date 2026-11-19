// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

import { shallowMount } from '@vue/test-utils'
import CreateVolume from '@/views/storage/CreateVolume.vue'
import { getAPI, postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
jest.mock('@/store', () => ({ getters: { userInfo: { account: 'admin', roletype: 'Admin' }, project: null } }))
jest.mock('@/role', () => ({ isAdmin: () => true }))
const offerings = [{ id: 'a', tags: 'fast,shared' }, { id: 'b', tags: 'missing' }, { id: 'untagged' }]
const pools = [{ id: 'matching', name: 'Primary', state: 'Up', tags: 'shared,fast' }, { id: 'wrong', name: 'Other', state: 'Up', tags: 'faster' }]
const flush = async () => { for (let i = 0; i < 30; i++) await Promise.resolve() }
function mount (path = '/vm/vm') {
  const handler = jest.fn()
  const wrapper = shallowMount({ ...CreateVolume, methods: { ...CreateVolume.methods, fetchData () {} } }, { props: { resource: { id: 'vm', zoneid: 'zone', account: 'admin' }, submitHandler: handler }, global: { mocks: { $route: { path }, $store: { getters: { userInfo: { account: 'admin', roletype: 'Admin' } } }, $t: x => x, $getApiParams: () => new Proxy({}, { get: () => ({ description: '' }) }), $notifyError: jest.fn() } } })
  wrapper.vm.form.zoneid = 'zone'; wrapper.vm.offerings = offerings; wrapper.vm.form.diskofferingid = 'a'
  return { wrapper, handler }
}
beforeEach(() => { jest.clearAllMocks(); getAPI.mockReset(); getAPI.mockResolvedValue({ liststoragepoolsresponse: { storagepool: pools } }) })
test('offering changes reconcile selected UUIDs and distinguish zero matches from an untagged offering', async () => {
  const { wrapper } = mount(); await flush()
  await wrapper.vm.onChangeCreateOnStorage(true); await flush()
  expect(wrapper.vm.storagePoolOptions.map(p => p.value)).toEqual(['matching'])
  wrapper.vm.form.storageid = 'matching'; wrapper.vm.onChangeDiskOffering('b'); await flush()
  expect(wrapper.vm.form.storageid).toBeUndefined(); expect(wrapper.vm.storagePoolOptions).toEqual([])
  wrapper.vm.onChangeDiskOffering('untagged'); await flush()
  expect(wrapper.vm.storagePoolOptions.map(p => p.value)).toEqual(['matching', 'wrong'])
  wrapper.vm.onChangeCreateOnStorage(false); await flush()
  expect(wrapper.vm.storagePools).toEqual([]); expect(wrapper.vm.form.storageid).toBeUndefined()
  wrapper.unmount()
})
test('late zone and toggle responses cannot restore obsolete candidates', async () => {
  const { wrapper } = mount(); await flush()
  let finish
  getAPI.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  wrapper.vm.onChangeCreateOnStorage(true); await flush()
  wrapper.vm.form.zoneid = 'other'; await flush()
  expect(wrapper.vm.createOnStorage).toBe(true)
  expect(getAPI).toHaveBeenCalledWith('listStoragePools', expect.objectContaining({ zoneid: 'other' }), { preserveOnFailure: true })
  finish({ liststoragepoolsresponse: { storagepool: [{ ...pools[0], id: 'old' }] } }); await flush()
  expect(wrapper.vm.storagePools.map(p => p.id)).toEqual(['matching', 'wrong'])
  getAPI.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  wrapper.vm.fetchStoragePools('other'); wrapper.vm.onChangeCreateOnStorage(false)
  finish({ liststoragepoolsresponse: { storagepool: pools } }); await flush()
  expect(wrapper.vm.storagePools).toEqual([])
  wrapper.unmount()
})
test('failed or incompatible storage selection cannot reach the create/attach handler', async () => {
  const { wrapper, handler } = mount(); await flush()
  wrapper.vm.formRef = { value: { validate: () => Promise.resolve(), scrollToField: jest.fn() } }
  wrapper.vm.createOnStorage = true; wrapper.vm.storagePools = pools; wrapper.vm.form.storageid = 'wrong'
  wrapper.vm.handleSubmit(); await flush()
  expect(handler).not.toHaveBeenCalled(); expect(postAPI).not.toHaveBeenCalled()
  wrapper.vm.form.storageid = 'matching'; wrapper.vm.storageFetchError = true
  wrapper.vm.handleSubmit(); await flush(); expect(handler).not.toHaveBeenCalled()
  wrapper.unmount()
})
test('snapshot creation continues to hide explicit storage selection', async () => {
  const { wrapper } = mount('/snapshot/snapshot')
  expect(wrapper.vm.showStoragePoolSelect).toBe(false)
  expect(wrapper.vm.createVolumeFromSnapshot).toBe(true)
  wrapper.unmount()
})

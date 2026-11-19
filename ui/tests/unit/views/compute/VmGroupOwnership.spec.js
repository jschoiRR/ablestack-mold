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

import DeployVM from '@/views/compute/DeployVM.vue'
import { getAPI, postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))

it('queries the final selected creation owner rather than the login owner', () => {
  const owner = { account: 'target', domainid: 'target-domain' }
  const ctx = { owner, groupLookup: { load: jest.fn() }, $store: { getters: { apis: { listInstanceGroups: {} }, userInfo: { account: 'admin' }, project: { id: 'other' } } } }
  DeployVM.methods.fetchInstaceGroups.call(ctx)
  expect(ctx.groupLookup.load).toHaveBeenCalledWith(owner, true)
})
it('clears a typed group and starts the new query on owner changes', () => {
  const ctx = { form: { group: 'previous' }, fetchInstaceGroups: jest.fn() }
  DeployVM.watch.groupOwnerKey.call(ctx)
  expect(ctx.form.group).toBeNull()
  expect(ctx.fetchInstaceGroups).toHaveBeenCalledTimes(1)
})
it.each([
  { selectedAccountType: 'Account', selectedAccount: null },
  { selectedAccountType: 'Project', selectedProject: null }
])('keeps incomplete creation ownership unresolved rather than falling back to admin %p', selection => {
  const ctx = { owner: { account: 'admin', domainid: 'root' }, isCreationSource: false }
  DeployVM.methods.fetchOwnerOptions.call(ctx, selection)
  expect(ctx.owner).toEqual({ projectid: null, domainid: null, account: null })
})
it('uses only the selected project when ownership changes to a project', () => {
  const ctx = { owner: {}, resetData: jest.fn() }
  DeployVM.methods.fetchOwnerOptions.call(ctx, { selectedAccountType: 'Project', selectedProject: 'target', initialized: true })
  expect(ctx.owner).toEqual({ account: null, domainid: null, projectid: 'target' })
  expect(ctx.resetData).toHaveBeenCalled()
})
it('prevents creation from a typed group while the group lookup has failed', async () => {
  const ctx = {
    loading: { deploy: false },
    owner: { account: 'target', domainid: 'domain' },
    form: { group: 'typed-before-failure' },
    groupLookupReady: false,
    formRef: { value: { validate: () => Promise.resolve() } },
    $message: { error: jest.fn() },
    $t: key => key
  }
  await DeployVM.methods.handleSubmit.call(ctx)
  await Promise.resolve()
  expect(postAPI).not.toHaveBeenCalled()
  expect(ctx.$message.error).toHaveBeenCalledWith('message.vm.group.lookup.blocked')
})
it('refuses to create a VM when the final owner is incomplete', async () => {
  const ctx = {
    loading: { deploy: false },
    owner: {},
    form: {},
    formRef: { value: { validate: () => Promise.resolve() } },
    $message: { error: jest.fn() },
    $t: key => key
  }
  postAPI.mockClear()
  await DeployVM.methods.handleSubmit.call(ctx)
  await Promise.resolve()
  expect(postAPI).not.toHaveBeenCalled()
  expect(ctx.$message.error).toHaveBeenCalledWith('message.vm.group.lookup.unavailable')
})
it('rechecks ownership after an asynchronous capability check before creating the VM', async () => {
  let finish
  getAPI.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  const ctx = {
    loading: { deploy: false },
    owner: { account: 'before', domainid: 'domain' },
    form: { group: 'selected', templateid: 'template', computeofferingid: 'offering' },
    groups: { opts: [{ value: 'selected', groupId: 'selected-id' }] },
    groupLookupReady: true,
    formRef: { value: { validate: () => Promise.resolve() } },
    selectedDataDiskOffering: { id: 'disk' },
    selectedDataDiskCount: 1,
    selectedDataDiskSize: 1,
    isoDataDiskSelection: {},
    $store: { getters: { apis: { listHypervisorCapabilities: {} } } },
    $message: { error: jest.fn() },
    $t: key => key
  }
  postAPI.mockClear()
  DeployVM.methods.handleSubmit.call(ctx)
  await Promise.resolve()
  ctx.owner = { projectid: 'after' }
  finish({ listhypervisorcapabilitiesresponse: {} })
  await Promise.resolve()
  await Promise.resolve()
  expect(postAPI).not.toHaveBeenCalled()
  expect(ctx.$message.error).toHaveBeenCalledWith('message.vm.group.lookup.blocked')
})

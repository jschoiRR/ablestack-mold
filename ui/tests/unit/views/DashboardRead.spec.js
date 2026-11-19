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

import mixin from '@/utils/dashboardReadMixin'
import { getAPI } from '@/api'
import { sourceToken } from '@/utils/request'
jest.mock('@/api', () => ({ getAPI: jest.fn() }))
jest.mock('@/utils/request', () => ({ sourceToken: { isCancel: jest.fn(error => error?.code === 'ERR_CANCELED') } }))
let vm
const bind = () => {
  vm = { $route: { name: 'dashboard', fullPath: '/dashboard' }, $store: { state: { user: { token: 'token' } }, getters: { userInfo: { id: 'admin' }, project: {} } }, data: { instances: 7 }, events: [1], account: { id: 'a' }, loading: false }
  Object.assign(vm, mixin.data()); Object.entries(mixin.methods).forEach(([key, fn]) => { vm[key] = fn.bind(vm) })
  Object.defineProperty(vm, 'dashboardScope', { get: () => mixin.computed.dashboardScope.call(vm) })
}
beforeEach(() => { jest.clearAllMocks(); bind() })
const defer = () => { let release, fail; const promise = new Promise((resolve, reject) => { release = resolve; fail = reject }); return { promise, resolve: release, reject: fail } }
test('owns a navigation cancellation without publishing empty success or an error', async () => {
  const apply = jest.fn(); getAPI.mockRejectedValue({ code: 'ERR_CANCELED' })
  await expect(vm.readDashboard('listVirtualMachines', {}, apply)).resolves.toBeUndefined()
  expect(apply).not.toHaveBeenCalled(); expect(vm.data.instances).toBe(7); expect(vm.dashboardReadError).toBe(false); expect(vm.loading).toBe(false)
  expect(sourceToken.isCancel).toHaveBeenCalled(); expect(getAPI).toHaveBeenCalledWith('listVirtualMachines', {}, { preserveOnFailure: true })
})
test('actual failure preserves last values and remains visible until successful retry', async () => {
  getAPI.mockRejectedValue(new Error('503')); await vm.readDashboard('listVirtualMachines', {}, () => {})
  expect(vm.dashboardReadError).toBe(true); expect(vm.data.instances).toBe(7)
  getAPI.mockResolvedValue({ count: 8 }); await vm.readDashboard('listVirtualMachines', {}, json => { vm.data.instances = json.count })
  expect(vm.dashboardReadError).toBe(false); expect(vm.data.instances).toBe(8)
})
test.each(['route', 'project', 'user', 'session', 'unmount'])('late %s results cannot publish', async kind => {
  const wait = defer(); getAPI.mockReturnValue(wait.promise); const apply = jest.fn(); const pending = vm.readDashboard('listVirtualMachines', {}, apply)
  if (kind === 'route') vm.$route.fullPath = '/userdata'
  if (kind === 'project') vm.$store.getters.project.id = 'new-project'
  if (kind === 'user') vm.$store.getters.userInfo.id = 'new-user'
  if (kind === 'session') vm.$store.state.user.token = 'new-session'
  if (kind === 'unmount') mixin.beforeUnmount.call(vm)
  wait.resolve({ count: 99 }); await pending; expect(apply).not.toHaveBeenCalled()
})
test('latest identical request wins while parallel argument sets remain independent', async () => {
  const a = defer(); const b = defer(); const c = defer(); getAPI.mockReturnValueOnce(a.promise).mockReturnValueOnce(b.promise).mockReturnValueOnce(c.promise)
  const first = jest.fn(); const latest = jest.fn(); const other = jest.fn()
  const tasks = [vm.readDashboard('listVirtualMachines', {}, first), vm.readDashboard('listVirtualMachines', {}, latest), vm.readDashboard('listVirtualMachines', { state: 'Running' }, other)]
  b.resolve({ count: 2 }); await tasks[1]; expect(vm.loading).toBe(true)
  c.resolve({ count: 3 }); await tasks[2]; a.resolve({ count: 1 }); await tasks[0]
  expect(first).not.toHaveBeenCalled(); expect(latest).toHaveBeenCalledWith({ count: 2 }); expect(other).toHaveBeenCalledWith({ count: 3 }); expect(vm.loading).toBe(false)
})
test('scope switch clears old owner values and old finalizers cannot stop new loading', async () => {
  const a = defer(); const b = defer(); getAPI.mockReturnValueOnce(a.promise).mockReturnValueOnce(b.promise)
  const previous = vm.readDashboard('listVirtualMachines', {}, () => {})
  vm.$store.getters.project.id = 'other'; mixin.watch.dashboardScope.call(vm)
  expect(vm.data.instances).toBeNull(); expect(vm.events).toEqual([]); expect(vm.account).toEqual({})
  const current = vm.readDashboard('listVirtualMachines', {}, () => {})
  a.reject(new Error('old 503')); await previous; expect(vm.loading).toBe(true); expect(vm.dashboardReadError).toBe(false)
  b.resolve({}); await current; expect(vm.loading).toBe(false)
})
test('disposed and inactive dashboard watchers never issue a new request', async () => {
  vm.$route.name = 'userdata'; await vm.readDashboard('listVirtualMachines', {}, () => {}); expect(getAPI).not.toHaveBeenCalled()
  vm.$route.name = 'dashboard'; mixin.beforeUnmount.call(vm); await vm.readDashboard('listVirtualMachines', {}, () => {}); expect(getAPI).not.toHaveBeenCalled()
})

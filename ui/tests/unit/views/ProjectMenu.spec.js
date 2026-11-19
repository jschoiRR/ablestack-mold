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

import Menu from '@/components/header/ProjectMenu.vue'
jest.mock('@/utils/request', () => ({ sourceToken: { isCancel: error => error?.code === 'ERR_CANCELED' } }))
const deferred = () => { let release; const promise = new Promise(resolve => { release = resolve }); return { promise, resolve: release } }
const create = () => ({ loading: false, defaultOption: { id: 0 }, selectedProjectId: 'next', $route: { name: 'userdata' }, $router: { push: jest.fn(() => Promise.resolve()) }, $store: { getters: { project: { id: 'previous' } }, dispatch: jest.fn(() => Promise.resolve()) }, $message: { success: jest.fn() }, $notifyError: jest.fn(), $t: key => key })
test('awaits capability completion before project commit/navigation, and locks double selection', async () => {
  const vm = create(); const wait = deferred(); vm.$store.dispatch.mockReturnValueOnce(wait.promise)
  const pending = Menu.methods.changeProject.call(vm, { id: 'next', name: 'Next' })
  await Menu.methods.changeProject.call(vm, { id: 'duplicate' })
  expect(vm.loading).toBe(true); expect(vm.$store.dispatch).toHaveBeenCalledTimes(1); expect(vm.$router.push).not.toHaveBeenCalled()
  wait.resolve(); await pending
  expect(vm.$store.dispatch.mock.calls.map(call => call[0])).toEqual(['ProjectView', 'SetProject', 'ToggleTheme'])
  expect(vm.$router.push).toHaveBeenCalledWith({ name: 'dashboard' }); expect(vm.$message.success).toHaveBeenCalledTimes(1); expect(vm.loading).toBe(false)
})
test.each(['ERR_CANCELED', '503'])('handles transition failure %s and restores selection without committing a new owner', async code => {
  const vm = create(); const error = { code }; vm.$store.dispatch.mockRejectedValue(error)
  await expect(Menu.methods.changeProject.call(vm, { id: 'next' })).resolves.toBeUndefined()
  expect(vm.selectedProjectId).toBe('previous'); expect(vm.$store.dispatch).toHaveBeenCalledTimes(1); expect(vm.$router.push).not.toHaveBeenCalled(); expect(vm.$message.success).not.toHaveBeenCalled()
  expect(vm.$notifyError).toHaveBeenCalledTimes(code === '503' ? 1 : 0); expect(vm.loading).toBe(false)
})

test.each([0, '0'])('default view %s is an empty project context, never projectid=0', async id => {
  const vm = create(); await Menu.methods.changeProject.call(vm, { id, name: 'Default view' })
  expect(vm.$store.dispatch).toHaveBeenNthCalledWith(1, 'ProjectView', undefined)
  expect(vm.$store.dispatch).toHaveBeenNthCalledWith(2, 'SetProject', {})
  expect(vm.$store.dispatch).toHaveBeenNthCalledWith(3, 'ToggleTheme', 'light')
})

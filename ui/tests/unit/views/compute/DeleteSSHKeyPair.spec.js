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

import { shallowMount, flushPromises } from '@vue/test-utils'
import Delete from '@/views/compute/DeleteSSHKeyPair'
import Autogen from '@/views/AutogenView'
import compute from '@/config/section/compute'
import { getAPI, postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
const one = Object.freeze({ id: '11111111-1111-4111-8111-111111111111', name: 'Same', fingerprint: 'fp', account: 'A', domainid: '22222222-2222-4222-8222-222222222222' })
const two = Object.freeze({ ...one, id: '33333333-3333-4333-8333-333333333333', account: 'B' })
let context
function model (targets = [one]) {
  const c = { currentAction: { sshKeyPairTargets: targets }, $store: { getters: { apis: { listSSHKeyPairs: {}, deleteSSHKeyPair: {}, listVirtualMachines: {} }, userInfo: { id: 'user' } }, state: { user: { token: 'session' } } }, $t: key => key, $emit: jest.fn() }
  Object.assign(c, Delete.data.call(c)); for (const [key, fn] of Object.entries(Delete.methods)) c[key] = fn.bind(c); for (const [key, fn] of Object.entries(Delete.computed)) Object.defineProperty(c, key, { get: () => fn.call(c) }); c.session = c.sessionKey(); return c
}
beforeEach(() => { jest.clearAllMocks(); context = model(); getAPI.mockImplementation((api, params) => Promise.resolve(api === 'listVirtualMachines' ? { listvirtualmachinesresponse: {} } : { listsshkeypairsresponse: { count: 1, sshkeypair: [params.id === two.id ? two : one] } })); postAPI.mockResolvedValue({ deletesshkeypairresponse: { success: true } }) })
test('partial failure retains rows; retry does not delete an already successful owner', async () => {
  context = model([one, two]); postAPI.mockImplementation((api, params) => params.account === 'B' ? Promise.reject(Object.assign(new Error('Denied B'), { response: { status: 431, data: { errorresponse: { errortext: 'Denied B' } } } })) : Promise.resolve({ deletesshkeypairresponse: { success: true } }))
  await context.execute(); expect(context.rows.map(r => r.status)).toEqual(['success', 'failed']); expect(context.rows[1].reason).toBe('Denied B'); expect(context.$emit).not.toHaveBeenCalled()
  postAPI.mockResolvedValue({ deletesshkeypairresponse: { success: true } }); await context.execute(); expect(postAPI.mock.calls.map(call => call[1].account)).toEqual(['A', 'B', 'B']); expect(context.rows.map(r => r.status)).toEqual(['success', 'success'])
})
test('pending deletion is guarded against double submit and close; results refresh only on close', async () => {
  let release; postAPI.mockImplementation(() => new Promise(resolve => { release = resolve })); const task = context.execute(); await flushPromises(); await context.execute(); context.close(); expect(postAPI).toHaveBeenCalledTimes(1); expect(context.$emit).not.toHaveBeenCalled(); expect(context.deleting).toBe(true)
  release({ deletesshkeypairresponse: { success: true } }); await task; context.close(); expect(context.$emit.mock.calls).toEqual([['close-action', { clearSelection: true, removed: [one.id] }], ['refresh-data']])
})
test('lost success response is reconciled by UUID without sending another deletion', async () => {
  postAPI.mockRejectedValue(new Error('Network Error')); getAPI.mockResolvedValueOnce({ listsshkeypairsresponse: { count: 1, sshkeypair: [one] } }).mockResolvedValueOnce({ listsshkeypairsresponse: {} }); await context.execute(); expect(context.rows[0].status).toBe('success'); expect(postAPI).toHaveBeenCalledTimes(1); await context.execute(); expect(postAPI).toHaveBeenCalledTimes(1)
})
test('unknown result stays uncertain on read error and is checked before retry', async () => {
  postAPI.mockRejectedValue(new Error('Network Error')); getAPI.mockResolvedValueOnce({ listsshkeypairsresponse: { count: 1, sshkeypair: [one] } }).mockRejectedValueOnce({ response: { status: 503 } }); await context.execute(); expect(context.rows[0].status).toBe('unknown'); await context.execute(); expect(postAPI).toHaveBeenCalledTimes(1)
  await context.checkUnknown(); expect(context.rows[0].status).toBe('failed'); expect(postAPI).toHaveBeenCalledTimes(1); postAPI.mockResolvedValue({ deletesshkeypairresponse: { success: true } }); await context.execute(); expect(postAPI).toHaveBeenCalledTimes(2)
})
test.each([{}, { count: 1, sshkeypair: [{ ...one, fingerprint: 'changed' }] }])('missing or changed original does not delete a new same-name registration', async body => { getAPI.mockResolvedValue({ listsshkeypairsresponse: body }); await context.execute(); expect(postAPI).not.toHaveBeenCalled(); expect(['absent', 'blocked']).toContain(context.rows[0].status) })
test.each([403, 503])('preflight %s failure blocks deletion', async status => { getAPI.mockRejectedValue({ response: { status } }); await context.execute(); expect(context.rows[0].status).toBe('blocked'); expect(postAPI).not.toHaveBeenCalled() })
test('session change during preflight blocks POST', async () => { getAPI.mockImplementation(() => { context.$store.state.user.token = 'new'; return Promise.resolve({ listsshkeypairsresponse: { count: 1, sshkeypair: [one] } }) }); await context.execute(); expect(postAPI).not.toHaveBeenCalled() })
test('VM lookup permission and errors remain distinct from unused', async () => {
  delete context.$store.getters.apis.listVirtualMachines; await context.loadUsage(); expect(context.rows[0].usage.state).toBe('forbidden'); expect(getAPI).not.toHaveBeenCalled()
  context = model(); getAPI.mockImplementation((api) => api === 'listVirtualMachines' ? Promise.reject(Object.assign(new Error('Unavailable'), { response: { status: 503 } })) : Promise.resolve({ listsshkeypairsresponse: { count: 1, sshkeypair: [one] } })); await context.loadUsage(); expect(context.rows[0].usage.state).toBe('error')
})
test('usage rejects a replacement registered while VM count is loading', async () => {
  let reads = 0; getAPI.mockImplementation((api) => Promise.resolve(api === 'listVirtualMachines' ? { listvirtualmachinesresponse: { count: 0 } } : { listsshkeypairsresponse: { count: 1, sshkeypair: [{ ...one, fingerprint: ++reads === 1 ? one.fingerprint : 'replaced' }] } })); await context.loadUsage(); expect(context.rows[0].usage.state).toBe('error')
})
test('SSH metadata reuses self-managed shell for single and bulk actions and captures selection before changes', () => {
  const action = compute.children.find(s => s.name === 'ssh').actions.find(a => a.api === 'deleteSSHKeyPair'); expect(action.selfManagedDialog).toBe(true); expect(action.groupAction).toBe(true)
  const row = { ...one }; const c = { $route: { path: '/ssh', params: {}, query: {} }, selectedItems: [row] }; Autogen.methods.execAction.call(c, action, true); row.name = 'Other'; c.selectedItems = [two]; expect(c.currentAction.sshKeyPairTargets[0].name).toBe(one.name)
})
test('shell preserves cancel then danger delete and disables Escape while busy', async () => {
  const wrapper = shallowMount(Delete, { props: { currentAction: { sshKeyPairTargets: [one] } }, global: { mocks: { $store: context.$store, $t: key => key }, stubs: { MoldDialog: { name: 'MoldDialog', props: ['closable', 'keyboard'], template: '<section><main><slot /></main><footer><slot name="footer" /></footer></section>' }, 'a-button': { props: { type: String, danger: Boolean, disabled: Boolean }, template: '<button :data-danger="danger" :disabled="disabled"><slot /></button>' } } } }); await flushPromises(); expect(wrapper.find('footer').text()).toBe('label.cancellabel.delete'); expect(wrapper.find('footer button[data-danger="true"]').exists()).toBe(true); await wrapper.setData({ busy: true, deleting: true }); expect(wrapper.findComponent({ name: 'MoldDialog' }).props('keyboard')).toBe(false); wrapper.unmount()
})

test('a removed UUID returned as API 431 is absent and never submitted for deletion', async () => { getAPI.mockRejectedValue({ response: { status: 431 } }); await context.execute(); expect(context.rows[0].status).toBe('absent'); expect(postAPI).not.toHaveBeenCalled() })

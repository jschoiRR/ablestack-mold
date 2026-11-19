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
import Delete from '@/views/compute/DeleteUserData'
import Autogen from '@/views/AutogenView'
import compute from '@/config/section/compute'
import { userDataDeleteTargets, userDataDeleteParams, userDataDeleteLookup, userDataDeleteIdentity } from '@/utils/userDataDeletion'
import { getAPI, postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
const one = { id: '11111111-1111-4111-8111-111111111111', name: '같은 이름', account: 'A', domainid: '22222222-2222-4222-8222-222222222222' }
const two = { ...one, id: '33333333-3333-4333-8333-333333333333', account: 'B' }
const response = rows => ({ listuserdataresponse: { count: rows.length, userdata: rows } })
const error = (code, reason) => ({ response: { status: code, data: { deleteuserdataresponse: { errorcode: code, errortext: reason } } } })
function model (targets = [one]) {
  const c = { currentAction: { userDataTargets: targets }, $route: { fullPath: '/userdata', params: {} }, $store: { getters: { apis: { listUserData: {}, deleteUserData: {} }, userInfo: { id: 'user' }, project: {} }, state: { user: { token: 'session' } } }, $t: key => key, $emit: jest.fn() }
  Object.assign(c, Delete.data.call(c)); for (const [key, fn] of Object.entries(Delete.methods)) c[key] = fn.bind(c); for (const [key, fn] of Object.entries(Delete.computed)) Object.defineProperty(c, key, { get: () => fn.call(c) }); c.session = c.sessionKey(); return c
}
let c
beforeEach(() => { jest.clearAllMocks(); c = model(); getAPI.mockImplementation((api, params) => Promise.resolve(response([params.id === two.id ? two : one]))); postAPI.mockResolvedValue({ deleteuserdataresponse: { success: true } }) })
test('immutable UUID/owner snapshot does not follow list refresh or same-name rows', () => {
  const source = { ...one }; const targets = userDataDeleteTargets({ invokedAsGroupAction: true }, [source]); source.id = two.id; source.account = 'Changed'
  expect(targets[0]).toEqual(one); expect(Object.isFrozen(targets)).toBe(true); expect(Object.isFrozen(targets[0])).toBe(true)
  expect(userDataDeleteTargets({ resource: one }, [two])).toEqual([one])
})
test('owner and project API scopes are exclusive; invalid targets fail closed', () => {
  expect(userDataDeleteParams(one)).toEqual({ id: one.id, account: 'A', domainid: one.domainid, ignoreproject: true })
  const project = { ...one, projectid: two.id }; expect(userDataDeleteParams(project)).toEqual({ id: one.id, projectid: two.id })
  expect(userDataDeleteLookup(project).account).toBeUndefined()
  for (const invalid of [{}, { ...one, id: 'bad' }, { ...one, domainid: '' }, { ...one, account: '' }, { ...one, projectid: 'bad' }]) expect(userDataDeleteParams(invalid)).toBeNull()
})
test.each([{}, { listuserdataresponse: { count: 2, userdata: [one] } }, { listuserdataresponse: { count: 1 } }, { listuserdataresponse: { userdata: {} } }])('malformed or incomplete lookup blocks deletion', async json => { getAPI.mockResolvedValue(json); await c.execute(); expect(c.rows[0].status).toBe('blocked'); expect(postAPI).not.toHaveBeenCalled() })
test.each([{ ...one, account: 'Changed' }, { ...one, projectid: two.id }, { ...one, domainid: two.id }, two])('changed identity blocks delete', async row => { getAPI.mockResolvedValue(response([row])); await c.execute(); expect(c.rows[0].status).toBe('blocked'); expect(postAPI).not.toHaveBeenCalled() })
test('same project identity remains valid independently of display account', () => { const p = { ...one, projectid: two.id }; expect(userDataDeleteIdentity(response([{ ...p, account: 'internal-project' }]), p)).toBe('ready') })
test('partial failure retains reason and retries only the failed original UUID', async () => {
  c = model([one, two]); postAPI.mockImplementation((api, params) => params.id === two.id ? Promise.reject(error(530, 'in use 한글')) : Promise.resolve({ deleteuserdataresponse: { success: true } }))
  await c.execute(); expect(c.rows.map(r => r.status)).toEqual(['success', 'failed']); expect(c.rows[1].reason).toContain('in use 한글'); expect(c.$emit).not.toHaveBeenCalled()
  postAPI.mockResolvedValue({ deleteuserdataresponse: { success: true } }); await c.execute(); expect(postAPI.mock.calls.map(x => x[1].id)).toEqual([one.id, two.id, two.id]); expect(c.totals.success).toBe(2)
})
test('pending request blocks duplicate submit, Escape/close and beforeunload', async () => {
  let release; postAPI.mockImplementation(() => new Promise(resolve => { release = resolve })); const task = c.execute(); await flushPromises(); await c.execute(); c.close(); expect(postAPI).toHaveBeenCalledTimes(1); expect(c.$emit).not.toHaveBeenCalled()
  const event = { preventDefault: jest.fn() }; c.beforeUnload(event); expect(event.preventDefault).toHaveBeenCalled(); expect(event.returnValue).toBe('')
  release({ deleteuserdataresponse: { success: true } }); await task; c.close(); expect(c.$emit.mock.calls).toEqual([['close-action', { started: true, removed: [one.id], retained: [] }], ['refresh-data']])
})
test('lost success response confirms UUID absence and never resends deletion', async () => {
  postAPI.mockRejectedValue(new Error('Network Error')); getAPI.mockResolvedValueOnce(response([one])).mockRejectedValueOnce(error(431, 'UUID missing')).mockResolvedValueOnce(response([two])); await c.execute(); expect(c.rows[0].status).toBe('success'); await c.execute(); expect(postAPI).toHaveBeenCalledTimes(1); expect(getAPI.mock.calls[2][1].name).toBe(one.name)
})
test('unknown response and read error remain unconfirmed until explicit read; then retry only present UUID', async () => {
  postAPI.mockRejectedValue(new Error('timeout')); getAPI.mockResolvedValueOnce(response([one])).mockRejectedValueOnce(error(503, 'read unavailable')); await c.execute(); expect(c.rows[0].status).toBe('unknown'); await c.execute(); expect(postAPI).toHaveBeenCalledTimes(1)
  await c.checkUnknown(); expect(c.rows[0].status).toBe('failed'); postAPI.mockResolvedValue({ deleteuserdataresponse: { success: true } }); await c.execute(); expect(postAPI).toHaveBeenCalledTimes(2)
})
test('already removed original does not delete a replacement with the same name', async () => {
  getAPI.mockRejectedValueOnce(error(431, 'missing UUID')).mockResolvedValueOnce(response([two])); await c.execute(); expect(c.rows[0].status).toBe('absent'); expect(postAPI).not.toHaveBeenCalled()
})
test.each([403, 432, 531, 503])('preflight %s failure sends no delete', async code => { getAPI.mockRejectedValue(error(code, 'denied/unavailable')); await c.execute(); expect(c.rows[0].status).toBe('blocked'); expect(postAPI).not.toHaveBeenCalled() })
test('UUID conversion 431 with failed fallback is never treated as absence', async () => { getAPI.mockRejectedValue(error(431, 'invalid')); await c.execute(); expect(c.rows[0].status).toBe('blocked'); expect(postAPI).not.toHaveBeenCalled() })
test.each(['session', 'project', 'route', 'permission'])('changing %s during preflight stops requests', async kind => {
  getAPI.mockImplementation(() => { if (kind === 'session') c.$store.state.user.token = 'new'; if (kind === 'project') c.$store.getters.project = { id: two.id }; if (kind === 'route') c.$route.fullPath = '/userdata?page=2'; if (kind === 'permission') delete c.$store.getters.apis.deleteUserData; return Promise.resolve(response([one])) }); await c.execute(); expect(postAPI).not.toHaveBeenCalled(); expect(c.rows[0].status).toBe('blocked')
})
test('loss of scope during POST marks outcome unknown and does not continue the batch', async () => {
  c = model([one, two]); postAPI.mockImplementation(() => { c.$store.getters.project = { id: two.id }; return Promise.resolve({ deleteuserdataresponse: { success: true } }) }); await c.execute(); expect(c.rows.map(r => r.status)).toEqual(['unknown', 'blocked']); expect(postAPI).toHaveBeenCalledTimes(1)
})
test('metadata exposes a named group toolbar; Autogen freezes selection before refresh', () => {
  const action = compute.children.find(s => s.name === 'userdata').actions.find(a => a.api === 'deleteUserData'); expect(action.toolbarLabel).toBe('label.remove.user.data'); expect(action.selfManagedDialog).toBe(true)
  const row = { ...one }; const ctx = { $route: { path: '/userdata' }, $store: c.$store, selectedItems: [row], listScope: () => 'scope' }; Autogen.methods.execAction.call(ctx, action, true); row.name = 'Other'; ctx.selectedItems = [two]; expect(ctx.currentAction.userDataTargets).toEqual([one])
  expect(Autogen.computed.visibleListActions.call({ actions: [action], dataView: false, selectedRowKeys: [one.id], selectedItems: [one], $store: c.$store })).toEqual([action])
  delete c.$store.getters.apis.deleteUserData; expect(Autogen.computed.visibleListActions.call({ actions: [action], dataView: false, selectedRowKeys: [one.id], $store: c.$store })).toEqual([])
})
test('single detail deletion returns to library; list keeps only failed selected rows', () => {
  const ctx = { currentAction: { api: 'deleteUserData', userDataListScope: 'scope' }, dataView: false, resource: {}, items: [one, two], listScope: () => 'scope', $nextTick: () => {}, $route: { path: '/userdata' }, $router: { push: jest.fn() } }
  Autogen.methods.closeAction.call(ctx, { started: true, removed: [one.id], retained: [two.id] }); expect(ctx.items).toEqual([two]); expect(ctx.selectedRowKeys).toEqual([two.id])
  Object.assign(ctx, { currentAction: { api: 'deleteUserData' }, dataView: true, resource: one }); Autogen.methods.closeAction.call(ctx, { started: true, removed: [one.id] }); expect(ctx.$router.push).toHaveBeenCalledWith('/userdata')
})
test('shared dialog keeps cancel before danger delete, no auto Ctrl+Enter, busy shell disabled', async () => {
  const wrapper = shallowMount(Delete, { props: { currentAction: { userDataTargets: [one] } }, global: { mocks: { $store: c.$store, $route: c.$route, $t: key => key }, stubs: { MoldDialog: { name: 'MoldDialog', props: ['closable', 'keyboard'], template: '<section><main><slot /></main><footer><slot name="footer" /></footer></section>' }, 'a-button': { props: { danger: Boolean, disabled: Boolean }, template: '<button :data-danger="danger" :disabled="disabled"><slot /></button>' } } } }); await flushPromises(); expect(wrapper.find('footer').text()).toBe('label.cancellabel.delete'); expect(wrapper.find('footer button[data-danger="true"]').exists()).toBe(true); await wrapper.setData({ busy: true }); expect(wrapper.findComponent({ name: 'MoldDialog' }).props('keyboard')).toBe(false); wrapper.unmount()
})

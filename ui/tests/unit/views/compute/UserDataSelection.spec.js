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
import { mount as mountComponent } from '@vue/test-utils'
import { nextTick } from 'vue'
import { Alert, Button } from 'ant-design-vue'
import Picker from '@/views/compute/wizard/UserDataSelection.vue'
import Reset from '@/views/compute/ResetUserData.vue'
import Deploy from '@/views/compute/DeployVM.vue'
import AutoScale from '@/views/compute/CreateAutoScaleVmGroup.vue'
import Vnf from '@/views/compute/DeployVnfAppliance.vue'
import { getAPI, postAPI } from '@/api'
import { applyUserDataSelection, userDataSelectionConsumer, userDataOwnerParams } from '@/utils/userDataSelection'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
jest.mock('@/store', () => ({ getters: { apis: {}, userInfo: {}, project: {} } }))

const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const table = { name: 'ATable', props: ['pagination', 'dataSource', 'rowSelection'], template: '<div />' }
const search = { name: 'AInputSearch', props: ['value', 'disabled'], template: '<input />' }
const reply = (rows = [], count = rows.length) => ({ listuserdataresponse: { count, userdata: rows } })
const row = i => ({ id: 'uuid-' + i, name: 'data-' + i, account: 'owner', domainid: 'domain', params: 'A,B' })
function mount (props = {}, apis = { listUserData: {} }) {
  return mountComponent(Picker, {
    props: { scope: { account: 'owner', domainid: 'domain' }, ...props },
    global: { mocks: { $t: key => key, $store: { getters: { apis } } }, stubs: { 'a-table': table, 'a-input-search': search, 'a-radio': { template: '<label><slot /></label>' }, 'a-alert': Alert, 'a-button': Button } }
  })
}
beforeEach(() => { jest.clearAllMocks(); getAPI.mockResolvedValue(reply()) })

test.each([0, 10, 11, 30, 100])('every server page is reachable with %i records and None never inflates total', async count => {
  const rows = Array.from({ length: count }, (_, i) => row(i))
  getAPI.mockImplementation((command, args) => Promise.resolve(reply(rows.slice((args.page - 1) * args.pagesize, args.page * args.pagesize), count)))
  const w = mount(); await flush()
  for (let page = 1; page <= Math.max(1, Math.ceil(count / 10)); page++) {
    if (page > 1) { w.findComponent(table).vm.$emit('change', { current: page, pageSize: 10 }); await flush() }
    expect(w.findComponent(table).props('pagination')).toMatchObject({ current: page, pageSize: 10, total: count })
    expect(w.vm.items).toEqual(rows.slice((page - 1) * 10, page * 10))
  }
  expect(w.vm.selectedId).toBe('0'); expect(w.vm.items.some(item => item.id === '0')).toBe(false)
  expect(getAPI.mock.calls.every(([, p]) => p.pagesize === 10 && p.account === 'owner' && p.domainid === 'domain' && p.ignoreproject === true)).toBe(true)
  w.unmount()
})

test('search submits server keyword, resets page and preserves selected item outside search results', async () => {
  getAPI.mockResolvedValue(reply([row(1)], 30)); const w = mount(); await flush(); w.vm.selectItem(row(1))
  getAPI.mockImplementation((command, args) => Promise.resolve(args.id ? reply([row(1)]) : reply([row(29)], 1)))
  w.findComponent(search).vm.$emit('search', ' data-29 '); await flush()
  expect(getAPI.mock.calls.some(([, p]) => p.keyword === 'data-29' && p.page === 1)).toBe(true)
  expect(w.vm.total).toBe(1); expect(w.vm.selectedId).toBe('uuid-1'); expect(w.vm.selectedItem.name).toBe('data-1')
  expect(w.emitted('select-user-data-item').filter(([id]) => id === 'uuid-1')).toHaveLength(1)
  w.unmount()
})

test('page-size change resets to page one and sends lowercase pagesize', async () => {
  getAPI.mockResolvedValue(reply([row(1)], 100)); const w = mount(); await flush()
  w.findComponent(table).vm.$emit('change', { current: 4, pageSize: 100 }); await flush()
  expect(getAPI).toHaveBeenLastCalledWith('listUserData', expect.objectContaining({ page: 1, pagesize: 100 }), expect.any(Object))
  expect(w.vm.pagination).toMatchObject({ current: 1, pageSize: 100, total: 100 }); w.unmount()
})

test('a deleted last page recovers to the remaining valid server page', async () => {
  getAPI.mockResolvedValueOnce(reply([row(1)], 11)).mockResolvedValueOnce(reply([], 10)).mockResolvedValueOnce(reply([row(1)], 10))
  const w = mount(); await flush(); w.findComponent(table).vm.$emit('change', { current: 2, pageSize: 10 }); await flush()
  expect(w.vm.query.page).toBe(1); expect(w.vm.total).toBe(10); expect(w.vm.ready).toBe(true); w.unmount()
})

test('inconsistent empty pages have a bounded fallback to page one', async () => {
  getAPI.mockResolvedValueOnce(reply([row(1)], 100)).mockResolvedValue(reply([], 100))
  const w = mount(); await flush(); w.findComponent(table).vm.$emit('change', { current: 10, pageSize: 10 }); await flush()
  expect(getAPI).toHaveBeenCalledTimes(4); expect(w.vm.query.page).toBe(1); w.unmount()
})

test('a late old search cannot overwrite the newer result or loading state', async () => {
  const w = mount(); await flush(); let resolveOld
  getAPI.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve })).mockResolvedValueOnce(reply([row(2)], 1))
  w.findComponent(search).vm.$emit('search', 'old'); w.findComponent(search).vm.$emit('search', 'new'); await flush()
  resolveOld(reply([row(1)], 1)); await flush()
  expect(w.vm.items).toEqual([row(2)]); expect(w.vm.query.keyword).toBe('new'); expect(w.vm.loading).toBe(false); w.unmount()
})

test('failure preserves successful data, pagination and selection, blocks row selection, and retry recovers', async () => {
  getAPI.mockResolvedValue(reply([row(1)], 30)); const w = mount(); await flush(); w.vm.selectItem(row(1))
  getAPI.mockRejectedValueOnce(new Error('network'))
  w.findComponent(search).vm.$emit('search', 'retry'); await flush()
  expect(w.vm.items).toEqual([row(1)]); expect(w.vm.total).toBe(30); expect(w.vm.query.keyword).toBe('')
  expect(w.vm.selectedId).toBe('uuid-1'); expect(w.vm.ready).toBe(false); expect(w.vm.selectionDisabled).toBe(true)
  getAPI.mockResolvedValue(reply([row(1)], 1)); await w.vm.load(w.vm.requestedQuery); await flush()
  expect(w.vm.ready).toBe(true); expect(w.vm.query.keyword).toBe('retry'); w.unmount()
})

test.each([401, 403])('HTTP %i is distinguished from empty success', async status => {
  getAPI.mockRejectedValue({ response: { status } }); const w = mount(); await flush()
  expect(w.vm.listError).toBe('message.userdata.selection.forbidden'); expect(w.vm.ready).toBe(false); w.unmount()
})

test('missing list permission never issues an API request', async () => {
  const w = mount({}, {}); await flush()
  expect(getAPI).not.toHaveBeenCalled(); expect(w.vm.listError).toBe('message.userdata.selection.forbidden'); w.unmount()
})

test('the installed Alert renders a retry button which actually reloads a failed query', async () => {
  getAPI.mockRejectedValueOnce(new Error('offline')); const w = mount(); await flush()
  const retry = w.findComponent(Alert).findComponent(Button)
  expect(retry.exists()).toBe(true); expect(retry.text()).toBe('label.refresh')
  getAPI.mockResolvedValue(reply([row(1)], 1)); await retry.trigger('click'); await flush()
  expect(w.vm.items).toEqual([row(1)]); expect(w.vm.ready).toBe(true); w.unmount()
})

test('missing owner never issues an unscoped request', async () => {
  const w = mount({ scope: {} }); await flush()
  expect(getAPI).not.toHaveBeenCalled(); expect(w.vm.listError).toBe('message.userdata.selection.scope'); w.unmount()
})

test('owner change clears selection immediately and ignores old owner replies', async () => {
  getAPI.mockResolvedValueOnce(reply([row(1)], 30)); const w = mount(); await flush(); w.vm.selectItem(row(1))
  let resolveOld; getAPI.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve })).mockResolvedValueOnce(reply([row(2)], 1))
  w.vm.handleSearch('old'); await w.setProps({ scope: { projectid: 'project' } }); await flush()
  resolveOld(reply([row(1)], 30)); await flush()
  expect(w.vm.selectedId).toBe('0'); expect(w.vm.items).toEqual([row(2)])
  expect(getAPI).toHaveBeenLastCalledWith('listUserData', expect.objectContaining({ projectid: 'project', ignoreproject: true }), expect.any(Object))
  expect(getAPI.mock.calls[getAPI.mock.calls.length - 1][1].account).toBeUndefined(); w.unmount()
})

test('prefill outside page one is resolved by owner-scoped UUID once and never reselected on paging', async () => {
  getAPI.mockImplementation((command, args) => Promise.resolve(args.id ? reply([row(99)]) : reply([row(1)], 100)))
  const w = mount({ preFillContent: { userdataid: 'uuid-99' } }); await flush()
  expect(w.emitted('select-user-data-item')).toEqual([['uuid-99', row(99)]])
  w.findComponent(table).vm.$emit('change', { current: 2, pageSize: 10 }); await flush()
  expect(w.emitted('select-user-data-item')).toHaveLength(1); expect(w.vm.selectedId).toBe('uuid-99'); w.unmount()
})

test('deleted selected UUID blocks submission until explicit None or another row is chosen', async () => {
  getAPI.mockImplementation((command, args) => Promise.resolve(args.id ? reply() : reply([row(1)], 1)))
  const w = mount({ value: 'deleted-id' }); await flush()
  expect(w.vm.selectionError).toBe('message.userdata.selection.missing'); expect(w.vm.ready).toBe(false)
  w.vm.selectItem(); await flush(); expect(w.vm.ready).toBe(true)
  expect(w.emitted('select-user-data-item').slice(-1)[0][0]).toBe('0'); w.unmount()
})

test('disabled selector rejects row clicks and None', async () => {
  getAPI.mockResolvedValue(reply([row(1)], 1)); const w = mount({ disabled: true }); await flush()
  w.vm.onClickRow(row(1)).onClick(); expect(w.vm.selectedId).toBe('0'); w.unmount()
})

test('radio change and its bubbling row click emit only one selection', async () => {
  getAPI.mockResolvedValue(reply([row(1)], 1)); const w = mount(); await flush()
  w.vm.rowSelection.onChange(['uuid-1'], [row(1)]); w.vm.onClickRow(row(1)).onClick()
  expect(w.emitted('select-user-data-item').filter(([id]) => id === 'uuid-1')).toHaveLength(1); w.unmount()
})

test('search Enter is stopped and cannot submit the enclosing form', async () => {
  const w = mount(); await flush()
  const e = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
  w.findComponent(search).element.dispatchEvent(e)
  expect(e.defaultPrevented).toBe(true); expect(postAPI).not.toHaveBeenCalled(); w.unmount()
})

test('unmount discards an outstanding response', async () => {
  let finish; getAPI.mockImplementation(() => new Promise(resolve => { finish = resolve }))
  const w = mount(); const vm = w.vm; w.unmount(); finish(reply([row(1)])); await flush(); expect(vm.items).toEqual([])
})

test.each([Reset, Deploy, Vnf])('consumer $name receives a verified row and clears stale variables on None', component => {
  const vm = { form: {}, userDataValues: { OLD: 'stale' }, userDataParams: [] }
  component.methods.updateUserData.call(vm, 'uuid-1', row(1))
  expect(vm.form.userdataid).toBe('uuid-1'); expect(vm.userDataParams.map(p => p.key)).toEqual(['A', 'B']); expect(vm.userDataValues).toEqual({})
  component.methods.updateUserData.call(vm, '0'); expect(vm.form.userdataid).toBeUndefined(); expect(vm.userDataParams).toEqual([])
})

test('AutoScale row selection supersedes an old parameter lookup', () => {
  const vm = { form: {}, userDataValues: {}, userDataParams: [], userDataLookupVersions: { userDataParams: 3 }, userDataLookupErrors: { userDataParams: true } }
  AutoScale.methods.updateUserData.call(vm, 'uuid-1', row(1))
  expect(vm.userDataLookupVersions.userDataParams).toBe(4); expect(vm.userDataLookupErrors.userDataParams).toBe(false)
  expect(vm.userDataParams.map(p => p.key)).toEqual(['A', 'B'])
})

test('stable None and mismatched row IDs cannot leak as a real userdataid', () => {
  const vm = { form: {}, userDataValues: {}, userDataParams: [] }; applyUserDataSelection(vm, 'uuid-2', row(1))
  expect(vm.form.userdataid).toBeUndefined(); expect(postAPI).not.toHaveBeenCalled()
})

test('shared submit guard applies to active library lookup, while manual/template modes remain available', () => {
  const blocked = userDataSelectionConsumer.computed.userDataSelectionBlocked
  const vm = { userdataTabKey: 'userdataregistered', userdataDefaultOverridePolicy: undefined, userDataSelectionReady: false }
  expect(blocked.call(vm)).toBe(true); vm.userDataSelectionReady = true; expect(blocked.call(vm)).toBe(false)
  vm.userDataSelectionReady = false; vm.userdataTabKey = 'userdatatext'; expect(blocked.call(vm)).toBe(false)
  vm.userdataTabKey = 'userdataregistered'; vm.userdataDefaultOverridePolicy = 'DENYOVERRIDE'; expect(blocked.call(vm)).toBe(false)
  expect(userDataOwnerParams({ account: 'a', domainid: 'd', projectid: 'p' })).toEqual({ projectid: 'p' })
})

test('CNI project picker shows the project owner while generic UserData keeps its existing columns', async () => {
  getAPI.mockResolvedValue({ listcniconfigurationresponse: { count: 0, cniconfig: [] } })
  const w = mount({ resourceKind: 'cni', scope: { projectid: 'project' } }, { listCniConfiguration: {} }); await flush()
  expect(w.vm.columns[1]).toMatchObject({ key: 'project', dataIndex: 'project' })
  await w.setProps({ scope: { account: 'owner', domainid: 'domain' } }); await flush()
  expect(w.vm.columns[1].key).toBe('account')
  await w.setProps({ resourceKind: 'userdata', scope: { projectid: 'project' } }); await flush()
  expect(w.vm.columns[1].key).toBe('account'); w.unmount()
})

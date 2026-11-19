// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
import { shallowMount } from '@vue/test-utils'
import { reactive, nextTick } from 'vue'
import SearchFilter from '@/components/view/SearchFilter'
import { getAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn() }))
const flush = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }
let wrappers; let store; let route
function render (value = 'group-id') {
  const w = shallowMount(SearchFilter, { props: { apiName: 'listVirtualMachines', filters: [{ key: 'affinitygroupid', value }] }, global: { renderStubDefaultSlot: true, mocks: { $store: store, $route: route, $isValidUuid: v => v !== 'invalid', $t: key => key === 'label.affinitygroup' ? 'Affinity 그룹' : key } } })
  wrappers.push(w); return w
}
beforeEach(() => {
  wrappers = []; jest.clearAllMocks()
  store = reactive({ getters: { userInfo: { id: 'user' }, project: {}, apis: { listAffinityGroups: {} } }, state: { user: { token: 'session' } } })
  route = reactive({ path: '/vm', query: { projectid: 'project' } })
  getAPI.mockResolvedValue({ listaffinitygroupsresponse: { affinitygroup: [{ id: 'group-id', name: '검증 그룹' }] } })
})
afterEach(() => wrappers.forEach(w => w.unmount()))
test('Affinity chip resolves the owner-scoped name, keeps the UUID and uses the translated label', async () => {
  const w = render(); await flush()
  expect(w.vm.searchFilters[0]).toMatchObject({ key: 'affinitygroupid', value: '검증 그룹', rawValue: 'group-id' })
  expect(w.vm.retrieveFieldLabel('affinitygroupid')).toBe('Affinity 그룹')
  expect(getAPI).toHaveBeenCalledWith('listAffinityGroups', { id: 'group-id', listall: true, projectid: 'project' }, { preserveOnFailure: true })
})
test('late responses cannot replace a newer filter', async () => {
  let old; getAPI.mockImplementationOnce(() => new Promise(resolve => { old = resolve }))
  const w = render(); await flush()
  getAPI.mockResolvedValue({ listaffinitygroupsresponse: { affinitygroup: [{ id: 'new-id', name: '새 그룹' }] } })
  await w.setProps({ filters: [{ key: 'affinitygroupid', value: 'new-id' }] }); await flush()
  old({ listaffinitygroupsresponse: { affinitygroup: [{ id: 'group-id', name: '이전 그룹' }] } }); await flush()
  expect(w.vm.searchFilters[0].value).toBe('새 그룹')
})
test('project changes immediately discard old names and ignore the old-scope response', async () => {
  const pending = []; getAPI.mockImplementation(() => new Promise(resolve => pending.push(resolve)))
  const w = render(); await flush(); route.query.projectid = 'other-project'; await flush()
  expect(w.vm.searchFilters[0].value).toBe('group-id')
  pending[0]({ listaffinitygroupsresponse: { affinitygroup: [{ id: 'group-id', name: '이전 범위' }] } }); await flush()
  expect(w.vm.searchFilters[0].value).toBe('group-id')
  pending[1]({ listaffinitygroupsresponse: { affinitygroup: [{ id: 'group-id', name: '현재 범위' }] } }); await flush()
  expect(w.vm.searchFilters[0].value).toBe('현재 범위')
})
test('lookup failure, inaccessible and invalid resources retain the UUID without a wrong name', async () => {
  getAPI.mockRejectedValueOnce(new Error('denied')); const w = render(); await flush()
  expect(w.vm.searchFilters[0].value).toBe('group-id')
  const count = getAPI.mock.calls.length; await w.setProps({ filters: [{ key: 'affinitygroupid', value: 'invalid' }] }); await flush()
  expect(getAPI).toHaveBeenCalledTimes(count); expect(w.vm.searchFilters[0].value).toBe('invalid')
})
test('a role without group lookup permission keeps the identifier and never broadens lookup', async () => {
  delete store.getters.apis.listAffinityGroups; const w = render(); await flush()
  expect(getAPI).not.toHaveBeenCalled(); expect(w.vm.searchFilters[0].value).toBe('group-id')
})

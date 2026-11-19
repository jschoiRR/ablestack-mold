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
import { mount } from '@vue/test-utils'
import { Button } from 'ant-design-vue'
import compute from '@/config/section/compute'
import Status from '@/components/view/InstanceGroupQueryStatus'
import { vmGroupQuery, vmGroupQueryParams, vmGroupQueryScope, vmGroupQueryRows, vmGroupQueryMessage } from '@/utils/vmGroupQuery'

const id = '12345678-1234-1234-1234-123456789abc'
test('group filter fields match the server contract; AutoScale keeps Zone', () => {
  expect(compute.children.find(section => section.name === 'vmgroup').searchFilters).toEqual(['name', 'domainid', 'account'])
  expect(compute.children.find(section => section.name === 'autoscalevmgroup').searchFilters).toContain('zoneid')
})
test('canonical URL removes unsupported fields and maps q/keyword on URL re-entry', () => {
  expect(vmGroupQuery({ name: ' 한글 ', zoneid: id, unknown: 'x', q: '찾기', page: 2, pagesize: 20 })).toEqual({ name: ' 한글 ', page: '2', pagesize: '20', q: '찾기', keyword: '찾기' })
  expect(vmGroupQuery({ keyword: 'current', q: 'old', projectid: id })).toEqual({ keyword: 'current', q: 'current', projectid: id })
})
test.each(['0', '-1', '1.5', 'NaN', '9007199254740992', ['2']])('invalid paging is removed: %j', page => {
  expect(vmGroupQuery({ page, pagesize: page })).toEqual({})
})
test('invalid owner/target values and arrays cannot masquerade as filters', () => {
  expect(vmGroupQuery({ id: 'bad', domainid: 'bad', projectid: 'bad', account: ['a', 'b'], listall: 'yes' })).toEqual({})
  expect(vmGroupQuery({ projectid: '-1', isrecursive: true, dataView: false })).toEqual({ projectid: '-1', isrecursive: 'true', dataView: 'false' })
})
test('transport contains only supported fields; sorting/tab/action are UI state', () => {
  expect(vmGroupQueryParams({ name: 'a', domainid: id, account: 'owner', projectid: id, page: 2, pagesize: 20, listall: true, sortkey: 'name', sortorder: 'desc', tab: 'comments', q: 'a', action: 'deleteInstanceGroup', zoneid: id, showIcon: true })).toEqual({ name: 'a', domainid: id, account: 'owner', projectid: id, page: 2, pagesize: 20, listall: true })
})
test('only supported page sort fields/order survive', () => {
  expect(vmGroupQuery({ sortkey: 'zoneid', sortorder: 'asc' })).toEqual({})
  expect(vmGroupQuery({ sortkey: 'name', sortorder: 'desc' })).toEqual({ sortkey: 'name', sortorder: 'desc' })
})
test('page/owner/target/session changes invalidate rows; sort/tab retain the context', () => {
  const route = { path: '/vmgroup', query: { page: '1' } }
  const getters = { userInfo: { id: 'user' }, project: {} }
  const scope = vmGroupQueryScope(route, getters, 'session')
  expect(vmGroupQueryScope({ ...route, query: { page: '1', tab: 'comments', sortkey: 'name', sortorder: 'desc' } }, getters, 'session')).toBe(scope)
  for (const query of [{ page: '2' }, { account: 'other', domainid: id }, { projectid: id }, { keyword: 'other' }]) expect(vmGroupQueryScope({ ...route, query }, getters, 'session')).not.toBe(scope)
  expect(vmGroupQueryScope(route, { ...getters, project: { id } }, 'session')).not.toBe(scope)
  expect(vmGroupQueryScope(route, getters, 'new-session')).not.toBe(scope)
  expect(vmGroupQueryScope({ path: '/vmgroup/' + id, query: {} }, getters, 'session')).not.toBe(scope)
})
test.each([{}, { listinstancegroupsresponse: { errorcode: 531 } }, { listinstancegroupsresponse: { instancegroup: {} } }, { listinstancegroupsresponse: { instancegroup: [{ id, name: 'a' }], count: 0 } }, { listinstancegroupsresponse: { instancegroup: [{ id, name: 'a' }, { id, name: 'b' }], count: 2 } }])('partial/malformed response is a failure, not an empty result: %j', json => {
  expect(() => vmGroupQueryRows(json)).toThrow()
})
test('server empty envelope and paged results are distinguished', () => {
  expect(vmGroupQueryRows({ listinstancegroupsresponse: {} })).toEqual({ rows: [], count: 0 })
  expect(vmGroupQueryRows({ listinstancegroupsresponse: { instancegroup: [{ id, name: 'a' }], count: 42 } }).count).toBe(42)
})
test('retained failure has a different status from empty/permission', () => {
  expect(vmGroupQueryMessage('error', true)).toBe('message.vm.group.query.stale')
  expect(vmGroupQueryMessage('error', false)).toBe('message.vm.group.query.error')
  expect(vmGroupQueryMessage('forbidden', true)).toBe('message.vm.group.query.forbidden')
})
test('status renders a Korean-locale success time and explicit retry without raw errors', () => {
  const wrapper = mount(Status, { props: { status: 'error', retained: true, updated: 1700000000000 }, global: { components: { 'a-button': Button }, mocks: { $t: (key, args) => args?.time ? key + args.time : key, $i18n: { locale: 'ko_KR' } } } })
  expect(wrapper.text()).toContain('message.vm.group.query.stale')
  expect(wrapper.text()).toContain('message.vm.group.query.updated')
  return wrapper.find('button').trigger('click').then(() => expect(wrapper.emitted('retry')).toHaveLength(1))
})

test.each(['loading', 'refreshing', 'ready', 'empty'])('normal %s uses the existing Mold list without any status ribbon', status => {
  const wrapper = mount(Status, { props: { status, updated: 1700000000000 }, global: { components: { 'a-button': Button }, mocks: { $t: key => key, $i18n: { locale: 'ko_KR' } } } })
  expect(wrapper.find('p').exists()).toBe(false)
  expect(wrapper.find('button').exists()).toBe(false)
  expect(wrapper.text()).toBe('')
  wrapper.unmount()
})

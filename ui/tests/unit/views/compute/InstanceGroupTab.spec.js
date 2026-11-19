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

import { mount, flushPromises } from '@vue/test-utils'
import { reactive } from 'vue'
import GroupTab from '@/views/compute/InstanceGroupTab'
import ListView from '@/components/view/ListView'
const Antd = require('ant-design-vue').default || require('ant-design-vue')
jest.mock('@/api', () => ({ getAPI: jest.fn().mockResolvedValue({}), postAPI: jest.fn() }))
const id = '11111111-1111-1111-1111-111111111111'
const resource = { id, name: '그룹', account: 'admin', domainid: id }
let wrapper
beforeAll(() => { window.matchMedia = window.matchMedia || (() => ({ matches: false, addListener: () => {}, removeListener: () => {} })) })
afterEach(() => { if (wrapper) wrapper.unmount(); wrapper = null })
function groupWrapper (device) {
  window.history.replaceState({}, '', '#/vmgroup/' + id)
  return mount(GroupTab, { props: { resource }, global: { plugins: [Antd], mocks: { $t: key => key, $route: reactive({ path: '/vmgroup/' + id, query: {} }), $store: { state: { app: { device } }, getters: { apis: { listAnnotations: {} } } } }, stubs: { DetailsTab: { template: '<div>details</div>' }, AnnotationsTab: { template: '<textarea aria-label="comment" />' }, InstanceGroupMembers: { template: '<div>members</div>', props: ['active'] } } } })
}
test.each(['desktop', 'tablet', 'mobile'])('new group tabs follow VM tab/content orientation for %s', async device => {
  wrapper = groupWrapper(device)
  expect(wrapper.find(device === 'mobile' ? '.ant-tabs-top' : '.ant-tabs-left').exists()).toBe(true)
  const tabs = wrapper.findAll('[role="tab"]'); expect(tabs.map(tab => tab.text())).toEqual(['label.details', 'label.instances', 'label.annotations'])
  await tabs[1].trigger('click'); await flushPromises(); expect(wrapper.vm.currentTab).toBe('members')
  expect(wrapper.find('[role="tabpanel"][aria-hidden="false"]').text()).toContain('members')
  expect(window.location.hash).toContain('tab=members')
})
test('comments draft and tab position persist across tab switches', async () => {
  wrapper = groupWrapper('desktop'); const tabs = wrapper.findAll('[role="tab"]')
  await tabs[2].trigger('click'); await flushPromises(); await wrapper.get('textarea').setValue('한글 초안')
  await tabs[1].trigger('click'); await tabs[2].trigger('click'); await flushPromises()
  expect(wrapper.get('textarea').element.value).toBe('한글 초안'); expect(wrapper.find('.ant-tabs-left').exists()).toBe(true)
})
test('embedded VM list uses VM detail links and disabled candidate selection', async () => {
  wrapper = mount(ListView, { props: { resourcePath: '/vm', keyboardContextMenu: true, explicitlyAllowRowSelection: true, selectionDisabled: row => row.id === id, columns: [{ key: 'name', dataIndex: 'name', title: 'VM' }], items: [{ id, name: 'member' }] }, global: { plugins: [Antd], provide: { parentFetchData: () => {}, parentToggleLoading: () => {} }, mocks: { $t: key => key, $showIcon: () => false, $route: { path: '/vmgroup/' + id, meta: { name: 'vmgroup' }, query: {} }, $store: { getters: { userInfo: { roletype: 'User' }, apis: {} } } }, stubs: { RouterLink: { props: ['to'], template: '<a :href="to.path"><slot /></a>' } } } })
  expect(wrapper.find('a[href="/vm/' + id + '"]').exists()).toBe(true)
  expect(wrapper.find('tbody input[type="checkbox"]').element.disabled).toBe(true)
  expect(wrapper.get('tr.ant-table-row').attributes('tabindex')).toBe('0')
})

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
import { shallowMount } from '@vue/test-utils'
import { h } from 'vue'
import InfoCard from '@/components/view/InfoCard'
import DetailsTab from '@/components/view/DetailsTab'
jest.mock('@/api', () => ({ getAPI: jest.fn().mockResolvedValue({}) }))
const names = ['USER.LOGIN', 'USER.LOGOUT', 'ROUTER.HEALTH.CHECKS', 'FIREWALL.CLOSE', 'ALERT.SERVICE.DOMAINROUTER']
function mount (component, name, routeName = 'ssh') {
  return shallowMount(component, {
    props: { resource: { id: '11111111-1111-1111-1111-111111111111', name }, isStatic: true },
    global: {
      mocks: { $route: { path: '/' + routeName + '/one', fullPath: '/' + routeName + '/one', meta: { name: routeName, details: ['name'] } }, $store: { getters: { apis: {}, userInfo: { roletype: 'Admin' }, darkMode: false } }, $router: { resolve: () => ({ matched: [] }) }, $t: key => names.some(n => n.toLowerCase() === key) ? 'Event label' : key === 'label.name' ? '이름' : key, $showIcon: () => false, $config: { theme: {} } },
      stubs: { 'a-card': { template: '<div><slot /></div>' }, 'a-spin': { template: '<div><slot /></div>' }, 'a-list': { render () { return h('div', this.$slots.renderItem({ item: 'name' })) } }, 'a-list-item': { template: '<div><slot /></div>' } }
    }
  })
}
test.each(names)('SSH header does not treat %s as an event translation key', name => {
  const wrapper = mount(InfoCard, name)
  expect(wrapper.find('.resource-details__name').text()).toBe(name)
  expect(wrapper.find('h4.name').text()).toBe(name)
  wrapper.unmount()
})
test.each(names)('SSH details name %s remains raw while the field label stays translated', name => {
  const wrapper = mount(DetailsTab, name)
  expect(wrapper.text()).toContain('이름')
  expect(wrapper.text()).toContain(name)
  expect(wrapper.text()).not.toContain('Event label')
  wrapper.unmount()
})
test('event resource headers retain their translated names', () => {
  const wrapper = mount(InfoCard, 'USER.LOGIN', 'event')
  expect(wrapper.find('.resource-details__name').text()).toBe('Event label')
  wrapper.unmount()
})
test('event detail values retain their translated names', () => {
  const wrapper = mount(DetailsTab, 'USER.LOGIN', 'event')
  expect(wrapper.text()).toContain('Event label')
  expect(wrapper.text()).not.toContain('USER.LOGIN')
  wrapper.unmount()
})

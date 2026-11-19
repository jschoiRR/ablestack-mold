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
import ListView from '@/components/view/ListView'
jest.mock('@/api', () => ({ getAPI: jest.fn().mockResolvedValue({}) }))

function cell (projectid, matched = [{}]) {
  const record = { id: 'group', projectid, project: projectid ? 'Project' : '' }
  const resolve = jest.fn(() => ({ matched }))
  const wrapper = shallowMount(ListView, {
    props: { columns: [{ key: 'project', dataIndex: 'project' }], items: [record] },
    global: {
      provide: { parentFetchData: jest.fn(), parentToggleLoading: jest.fn() },
      mocks: { $route: { name: 'vmgroup', path: '/vmgroup', meta: {} }, $router: { resolve }, $store: { getters: { apis: {}, userInfo: { roletype: 'Admin' } } }, $t: key => key },
      stubs: {
        'a-table': { render () { return h('div', this.$slots.bodyCell({ column: { key: 'project' }, text: record.project, record })) } },
        'router-link': { props: ['to'], render () { return h('a', { href: this.to.path }, this.$slots.default()) } }
      }
    }
  })
  return { wrapper, resolve }
}

test.each([undefined, null, ''])('non-project group with id=%s has no invalid or focusable project link', id => {
  const { wrapper, resolve } = cell(id)
  expect(wrapper.find('a').exists()).toBe(false)
  expect(resolve).not.toHaveBeenCalled()
  wrapper.unmount()
})
test('real project reference keeps its link and visible name', () => {
  const { wrapper } = cell('project-id')
  expect(wrapper.find('a').attributes('href')).toBe('/project/project-id')
  expect(wrapper.find('a').text()).toBe('Project')
  wrapper.unmount()
})
test.each([{ matched: [] }, { matched: [{ redirect: '/exception/404' }] }])('unavailable project route renders text without a link', ({ matched }) => {
  const { wrapper } = cell('project-id', matched)
  expect(wrapper.find('a').exists()).toBe(false)
  expect(wrapper.text()).toBe('Project')
  wrapper.unmount()
})

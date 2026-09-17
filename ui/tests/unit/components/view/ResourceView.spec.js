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

import fs from 'fs'
import path from 'path'
import { mount, flushPromises } from '@vue/test-utils'
import { defineAsyncComponent, h, markRaw } from 'vue'
import ResourceView from '@/components/view/ResourceView'
import AutogenView from '@/views/AutogenView'

jest.mock('@/components/view/InfoCard', () => ({ render: () => null }))
jest.mock('@/api', () => ({ getAPI: jest.fn() }))
jest.mock('@/utils/mixin.js', () => ({ mixinDevice: {} }))

function context (query = {}, tabs = [{ name: 'details' }, { name: 'events' }]) {
  return { tabs, $route: { query }, historyTab: '', showTab: tab => !tab.hidden }
}

describe('ResourceView resource navigation', () => {
  it.each([
    ['/customaction/2', {}, true],
    ['/customaction/3', { tab: 'events' }, true],
    ['/vm/4', { keyword: 'action/' }, true],
    ['/action/updateCustomAction', {}, false],
    ['/vm/4', { tab: 'browser' }, false]
  ])('refreshes the destination data for %s with query %j: %s', (routePath, query, refresh) => {
    const vm = {
      resetSelection: jest.fn(),
      clearAutoRefresh: jest.fn(),
      fetchData: jest.fn(),
      scheduleAutoRefresh: jest.fn()
    }
    AutogenView.watch.$route.call(vm,
      { path: routePath, fullPath: routePath + '?' + new URLSearchParams(query), query },
      { fullPath: '/extension/1?tab=customactions' })
    expect(vm.fetchData).toHaveBeenCalledTimes(refresh ? 1 : 0)
  })

  it('isolates detail instances by path, not tab query or delayed API resource ID', () => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../../src/views/AutogenView.vue'), 'utf8')
    expect(source).toMatch(/<resource-view\s+v-else\s+:key="\$route.path"/)
  })

  it.each([
    [{ tab: 'events' }, '', 'events'],
    [{ tab: 'customactions' }, '', 'details'],
    [{}, 'events', 'events'],
    [{}, 'customactions', 'details']
  ])('selects a valid tab for query %j and history %s', (query, historyTab, expected) => {
    const vm = { ...context(query), historyTab }
    ResourceView.methods.setActiveTab.call(vm)
    expect(vm.activeTab).toBe(expected)
  })

  it('preserves a declared tab while resource-dependent visibility is loading', () => {
    const vm = context({ tab: 'events' }, [{ name: 'details' }, { name: 'events', hidden: true }])
    ResourceView.methods.setActiveTab.call(vm)
    expect(vm.activeTab).toBe('events')
    vm.tabs = []
    ResourceView.methods.setActiveTab.call(vm)
    expect(vm.activeTab).toBe('')
  })

  it('removes the exact popstate listener on unmount', () => {
    const add = jest.spyOn(window, 'addEventListener')
    const remove = jest.spyOn(window, 'removeEventListener')
    const vm = { setActiveTab: jest.fn(), fetchData: jest.fn() }
    ResourceView.created.call(vm)
    ResourceView.beforeUnmount.call(vm)
    expect(add).toHaveBeenCalledWith('popstate', vm.setActiveTab)
    expect(remove).toHaveBeenCalledWith('popstate', vm.setActiveTab)
    add.mockRestore()
    remove.mockRestore()
  })

  it('repeatedly navigates between async tabs of different resources without retaining old content', async () => {
    const errors = []
    const unmounted = jest.fn()
    const asyncTab = label => markRaw(defineAsyncComponent(async () => ({
      props: ['resource'],
      unmounted,
      render () { return h('span', `${label}:${this.resource.id}`) }
    })))
    const extensionTabs = [{ name: 'details', component: asyncTab('extension') }, { name: 'customactions', component: asyncTab('actions') }]
    const actionTabs = [{ name: 'details', component: asyncTab('action') }, { name: 'events', resourceType: 'ExtensionCustomAction', component: asyncTab('events') }]
    const layout = { render () { return h('div', this.$slots.right()) } }
    const slot = { render () { return h('div', this.$slots.default()) } }
    const wrapper = mount({
      data: () => ({ routePath: '/extension/1', resource: { id: '1' }, tabs: extensionTabs }),
      render () { return h(ResourceView, { key: this.routePath, resource: this.resource, tabs: this.tabs }) }
    }, {
      global: {
        mocks: { $route: { query: {} }, $t: key => key, $store: { getters: { userInfo: {} } } },
        stubs: { ResourceLayout: layout, ACard: slot, ATabs: slot, ATabPane: slot },
        config: { errorHandler: error => errors.push(error) }
      }
    })
    for (let i = 0; i < 3; i++) {
      await flushPromises()
      expect(wrapper.text()).toContain('extension:1')
      await wrapper.setData({ routePath: '/customaction/2', resource: { id: '2' }, tabs: actionTabs })
      await flushPromises()
      expect(wrapper.text()).toContain('action:2')
      expect(wrapper.text()).not.toContain('extension:')
      await wrapper.setData({ routePath: '/extension/1', resource: { id: '1' }, tabs: extensionTabs })
    }
    await flushPromises()
    wrapper.unmount()
    expect(unmounted).toHaveBeenCalled()
    expect(errors).toEqual([])
  })
})

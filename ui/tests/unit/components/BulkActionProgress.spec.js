// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

import { mount, flushPromises } from '@vue/test-utils'
import BulkActionProgress from '@/components/view/BulkActionProgress'
import { bulkColumns, updateBulkItem } from '@/utils/bulkAction'

const render = (props = {}, fetch = jest.fn().mockResolvedValue()) => mount(BulkActionProgress, {
  props: { showGroupActionModal: true, ...props },
  global: {
    provide: { parentFetchData: fetch },
    mocks: { $t: key => key, $route: { path: '/vm', meta: {} }, $config: {} },
    stubs: {
      'a-modal': { template: '<div><slot /><slot name="footer" /></div>' },
      'a-card': { template: '<div><slot /></div>' },
      'a-table': true,
      'a-divider': true,
      'a-button': true
    }
  }
})

describe('Bulk action progress', () => {
  beforeEach(() => jest.useFakeTimers())
  afterEach(() => jest.useRealTimers())

  it('never mutates list columns when preparing repeated jobs', () => {
    const columns = [{ key: 'name' }, { key: 'status' }]
    const first = bulkColumns(columns)
    first.unshift({ key: 'status' })
    const second = bulkColumns(columns)
    second.unshift({ key: 'status' })
    expect(columns).toEqual([{ key: 'name' }, { key: 'status' }])
    expect(second.filter(c => c.key === 'status')).toHaveLength(1)
  })
  it.each(['success', 'failed'])('does not regress a terminal %s result', terminal => {
    const item = { status: 'InProgress', jobid: 'job-1' }
    updateBulkItem(item, terminal, 'job-1')
    updateBulkItem(item, 'InProgress', 'job-1')
    updateBulkItem(item, terminal === 'success' ? 'failed' : 'success', 'job-1')
    expect(item.status).toBe(terminal)
  })
  it('rejects events from another job', () => {
    const item = { status: 'InProgress', jobid: 'new' }
    updateBulkItem(item, 'success', 'old')
    expect(item.status).toBe('InProgress')
  })
  it('keeps filtered results current and restores all rows after clearing a filter', async () => {
    const wrapper = render({ selectedItems: [{ id: 'a', status: 'InProgress' }, { id: 'b', status: 'success' }] })
    wrapper.vm.handleTableChange({}, { status: ['InProgress'] })
    expect(wrapper.vm.filteredItems).toHaveLength(1)
    await wrapper.setProps({ selectedItems: [{ id: 'a', status: 'success' }, { id: 'b', status: 'success' }] })
    expect(wrapper.vm.filteredItems).toHaveLength(0)
    wrapper.vm.handleTableChange({}, { status: null })
    expect(wrapper.vm.filteredItems).toHaveLength(2)
    expect(wrapper.vm.succeededCount).toBe(2)
    wrapper.unmount()
  })
  it('coalesces job changes into a list refresh and does not loop on resource updates', async () => {
    const fetch = jest.fn().mockResolvedValue()
    const wrapper = render({ selectedItems: [{ id: 'a', status: 'InProgress' }] }, fetch)
    await wrapper.setProps({ selectedItems: [{ id: 'a', status: 'success' }] })
    await wrapper.setProps({ selectedItems: [{ id: 'a', status: 'success', name: 'fresh name' }] })
    jest.advanceTimersByTime(50)
    await flushPromises()
    expect(fetch).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
  it('cleans pending refreshes on unmount', async () => {
    const fetch = jest.fn()
    const wrapper = render({ selectedItems: [{ id: 'a', status: 'InProgress' }] }, fetch)
    await wrapper.setProps({ selectedItems: [{ id: 'a', status: 'failed' }] })
    wrapper.unmount()
    jest.runOnlyPendingTimers()
    expect(fetch).not.toHaveBeenCalled()
  })
  it('deduplicates the operation status column in all shared dialogs', () => {
    const wrapper = render({ selectedColumns: [{ key: 'status' }, { key: 'name' }, { key: 'status' }] })
    expect(wrapper.vm.progressColumns.map(c => c.key)).toEqual(['status', 'name'])
    wrapper.unmount()
  })
})

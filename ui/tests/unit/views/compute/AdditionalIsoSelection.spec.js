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

import { shallowMount } from '@vue/test-utils'
import AdditionalIsoSelection from '@/views/compute/AdditionalIsoSelection.vue'
import { getAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn() }))
const mount = () => shallowMount(AdditionalIsoSelection, {
  props: { zoneId: 'zone-a', primaryId: 'install', supported: true },
  global: { mocks: { $t: key => key } }
})
describe('Additional ISO deployment selection', () => {
  beforeEach(() => jest.clearAllMocks())
  test('starts disabled without querying media', () => {
    const wrapper = mount()
    expect(wrapper.vm.enabled).toBe(false)
    expect(getAPI).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  test('only selects a non-bootable ISO distinct from the primary', async () => {
    getAPI.mockResolvedValue({
      listisosresponse: {
        iso: [
          { id: 'driver', name: 'VirtIO', bootable: false },
          { id: 'boot', bootable: true },
          { id: 'install', bootable: false },
          { id: 'unknown' }
        ]
      }
    })
    const wrapper = mount()
    wrapper.vm.enabled = true
    await wrapper.vm.toggle()
    expect(getAPI).toHaveBeenCalledWith('listIsos', expect.objectContaining({ bootable: false, zoneid: 'zone-a' }))
    expect(wrapper.vm.isos.map(iso => iso.id)).toEqual(['driver'])
    expect(wrapper.emitted('change').slice(-1)[0][0].valid).toBe(false)
    wrapper.vm.selected = 'driver'
    wrapper.vm.publish()
    expect(wrapper.emitted('change').slice(-1)[0][0]).toEqual({ enabled: true, ids: ['driver'], valid: true })
    wrapper.vm.enabled = false
    await wrapper.vm.toggle()
    expect(wrapper.emitted('change').slice(-1)[0][0]).toEqual({ enabled: false, ids: [], valid: true })
    wrapper.unmount()
  })
  test('discards a late response after zone change', async () => {
    let complete
    getAPI.mockReturnValue(new Promise(resolve => { complete = resolve }))
    const wrapper = mount()
    wrapper.vm.enabled = true
    const pending = wrapper.vm.toggle()
    await wrapper.setProps({ zoneId: 'zone-b' })
    complete({ listisosresponse: { iso: [{ id: 'stale', bootable: false }] } })
    await pending
    expect(wrapper.vm.isos).toEqual([])
    expect(wrapper.vm.enabled).toBe(false)
    wrapper.unmount()
  })
  test('resets selection when the installation ISO or capability changes', async () => {
    const wrapper = mount()
    wrapper.vm.enabled = true
    wrapper.vm.selected = 'driver'
    await wrapper.setProps({ primaryId: 'another-install' })
    expect(wrapper.vm.selected).toBeUndefined()
    wrapper.vm.enabled = true
    await wrapper.setProps({ supported: false })
    expect(wrapper.vm.enabled).toBe(false)
    wrapper.unmount()
  })
  test('a request failure blocks only the enabled additional selection', async () => {
    getAPI.mockRejectedValue(new Error('unavailable'))
    const wrapper = mount()
    wrapper.vm.enabled = true
    await wrapper.vm.toggle()
    expect(wrapper.emitted('change').slice(-1)[0][0].valid).toBe(false)
    wrapper.vm.reset()
    expect(wrapper.emitted('change').slice(-1)[0][0].valid).toBe(true)
    wrapper.unmount()
  })
})

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
import { h } from 'vue'
import ListView from '@/components/view/ListView'
jest.mock('@/api', () => ({ getAPI: jest.fn().mockResolvedValue({}) }))
jest.mock('vue-code-highlight/src/CodeHighlight.vue', () => ({}), { virtual: true })
jest.mock('vue-code-highlight/themes/prism-okaidia.css', () => ({}), { virtual: true })
function cell (type, name = 'affinitygroup') {
  const record = { id: 'group', type }
  return shallowMount(ListView, {
    props: { columns: [{ key: 'type', dataIndex: 'type' }], items: [record] },
    global: {
      provide: { parentFetchData: jest.fn(), parentToggleLoading: jest.fn() },
      mocks: { $route: { name, path: '/' + name, meta: { name } }, $store: { getters: { apis: {}, userInfo: { roletype: 'Admin' } } }, $t: key => key === 'label.affinity.type.strict.affinity' ? '같은 호스트 배치 · 엄격' : key },
      stubs: { AffinityGroupPolicy: false, 'a-table': { render () { return h('div', this.$slots.bodyCell({ column: { key: 'type' }, text: type, record })) } } }
    }
  })
}
test('translated affinity policy replaces the generic raw type instead of rendering both', () => {
  const w = cell('host affinity'); expect(w.text()).toBe('같은 호스트 배치 · 엄격'); w.unmount()
})
test('an unknown affinity policy remains visible once without a false translation', () => {
  const w = cell('Future policy'); expect(w.text()).toBe('Future policy'); w.unmount()
})
test('the existing VM snapshot type rendering remains independent', () => {
  const w = cell('Disk', 'vmsnapshot'); expect(w.text()).toBe('label.vmsnapshot.disk'); w.unmount()
})

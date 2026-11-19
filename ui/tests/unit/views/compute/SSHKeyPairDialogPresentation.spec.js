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
import CreateSSHKeyPair from '@/views/compute/CreateSSHKeyPair'
import MoldDialog from '@/components/view/MoldDialog'
import compute from '@/config/section/compute'
import AutogenView from '@/views/AutogenView'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
const shell = { props: ['title', 'size', 'closable', 'keyboard'], template: '<section><header>{{ title }}</header><main><slot /></main><footer><slot name="footer" /></footer></section>' }
const button = { props: ['type', 'disabled', 'loading'], template: '<button :data-primary="type === \'primary\'" :disabled="disabled"><slot /></button>' }
function mount (api) {
  return shallowMount(CreateSSHKeyPair, { props: { currentAction: { api } }, global: { mocks: { $t: key => key, $store: { getters: { userInfo: { roletype: 'User' }, apis: { [api]: {} } } }, $getApiParams: () => ({ name: {}, publickey: {}, account: {}, domainid: {} }) }, directives: { focus: {}, ctrlEnter: {}, clipboard: {} }, stubs: { MoldDialog: shell, TooltipLabel: { props: ['title'], template: '<span>{{ title }}</span>' }, 'a-input': { name: 'AInput', template: '<input />' }, 'a-spin': { template: '<div><slot /></div>' }, 'a-form': { template: '<form><slot /></form>' }, 'a-form-item': { template: '<label><slot name="label" /><slot /></label>' }, 'a-button': button, 'a-descriptions': { template: '<dl><slot /></dl>' }, 'a-descriptions-item': { template: '<dd><slot /></dd>' } } } })
}
test.each([['createSSHKeyPair', 'label.create.ssh.key.pair', 'label.create', false], ['registerSSHKeyPair', 'label.register.ssh.key.pair', 'label.ssh.keypair.register', true]])('%s has an independent Footer and matching title/primary/fields', (api, title, primary, publicField) => {
  const wrapper = mount(api); expect(wrapper.find('header').text()).toBe(title); expect(wrapper.findComponent(MoldDialog).props('size')).toBe('md'); expect(wrapper.find('footer').text()).toBe('label.cancel' + primary); expect(wrapper.find('form button').exists()).toBe(false); expect(wrapper.findComponent({ name: 'AInput' }).exists()).toBe(true); expect(wrapper.find('form').text().includes('label.publickey')).toBe(publicField); wrapper.unmount()
})
test('private-key result has one primary and raw summary without private key content in DOM', async () => {
  const wrapper = mount('createSSHKeyPair'); await wrapper.setData({ isSubmitted: true, privateKey: 'UNIT-ONLY-PRIVATE-CONTENT', result: { name: 'USER.LOGIN', fingerprint: 'fp', account: 'owner', domain: 'ROOT', project: 'Project' } })
  expect(wrapper.find('header').text()).toBe('label.ssh.privatekey.result'); expect(wrapper.find('main').text()).toContain('USER.LOGIN'); expect(wrapper.html()).not.toContain('UNIT-ONLY-PRIVATE-CONTENT'); expect(wrapper.findAll('footer button[data-primary="true"]')).toHaveLength(1); expect(wrapper.find('footer').text()).toBe('label.closelabel.copy.clipboardlabel.download'); wrapper.unmount()
})
test('loading keeps shell and Footer while blocking closing and keyboard cancellation', async () => {
  const wrapper = mount('registerSSHKeyPair'); await wrapper.setData({ loading: true, error: 'Retained API error' }); expect(wrapper.find('footer').exists()).toBe(true); expect(wrapper.findComponent(MoldDialog).props('closable')).toBe(false); expect(wrapper.findComponent(MoldDialog).props('keyboard')).toBe(false); expect(wrapper.find('footer button').attributes('disabled')).toBeDefined(); wrapper.unmount()
})
test('unsaved close uses the same shell rather than a second modal', async () => {
  const wrapper = mount('createSSHKeyPair'); await wrapper.setData({ isSubmitted: true, privateKey: 'unit-only' }); await wrapper.find('footer button').trigger('click'); expect(wrapper.vm.confirmDiscard).toBe(true); expect(wrapper.findAllComponents(MoldDialog)).toHaveLength(1); expect(wrapper.find('footer').text()).toBe('label.ssh.privatekey.continuelabel.ssh.privatekey.discard'); expect(wrapper.emitted('close-action')).toBeUndefined(); wrapper.unmount()
})
test.each(['createSSHKeyPair', 'registerSSHKeyPair'])('%s-only role sees its matching entry and self-managed shell', api => {
  const actions = compute.children.find(section => section.name === 'ssh').actions
  const visible = AutogenView.computed.visibleListActions.call({ actions, dataView: false, selectedRowKeys: [], $store: { getters: { apis: { [api]: {} } } } })
  expect(visible.map(action => action.api)).toEqual([api]); expect(visible[0].selfManagedDialog).toBe(true)
})

test('the external Footer submit belongs to the form and Ctrl+Enter uses the same handler', async () => {
  const submit = jest.spyOn(CreateSSHKeyPair.methods, 'handleSubmit').mockImplementation(() => {}); const wrapper = mount('registerSSHKeyPair')
  expect(wrapper.find('form').attributes('id')).toBe('ssh-keypair-form'); expect(wrapper.find('footer button[data-primary="true"]').attributes('form')).toBe('ssh-keypair-form')
  await wrapper.find('form').trigger('keydown', { key: 'Enter', ctrlKey: true }); expect(submit).toHaveBeenCalledTimes(1)
  await wrapper.find('footer button[data-primary="true"]').trigger('keydown', { key: 'Enter', ctrlKey: true }); expect(submit).toHaveBeenCalledTimes(2); wrapper.unmount(); submit.mockRestore()
})

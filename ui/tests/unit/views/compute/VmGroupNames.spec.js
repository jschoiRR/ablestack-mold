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
import AutogenView from '@/views/AutogenView'
import InstanceGroupDeleteSummary from '@/components/view/InstanceGroupDeleteSummary'
import compute from '@/config/section/compute'
import { normalizeVmGroupName, vmGroupErrorKey, vmGroupDeleteTargets } from '@/utils/vmGroupNames'
import { buildVmGroupUpdate } from '@/utils/vmGroupAssignment'

test.each([null, undefined, '', '   ', '\t\r\n', '\u00a0\u3000\ufeff', '\u2000\u202f\u205f'])('rejects blank name %j', value => {
  expect(() => normalizeVmGroupName(value)).toThrow('message.vm.group.blank.name')
})
test.each(['가', 'a'])('accepts 255 and rejects 256 %s characters', character => {
  expect(normalizeVmGroupName(' ' + character.repeat(255) + ' ')).toBe(character.repeat(255))
  expect(() => normalizeVmGroupName(character.repeat(256))).toThrow('message.vm.group.name.length')
})
test('supplementary characters fit the ORM boundary without truncation', () => {
  const name = '😀'.repeat(127) + 'A'
  expect(normalizeVmGroupName(' ' + name + ' ')).toBe(name)
  expect(() => normalizeVmGroupName('😀'.repeat(128))).toThrow('message.vm.group.name.length')
})
test('trims surrounding Unicode whitespace while preserving internal whitespace', () => {
  expect(normalizeVmGroupName('\u00a0 한글  그룹 \u3000')).toBe('한글  그룹')
})
test('accepts the bounded raw input after trimming', () => {
  expect(normalizeVmGroupName(' '.repeat(1023) + 'A')).toBe('A')
})
test('rejects oversized raw input before normalization', () => {
  expect(() => normalizeVmGroupName(' '.repeat(1024) + 'A')).toThrow('message.vm.group.name.input.length')
})
test('rejects internal ASCII controls before transmission', () => {
  expect(() => normalizeVmGroupName('한\t글')).toThrow('message.vm.group.name.characters')
})
test('preserves exact legacy no-op without rewriting it', () => {
  expect(normalizeVmGroupName(' 기존 ', ' 기존 ')).toBe(' 기존 ')
  expect(normalizeVmGroupName(' 새 이름 ', ' 기존 ')).toBe('새 이름')
})
test('VM assignment selects an existing normalized candidate or creates the normalized name', () => {
  expect(buildVmGroupUpdate({ group: 'A' }, ' B ', [{ value: 'B', groupId: 'b' }])).toEqual({ groupid: 'b' })
  expect(buildVmGroupUpdate({ group: 'A' }, ' 새 이름 ', [])).toEqual({ group: '새 이름' })
})
test('existing legacy UUID selection and no-op are retained', () => {
  expect(buildVmGroupUpdate({ group: 'A' }, ' 기존 ', [{ value: ' 기존 ', groupId: 'old' }])).toEqual({ groupid: 'old' })
  expect(buildVmGroupUpdate({ group: ' 기존 ' }, ' 기존 ', [])).toEqual({})
})
test.each([
  ['[VM_GROUP_NAME_DUPLICATE] duplicate', 431, 'message.vm.group.name.duplicate'],
  ['Unable to create Instance group, a group with name A already exists for Account AccountVO:private', 431, 'message.vm.group.name.duplicate'],
  ['[VM_GROUP_NAME_REQUIRED]', 431, 'message.vm.group.blank.name'],
  ['[VM_GROUP_NAME_TOO_LONG]', 431, 'message.vm.group.name.length'],
  ['Value greater than max allowed length 1024 for param: groupName', 431, 'message.vm.group.name.input.length'],
  ['Received value containing illegal ASCII non-printable characters for parameter name', 431, 'message.vm.group.name.characters'],
  ['[VM_GROUP_NOT_FOUND]', 431, 'message.vm.group.operation.missing'],
  ['unable to find an instance group with specified groupId', 431, 'message.vm.group.operation.missing'],
  ['denied', 531, 'message.vm.group.operation.forbidden'],
  ['denied', 432, 'message.vm.group.operation.forbidden'],
  ['arbitrary AccountVO:private', 530, 'message.vm.group.operation.failed']
])('maps server error %s to a safe localized key', (errortext, errorcode, expected) => {
  expect(vmGroupErrorKey({ response: { data: { errorresponse: { errortext, errorcode } } } })).toBe(expected)
})
test('lost response does not expose raw internal details', () => {
  expect(vmGroupErrorKey(new Error('Network Error'))).toBe('message.vm.group.operation.failed')
})
test('deletion confirmation snapshots names, owners and IDs', () => {
  const selected = [{ id: 'a', name: '<img src=x>', account: 'owner-a', domain: 'ROOT' }, { id: 'b', name: 'B', project: 'P', domain: 'ROOT' }]
  const targets = vmGroupDeleteTargets({ invokedAsGroupAction: true }, selected)
  selected[0].id = 'other'; selected[1].name = 'changed'
  expect(targets.map(group => group.id)).toEqual(['a', 'b'])
  const wrapper = mount(InstanceGroupDeleteSummary, { props: { groups: targets }, global: { mocks: { $t: (key, values) => key + (values ? values.count : '') } } })
  expect(wrapper.text()).toContain('owner-a')
  expect(wrapper.text()).toContain('P')
  expect(wrapper.text()).toContain('ROOT')
  expect(wrapper.text()).toContain('message.vm.group.delete.targets2')
  expect(wrapper.find('img').exists()).toBe(false)
  expect(wrapper.text()).toContain('<img src=x>')
})
test('single deletion snapshots the current target only', () => {
  expect(vmGroupDeleteTargets({ resource: { id: 'one', name: 'One', account: 'owner', domain: 'ROOT' } }, [{ id: 'other' }])).toEqual([{ id: 'one', name: 'One', account: 'owner', domain: 'ROOT', project: undefined }])
})
test('bulk deletion uses confirmed targets after live list selection is cleared', async () => {
  const context = {
    dataView: false,
    selectedRowKeys: [],
    selectedItems: [],
    items: [],
    chosenColumns: [],
    currentAction: { api: 'deleteInstanceGroup', invokedAsGroupAction: true, vmGroupTargets: [{ id: 'a', name: 'A' }, { id: 'b', name: 'B' }], groupMap: keys => keys.map(id => ({ id })) },
    form: {},
    formRef: { value: { validate: () => Promise.resolve() } },
    modalInfo: {},
    callGroupApi: jest.fn().mockResolvedValue(),
    fetchData: jest.fn(),
    $message: { info: jest.fn() },
    $t: key => key
  }
  AutogenView.methods.handleSubmit.call(context, {})
  await flushPromises()
  expect(context.callGroupApi.mock.calls).toEqual([[{ id: 'a' }, 'A'], [{ id: 'b' }, 'B']])
  expect(context.selectedItems.map(group => group.id)).toEqual(['a', 'b'])
})
test('single deletion uses confirmed UUID after the resource pointer changes', async () => {
  const context = formContext('deleteInstanceGroup', undefined, {})
  context.currentAction.vmGroupTargets = [{ id: 'confirmed', name: 'Confirmed' }]
  context.resource = { id: 'other', name: 'Other' }
  AutogenView.methods.execSubmit.call(context, { preventDefault: jest.fn() })
  await flushPromises()
  expect(context.postSnapshotAwareAction).toHaveBeenCalledWith(context.currentAction, { id: 'confirmed' })
})
test('group deletion stays in detail and context menu with no VM deletion API', () => {
  const action = compute.children.find(section => section.name === 'vmgroup').actions.find(action => action.api === 'deleteInstanceGroup')
  expect(action.dataView && action.groupAction && action.popup).toBe(true)
  expect(action.groupMap(['a', 'b'])).toEqual([{ id: 'a' }, { id: 'b' }])
})
function formContext (api, name, failure) {
  const context = {
    form: { name },
    resource: { id: 'target', name: 'Original' },
    formRef: { value: { validate: jest.fn().mockResolvedValue(), scrollToField: jest.fn() } },
    currentAction: { api, params: api === 'createInstanceGroup' ? [{ name: 'name', type: 'string' }] : [{ name: 'id' }, { name: 'name', type: 'string' }] },
    selectedItems: [],
    $t: key => key,
    $message: { info: jest.fn() },
    $notification: { error: jest.fn() },
    $notifyError: jest.fn(),
    listScope: () => 'scope',
    getDataIdentifier: params => params.id,
    postSnapshotAwareAction: jest.fn().mockRejectedValue(failure),
    closeAction: jest.fn()
  }
  context.notifyVmGroupError = error => AutogenView.methods.notifyVmGroupError.call(context, error)
  return context
}
test.each(['createInstanceGroup', 'updateInstanceGroup'])('%s retains raw input and target after duplicate failure', async api => {
  const context = formContext(api, ' 사용자 입력 ', { response: { data: { errorresponse: { errorcode: 431, errortext: '[VM_GROUP_NAME_DUPLICATE] AccountVO:private' } } } })
  AutogenView.methods.execSubmit.call(context, { preventDefault: jest.fn() })
  await flushPromises()
  expect(context.postSnapshotAwareAction).toHaveBeenCalledWith(context.currentAction, { ...(api === 'updateInstanceGroup' ? { id: 'target' } : {}), name: '사용자 입력' })
  expect(context.form.name).toBe(' 사용자 입력 ')
  expect(context.resource.id).toBe('target')
  expect(context.closeAction).not.toHaveBeenCalled()
  expect(context.$notifyError).not.toHaveBeenCalled()
  expect(context.$notification.error).toHaveBeenCalledWith({ message: 'message.request.failed', description: 'message.vm.group.name.duplicate' })
  expect(context.actionLoading).toBe(false)
})
test('unchanged rename does not POST and reports a no-op', async () => {
  const context = formContext('updateInstanceGroup', 'Original', {})
  AutogenView.methods.execSubmit.call(context, { preventDefault: jest.fn() })
  await flushPromises()
  expect(context.postSnapshotAwareAction).not.toHaveBeenCalled()
  expect(context.$message.info).toHaveBeenCalledWith('message.vm.group.name.unchanged')
})
test('Autogen group-name rule rejects blank and overlong values before requests', async () => {
  const context = { currentAction: { api: 'createInstanceGroup' }, resource: {}, rules: {}, $t: key => key }
  AutogenView.methods.setRules.call(context, { name: 'name', type: 'string', required: true })
  const rule = context.rules.name[0]
  await expect(rule.validator({}, ' ')).rejects.toBe('message.vm.group.blank.name')
  await expect(rule.validator({}, '가'.repeat(256))).rejects.toBe('message.vm.group.name.length')
  await expect(rule.validator({}, ' 한글 ')).resolves.toBeUndefined()
})

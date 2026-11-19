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

import { buildVmGroupUpdate, isGroupOnlyEdit } from '@/utils/vmGroupAssignment'
import EditVM from '@/views/compute/EditVM.vue'
import { postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))

const resource = { id: 'vm', group: 'A', groupid: 'a' }
const groups = [{ value: 'A', groupId: 'a' }, { value: 'B', groupId: 'b' }]
it('preserves membership for unchanged names and an initially ungrouped VM', () => {
  expect(buildVmGroupUpdate(resource, 'A', groups)).toEqual({})
  expect(buildVmGroupUpdate({ id: 'vm' }, undefined)).toEqual({})
})
it('sends explicit removal for cleared input and existing IDs for moves', () => {
  expect(buildVmGroupUpdate(resource, '', groups)).toEqual({ cleargroup: true })
  expect(buildVmGroupUpdate(resource, 'B', groups)).toEqual({ groupid: 'b' })
})
it('trims new names and preserves names selected from existing candidates', () => {
  expect(buildVmGroupUpdate(resource, ' 새 그룹 ', groups)).toEqual({ group: '새 그룹' })
  expect(buildVmGroupUpdate(resource, 'B', groups, { groupId: false })).toEqual({ group: 'B' })
  expect(buildVmGroupUpdate(resource, ' 기존 ', [{ value: ' 기존 ', groupId: 'old' }], { groupId: false })).toEqual({ group: ' 기존 ' })
})
it('rejects spaces and refuses to silently ignore clear on an older server', () => {
  expect(() => buildVmGroupUpdate(resource, '   ', groups)).toThrow('message.vm.group.blank.name')
  expect(() => buildVmGroupUpdate(resource, '', groups, { clearGroup: false })).toThrow('message.vm.group.clear.unsupported')
})
it('keeps changed VM attributes out of the group-only path', () => {
  const initial = { displayname: 'vm', group: 'A', userdata: 'data', haenable: true }
  expect(isGroupOnlyEdit({ ...initial, group: 'B' }, initial)).toBe(true)
  expect(isGroupOnlyEdit({ ...initial, group: 'B', userdata: 'changed' }, initial)).toBe(false)
  expect(isGroupOnlyEdit({ ...initial, group: 'B', newSetting: 'new' }, initial)).toBe(false)
})
it.each([['', { cleargroup: true }], ['B', { groupid: 'b' }], ['New', { group: 'New' }]])(
  'submits a metadata-only request for group %s', async (value, expected) => {
    postAPI.mockClear()
    postAPI.mockResolvedValue({})
    const initial = { displayname: 'vm', ostypeid: 'os', haenable: true, group: 'A', userdata: 'data' }
    const ctx = {
      formRef: { value: { validate: () => Promise.resolve() } },
      form: { ...initial, group: value },
      initialForm: initial,
      resource,
      groups: { opts: groups },
      groupLookupReady: true,
      apiParams: { groupid: {}, cleargroup: {} },
      $toBase64AndURIEncoded: jest.fn(),
      $t: text => text,
      $message: { success: jest.fn(), error: jest.fn() },
      $emit: jest.fn(),
      onCloseAction: jest.fn(),
      $notifyError: jest.fn()
    }
    await EditVM.methods.handleSubmit.call(ctx)
    await Promise.resolve()
    expect(postAPI).toHaveBeenCalledWith('updateVirtualMachine', { id: 'vm', ...expected })
  }
)
it('retains form values and submits nothing for a rejected whitespace name', async () => {
  postAPI.mockClear()
  const ctx = {
    formRef: { value: { validate: () => Promise.resolve() } },
    form: { group: '   ' },
    initialForm: {},
    groupLookupReady: true,
    resource,
    groups: { opts: groups },
    apiParams: { groupid: {}, cleargroup: {} },
    $t: text => text,
    $message: { error: jest.fn() }
  }
  await EditVM.methods.handleSubmit.call(ctx)
  await Promise.resolve()
  expect(postAPI).not.toHaveBeenCalled()
  expect(ctx.form.group).toBe('   ')
  expect(ctx.$message.error).toHaveBeenCalled()
})

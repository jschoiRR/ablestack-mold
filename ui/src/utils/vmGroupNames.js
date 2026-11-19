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

export const VM_GROUP_NAME_MAX = 255
export const VM_GROUP_NAME_INPUT_MAX = 1024
// Boundary tabs and line breaks are intentionally included, matching the server policy.
// eslint-disable-next-line no-control-regex
const boundaryWhitespace = /^[\u0009-\u000d\u0020\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+|[\u0009-\u000d\u0020\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+$/g

export function normalizeVmGroupName (value, previous) {
  if (previous !== undefined && value === previous) return value
  const raw = value == null ? '' : String(value)
  if (raw.length > VM_GROUP_NAME_INPUT_MAX) throw new Error('message.vm.group.name.input.length')
  const name = raw.replace(boundaryWhitespace, '')
  if (!name) throw new Error('message.vm.group.blank.name')
  // eslint-disable-next-line no-control-regex
  if (/[\u0000-\u001f\u007f]/.test(name)) throw new Error('message.vm.group.name.characters')
  if (name.length > VM_GROUP_NAME_MAX) throw new Error('message.vm.group.name.length')
  return name
}

export function isVmGroupNameAction (api) {
  return ['createInstanceGroup', 'updateInstanceGroup'].includes(api)
}

export function isVmGroupAction (api) {
  return isVmGroupNameAction(api) || api === 'deleteInstanceGroup'
}

export function vmGroupErrorKey (error) {
  const data = error?.response?.data || {}
  const response = Object.values(data).find(item => item && typeof item === 'object' && item.errortext) || data
  const text = String(response.errortext || error?.message || '')
  const code = Number(response.errorcode)
  if (text.includes('VM_GROUP_NAME_DUPLICATE') || (code === 431 && /group.*already exists/i.test(text))) return 'message.vm.group.name.duplicate'
  if (text.includes('VM_GROUP_NAME_REQUIRED') || text === 'message.vm.group.blank.name') return 'message.vm.group.blank.name'
  if (text.includes('VM_GROUP_NAME_INVALID_CHARACTERS') || /illegal ASCII non-printable/i.test(text)) return 'message.vm.group.name.characters'
  if (text.includes('VM_GROUP_NAME_INPUT_TOO_LONG') || text === 'message.vm.group.name.input.length' || /max allowed length 1024/i.test(text)) return 'message.vm.group.name.input.length'
  if (text.includes('VM_GROUP_NAME_TOO_LONG') || text === 'message.vm.group.name.length') return 'message.vm.group.name.length'
  if ([401, 403, 432, 531].includes(code) || [401, 403].includes(error?.response?.status)) return 'message.vm.group.operation.forbidden'
  if (text.includes('VM_GROUP_NOT_FOUND') || text === 'message.vm.group.operation.missing' || /unable to (find|locate).*instance group|unable to find.*vm group/i.test(text)) return 'message.vm.group.operation.missing'
  return 'message.vm.group.operation.failed'
}

export function vmGroupDeleteTargets (action, selected = []) {
  return Object.freeze((action.invokedAsGroupAction ? selected : [action.resource]).filter(Boolean)
    .map(group => Object.freeze({ id: group.id, name: group.name, account: group.account, domain: group.domain, project: group.project })))
}

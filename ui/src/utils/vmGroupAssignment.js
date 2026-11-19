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

import { normalizeVmGroupName } from '@/utils/vmGroupNames'

export function buildVmGroupUpdate (resource, value, groups = [], supported = { groupId: true, clearGroup: true }) {
  const name = value == null ? '' : String(value)
  const previous = resource.group == null ? '' : String(resource.group)
  if (name === previous) return {}
  if (name === '') {
    if (!previous && !resource.groupid) return {}
    if (!supported.clearGroup) throw new Error('message.vm.group.clear.unsupported')
    return { cleargroup: true }
  }
  const existing = groups.find(group => group.value === name && group.groupId)
  if (existing && supported.groupId) return { groupid: existing.groupId }
  const normalized = normalizeVmGroupName(name, existing?.value)
  const canonical = groups.find(group => group.value === normalized && group.groupId)
  return canonical && supported.groupId ? { groupid: canonical.groupId } : { group: normalized }
}

export function isGroupOnlyEdit (values, initialValues) {
  return Object.keys(values).every(key => key === 'group' ||
    JSON.stringify(values[key]) === JSON.stringify(initialValues[key]))
}

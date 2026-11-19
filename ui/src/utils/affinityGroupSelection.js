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

import { getAPI } from '@/api'
import { affinitySessionScope, affinityApiError } from '@/utils/affinityGroupOperations'

export const affinitySelectionIds = ids => [...new Set((Array.isArray(ids) ? ids : []).filter(Boolean))]

export function affinitySelectionGroups (ids, knownGroups = []) {
  const known = new Map(knownGroups.map(group => [group.id, group]))
  return affinitySelectionIds(ids).map(id => known.get(id) || { id, name: id })
}

export function affinitySelectionScope (store, owner = {}, zoneId = '') {
  return JSON.stringify([affinitySessionScope(store), owner.account || '', owner.domainid || '', owner.projectid || '', zoneId])
}

// The four creation wizards use the same selector. Keep this lookup specific to
// affinity groups so other wizard option loaders keep their existing contracts.
export async function fetchAffinityGroupOptions (vm, param) {
  const token = Symbol('affinity lookup')
  vm.affinityLookupToken = token
  const query = { ...param.options }
  const scope = affinitySelectionScope(vm.$store, query, vm.zoneId)
  if (vm.affinityLookupScope && scope !== vm.affinityLookupScope) {
    const previous = JSON.parse(vm.affinityLookupScope)
    const next = JSON.parse(scope)
    const initialZone = !previous[4] && next[4] && JSON.stringify(previous.slice(0, 4)) === JSON.stringify(next.slice(0, 4))
    if (!initialZone) {
      vm.options.affinityGroups = []
      vm.rowCount.affinityGroups = 0
      vm.form.affinitygroupids = []
      vm.affinityGroups = []
    }
  }
  vm.affinityLookupScope = scope
  const current = () => vm.affinityLookupToken === token && !vm.$?.isUnmounted && scope === affinitySelectionScope(vm.$store, vm.params.affinityGroups.options, vm.zoneId)
  vm.loading.affinityGroups = true
  vm.affinityGroupsError = ''
  param.loading = true
  try {
    const response = await getAPI('listAffinityGroups', query, { preserveOnFailure: true, timeout: 15000 })
    if (!current()) return
    const result = response.listaffinitygroupsresponse
    vm.options.affinityGroups = result.affinitygroup || []
    vm.rowCount.affinityGroups = result.count || 0
    param.opts = vm.options.affinityGroups
    return vm.options.affinityGroups
  } catch (error) {
    if (current()) vm.affinityGroupsError = affinityApiError(error) || 'message.affinity.lookup.failed'
  } finally {
    if (current()) {
      vm.loading.affinityGroups = false
      param.loading = false
    }
  }
}

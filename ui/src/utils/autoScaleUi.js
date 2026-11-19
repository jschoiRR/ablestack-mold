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

import { autoScaleOwnerParams } from '@/utils/autoScaleEditing'

// Preserve a user's saved visibility choices when former single-value columns
// are consolidated. A selection consisting only of removed columns stays useful.
export function autoScaleVisibleColumns (columns) {
  const oldDefault = ['name', 'state', 'minmembers', 'maxmembers', 'availablevirtualmachinecount', 'publicip', 'publicport', 'privateport', 'associatednetworkname', 'account'].every(key => columns.includes(key))
  if (oldDefault && columns.every(key => ['name', 'state', 'minmembers', 'maxmembers', 'availablevirtualmachinecount', 'publicip', 'publicport', 'privateport', 'associatednetworkname', 'account', 'project'].includes(key))) return ['name', 'state', 'availablevirtualmachinecount', 'associatednetworkname', 'lastcheckedat', 'account', ...(columns.includes('project') ? ['project'] : [])]
  const available = ['name', 'state', 'availablevirtualmachinecount', 'associatednetworkname', 'lastcheckedat', 'account', 'project', 'domain', 'infrastructurenetworkname', 'lbprovider', 'interval', 'created', 'zonename']
  return [...new Set(columns.map(key => ['minmembers', 'maxmembers'].includes(key) ? 'availablevirtualmachinecount' : ['publicip', 'publicport', 'privateport'].includes(key) ? 'associatednetworkname' : key).filter(key => available.includes(key)))]
}

// Exact-ID template listing evaluates the caller's personal account, even for
// project-shared images. Revalidate against fresh scoped, ready image lists in
// project mode; profile creation still performs server-side owner access checks.
export async function autoScaleTemplateById (request, params) {
  if (params.projectid) {
    const { id, ...scope } = params
    for (const templatefilter of ['executable', 'sharedexecutable']) {
      for (let page = 1; page <= 50; page++) {
        const json = await request('listTemplates', { ...scope, templatefilter, page, pagesize: 100 })
        const result = json.listtemplatesresponse || {}
        const images = result.template || []
        const image = images.find(item => item.id === id)
        if (image) return { listtemplatesresponse: { count: 1, template: [image] } }
        if (!images.length || page * 100 >= (result.count ?? images.length)) break
      }
    }
    return { listtemplatesresponse: { count: 0, template: [] } }
  }
  const response = await request('listTemplates', { ...params, templatefilter: 'executable' })
  if (response.listtemplatesresponse?.template?.some(item => item.id === params.id)) return response
  return request('listTemplates', { ...params, templatefilter: 'sharedexecutable' })
}

// Read Zone names from the group's authorized network only when the optional
// column is visible. Deduplicate each network/owner query within a page refresh.
export async function autoScaleInfrastructureRows (request, rows, allowed = true) {
  const lookups = new Map()
  return Promise.all(rows.map(async row => {
    if (!allowed) return { ...row, infrastructurestatus: 'DENIED' }
    if (!row.associatednetworkid) return { ...row }
    const params = { ...autoScaleOwnerParams(row), listall: true, id: row.associatednetworkid }
    const key = JSON.stringify(params)
    if (!lookups.has(key)) lookups.set(key, request('listNetworks', params).then(json => ({ network: json.listnetworksresponse?.network?.find(network => network.id === row.associatednetworkid) })).catch(() => ({ failed: true })))
    const result = await lookups.get(key)
    return { ...row, zonename: result.network?.zonename, zoneid: result.network?.zoneid, infrastructurestatus: result.failed ? 'FAILED' : result.network ? 'CURRENT' : 'UNAVAILABLE' }
  }))
}

// Resource/security identity is stable across tab switches and status timestamps.
export function autoScaleResourceScope (vm) {
  const getters = vm.$store?.getters || {}
  return JSON.stringify([vm.resource?.id, vm.resource?.vmprofileid, vm.resource?.lbruleid, getters.userInfo?.id, getters.userInfo?.roleid, getters.userInfo?.roletype, getters.project?.id])
}

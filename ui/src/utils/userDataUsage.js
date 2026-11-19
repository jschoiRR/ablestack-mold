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

// Association is the persisted UUID, never equality of copied content or guest execution.
export const userDataUsageKinds = {
  vms: { api: 'listVirtualMachines', body: 'listvirtualmachinesresponse', items: 'virtualmachine', path: '/vm' },
  templates: { api: 'listTemplates', body: 'listtemplatesresponse', items: 'template', path: '/template' },
  isos: { api: 'listIsos', body: 'listisosresponse', items: 'iso', path: '/iso' }
}
export function userDataUsageParams (source, kind, project, role, options = {}) {
  if (!/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(source?.id || '')) throw new Error('A library UUID is required')
  const projectid = source.projectid || project?.id
  const filter = role === 'User' ? 'executable' : 'all'
  return {
    userdataid: source.id,
    listall: true,
    ...(projectid ? { projectid } : { ignoreproject: true }),
    ...(kind === 'vms' ? {} : { [kind === 'templates' ? 'templatefilter' : 'isofilter']: filter, showunique: true }),
    page: 1,
    pagesize: 20,
    ...options
  }
}
export function userDataUsageRows (json, source, kind) {
  const config = userDataUsageKinds[kind]; const body = json?.[config.body]
  if (!body || body.errorcode || json.errorresponse) throw new Error('Invalid reference response')
  const rows = body[config.items] || []; const count = body.count ?? (rows.length ? undefined : 0)
  const uuid = /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i
  if (!Array.isArray(rows) || !Number.isSafeInteger(count) || count < rows.length ||
    new Set(rows.map(row => row.id)).size !== rows.length ||
    rows.some(row => !uuid.test(row.id || '') || row.userdataid?.toLowerCase() !== source.id?.toLowerCase())) throw new Error('Reference UUID or count mismatch')
  return { rows: rows.map(row => ({ ...row, name: row.displayname || row.displaytext || row.name || row.id })), count }
}
export function userDataUsageSupported (apis, kind) {
  return !!apis[userDataUsageKinds[kind].api]?.params?.some(param => param.name === 'userdataid')
}

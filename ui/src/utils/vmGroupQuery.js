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

// listInstanceGroups uses exact name and keyword(name/UUID) search, owner ACL,
// and id-ordered pagination. Sorting is deliberately limited to the visible page.
const apiKeys = ['id', 'name', 'keyword', 'account', 'domainid', 'projectid', 'isrecursive', 'listall', 'page', 'pagesize']
const uiKeys = ['q', 'tab', 'action', 'dataView', 'sortkey', 'sortorder', 'memberkeyword', 'memberpage', 'memberpagesize', 'membersort', 'memberorder']
const sortKeys = ['name', 'account', 'domain', 'project', 'virtualmachinecount']
const uuid = /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i

export function vmGroupQuery (query = {}) {
  const clean = {}
  for (const key of [...apiKeys, ...uiKeys]) {
    const value = query[key]
    if (typeof value !== 'string' && typeof value !== 'number' && typeof value !== 'boolean') continue
    if (String(value) === '') continue
    clean[key] = String(value)
  }
  for (const key of ['id', 'domainid', 'projectid']) {
    if (clean[key] && !uuid.test(clean[key]) && !(key === 'projectid' && clean[key] === '-1')) delete clean[key]
  }
  for (const key of ['page', 'pagesize', 'memberpage', 'memberpagesize']) {
    if (clean[key] && (!/^\d+$/.test(clean[key]) || !Number.isSafeInteger(Number(clean[key])) || Number(clean[key]) < 1)) delete clean[key]
  }
  for (const key of ['isrecursive', 'listall', 'dataView']) {
    if (clean[key] && !['true', 'false'].includes(clean[key])) delete clean[key]
  }
  if (clean.keyword || clean.q) clean.q = clean.keyword = clean.keyword || clean.q
  if (!sortKeys.includes(clean.sortkey) || !['asc', 'desc'].includes(clean.sortorder)) {
    delete clean.sortkey
    delete clean.sortorder
  }
  if (!['name', 'state', 'account', 'domain', 'project', 'group'].includes(clean.membersort) || !['asc', 'desc'].includes(clean.memberorder)) {
    delete clean.membersort
    delete clean.memberorder
  }
  return clean
}

export function vmGroupQueryChanged (query, clean) {
  return JSON.stringify(Object.keys(query).sort().map(key => [key, query[key]])) !==
    JSON.stringify(Object.keys(clean).sort().map(key => [key, clean[key]]))
}

export function vmGroupQueryParams (params) {
  return Object.fromEntries(apiKeys.filter(key => params[key] !== undefined).map(key => [key, params[key]]))
}

export function vmGroupQueryScope (route, getters, token) {
  const query = vmGroupQueryParams(vmGroupQuery(route.query))
  return JSON.stringify([route.path, query, route.query?.dataView === 'true', getters.userInfo?.id, token,
    getters.project?.id, getters.listAllProjects])
}

export function vmGroupQueryRows (json) {
  const response = json?.listinstancegroupsresponse
  if (response?.errorcode || json?.errorresponse) throw json
  if (!response || (response.instancegroup !== undefined && !Array.isArray(response.instancegroup))) throw new Error('Invalid group response')
  const rows = response.instancegroup || []
  if (rows.some(row => !row || !uuid.test(row.id) || typeof row.name !== 'string') || new Set(rows.map(row => row.id)).size !== rows.length) throw new Error('Invalid group rows')
  if (response.count !== undefined && (typeof response.count !== 'number' && (typeof response.count !== 'string' || !/^\d+$/.test(response.count)))) throw new Error('Invalid group count')
  const count = response.count === undefined && rows.length === 0 ? 0 : Number(response.count)
  if (!Number.isSafeInteger(count) || count < rows.length || count < 0) throw new Error('Invalid group count')
  return { rows, count }
}

export function vmGroupQueryMessage (status, retained) {
  return 'message.vm.group.query.' + (status === 'error' && retained ? 'stale' : status)
}

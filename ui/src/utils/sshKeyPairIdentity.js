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

const uuid = /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i
const value = text => typeof text === 'string' && text.length > 0
const sameId = (left, right) => value(left) && value(right) && left.toLowerCase() === right.toLowerCase()

export function isSshKeyPairDetailRoute (route) {
  return /^\/ssh(?:\/[^/]+)?$/.test(route?.path || '') && Boolean(value(route?.params?.id) || route?.query?.dataView)
}

function owner (record) {
  const query = {}
  if (value(record?.projectid)) query.projectid = record.projectid
  else if (value(record?.account)) query.account = record.account
  if (value(record?.domainid)) query.domainid = record.domainid
  return query
}

export function sshKeyPairNameLink (name, record, tab) {
  const query = { ...owner(record), keypairname: name }
  if (tab) query.tab = tab
  return { path: '/ssh/' + encodeURIComponent(name), query }
}

export function sshKeyPairLink (record, tab) {
  if (!uuid.test(record?.id || '')) return sshKeyPairNameLink(record.name, record, tab)
  const query = owner(record)
  if (tab) query.tab = tab
  return { path: '/ssh/' + record.id, query }
}

export function sshKeyPairDetailTarget (route, project = {}, listAllProjects = false) {
  const query = route.query || {}
  const routeId = route.params?.id
  const named = query.keypairname !== undefined
  if (!value(routeId) || (named && query.keypairname !== routeId)) return { invalid: true }
  for (const key of ['account', 'domainid', 'projectid']) {
    if (query[key] !== undefined && typeof query[key] !== 'string') return { invalid: true }
  }
  const target = named || !uuid.test(routeId) ? { name: routeId } : { id: routeId }
  if (value(query.account)) target.account = query.account
  if (value(query.domainid)) target.domainid = query.domainid
  if (value(query.projectid)) target.projectid = query.projectid
  else if (!target.account && !target.domainid) {
    if (value(project.id)) target.projectid = project.id
    else if (listAllProjects) target.projectid = '-1'
  }
  return target
}

export function sshKeyPairDetailScope (route, getters, token) {
  return JSON.stringify([route.path, sshKeyPairDetailTarget(route, getters.project, getters.listAllProjects),
    route.query?.fingerprint, route.query?.keyword, getters.project?.id, getters.listAllProjects, getters.userInfo?.id, token])
}

export function sshKeyPairDetailParams (params, target) {
  const result = { ...params }
  for (const key of ['id', 'name', 'account', 'domainid', 'projectid', 'keypairname', 'tab', 'action']) delete result[key]
  return { ...result, ...target, listall: true, page: 1, pagesize: 2, ignoreproject: !target.projectid }
}

export function sshKeyPairMatchesTarget (record, target) {
  if (!target || target.invalid || !uuid.test(record?.id || '') || !value(record?.name)) return false
  if (target.id && !sameId(record.id, target.id)) return false
  if (target.name !== undefined && record.name !== target.name) return false
  if (target.account && (record.account !== target.account || (record.projectid && !target.projectid))) return false
  if (target.domainid && !sameId(record.domainid, target.domainid)) return false
  if (target.projectid && target.projectid !== '-1' && !sameId(record.projectid, target.projectid)) return false
  return true
}

export function sshKeyPairDetailFailure (error) {
  const response = error?.response
  const body = response?.data || error || {}
  const code = body.errorresponse?.errorcode || body.listsshkeypairsresponse?.errorcode
  if ([401, 403, 432].includes(response?.status) || [401, 403, 432, 531].includes(code)) return 'forbidden'
  if (response?.status === 431) return 'missing'
  return 'error'
}

export function sshKeyPairDetailResult (json, target) {
  const body = json?.listsshkeypairsresponse
  if (!body || json.errorresponse || body.errorcode) return { status: sshKeyPairDetailFailure(json) }
  const rows = body.sshkeypair || []
  const count = body.count ?? (rows.length ? undefined : 0)
  if (!Array.isArray(rows) || !Number.isSafeInteger(count) || count < rows.length) return { status: 'error' }
  if (count === 0 && rows.length === 0) return { status: 'missing' }
  if (count !== 1 || rows.length !== 1) return { status: target.name !== undefined ? 'ambiguous' : 'mismatch' }
  if (!sshKeyPairMatchesTarget(rows[0], target)) return { status: 'mismatch' }
  return { status: 'ready', rows }
}

export function sshKeyPairVmQuery (record) {
  return { keypair: record.name, ...owner(record) }
}

export function sshKeyPairVmParams (params, query) {
  if (!value(query?.keypair)) return params
  if (value(query.projectid)) return { ...params, projectid: query.projectid }
  if (!value(query.account) || !value(query.domainid)) return params
  const result = { ...params, ignoreproject: true }
  delete result.projectid
  return result
}

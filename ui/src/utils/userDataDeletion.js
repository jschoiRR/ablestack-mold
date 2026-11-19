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
const fields = ['id', 'name', 'account', 'domainid', 'domain', 'projectid', 'project']
export function userDataDeleteTargets (action, selection = []) {
  const rows = action.invokedAsGroupAction ? selection : [action.resource]
  return Object.freeze(rows.filter(Boolean).map(row => Object.freeze(Object.fromEntries(fields.map(key => [key, row[key]])))))
}
export function userDataDeleteParams (target) {
  if (!uuid.test(target?.id || '') || !target.name || !uuid.test(target.domainid || '') ||
      (target.projectid ? !uuid.test(target.projectid) : !target.account)) return null
  return { id: target.id, ...(target.projectid ? { projectid: target.projectid } : { account: target.account, domainid: target.domainid, ignoreproject: true }) }
}
export function userDataDeleteLookup (target, byName = false) {
  const params = userDataDeleteParams(target)
  if (!params) return null
  return { ...params, ...(byName ? { id: undefined, name: target.name } : {}), listall: true, page: 1, pagesize: byName ? 500 : 2 }
}
export function userDataDeleteIdentity (json, target, byName = false) {
  const body = json?.listuserdataresponse
  if (!body || body.errorcode || (body.userdata !== undefined && !Array.isArray(body.userdata))) return 'error'
  const rows = body.userdata || []
  const count = body.count === undefined && !rows.length ? 0 : Number(body.count)
  if (!Number.isInteger(count) || count !== rows.length) return 'error'
  const matches = rows.filter(row => row.id === target.id)
  if (!matches.length) return rows.length && !byName ? 'mismatch' : 'missing'
  if (matches.length !== 1) return 'mismatch'
  const row = matches[0]
  return row.name === target.name && row.domainid === target.domainid &&
    (target.projectid ? row.projectid === target.projectid : !row.projectid && row.account === target.account) ? 'ready' : 'mismatch'
}
export function userDataDeleteError (error) {
  const body = Object.values(error?.response?.data || {}).find(value => value?.errortext || value?.errorcode)
  return { code: Number(body?.errorcode || error?.response?.status), reason: body?.errortext || error?.message || '' }
}

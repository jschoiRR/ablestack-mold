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

import { sshKeyPairDetailResult } from '@/utils/sshKeyPairIdentity'

const uuid = /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i
const fields = ['id', 'name', 'fingerprint', 'account', 'domainid', 'domain', 'projectid', 'project']
export function sshKeyPairDeleteTargets (action, selection = []) {
  const rows = action.invokedAsGroupAction ? selection : [action.resource]
  return Object.freeze(rows.filter(Boolean).map(row => Object.freeze(Object.fromEntries(fields.map(key => [key, row[key]])))))
}

export function sshKeyPairDeleteParams (target) {
  if (!uuid.test(target?.id || '') || typeof target.name !== 'string' || !target.name || !target.fingerprint ||
      !uuid.test(target.domainid || '') || (!target.projectid && !target.account) || (target.projectid && !uuid.test(target.projectid))) return null
  return { name: target.name, domainid: target.domainid, ...(target.projectid ? { projectid: target.projectid } : { account: target.account, ignoreproject: true }) }
}

export function sshKeyPairDeleteLookup (target) {
  const params = sshKeyPairDeleteParams(target)
  return params && { ...params, id: target.id, listall: true, page: 1, pagesize: 2 }
}

export function sshKeyPairDeleteIdentity (json, target) {
  const params = sshKeyPairDeleteParams(target)
  if (!params) return 'mismatch'
  const result = sshKeyPairDetailResult(json, { ...params, id: target.id })
  if (result.status !== 'ready') return result.status
  return result.rows[0].fingerprint === target.fingerprint ? 'ready' : 'mismatch'
}

export function sshKeyPairDeleteError (error) {
  const code = error?.response?.status
  return {
    state: [400, 401, 403, 404, 405, 409, 431, 432].includes(code) ? 'failed' : 'unknown',
    reason: error?.response?.data?.errorresponse?.errortext || error?.message || ''
  }
}

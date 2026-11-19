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

import { sshKeyPairVmQuery, sshKeyPairVmParams, sshKeyPairDetailFailure } from '@/utils/sshKeyPairIdentity'

export function sshKeyPairUsageParams (record, options = {}) {
  const query = sshKeyPairVmQuery(record)
  return sshKeyPairVmParams({ ...query, listall: true, page: 1, pagesize: 1, details: 'min', ...options }, query)
}

export function sshKeyPairUsageResult (json) {
  const body = json?.listvirtualmachinesresponse
  if (!body || json.errorresponse || body.errorcode) return { state: sshKeyPairDetailFailure(json) === 'forbidden' ? 'forbidden' : 'error' }
  const rows = body.virtualmachine || []
  const count = body.count ?? (rows.length === 0 ? 0 : undefined)
  if (!Array.isArray(rows) || !Number.isSafeInteger(count) || count < rows.length) return { state: 'error' }
  return { state: 'ready', count }
}

// ACL-scoped VM association from Cloud, not guest authorized_keys inspection.
export function sshKeyPairUsageRows (json, record) {
  const result = sshKeyPairUsageResult(json)
  if (result.state !== 'ready') return result
  const rows = json.listvirtualmachinesresponse.virtualmachine || []
  const uuid = /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i
  const same = (a, b) => typeof a === 'string' && typeof b === 'string' && a.toLowerCase() === b.toLowerCase()
  if (rows.some(vm => !uuid.test(vm.id || '') || typeof vm.state !== 'string' || !vm.state || !same(vm.domainid, record.domainid) ||
    (record.projectid ? !same(vm.projectid, record.projectid) : vm.projectid || vm.account !== record.account)) || new Set(rows.map(vm => vm.id)).size !== rows.length) return { state: 'error' }
  return { ...result, rows: rows.map(vm => ({ ...vm, name: vm.displayname || vm.name || vm.id })) }
}

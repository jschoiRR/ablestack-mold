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

import { userDataContent } from '@/utils/userDataContent'
import { userDataDeleteLookup, userDataDeleteIdentity, userDataDeleteError } from '@/utils/userDataDeletion'

const uuid = /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i
export const vmUserDataSupported = apis => Array.isArray(apis?.getVirtualMachineUserData?.params) && apis.getVirtualMachineUserData.params.some(p => p.name === 'virtualmachineid')
const fail = state => { throw Object.assign(new Error(state), { contentState: state }) }
const revision = vm => JSON.stringify(['id', 'account', 'domainid', 'projectid', 'userdataid', 'userdatapolicy', 'userdatadetails', 'userdata'].map(key => vm[key] ?? ''))

// All queries stay tied to the selected VM. The backend remains authoritative
// for access. Comparison is optional and never grants access to a library.
export async function readVmUserData (get, source, apis, current) {
  if (!uuid.test(source.id || '')) fail('mismatch')
  const options = { preserveOnFailure: true, timeout: 20000 }
  const listApi = ['listVirtualMachines', 'listVirtualMachinesMetrics'].find(api => apis?.[api])
  let metadataEnabled = !!listApi
  const metadata = async () => {
    if (!metadataEnabled) return null
    const json = await get(listApi, { id: source.id, listall: true, details: 'all', userdata: true, ...(source.projectid ? { projectid: source.projectid } : {}) }, options)
    const body = json?.[listApi.toLowerCase() + 'response']
    if (!body || body.errorcode || (body.virtualmachine !== undefined && !Array.isArray(body.virtualmachine))) fail('invalid')
    const rows = body.virtualmachine || []
    if (!rows.length) fail('missing')
    if (rows.length !== 1 || rows[0].id !== source.id || ['account', 'domainid', 'projectid'].some(key => (source[key] || '') !== (rows[0][key] || ''))) fail('mismatch')
    return rows[0]
  }
  let before
  try { before = await metadata() } catch (error) {
    if (![401, 403, 432, 531].includes(userDataDeleteError(error).code)) throw error
    metadataEnabled = false; before = null
  }
  if (!current()) return
  const json = await get('getVirtualMachineUserData', { virtualmachineid: source.id }, options)
  if (!current()) return
  const body = json?.getvirtualmachineuserdataresponse?.virtualmachineuserdata
  if (!body || body.virtualmachineid !== source.id) fail('mismatch')
  let content
  try { content = userDataContent(body.userdata) } catch (_) { fail('invalid') }
  const after = await metadata()
  if (!current()) return
  let listedBody = ''
  try { listedBody = decodeURIComponent(after?.userdata || '') } catch (_) { fail('invalid') }
  if (before && (revision(before) !== revision(after) || listedBody !== content.encoded)) fail('mismatch')
  const vm = after || source
  let comparison = vm.userdataid ? 'unavailable' : (after ? 'unlinked' : 'unavailable')
  if (after?.userdataid && apis?.listUserData) {
    try {
      const target = { id: after.userdataid, name: after.userdataname, account: after.account, domainid: after.domainid, projectid: after.projectid }
      const lookup = userDataDeleteLookup(target)
      if (lookup) {
        const library = await get('listUserData', lookup, options)
        if (!current()) return
        if (userDataDeleteIdentity(library, target) === 'ready') {
          const original = userDataContent(library.listuserdataresponse.userdata[0].userdata)
          comparison = original.encoded === content.encoded ? 'same' : 'different'
        }
      }
    } catch (_) { comparison = 'unavailable' }
  }
  if (!current()) return
  // A library read can be slow; recheck VM revision before publishing its body.
  if (after) {
    const final = await metadata()
    if (!current()) return
    if (revision(after) !== revision(final)) fail('mismatch')
  }
  return { content, vm, comparison }
}

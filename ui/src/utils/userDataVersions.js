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

import { userDataDeleteParams, userDataDeleteLookup, userDataDeleteIdentity } from './userDataDeletion'
import { userDataContent } from './userDataContent'

export const versionPrefix = 'MOLD_USERDATA_VERSION/1\n'
export const cloneApis = ['listUserData', 'registerUserData', 'listAnnotations', 'addAnnotation']
const options = { preserveOnFailure: true, timeout: 20000 }
const uuid = /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i

export function cloneOwner (source) {
  const params = userDataDeleteParams(source)
  if (!params) throw new Error('message.userdata.content.mismatch')
  const { id, ...owner } = params
  return owner
}

export async function readCloneSource (get, source) {
  const params = userDataDeleteLookup(source)
  if (!params) throw new Error('message.userdata.content.mismatch')
  const json = await get('listUserData', params, options)
  const state = userDataDeleteIdentity(json, source)
  if (state !== 'ready') throw new Error('message.userdata.content.' + state)
  const row = json.listuserdataresponse.userdata[0]
  userDataContent(row.userdata) // Do not turn a corrupt or binary source into replacement text.
  return Object.freeze({ ...row })
}

// A lost POST response must be reconciled before sending another registration.
// Exact name, owner, bytes and variable declarations must all agree.
export async function findCloneAttempt (get, source, params) {
  const json = await get('listUserData', { ...cloneOwner(source), name: params.name, listall: true, page: 1, pagesize: 500 }, options)
  const body = json?.listuserdataresponse
  const rows = body?.userdata || []
  if (!body || body.errorcode || !Array.isArray(rows) || Number(body.count || 0) !== rows.length) throw new Error('message.userdata.clone.uncertain')
  const exact = rows.filter(row => row.name === params.name)
  if (!exact.length) return null
  if (exact.length !== 1) throw new Error('message.userdata.clone.uncertain')
  const row = exact[0]
  const scope = userDataDeleteIdentity({ listuserdataresponse: { count: 1, userdata: [row] } }, { ...source, id: row.id, name: params.name })
  if (!uuid.test(row.id || '') || scope !== 'ready' || row.userdata !== params.userdata || (row.params || '') !== (params.params || '')) throw new Error('message.userdata.clone.name.conflict')
  return Object.freeze({ ...row })
}

export function cloneDifference (source, encoded, params) {
  const before = userDataContent(source.userdata); const after = userDataContent(encoded)
  let first = 0
  while (first < before.bytes.length && first < after.bytes.length && before.bytes[first] === after.bytes[first]) first++
  return { changed: before.encoded !== after.encoded, before: before.bytes.length, after: after.bytes.length, first: before.encoded === after.encoded ? '-' : first + 1, paramsChanged: (source.params || '') !== (params || '') }
}

export function cloneRecord (source, target) {
  const difference = cloneDifference(source, target.userdata, target.params)
  // addAnnotation has a 255-character API limit. Immutable UUIDs and byte
  // counts fit for every valid name/variable length; never truncate provenance.
  return versionPrefix + JSON.stringify({ sourceid: source.id, targetid: target.id, before: difference.before, after: difference.after })
}

export function parseCloneRecord (text, targetid) {
  if (typeof text !== 'string' || !text.startsWith(versionPrefix)) return null
  try {
    const value = JSON.parse(text.slice(versionPrefix.length))
    if (value.targetid !== targetid || !uuid.test(value.sourceid || '') || value.sourceid === targetid ||
      !['before', 'after'].every(key => Number.isInteger(value[key]) && value[key] > 0 && value[key] <= 786432)) return null
    return value
  } catch (error) { return null }
}

export async function readCloneNotes (get, id, projectid) {
  const result = []; let total
  for (let page = 1; page <= 100; page++) {
    const json = await get('listAnnotations', { entityid: id, entitytype: 'USER_DATA', annotationfilter: 'all', page, pagesize: 500, ...(projectid ? { projectid } : { ignoreproject: true }) }, options)
    const body = json?.listannotationsresponse; const rows = body?.annotation || []
    const count = Number(body?.count || 0)
    if (!body || body.errorcode || !Array.isArray(rows) || !Number.isInteger(count) || count < 0 || (total !== undefined && count !== total)) throw new Error('message.userdata.clone.history.error')
    total = count; result.push(...rows)
    if (result.length === total) return result
    if (!rows.length || result.length > total) break
  }
  throw new Error('message.userdata.clone.history.error')
}

export async function saveCloneNote (get, post, target, annotation, current = () => true) {
  const exists = async () => (await readCloneNotes(get, target.id, target.projectid)).some(row => row.annotation === annotation)
  if (await exists()) return
  if (!current()) return
  try {
    const json = await post('addAnnotation', { entityid: target.id, entitytype: 'USER_DATA', annotation, adminsonly: false, ...(target.projectid ? { projectid: target.projectid } : { ignoreproject: true }) }, options)
    if (!json?.addannotationresponse?.annotation?.id) throw new Error('message.userdata.clone.history.error')
  } catch (error) {
    if (await exists()) return
    throw error
  }
}

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

import { cniDeleteIdentity, cniDeleteLookup } from './cniDeletion'
import { cloneOwner, cloneDifference, readCloneNotes } from './userDataVersions'
import { userDataContent } from './userDataContent'

export const cniCloneApis = ['listCniConfiguration', 'registerCniConfiguration', 'listAnnotations', 'addAnnotation']
export const cniVersionPrefix = 'MOLD_CNI_CLONE/1\n'
const options = { preserveOnFailure: true, timeout: 20000 }
const uuid = /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i
const fail = (key) => {
  throw new Error('message.cni.clone.' + key)
}
export { cloneOwner, cloneDifference }

export async function readCniCloneSource (get, source) {
  const params = cniDeleteLookup(source)
  if (!params) fail('identity')
  const json = await get('listCniConfiguration', params, options)
  if (cniDeleteIdentity(json, source) !== 'ready') fail('identity')
  const row = json.listcniconfigurationresponse.cniconfig[0]
  const content = userDataContent(row.userdata)
  if (content.empty) fail('empty')
  return Object.freeze({ ...row })
}

export async function findCniCloneAttempt (get, source, params) {
  const json = await get(
    'listCniConfiguration',
    { ...cloneOwner(source), name: params.name, listall: true, page: 1, pagesize: 500 },
    options
  )
  const body = json?.listcniconfigurationresponse
  const rows = body?.cniconfig || []
  if (!body || body.errorcode || !Array.isArray(rows) || !Number.isSafeInteger(body.count ?? 0) || (body.count ?? 0) !== rows.length) { fail('uncertain') }
  const exact = rows.filter((row) => row.name === params.name)
  if (!exact.length) return null
  if (exact.length !== 1) fail('uncertain')
  const row = exact[0]
  const expected = { ...source, id: row.id, name: params.name }
  if (
    !uuid.test(row.id || '') ||
    row.id === source.id ||
    cniDeleteIdentity({ listcniconfigurationresponse: { count: 1, cniconfig: [row] } }, expected) !== 'ready' ||
    row.userdata !== params.cniconfig ||
    (row.params || '') !== (params.params || '')
  ) { fail('conflict') }
  return Object.freeze({ ...row })
}

function ownerScope (row) {
  cloneOwner(row)
  return { domainid: row.domainid, ...(row.projectid ? { projectid: row.projectid } : { account: row.account }) }
}
export function cniCloneRecord (source, target) {
  if (
    !uuid.test(source.id || '') ||
    !uuid.test(target.id || '') ||
    source.id === target.id ||
    JSON.stringify(ownerScope(source)) !== JSON.stringify(ownerScope(target))
  ) { fail('identity') }
  const difference = cloneDifference(source, target.userdata, target.params)
  return {
    version: 1,
    sourceid: source.id,
    targetid: target.id,
    owner: ownerScope(target),
    before: difference.before,
    after: difference.after
  }
}
function validRecord (record, target) {
  try {
    return (
      record.version === 1 &&
      uuid.test(record.sourceid || '') &&
      record.sourceid !== target.id &&
      record.targetid === target.id &&
      JSON.stringify(record.owner) === JSON.stringify(ownerScope(target)) &&
      ['before', 'after'].every((k) => Number.isSafeInteger(record[k]) && record[k] > 0 && record[k] <= 786432)
    )
  } catch (_) {
    return false
  }
}

// Annotation defaults to 255 characters. Store the complete, untruncated UTF-8
// provenance in bounded chunks; incomplete or conflicting sets are not history.
export function cniCloneChunks (record) {
  if (!uuid.test(record?.targetid || '')) fail('identity')
  const encoded = btoa(unescape(encodeURIComponent(JSON.stringify(record))))
  const parts = encoded.match(/.{1,160}/g) || []
  if (!parts.length || parts.length > 32) fail('identity')
  return parts.map((b, n) => cniVersionPrefix + JSON.stringify({ t: record.targetid, n, c: parts.length, b }))
}
export function parseCniCloneRecord (rows, target) {
  try {
    const parts = rows
      .filter((r) => typeof r.annotation === 'string' && r.annotation.startsWith(cniVersionPrefix))
      .map((r) => JSON.parse(r.annotation.slice(cniVersionPrefix.length)))
      .filter((r) => r.t === target.id)
    if (!parts.length) return null
    const count = parts[0].c
    const chunks = new Map()
    for (const p of parts) {
      if (
        !Number.isInteger(count) ||
        count < 1 ||
        count > 32 ||
        p.c !== count ||
        !Number.isInteger(p.n) ||
        p.n < 0 ||
        p.n >= count ||
        typeof p.b !== 'string' ||
        !/^[A-Za-z0-9+/=]{1,160}$/.test(p.b) ||
        (chunks.has(p.n) && chunks.get(p.n) !== p.b)
      ) { return null }
      chunks.set(p.n, p.b)
    }
    if (chunks.size !== count) return null
    const encoded = Array.from({ length: count }, (_, i) => chunks.get(i)).join('')
    const raw = atob(encoded)
    if (btoa(raw) !== encoded) return null
    const record = JSON.parse(decodeURIComponent(escape(raw)))
    return validRecord(record, target) ? record : null
  } catch (_) {
    return null
  }
}
export async function saveCniCloneRecord (get, post, target, record, current = () => true) {
  if (!validRecord(record, target)) fail('identity')
  const chunks = cniCloneChunks(record)
  const read = () => readCloneNotes(get, target.id, target.projectid)
  let rows = await read()
  const complete = () => JSON.stringify(parseCniCloneRecord(rows, target)) === JSON.stringify(record)
  if (complete()) return
  for (const annotation of chunks) {
    if (!current()) return
    if (rows.some((row) => row.annotation === annotation)) continue
    // Recheck target identity/type/ownership before writing advisory metadata.
    const live = await readCniCloneSource(get, target)
    if (live.userdata !== target.userdata || (live.params || '') !== (target.params || '')) fail('identity')
    if (!current()) return
    try {
      const json = await post(
        'addAnnotation',
        {
          entityid: target.id,
          entitytype: 'USER_DATA',
          annotation,
          adminsonly: false,
          ...(target.projectid ? { projectid: target.projectid } : { ignoreproject: true })
        },
        options
      )
      if (!json?.addannotationresponse?.annotation?.id) fail('history')
    } catch (error) {
      rows = await read()
      if (!rows.some((row) => row.annotation === annotation)) throw error
      continue
    }
    rows = await read()
  }
  if (current() && !complete()) fail('history')
}

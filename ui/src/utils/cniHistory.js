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

import { cniDeleteIdentity, cniDeleteError } from './cniDeletion'
import { cloneOwner, cniVersionPrefix, parseCniCloneRecord } from './cniVersions'
import { userDataContent } from './userDataContent'

// History paging/search does not change the parent CNI identity or API scope.
export function cniHistoryRouteKey (route) {
  const entries = Object.entries(route.query || {}).filter(([key]) => !['cnihistorypage', 'cnihistorysize', 'cnihistoryq'].includes(key)).sort(([a], [b]) => a.localeCompare(b))
  return JSON.stringify([route.path, entries])
}

export const cniHistoryApis = ['listCniConfiguration', 'listAnnotations']
export const cniHistoryLimits = Object.freeze({ pageSize: 50, configurations: 200, notePageSize: 100, notes: 500, depth: 16, workers: 4, diffLines: 500, diffCharacters: 65536 })
const uuid = /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i
const options = { preserveOnFailure: true, timeout: 20000 }
const fail = (state) => { throw Object.assign(new Error('message.cni.history.' + state), { historyState: state }) }
const own = (anchor, row) => typeof row?.name === 'string' && !!row.name && cniDeleteIdentity({ listcniconfigurationresponse: { count: 1, cniconfig: [row] } }, { ...anchor, id: row?.id, name: row?.name }) === 'ready'
const cancelled = (current) => { if (!current()) fail('cancelled') }
const content = (row) => { try { return userDataContent(row.userdata) } catch (_) { return null } }

export async function readHistoryCni (get, anchor, id = anchor.id) {
  if (!uuid.test(id || '')) fail('identity')
  const json = await get('listCniConfiguration', { ...cloneOwner(anchor), id, listall: true, page: 1, pagesize: 2 }, options)
  const body = json?.listcniconfigurationresponse
  const rows = body?.cniconfig ?? []
  const count = body?.count ?? (Array.isArray(rows) && rows.length === 0 ? 0 : undefined)
  if (!body || json.errorresponse || body.errorcode || !Array.isArray(rows) || !Number.isSafeInteger(count) || count !== rows.length || rows.length > 1) fail('incomplete')
  if (!rows.length) return null
  const row = rows[0]
  if (row.id !== id || !own(anchor, row) || (id === anchor.id && row.name !== anchor.name)) fail('identity')
  return Object.freeze({ ...row })
}

async function readNotes (get, row, current) {
  const result = []; const seen = new Set(); let total
  for (let page = 1; result.length < cniHistoryLimits.notes; page++) {
    cancelled(current)
    const json = await get('listAnnotations', { entityid: row.id, entitytype: 'USER_DATA', annotationfilter: 'all', page, pagesize: cniHistoryLimits.notePageSize, ...(row.projectid ? { projectid: row.projectid } : { ignoreproject: true }) }, options)
    cancelled(current)
    const body = json?.listannotationsresponse; const rows = body?.annotation ?? []
    const count = body?.count ?? (Array.isArray(rows) && rows.length === 0 ? 0 : undefined)
    if (!body || json.errorresponse || body.errorcode || !Array.isArray(rows) || !Number.isSafeInteger(count) || count < 0 || (total !== undefined && count !== total) || rows.length > cniHistoryLimits.notePageSize) fail('incomplete')
    total = count
    for (const note of rows) {
      if (!uuid.test(note?.id || '') || seen.has(note.id) || typeof note.annotation !== 'string' || (note.entityid && note.entityid !== row.id) || (note.entitytype && note.entitytype !== 'USER_DATA')) fail('incomplete')
      seen.add(note.id); result.push(note)
    }
    if (result.length === total) return result
    if (!rows.length || result.length > total) fail('incomplete')
  }
  fail('limited')
}

async function inspect (get, row, current) {
  const item = { id: row.id, row, state: 'none', record: null, available: true }
  try {
    const notes = await readNotes(get, row, current)
    if (!notes.some(n => n.annotation.startsWith(cniVersionPrefix))) return item
    item.record = parseCniCloneRecord(notes, row)
    const decoded = content(row)
    item.state = item.record && decoded && item.record.after === decoded.bytes.length ? 'valid' : 'invalid'
  } catch (error) {
    if (!current()) throw error
    item.state = error.historyState === 'limited' ? 'limited' : 'unreadable'
  }
  return item
}

export async function readCniHistory (get, anchor, current = () => true) {
  cancelled(current)
  const source = await readHistoryCni(get, anchor)
  cancelled(current)
  if (!source) fail('unavailable')
  const rows = []; const seen = new Set(); let total; let limited = false
  for (let page = 1; rows.length < cniHistoryLimits.configurations; page++) {
    cancelled(current)
    const json = await get('listCniConfiguration', { ...cloneOwner(source), listall: true, page, pagesize: cniHistoryLimits.pageSize }, options)
    cancelled(current)
    const body = json?.listcniconfigurationresponse; const batch = body?.cniconfig ?? []
    const count = body?.count ?? (Array.isArray(batch) && batch.length === 0 ? 0 : undefined)
    if (!body || json.errorresponse || body.errorcode || !Array.isArray(batch) || !Number.isSafeInteger(count) || count < 0 || (total !== undefined && count !== total) || batch.length > cniHistoryLimits.pageSize) fail('incomplete')
    total = count
    for (const row of batch) {
      if (!uuid.test(row?.id || '') || seen.has(row.id) || !own(source, row)) fail('identity')
      seen.add(row.id); rows.push(row)
    }
    if (rows.length === total) break
    if (!batch.length || rows.length > total) fail('incomplete')
    if (rows.length === cniHistoryLimits.configurations) limited = true
  }
  if (!seen.has(source.id)) {
    if (!limited) fail('incomplete')
    rows.push(source)
  } else {
    const index = rows.findIndex(row => row.id === source.id)
    if (rows[index].name !== source.name || rows[index].userdata !== source.userdata || rows[index].params !== source.params) fail('identity')
    rows[index] = source
  }
  const items = new Map(); let next = 0
  await Promise.all(Array.from({ length: Math.min(cniHistoryLimits.workers, rows.length) }, async () => {
    while (next < rows.length) {
      cancelled(current)
      const row = rows[next++]
      items.set(row.id, await inspect(get, row, current))
    }
  }))
  // Parents outside a bounded inventory are read only by the exact typed UUID.
  let id = source.id; const parentsSeen = new Set()
  for (let depth = 0; depth < cniHistoryLimits.depth; depth++) {
    cancelled(current)
    const item = items.get(id)
    if (!item?.record || item.state !== 'valid' || parentsSeen.has(id)) break
    parentsSeen.add(id); id = item.record.sourceid
    if (!items.has(id)) {
      try {
        const row = await readHistoryCni(get, source, id)
        cancelled(current)
        items.set(id, row ? await inspect(get, row, current) : { id, available: false, state: 'unavailable', row: null, record: null })
      } catch (error) {
        if (!current()) throw error
        const code = cniDeleteError(error).code
        items.set(id, { id, available: false, state: [401, 403, 404, 431, 432, 531].includes(code) ? 'unavailable' : 'unreadable', row: null, record: null })
      }
    }
    if (depth === cniHistoryLimits.depth - 1 && items.get(id)?.record) limited = true
  }
  // Only type/owner/byte-count verified edges enter the graph.
  for (const item of items.values()) {
    if (item.state !== 'valid') continue
    const parent = items.get(item.record.sourceid)
    if (parent?.available) {
      const before = content(parent.row)
      if (!before || before.bytes.length !== item.record.before) item.state = 'invalid'
    }
  }
  const related = new Set([source.id]); let changed = true
  while (changed) {
    changed = false
    for (const item of items.values()) {
      if (item.state !== 'valid') continue
      const parent = item.record.sourceid
      if (related.has(item.id) && items.has(parent) && !related.has(parent)) { related.add(parent); changed = true }
      if (related.has(parent) && !related.has(item.id)) { related.add(item.id); changed = true }
    }
  }
  const cycles = new Set()
  for (const start of related) {
    const path = []; const positions = new Set(); let cursor = start
    while (items.get(cursor)?.state === 'valid') {
      if (positions.has(cursor)) { path.forEach(v => cycles.add(v)); break }
      positions.add(cursor); path.push(cursor); cursor = items.get(cursor).record.sourceid
    }
  }
  function generation (start) {
    let cursor = start; let n = 0; const visited = new Set()
    while (items.get(cursor)?.state === 'valid') {
      if (visited.has(cursor) || n >= cniHistoryLimits.depth) return null
      visited.add(cursor); n++; cursor = items.get(cursor).record.sourceid
    }
    return n
  }
  const result = [...related].map(id => {
    const item = items.get(id); const parent = items.get(item.record?.sourceid)
    return { ...item, state: cycles.has(id) ? 'cycle' : item.state === 'valid' && !parent?.available ? (items.has(item.record.sourceid) ? 'sourceUnavailable' : 'limited') : item.state, generation: generation(id), current: id === source.id, parent: parent?.available ? parent.row : null, comparable: item.state === 'valid' && !cycles.has(id) && !!parent?.available }
  }).sort((a, b) => (a.generation ?? 999) - (b.generation ?? 999) || a.id.localeCompare(b.id))
  if (result.some(item => item.generation === null && item.state !== 'cycle')) limited = true
  const fresh = await readHistoryCni(get, source)
  cancelled(current)
  if (!fresh || fresh.userdata !== source.userdata || fresh.params !== source.params) fail('identity')
  return { rows: result, limited, scanned: rows.length, total, warnings: [...items.values()].filter(x => ['invalid', 'unreadable', 'limited'].includes(x.state)).length, incomplete: [...items.values()].some(x => ['unreadable', 'limited'].includes(x.state)) }
}

// A linear prefix/suffix change region avoids quadratic diff work on large CNI.
export function compareCniBodies (before, after) {
  const variables = row => typeof row.params === 'string' ? row.params.split(',').filter(Boolean) : []
  const leftParams = variables(before); const rightParams = variables(after)
  const added = rightParams.filter(v => !leftParams.includes(v)); const removed = leftParams.filter(v => !rightParams.includes(v))
  const result = { added, removed, paramsEqual: (before.params || '') === (after.params || ''), beforeParams: before.params || '', afterParams: after.params || '', rows: [], limited: false }
  let left; let right
  try { left = userDataContent(before.userdata); right = userDataContent(after.userdata) } catch (_) { return { ...result, state: 'corrupt' } }
  let first = 0
  while (first < left.bytes.length && first < right.bytes.length && left.bytes[first] === right.bytes[first]) first++
  Object.assign(result, { equal: left.encoded === right.encoded, before: left.bytes.length, after: right.bytes.length, first: left.encoded === right.encoded ? null : first + 1, state: left.encoded === right.encoded ? 'same' : 'different', binary: left.text === null || right.text === null })
  if (result.equal || result.binary) return result
  const a = left.text.split('\n'); const b = right.text.split('\n'); let start = 0; let end = 0
  while (start < a.length && start < b.length && a[start] === b[start]) start++
  while (end < a.length - start && end < b.length - start && a[a.length - end - 1] === b[b.length - end - 1]) end++
  let characters = 0
  for (const [kind, lines] of [['removed', a], ['added', b]]) {
    for (let i = start; i < lines.length - end; i++) {
      if (result.rows.length >= cniHistoryLimits.diffLines || characters >= cniHistoryLimits.diffCharacters) { result.limited = true; return result }
      const remaining = cniHistoryLimits.diffCharacters - characters
      const text = lines[i].slice(0, remaining); characters += text.length
      result.rows.push({ key: kind + i, kind, line: i + 1, text })
      if (text.length !== lines[i].length) { result.limited = true; return result }
    }
  }
  return result
}

export async function readCniComparison (get, anchor, left, right, current = () => true, expectedRecord = null) {
  if (left.id === right.id) fail('identity')
  cancelled(current)
  const origin = await readHistoryCni(get, anchor)
  cancelled(current)
  if (!origin) fail('unavailable')
  const a = left.id === origin.id ? origin : await readHistoryCni(get, origin, left.id)
  cancelled(current)
  const b = right.id === origin.id ? origin : await readHistoryCni(get, origin, right.id)
  cancelled(current)
  if (!a || !b) fail('unavailable')
  if (a.name !== left.name || b.name !== right.name) fail('identity')
  if (expectedRecord) {
    const record = parseCniCloneRecord(await readNotes(get, b, current), b)
    cancelled(current)
    const before = content(a); const after = content(b)
    if (!record || !before || !after || record.sourceid !== a.id || record.targetid !== b.id || record.before !== before.bytes.length || record.after !== after.bytes.length || ['sourceid', 'targetid', 'before', 'after'].some(key => record[key] !== expectedRecord[key])) fail('relation')
  }
  const fresh = await readHistoryCni(get, origin)
  cancelled(current)
  if (!fresh || fresh.userdata !== origin.userdata || fresh.params !== origin.params) fail('identity')
  return { left: a, right: b, difference: compareCniBodies(a, b) }
}

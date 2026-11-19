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

import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { reactive, nextTick } from 'vue'
import { Table } from 'ant-design-vue'
import History from '@/views/compute/CniHistory'
import { cniHistoryApis, readHistoryCni, readCniHistory, compareCniBodies, readCniComparison, cniHistoryLimits } from '@/utils/cniHistory'
import { cniCloneRecord, cniCloneChunks } from '@/utils/cniVersions'
import { getAPI, postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
const id = n => '00000000-0000-4000-8000-' + String(n).padStart(12, '0')
const encoded = text => Buffer.from(text).toString('base64')
const source = { id: id(1), name: 'root', account: 'owner', domainid: id(9), domain: 'domain', userdata: encoded('\ufeff#cloud-config\r\n# old 😀\r\n'), params: 'OLD,KEEP' }
const child = { ...source, id: id(2), name: 'child', userdata: encoded('#cloud-config\n# new 😀\n'), params: 'NEW,KEEP' }
const grandchild = { ...child, id: id(3), name: 'grandchild' }
const list = (rows, count = rows.length) => ({ listcniconfigurationresponse: { count, cniconfig: rows } })
const notes = (rows, count = rows.length) => ({ listannotationsresponse: { count, annotation: rows } })
const relation = (a, b) => cniCloneChunks(cniCloneRecord(a, b)).map((annotation, i) => ({ id: id(1000 + Number(b.id.slice(-4)) * 100 + i), entityid: b.id, entitytype: 'USER_DATA', annotation }))
function api (rows = [source, child, grandchild], annotations = { [child.id]: relation(source, child), [grandchild.id]: relation(child, grandchild) }) {
  const calls = []; const read = jest.fn(async (cmd, p) => {
    calls.push({ cmd, p })
    if (cmd === 'listAnnotations') { const ns = annotations[p.entityid] || []; return notes(ns.slice((p.page - 1) * p.pagesize, p.page * p.pagesize), ns.length) }
    if (cmd !== 'listCniConfiguration') throw Error('unexpected write or API ' + cmd)
    return p.id ? list(rows.filter(r => r.id === p.id)) : list(rows.slice((p.page - 1) * p.pagesize, p.page * p.pagesize), rows.length)
  })
  return { read, calls }
}
function context (resource = child) {
  const c = { resource, tab: 'cni.history', loading: false, resourceActionReady: () => true, resourceReadPending: () => false, $route: { path: '/cniconfiguration/' + resource.id, query: {} }, $router: { push: jest.fn() }, $store: { getters: { apis: Object.fromEntries(cniHistoryApis.map(a => [a, {}])), project: {}, userInfo: { id: 'admin' } }, state: { user: { token: 'session' } } }, $t: k => k }
  Object.assign(c, History.data.call(c))
  Object.entries(History.computed).forEach(([k, fn]) => Object.defineProperty(c, k, { get: () => fn.call(c) }))
  Object.entries(History.methods).forEach(([k, fn]) => { c[k] = fn.bind(c) })
  return c
}
beforeEach(() => jest.resetAllMocks())
test('two generations form an ordered family with verified parent links', async () => {
  const { read, calls } = api(); const r = await readCniHistory(read, child)
  expect(r.rows.map(x => x.id)).toEqual([source.id, child.id, grandchild.id]); expect(r.rows.map(x => x.generation)).toEqual([0, 1, 2])
  expect(r.rows[1].parent).toEqual(source); expect(r.rows[1].current).toBe(true); expect(r.rows[2].comparable).toBe(true)
  expect(r.limited).toBe(false); expect(r.warnings).toBe(0); expect(calls.every(x => ['listCniConfiguration', 'listAnnotations'].includes(x.cmd))).toBe(true)
})
test('branches and identical bodies are still related, unrelated CNI is excluded', async () => {
  const branch = { ...child, id: id(4), name: 'branch' }; const other = { ...source, id: id(5), name: 'other' }
  const { read } = api([other, grandchild, branch, source, child], { [child.id]: relation(source, child), [grandchild.id]: relation(child, grandchild), [branch.id]: relation(source, branch) })
  const r = await readCniHistory(read, child); expect(r.rows.map(x => x.id)).not.toContain(other.id); expect(r.rows).toHaveLength(4)
})
test('an unversioned current CNI is a known root; normal UserData comments are ignored', async () => {
  const { read } = api([source], { [source.id]: [{ id: id(70), annotation: 'MOLD_USERDATA_VERSION/1\n{}' }, { id: id(71), annotation: 'ordinary' }] })
  const r = await readCniHistory(read, source); expect(r.rows).toHaveLength(1); expect(r.rows[0].state).toBe('none'); expect(r.warnings).toBe(0)
})
test.each(['id', 'account', 'domainid', 'projectid', 'name'])('exact anchor read rejects changed %s', async key => {
  const read = jest.fn().mockResolvedValue(list([{ ...source, [key]: 'foreign' }]))
  await expect(readHistoryCni(read, source)).rejects.toThrow('identity')
})
test.each([{}, { listuserdataresponse: { count: 1, userdata: [source] } }, { listcniconfigurationresponse: { count: 1 } }, { listcniconfigurationresponse: { count: '1', cniconfig: [source] } }])('malformed and wrong-type envelopes are not history', async value => {
  await expect(readHistoryCni(jest.fn().mockResolvedValue(value), source)).rejects.toThrow('incomplete')
})
test('owner and project filters remain explicit for every request', async () => {
  for (const project of [false, true]) {
    const rows = [source, child].map(r => project ? { ...r, projectid: id(90), project: 'project' } : r)
    const { read, calls } = api(rows, { [child.id]: relation(rows[0], rows[1]) }); await readCniHistory(read, rows[0])
    for (const call of calls) {
      if (project) { expect(call.p.projectid).toBe(id(90)); expect(call.p.account).toBeUndefined(); expect(call.p.ignoreproject).toBeUndefined() } else if (call.cmd === 'listCniConfiguration') { expect(call.p.account).toBe(source.account); expect(call.p.domainid).toBe(source.domainid); expect(call.p.ignoreproject).toBe(true) }
    }
  }
})
test('two inventory pages and two annotation pages are complete before rendering', async () => {
  const rows = Array.from({ length: 51 }, (_, n) => ({ ...source, id: id(n + 1), name: n ? 'row' + n : source.name }))
  const ns = Array.from({ length: 101 }, (_, n) => ({ id: id(n + 10000), annotation: 'ordinary' }))
  const { read, calls } = api(rows, { [source.id]: ns }); const r = await readCniHistory(read, source)
  expect(r.scanned).toBe(51); expect(r.limited).toBe(false); expect(calls.filter(c => c.cmd === 'listCniConfiguration' && !c.p.id)).toHaveLength(2); expect(calls.some(c => c.cmd === 'listAnnotations' && c.p.page === 2)).toBe(true)
})
test.each(['empty', 'duplicate', 'count-change', 'foreign'])('unstable inventory %s is not an empty or complete result', async scenario => {
  const rows = Array.from({ length: 51 }, (_, n) => ({ ...source, id: id(n + 1), name: n ? 'row' + n : source.name })); const { read } = api(rows)
  const changed = async (cmd, p) => {
    if (cmd === 'listCniConfiguration' && !p.id && p.page === 2) {
      if (scenario === 'empty') return list([], 51)
      if (scenario === 'duplicate') return list([rows[0]], 51)
      if (scenario === 'count-change') return list([rows[50]], 52)
      return list([{ ...rows[50], account: 'foreign' }], 51)
    }
    return read(cmd, p)
  }
  await expect(readCniHistory(changed, source)).rejects.toThrow()
})
test('inventory bound is explicit; missing anchor is separately read without unbounded scan', async () => {
  const rows = Array.from({ length: 202 }, (_, n) => ({ ...source, id: id(n + 1), name: n ? 'row' + n : source.name })); const { read, calls } = api(rows, {})
  const r = await readCniHistory(read, rows[201]); expect(r.limited).toBe(true); expect(r.scanned).toBe(201); expect(r.rows[0].id).toBe(rows[201].id)
  expect(calls.filter(x => x.cmd === 'listCniConfiguration' && !x.p.id)).toHaveLength(4)
})
test.each(['missing-chunk', 'bad-json', 'wrong-owner', 'wrong-target', 'after-bytes', 'duplicate-note'])('invalid metadata %s is visible, never trusted as a parent link', async scenario => {
  const ns = relation(source, child)
  if (scenario === 'missing-chunk') ns.pop()
  if (scenario === 'bad-json') ns[0].annotation = 'MOLD_CNI_CLONE/1\n{'
  if (scenario === 'wrong-owner') ns.splice(0, ns.length, ...cniCloneChunks({ ...cniCloneRecord(source, child), owner: { domainid: source.domainid, account: 'foreign' } }).map((annotation, i) => ({ id: id(500 + i), annotation })))
  if (scenario === 'wrong-target') ns.splice(0, ns.length, ...relation(source, grandchild).map(n => ({ ...n, entityid: child.id })))
  if (scenario === 'after-bytes') ns.splice(0, ns.length, ...cniCloneChunks({ ...cniCloneRecord(source, child), after: 1 }).map((annotation, i) => ({ id: id(500 + i), annotation })))
  if (scenario === 'duplicate-note') ns.push(ns[0])
  const { read } = api([source, child], { [child.id]: ns }); const r = await readCniHistory(read, child)
  expect(r.rows[0].state).toBe(scenario === 'duplicate-note' ? 'unreadable' : 'invalid'); expect(r.rows[0].comparable).toBe(false); expect(r.warnings).toBe(1)
})
test('source byte-count mismatch is a damaged relationship', async () => {
  const { read } = api([{ ...source, userdata: encoded('different length') }, child], { [child.id]: relation(source, child) })
  expect((await readCniHistory(read, child)).rows[0].state).toBe('invalid')
})
test.each([null, 403, 431])('deleted, foreign-type or inaccessible source %s is a placeholder without a link', async code => {
  const { read } = api([child], { [child.id]: relation(source, child) })
  const wrapped = async (cmd, p) => { if (p.id === source.id && code) throw Object.assign(new Error('denied'), { response: { status: code } }); return read(cmd, p) }
  const r = await readCniHistory(wrapped, child); expect(r.rows[0].state).toBe('unavailable'); expect(r.rows[0].row).toBeNull(); expect(r.rows[1].comparable).toBe(false); expect(r.rows[1].state).toBe('sourceUnavailable')
})
test('cyclic records and paths into a cycle cannot be compared as valid lineage', async () => {
  const { read } = api([source, child, grandchild], { [source.id]: relation(child, source), [child.id]: relation(source, child), [grandchild.id]: relation(child, grandchild) })
  const r = await readCniHistory(read, source); expect(r.rows).toHaveLength(3); expect(r.rows.every(x => x.state === 'cycle' && !x.comparable && x.generation === null)).toBe(true)
})
test('annotation bounds and read failures explicitly prevent complete-history claims', async () => {
  const ns = Array.from({ length: 501 }, (_, n) => ({ id: id(n + 10000), annotation: 'ordinary' })); const { read } = api([source], { [source.id]: ns }); const r = await readCniHistory(read, source)
  expect(r.incomplete).toBe(true); expect(r.rows[0].state).toBe('limited'); expect(r.warnings).toBe(1)
})
test('duplicate and changing annotation pages never become valid records', async () => {
  const ns = Array.from({ length: 101 }, (_, n) => ({ id: id(n + 10000), annotation: 'ordinary' })); const { read } = api([source], { [source.id]: ns })
  const r = await readCniHistory(async (cmd, p) => cmd === 'listAnnotations' && p.page === 2 ? notes([ns[0]], 101) : read(cmd, p), source)
  expect(r.incomplete).toBe(true); expect(r.rows[0].state).toBe('unreadable')
})
test('cancellation stops queued owner reads and prevents publishing an old generation', async () => {
  const { read, calls } = api(); let active = true
  await expect(readCniHistory(async (cmd, p) => { const value = await read(cmd, p); if (cmd === 'listAnnotations') active = false; return value }, source, () => active)).rejects.toThrow('cancelled')
  expect(calls.length).toBeLessThanOrEqual(6)
})
test('bytes, BOM/CRLF, first difference and added/removed declarations are exact', () => {
  const d = compareCniBodies(source, child); expect(d.equal).toBe(false); expect(d.before).toBe(Buffer.from(source.userdata, 'base64').length); expect(d.first).toBe(1)
  expect(d.added).toEqual(['NEW']); expect(d.removed).toEqual(['OLD']); expect(d.rows.some(r => r.text.includes('\r'))).toBe(true)
  expect(compareCniBodies(child, grandchild)).toMatchObject({ state: 'same', paramsEqual: true, rows: [] })
  expect(compareCniBodies(child, { ...child, params: 'KEEP,NEW' })).toMatchObject({ state: 'same', paramsEqual: false, added: [], removed: [] })
})
test.each([['/wAB', '/wAB', true], ['/wAB', '/wAC', false]])('binary %s / %s never silently decodes replacement text', (a, b, equal) => {
  const d = compareCniBodies({ ...source, userdata: a }, { ...child, userdata: b }); expect(d.binary).toBe(true); expect(d.equal).toBe(equal); expect(d.rows).toEqual([])
})
test.each(['!', 'YR==', null])('corrupt or empty encoded input %s remains distinct', value => {
  const d = compareCniBodies({ ...source, userdata: value }, child); expect(d.state).toBe(value === null ? 'different' : 'corrupt')
})
test('a bounded linear change region preserves source line numbers and announces truncation', () => {
  const d = compareCniBodies({ ...source, userdata: encoded('same\nold\nend\n') }, { ...child, userdata: encoded('same\nnew\nend\n') })
  expect(d.rows).toEqual([{ key: 'removed1', kind: 'removed', line: 2, text: 'old' }, { key: 'added1', kind: 'added', line: 2, text: 'new' }])
  const large = compareCniBodies({ ...source, userdata: encoded('a\n'.repeat(1000)) }, child); expect(large.limited).toBe(true); expect(large.rows.length).toBe(cniHistoryLimits.diffLines)
  const long = compareCniBodies({ ...source, userdata: encoded('a'.repeat(100000)) }, child); expect(long.limited).toBe(true); expect(long.rows[0].text.length).toBe(cniHistoryLimits.diffCharacters)
})
test('comparison rereads both exact typed owner-scoped UUIDs and sends no mutation', async () => {
  const { read, calls } = api(); const r = await readCniComparison(read, source, source, child); expect(r.difference.state).toBe('different'); expect(calls.map(x => x.p.id)).toEqual([source.id, child.id, source.id])
  await expect(readCniComparison(read, source, source, source)).rejects.toThrow('identity')
  await expect(readCniComparison(read, source, source, { ...child, name: 'stale' })).rejects.toThrow('identity')
})
test('a stale source read is discarded before the second comparison read', async () => {
  const { read } = api(); let active = true
  await expect(readCniComparison(async (...args) => { const r = await read(...args); active = false; return r }, source, source, child, () => active)).rejects.toThrow('cancelled')
  expect(read).toHaveBeenCalledTimes(1)
})
test('the tab publishes real graph results and comparison, never invokes a write API', async () => {
  const c = context(); getAPI.mockImplementation(api().read); await c.fetchData(); expect(c.rows).toHaveLength(3)
  c.selected = [source.id, child.id]; c.compareSelected(); await Promise.resolve(); await Promise.resolve(); await Promise.resolve(); await Promise.resolve()
  expect(c.comparisonOpen).toBe(true); expect(postAPI).not.toHaveBeenCalled()
})
test('refresh errors retain rows but gate comparisons; retry recovers', async () => {
  const c = context(); const read = api().read; getAPI.mockImplementation(read); await c.fetchData(); const old = c.rows
  getAPI.mockRejectedValue(Error('timeout')); await c.fetchData(); expect(c.rows).toBe(old); expect(c.error).toBe('message.cni.history.error'); c.selected = [source.id, child.id]; expect(c.canCompareSelection).toBe(false)
  getAPI.mockImplementation(read); await c.fetchData(); expect(c.error).toBe('')
})
test.each([401, 403, 432, 531])('permission error %s clears stale rows', async code => {
  const c = context(); c.rows = [{ id: source.id }]; getAPI.mockRejectedValue({ response: { status: code } }); await c.fetchData(); expect(c.rows).toEqual([]); expect(c.error).toBe('label.forbidden')
})
test('scope, session, parent refresh and tab changes discard open comparison and delayed reads', async () => {
  const c = context(); let release; getAPI.mockImplementation(() => new Promise(resolve => { release = resolve })); const work = c.fetchData()
  c.$store.state.user.token = 'new'; delete c.$store.getters.apis.listAnnotations; History.watch.scope.call(c); release(list([child])); await work
  expect(c.rows).toEqual([]); expect(c.comparison).toBeNull(); expect(c.comparisonOpen).toBe(false)
})
test('closing during comparison prevents late content from restoring the dialog', async () => {
  const c = context(); let release; getAPI.mockImplementation(() => new Promise(resolve => { release = resolve })); const work = c.openComparison(source, child); c.closeComparison(); release(list([source])); await work
  expect(c.comparisonOpen).toBe(false); expect(c.comparison).toBeNull()
})
test('URL search/pagination and project links preserve return state', () => {
  const c = context({ ...child, projectid: id(90) }); c.$route.query = { cnireturn: '{"page":"2"}', cnihistorypage: '2', cnihistorysize: '10', cnihistoryq: 'child' }; c.readQuery()
  expect(c.page).toBe(2); expect(c.pagesize).toBe(10); expect(c.keyword).toBe('child'); expect(c.resourceLink(source.id).query).toMatchObject({ cnireturn: '{"page":"2"}', projectid: id(90), tab: 'cni.history' })
  c.search('root'); expect(c.$router.push).toHaveBeenCalledWith(expect.objectContaining({ query: expect.objectContaining({ cnihistoryq: 'root', cnihistorypage: '1' }) }))
})
test('deep ancestry is bounded and explicitly marked, without recursive stack growth', async () => {
  const rows = Array.from({ length: 20 }, (_, n) => ({ ...source, id: id(n + 1), name: n ? 'row' + n : source.name })); const ns = Object.fromEntries(rows.slice(1).map((r, i) => [r.id, relation(rows[i], r)])); const { read } = api(rows, ns)
  const result = await readCniHistory(read, rows[19]); expect(result.limited).toBe(true); expect(result.rows).toHaveLength(20); expect(result.rows.some(r => r.generation === null)).toBe(true)
})
test('annotation workers never exceed the configured concurrent read bound', async () => {
  const rows = Array.from({ length: 30 }, (_, n) => ({ ...source, id: id(n + 1), name: n ? 'row' + n : source.name })); const { read } = api(rows, {}); let pending = 0; let maximum = 0
  await readCniHistory(async (cmd, p) => { if (cmd !== 'listAnnotations') return read(cmd, p); pending++; maximum = Math.max(pending, maximum); await Promise.resolve(); const r = await read(cmd, p); pending--; return r }, source)
  expect(maximum).toBeLessThanOrEqual(cniHistoryLimits.workers); expect(maximum).toBeGreaterThan(1)
})
test.each(['parent', 'tab'])('a %s transition cancels an in-flight comparison and removes export content', async kind => {
  const c = context(); let release; getAPI.mockImplementation(() => new Promise(resolve => { release = resolve })); const work = c.openComparison(source, child)
  if (kind === 'parent') { c.loading = true; History.watch.parentState.call(c, 'pending') } else { c.tab = 'details'; History.watch.active.call(c, false) }
  release(list([source])); await work; expect(c.comparisonOpen).toBe(kind === 'parent'); expect(c.comparison).toBeNull()
})

test('relationship comparison rereads complete metadata and rejects a removed or changed source relation', async () => {
  const good = api(); const record = cniCloneRecord(source, child)
  expect((await readCniComparison(good.read, source, source, child, () => true, record)).difference.state).toBe('different')
  const removed = api([source, child], {})
  await expect(readCniComparison(removed.read, source, source, child, () => true, record)).rejects.toThrow('relation')
})
test('an unavailable context anchor stops comparison before reading other configurations', async () => {
  const { read, calls } = api([source, grandchild], {})
  await expect(readCniComparison(read, child, source, grandchild)).rejects.toThrow('unavailable'); expect(calls).toHaveLength(1)
})

test('failed parent context is unverified, never a successful empty history', () => {
  const c = context(); c.resourceActionReady = () => false
  expect(c.emptyMessage).toBe('message.cni.history.unverified'); expect(c.canCompareSelection).toBe(false)
  c.loading = true; expect(c.emptyMessage).toBe('label.loading')
})

test('a periodic parent refresh keeps the dialog snapshot and revalidates it before export', async () => {
  const c = context(); getAPI.mockImplementation(api().read); await c.openComparison(source, child); const snapshot = c.comparison
  c.resourceReadPending = () => true; c.resourceActionReady = () => false; History.watch.parentState.call(c, 'pending')
  expect(c.parentReady).toBe(false); expect(c.comparisonOpen).toBe(true); expect(c.comparison).toBe(snapshot)
  c.resourceReadPending = () => false; c.resourceActionReady = () => true; await History.watch.parentState.call(c, 'ready')
  expect(c.comparisonOpen).toBe(true); expect(c.comparison.difference.state).toBe('different')
  c.resourceActionReady = () => false; History.watch.parentState.call(c, 'unverified'); expect(c.comparisonOpen).toBe(false); expect(c.comparison).toBeNull()
})
test('normal same-scope refresh retains valid cross-page selection', async () => {
  const c = context(); c.selected = [source.id, child.id]; getAPI.mockImplementation(api().read); await c.fetchData(); expect(c.selected).toEqual([source.id, child.id])
})
test('an independently selected corrupt body is returned as corrupt rather than a read error', async () => {
  const { read } = api([{ ...source, userdata: 'YR==' }, child], {})
  expect((await readCniComparison(read, child, source, child)).difference.state).toBe('corrupt')
})

test('rendered comparison keeps its snapshot but disables native export controls during a parent read', async () => {
  const c = context(); const state = reactive({ pending: false, valid: true }); getAPI.mockImplementation(api().read)
  const pass = { template: '<div><slot /></div>' }
  const wrapper = mount(History, {
    props: { resource: child, tab: 'cni.history' },
    global: {
      mocks: { $store: c.$store, $route: c.$route, $router: c.$router, $t: c.$t },
      provide: { resourceActionReady: () => state.valid && !state.pending, resourceReadPending: () => state.pending },
      stubs: { MoldDialog: pass, UserDataContent: { template: '<button data-test="body-export">Export</button>' }, ASpin: pass, ADescriptions: pass, ADescriptionsItem: pass, ATable: true, AAlert: true, AButton: true, AInputSearch: true, APagination: true, ATag: true, RouterLink: RouterLinkStub }
    }
  })
  await flushPromises(); await wrapper.vm.openComparison(source, child); await nextTick()
  expect(wrapper.find('[data-test="body-export"]').exists()).toBe(true); const snapshot = wrapper.vm.comparison
  state.pending = true; await nextTick(); await nextTick()
  expect(wrapper.find('fieldset').element.disabled).toBe(true); expect(wrapper.vm.comparison).toBe(snapshot); expect(wrapper.vm.comparisonOpen).toBe(true)
  state.pending = false; await flushPromises(); await nextTick()
  expect(wrapper.find('fieldset').element.disabled).toBe(false); expect(wrapper.vm.comparisonOpen).toBe(true)
  state.valid = false; await nextTick(); await nextTick()
  expect(wrapper.find('[data-test="body-export"]').exists()).toBe(false); expect(wrapper.vm.emptyMessage).toBe('message.cni.history.unverified'); wrapper.unmount()
})

test('the real table keeps selected UUIDs when a configuration moves off the current page', async () => {
  window.matchMedia = jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() }))
  const c = context(); getAPI.mockImplementation(api().read)
  const wrapper = mount(History, {
    props: { resource: child, tab: 'cni.history' },
    global: {
      mocks: { $store: c.$store, $route: c.$route, $router: c.$router, $t: c.$t },
      components: { ATable: Table },
      provide: { resourceActionReady: () => true, resourceReadPending: () => false },
      stubs: { RouterLink: RouterLinkStub, MoldDialog: true, UserDataContent: true, AAlert: true, AButton: true, AInputSearch: true, APagination: true, ATag: true, 'reload-outlined': true }
    }
  })
  await flushPromises(); await wrapper.setData({ pagesize: 1, page: 1 })
  await wrapper.find('tr[data-row-key="' + source.id + '"] input').setValue(true); await nextTick()
  expect(wrapper.vm.selected).toEqual([source.id])
  await wrapper.setData({ page: 3 }); await nextTick()
  await wrapper.find('tr[data-row-key="' + grandchild.id + '"] input').setValue(true); await nextTick()
  expect(wrapper.vm.selected).toEqual([source.id, grandchild.id]); expect(wrapper.vm.canCompareSelection).toBe(true); wrapper.unmount()
})

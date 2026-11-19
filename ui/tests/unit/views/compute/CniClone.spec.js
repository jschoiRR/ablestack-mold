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

import Clone from '@/views/compute/CloneCniConfiguration'
import Annotations from '@/components/view/AnnotationsTab'
import {
  cniCloneApis,
  cniVersionPrefix,
  cloneOwner,
  readCniCloneSource,
  findCniCloneAttempt,
  cniCloneRecord,
  cniCloneChunks,
  parseCniCloneRecord,
  saveCniCloneRecord
} from '@/utils/cniVersions'
import { getAPI, postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
const source = {
  id: '11111111-1111-4111-8111-111111111111',
  name: 'source',
  account: 'owner',
  domainid: '22222222-2222-4222-8222-222222222222',
  domain: 'domain',
  userdata: Buffer.from('\ufeff#cloud-config\r\n# 한글 😀\r\n').toString('base64'),
  params: 'OLD'
}
const target = {
  ...source,
  id: '33333333-3333-4333-8333-333333333333',
  name: 'source-v2',
  userdata: Buffer.from('#cloud-config\n# new 😀\n').toString('base64'),
  params: 'NEW'
}
const params = { name: target.name, cniconfig: target.userdata, params: target.params }
const list = (rows) => ({ listcniconfigurationresponse: { count: rows.length, cniconfig: rows } })
const notes = (rows) => ({ listannotationsresponse: { count: rows.length, annotation: rows } })
function context () {
  const c = {
    resource: { ...source },
    $route: { path: '/cniconfiguration/' + source.id },
    $router: { resolve: (p) => ({ href: '#' + p }), push: jest.fn() },
    $store: {
      getters: { apis: Object.fromEntries(cniCloneApis.map((a) => [a, {}])), project: {}, userInfo: { id: 'admin' } },
      state: { user: { token: 'session' } }
    },
    $t: (k) => k,
    $emit: jest.fn(),
    $nextTick: (fn) => Promise.resolve().then(fn),
    $refs: { formRef: { validate: jest.fn().mockResolvedValue(), scrollToField: jest.fn() } }
  }
  Object.assign(c, Clone.data.call(c))
  Object.entries(Clone.computed).forEach(([k, fn]) => Object.defineProperty(c, k, { get: () => fn.call(c) }))
  Object.entries(Clone.methods).forEach(([k, fn]) => {
    c[k] = fn.bind(c)
  })
  c.source = Object.freeze({ ...source })
  c.form = { name: target.name, cniconfig: target.userdata, isbase64: true, params: ['NEW'] }
  return c
}
function api (c) {
  const state = { registered: false, notes: [], registrationCount: 0 }
  getAPI.mockImplementation(async (cmd, p) =>
    cmd === 'listAnnotations'
      ? notes(state.notes)
      : p.id
        ? list([p.id === source.id ? source : target])
        : list(state.registered ? [target] : [])
  )
  postAPI.mockImplementation(async (cmd, p) => {
    if (cmd === 'registerCniConfiguration') {
      state.registrationCount++
      state.registered = true
      return { registercniconfigurationresponse: { cniconfig: target } }
    }
    state.notes.push({ id: 'note-' + state.notes.length, annotation: p.annotation })
    return { addannotationresponse: { annotation: { id: 'note-' + state.notes.length } } }
  })
  return state
}
beforeEach(() => jest.resetAllMocks())
test('account and project owners remain explicit and CNI reads use only the CNI API', async () => {
  expect(cloneOwner(source)).toEqual({ account: 'owner', domainid: source.domainid, ignoreproject: true })
  expect(cloneOwner({ ...source, projectid: target.id })).toEqual({ projectid: target.id })
  getAPI.mockResolvedValue(list([source]))
  expect(await readCniCloneSource(getAPI, source)).toEqual(source)
  expect(getAPI.mock.calls[0][0]).toBe('listCniConfiguration')
})
test.each(['id', 'name', 'domainid', 'account', 'projectid'])('rejects a mismatching source %s', async (key) => {
  getAPI.mockResolvedValue(list([{ ...source, [key]: 'foreign' }]))
  await expect(readCniCloneSource(getAPI, source)).rejects.toThrow('identity')
})
test.each([{}, { listcniconfigurationresponse: { count: 1 } }, { listcniconfigurationresponse: { count: 2, cniconfig: [source] } }])(
  'rejects incomplete or wrong-type envelopes',
  async (response) => {
    getAPI.mockResolvedValue(response)
    await expect(readCniCloneSource(getAPI, source)).rejects.toThrow()
  }
)
test('binary remains byte-preserving and an empty source is blocked', async () => {
  getAPI.mockResolvedValue(list([{ ...source, userdata: '/wAB' }]))
  expect((await readCniCloneSource(getAPI, source)).userdata).toBe('/wAB')
  getAPI.mockResolvedValue(list([{ ...source, userdata: '' }]))
  await expect(readCniCloneSource(getAPI, source)).rejects.toThrow('empty')
})
test.each(['userdata', 'params', 'account', 'domainid', 'id'])('reconciliation rejects a conflicting target %s', async (key) => {
  getAPI.mockResolvedValue(list([{ ...target, [key]: key === 'id' ? source.id : 'different' }]))
  await expect(findCniCloneAttempt(getAPI, source, params)).rejects.toThrow('conflict')
})
test('name substring is not a result and pagination uncertainty is not absence', async () => {
  getAPI.mockResolvedValue(list([{ ...target, name: 'prefix-' + target.name }]))
  expect(await findCniCloneAttempt(getAPI, source, params)).toBeNull()
  getAPI.mockResolvedValue({ listcniconfigurationresponse: { count: 501, cniconfig: [] } })
  await expect(findCniCloneAttempt(getAPI, source, params)).rejects.toThrow('uncertain')
})
test.each([source, { ...source, account: '한글😀'.repeat(80) }, { ...source, projectid: '44444444-4444-4444-8444-444444444444' }])(
  'full owner provenance fits bounded chunks without truncation',
  (s) => {
    const t = { ...target, account: s.account, projectid: s.projectid }
    const record = cniCloneRecord(s, t)
    const chunks = cniCloneChunks(record)
    expect(chunks.every((x) => x.length <= 255)).toBe(true)
    expect(
      parseCniCloneRecord(
        chunks.map((annotation) => ({ annotation })),
        t
      )
    ).toEqual(record)
    expect(chunks.every((x) => x.startsWith(cniVersionPrefix))).toBe(true)
  }
)
test('partial, conflicting, foreign owner, wrong target and UserData notes are not CNI provenance', () => {
  const record = cniCloneRecord(source, target)
  const chunks = cniCloneChunks(record)
  const rows = chunks.map((annotation) => ({ annotation }))
  expect(parseCniCloneRecord(rows.slice(1), target)).toBeNull()
  const conflict = { ...JSON.parse(chunks[0].slice(cniVersionPrefix.length)), b: 'AAAA' }
  expect(parseCniCloneRecord([...rows, { annotation: cniVersionPrefix + JSON.stringify(conflict) }], target)).toBeNull()
  expect(parseCniCloneRecord(rows, { ...target, account: 'foreign' })).toBeNull()
  expect(parseCniCloneRecord(rows, source)).toBeNull()
  expect(parseCniCloneRecord([{ annotation: 'MOLD_USERDATA_VERSION/1\n{}' }], target)).toBeNull()
  expect(parseCniCloneRecord([...rows, ...rows], target)).toEqual(record)
})
test('save partial chunks and lost annotation response is idempotent', async () => {
  const c = context()
  const state = api(c)
  const record = cniCloneRecord(source, target)
  const chunks = cniCloneChunks(record)
  state.notes = [{ annotation: chunks[0] }]
  let once = true
  postAPI.mockImplementation(async (cmd, p) => {
    state.notes.push({ annotation: p.annotation })
    if (once) {
      once = false
      throw Error('lost response')
    }
    return { addannotationresponse: { annotation: { id: 'x' } } }
  })
  await saveCniCloneRecord(getAPI, postAPI, target, record)
  expect(parseCniCloneRecord(state.notes, target)).toEqual(record)
  const n = postAPI.mock.calls.length
  await saveCniCloneRecord(getAPI, postAPI, target, record)
  expect(postAPI.mock.calls).toHaveLength(n)
})
test('never reports relation success from a POST success whose data cannot be read back', async () => {
  api(context())
  postAPI.mockResolvedValue({ addannotationresponse: { annotation: { id: 'x' } } })
  await expect(saveCniCloneRecord(getAPI, postAPI, target, cniCloneRecord(source, target))).rejects.toThrow('history')
})
test('context cancellation prevents annotation writes', async () => {
  api(context())
  await saveCniCloneRecord(getAPI, postAPI, target, cniCloneRecord(source, target), () => false)
  expect(postAPI).not.toHaveBeenCalled()
})
test('actual component registers CNI with frozen ownership and reads a complete relation', async () => {
  const c = context()
  const state = api(c)
  await c.submit()
  expect(c.target.id).toBe(target.id)
  expect(c.saved).toBe(true)
  expect(state.registrationCount).toBe(1)
  expect(postAPI.mock.calls[0][1]).toEqual({ ...cloneOwner(source), ...params })
  expect(postAPI.mock.calls.some((x) => x[0] === 'registerUserData')).toBe(false)
  expect(parseCniCloneRecord(state.notes, target)).toEqual(c.record)
  expect(c.$emit).toHaveBeenCalledWith('refresh-data')
})
test('identical bytes with a new name are a valid clone', async () => {
  const c = context()
  const same = { ...target, userdata: source.userdata, params: source.params }
  c.form.cniconfig = source.userdata
  c.form.params = ['OLD']
  let registered = false
  const rows = []
  getAPI.mockImplementation(async (cmd, p) =>
    cmd === 'listAnnotations' ? notes(rows) : p.id ? list([p.id === source.id ? source : same]) : list(registered ? [same] : [])
  )
  postAPI.mockImplementation(async (cmd, p) => {
    if (cmd === 'registerCniConfiguration') {
      registered = true
      return { registercniconfigurationresponse: { cniconfig: same } }
    }
    rows.push({ annotation: p.annotation })
    return { addannotationresponse: { annotation: { id: 'x' } } }
  })
  await c.submit()
  expect(c.saved).toBe(true)
  expect(c.record.before).toBe(c.record.after)
})
test('lost registration response reconciles without a second register', async () => {
  const c = context()
  const state = api(c)
  postAPI.mockImplementationOnce(async () => {
    state.registrationCount++
    state.registered = true
    throw Error('lost')
  })
  await c.submit()
  expect(c.target).toBeNull()
  expect(c.attempt).toBeTruthy()
  expect(c.error).toContain('uncertain')
  await c.submit()
  expect(c.saved).toBe(true)
  expect(state.registrationCount).toBe(1)
})
test('confirmed missing registration unlocks input but does not automatically post', async () => {
  const c = context()
  api(c)
  c.attempt = params
  await c.submit()
  expect(c.attempt).toBeNull()
  expect(c.error).toContain('notfound')
  expect(postAPI).not.toHaveBeenCalled()
})
test('name conflicts preserve draft and never post', async () => {
  const c = context()
  const state = api(c)
  state.registered = true
  await c.submit()
  expect(c.error).toContain('conflict')
  expect(c.form.cniconfig).toBe(target.userdata)
  expect(postAPI).not.toHaveBeenCalled()
})
test('relation failure retries only metadata for the known target UUID', async () => {
  const c = context()
  const state = api(c)
  const original = postAPI.getMockImplementation()
  postAPI.mockImplementation(async (cmd, p, o) => (cmd === 'addAnnotation' ? Promise.reject(Error('offline')) : original(cmd, p, o)))
  await c.submit()
  expect(c.target.id).toBe(target.id)
  expect(c.saved).toBe(false)
  expect(c.record).toBeTruthy()
  postAPI.mockImplementation(original)
  await c.submit()
  expect(c.saved).toBe(true)
  expect(state.registrationCount).toBe(1)
})
test('permission loss preserves the receipt and restoration retries metadata only', async () => {
  const c = context()
  const state = api(c)
  c.target = target
  c.record = cniCloneRecord(source, target)
  delete c.$store.getters.apis.addAnnotation
  Clone.watch.allowed.call(c, false)
  await c.submit()
  expect(c.target.id).toBe(target.id)
  expect(postAPI).not.toHaveBeenCalled()
  c.$store.getters.apis.addAnnotation = {}
  Clone.watch.allowed.call(c, true)
  await c.submit()
  expect(c.saved).toBe(true)
  expect(state.registrationCount).toBe(0)
})
test('repeated click during a pending validation does not register twice', async () => {
  const c = context()
  const state = api(c)
  let release
  c.$refs.formRef.validate = () =>
    new Promise((resolve) => {
      release = resolve
    })
  const first = c.submit()
  await c.submit()
  release()
  await first
  expect(state.registrationCount).toBe(1)
})
test('changed source and malformed registration success cannot become success', async () => {
  const c = context()
  api(c)
  getAPI.mockResolvedValue(list([{ ...source, userdata: target.userdata }]))
  await c.submit()
  expect(c.error).toContain('changed')
  expect(postAPI).not.toHaveBeenCalled()
  api(c)
  postAPI.mockResolvedValue({})
  await c.submit()
  expect(c.attempt).toBeTruthy()
  expect(c.target).toBeNull()
  expect(c.error).toContain('uncertain')
})
test('scope change after delayed source read discards stale data', async () => {
  const c = context()
  let release
  getAPI.mockImplementation(
    () =>
      new Promise((resolve) => {
        release = resolve
      })
  )
  c.source = null
  const pending = c.load()
  c.generation++
  release(list([source]))
  await pending
  expect(c.source).toBeNull()
})

test('CNI metadata is presented without protocol text while other comments keep their content', () => {
  const annotation = 'MOLD_CNI_CLONE/1\nopaque'; const call = (path, text) => Annotations.methods.annotationText.call({ $route: { path }, $t: k => k }, { annotation: text })
  expect(call('/cniconfiguration/id', annotation)).toBe('label.cni.clone.relation')
  expect(call('/userdata/id', annotation)).toBe(annotation)
  expect(call('/vm/id', '일반 코멘트')).toBe('일반 코멘트')
  expect(call('/cniconfiguration/id', '사용자 코멘트')).toBe('사용자 코멘트')
})

test('validation failure keeps focus and scroll on the invalid field', async () => {
  const c = context(); const submit = document.createElement('button'); const name = document.createElement('input'); document.body.append(submit, name); submit.focus()
  const scroll = { scrollTop: 137 }; c.$refs.content = { closest: () => scroll }; c.$refs.nameInput = name
  c.$refs.formRef.scrollToField = () => { scroll.scrollTop = 1 }
  const focusError = c.focusError
  c.focusError = (error) => {
    expect(c.loading).toBe(false)
    focusError(error)
  }
  c.$refs.formRef.validate.mockRejectedValue({ errorFields: [{ name: ['name'] }] })
  await c.submit(); await c.$nextTick(() => {})
  expect(document.activeElement).toBe(name); expect(scroll.scrollTop).toBe(1); expect(postAPI).not.toHaveBeenCalled(); submit.remove(); name.remove()
})

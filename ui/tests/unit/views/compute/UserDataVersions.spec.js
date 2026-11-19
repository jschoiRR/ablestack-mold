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

import { mount, flushPromises } from '@vue/test-utils'
import componentsUse from '@/core/lazy_lib/components_use'
import iconsUse from '@/core/lazy_lib/icons_use'
import Register from '@/views/compute/RegisterUserData'
import Versions from '@/views/compute/UserDataVersions'
import Content from '@/views/compute/UserDataContent'
import { cloneOwner, readCloneSource, findCloneAttempt, cloneDifference, cloneRecord, parseCloneRecord, readCloneNotes, saveCloneNote, cloneApis } from '@/utils/userDataVersions'
import { getAPI, postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
const source = { id: '11111111-1111-4111-8111-111111111111', name: 'source', account: 'owner', domainid: '22222222-2222-4222-8222-222222222222', userdata: Buffer.from('\ufeff#cloud-config\r\n# 한글 😀\r\n').toString('base64'), params: 'OLD' }
const target = { ...source, id: '33333333-3333-4333-8333-333333333333', name: 'source-v2', userdata: Buffer.from('#cloud-config\n# new\n').toString('base64'), params: 'NEW' }
const params = { name: target.name, userdata: target.userdata, params: target.params }
const listed = (rows = []) => ({ listuserdataresponse: { count: rows.length, userdata: rows } })
const notes = (rows = []) => ({ listannotationsresponse: { count: rows.length, annotation: rows } })
const flush = async () => { for (let n = 0; n < 24; n++) await Promise.resolve() }
function ctx (component = Register) {
  const c = { currentAction: { cloneUserData: true }, resource: { ...source }, tab: 'userdata.versions', $route: { name: 'userdata', path: '/userdata/' + source.id }, $router: { resolve: path => ({ href: '#' + path }), push: jest.fn() }, $refs: {}, $store: { getters: { apis: Object.fromEntries(cloneApis.map(api => [api, {}])), userInfo: { id: 'user', roletype: 'User' }, project: {} }, state: { user: { token: 'token' } } }, $t: key => key, $emit: jest.fn(), $nextTick: fn => Promise.resolve().then(fn) }
  Object.assign(c, component.data.call(c))
  Object.entries(component.methods).forEach(([key, fn]) => { c[key] = fn.bind(c) })
  Object.entries(component.computed).forEach(([key, fn]) => Object.defineProperty(c, key, { get: () => fn.call(c) }))
  c.form = { name: target.name, userdata: target.userdata, isbase64: true, params: ['NEW'] }; c.formRef = { value: { validate: jest.fn().mockResolvedValue(), scrollToField: jest.fn() } }; c.source = Object.freeze({ ...source })
  return c
}
beforeEach(() => jest.resetAllMocks())
test('owner is frozen and project clones never inherit ambient account or ignoreproject', () => {
  expect(cloneOwner(source)).toEqual({ account: source.account, domainid: source.domainid, ignoreproject: true })
  expect(cloneOwner({ ...source, projectid: target.id })).toEqual({ projectid: target.id })
  expect(() => cloneOwner({ id: source.id })).toThrow('mismatch')
})
test.each(['id', 'name', 'account', 'domainid', 'projectid'])('source %s mismatch fails closed', async field => {
  getAPI.mockResolvedValue(listed([{ ...source, [field]: 'other' }]))
  await expect(readCloneSource(getAPI, source)).rejects.toThrow('mismatch')
})
test('missing, corrupt source and denied API cannot become an empty clone', async () => {
  getAPI.mockResolvedValue(listed()); await expect(readCloneSource(getAPI, source)).rejects.toThrow('missing')
  getAPI.mockResolvedValue(listed([{ ...source, userdata: 'invalid!' }])); await expect(readCloneSource(getAPI, source)).rejects.toThrow()
  getAPI.mockRejectedValue({ response: { status: 432 } }); await expect(readCloneSource(getAPI, source)).rejects.toBeDefined()
})
test('byte comparison preserves BOM, CRLF and binary; params do not change duplicate policy', () => {
  expect(cloneDifference(source, source.userdata, 'OTHER')).toMatchObject({ changed: false, paramsChanged: true })
  expect(cloneDifference(source, '/wAB', 'OLD')).toMatchObject({ changed: true, after: 3, first: 1 })
})
test.each(['userdata', 'params', 'account', 'domainid', 'projectid'])('lost registration cannot reconcile mismatching %s', async field => {
  getAPI.mockResolvedValue(listed([{ ...target, [field]: 'other' }]))
  await expect(findCloneAttempt(getAPI, source, params)).rejects.toThrow('conflict')
})
test('reconciliation ignores substring names, verifies exact UUID/owner/bytes and fails on truncated lists', async () => {
  getAPI.mockResolvedValue(listed([{ ...target, name: target.name + '-other' }])); expect(await findCloneAttempt(getAPI, source, params)).toBeNull()
  getAPI.mockResolvedValue(listed([target])); expect((await findCloneAttempt(getAPI, source, params)).id).toBe(target.id)
  getAPI.mockResolvedValue({ listuserdataresponse: { count: 501, userdata: [target] } }); await expect(findCloneAttempt(getAPI, source, params)).rejects.toThrow('uncertain')
})
test('source metadata excludes bodies and tolerates malformed or foreign notes without trusting them', () => {
  const note = cloneRecord(source, target); const value = parseCloneRecord(note, target.id)
  expect(value).toMatchObject({ sourceid: source.id, targetid: target.id })
  expect(cloneRecord({ ...source, name: 'x'.repeat(255), params: 'x'.repeat(4096) }, { ...target, name: 'y'.repeat(255), params: 'y'.repeat(4096) }).length).toBeLessThanOrEqual(255)
  expect(note).not.toContain(source.userdata); expect(note).not.toContain(target.userdata)
  for (const invalid of ['normal comment', 'MOLD_USERDATA_VERSION/1\n{}', note.replace(source.id, 'javascript:alert(1)'), note.replace('"before":', '"before":-')]) expect(parseCloneRecord(invalid, target.id)).toBeNull()
  expect(parseCloneRecord(note, source.id)).toBeNull()
})
test('history traverses pages and rejects inconsistent counts instead of claiming no provenance', async () => {
  getAPI.mockResolvedValueOnce({ listannotationsresponse: { count: 2, annotation: [{ id: 'a' }] } }).mockResolvedValueOnce({ listannotationsresponse: { count: 2, annotation: [{ id: 'b' }] } })
  expect((await readCloneNotes(getAPI, target.id)).length).toBe(2)
  getAPI.mockResolvedValue({ listannotationsresponse: { count: 2, annotation: [] } }); await expect(readCloneNotes(getAPI, target.id)).rejects.toThrow('history.error')
})
test('source note retry reconciles an already saved or response-lost note without duplicate POST', async () => {
  const annotation = cloneRecord(source, target)
  getAPI.mockResolvedValue(notes([{ annotation }])); await saveCloneNote(getAPI, postAPI, target, annotation); expect(postAPI).not.toHaveBeenCalled()
  getAPI.mockResolvedValueOnce(notes()).mockResolvedValueOnce(notes([{ annotation }]))
  postAPI.mockRejectedValueOnce(new Error('lost'))
  await saveCloneNote(getAPI, postAPI, target, annotation); expect(postAPI).toHaveBeenCalledTimes(1)
})
test('scope change before note mutation prevents a late POST under another session', async () => {
  getAPI.mockResolvedValue(notes()); await saveCloneNote(getAPI, postAPI, target, 'note', () => false); expect(postAPI).not.toHaveBeenCalled()
})
test('clone seeds exact Base64 and variables, then explicit text editing normalizes line endings', async () => {
  const c = ctx(); c.source = null; getAPI.mockResolvedValue(listed([source])); await c.loadCloneSource()
  expect(c.form).toMatchObject({ name: 'source-v2', userdata: source.userdata, isbase64: true, params: ['OLD'] })
  c.editCloneText(); expect(c.form.userdata).toBe('\ufeff#cloud-config\n# 한글 😀\n'); expect(c.form.isbase64).toBe(false)
})
test.each(['project', 'user', 'resource', 'permission', 'disposed'])('late clone source after %s change never publishes', async kind => {
  const c = ctx(); c.source = null; let done; getAPI.mockReturnValue(new Promise(resolve => { done = resolve })); const p = c.loadCloneSource()
  if (kind === 'project') c.$store.getters.project.id = 'other'
  if (kind === 'user') c.$store.getters.userInfo.id = 'other'
  if (kind === 'resource') c.resource.id = target.id
  if (kind === 'permission') delete c.$store.getters.apis.addAnnotation
  if (kind === 'disposed') c.disposed = true
  done(listed([source])); await p; expect(c.source).toBeNull()
})
test('unchanged body or parameter-only version never posts', async () => {
  const c = ctx(); c.form.userdata = source.userdata; getAPI.mockResolvedValue(listed([source])); await c.submitClone()
  expect(c.error).toBe('message.userdata.clone.duplicate'); expect(postAPI).not.toHaveBeenCalled(); expect(c.cloneAttempt).toBeNull()
})
test('registration success verifies bytes and owner, records provenance and leaves a receipt', async () => {
  const c = ctx(); getAPI.mockImplementation(async (cmd, p) => cmd === 'listAnnotations' ? notes() : p.id ? listed([source]) : listed(postAPI.mock.calls.length ? [target] : []))
  postAPI.mockImplementation(async cmd => cmd === 'registerUserData' ? { registeruserdataresponse: { userdata: target } } : { addannotationresponse: { annotation: { id: 'note' } } })
  await c.handleSubmit(); expect(c.createdVersion.id).toBe(target.id); expect(c.noteSaved).toBe(true)
  expect(postAPI.mock.calls.map(call => call[0])).toEqual(['registerUserData', 'addAnnotation'])
  expect(postAPI.mock.calls[0][1]).toMatchObject({ account: source.account, domainid: source.domainid, ignoreproject: true, ...params })
  expect(c.$emit.mock.calls).toEqual([['refresh-data']]); expect(c.loading).toBe(false)
})
test('failed registration retains a frozen attempt; a late actual result is reconciled without a second registration', async () => {
  const c = ctx(); getAPI.mockImplementation(async (cmd, p) => p.id ? listed([source]) : listed())
  postAPI.mockRejectedValueOnce(new Error('Network Error')); await c.handleSubmit(); expect(c.cloneAttempt).toMatchObject(params); expect(c.createdVersion).toBeNull(); expect(c.form.userdata).toBe(target.userdata)
  getAPI.mockImplementation(async (cmd, p) => cmd === 'listAnnotations' ? notes() : p.id ? listed([source]) : listed([target]))
  postAPI.mockResolvedValue({ addannotationresponse: { annotation: { id: 'note' } } }); await c.handleSubmit()
  expect(c.createdVersion.id).toBe(target.id); expect(c.noteSaved).toBe(true)
  expect(postAPI.mock.calls.filter(call => call[0] === 'registerUserData')).toHaveLength(1)
})
test('confirmed missing attempt unlocks inputs and requires an explicit subsequent registration', async () => {
  const c = ctx(); c.cloneAttempt = params; getAPI.mockImplementation(async (cmd, p) => p.id ? listed([source]) : listed())
  await c.handleSubmit(); expect(c.cloneAttempt).toBeNull(); expect(c.error).toBe('message.userdata.clone.notfound'); expect(postAPI).not.toHaveBeenCalled()
})
test('note failure retries only the note, never the already created version', async () => {
  const c = ctx(); c.createdVersion = target; c.note = cloneRecord(source, target); getAPI.mockResolvedValue(notes()); postAPI.mockRejectedValue(new Error('503'))
  await c.handleSubmit(); expect(c.noteSaved).toBe(false); expect(c.createdVersion.id).toBe(target.id)
  postAPI.mockResolvedValue({ addannotationresponse: { annotation: { id: 'note' } } }); await c.handleSubmit(); expect(c.noteSaved).toBe(true)
  expect(postAPI.mock.calls.every(call => call[0] === 'addAnnotation')).toBe(true)
})
test('a confirmed concurrent name conflict unlocks the retained draft without posting again', async () => {
  const c = ctx(); c.cloneAttempt = params; const before = { ...c.form }
  getAPI.mockImplementation(async (cmd, p) => p.id ? listed([source]) : listed([{ ...target, userdata: 'eA==' }]))
  await c.handleSubmit(); expect(c.cloneAttempt).toBeNull(); expect(c.form).toEqual(before)
  expect(c.error).toBe('message.userdata.clone.name.conflict'); expect(postAPI).not.toHaveBeenCalled()
})
test('pending clone blocks close, unload and duplicate submissions; revoked capability prevents mutation', async () => {
  const c = ctx(); let done; getAPI.mockReturnValue(new Promise(resolve => { done = resolve }))
  const p = c.handleSubmit(); await flush(); c.handleSubmit(); c.closeAction(); expect(c.blocksNavigation).toBe(true); expect(c.$emit).not.toHaveBeenCalled()
  const event = { preventDefault: jest.fn() }; c.beforeUnload(event); expect(event.preventDefault).toHaveBeenCalled()
  delete c.$store.getters.apis.registerUserData; done(listed([source])); await p; expect(postAPI).not.toHaveBeenCalled()
})
test('version tab verifies current entity, deduplicates advisory records and clears them on permission errors', async () => {
  const c = ctx(Versions); const annotation = cloneRecord(target, source)
  getAPI.mockImplementation(async cmd => cmd === 'listUserData' ? listed([source]) : notes([{ annotation }, { annotation }, { annotation: '<script>ignored</script>' }]))
  await c.fetchData(); expect(c.records).toHaveLength(1)
  getAPI.mockRejectedValue({ response: { status: 432 } }); await c.fetchData(); expect(c.records).toEqual([]); expect(c.error).toBe('label.forbidden')
})
test('version tab late records cannot cross a project scope', async () => {
  const c = ctx(Versions); let done
  getAPI.mockImplementation(cmd => cmd === 'listUserData' ? Promise.resolve(listed([source])) : new Promise(resolve => { done = resolve }))
  const p = c.fetchData(); await flush(); c.$store.getters.project.id = 'other'; done(notes([{ annotation: cloneRecord(target, source) }])); await p; expect(c.records).toEqual([])
})
test('reused content preview preserves exact bytes and performs no source lookup', async () => {
  const c = ctx(Content); c.tab = 'userdata.content'; c.previewEncoded = source.userdata
  await c.fetchData(); expect(c.content.text).toBe('\ufeff#cloud-config\r\n# 한글 😀\r\n'); expect(c.status).toBe('ready'); expect(getAPI).not.toHaveBeenCalled()
  c.previewEncoded = '/wAB'; await c.fetchData(); expect(c.mode).toBe('base64'); expect(c.content.text).toBeNull()
  c.previewEncoded = '!'; await c.fetchData(); expect(c.status).toBe('invalid'); expect(c.content).toBeNull()
})

test('unnamed library detail route retains the userdata API and clone contract', async () => {
  const c = ctx(); c.$route.name = undefined; c.$route.meta = { name: 'userdata' }
  expect(c.isUserData).toBe(true); expect(c.cloneMode).toBe(true); expect(c.apiName).toBe('registerUserData')
  getAPI.mockResolvedValue(listed([source])); c.source = null; await c.loadCloneSource()
  expect(c.form.userdata).toBe(source.userdata); expect(c.form.params).toEqual(['OLD'])
})

function mountedOptions (component, route) {
  const c = ctx(component)
  return {
    props: { resource: source, currentAction: { cloneUserData: true }, tab: 'userdata.versions' },
    global: {
      plugins: [componentsUse, iconsUse],
      directives: { focus: () => {} },
      mocks: { $route: route, $router: c.$router, $store: c.$store, $t: key => key, $getApiParams: () => Object.fromEntries(['name', 'userdata', 'params', 'domainid', 'account', 'cniconfig'].map(key => [key, { description: key }])) },
      stubs: { MoldDialog: { template: '<section><header><slot name="title" /></header><main><slot /></main><footer><slot name="footer" /></footer></section>' }, TooltipLabel: { props: ['title'], template: '<label>{{ title }}</label>' }, UserDataContent: true, 'router-link': { template: '<a><slot /></a>' } }
    }
  }
}
test('actual production component registry renders clone form on an unnamed detail route', async () => {
  getAPI.mockImplementation(async cmd => cmd === 'listCapabilities' ? { listcapabilitiesresponse: { capability: {} } } : listed([source]))
  const wrapper = mount(Register, mountedOptions(Register, { path: '/userdata/' + source.id, meta: { name: 'userdata' } }))
  await flushPromises()
  expect(wrapper.find('header').text()).toContain('label.userdata.clone')
  expect(wrapper.find('textarea').element.value).toBe(source.userdata)
  expect(wrapper.find('input[type=checkbox]').element.checked).toBe(true)
  expect(wrapper.text()).not.toContain('label.cniconfiguration')
  expect(wrapper.find('footer').text()).toBe('label.cancellabel.userdata.clone.create')
  await wrapper.find('textarea').setValue('!invalid')
  expect(wrapper.find('main').text()).not.toContain('message.userdata.clone.duplicate')
  wrapper.unmount()
})
test('actual production registry renders visible empty provenance feedback', async () => {
  getAPI.mockImplementation(async cmd => cmd === 'listUserData' ? listed([source]) : notes())
  const wrapper = mount(Versions, mountedOptions(Versions, { path: '/userdata/' + source.id, meta: { name: 'userdata' } }))
  await flushPromises()
  expect(wrapper.find('.ant-alert').text()).toContain('message.userdata.clone.history.empty')
  expect(wrapper.find('a-empty').exists()).toBe(false)
  wrapper.unmount()
})
test('long source name truncation never splits a Unicode surrogate pair', async () => {
  const c = ctx(); c.source = null; c.resource.name = 'x'.repeat(246) + '😀ab'
  getAPI.mockResolvedValue(listed([{ ...source, name: c.resource.name }]))
  await c.loadCloneSource(); expect(c.form.name).toBe('x'.repeat(246) + '-v2')
})
test('completed receipt restores focus to close when the submit button was removed', async () => {
  const c = ctx(); c.createdVersion = target
  const button = document.createElement('button'); document.body.appendChild(button); c.$refs.closeButton = { $el: button }
  c.restoreFormPosition({ focus: { isConnected: false } }); await flush()
  expect(document.activeElement).toBe(button); button.remove()
})
test('clone links close the action before navigation and are inert while pending', () => {
  const c = ctx(); const event = { preventDefault: jest.fn(), button: 0 }; const path = '/userdata/' + target.id
  c.loading = true; c.openCloneRoute(event, path); expect(c.$router.push).not.toHaveBeenCalled(); expect(c.$emit).not.toHaveBeenCalled()
  c.loading = false; c.openCloneRoute(event, path); expect(c.$emit).toHaveBeenCalledWith('close-action'); expect(c.$router.push).toHaveBeenCalledWith(path)
  expect(c.$emit.mock.invocationCallOrder[0]).toBeLessThan(c.$router.push.mock.invocationCallOrder[0])
})

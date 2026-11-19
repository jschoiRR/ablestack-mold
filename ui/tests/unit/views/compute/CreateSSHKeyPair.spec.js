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
import CreateSSHKeyPair from '@/views/compute/CreateSSHKeyPair'
import { postAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
let ctx, anchor
const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve() }
beforeEach(() => {
  jest.clearAllMocks()
  anchor = { click: jest.fn() }
  jest.spyOn(document, 'createElement').mockReturnValue(anchor)
  ctx = { $t: key => key, $store: { getters: { userInfo: { roletype: 'Admin', account: 'admin', domain: 'ROOT' }, project: { id: 'project-1', name: 'Project 1' }, apis: { createSSHKeyPair: {}, registerSSHKeyPair: {} } } }, currentAction: { api: 'registerSSHKeyPair' }, $nextTick: callback => Promise.resolve().then(callback), $refs: { name: {}, publickey: {}, domainid: {}, account: {} }, $emit: jest.fn(), $notifyError: jest.fn(), $message: { success: jest.fn() }, $notification: { info: jest.fn() } }
  Object.assign(ctx, CreateSSHKeyPair.data.call(ctx))
  Object.entries(CreateSSHKeyPair.computed).forEach(([key, getter]) => Object.defineProperty(ctx, key, { get: () => getter.call(ctx) }))
  URL.createObjectURL = jest.fn().mockReturnValue('blob:test-key'); URL.revokeObjectURL = jest.fn()
  for (const mixin of CreateSSHKeyPair.mixins) Object.entries(mixin.methods).forEach(([key, method]) => { ctx[key] = method.bind(ctx) })
  Object.entries(CreateSSHKeyPair.methods).forEach(([key, method]) => { ctx[key] = method.bind(ctx) })
  ctx.form = { name: 'retained-key', publickey: 'invalid-key', domainid: 1, account: 'owner-1' }
  ctx.domains = [{ id: null }, { id: 'domain-1', path: '/domain-1' }]
  ctx.selectedDomain = ctx.domains[1]
  ctx.formRef = { value: { validate: jest.fn().mockResolvedValue(), scrollToField: jest.fn() } }
  postAPI.mockResolvedValue({ registersshkeypairresponse: { keypair: { id: 'registered-id' } } })
})
afterEach(() => { jest.restoreAllMocks() })
test.each(['invalid public key', 'duplicate name', 'HTTP 503', 'connection lost'])('%s leaves the registration form, owner selection and project intact', async reason => {
  const failure = new Error(reason); postAPI.mockRejectedValueOnce(failure)
  const form = { ...ctx.form }; const selected = ctx.selectedDomain
  await ctx.handleSubmit({ preventDefault: jest.fn() })
  expect(ctx.form).toEqual(form)
  expect(ctx.selectedDomain).toBe(selected)
  expect(ctx.$store.getters.project.id).toBe('project-1')
  expect(ctx.loading).toBe(false)
  expect(ctx.isSubmitted).toBe(false)
  expect(ctx.privateKey).toBe('')
  expect(ctx.$notifyError).toHaveBeenCalledWith(failure)
  expect(postAPI.mock.calls[0][2]).toEqual({ preserveOnFailure: true })
  expect(ctx.$message.success).not.toHaveBeenCalled()
  expect(ctx.$emit).not.toHaveBeenCalled()
})
test('the same form can be corrected and registered after failure', async () => {
  postAPI.mockRejectedValueOnce(new Error('invalid'))
  await ctx.handleSubmit()
  ctx.form.publickey = 'ssh-ed25519 valid-key'
  await ctx.handleSubmit()
  expect(postAPI).toHaveBeenNthCalledWith(2, 'registerSSHKeyPair', { name: 'retained-key', publickey: 'ssh-ed25519 valid-key', domainid: 'domain-1', account: 'owner-1' }, { preserveOnFailure: true })
  expect(ctx.$message.success).toHaveBeenCalledTimes(1)
  expect(ctx.$emit.mock.calls).toEqual([['refresh-data'], ['close-action']])
  expect(ctx.loading).toBe(false)
})
test('registration success refreshes before closing and never creates a private-key result', async () => {
  await ctx.handleSubmit({ name: 'form-finish-values' })
  expect(postAPI).toHaveBeenCalledTimes(1)
  expect(ctx.$emit.mock.calls).toEqual([['refresh-data'], ['close-action']])
  expect(ctx.privateKey).toBe('')
  expect(ctx.isSubmitted).toBe(false)
})
test('two submissions during asynchronous validation produce one request', async () => {
  let finish
  ctx.formRef.value.validate.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  const pending = ctx.handleSubmit(); ctx.handleSubmit(); ctx.handleSubmit({ preventDefault: jest.fn() })
  expect(ctx.loading).toBe(true)
  expect(ctx.formRef.value.validate).toHaveBeenCalledTimes(1)
  expect(postAPI).not.toHaveBeenCalled()
  finish(); await pending
  expect(postAPI).toHaveBeenCalledTimes(1)
})
test.each([true, false])('pending API request blocks repeats and unlocks after success=%s', async success => {
  let finish, fail
  postAPI.mockImplementationOnce(() => new Promise((resolve, reject) => { finish = resolve; fail = reject }))
  const pending = ctx.handleSubmit(); await flush()
  ctx.handleSubmit(); expect(postAPI).toHaveBeenCalledTimes(1); expect(ctx.loading).toBe(true)
  if (success) finish({ registersshkeypairresponse: { keypair: {} } }); else fail(new Error('temporary failure'))
  await pending; expect(ctx.loading).toBe(false)
  expect(ctx.$emit).toHaveBeenCalledTimes(success ? 2 : 0)
})
test('required-field validation scrolls to the field and unlocks retry without an API call', async () => {
  ctx.formRef.value.validate.mockRejectedValueOnce({ errorFields: [{ name: ['name'] }] })
  await ctx.handleSubmit()
  expect(postAPI).not.toHaveBeenCalled()
  expect(ctx.formRef.value.scrollToField).toHaveBeenCalledWith(['name'])
  expect(ctx.loading).toBe(false)
  await ctx.handleSubmit(); expect(postAPI).toHaveBeenCalledTimes(1)
})
test('unexpected validation error is reported without losing the form', async () => {
  const failure = new Error('validation unavailable'); ctx.formRef.value.validate.mockRejectedValueOnce(failure)
  await ctx.handleSubmit()
  expect(ctx.$notifyError).toHaveBeenCalledWith(failure)
  expect(ctx.form.name).toBe('retained-key')
  expect(ctx.loading).toBe(false)
  expect(postAPI).not.toHaveBeenCalled()
})
test('hidden owner fields are omitted while visible input is preserved', async () => {
  ctx.$refs.account = null; ctx.$refs.domainid = null
  await ctx.handleSubmit()
  expect(postAPI).toHaveBeenCalledWith('registerSSHKeyPair', { name: 'retained-key', publickey: 'invalid-key' }, { preserveOnFailure: true })
  expect(ctx.form.account).toBe('owner-1')
})
test('default domain does not add an owner domain to registration', async () => {
  ctx.form.domainid = 0
  await ctx.handleSubmit()
  expect(postAPI.mock.calls[0][1]).not.toHaveProperty('domainid')
})
test('new key generation retains the private-key result and existing download/copy/close behavior', async () => {
  ctx.form.publickey = ''
  ctx.currentAction.api = 'createSSHKeyPair'
  const key = 'unit-test-private-key\n'
  postAPI.mockResolvedValueOnce({ createsshkeypairresponse: { keypair: { privatekey: key } } })
  await ctx.handleSubmit()
  expect(postAPI).toHaveBeenCalledWith('createSSHKeyPair', { name: 'retained-key', domainid: 'domain-1', account: 'owner-1' }, { preserveOnFailure: true })
  expect(ctx.isSubmitted).toBe(true)
  expect(ctx.loading).toBe(false)
  expect(ctx.$emit.mock.calls).toEqual([['refresh-data']])
  expect(ctx.privateKey).toBe(key)
  expect(ctx.result).not.toHaveProperty('privatekey')
  ctx.downloadKey(); expect(anchor.click).toHaveBeenCalledTimes(1)
  expect(anchor.href).toBe('blob:test-key')
  expect(anchor.download).toBe('retained-key.key')
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test-key')
  ctx.notifyCopied(); expect(ctx.resultFeedback).toBe('message.success.copy.clipboard')
  expect(ctx.savedAcknowledged).toBe(false)
  ctx.handleSubmit(); expect(postAPI).toHaveBeenCalledTimes(1)
  ctx.closeAction(); expect(ctx.confirmDiscard).toBe(true)
  expect(ctx.$emit).not.toHaveBeenCalledWith('close-action')
  ctx.continueSaving(); expect(ctx.privateKey).toBe(key)
  ctx.savedAcknowledged = true; ctx.closeAction()
  expect(ctx.$emit).toHaveBeenLastCalledWith('close-action'); expect(ctx.privateKey).toBe('')
})
test('new key generation failure retains inputs and can be retried', async () => {
  ctx.currentAction.api = 'createSSHKeyPair'
  delete ctx.form.publickey; postAPI.mockRejectedValueOnce(new Error('duplicate'))
  await ctx.handleSubmit()
  expect(ctx.form.name).toBe('retained-key')
  expect(ctx.isSubmitted).toBe(false)
  expect(ctx.privateKey).toBe('')
  expect(ctx.loading).toBe(false)
  expect(ctx.$emit).not.toHaveBeenCalled()
  postAPI.mockResolvedValueOnce({ createsshkeypairresponse: { keypair: { privatekey: 'unit-only-key' } } })
  await ctx.handleSubmit(); expect(ctx.isSubmitted).toBe(true)
})

test('register-only permission cannot fall back to creation when public key is empty', async () => {
  delete ctx.$store.getters.apis.createSSHKeyPair
  ctx.form.publickey = ''
  ctx.formRef.value.validate.mockRejectedValueOnce({ errorFields: [{ name: ['publickey'] }] })
  await ctx.handleSubmit()
  expect(postAPI).not.toHaveBeenCalled()
  expect(ctx.mode).toBe('register')
})
test('create-only mode sends only create even when a stale public key is present', async () => {
  delete ctx.$store.getters.apis.registerSSHKeyPair
  ctx.currentAction.api = 'createSSHKeyPair'
  postAPI.mockResolvedValueOnce({ createsshkeypairresponse: { keypair: { name: 'retained-key', privatekey: 'unit-only-key', fingerprint: 'fingerprint', account: 'owner-1', domain: 'domain-1' } } })
  await ctx.handleSubmit()
  expect(postAPI.mock.calls[0][0]).toBe('createSSHKeyPair')
  expect(postAPI.mock.calls[0][1]).not.toHaveProperty('publickey')
  expect(ctx.resultSummary).toContainEqual({ label: 'label.project', value: 'Project 1' })
})
test('missing permission blocks submit and revocation during validation blocks the request', async () => {
  delete ctx.$store.getters.apis.registerSSHKeyPair
  await ctx.handleSubmit(); expect(ctx.error).toBe('message.ssh.keypair.permission'); expect(postAPI).not.toHaveBeenCalled()
  ctx.$store.getters.apis.registerSSHKeyPair = {}
  let finish; ctx.formRef.value.validate.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  const pending = ctx.handleSubmit(); delete ctx.$store.getters.apis.registerSSHKeyPair; finish(); await pending
  expect(postAPI).not.toHaveBeenCalled(); expect(ctx.loading).toBe(false)
})
test('copy failure is visible and never implies private-key storage', () => {
  ctx.copyFailed(); expect(ctx.error).toBe('message.ssh.privatekey.copy.failed'); expect(ctx.resultFeedback).toBe(''); expect(ctx.savedAcknowledged).toBe(false)
})
test('download failure is visible and object URLs are released', () => {
  ctx.privateKey = 'unit-only-key'; anchor.click.mockImplementationOnce(() => { throw new Error('download unavailable') })
  ctx.downloadKey(); expect(ctx.error).toBe('message.ssh.privatekey.download.failed'); expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test-key'); expect(ctx.savedAcknowledged).toBe(false)
})
test('unsaved result prevents refresh until acknowledgement and discard clears its memory', () => {
  ctx.isSubmitted = true; ctx.privateKey = 'unit-only-key'
  const event = { preventDefault: jest.fn() }; ctx.beforeUnload(event); expect(event.preventDefault).toHaveBeenCalledTimes(1)
  ctx.savedAcknowledged = true; ctx.beforeUnload(event); expect(event.preventDefault).toHaveBeenCalledTimes(1)
  ctx.finishClose(); expect(ctx.privateKey).toBe(''); expect(ctx.$emit).toHaveBeenCalledWith('close-action')
})
test('late create response after unmount cannot retain or display a private key', async () => {
  ctx.currentAction.api = 'createSSHKeyPair'
  let finish; postAPI.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  const pending = ctx.handleSubmit(); await flush(); CreateSSHKeyPair.beforeUnmount.call(ctx)
  finish({ createsshkeypairresponse: { keypair: { privatekey: 'unit-only-key' } } }); await pending
  expect(ctx.privateKey).toBe(''); expect(ctx.isSubmitted).toBe(false); expect(ctx.$emit).not.toHaveBeenCalled()
})

test('pending submission also warns on browser refresh instead of implying request cancellation', () => {
  ctx.loading = true
  const event = { preventDefault: jest.fn() }; ctx.beforeUnload(event)
  expect(event.preventDefault).toHaveBeenCalledTimes(1); expect(event.returnValue).toBe('')
})

test('first invalid field receives focus only after loading has been released', async () => {
  const focus = jest.fn(() => { expect(ctx.loading).toBe(false) }); ctx.$refs.nameInput = { focus }
  ctx.formRef.value.validate.mockRejectedValueOnce({ errorFields: [{ name: ['name'] }] })
  await ctx.handleSubmit(); await flush()
  expect(focus).toHaveBeenCalledTimes(1); expect(ctx.formRef.value.scrollToField).toHaveBeenCalledWith(['name'])
})

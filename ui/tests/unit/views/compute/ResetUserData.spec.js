// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with this
// work for additional information regarding copyright ownership. The ASF
// licenses this file to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the License.
// You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// License for the specific language governing permissions and limitations
// under the License.
import { shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { Buffer } from 'buffer'
import Reset from '@/views/compute/ResetUserData.vue'
import { getAPI, postAPI } from '@/api'
import { decodeResetUserData, resetUserDataPayload, resetUserDataValues, userDataResetSupport } from '@/utils/userDataReset'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
jest.mock('@/store', () => ({ getters: { apis: {}, userInfo: {}, project: {} } }))
const encode = text => Buffer.from(text, 'utf8').toString('base64')
const flush = async () => { for (let i = 0; i < 16; i++) { await Promise.resolve(); await nextTick() } }
const resource = { id: 'vm', account: 'owner', domainid: 'domain', templateid: 'image', zoneid: 'zone', state: 'Stopped', nic: [{ networkid: 'network', isdefault: true }] }
const template = { id: 'image', account: 'owner', domainid: 'domain' }
const library = { id: 'library', name: 'off-page library', params: 'VAR1,B' }
function draft (overrides = {}) {
  return { resource, editCurrent: true, template: {}, form: { userdataid: 'library', userdata: '수동 본문' }, userdataTabKey: 'userdataregistered', userDataParams: [{ key: 'VAR1' }], userDataValues: { VAR1: '한글, 값=1' }, templateUserDataParams: [{ key: 'T' }], templateUserDataValues: { T: 'template' }, ...overrides }
}
function mount (row = {}, image = template, props = {}) {
  getAPI.mockImplementation((command, params) => {
    if (command === 'listVirtualMachines') return Promise.resolve({ listvirtualmachinesresponse: { virtualmachine: [{ ...resource, ...row }] } })
    if (command === 'listAutoScaleVmProfiles') return Promise.resolve({ listautoscalevmprofilesresponse: { autoscalevmprofile: [{ ...resource, id: 'profile', ...row }] } })
    if (command === 'listTemplates') return Promise.resolve({ listtemplatesresponse: { template: [image] } })
    if (command === 'listNetworks') return Promise.resolve({ listnetworksresponse: { network: [{ id: params.id, service: [{ name: 'UserData', provider: [{ name: 'ConfigDrive' }] }] }] } })
    return Promise.resolve({ listuserdataresponse: { userdata: [params.id === 'template-data' ? { id: 'template-data', params: 'T', userdata: encode('#cloud-config\nT') } : library] } })
  })
  return shallowMount(Reset, { props: { resource, ...props }, global: { mocks: { $t: k => k, $store: { getters: { apis: { listUserData: {} } } }, $toBase64AndURIEncoded: encode, $message: { success: jest.fn() }, $notifyError: jest.fn() }, stubs: { MoldDialog: { template: '<div><slot/><slot name="footer"/></div>' }, 'a-spin': { template: '<div><slot/></div>' }, 'a-form': { template: '<form><slot/></form>' }, 'a-alert': { template: '<p><slot name="description"/></p>' } } } })
}
beforeEach(() => { jest.clearAllMocks(); postAPI.mockResolvedValue({ resetuserdataforvirtualmachineresponse: {} }) })

test.each(['Running', 'Starting', 'Stopping', 'Destroyed'])('fresh %s state blocks reset', state => {
  expect(userDataResetSupport({ ...resource, state }, [])).toBe('message.userdata.reset.unsupported.state')
})
test('unsupported L2 and secondary-only ConfigDrive cannot reset; VR secondary can', () => {
  const vm = { ...resource, nic: [{ networkid: 'network', isdefault: false }] }
  const net = { id: 'network', service: [{ name: 'UserData', provider: [{ name: 'ConfigDrive' }] }] }
  expect(userDataResetSupport(vm, [net])).toBe('message.userdata.reset.unsupported.network')
  expect(userDataResetSupport(resource, [net])).toBe('message.userdata.reset.delivery.configdrive')
  net.service[0].provider[0].name = 'VirtualRouter'
  expect(userDataResetSupport(vm, [net])).toBe('message.userdata.reset.delivery.provider')
  expect(userDataResetSupport(resource, [])).toBe('message.userdata.reset.unsupported.network')
})
test('unsupported VM remains readable and keep is a no-op even if change is forced', async () => {
  const w = mount({ nic: [] }); await flush()
  expect(w.vm.currentLoaded).toBe(true); expect(w.vm.resetUnsupported).toBe(true)
  w.vm.editCurrent = true; w.vm.handleSubmit(); expect(postAPI).not.toHaveBeenCalled()
  w.vm.editCurrent = false; w.vm.handleSubmit(); expect(w.emitted('close-action')).toBeTruthy(); w.unmount()
})
test('real command-shaped 530 response preserves the provider recovery reason', async () => {
  const w = mount({ userdata: encode('current') }); await flush(); w.vm.editCurrent = true
  postAPI.mockRejectedValueOnce({ message: 'Request failed with status code 530', response: { data: { resetuserdataforvirtualmachineresponse: { errorcode: 530, errortext: 'Cloud data unchanged; provider restoration unconfirmed' } } } })
  await w.vm.handleSubmit(); expect(w.vm.submitError).toBe('Cloud data unchanged; provider restoration unconfirmed'); expect(w.vm.form.userdata).toBe('current'); w.unmount()
})
test('lost transport response is an unknown outcome, not a confirmed server failure', async () => {
  const w = mount({ userdata: encode('current') }); await flush(); w.vm.editCurrent = true
  postAPI.mockRejectedValueOnce(new Error('Network Error'))
  await w.vm.handleSubmit(); expect(w.vm.submitError).toBe('message.userdata.reset.outcome.unknown'); expect(w.vm.form.userdata).toBe('current'); expect(w.emitted('close-action')).toBeFalsy(); w.unmount()
})

test('current keep is a no-op, not an empty reset', () => {
  expect(resetUserDataPayload(draft({ editCurrent: false }), encode)).toBeNull()
})
test.each(['userdataregistered', 'userdatatext'])('only active %s input is serialized despite a hidden draft', mode => {
  const p = resetUserDataPayload(draft({ userdataTabKey: mode }), encode)
  if (mode === 'userdataregistered') { expect(p.userdataid).toBe('library'); expect(p.userdata).toBeUndefined(); expect(p['userdatadetails[0].VAR1']).toBe('한글, 값=1') } else { expect(p.userdata).toBe(encode('수동 본문')); expect(p.userdataid).toBeUndefined(); expect(Object.keys(p).some(k => k.startsWith('userdatadetails'))).toBe(false) }
})
test.each([undefined, '', '0'])('None %s clears without sending a fake UUID or stale custom variables', id => {
  expect(resetUserDataPayload(draft({ form: { userdataid: id, userdata: 'hidden' } }), encode)).toEqual({ id: 'vm', userdata: '' })
})
test.each(['ALLOWOVERRIDE', 'APPEND', 'DENYOVERRIDE'])('%s template default has no inactive custom payload', policy => {
  const vm = draft({ template: { userdataid: 'template-data' }, userdataDefaultOverridePolicy: policy, doUserdataOverride: false, doUserdataAppend: false })
  expect(resetUserDataPayload(vm, encode)).toEqual({ id: 'vm', userdata: '', 'userdatadetails[0].T': 'template' })
})
test('ALLOW manual override excludes template and library variables', () => {
  const vm = draft({ template: { userdataid: 'template-data' }, userdataDefaultOverridePolicy: 'ALLOWOVERRIDE', doUserdataOverride: true, userdataTabKey: 'userdatatext' })
  expect(resetUserDataPayload(vm, encode)).toEqual({ id: 'vm', userdata: encode('수동 본문') })
})
test.each(['userdataregistered', 'userdatatext'])('ALLOW empty %s returns to template and preserves template variables', mode => {
  const vm = draft({ template: { userdataid: 'template-data' }, userdataDefaultOverridePolicy: 'ALLOWOVERRIDE', doUserdataOverride: true, userdataTabKey: mode, form: {} })
  expect(resetUserDataPayload(vm, encode)).toEqual({ id: 'vm', userdata: '', 'userdatadetails[0].T': 'template' })
})
test('APPEND library combines only effective variable definitions and deduplicates keys', () => {
  const vm = draft({ template: { userdataid: 'template-data' }, userdataDefaultOverridePolicy: 'APPEND', doUserdataAppend: true, templateUserDataParams: [{ key: 'T' }, { key: 'VAR1' }] })
  expect(resetUserDataPayload(vm, encode)).toEqual({ id: 'vm', userdataid: 'library', 'userdatadetails[0].T': 'template', 'userdatadetails[1].VAR1': '한글, 값=1' })
})
test('legacy details preserve punctuation and Korean, object details are filtered', () => {
  expect(resetUserDataValues('{VAR1=한글, x=1 {value}, B=two=2}', ['VAR1', 'B'])).toEqual({ VAR1: '한글, x=1 {value}', B: 'two=2' })
  expect(resetUserDataValues({ VAR1: '한글', OLD: 'stale' }, ['VAR1'])).toEqual({ VAR1: '한글' })
  expect(() => resetUserDataValues('{VAR1=a, VAR1=b}', ['VAR1'])).toThrow('Ambiguous')
})
test('UTF-8 and literal percent round trip, binary is not silently corrupted', () => {
  expect(decodeResetUserData(encode('한글 % +\n'))).toBe('한글 % +\n')
  expect(() => decodeResetUserData('/w==')).toThrow('UTF-8')
})
test('valid variable names that match object properties remain ordinary string values', () => {
  const values = resetUserDataValues('{__proto__=한글, constructor=value}', ['__proto__', 'constructor'])
  const vm = draft({ userDataParams: [{ key: '__proto__' }, { key: 'constructor' }], userDataValues: values })
  expect(resetUserDataPayload(vm, encode)['userdatadetails[0].__proto__']).toBe('한글')
  expect(resetUserDataPayload(vm, encode)['userdatadetails[1].constructor']).toBe('value')
})
test('fresh current library, off-page variables and policy load before editing, keep sends no mutation', async () => {
  const w = mount({ userdataid: 'library', userdataname: library.name, userdata: encode('#cloud-config\nVAR1'), userdatadetails: '{VAR1=한글, 값=1, B=two}' }); await flush()
  expect(getAPI).toHaveBeenCalledWith('listVirtualMachines', expect.objectContaining({ id: 'vm', userdata: true, ignoreproject: true }), expect.any(Object))
  expect(w.vm.currentLoaded).toBe(true); expect(w.vm.form.userdataid).toBe('library'); expect(w.vm.userDataValues).toEqual({ VAR1: '한글, 값=1', B: 'two' })
  expect(w.vm.editCurrent).toBe(false); w.vm.handleSubmit(); expect(postAPI).not.toHaveBeenCalled(); expect(w.emitted('close-action')).toHaveLength(1); w.unmount()
})
test('mode round trips preserve drafts and verified selection remount preserves restored variables', async () => {
  const w = mount({ userdataid: 'library', userdatadetails: '{VAR1=한글, B=two}' }); await flush()
  w.vm.updateUserData('library', library); w.vm.userDataValues.VAR1 = '수정'
  w.vm.onUserdataTabChange('userdatatext', 'userdataTabKey'); w.vm.form.userdata = 'draft'
  w.vm.onUserdataTabChange('userdataregistered', 'userdataTabKey'); w.vm.updateUserData('library', library)
  expect(w.vm.userDataValues.VAR1).toBe('수정'); expect(w.vm.form.userdata).toBe('draft')
  w.vm.updateUserData('new', { id: 'new', params: 'NEW' }); expect(w.vm.userDataValues).toEqual({}); w.unmount()
})
test('manual current content restores as text without a library ID', async () => {
  const w = mount({ userdata: encode('#cloud-config\n한글') }); await flush()
  expect(w.vm.userdataTabKey).toBe('userdatatext'); expect(w.vm.form.userdata).toBe('#cloud-config\n한글'); w.unmount()
})
test('APPEND current combined body is visible, never used as a new append input', async () => {
  const w = mount({ userdata: encode('combined') }, { ...template, userdataid: 'template-data', userdatapolicy: 'APPEND' }); await flush()
  expect(w.vm.currentText).toBe('combined'); expect(w.vm.appendManualCurrent).toBe(true); expect(w.vm.form.userdata).toBe(''); expect(w.vm.editCurrent).toBe(false); w.unmount()
})
test('template-only variables load even without a VM library ID', async () => {
  const w = mount({ userdata: encode('#cloud-config\nT'), userdatadetails: '{T=템플릿 한글}' }, { ...template, userdataid: 'template-data', userdatapolicy: 'DENYOVERRIDE' }); await flush()
  expect(w.vm.templateUserDataValues).toEqual({ T: '템플릿 한글' }); expect(w.vm.currentValues.T).toBe('템플릿 한글'); expect(w.vm.currentKind).toBe('label.userdata.template.default'); w.unmount()
})
test('current/policy failures block submit and retry recovers', async () => {
  const w = mount(); getAPI.mockRejectedValue(new Error('503')); await flush()
  expect(w.vm.templateError).toBe(true); w.vm.handleSubmit(); expect(postAPI).not.toHaveBeenCalled()
  getAPI.mockImplementation(command => Promise.resolve(command === 'listVirtualMachines' ? { listvirtualmachinesresponse: { virtualmachine: [resource] } } : command === 'listNetworks' ? { listnetworksresponse: { network: [{ id: 'network', service: [] }] } } : { listtemplatesresponse: { template: [template] } }))
  await w.vm.fetchTemplateData(); expect(w.vm.currentLoaded).toBe(true); expect(w.vm.templateError).toBe(false); w.unmount()
})
test('failed mutation keeps the dialog, active mode and drafts; retry submits one input', async () => {
  const w = mount({ userdata: encode('current') }); await flush(); w.vm.editCurrent = true; w.vm.form.userdata = '수정'
  postAPI.mockRejectedValueOnce({ response: { data: { errorresponse: { errortext: '503 test' } } } })
  await w.vm.handleSubmit(); expect(w.vm.submitError).toBe('503 test'); expect(w.vm.form.userdata).toBe('수정'); expect(w.emitted('close-action')).toBeUndefined()
  expect(w.vm.$notifyError).not.toHaveBeenCalled()
  await w.vm.handleSubmit(); expect(postAPI).toHaveBeenLastCalledWith('resetUserDataForVirtualMachine', { id: 'vm', userdata: encode('수정') }, { preserveOnFailure: true }); w.unmount()
})
test('late current read after unmount cannot initialize a closed dialog', async () => {
  let finish; const w = mount(); getAPI.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  const promise = w.vm.fetchTemplateData(); const vm = w.vm; w.unmount(); finish({ listvirtualmachinesresponse: { virtualmachine: [resource] } }); await promise
  expect(vm.currentLoaded).toBe(false)
})
test('AutoScale reads the actual profile rather than the parent group current values', async () => {
  const w = mount({ userdataid: 'library', userdatadetails: '{VAR1=profile}' }, template, { resource: { ...resource, resetUserDataApiName: 'updateAutoScaleVmProfile', resetUserDataResourceId: 'profile' } }); await flush()
  expect(getAPI).toHaveBeenCalledWith('listAutoScaleVmProfiles', expect.objectContaining({ id: 'profile' }), expect.any(Object)); expect(w.vm.userDataValues.VAR1).toBe('profile'); w.unmount()
})

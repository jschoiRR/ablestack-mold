// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import { mount, shallowMount } from '@vue/test-utils'
import { h, reactive } from 'vue'
import fs from 'fs'
import path from 'path'
import LoadBalancing from '@/views/compute/AutoScaleLoadBalancing.vue'
import Profile from '@/views/compute/AutoScaleVmProfile.vue'
import NetworkSelection from '@/views/compute/wizard/NetworkSelection.vue'
import Cell from '@/views/compute/AutoScaleListCell.vue'
import ListView from '@/components/view/ListView'
import ResourceView from '@/components/view/ResourceView.vue'
import { autoScaleInfrastructureRows, autoScaleVisibleColumns, autoScaleTemplateById } from '@/utils/autoScaleUi'
import { getAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn() }))
const translate = (key, params) => params ? `${key}:${params.min}/${params.max}` : key
function cell (resource, column) {
  return mount(Cell, { props: { resource, column }, global: { mocks: { $t: translate, $toLocaleDate: value => value }, stubs: { RouterLink: { props: ['to'], render () { return h('a', { href: this.to }, this.$slots.default()) } } } } })
}
test('member counts include zero and bounds independently; missing is not zero', () => {
  const wrapper = cell({ availablevirtualmachinecount: 0, minmembers: 1, maxmembers: 4 }, 'availablevirtualmachinecount')
  expect(wrapper.text()).toContain('0'); expect(wrapper.text()).toContain('1/4')
  const absent = cell({}, 'availablevirtualmachinecount'); expect(absent.text()).toContain('—/—')
  wrapper.unmount(); absent.unmount()
})
test('network/LB is rendered as safe text and links to its own network only', () => {
  const wrapper = cell({ associatednetworkid: 'network', associatednetworkname: '<img onerror=x>', publicip: '10.0.0.1', publicport: 80, privateport: 8080 }, 'associatednetworkname')
  expect(wrapper.find('img').exists()).toBe(false); expect(wrapper.get('a').attributes('href')).toBe('/guestnetwork/network')
  expect(wrapper.text()).toContain('<img onerror=x>'); expect(wrapper.text()).toContain('10.0.0.1:80 → 8080'); wrapper.unmount()
})
test('operation state takes precedence over stale evaluation and invalid dates remain readable', () => {
  const wrapper = cell({ lastcheckedat: 'bad', operationinprogress: true, scalingaction: 'CLEANUP', scalingphase: 'CLEANUP_RETRY', evaluation: { status: 'MATCHED' } }, 'lastcheckedat')
  expect(wrapper.text()).toContain('label.autoscale.not.recorded'); expect(wrapper.text()).toContain('CLEANUP_RETRY'); expect(wrapper.text()).not.toContain('MATCHED'); wrapper.unmount()
})
test('future evaluation status is visible without exposing a missing translation key', () => {
  const wrapper = cell({ evaluation: { status: 'FUTURE' } }, 'lastcheckedat')
  expect(wrapper.text()).toContain('label.autoscale.evaluation.unknown (FUTURE)'); wrapper.unmount()
})
test('saved columns consolidate old port/range choices without duplicates or lost owner scope', () => {
  expect(autoScaleVisibleColumns(['name', 'minmembers', 'maxmembers', 'publicip', 'publicport', 'privateport', 'account', 'project'])).toEqual(['name', 'availablevirtualmachinecount', 'associatednetworkname', 'account', 'project'])
  expect(autoScaleVisibleColumns(['state', 'publicport'])).toEqual(['state', 'associatednetworkname'])
})
const source = file => fs.readFileSync(path.resolve(__dirname, '../../../../src', file), 'utf8')
test('common DetailsTab cannot show autoscale cards or operation alerts on any resource', () => {
  expect(source('components/view/DetailsTab.vue')).not.toMatch(/auto-scale|autoScale|autoscale/)
})
test.each(['vm', 'volume', 'template', 'autoscalevmgroup'])('common info card slot remains isolated for %s', name => {
  getAPI.mockResolvedValue({})
  const wrapper = mount(ResourceView, {
    props: { resource: { id: 'resource' }, tabs: [{ name: 'details', component: { render: () => h('span', 'details') } }] },
    global: {
      mocks: { $route: { path: '/' + name + '/resource', meta: { name }, query: {} }, $t: translate, $store: reactive({ state: { app: { device: 'desktop' } }, getters: { userInfo: {} } }) },
      stubs: { ResourceLayout: { render () { return h('div', [this.$slots.left(), this.$slots.right()]) } }, InfoCard: { render () { return h('div', this.$slots.details()) } }, AutoScaleInfoSummary: { render: () => h('span', 'autoscale-only-summary') }, ACard: { render () { return h('div', this.$slots.default()) } } }
    }
  })
  expect(wrapper.text().includes('autoscale-only-summary')).toBe(name === 'autoscalevmgroup'); expect(wrapper.text()).toContain('details'); wrapper.unmount()
})

test('the saved old default gains the new status clock and retains project ownership', () => {
  const old = ['name', 'state', 'associatednetworkname', 'publicip', 'publicport', 'privateport', 'minmembers', 'maxmembers', 'availablevirtualmachinecount', 'account', 'project']
  expect(autoScaleVisibleColumns(old)).toEqual(['name', 'state', 'availablevirtualmachinecount', 'associatednetworkname', 'lastcheckedat', 'account', 'project'])
})

test.each([true, false])('network support labels localize only when the autoscale wizard opts in: %s', supported => {
  const context = { items: [{ id: 'network', supportsvmautoscaling: supported }], vpcs: [], $t: translate, localizeAutoScaleSupport: true }
  expect(NetworkSelection.computed.networkItems.call(context)[0].supportsvmautoscaling).toBe(supported ? 'label.yes' : 'label.no')
  context.localizeAutoScaleSupport = false
  expect(NetworkSelection.computed.networkItems.call(context)[0].supportsvmautoscaling).toBe(supported ? 'Yes' : 'No')
})

test('shared exact image lookup preserves project, zone and ready scope', async () => {
  const request = jest.fn().mockResolvedValueOnce({ listtemplatesresponse: { template: [] } }).mockResolvedValueOnce({ listtemplatesresponse: { template: [{ id: 'shared-image' }] } })
  const scope = { id: 'shared-image', projectid: 'project', zoneid: 'zone', isready: true }
  const response = await autoScaleTemplateById(request, scope)
  const { id, ...projectScope } = scope
  expect(request.mock.calls).toEqual([['listTemplates', { ...projectScope, templatefilter: 'executable', page: 1, pagesize: 100 }], ['listTemplates', { ...projectScope, templatefilter: 'sharedexecutable', page: 1, pagesize: 100 }]])
  expect(response.listtemplatesresponse.template[0].id).toBe('shared-image')
})
test('existing executable image never triggers a shared query', async () => {
  const response = { listtemplatesresponse: { template: [{ id: 'owned' }] } }; const request = jest.fn().mockResolvedValue(response)
  expect(await autoScaleTemplateById(request, { id: 'owned' })).toBe(response)
  expect(request).toHaveBeenCalledTimes(1)
})
test('permission/query failure is surfaced without attempting a different filter', async () => {
  const request = jest.fn().mockRejectedValue(new Error('permission'))
  await expect(autoScaleTemplateById(request, { id: 'image' })).rejects.toThrow('permission')
  expect(request).toHaveBeenCalledTimes(1)
})

test('profile includes scoped private shared templates and excludes non-boot image types', async () => {
  getAPI.mockResolvedValueOnce({ listtemplatesresponse: { template: [{ id: 'owned', format: 'QCOW2', templatetype: 'USER' }] } }).mockResolvedValueOnce({ listtemplatesresponse: { template: [{ id: 'shared', format: 'QCOW2', templatetype: 'USER' }, { id: 'owned', format: 'QCOW2', templatetype: 'USER' }, { id: 'data', format: 'QCOW2', templatetype: 'DATADISK' }] } })
  const ctx = { resource: { projectid: 'project' }, zoneid: 'zone', templatesList: [], $notifyError: jest.fn() }
  await Profile.methods.fetchTemplateData.call(ctx)
  expect(ctx.templatesList.map(image => image.id)).toEqual(['owned', 'shared'])
  expect(getAPI.mock.calls.slice(-2).every(([, params]) => params.projectid === 'project' && params.zoneid === 'zone' && params.isready === true)).toBe(true)
  expect(ctx.$notifyError).not.toHaveBeenCalled()
})

test('owner cell identifies project ownership when account is absent, and keeps normal account names', () => {
  const project = cell({ project: '한글 프로젝트' }, 'account'); const account = cell({ account: 'owner' }, 'account'); const absent = cell({}, 'account')
  expect(project.text()).toBe('한글 프로젝트'); expect(account.text()).toBe('owner'); expect(absent.text()).toBe('—')
  project.unmount(); account.unmount(); absent.unmount()
})

test('project image recheck follows pagination and never accepts an image outside the allowed lists', async () => {
  const request = jest.fn().mockResolvedValueOnce({ listtemplatesresponse: { count: 101, template: Array.from({ length: 100 }, (_, index) => ({ id: `other-${index}` })) } }).mockResolvedValueOnce({ listtemplatesresponse: { count: 101, template: [{ id: 'shared' }] } })
  const response = await autoScaleTemplateById(request, { id: 'shared', projectid: 'project', zoneid: 'zone', isready: true })
  expect(response.listtemplatesresponse.template[0].id).toBe('shared')
  expect(request.mock.calls[1][1].page).toBe(2)
  expect(request.mock.calls.every(([, args]) => args.id === undefined && args.projectid === 'project' && args.isready)).toBe(true)
  request.mockReset().mockResolvedValue({ listtemplatesresponse: {} })
  expect((await autoScaleTemplateById(request, { id: 'not-shared', projectid: 'project' })).listtemplatesresponse.template).toEqual([])
})
test('project list permission failure is surfaced without a broader lookup', async () => {
  const request = jest.fn().mockRejectedValue(new Error('permission'))
  await expect(autoScaleTemplateById(request, { id: 'image', projectid: 'project' })).rejects.toThrow('permission')
  expect(request).toHaveBeenCalledTimes(1)
})

// Legacy API rules omit the LB protocol; the edit dialog must submit an explicit
// TCP protocol to prevent the server from leaving an algorithm change partially applied.
test('legacy LB edit provides TCP and preserves explicitly configured protocols', () => {
  const ctx = { editRuleDetails: {} }
  LoadBalancing.methods.openEditRuleModal.call(ctx, { id: 'legacy', name: 'rule', algorithm: 'roundrobin' })
  expect(ctx.editRuleDetails.protocol).toBe('tcp')
  LoadBalancing.methods.openEditRuleModal.call(ctx, { id: 'explicit', name: 'rule', algorithm: 'roundrobin', protocol: 'ssl' })
  expect(ctx.editRuleDetails.protocol).toBe('ssl')
})

test('AutoScale network initialization uses flat API parameters without altering default VM requests', async () => {
  getAPI.mockResolvedValue({ listvpcsresponse: { vpc: [] } })
  getAPI.mockClear()
  NetworkSelection.created.call({ localizeAutoScaleSupport: true, vpcs: [] })
  await Promise.resolve()
  expect(getAPI.mock.calls[0][0]).toBe('listVPCs')
  expect(getAPI.mock.calls[0][1]).not.toHaveProperty('params')
  getAPI.mockClear()
  NetworkSelection.created.call({ localizeAutoScaleSupport: false, vpcs: [] })
  await Promise.resolve()
  expect(getAPI.mock.calls[0][1]).toHaveProperty('params')
})

test('optional infrastructure visibility survives route reload without automatically selecting defaults', () => {
  const selected = ['name', 'domain', 'infrastructurenetworkname', 'lbprovider', 'interval', 'created', 'zonename']
  expect(autoScaleVisibleColumns(selected)).toEqual(selected)
  expect(autoScaleVisibleColumns(['name', 'zonename', 'zonename', 'unsupported'])).toEqual(['name', 'zonename'])
})

test('Zone enrichment queries each authorized project network once per page and keeps rows and counts', async () => {
  const request = jest.fn().mockResolvedValue({ listnetworksresponse: { network: [{ id: 'network', zoneid: 'zone', zonename: '한글 Zone' }] } })
  const rows = [{ id: 'one', associatednetworkid: 'network', projectid: 'project', availablevirtualmachinecount: 0 }, { id: 'two', associatednetworkid: 'network', projectid: 'project' }]
  const result = await autoScaleInfrastructureRows(request, rows)
  expect(request).toHaveBeenCalledTimes(1)
  expect(request).toHaveBeenCalledWith('listNetworks', { projectid: 'project', listall: true, id: 'network' })
  expect(result.map(row => row.zonename)).toEqual(['한글 Zone', '한글 Zone'])
  expect(result[0].availablevirtualmachinecount).toBe(0)
  expect(rows[0].zonename).toBeUndefined()
})
test('Zone enrichment failure/denial is explicit, preserves group rows and never guesses a Zone', async () => {
  const request = jest.fn().mockRejectedValue(new Error('offline'))
  const rows = [{ id: 'group', associatednetworkid: 'network', account: 'owner', domainid: 'domain' }]
  const failed = await autoScaleInfrastructureRows(request, rows)
  expect(request).toHaveBeenCalledWith('listNetworks', { account: 'owner', domainid: 'domain', listall: true, id: 'network' })
  expect(failed[0].infrastructurestatus).toBe('FAILED'); expect(failed[0].zonename).toBeUndefined()
  request.mockClear()
  expect((await autoScaleInfrastructureRows(request, rows, false))[0].infrastructurestatus).toBe('DENIED')
  expect(request).not.toHaveBeenCalled()
})
test('an inaccessible network cannot contribute a name from another network response', async () => {
  const request = jest.fn().mockResolvedValue({ listnetworksresponse: { network: [{ id: 'other', zonename: 'private' }] } })
  expect((await autoScaleInfrastructureRows(request, [{ associatednetworkid: 'requested' }]))[0]).toMatchObject({ infrastructurestatus: 'UNAVAILABLE', zonename: undefined })
})

test.each(['autoscalevmgroup', 'vm'])('Zone is rendered exactly once in the shared list for %s', name => {
  const record = { id: 'group', zoneid: 'zone', zonename: '한글 Zone' }
  const wrapper = shallowMount(ListView, {
    props: { columns: [{ key: 'zonename', dataIndex: 'zonename' }], items: [record] },
    global: {
      provide: { parentFetchData: jest.fn(), parentToggleLoading: jest.fn() },
      mocks: { $route: { name, path: '/' + name, meta: {} }, $store: { getters: { apis: {}, userInfo: { roletype: 'Admin' } } }, $router: { resolve: () => ({ matched: [{ redirect: '/exception/404' }] }) }, $t: key => key },
      stubs: {
        AutoScaleListCell: false,
        'a-table': { render () { return h('div', this.$slots.bodyCell({ column: { key: 'zonename' }, text: record.zonename, record })) } },
        RouterLink: { props: ['to'], render () { return h('span', this.$slots.default()) } }
      }
    }
  })
  expect(wrapper.text()).toBe('한글 Zone'); wrapper.unmount()
})

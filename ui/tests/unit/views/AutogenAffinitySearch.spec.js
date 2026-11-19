// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with this
// work for additional information regarding copyright ownership. The ASF
// licenses this file under the Apache License, Version 2.0 (the "License").
// You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 .
// Unless required by applicable law or agreed to in writing, software is
// distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
import Autogen from '@/views/AutogenView'
import Search from '@/components/view/SearchView'
import compute from '@/config/section/compute'
import store from '@/store'
import { getAPI } from '@/api'
import { affinityGroupListParams, affinityGroupQuery } from '@/utils/affinityGroupCreation'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn(), callAPI: jest.fn() }))
jest.mock('@/store', () => ({ getters: {}, state: { user: { token: 'session' } } }))
let c
const filters = ['name', 'type', 'domainid', 'account', 'projectid']
function route (query = {}) { c.$route = { name: 'affinitygroup', path: '/affinitygroup', fullPath: '/affinitygroup?' + new URLSearchParams(query), params: {}, query, meta: { name: 'affinitygroup', permission: ['listAffinityGroups'], columns: ['name', 'type', 'account', 'domain'], actions: [], searchFilters: filters, customParamHandler: affinityGroupListParams } } }
beforeEach(() => {
  jest.clearAllMocks(); store.getters = { defaultListViewPageSize: 20, userInfo: { id: 'user', roletype: 'Admin' }, project: {}, apis: { listAffinityGroups: {}, listDomains: {}, listAccounts: {}, listProjects: {}, createAccount: {} }, customColumns: {}, metrics: false, listAllProjects: false }
  c = { $store: store, $router: { replace: jest.fn(), push: jest.fn() }, $refs: {}, $emit: jest.fn(), $t: k => k, $showIcon: () => false, $i18n: { locale: 'ko_KR' }, $message: { info: jest.fn(), error: jest.fn() }, $notifyError: jest.fn(), $nextTick: fn => fn() }
  route(); Object.assign(c, Autogen.data.call(c)); Object.entries(Autogen.methods).forEach(([k, fn]) => { c[k] = fn.bind(c) }); c.updateSelectedColumns = jest.fn()
  getAPI.mockResolvedValue({ listaffinitygroupsresponse: { count: 1, affinitygroup: [{ id: 'id', name: 'fixture' }] } })
})
function search () {
  const s = { $route: c.$route, $store: store, $t: k => k, $message: { error: jest.fn() }, $emit: jest.fn(), apiName: 'listAffinityGroups', searchFilters: filters }
  Object.assign(s, Search.data.call(s)); Object.entries(Search.methods).forEach(([k, fn]) => { s[k] = fn.bind(s) }); s.form = {}; s.formRef = { value: { validate: () => Promise.resolve(), resetFields: jest.fn() } }; return s
}
test('actual affinity config excludes Zone and uses the self managed creation dialog', () => {
  const config = compute.children.find(row => row.name === 'affinitygroup')
  expect(config.searchFilters).toEqual(filters); expect(config.actions.find(action => action.api === 'createAffinityGroup').selfManagedDialog).toBe(true)
})
test('legacy Zone URLs are normalized before any API request without losing supported search state', () => {
  route({ zoneid: 'invalid-zone', name: 'exact', type: 'host affinity', page: '2' }); c.fetchData()
  expect(c.$router.replace).toHaveBeenCalledWith({ query: { name: 'exact', type: 'host affinity', page: '2' } }); expect(getAPI).not.toHaveBeenCalled()
  expect(affinityGroupListParams({ zoneid: 'invalid', name: 'exact', type: 'raw', sortkey: 'name', tab: 'details', page: 1 })).toEqual({ name: 'exact', type: 'raw', page: 1 })
})
test('name/type/account/domain/project filters reach the API with raw type values', async () => {
  route({ name: 'exact', type: 'non-strict host affinity', domainid: 'domain', account: 'owner', page: '2', pagesize: '50' }); await c.fetchData()
  expect(getAPI.mock.calls[0][1]).toEqual({ name: 'exact', type: 'non-strict host affinity', domainid: 'domain', account: 'owner', listall: true, page: 2, pagesize: 50 })
  store.getters.listAllProjects = true; route({ projectid: 'project', name: 'exact' }); await c.fetchData(); expect(getAPI.mock.calls[1][1].projectid).toBe('project')
  expect(affinityGroupQuery({ projectid: 'project', account: 'old', domainid: 'domain' })).toEqual({ projectid: 'project', domainid: 'domain' })
})
test('search/reset/page retain supported conditions and reload restores advanced filters from URL', async () => {
  route({ name: 'old', type: 'old', domainid: 'old', account: 'old', projectid: 'old', page: '4', pagesize: '20' }); c.onSearch({ name: 'new', type: 'host affinity', domainid: 'domain', account: 'owner' })
  expect(c.$router.push.mock.calls[0][0].query).toEqual({ name: 'new', type: 'host affinity', domainid: 'domain', account: 'owner', page: '1', pagesize: '20' })
  c.onSearch({}); expect(c.$router.push.mock.calls[1][0].query).toEqual({ page: '1', pagesize: '20' })
  route({ name: 'exact', type: 'host affinity', projectid: 'project' }); const s = search(); s.initFields(); s.fillFormFieldValues(); s.updateIsFiltered(); expect(s.form).toMatchObject(c.$route.query); expect(s.isFiltered).toBe(true)
  c.changePage(2, 20); expect(c.$router.push.mock.calls[2][0].query).toMatchObject({ name: 'exact', type: 'host affinity', projectid: 'project', page: 2 })
})
test('filter options keep raw type IDs separate from labels and show retry after failure', async () => {
  const s = search(); s.initFields()
  getAPI.mockResolvedValueOnce({ listaffinitygrouptypesresponse: { affinityGroupType: [{ type: 'host affinity' }, { type: 'future type' }] } })
  await s.fetchAffinityFilters(['type']); const field = s.fields.find(row => row.name === 'type'); expect(field.opts.map(row => row.id)).toEqual(['future type', 'host affinity']); expect(field.opts[1].name).toBe('label.affinity.type.strict.affinity')
  getAPI.mockRejectedValueOnce(new Error('offline')); await s.fetchAffinityFilters(['type']); expect(field.error).toBe(true); expect(field.opts).toEqual([])
})
test('domain changes clear incompatible owners and stale options cannot overwrite a new domain', async () => {
  const s = search(); s.initFields(); s.form = { domainid: 'old', account: 'old', projectid: 'old' }
  let finish; getAPI.mockImplementationOnce(() => new Promise(resolve => { finish = resolve })).mockImplementation(command => Promise.resolve(command === 'listAccounts' ? { listaccountsresponse: { count: 1, account: [{ id: 'new', name: 'new', domainid: 'new' }] } } : { listprojectsresponse: { count: 0 } }))
  const old = s.fetchAffinityFilters(['account']); s.form.domainid = 'new'; s.onSelectFieldChange('domainid'); await Promise.resolve(); await Promise.resolve()
  finish({ listaccountsresponse: { count: 1, account: [{ id: 'old', name: 'old', domainid: 'old' }] } }); await old
  expect(s.form.account).toBeUndefined(); expect(s.form.projectid).toBeUndefined(); expect(s.fields.find(row => row.name === 'account').opts[0].id).toBe('new')
})
test('search errors keep the route recoverable and late list responses are ignored', async () => {
  let done; getAPI.mockImplementationOnce(() => new Promise(resolve => { done = resolve })); const old = c.fetchData(); route({ name: 'new' }); await c.fetchData(); done({ listaffinitygroupsresponse: { count: 1, affinitygroup: [{ id: 'stale' }] } }); await old; expect(c.items[0].id).toBe('id')
  route({ domainid: 'invalid' }); getAPI.mockRejectedValueOnce({ response: { status: 431 } }); await c.fetchData(); expect(c.listRefreshError).toBe(true); expect(c.$router.push).not.toHaveBeenCalled()
})

test('root-admin descendant accounts and projects are excluded from a selected domain', async () => {
  const s = search(); s.initFields(); s.form = { domainid: 'root' }
  getAPI.mockImplementation(command => Promise.resolve(command === 'listAccounts'
    ? { listaccountsresponse: { count: 2, account: [{ id: 'a', name: 'root-owner', domainid: 'root' }, { id: 'b', name: 'child-owner', domainid: 'child' }] } }
    : { listprojectsresponse: { count: 2, project: [{ id: 'p', name: 'root-project', domainid: 'root' }, { id: 'q', name: 'child-project', domainid: 'child' }] } }))
  await s.fetchAffinityFilters(['account', 'projectid'])
  expect(s.fields.find(row => row.name === 'account').opts.map(row => row.name)).toEqual(['root-owner'])
  expect(s.fields.find(row => row.name === 'projectid').opts.map(row => row.id)).toEqual(['p'])
})

test('Back/Forward updates an already open affinity filter form from the canonical route', () => {
  route({ projectid: 'project' }); const s = search(); s.initFields(); s.fillFormFieldValues(); s.visibleFilter = true; s.fetchAffinityFilters = jest.fn()
  route({ account: 'owner', domainid: 'root' }); s.$route = c.$route
  Search.watch.$route.call(s, s.$route, {})
  expect(s.form.account).toBe('owner'); expect(s.form.projectid).toBeUndefined(); expect(s.fetchAffinityFilters).toHaveBeenCalledWith(filters)
})
test('account lookup and submit require an explicit domain without silently changing the search scope', async () => {
  const s = search(); s.initFields(); await s.fetchAffinityFilters(['account']); expect(getAPI).not.toHaveBeenCalled()
  s.form = { account: 'owner' }; s.handleSubmit(); expect(s.$emit).not.toHaveBeenCalled(); expect(s.$message.error).toHaveBeenCalledWith('message.affinity.search.account.domain')
  s.form.domainid = 'root'; s.handleSubmit(); await Promise.resolve(); expect(s.$emit).toHaveBeenCalledWith('search', { account: 'owner', domainid: 'root' })
})

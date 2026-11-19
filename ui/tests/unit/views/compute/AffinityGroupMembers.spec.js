// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// Licensed under the Apache License, Version 2.0. You may obtain a copy
// at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by law,
// software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied.
import { shallowMount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import AffinityGroupMembers from '@/views/compute/AffinityGroupMembers.vue'
import DeleteAffinityGroup from '@/views/compute/DeleteAffinityGroup.vue'
import DeleteAffinityGroups from '@/views/compute/DeleteAffinityGroups.vue'
import { getAPI, postAPI } from '@/api'
import { clearAffinityOperations, getAffinityOperation } from '@/utils/affinityGroupOperations'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
const group = { id: 'target', name: 'Target', account: 'owner', domainid: 'domain', virtualmachineIds: ['one', 'two', 'three'] }
const vms = ['one', 'two', 'three'].map(id => ({ id, name: id, state: 'Stopped', account: 'owner', domainid: 'domain', affinitygroup: [{ id: 'other' }] }))
const flush = async () => { for (let i = 0; i < 30; i++) { await Promise.resolve(); await nextTick() } }
let wrappers; let store; let fresh; let groups; let failList; let parentRefresh
function mount (component = AffinityGroupMembers) {
  const wrapper = shallowMount(component, { props: { resource: group }, global: { renderStubDefaultSlot: true, provide: { parentFetchData: parentRefresh }, mocks: { $store: store, $route: { fullPath: '/affinitygroup/target', query: {} }, $router: { push: jest.fn() }, $t: key => key }, stubs: { MoldDialog: { template: '<div><slot/><slot name="footer"/></div>' } } } })
  wrappers.push(wrapper); return wrapper
}
beforeEach(() => {
  clearAffinityOperations(); wrappers = []; jest.clearAllMocks(); parentRefresh = jest.fn(); failList = false; groups = { ...group }; fresh = vms.map(vm => ({ ...vm, affinitygroup: [...vm.affinitygroup] }))
  store = reactive({ state: { user: { token: Math.random() } }, getters: { userInfo: { id: 'user' }, project: {}, apis: { listVirtualMachines: {}, listAffinityGroups: {}, updateVMAffinityGroup: {} } } })
  getAPI.mockImplementation(async (cmd, params) => {
    if (cmd === 'queryAsyncJobResult') return { queryasyncjobresultresponse: { jobstatus: 1 } }
    if (cmd === 'listAffinityGroups') return { listaffinitygroupsresponse: { count: 1, affinitygroup: [groups] } }
    if (failList) throw new Error('offline')
    return { listvirtualmachinesresponse: { count: params.id ? 1 : 3, virtualmachine: params.id ? fresh.filter(vm => vm.id === params.id) : fresh.slice(0, 2) } }
  })
  postAPI.mockImplementation(async (cmd, params) => ({ [cmd === 'deleteAffinityGroup' ? 'deleteaffinitygroupresponse' : 'updatevirtualmachineresponse']: { jobid: 'job-' + params.id } }))
})
afterEach(() => { wrappers.forEach(w => w.unmount()); clearAffinityOperations() })
test('full mapping count and query total are independent of the current page; failed refresh retains rows', async () => {
  const w = mount(); await flush()
  expect(w.vm.memberCount).toBe(3); expect(w.vm.total).toBe(3); expect(w.vm.rows).toHaveLength(2)
  failList = true; await w.vm.fetchData(); expect(w.vm.rows).toHaveLength(2); expect(w.vm.total).toBe(3); expect(w.vm.error).toBe('offline')
})
test.each([AffinityGroupMembers, DeleteAffinityGroup])('$name reads an account group in its owner scope instead of the caller all-project scope', async component => {
  const normal = getAPI.getMockImplementation()
  getAPI.mockImplementation((cmd, params) => cmd === 'listAffinityGroups' && (params.projectid === '-1' || params.account !== group.account || params.domainid !== group.domainid)
    ? Promise.resolve({ listaffinitygroupsresponse: {} }) : normal(cmd, params))
  const w = mount(component); await flush()
  expect(w.vm.error).toBe('')
  expect(component === AffinityGroupMembers ? w.vm.memberCount : w.vm.count).toBe(3)
  expect(getAPI).toHaveBeenCalledWith('listAffinityGroups', { id: 'target', listall: true, domainid: 'domain', account: 'owner' }, expect.any(Object))
})

test('refresh clamps an emptied last member page and fetches the remaining page', async () => {
  const w = mount(); await flush(); groups = { ...group, virtualmachineIds: ['one'] }
  getAPI.mockImplementation(async (cmd, params) => cmd === 'listAffinityGroups' ? { listaffinitygroupsresponse: { affinitygroup: [groups] } } : { listvirtualmachinesresponse: { count: 1, ...(params.page === 1 ? { virtualmachine: [fresh[0]] } : {}) } })
  w.vm.page = 2; await w.vm.fetchData(); await flush()
  expect(w.vm.page).toBe(1); expect(w.vm.total).toBe(1); expect(w.vm.rows[0].id).toBe('one'); expect(w.vm.error).toBe('')
})
test('batch partial failure retries only failed VM and preserves other group IDs', async () => {
  const w = mount(); await flush()
  postAPI.mockRejectedValueOnce(Object.assign(new Error('denied'), { response: { data: { errorresponse: { errorcode: 431, errortext: 'denied' } } } }))
  w.vm.confirm('add', fresh.slice(0, 2)); await w.vm.execute(false)
  expect(w.vm.batch.results.one.status).toBe('failed'); expect(w.vm.batch.results.two.status).toBe('succeeded')
  expect(postAPI.mock.calls[1][1]).toEqual({ id: 'two', affinitygroupids: 'other,target' })
  await w.vm.execute(true)
  expect(postAPI.mock.calls.map(call => call[1].id)).toEqual(['one', 'two', 'one'])
  expect(w.vm.batch.results.one.status).toBe('succeeded')
})
test('a newly confirmed batch clears a known terminal failure instead of silently skipping it', async () => {
  const w = mount(); await flush()
  postAPI.mockRejectedValueOnce(Object.assign(new Error('denied'), { response: { data: { errorresponse: { errorcode: 431, errortext: 'denied' } } } }))
  w.vm.confirm('add', [fresh[0]]); await w.vm.execute(false)
  expect(w.vm.batch.results.one.status).toBe('failed')
  w.vm.confirm('add', [fresh[0]]); expect(w.vm.batch.results.one.status).toBe('idle')
  await w.vm.execute(false); expect(w.vm.batch.results.one.status).toBe('succeeded'); expect(postAPI).toHaveBeenCalledTimes(2)
})
test('completion and receipt recheck refresh the parent without clearing its resource or closing results', async () => {
  const w = mount(); await flush()
  // Autogen's ordinary load may reset resource when the tab URL changes its scope.
  parentRefresh.mockImplementation(options => { if (!options?.irefresh) return w.setProps({ resource: {} }) })
  w.vm.confirm('add', [fresh[0]]); await w.vm.execute(false); await flush()
  expect(w.vm.dialog).toBe('results'); expect(w.vm.batch.results.one.status).toBe('succeeded')
  await w.vm.recheck(fresh[0]); await flush()
  expect(w.vm.dialog).toBe('results'); expect(w.props('resource').id).toBe('target')
  expect(parentRefresh.mock.calls).toEqual([[{ irefresh: true }], [{ irefresh: true }]])
})
test('unknown POST outcome blocks both resubmission and new batch; result survives remount', async () => {
  const w = mount(); await flush(); postAPI.mockRejectedValue(new Error('transport'))
  w.vm.confirm('add', [fresh[0]]); await w.vm.execute(false)
  expect(w.vm.batch.results.one.status).toBe('unknown')
  await w.vm.execute(true); w.vm.confirm('remove', [fresh[1]])
  expect(w.vm.dialog).toBe('results'); expect(postAPI).toHaveBeenCalledTimes(1)
  w.unmount(); wrappers = []; const again = mount(); await flush()
  expect(again.vm.batch.targets[0].id).toBe('one'); expect(again.vm.batch.results.one.status).toBe('unknown')
})
test('another workflow receipt blocks a new goal and rechecking it never completes the unsubmitted goal', async () => {
  groups = { ...group, virtualmachineIds: [] }
  const normal = getAPI.getMockImplementation(); let queryFails = true
  getAPI.mockImplementation((cmd, params) => {
    if (cmd === 'queryAsyncJobResult') return queryFails ? Promise.reject(new Error('offline')) : Promise.resolve({ queryasyncjobresultresponse: { jobstatus: 1 } })
    if (cmd === 'listVirtualMachines' && params.affinitygroupid) return Promise.resolve({ listvirtualmachinesresponse: {} })
    return normal(cmd, params)
  })
  const w = mount(); await flush()
  const previous = getAffinityOperation(w.vm.securityScope, 'one', { get: getAPI, post: postAPI, maxRetries: 0 })
  await previous.run('updateVMAffinityGroup', { id: 'one', affinitygroupids: 'other' })
  expect(previous.snapshot().status).toBe('unknown')
  w.vm.confirm('add', [fresh[0]])
  expect(w.vm.dialog).toBe('blocked'); expect(w.vm.batch.targets).toEqual([])
  queryFails = false; await w.vm.recheckBlocked()
  expect(w.vm.blocked.state.status).toBe('succeeded'); expect(w.vm.batch.results).toEqual({}); expect(w.vm.memberCount).toBe(0)
  expect(postAPI).toHaveBeenCalledTimes(1)
})
test('batch receipts remain their own history when another dialog later changes the VM', async () => {
  const w = mount(); await flush(); w.vm.confirm('add', [fresh[0]]); await w.vm.execute(false)
  const originalJob = w.vm.batch.results.one.jobId; const op = w.vm.operation(fresh[0])
  w.unmount(); wrappers = []
  op.acknowledge(); postAPI.mockResolvedValueOnce({ updatevirtualmachineresponse: { jobid: 'other-dialog-job' } })
  await op.run('updateVMAffinityGroup', { id: 'one', affinitygroupids: 'external' })
  const again = mount(); await flush()
  expect(again.vm.batch.results.one.jobId).toBe(originalJob)
  expect(again.vm.batch.results.one.status).toBe('succeeded')
  expect(op.snapshot().jobId).toBe('other-dialog-job')
})
test('external change after confirmation prevents POST; explicit retry merges current unrelated groups', async () => {
  const w = mount(); await flush(); w.vm.confirm('add', [fresh[0]])
  fresh[0] = { ...fresh[0], affinitygroup: [{ id: 'external' }, { id: 'other' }] }
  await w.vm.execute(false); expect(postAPI).not.toHaveBeenCalled(); expect(w.vm.batch.results.one.error).toBe('message.affinity.members.changed')
  await w.vm.execute(true); expect(postAPI.mock.calls[0][1].affinitygroupids).toBe('external,other,target')
})
test('selections retain off-page snapshots and security scope clears them and results', async () => {
  const w = mount(); await flush(); w.vm.selectMembers(['one']); w.vm.rows = [fresh[2]]; w.vm.selectMembers(['one', 'three'])
  expect(Object.keys(w.vm.selected).sort()).toEqual(['one', 'three'])
  w.vm.confirmRemove(); expect(w.vm.batch.targets.map(vm => vm.id)).toEqual(['one', 'three'])
  store.getters.project = { id: 'new' }; await flush()
  expect(w.vm.selection).toEqual([]); expect(w.vm.dialog).toBe(''); expect(w.vm.batch.targets).toEqual([])
})
test('Running rows and missing API permission cannot start member changes', async () => {
  const w = mount(); await flush(); w.vm.confirm('add', [{ ...fresh[0], state: 'Running' }]); expect(w.vm.dialog).toBe('')
  delete store.getters.apis.updateVMAffinityGroup; w.vm.confirm('add', [fresh[0]]); expect(w.vm.dialog).toBe(''); expect(postAPI).not.toHaveBeenCalled()
})
test('restricted VM listing retains the full mapping count and does not claim the group is empty', async () => {
  delete store.getters.apis.listVirtualMachines
  const w = mount(); await flush()
  expect(w.vm.memberCount).toBe(3); expect(w.vm.rows).toEqual([]); expect(w.vm.canRead).toBe(false)
  expect(w.html()).toContain('message.affinity.members.restricted'); expect(getAPI).not.toHaveBeenCalled()
})
test('delete dialog loads exact total and requires reconfirmation after mapping changes', async () => {
  const w = mount(DeleteAffinityGroup); await flush(); expect(w.vm.count).toBe(3)
  groups = { ...group, virtualmachineIds: ['one', 'two'] }
  await w.vm.submit(); expect(postAPI).not.toHaveBeenCalled(); expect(w.vm.count).toBe(2); expect(w.vm.operation.status).toBe('failed')
  await w.vm.submit(); expect(postAPI).toHaveBeenCalledTimes(1); expect(w.vm.$router.push).toHaveBeenCalledWith('/affinitygroup')
})
test('selected group deletion confirms one target at a time and cancellation preserves unsubmitted targets', async () => {
  const action = { affinityTargets: [group, { ...group, id: 'second', name: 'Second' }] }
  const w = shallowMount(DeleteAffinityGroups, { props: { currentAction: action }, global: { mocks: { $route: { params: {}, query: {} }, $router: { push: jest.fn() } } } }); wrappers.push(w)
  expect(w.findComponent(DeleteAffinityGroup).props('resource').id).toBe('target')
  action.affinityTargets.pop()
  expect(w.vm.targets).toHaveLength(2)
  w.findComponent(DeleteAffinityGroup).vm.$emit('deleted', 'target'); await nextTick()
  expect(w.findComponent(DeleteAffinityGroup).props('resource').id).toBe('second')
  w.findComponent(DeleteAffinityGroup).vm.$emit('close-action'); await nextTick()
  expect(w.emitted('refresh-data')).toHaveLength(1); expect(w.emitted('close-action')).toHaveLength(1)
  expect(postAPI).not.toHaveBeenCalled()
})

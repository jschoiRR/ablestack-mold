// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// Licensed under the Apache License, Version 2.0. You may obtain a copy
// at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by law,
// software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied.
import { affinityMemberCount, affinityMemberPage, affinityMemberParams, affinityGroupQueryParams, affinityMemberSnapshot, prepareAffinityMemberChange, affinityMemberBatch } from '@/utils/affinityGroupMembers'

const group = { id: 'target', account: 'owner', domainid: 'domain' }
const vm = { id: 'vm', account: 'owner', domainid: 'domain', state: 'Stopped', affinitygroup: [{ id: 'other' }] }
function lookup (fresh = vm, target = group) {
  return jest.fn((cmd) => Promise.resolve(cmd === 'listVirtualMachines' ? { listvirtualmachinesresponse: { virtualmachine: fresh ? [fresh] : [] } } : { listaffinitygroupsresponse: { affinitygroup: target ? [target] : [] } }))
}
test('count uses all unique mapping IDs independently of visible rows and empty omission', () => {
  expect(affinityMemberCount({ virtualmachineIds: ['a', 'b', 'a'] })).toBe(2)
  expect(affinityMemberCount({})).toBe(0)
  expect(affinityMemberPage({ listvirtualmachinesresponse: { count: 11, virtualmachine: [{ id: 'a' }] } }).count).toBe(11)
  expect(affinityMemberPage({ listvirtualmachinesresponse: {} })).toEqual({ rows: [], count: 0 })
})
test.each([{}, { count: -1 }, { count: '1' }, { count: 0, virtualmachine: [{}] }])('malformed or missing count never fabricates an empty result %s', value => {
  if (Object.keys(value).length) expect(() => affinityMemberPage({ listvirtualmachinesresponse: value })).toThrow()
  else expect(() => affinityMemberPage({})).toThrow()
})
test('an emptied last page preserves the total so its caller can clamp and refetch', () => {
  expect(affinityMemberPage({ listvirtualmachinesresponse: { count: 10 } })).toEqual({ rows: [], count: 10 })
})
test('account, project and shared member queries keep their actual owner scopes', () => {
  expect(affinityMemberParams(group, 2, 10, 'find')).toMatchObject({ affinitygroupid: 'target', listall: true, account: 'owner', domainid: 'domain', page: 2, pagesize: 10, keyword: 'find' })
  expect(affinityMemberParams({ ...group, projectid: 'project' }, 1, 10, '', false)).toMatchObject({ projectid: 'project', domainid: 'domain' })
  expect(affinityMemberParams(group, 1, 10, '', false)).toMatchObject({ account: 'owner', domainid: 'domain' })
  expect(affinityMemberParams(group, 1, 10, '', false)).not.toHaveProperty('projectid')
  expect(affinityMemberParams(group, 1, 10, '')).not.toHaveProperty('projectid')
  expect(affinityMemberParams({ ...group, type: 'ExplicitDedication' }, 1, 10, '')).toMatchObject({ affinitygroupid: 'target', domainid: 'domain', isrecursive: true, projectid: '-1' })
  expect(affinityGroupQueryParams(group)).toEqual({ id: 'target', listall: true, account: 'owner', domainid: 'domain' })
  expect(affinityGroupQueryParams({ ...group, projectid: 'p' })).toEqual({ id: 'target', listall: true, projectid: 'p', domainid: 'domain' })
})
test('add reads current memberships and preserves unrelated groups', async () => {
  const get = lookup()
  expect(await prepareAffinityMemberChange(get, group, affinityMemberSnapshot(vm), 'add')).toEqual({ affinitygroupids: 'other,target' })
  expect(get.mock.calls[1][1]).toEqual({ id: 'target', domainid: 'domain', account: 'owner' })
})
test('remove preserves every other membership', async () => {
  const linked = { ...vm, affinitygroup: [{ id: 'other' }, { id: 'target' }, { id: 'third' }] }
  expect(await prepareAffinityMemberChange(lookup(linked), group, linked, 'remove')).toEqual({ affinitygroupids: 'other,third' })
})
test('remove last membership sends the required empty replacement', async () => {
  const linked = { ...vm, affinitygroup: [{ id: 'target' }] }
  expect(await prepareAffinityMemberChange(lookup(linked), group, linked, 'remove')).toEqual({ affinitygroupids: '' })
})
test.each(['Running', 'Starting', 'Stopping', 'Destroyed'])('fresh %s state fails before submission', async state => {
  const get = lookup({ ...vm, state })
  await expect(prepareAffinityMemberChange(get, group, vm, 'add')).rejects.toThrow('message.affinity.vm.stopped')
  expect(get).toHaveBeenCalledTimes(1)
})
test.each([{ account: 'foreign' }, { domainid: 'foreign' }, { projectid: 'project' }])('fresh owner change is rejected %s', async owner => {
  await expect(prepareAffinityMemberChange(lookup({ ...vm, ...owner }), group, vm, 'add')).rejects.toThrow('message.affinity.owner.changed')
})
test('same admin visibility does not permit cross-account association', async () => {
  const foreign = { ...vm, account: 'foreign' }
  await expect(prepareAffinityMemberChange(lookup(foreign), group, foreign, 'add')).rejects.toThrow('message.affinity.owner.changed')
})
test('domain shared target uses the VM-owner eligible-groups contract', async () => {
  const shared = { ...group, account: 'system', type: 'ExplicitDedication' }
  expect(await prepareAffinityMemberChange(lookup(vm, shared), shared, vm, 'add')).toEqual({ affinitygroupids: 'other,target' })
})
test('inaccessible VM and unavailable target fail closed', async () => {
  await expect(prepareAffinityMemberChange(lookup(null), group, vm, 'add')).rejects.toThrow('message.affinity.owner.changed')
  await expect(prepareAffinityMemberChange(lookup(vm, null), group, vm, 'add')).rejects.toThrow('message.affinity.selection.unavailable')
})
test('external membership changes since confirmation require explicit refresh', async () => {
  const get = lookup({ ...vm, affinitygroup: [{ id: 'other' }, { id: 'external' }] })
  await expect(prepareAffinityMemberChange(get, group, vm, 'add')).rejects.toThrow('message.affinity.members.changed')
})
test('scope changes and lookup failures cannot prepare any replacement', async () => {
  await expect(prepareAffinityMemberChange(lookup(), group, vm, 'add', () => false)).rejects.toThrow('message.affinity.owner.changed')
  await expect(prepareAffinityMemberChange(jest.fn().mockRejectedValue(new Error('offline')), group, vm, 'add')).rejects.toThrow('offline')
})
test('snapshots survive later object mutation; receipts persist only in their security scope', () => {
  const original = { ...vm, affinitygroup: [{ id: 'one' }] }; const snapshot = affinityMemberSnapshot(original)
  original.affinitygroup[0].id = 'two'; expect(snapshot.affinitygroup[0].id).toBe('one')
  const batch = affinityMemberBatch('session1', 'target'); batch.targets.push(snapshot)
  expect(affinityMemberBatch('session1', 'target').targets).toHaveLength(1)
  expect(affinityMemberBatch('session2', 'target').targets).toEqual([])
})

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

import { vmGroupOwner, vmGroupOwnerKey, vmGroupLookupStatus } from './vmGroupLookup'
import { vmGroupErrorKey } from './vmGroupNames'
const uuid = /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i

export function sameVmGroupOwner (left, right) {
  return !!vmGroupOwner(left) && vmGroupOwnerKey(left) === vmGroupOwnerKey(right)
}

export function vmGroupMemberScope (group, store, route) {
  return JSON.stringify([group.id, vmGroupOwnerKey(group), store.getters.userInfo?.id, store.state?.user?.token,
    store.getters.project?.id, route.path])
}

export function vmGroupMemberParams (group, { keyword = '', page = 1, pagesize = 20 } = {}, members = true) {
  const owner = vmGroupOwner(group)
  if (!group.id || !owner) throw new Error('message.vm.group.members.owner')
  return {
    ...owner,
    listall: true,
    state: 'present',
    ...(members ? { groupid: group.id } : {}),
    ...(keyword ? { keyword } : {}),
    page,
    pagesize
  }
}

export function vmGroupMemberRows (json, group, members = true) {
  const response = json?.listvirtualmachinesresponse
  if (!response || response.errorcode || json?.errorresponse || (response.virtualmachine !== undefined && !Array.isArray(response.virtualmachine))) throw json
  const rows = response.virtualmachine || []
  const count = Number(response.count ?? (rows.length ? NaN : 0))
  if (!Number.isSafeInteger(count) || count < rows.length || rows.some(vm => !uuid.test(vm.id) || typeof vm.state !== 'string' || ['Destroyed', 'Expunging'].includes(vm.state) || !sameVmGroupOwner(vm, group) || (members && vm.groupid !== group.id)) || new Set(rows.map(vm => vm.id)).size !== rows.length) throw new Error('Unexpected member response')
  return { rows: rows.map(vm => ({ ...vm, name: vm.displayname || vm.name || vm.id })), count }
}

export function vmGroupMemberSummary (group) {
  const count = group.virtualmachinecount
  const states = group.virtualmachinestates
  if (!Number.isSafeInteger(count) || count < 0 || !states || typeof states !== 'object' || Array.isArray(states)) return null
  const entries = Object.entries(states)
  if (entries.some(([state, value]) => !state || !Number.isSafeInteger(value) || value < 0) || entries.reduce((sum, [, value]) => sum + value, 0) !== count) return null
  return { count, states: entries.sort(([left], [right]) => left.localeCompare(right)) }
}

export function vmGroupMemberActionReason (group, rows, apis, ready = true) {
  if (!vmGroupOwner(group)) return 'message.vm.group.members.owner'
  if (!('updateVirtualMachine' in apis)) return 'message.vm.group.members.readonly'
  if (!ready) return 'message.vm.group.members.refresh'
  const params = apis.updateVirtualMachine?.params || []
  if (!params.some(param => param.name === 'groupid') || !params.some(param => param.name === 'cleargroup')) return 'message.vm.group.members.unsupported'
  if (rows.some(vm => !sameVmGroupOwner(vm, group))) return 'message.vm.group.members.owner'
  return ''
}

export function vmGroupMemberSnapshot (rows) {
  return Object.freeze(rows.map(vm => Object.freeze({ ...vm })))
}

async function completedUpdate (getAPI, response) {
  const data = response?.updatevirtualmachineresponse
  if (!data || data.errorcode || response.errorresponse) throw response
  if (!data.jobid) return
  for (let index = 0; index < 120; index++) {
    const result = (await getAPI('queryAsyncJobResult', { jobid: data.jobid }, { preserveOnFailure: true, timeout: 20000 }))?.queryasyncjobresultresponse
    if (!result) throw new Error('Invalid job response')
    if (result.jobstatus === 1) return
    if (result.jobstatus === 2) throw result.jobresult || result
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  throw new Error('message.vm.group.members.unknown')
}

// Check each snapshot before writing; never use a changed table selection as a target.
export async function updateVmGroupMembers (getAPI, postAPI, source, snapshots, target, current = () => true, previous = []) {
  const results = previous.filter(result => result.status === 'success')
  const completed = new Set(results.map(result => result.id))
  for (const snapshot of snapshots) {
    if (completed.has(snapshot.id)) continue
    const result = { id: snapshot.id, name: snapshot.displayname || snapshot.name, status: 'failed', message: '' }
    try {
      if (!current()) throw new Error('message.vm.group.members.context')
      if (!target && snapshot.groupid !== source.id) throw new Error('message.vm.group.members.changed')
      const params = { ...vmGroupMemberParams(source, { pagesize: 1 }, false), id: snapshot.id }
      const vmRows = vmGroupMemberRows(await getAPI('listVirtualMachines', params, { preserveOnFailure: true, timeout: 20000 }), source, false).rows
      const vm = vmRows.find(row => row.id === snapshot.id)
      if (!vm || (vm.groupid || null) !== (snapshot.groupid || null)) throw new Error('message.vm.group.members.changed')
      const group = target || source
      const json = await getAPI('listInstanceGroups', { ...vmGroupOwner(group), id: group.id, listall: true }, { preserveOnFailure: true, timeout: 20000 })
      const response = json?.listinstancegroupsresponse
      if (!response || response.errorcode || !response.instancegroup?.some(row => row.id === group.id && sameVmGroupOwner(row, source))) throw new Error('message.vm.group.members.changed')
      if (target && !sameVmGroupOwner(target, source)) throw new Error('message.vm.group.members.owner')
      if (!current()) throw new Error('message.vm.group.members.context')
      const write = { id: snapshot.id, ...(target ? { groupid: target.id } : { cleargroup: true }) }
      await completedUpdate(getAPI, await postAPI('updateVirtualMachine', write, { preserveOnFailure: true, timeout: 20000 }))
      const after = vmGroupMemberRows(await getAPI('listVirtualMachines', params, { preserveOnFailure: true, timeout: 20000 }), source, false).rows.find(row => row.id === snapshot.id)
      if (!after || (after.groupid || null) !== (target?.id || null)) throw new Error('message.vm.group.members.unknown')
      result.status = 'success'
    } catch (error) {
      const key = error?.message
      result.message = key?.startsWith('message.vm.group.members.') ? key : vmGroupLookupStatus(error) === 'forbidden' ? 'message.vm.group.members.forbidden' : vmGroupErrorKey(error)
      if (result.message === 'message.vm.group.members.unknown' || error?.code === 'ECONNABORTED' || (!error?.response && error?.message === 'Network Error')) result.status = 'unknown'
    }
    results.push(result)
    if (!current()) break
  }
  return results
}

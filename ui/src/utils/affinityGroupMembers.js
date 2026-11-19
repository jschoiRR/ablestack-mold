// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

import { affinityOwnerParams, affinityOwnerKey, affinityVmCanChange } from './affinityGroupOperations'

export const affinityMemberCount = group => new Set(group.virtualmachineIds || []).size
export const affinityVmIds = vm => [...new Set((vm.affinitygroup || []).map(group => group.id))].sort()
export const affinityMemberSnapshot = vm => ({ ...vm, affinitygroup: (vm.affinitygroup || []).map(group => ({ ...group })) })
export const affinityMemberLocked = status => !['idle', 'failed', 'succeeded'].includes(status)

export function affinityMemberPage (response) {
  const value = response?.listvirtualmachinesresponse
  if (!value || (value.count !== undefined && (!Number.isInteger(value.count) || value.count < 0))) throw new Error('message.affinity.members.lookup')
  const rows = value.virtualmachine || []
  const count = value.count || 0
  if (!Array.isArray(rows) || rows.length > count) throw new Error('message.affinity.members.lookup')
  return { rows, count }
}

export function affinityMemberParams (group, page, pagesize, keyword, members = true) {
  return {
    listall: true,
    page,
    pagesize,
    details: 'min,affgrp',
    ...(group.type === 'ExplicitDedication' ? { domainid: group.domainid, isrecursive: true, projectid: '-1' } : affinityOwnerParams(group)),
    ...(members ? { affinitygroupid: group.id } : {}),
    ...(keyword ? { keyword } : {})
  }
}

export function affinityGroupQueryParams (group) {
  return { id: group.id, listall: true, ...(group.type === 'ExplicitDedication' ? { domainid: group.domainid, isrecursive: true, projectid: '-1' } : affinityOwnerParams(group)) }
}

// The server's owner-scoped group lookup is authoritative for domain-shared groups.
// A second read immediately before POST detects changes since confirmation, but the
// replacement API has no compare-and-swap parameter: concurrent writes after this
// read remain a documented API limitation, not an atomic UI guarantee.
export async function prepareAffinityMemberChange (get, group, snapshot, mode, current = () => true) {
  const options = { preserveOnFailure: true, timeout: 15000 }
  const response = await get('listVirtualMachines', { id: snapshot.id, details: 'min,affgrp', ...affinityOwnerParams(snapshot), listall: true }, options)
  const vm = response?.listvirtualmachinesresponse?.virtualmachine?.find(row => row.id === snapshot.id)
  if (!current()) throw new Error('message.affinity.owner.changed')
  if (!vm || affinityOwnerKey(vm) !== affinityOwnerKey(snapshot)) throw new Error('message.affinity.owner.changed')
  if (!affinityVmCanChange(vm)) throw new Error('message.affinity.vm.stopped')
  const eligible = await get('listAffinityGroups', { id: group.id, ...affinityOwnerParams(vm) }, options)
  const target = eligible?.listaffinitygroupsresponse?.affinitygroup?.find(row => row.id === group.id)
  if (!current() || !target || affinityOwnerKey(target) !== affinityOwnerKey(group)) throw new Error('message.affinity.selection.unavailable')
  if (group.type !== 'ExplicitDedication' && affinityOwnerKey(vm) !== affinityOwnerKey(group)) throw new Error('message.affinity.owner.changed')
  const before = affinityVmIds(snapshot); const latest = affinityVmIds(vm)
  if (JSON.stringify(before) !== JSON.stringify(latest)) throw new Error('message.affinity.members.changed')
  const ids = new Set(latest)
  if (mode === 'add') ids.add(group.id)
  else if (mode === 'remove') ids.delete(group.id)
  else throw new Error('message.affinity.members.invalid')
  return { affinitygroupids: [...ids].sort().join(',') }
}

// Keep batch receipts when the tab/dialog closes. Per-VM locks are shared with
// ChangeAffinity, so an unknown or pending write can never be silently replaced.
let batchScope
let batchSequence = 0
const batches = new Map()
export function affinityMemberBatch (scope, groupId) {
  if (scope !== batchScope) {
    for (const batch of batches.values()) releaseAffinityBatchObservers(batch)
    batches.clear(); batchScope = scope
  }
  if (!batches.has(groupId)) batches.set(groupId, { groupId, token: '', mode: '', targets: [], results: {}, busy: false, subscriptions: {} })
  return batches.get(groupId)
}

function releaseAffinityBatchObservers (batch) {
  for (const unsubscribe of Object.values(batch.subscriptions || {})) unsubscribe()
  batch.subscriptions = {}
}

export function resetAffinityMemberBatch (batch, mode, targets) {
  releaseAffinityBatchObservers(batch)
  batch.token = batch.groupId + ':' + (++batchSequence)
  batch.mode = mode
  batch.targets = targets.map(affinityMemberSnapshot)
  batch.results = Object.fromEntries(targets.map(vm => [vm.id, { status: 'idle', error: '', jobId: '' }]))
}

// The cache, rather than the view, owns receipt observers. Completed receipts
// stay correlated to their batch when another dialog later updates the same VM.
export function observeAffinityMemberBatch (batch, vmId, operation) {
  if (batch.subscriptions[vmId]) return
  const token = batch.token
  batch.subscriptions[vmId] = operation.subscribe(state => {
    if (state.context === token) batch.results[vmId] = state
  })
}

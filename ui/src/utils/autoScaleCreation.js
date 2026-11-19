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

import { createJobTracker } from './jobTracker'

const resources = {
  profile: ['createAutoScaleVmProfile', 'deleteAutoScaleVmProfile', 'listAutoScaleVmProfiles', 'autoscalevmprofile'],
  condition: ['createCondition', 'deleteCondition', 'listConditions', 'condition'],
  policy: ['createAutoScalePolicy', 'deleteAutoScalePolicy', 'listAutoScalePolicies', 'autoscalepolicy'],
  group: ['createAutoScaleVmGroup', 'deleteAutoScaleVmGroup', 'listAutoScaleVmGroups', 'autoscalevmgroup']
}
const copy = value => JSON.parse(JSON.stringify(value))
const root = response => Object.values(response || {})[0] || {}
const uuid = () => {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  bytes[6] = (bytes[6] & 15) | 64
  bytes[8] = (bytes[8] & 63) | 128
  const hex = [...bytes].map(x => x.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
export const autoScaleCreationScope = (origin, user, project) => JSON.stringify([origin, user?.id, user?.account, user?.domainid, project?.id || ''])
export const creationStageLabel = stage => stage.type === 'profile' ? 'message.creating.autoscale.vmprofile' : stage.type === 'group' ? 'message.creating.autoscale.vmgroup' : stage.type === 'condition' ? 'label.conditions' : 'label.policy'

// The durable record deliberately excludes ALL profile parameters, including
// UserData, user variables, counter/SNMP parameters, credentials and API keys.
function durable (operation) {
  return {
    version: 1,
    id: operation.id,
    scope: operation.scope,
    name: operation.name,
    status: operation.status,
    created: operation.created,
    failureKind: operation.failureKind,
    stages: operation.stages.map(stage => ({
      key: stage.key,
      type: stage.type,
      requestId: stage.requestId,
      status: stage.status,
      owned: stage.owned,
      resourceId: stage.resourceId,
      jobId: stage.jobId,
      cleanup: stage.cleanup,
      cleanupJobId: stage.cleanupJobId,
      params: stage.type === 'profile' ? {} : stage.params,
      dependencies: stage.dependencies
    }))
  }
}

export function createAutoScaleCreation ({ api, storage, scope, getScope = () => scope, onChange = () => {}, makeId = uuid, pollOptions = {} }) {
  const storageKey = `autoscale.creation.v1:${scope}`
  let operation = null
  let active = true
  let busy = false
  let profileParams = null
  const check = () => { if (!active || getScope() !== scope) throw new Error('scope') }
  const emit = () => { if (active && getScope() === scope) onChange(operation ? copy(durable(operation)) : null, busy) }
  const save = () => {
    try {
      if (operation) storage.setItem(storageKey, JSON.stringify(durable(operation)))
    } finally { emit() }
  }
  const get = async (command, params) => {
    check()
    const response = await api.get(command, params, { backgroundJob: true, preserveOnFailure: true, timeout: 15000 })
    check()
    return root(response)
  }
  const tracker = createJobTracker({
    query: id => get('queryAsyncJobResult', { jobid: id }),
    onState: () => emit(),
    interval: 1000,
    maxRetries: 3,
    maxDuration: 15 * 60 * 1000,
    ...pollOptions
  })
  try {
    const stored = JSON.parse(storage.getItem(storageKey) || 'null')
    if (stored?.version === 1 && stored.scope === scope && Array.isArray(stored.stages)) {
      operation = durable(stored)
      // The old page no longer owns a tracker after reload or navigation.
      if (operation.status === 'running') { operation.status = 'unknown'; operation.failureKind = 'interrupted' }
    }
  } catch (e) {
    // Do not overwrite an unreadable ledger and accidentally permit another POST.
    operation = { version: 1, id: '', scope, name: '', status: 'unknown', failureKind: 'storage', stages: [] }
  }
  const request = async stage => {
    const response = await get('queryAutoScaleCreationRequest', { requestid: stage.requestId })
    const receipt = response.autoscalecreationrequest || response
    if (receipt.status !== 'NOTFOUND' && (receipt.creationoperationid !== operation.id || receipt.creationstage !== stage.key || receipt.command !== resources[stage.type][0] || (stage.resourceId && receipt.resourceid !== stage.resourceId))) throw new Error('receipt')
    if (receipt.resourceid) stage.resourceId = receipt.resourceid
    if (receipt.jobid) stage.jobId = receipt.jobid
    return receipt
  }
  let allowConfirmedReplay = false
  const reconcile = async stage => {
    const receipt = await request(stage)
    if (receipt.status === 'SUCCESS' && receipt.resourceid) { stage.status = 'success'; save(); return }
    if (['FAILED', 'REMOVED'].includes(receipt.status)) { stage.status = 'failed'; save(); return }
    if (allowConfirmedReplay && receipt.status === 'CREATED' && stage.type === 'group' && receipt.resourceid && !receipt.jobid) {
      allowConfirmedReplay = false
      await api.post(resources.group[0], parameters(stage), { preserveOnFailure: true, timeout: 45000 })
      check()
      return reconcile(stage)
    }
    // Only an explicit recovery action may replay an unaccepted request. The
    // original UUID is retained, so a late server acceptance is deduplicated.
    if (allowConfirmedReplay && receipt.status === 'NOTFOUND' && !stage.resourceId && !stage.jobId) {
      allowConfirmedReplay = false
      stage.status = 'new'; save()
      return execute(stage)
    }
    if (receipt.status === 'PENDING' && receipt.jobid) {
      stage.status = 'pending'; save()
      const result = await tracker.track(receipt.jobid, {}, true)
      check()
      if (result.jobstatus === 2) { stage.status = 'failed'; save(); return }
      if (result.jobstatus === 1) {
        const confirmed = await request(stage)
        if (confirmed.status === 'SUCCESS' && confirmed.resourceid) { stage.status = 'success'; save(); return }
      }
      operation.failureKind = result.trackingStatus || 'unknown'
    }
    stage.status = 'unknown'; save()
    throw new Error('unknown')
  }
  const parameters = stage => {
    const ids = keys => keys.map(key => operation.stages.find(item => item.key === key)?.resourceId).join(',')
    const params = stage.type === 'profile' ? profileParams : { ...stage.params }
    if (!params) throw new Error('profile')
    if (stage.type === 'policy') params.conditionids = ids(stage.dependencies.conditions)
    if (stage.type === 'group') {
      params.vmprofileid = ids(['profile'])
      params.scaleuppolicyids = ids(stage.dependencies.up)
      params.scaledownpolicyids = ids(stage.dependencies.down)
    }
    return { ...params, customjobid: stage.requestId, creationoperationid: operation.id, creationstage: stage.key }
  }
  const execute = async stage => {
    if (!stage.owned || stage.status === 'success') return
    if (stage.status === 'failed') return
    if (stage.status !== 'new') { await reconcile(stage); return }
    const params = parameters(stage)
    stage.status = 'submitting'; save() // persist intent BEFORE POST
    try {
      check()
      const response = root(await api.post(resources[stage.type][0], params, { preserveOnFailure: true, timeout: 45000 }))
      check()
      if (response.jobid) stage.jobId = response.jobid
      const resource = response[resources[stage.type][3]] || response
      if (resource.id) stage.resourceId = resource.id
      save()
      check()
    } catch (error) {
      check()
      try {
        const receipt = await request(stage)
        if (receipt.status === 'NOTFOUND' && (
          (error?.response?.status >= 400 && error.response.status < 500 && ![401, 403, 408, 429].includes(error.response.status)) ||
          ([530, 531, 532, 533, 534, 535, 536, 537].includes(error?.response?.status) && Number(root(error.response.data).errorcode) === error.response.status)
        )) {
          stage.status = 'failed'; save(); return
        }
      } catch (queryError) {
        stage.status = 'unknown'; save(); throw queryError
      }
      // Lost response does not mean failed creation. Query the preassigned UUID.
    }
    await reconcile(stage)
  }
  const present = async stage => {
    const result = await get(resources[stage.type][2], { id: stage.resourceId })
    return (result[resources[stage.type][3]] || []).some(item => item.id === stage.resourceId)
  }
  const clean = async () => {
    if (!operation?.id || !operation.stages.length) throw new Error('storage')
    if (!operation || operation.status === 'success') return
    // All attempted stages must have a definitive outcome before deleting anything.
    for (const stage of operation.stages) {
      if (!stage.owned || stage.status === 'new' || stage.cleanup === 'removed') continue
      const receipt = await request(stage)
      if (receipt.status === 'NOTFOUND' && stage.status === 'failed' && !stage.resourceId) continue
      if (!['SUCCESS', 'FAILED', 'REMOVED'].includes(receipt.status)) throw new Error('cleanup-unknown')
      if (stage.type === 'group' && receipt.status === 'SUCCESS') throw new Error('active-group')
      if (stage.type === 'group' && receipt.resourceid && receipt.resourcestate !== 'NEW' && receipt.status !== 'REMOVED') throw new Error('active-group')
    }
    let residual = false
    const seen = new Set()
    for (const stage of [...operation.stages].reverse()) {
      check()
      if (!stage.owned || !stage.resourceId || stage.cleanup === 'removed' || seen.has(stage.resourceId)) continue
      seen.add(stage.resourceId)
      try {
        if (!await present(stage)) { stage.cleanup = 'removed'; save(); continue }
        // Recheck ownership immediately before each DELETE; backend in-use checks
        // also protect resources which another group/policy started sharing.
        const receipt = await request(stage)
        if (receipt.status === 'REMOVED') { stage.cleanup = 'removed'; save(); continue }
        if (stage.cleanupJobId) {
          const result = await tracker.track(stage.cleanupJobId, {}, true)
          if (result.jobstatus !== 1) throw new Error('cleanup-job')
        } else {
          const params = { id: stage.resourceId, ...(stage.type === 'group' ? { cleanup: false } : {}) }
          let response
          try {
            response = root(await api.post(resources[stage.type][1], params, { preserveOnFailure: true, timeout: 45000, backgroundJob: true }))
          } catch (error) {
            check()
            if (await present(stage)) throw error
            response = { success: true }
          }
          check()
          if (response.jobid) {
            stage.cleanupJobId = response.jobid; save()
            const result = await tracker.track(response.jobid, {}, true)
            if (result.jobstatus !== 1) throw new Error('cleanup-job')
          }
        }
        if (await present(stage)) throw new Error('cleanup-remains')
        stage.cleanup = 'removed'
      } catch (error) {
        check()
        stage.cleanup = 'blocked'; stage.cleanupJobId = null; residual = true
      }
      save()
    }
    operation.status = residual ? 'residual' : 'cleaned'; save()
  }
  const guarded = async action => {
    if (busy) return
    busy = true; emit()
    try { check(); await action() } catch (error) {
      if (operation && active && getScope() === scope) {
        operation.status = 'unknown'
        operation.failureKind = operation.failureKind || error.message
        try { save() } catch (storageError) { operation.failureKind = 'storage' }
      }
    } finally { busy = false; emit() }
    return operation ? copy(durable(operation)) : null
  }
  const run = async () => {
    if (!operation?.id || !operation.stages.length) throw new Error('storage')
    operation.status = 'running'; operation.failureKind = null; save()
    for (const stage of operation.stages) {
      check()
      // Even completed stages are rechecked on recovery, preventing references to
      // removed resources. Existing/shared resources are never cleanup targets.
      if (stage.status === 'success' && stage.owned) await reconcile(stage)
      else await execute(stage)
      check()
      if (stage.status === 'failed') {
        operation.status = 'failed'; save(); await clean(); return
      }
    }
    operation.status = 'success'; save()
  }
  return {
    snapshot: () => operation ? copy(durable(operation)) : null,
    busy: () => busy,
    async start ({ name, group, up, down, profile }) {
      if (busy || (operation && !['success', 'cleaned'].includes(operation.status))) return this.snapshot()
      profileParams = { ...profile }
      const stages = [{ key: 'profile', type: 'profile', params: {}, dependencies: {} }]
      const policyKeys = { up: [], down: [] }
      for (const [direction, policies] of [['up', up], ['down', down]]) {
        policies.forEach((policy, index) => {
          const key = `${direction}.${index}.policy`
          const conditions = policy.conditions.map((condition, i) => {
            const conditionKey = `${direction}.${index}.condition.${i}`
            stages.push({ key: conditionKey, type: 'condition', owned: !condition.id, resourceId: condition.id, params: { counterid: condition.counterid, relationaloperator: condition.relationaloperator, threshold: condition.threshold }, dependencies: {} })
            return conditionKey
          })
          policyKeys[direction].push(key)
          stages.push({ key, type: 'policy', owned: !policy.id, resourceId: policy.id, params: { name: policy.name, action: direction === 'up' ? 'ScaleUp' : 'ScaleDown', duration: policy[direction === 'up' ? 'scaleupduration' : 'scaledownduration'], quiettime: policy[direction === 'up' ? 'scaleupquiettime' : 'scaledownquiettime'] }, dependencies: { conditions } })
        })
      }
      stages.push({ key: 'group', type: 'group', params: { name, lbruleid: group.lbruleid, minmembers: group.minmembers, maxmembers: group.maxmembers, interval: group.interval }, dependencies: policyKeys })
      operation = { version: 1, id: makeId(), scope, name, status: 'running', created: Date.now(), stages: stages.map(stage => ({ ...stage, owned: stage.owned !== false, requestId: makeId(), status: stage.resourceId ? 'success' : 'new' })) }
      // Failure to persist is settled by guarded; no API request has been sent.
      return guarded(run)
    },
    resume: ({ profile } = {}) => {
      if (profile) profileParams = { ...profile }
      allowConfirmedReplay = true
      return guarded(run)
    },
    cleanup: () => guarded(clean),
    forget () {
      if (busy || !['cleaned', 'success'].includes(operation?.status)) return
      storage.removeItem(storageKey); operation = null; profileParams = null; emit()
    },
    stop () {
      if (active && operation?.status === 'running' && getScope() === scope) {
        operation.status = 'unknown'; operation.failureKind = 'interrupted'
        try { save() } catch (e) { /* Keep the original ledger when storage is unavailable. */ }
      }
      active = false; tracker.clear()
    }
  }
}

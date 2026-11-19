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

import { createJobTracker } from '@/utils/jobTracker'

// Server-side owner scoping also includes eligible domain-wide groups.
export function affinityOwnerParams (resource) {
  if (!resource?.domainid || (!resource.projectid && !resource.account)) {
    throw new Error('message.affinity.owner.unavailable')
  }
  return resource.projectid
    ? { domainid: resource.domainid, projectid: resource.projectid }
    : { domainid: resource.domainid, account: resource.account }
}

export function affinityOwnerKey (resource) {
  return JSON.stringify(affinityOwnerParams(resource))
}

export function affinitySessionScope (store) {
  const user = store.getters.userInfo || {}
  return [store.state?.user?.token, user.id, user.accountid, user.domainid, store.getters.project?.id].join('|')
}

export function affinityApiError (error) {
  const body = error?.response?.data
  const result = Object.values(body || {}).find(value => value && typeof value === 'object' && value.errorcode)
  return result ? result.errortext || 'message.affinity.operation.failed' : ''
}

export function affinityVmCanChange (resource) {
  return resource?.state === 'Stopped' && resource.hypervisor !== 'External' &&
    !['sharedfsvm', 'cksnode'].includes(resource.vmtype)
}

let activeScope
const operations = new Map()

export function clearAffinityOperations () {
  for (const operation of operations.values()) operation.invalidate()
  operations.clear()
  activeScope = undefined
}

// Receipts survive closing a dialog or changing routes in this application session.
// The registry never submits again after an unknown POST outcome.
export function getAffinityOperation (scope, id, { get, post, getScope = () => scope, interval = 1500, maxDuration = 120000, maxRetries = 3 }) {
  if (scope !== activeScope) {
    clearAffinityOperations()
    activeScope = scope
  }
  if (operations.has(id)) return operations.get(id)
  let valid = true
  let state = { status: 'idle', jobId: '', ids: [], error: '', params: {}, context: '' }
  let running
  const listeners = new Set()
  const snapshot = () => ({ ...state, ids: [...state.ids], params: { ...state.params } })
  const current = () => {
    if (valid && getScope() !== scope) clearAffinityOperations()
    return valid
  }
  const publish = patch => {
    if (!current()) return
    state = { ...state, ...patch }
    for (const listener of listeners) {
      try { listener(snapshot()) } catch (_) { console.error('[affinity] observer failed') }
    }
  }
  const receive = (jobId, result) => {
    if (jobId !== state.jobId) return
    if (result.jobstatus === 1) publish({ status: 'succeeded', error: '' })
    else if (result.jobstatus === 2) publish({ status: 'failed', error: result.jobresult?.errortext || 'message.affinity.operation.failed' })
    else if (result.trackingStatus === 'retrying') publish({ status: 'retrying' })
    else if (result.trackingStatus) publish({ status: result.trackingStatus, error: '' })
    else publish({ status: 'pending', error: '' })
  }
  const tracker = createJobTracker({
    interval,
    maxDuration,
    maxRetries,
    query: async jobId => {
      if (!current()) throw new Error('Scope changed')
      const response = await get('queryAsyncJobResult', { jobid: jobId }, { backgroundJob: true, preserveOnFailure: true, timeout: 15000 })
      return response.queryasyncjobresultresponse
    },
    onState: receive
  })
  const track = retry => {
    publish({ status: 'pending', error: '' })
    const jobId = state.jobId
    running = tracker.track(jobId, {}, retry).then(result => {
      // The shared tracker may return an already-known terminal receipt.
      if (!['succeeded', 'failed'].includes(state.status)) receive(jobId, result)
      return snapshot()
    })
    return running
  }
  const operation = {
    snapshot,
    subscribe (listener) {
      listeners.add(listener)
      listener(snapshot())
      return () => listeners.delete(listener)
    },
    invalidate () {
      valid = false
      listeners.clear()
      tracker.clear()
    },
    acknowledge () {
      if (['succeeded', 'failed'].includes(state.status)) state = { status: 'idle', jobId: '', ids: [], error: '', params: {}, context: '' }
    },
    run (command, params, prepare = async () => {}, context = '') {
      if (!current()) return Promise.resolve(snapshot())
      if (!['idle', 'failed'].includes(state.status)) return running || Promise.resolve(snapshot())
      publish({ status: 'preparing', context, params: { ...params }, jobId: '', ids: [...new Set(params.affinitygroupids?.split(',').filter(Boolean) || [])], error: '' })
      running = (async () => {
        try {
          const prepared = await prepare()
          if (prepared) {
            params = { ...params, ...prepared }
            publish({ params: { ...params }, ids: [...new Set(params.affinitygroupids?.split(',').filter(Boolean) || [])] })
          }
        } catch (error) {
          publish({ status: 'failed', error: affinityApiError(error) || error.message || 'message.affinity.operation.failed' })
          return snapshot()
        }
        if (!current()) return snapshot()
        publish({ status: 'submitting' })
        let response
        try {
          response = await post(command, params, { preserveOnFailure: true, backgroundJob: true, timeout: 30000 })
        } catch (error) {
          const apiError = affinityApiError(error)
          publish({ status: apiError ? 'failed' : 'unknown', error: apiError })
          return snapshot()
        }
        if (!current()) return snapshot()
        // UpdateVMAffinityGroupCmd reuses UpdateVMCmd's response name.
        const responseName = command === 'updateVMAffinityGroup' ? 'updatevirtualmachineresponse' : command.toLowerCase() + 'response'
        const envelope = response?.[responseName]
        if (!envelope?.jobid) {
          publish({ status: 'unknown', error: '' })
          return snapshot()
        }
        publish({ jobId: envelope.jobid })
        return track(false)
      })()
      return running
    },
    recheck () {
      if (!current() || !state.jobId || !['unknown', 'timeout', 'cancelled'].includes(state.status)) return running || Promise.resolve(snapshot())
      return track(true)
    }
  }
  operations.set(id, operation)
  return operation
}

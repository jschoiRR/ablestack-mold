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

export function autoScaleOwnerParams (resource) {
  return resource.projectid ? { projectid: resource.projectid } : { domainid: resource.domainid, account: resource.account }
}

export async function runAutoScaleMutation (post, get, command, params) {
  let response
  try {
    response = await post(command, params)
  } catch (error) {
    const apiError = error?.response?.data?.errorresponse
    const status = error?.response?.status
    error.trackingStatus = apiError || [400, 401, 403, 404].includes(status) ? 'failed' : 'unknown'
    throw error
  }
  const envelope = Object.values(response || {}).find(value => value && typeof value === 'object')
  const jobId = envelope?.jobid
  if (!jobId) {
    const error = new Error('Missing async job receipt')
    error.trackingStatus = 'unknown'
    throw error
  }
  const tracker = createJobTracker({
    query: async id => (await get('queryAsyncJobResult', { jobid: id })).queryasyncjobresultresponse,
    onState: () => {},
    interval: 1000,
    maxRetries: 3,
    maxDuration: 15 * 60 * 1000
  })
  let result
  try {
    result = await tracker.track(jobId)
  } finally {
    tracker.clear()
  }
  if (result?.jobstatus !== 1) {
    const error = new Error(result?.jobresult?.errortext || 'Async job outcome is unknown')
    error.trackingStatus = result?.jobstatus === 2 ? 'failed' : 'unknown'
    error.jobId = jobId
    throw error
  }
  return result.jobresult || {}
}

// Lists only resources visible to the caller in the current owner/project scope.
export async function autoScaleAffectedGroups (get, resource, selector) {
  const groups = []
  let page = 1
  let rows
  do {
    const json = await get('listAutoScaleVmGroups', { ...autoScaleOwnerParams(resource), listall: true, page, pagesize: 100 })
    rows = json.listautoscalevmgroupsresponse?.autoscalevmgroup || []
    groups.push(...rows)
    page++
  } while (rows.length === 100)
  return groups.filter(group => {
    if (selector.profileId) return group.vmprofileid === selector.profileId
    const policies = [...(group.scaleuppolicies || []), ...(group.scaledownpolicies || [])]
    if (selector.policyId) return policies.some(policy => policy.id === selector.policyId)
    return policies.some(policy => (policy.conditions || []).some(condition => condition.id === selector.conditionId))
  }).map(group => group.name || group.id)
}

export function autoScaleMutationDescription (error, t) {
  if (error?.trackingStatus === 'unknown') return t('message.autoscale.edit.unknown', { id: error.jobId || '-' })
  const message = error?.response?.data?.errorresponse?.errortext || error?.message || String(error)
  const translations = [
    ['must be a positive integer in GiB', 'message.autoscale.profile.size.invalid'],
    ['disk offering must exist and be active', 'message.autoscale.profile.disk.invalid'],
    ['strict service offering', 'message.autoscale.profile.disk.strict'],
    ['network must exist in the profile zone', 'message.autoscale.profile.network.invalid'],
    ['SSH keypair must belong', 'message.autoscale.profile.key.invalid'],
    ['affinity group must exist', 'message.autoscale.profile.affinity.invalid'],
    ['security group must exist', 'message.autoscale.profile.security.invalid'],
    ['disksize requires a data disk offering', 'message.autoscale.profile.data.invalid'],
    ['custom data disk offering requires disksize', 'message.autoscale.profile.data.size'],
    ['disksize is only supported', 'message.autoscale.profile.data.fixed'],
    ['template is not ready', 'message.autoscale.profile.template.ready'],
    ['active fixed service offering', 'message.autoscale.profile.offering.invalid'],
    ['associated with is disabled', 'message.autoscale.edit.disabled.required'],
    ['must be disabled', 'message.autoscale.edit.disabled.required'],
    ['non-disabled', 'message.autoscale.edit.disabled.required'],
    ['Unknown resource schedule timezone', 'message.autoscale.schedule.timezone.invalid'],
    ['nonexistent or ambiguous due to DST', 'message.autoscale.schedule.dst.invalid']
  ]
  const translated = translations.find(([match]) => message.includes(match))
  return translated ? t(translated[1]) : message
}

export async function createAndAttachAutoScale ({ createCondition, createPolicy, attach, removePolicy, removeCondition, retained }) {
  let condition, policy
  try {
    condition = await createCondition()
    if (createPolicy) policy = await createPolicy(condition)
    await attach(policy || condition)
    return { condition, policy }
  } catch (error) {
    if (error.trackingStatus === 'failed') {
      try {
        if (policy) { await removePolicy(policy.id); policy = null }
        if (condition) { await removeCondition(condition.id); condition = null }
      } catch (cleanupError) {
        // Never remove a condition if deleting its policy was uncertain or failed.
      }
    }
    if (condition || policy) retained({ condition: condition?.id, policy: policy?.id })
    throw error
  }
}

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

export function autoScaleStateSummary (counts) {
  if (!counts || typeof counts !== 'object') return null
  const total = states => states.reduce((sum, state) => sum + (Number(counts[state]) || 0), 0)
  const known = ['Running', 'Starting', 'Stopping', 'Migrating', 'Stopped', 'Error', 'Unknown', 'Destroyed', 'Expunging', 'Removed', 'Missing']
  return {
    running: total(['Running']),
    transition: total(['Starting', 'Stopping', 'Migrating']),
    stopped: total(['Stopped']),
    error: total(['Error', 'Unknown']),
    cleanup: total(['Destroyed', 'Expunging', 'Removed', 'Missing']),
    other: total(Object.keys(counts).filter(state => !known.includes(state))),
    total: total(Object.keys(counts))
  }
}

export function autoScaleTime (time, format, translate) {
  const date = new Date(time)
  return time && Number.isFinite(date.getTime()) ? format(date.toISOString()) : translate('label.autoscale.not.recorded')
}

export function autoScaleEvaluationChanged (resource) {
  const evaluated = resource.evaluation?.policies || []
  const configured = [...(resource.scaleuppolicies || []), ...(resource.scaledownpolicies || [])]
  return evaluated.some(policy => {
    const current = configured.find(item => item.id === policy.id)
    if (!current || Number(current.duration) !== Number(policy.duration)) return true
    return (policy.conditions || []).some(metric => {
      const condition = (current.conditions || []).find(item => item.id === metric.id)
      return !condition || condition.relationaloperator !== metric.operator || Number(condition.threshold) !== Number(metric.threshold)
    })
  })
}

export function autoScaleLbState (vm, state) {
  if (state.denied) return 'DENIED'
  if (state.failed) return state.loaded ? 'STALE' : 'FAILED'
  if (!state.loaded) return 'WAITING'
  const mapped = state.rows.find(item => item.id === vm.id)
  return mapped ? (mapped.servicestate || 'REGISTERED') : 'UNREGISTERED'
}

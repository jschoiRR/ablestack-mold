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

export const autoScaleOperators = ['GT', 'GE', 'LT', 'LE', 'EQ']

export function validAutoScalePolicyTiming (policy, interval) {
  const integer = value => (typeof value === 'number' || typeof value === 'string') && /^\d+$/.test(String(value)) && Number.isInteger(Number(value)) && Number(value) <= 2147483647
  return integer(policy.duration) && Number(policy.duration) > 0 && Number(policy.duration) >= Number(interval || 1) &&
    integer(policy.quiettime) && Number(policy.quiettime) >= 0
}

export function autoScaleEvaluationValue (value) {
  return typeof value === 'number' && Number.isFinite(value) ? value.toLocaleString(undefined, { maximumFractionDigits: 3 }) : '—'
}

const counterLabels = {
  'VM CPU - average percentage': 'cpu',
  'VM Memory - average percentage': 'memory',
  'Public Network - mbps received per vm': 'network.rx',
  'Public Network - mbps transmit per vm': 'network.tx',
  'Load Balancer - average connections per vm': 'connections'
}

export function autoScaleCounterLabel (name, translate) {
  return counterLabels[name] ? translate('label.autoscale.counter.' + counterLabels[name]) : name
}

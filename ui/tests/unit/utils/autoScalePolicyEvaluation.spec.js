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

import { autoScaleOperators, validAutoScalePolicyTiming, autoScaleEvaluationValue, autoScaleCounterLabel } from '@/utils/autoScalePolicyEvaluation'
import UpPolicy from '@/views/compute/AutoScaleUpPolicyTab.vue'
import DownPolicy from '@/views/compute/AutoScaleDownPolicyTab.vue'
import Evaluation from '@/views/compute/AutoScaleEvaluation.vue'
import ko from '../../../public/locales/ko_KR.json'
import en from '../../../public/locales/en.json'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
jest.mock('@/store', () => ({ getters: { apis: {}, userInfo: {}, project: null } }))

test('all supported comparison operators are available', () => {
  expect(autoScaleOperators).toEqual(['GT', 'GE', 'LT', 'LE', 'EQ'])
})
test.each([0, -1, 9, 1.5, 2147483648, '', null, undefined])('invalid duration %s does not submit', duration => {
  expect(validAutoScalePolicyTiming({ duration, quiettime: 0 }, 10)).toBe(false)
})
test.each([10, 60, '60', 2147483647])('valid duration %s and zero quiet time', duration => {
  expect(validAutoScalePolicyTiming({ duration, quiettime: 0 }, 10)).toBe(true)
})
test.each([-1, 0.5, 2147483648, '', null, undefined])('invalid quiet time %s does not submit', quiettime => {
  expect(validAutoScalePolicyTiming({ duration: 60, quiettime }, 10)).toBe(false)
})
test.each([null, undefined, NaN, Infinity, '0'])('missing/invalid %s is not displayed as zero', value => {
  expect(autoScaleEvaluationValue(value)).toBe('—')
})
test('actual zero is displayed as zero', () => {
  expect(autoScaleEvaluationValue(0)).toBe('0')
})
test('network scope and memory capability notices follow observed metrics', () => {
  expect(Evaluation.computed.hasNetworkMetric.call({ metrics: [{ unit: 'Mbps/VM' }] })).toBe(true)
  expect(Evaluation.computed.hasMemoryMetric.call({ metrics: [{ source: 'MEMORY' }] })).toBe(true)
  expect(Evaluation.computed.hasNetworkMetric.call({ metrics: [{ unit: '%' }] })).toBe(false)
})
test('policy names resolve within the current group without showing internal ids', () => {
  const ctx = { resource: { scaleuppolicies: [{ id: 'up', name: '확장 정책' }] }, $t: key => key }
  expect(Evaluation.methods.policyName.call(ctx, { id: 'up' })).toBe('확장 정책')
  expect(Evaluation.methods.policyName.call(ctx, { id: 'unknown', action: 'SCALEUP' })).toBe('label.autoscale.operation.SCALEUP')
})
test('all evaluation status/reason labels exist in Korean and English', () => {
  const statuses = ['WAITING', 'NO_ACTION', 'SCALEUP', 'SCALEDOWN', 'MAX_MEMBERS', 'MIN_MEMBERS', 'COOLDOWN', 'SCALEUP_UNCERTAIN', 'MATCHED', 'NOT_MATCHED', 'BLOCKED']
  const reasons = ['INVALID_CONFIGURATION', 'NO_RESOURCES', 'NO_SAMPLES', 'MISSING_RESOURCE', 'INVALID_SAMPLE', 'STALE_SAMPLE', 'MISSING_SAMPLE', 'COUNTER_RESET', 'INSUFFICIENT_DURATION', 'MEMBERS_CHANGED', 'COLLECTION_UNAVAILABLE']
  for (const locale of [ko, en]) {
    for (const status of statuses) expect(locale['label.autoscale.evaluation.' + status]).toBeTruthy()
    for (const reason of reasons) expect(locale['message.autoscale.evaluation.' + reason]).toBeTruthy()
  }
})

test('known counters use translated labels and custom names are preserved', () => {
  const translate = key => ko[key]
  expect(autoScaleCounterLabel('VM CPU - average percentage', translate)).toBe('VM CPU 평균 사용률')
  expect(autoScaleCounterLabel('Load Balancer - average connections per vm', translate)).toBe('VM당 부하 분산 평균 연결 수')
  expect(autoScaleCounterLabel('관리자 카운터', translate)).toBe('관리자 카운터')
})

test.each([UpPolicy, DownPolicy])('condition editor accepts actual numeric zero', async component => {
  const field = () => ({ classList: { add: jest.fn(), remove: jest.fn() } })
  const ctx = {
    loading: false,
    newCondition: { counterid: 'cpu', relationaloperator: 'GE', threshold: 0 },
    resource: { account: 'owner', domainid: 'domain' },
    $refs: { newConditionCounterId: field(), newConditionRelationalOperator: field(), newConditionThreshold: field() },
    isNumber: component.methods.isNumber,
    addCondition: jest.fn().mockResolvedValue({ id: 'new' }),
    policy: { id: 'policy', conditions: [{ id: 'existing' }] },
    saveEdit: action => action(),
    mutate: jest.fn().mockResolvedValue({}),
    deleteCondition: jest.fn(),
    recordRetained: jest.fn(),
    $notification: { error: jest.fn() }
  }
  await component.methods.addConditionToPolicy.call(ctx)
  expect(ctx.addCondition).toHaveBeenCalledWith(expect.objectContaining({ threshold: 0 }))
  expect(ctx.mutate).toHaveBeenCalledWith('updateAutoScalePolicy', { id: 'policy', conditionids: 'existing,new' })
})

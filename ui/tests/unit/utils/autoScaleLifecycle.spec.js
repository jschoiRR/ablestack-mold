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

import { autoScaleLifecycleNotice, autoScaleLifecycleErrorKey } from '@/utils/autoScaleLifecycle'

describe('AutoScale lifecycle notices', () => {
  it('keeps accepted scale work visible after disable', () => {
    expect(autoScaleLifecycleNotice({ state: 'DISABLED', scalingaction: 'SCALEDOWN', scalingphase: 'DRAINING' })).toEqual({ type: 'warning', message: 'message.autoscale.operation.stopping', description: 'message.autoscale.lifecycle.members.retained' })
  })
  it.each(['STARTING_RETRY', 'ASSIGNING_RETRY', 'EXCLUDING_RETRY', 'DESTROYING_RETRY'])('explains failure and automatic reconciliation for %s', scalingphase => {
    const notice = autoScaleLifecycleNotice({ state: 'SCALING', scalingphase })
    expect(notice.type).toBe('warning')
    expect(notice.message).toBe('message.autoscale.lifecycle.retry')
  })
  it('explains cleanup completion followed by explicit group deletion retry', () => {
    const notice = autoScaleLifecycleNotice({ state: 'DISABLED', scalingaction: 'CLEANUP', scalingphase: 'DESTROYING_RETRY' })
    expect(notice.description).toBe('message.autoscale.lifecycle.cleanup.retry')
    expect(notice.message).toBe('message.autoscale.lifecycle.retry')
  })
  it('distinguishes normal group cleanup from scale down', () => {
    expect(autoScaleLifecycleNotice({ state: 'DISABLED', scalingaction: 'CLEANUP', scalingphase: 'DRAINING' }).message).toBe('message.autoscale.lifecycle.cleanup')
  })
})

describe('AutoScale lifecycle API error localization', () => {
  it.each([
    ['Cannot delete autoscale vm group : internal details. because there are 2 VMs. Please remove the VMs or pass cleanup=true flag which will destroy all VMs.', 'message.autoscale.lifecycle.delete.members'],
    ['Failed to delete autoscale vm group', 'message.autoscale.lifecycle.delete.failed'],
    ['Cannot perform actions on VM in an AutoScale VM group, Please disable the VM group and retry.', 'message.autoscale.lifecycle.vm.action.blocked'],
    ['AutoScale VM group has an operation in progress. Wait for it to finish and retry', 'message.autoscale.operation.wait']
  ])('localizes a known API failure without exposing internal objects', (description, key) => {
    expect(autoScaleLifecycleErrorKey(description)).toBe(key)
  })
  it.each([undefined, '', 'unrelated storage failure'])('preserves unrelated diagnostics: %s', description => {
    expect(autoScaleLifecycleErrorKey(description)).toBeNull()
  })
})

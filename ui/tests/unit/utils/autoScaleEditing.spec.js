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

import { runAutoScaleMutation, autoScaleOwnerParams, createAndAttachAutoScale, autoScaleAffectedGroups } from '@/utils/autoScaleEditing'
import { createJobTracker } from '@/utils/jobTracker'
jest.mock('@/utils/jobTracker', () => ({ createJobTracker: jest.fn() }))
const tracker = { track: jest.fn(), clear: jest.fn() }
beforeEach(() => { jest.clearAllMocks(); createJobTracker.mockReturnValue(tracker) })
test('a project edit creates objects in the project without account fallback', () => {
  expect(autoScaleOwnerParams({ projectid: 'project', account: 'admin' })).toEqual({ projectid: 'project' })
})
test('mutation awaits confirmed async completion and clears the tracker', async () => {
  tracker.track.mockResolvedValue({ jobstatus: 1, jobresult: { condition: { id: 'saved' } } })
  const post = jest.fn().mockResolvedValue({ conditionresponse: { jobid: 'job' } })
  expect(await runAutoScaleMutation(post, jest.fn(), 'createCondition', {})).toEqual({ condition: { id: 'saved' } })
  expect(tracker.track).toHaveBeenCalledWith('job'); expect(tracker.clear).toHaveBeenCalled()
  expect(post).toHaveBeenCalledTimes(1)
})
test.each([null, 2])('unknown or failed job never replays POST (%s)', async status => {
  tracker.track.mockResolvedValue({ jobstatus: status, jobresult: { errortext: 'failed' } })
  const post = jest.fn().mockResolvedValue({ updateconditionresponse: { jobid: 'job' } })
  await expect(runAutoScaleMutation(post, jest.fn(), 'updateCondition', {})).rejects.toMatchObject({ jobId: 'job', trackingStatus: status === 2 ? 'failed' : 'unknown' })
  expect(post).toHaveBeenCalledTimes(1)
})
test('POST response loss is unknown without a replay', async () => {
  const post = jest.fn().mockRejectedValue(new Error('connection lost'))
  await expect(runAutoScaleMutation(post, jest.fn(), 'createCondition', {})).rejects.toMatchObject({ trackingStatus: 'unknown' })
  expect(post).toHaveBeenCalledTimes(1)
})
const linked = status => ({
  createCondition: jest.fn().mockResolvedValue({ id: 'condition' }),
  createPolicy: jest.fn().mockResolvedValue({ id: 'policy' }),
  attach: jest.fn().mockRejectedValue(Object.assign(new Error('failed'), { trackingStatus: status })),
  removePolicy: jest.fn().mockResolvedValue({}),
  removeCondition: jest.fn().mockResolvedValue({}),
  retained: jest.fn()
})
test('confirmed attachment failure compensates newly created metadata in dependency order', async () => {
  const options = linked('failed')
  await expect(createAndAttachAutoScale(options)).rejects.toThrow()
  expect(options.removePolicy).toHaveBeenCalledWith('policy'); expect(options.removeCondition).toHaveBeenCalledWith('condition')
  expect(options.removePolicy.mock.invocationCallOrder[0]).toBeLessThan(options.removeCondition.mock.invocationCallOrder[0])
  expect(options.retained).not.toHaveBeenCalled()
})
test('unknown attachment result preserves metadata without compensation', async () => {
  const options = linked('unknown'); await expect(createAndAttachAutoScale(options)).rejects.toThrow()
  expect(options.removePolicy).not.toHaveBeenCalled(); expect(options.removeCondition).not.toHaveBeenCalled()
  expect(options.retained).toHaveBeenCalledWith({ condition: 'condition', policy: 'policy' })
})
test('uncertain policy cleanup preserves its condition', async () => {
  const options = linked('failed'); options.removePolicy.mockRejectedValue(new Error('lost'))
  await expect(createAndAttachAutoScale(options)).rejects.toThrow()
  expect(options.removeCondition).not.toHaveBeenCalled(); expect(options.retained).toHaveBeenCalled()
})
test('impact lookup stays scoped and includes both directions', async () => {
  const get = jest.fn().mockResolvedValue({
    listautoscalevmgroupsresponse: {
      autoscalevmgroup: [
        { id: 'one', name: 'shared', scaledownpolicies: [{ id: 'p', conditions: [{ id: 'c' }] }] }, { id: 'two', vmprofileid: 'other' }
      ]
    }
  })
  expect(await autoScaleAffectedGroups(get, { projectid: 'project' }, { conditionId: 'c' })).toEqual(['shared'])
  expect(get.mock.calls[0][1]).toMatchObject({ projectid: 'project' })
})

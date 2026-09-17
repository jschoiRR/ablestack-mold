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
const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve() }
describe('job tracker', () => {
  beforeEach(() => jest.useFakeTimers())
  afterEach(() => jest.useRealTimers())
  it('tracks two jobs independently and deduplicates calls', async () => {
    const calls = {}; const onState = jest.fn()
    const tracker = createJobTracker({ query: jest.fn(id => Promise.resolve({ jobstatus: (calls[id] = (calls[id] || 0) + 1) === 1 ? 0 : 1 })), onState })
    const a = tracker.track('a'); expect(tracker.track('a')).toBe(a)
    const b = tracker.track('b'); await flush(); jest.runOnlyPendingTimers(); await flush()
    expect((await a).jobstatus).toBe(1); expect((await b).jobstatus).toBe(1)
    expect(tracker.track('a')).toBe(a); expect(calls).toEqual({ a: 2, b: 2 })
  })
  it('retries a rejected query and recovers without marking task failed', async () => {
    const query = jest.fn().mockRejectedValueOnce(new Error('network')).mockResolvedValue({ jobstatus: 1 })
    const onState = jest.fn(); const tracker = createJobTracker({ query, onState }); const job = tracker.track('a')
    await flush(); expect(onState.mock.calls[0][1].trackingStatus).toBe('retrying')
    jest.runOnlyPendingTimers(); await flush(); expect((await job).jobstatus).toBe(1)
  })
  it('bounds retries and allows explicit recheck after unknown result', async () => {
    const query = jest.fn().mockRejectedValue(new Error('offline')); const onState = jest.fn()
    const tracker = createJobTracker({ query, onState, maxRetries: 1 }); const job = tracker.track('a')
    await flush(); jest.runOnlyPendingTimers(); await flush()
    expect((await job).trackingStatus).toBe('unknown'); expect(query).toHaveBeenCalledTimes(2)
    query.mockResolvedValue({ jobstatus: 1 }); expect((await tracker.track('a', {}, true)).jobstatus).toBe(1)
  })
  it('does not allow a late response after scope cancellation to regress status', async () => {
    let respond; const onState = jest.fn(); const tracker = createJobTracker({ query: () => new Promise(resolve => { respond = resolve }), onState })
    const job = tracker.track('a'); tracker.clear(); expect((await job).trackingStatus).toBe('cancelled')
    respond({ jobstatus: 0 }); await flush(); expect(onState).toHaveBeenCalledTimes(1)
  })
  it('settles even when notification callbacks throw', async () => {
    const error = jest.spyOn(console, 'error').mockImplementation(() => {})
    const tracker = createJobTracker({ query: () => Promise.resolve({ jobstatus: 2 }), onState: () => { throw new Error('listener') } })
    expect((await tracker.track('a')).jobstatus).toBe(2); error.mockRestore()
  })
  it('does not retry authorization errors', async () => {
    const query = jest.fn().mockRejectedValue({ response: { status: 401 } })
    const tracker = createJobTracker({ query, onState: () => {} })
    expect((await tracker.track('a')).trackingStatus).toBe('unknown'); expect(query).toHaveBeenCalledTimes(1)
  })
})

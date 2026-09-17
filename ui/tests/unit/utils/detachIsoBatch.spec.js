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

import { detachIsoBatch } from '@/utils/detachIsoBatch'
describe('ISO detach batch', () => {
  it('waits for job completion before submitting another ISO', async () => {
    const order = []
    const results = await detachIsoBatch({ ids: ['a', 'b'], submit: async id => { order.push('submit ' + id); return { jobid: id } }, poll: async id => { order.push('complete ' + id); return { jobstatus: 1 } }, onProgress: () => {} })
    expect(order).toEqual(['submit a', 'complete a', 'submit b', 'complete b']); expect(results).toHaveLength(2)
  })
  it('continues explicit partial failure, but stops after an unknown outcome', async () => {
    const submit = jest.fn(async id => ({ jobid: id }))
    const results = await detachIsoBatch({ ids: ['a', 'b', 'c'], submit, poll: async id => id === 'a' ? { jobstatus: 2 } : { trackingStatus: 'unknown' }, onProgress: () => {} })
    expect(results).toHaveLength(2); expect(submit).toHaveBeenCalledTimes(2)
  })
  it('does not interpret missing jobid or network loss as success', async () => {
    for (const submit of [async () => ({}), async () => { throw new Error('offline') }, async () => Promise.reject(Object.assign(new Error('gateway'), { response: { status: 502, data: '<html>gateway error</html>' } }))]) {
      const results = await detachIsoBatch({ ids: ['a', 'b'], submit, poll: jest.fn(), onProgress: () => {} })
      expect(results).toHaveLength(1); expect(results[0].trackingStatus).toBe('unknown')
    }
  })
  it('does not submit into a changed security scope', async () => {
    const submit = jest.fn()
    expect(await detachIsoBatch({ ids: ['a'], submit, isCurrent: () => false })).toEqual([])
    expect(submit).not.toHaveBeenCalled()
  })
})

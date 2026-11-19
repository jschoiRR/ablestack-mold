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

import { storageTags, offeringStoragePools, readStoragePools } from '@/utils/volumeStoragePools'
const pools = [
  { id: 'both', state: 'Up', tags: ' fast, shared,fast ' },
  { id: 'one', state: 'Up', tags: 'fast' },
  { id: 'substring', state: 'Up', tags: 'faster,shared' },
  { id: 'none', state: 'Up' },
  { id: 'down', state: 'Maintenance', tags: 'fast,shared' },
  { id: 'rule', state: 'Up', tags: 'fast,shared', istagarule: true }
]
test('normalizes tags without changing case or accepting partial matches', () => {
  expect(storageTags(' fast, ,shared,fast ')).toEqual(['fast', 'shared'])
  expect(offeringStoragePools(pools, { tags: 'fast,shared' }).map(p => p.id)).toEqual(['both'])
  expect(offeringStoragePools(pools, { tags: 'FAST' })).toEqual([])
  expect(offeringStoragePools(pools, { tags: 'missing' })).toEqual([])
  expect(offeringStoragePools(pools, null)).toEqual([])
})
test('untagged offerings retain Up pools; required tags exclude unverified server rules', () => {
  expect(offeringStoragePools(pools, { tags: '' }).map(p => p.id)).toEqual(['both', 'one', 'substring', 'none', 'rule'])
  expect(offeringStoragePools(pools, { tags: 'fast' }).map(p => p.id)).toEqual(['both', 'one'])
})
test('collects every page before filtering; duplicate or missing pages fail instead of publishing partial candidates', async () => {
  const api = jest.fn().mockResolvedValueOnce({ liststoragepoolsresponse: { count: 2, storagepool: [pools[1]] } }).mockResolvedValueOnce({ liststoragepoolsresponse: { count: 2, storagepool: [pools[0]] } })
  expect(offeringStoragePools(await readStoragePools(api, 'zone', () => true), { tags: 'fast,shared' }).map(p => p.id)).toEqual(['both'])
  expect(api.mock.calls[1][1]).toMatchObject({ page: 2, pagesize: 100, zoneid: 'zone' })
  expect(api.mock.calls[1][2]).toEqual({ preserveOnFailure: true })
  api.mockResolvedValue({ liststoragepoolsresponse: { count: 2, storagepool: [pools[1]] } })
  await expect(readStoragePools(api, 'zone', () => true)).rejects.toThrow('Incomplete')
  api.mockResolvedValue({})
  await expect(readStoragePools(api, 'zone', () => true)).rejects.toThrow('Missing')
})
test('discarded requests never publish a partial list; a later page failure is retained as a failure', async () => {
  const api = jest.fn().mockResolvedValue({ liststoragepoolsresponse: { count: 2, storagepool: [pools[0]] } })
  expect(await readStoragePools(api, 'old', () => false)).toBeNull()
  api.mockResolvedValueOnce({ liststoragepoolsresponse: { count: 2, storagepool: [pools[0]] } }).mockRejectedValueOnce(new Error('offline'))
  await expect(readStoragePools(api, 'zone', () => true)).rejects.toThrow('offline')
})

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

export function storageTags (tags) {
  return [...new Set(String(tags || '').split(',').map(tag => tag.trim()).filter(Boolean))]
}

export function offeringStoragePools (pools, offering) {
  if (!offering) return []
  const required = storageTags(offering.tags)
  return pools.filter(pool => pool.state === 'Up' && (!required.length ||
    (!pool.istagarule && required.every(tag => storageTags(pool.tags).includes(tag)))))
}

// listStoragePools is paginated. Publish only after every page has succeeded.
export async function readStoragePools (getAPI, zoneid, isCurrent) {
  const pools = []
  const seen = new Set()
  let page = 1
  let total = 0
  do {
    const json = await getAPI('listStoragePools', { zoneid, showicon: true, page, pagesize: 100 }, { preserveOnFailure: true })
    if (!isCurrent()) return null
    const result = json.liststoragepoolsresponse
    if (!result) throw new Error('Missing storage pool response')
    const batch = result.storagepool || []
    total = Number(result.count || 0)
    const fresh = batch.filter(pool => !seen.has(pool.id))
    if (total > pools.length && !fresh.length) throw new Error('Incomplete storage pool response')
    fresh.forEach(pool => seen.add(pool.id))
    pools.push(...fresh)
    page++
  } while (pools.length < total)
  return pools
}

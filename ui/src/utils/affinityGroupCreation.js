// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

export const affinitySearchKeys = ['name', 'type', 'domainid', 'account', 'projectid']

// Upgrade41910to42000 uses VARCHAR(191) for indexed utf8mb4 names.
// Parameter.length defaults to 255 Java UTF-16 code units, including description.
export function affinityNameValid (name) {
  const text = (name || '').trim()
  return !!text && [...text].length <= 191 && text.length <= 255
}

export function affinityTypeOptions (response) {
  const root = response?.listaffinitygrouptypesresponse
  if (!root || (!Array.isArray(root.affinityGroupType) && root.count !== 0 && Object.keys(root).length)) throw new Error('Invalid affinity types response')
  return [...new Set((root.affinityGroupType || []).map(row => row.type).filter(type => typeof type === 'string' && type.trim()))]
    .map(type => ({
      id: type,
      name: type,
      labelKey: {
        'host affinity': 'label.affinity.type.strict.affinity',
        'host anti-affinity': 'label.affinity.type.strict.anti',
        'non-strict host affinity': 'label.affinity.type.non.strict.affinity',
        'non-strict host anti-affinity': 'label.affinity.type.non.strict.anti'
      }[type]
    }))
}

export async function affinityOwnerOptions (get, kind, domainid) {
  const spec = { domain: ['listDomains', 'listdomainsresponse', 'domain'], account: ['listAccounts', 'listaccountsresponse', 'account'], project: ['listProjects', 'listprojectsresponse', 'project'] }[kind]
  const rows = new Map()
  for (let page = 1; page <= 100; page++) {
    const params = { listall: true, page, pagesize: 500, ignoreproject: true, ...(kind === 'domain' ? { details: 'min' } : { ...(domainid ? { domainid } : {}), isrecursive: false, state: kind === 'project' ? 'Active' : 'Enabled' }) }
    const json = await get(spec[0], params, { preserveOnFailure: true, timeout: 15000 })
    const root = json?.[spec[1]]
    if (!root || (!Array.isArray(root[spec[2]]) && root.count !== 0 && Object.keys(root).length)) throw new Error('Invalid owner response')
    const chunk = root[spec[2]] || []
    chunk.forEach(row => rows.set(row.id, row))
    if (root.count != null ? rows.size >= root.count : chunk.length < 500) return [...rows.values()]
    if (!chunk.length) throw new Error('Incomplete owner options')
  }
  throw new Error('Owner options exceeded pagination limit')
}

export function affinityGroupQuery (query = {}) {
  // Zone was never a listAffinityGroups parameter. Drop old links explicitly.
  const clean = { ...query }
  delete clean.zoneid
  if (clean.projectid && clean.projectid !== '-1') delete clean.account
  return clean
}

export function affinityGroupListParams (params) {
  const clean = affinityGroupQuery(params)
  const keys = [...affinitySearchKeys, 'id', 'virtualmachineid', 'keyword', 'isrecursive', 'listall', 'page', 'pagesize']
  return Object.fromEntries(keys.filter(key => clean[key] !== undefined && clean[key] !== null && clean[key] !== '').map(key => [key, clean[key]]))
}

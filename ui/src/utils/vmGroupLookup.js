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

export function vmGroupOwner (owner = {}) {
  if (owner?.projectid) return { projectid: owner.projectid }
  if (owner?.account && owner?.domainid) return { account: owner.account, domainid: owner.domainid }
  return null
}

export function vmGroupOwnerKey (owner) {
  return JSON.stringify(vmGroupOwner(owner))
}

export function vmGroupLookupReady (state, owner) {
  return ['ready', 'empty'].includes(state?.status) && state.scopeKey === vmGroupOwnerKey(owner)
}

export function vmGroupLookupStatus (error) {
  const data = error?.response?.data || error || {}
  const body = data.errorresponse || Object.values(data).find(value => value?.errorcode) || data
  return [401, 403, 432, 531].includes(Number(body.errorcode || error?.response?.status)) ? 'forbidden' : 'error'
}

function ownsGroup (group, owner) {
  return owner.projectid ? group.projectid === owner.projectid
    : !group.projectid && group.account === owner.account && group.domainid === owner.domainid
}

// The request generation covers owner changes, retries and component disposal.
// Publish options only after all pages have passed ownership validation.
export function createVmGroupLookup (getAPI, publish) {
  let generation = 0
  let disposed = false
  return {
    dispose () { disposed = true; generation++ },
    async load (resourceOwner, authorized = true) {
      const request = ++generation
      const owner = vmGroupOwner(resourceOwner)
      const scopeKey = vmGroupOwnerKey(resourceOwner)
      const current = () => !disposed && request === generation
      const update = (status, opts = []) => { if (current()) publish({ status, scopeKey, opts, loading: status === 'loading' }) }
      if (!owner || !authorized) { update(owner ? 'forbidden' : 'unavailable'); return }
      update('loading')
      try {
        const groups = new Map()
        for (let page = 1; page <= 100; page++) {
          const json = await getAPI('listInstanceGroups', { ...owner, listall: true, page, pagesize: 500 }, { preserveOnFailure: true, timeout: 20000 })
          if (!current()) return
          const response = json?.listinstancegroupsresponse
          if (!response || response.errorcode || (response.instancegroup !== undefined && !Array.isArray(response.instancegroup))) throw json
          const items = response.instancegroup || []
          if (items.some(group => !group.id || typeof group.name !== 'string' || !ownsGroup(group, owner))) throw new Error('Unexpected group owner')
          if (items.some(group => groups.has(group.id)) || new Set(items.map(group => group.id)).size !== items.length) throw new Error('Repeated group page')
          items.forEach(group => groups.set(group.id, { id: group.id, label: group.name, value: group.name, groupId: group.id }))
          const count = Number(response.count)
          if (response.count !== undefined && (!Number.isSafeInteger(count) || count < 0 || count < groups.size)) throw new Error('Invalid group count')
          if (response.count !== undefined ? groups.size >= count : items.length < 500) {
            update(groups.size ? 'ready' : 'empty', [...groups.values()])
            return
          }
          if (!items.length) throw new Error('Incomplete group page')
        }
        throw new Error('Group page limit exceeded')
      } catch (error) { update(vmGroupLookupStatus(error)) }
    }
  }
}

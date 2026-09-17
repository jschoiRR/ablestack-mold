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

// A completion-driven timer: one request per owner, no hidden-tab traffic.
export function createListRefresh ({ refresh, active = () => true, interval = 10000, onError = () => {}, random = Math.random }) {
  let timer = null
  let running = false
  let stopped = false
  let failures = 0
  let pending = false
  const clear = () => { clearTimeout(timer); timer = null }
  const eligible = () => !stopped && !document.hidden && active()
  const schedule = () => {
    clear()
    if (!stopped && !document.hidden) timer = setTimeout(tick, Math.min(interval * Math.pow(2, failures) * (failures ? 1 + random() * 0.1 : 1), 120000))
  }
  const tick = async () => {
    clear()
    if (!eligible()) { schedule(); return }
    if (running) { pending = true; return }
    running = true
    try {
      await refresh()
      failures = 0
    } catch (error) {
      failures += 1
      onError(error)
    } finally {
      running = false
      if (pending && eligible()) { pending = false; tick() } else schedule()
    }
  }
  const visibility = () => { if (document.hidden) clear(); else tick() }
  document.addEventListener('visibilitychange', visibility)
  schedule()
  return {
    refresh: tick,
    schedule,
    stop () { stopped = true; clear(); document.removeEventListener('visibilitychange', visibility) }
  }
}

// Keep identity fields independent of mutable status and display values.
export function listRowKey (record) {
  if (record.uid != null) return record.uid
  if (record.metadata?.rule_uid != null) return record.metadata.rule_uid
  if (record.id != null) return record.id
  const fields = ['name', 'account', 'domainid', 'projectid', 'zoneid', 'hostid', 'resourceid', 'resourcetype', 'usageType', 'startdate', 'enddate', 'type', 'protocol', 'cidr', 'startport', 'endport']
  const identity = fields.filter(key => record[key] != null).map(key => [key, record[key]])
  return JSON.stringify(identity.length ? identity : Object.keys(record).sort().map(key => [key, record[key]]))
}

export function canRefreshList (element) {
  // Vue fragments start at an anchor; use their first rendered element.
  let root = element
  if (root && root.nodeType !== 1) {
    root = root.nextElementSibling
  }
  return !!root?.getClientRects?.().length &&
    !root.querySelector?.('input:not([type="checkbox"]):not([type="radio"]):not([type="button"]):focus, textarea:focus, [contenteditable="true"]:focus, [data-list-editing="true"]') &&
    root.getAttribute?.('data-list-editing') !== 'true' &&
    !Array.from(document.querySelectorAll('.ant-modal-wrap')).some(el => el.getClientRects().length && getComputedStyle(el).display !== 'none')
}

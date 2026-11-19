// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

import { sshKeyPairDeleteTargets, sshKeyPairDeleteParams, sshKeyPairDeleteLookup, sshKeyPairDeleteIdentity, sshKeyPairDeleteError } from '@/utils/sshKeyPairDeletion'
import { sshKeyPairUsageParams, sshKeyPairUsageResult } from '@/utils/sshKeyPairUsage'
const target = { id: '11111111-1111-4111-8111-111111111111', name: 'USER.LOGIN', fingerprint: 'fp', account: 'owner', domainid: '22222222-2222-4222-8222-222222222222' }
const result = row => ({ listsshkeypairsresponse: row ? { count: 1, sshkeypair: [row] } : {} })
test('opening captures identity and ownership independently from selection and mutable rows', () => {
  const row = { ...target }; const selection = [row]; const snapshot = sshKeyPairDeleteTargets({ invokedAsGroupAction: true }, selection); row.name = 'Other'; selection.length = 0
  expect(snapshot).toHaveLength(1); expect(snapshot[0]).toEqual(target); expect(Object.isFrozen(snapshot[0])).toBe(true); expect(Object.isFrozen(snapshot)).toBe(true)
})
test('account deletions explicitly ignore ambient project; project deletions keep their project', () => {
  expect(sshKeyPairDeleteParams(target)).toEqual({ name: target.name, account: target.account, domainid: target.domainid, ignoreproject: true })
  const projectid = '33333333-3333-4333-8333-333333333333'; expect(sshKeyPairDeleteParams({ ...target, projectid })).toEqual({ name: target.name, domainid: target.domainid, projectid })
  expect(sshKeyPairDeleteLookup(target)).toMatchObject({ id: target.id, name: target.name, pagesize: 2, ignoreproject: true })
})
test.each(['id', 'name', 'fingerprint', 'account', 'domainid'])('missing %s cannot produce a deletion request', key => { expect(sshKeyPairDeleteParams({ ...target, [key]: undefined })).toBeNull() })
test.each([{ id: '44444444-4444-4444-8444-444444444444' }, { name: 'Other' }, { fingerprint: 'changed' }, { account: 'other' }, { domainid: '55555555-5555-4555-8555-555555555555' }])('a changed target %j is rejected', patch => { expect(sshKeyPairDeleteIdentity(result({ ...target, ...patch }), target)).toBe('mismatch') })
test('empty versus malformed target lookup are distinct', () => { expect(sshKeyPairDeleteIdentity(result(), target)).toBe('missing'); expect(sshKeyPairDeleteIdentity({}, target)).toBe('error') })
test.each([undefined, 500, 502, 503, 504])('transport/server response %s remains uncertain', status => { expect(sshKeyPairDeleteError({ response: { status }, message: 'lost' }).state).toBe('unknown') })
test.each([401, 403, 431, 432])('explicit rejection %s is retryable failure', status => { expect(sshKeyPairDeleteError({ response: { status } }).state).toBe('failed') })
test('usage retains exact name/owner and does not inherit global project', () => { expect(sshKeyPairUsageParams(target)).toMatchObject({ keypair: target.name, account: target.account, domainid: target.domainid, ignoreproject: true, pagesize: 1, details: 'min' }) })
test.each([[{}, 'error'], [{ errorresponse: { errorcode: 432 } }, 'forbidden'], [{ listvirtualmachinesresponse: { count: '0' } }, 'error'], [{ listvirtualmachinesresponse: { virtualmachine: [{}] } }, 'error']])('usage failure %j never means unused', (json, expected) => { expect(sshKeyPairUsageResult(json).state).toBe(expected) })
test('only a valid empty result means zero visible VMs', () => { expect(sshKeyPairUsageResult({ listvirtualmachinesresponse: {} })).toEqual({ state: 'ready', count: 0 }); expect(sshKeyPairUsageResult({ listvirtualmachinesresponse: { count: 8, virtualmachine: [{}] } })).toEqual({ state: 'ready', count: 8 }) })

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
import { sshKeyPairLink, sshKeyPairNameLink, sshKeyPairDetailTarget, sshKeyPairDetailParams, sshKeyPairMatchesTarget, sshKeyPairDetailScope, sshKeyPairDetailResult, sshKeyPairVmQuery, sshKeyPairVmParams } from '@/utils/sshKeyPairIdentity'
const a = '11111111-1111-1111-1111-111111111111'
const b = '22222222-2222-2222-2222-222222222222'
const domain = '33333333-3333-3333-3333-333333333333'
const project = '44444444-4444-4444-4444-444444444444'
const key = { id: a, name: 'same', account: 'admin', domainid: domain }
const route = (id = a, query = {}) => ({ path: '/ssh/' + encodeURIComponent(id), params: { id }, query })
const reply = (rows = [key], count = rows.length) => ({ listsshkeypairsresponse: { sshkeypair: rows, count } })
test.each([false, true])('annotation=%s keeps the same UUID and owner for both links', hasannotations => {
  expect(sshKeyPairLink({ ...key, hasannotations })).toEqual({ path: '/ssh/' + a, query: { account: 'admin', domainid: domain } })
  expect(sshKeyPairLink({ ...key, hasannotations }, 'comments')).toEqual({ path: '/ssh/' + a, query: { account: 'admin', domainid: domain, tab: 'comments' } })
})
test('project UUID reference carries the actual project and domain', () => {
  expect(sshKeyPairLink({ ...key, account: undefined, projectid: project }).query).toEqual({ domainid: domain, projectid: project })
})
test.each(['key / + ? #', a])('VM name=%s is encoded and explicitly resolved as a name', name => {
  const link = sshKeyPairNameLink(name, key)
  expect(link.path).toBe('/ssh/' + encodeURIComponent(name))
  expect(link.query).toEqual({ account: 'admin', domainid: domain, keypairname: name })
  expect(sshKeyPairDetailTarget(route(name, link.query))).toEqual({ name, account: 'admin', domainid: domain })
})
test('legacy name route is supported while a plain UUID route stays a UUID lookup', () => {
  expect(sshKeyPairDetailTarget(route('same'))).toEqual({ name: 'same' })
  expect(sshKeyPairDetailTarget(route(a))).toEqual({ id: a })
})
test('named marker disagreement and non-scalar owner scope are rejected', () => {
  expect(sshKeyPairDetailTarget(route('same', { keypairname: 'other' })).invalid).toBe(true)
  expect(sshKeyPairDetailTarget(route(a, { account: ['one', 'two'] })).invalid).toBe(true)
})
test('explicit owner wins over a selected project; unscoped bookmarks retain project context', () => {
  expect(sshKeyPairDetailTarget(route(a, { account: 'admin', domainid: domain }), { id: project }, true)).toEqual({ id: a, account: 'admin', domainid: domain })
  expect(sshKeyPairDetailTarget(route(a), { id: project })).toEqual({ id: a, projectid: project })
  expect(sshKeyPairDetailTarget(route(a), {}, true)).toEqual({ id: a, projectid: '-1' })
})
test('only the target identity/scope reaches the detail request and pagination is independent', () => {
  expect(sshKeyPairDetailParams({ id: b, name: 'wrong', projectid: project, keypairname: 'wrong', tab: 'comments', action: 'deleteSSHKeyPair', page: 10, pagesize: 20, fingerprint: 'fp' }, { id: a, account: 'admin', domainid: domain })).toEqual({ id: a, account: 'admin', domainid: domain, page: 1, pagesize: 2, fingerprint: 'fp', listall: true, ignoreproject: true })
})
test.each([{ id: b }, { id: a, account: 'other' }, { name: 'other' }, { id: a, domainid: b }, { id: a, projectid: project }, { id: a, account: 'admin', domainid: domain, projectid: project }])('mismatching target %p rejects the response', target => {
  expect(sshKeyPairMatchesTarget(key, target)).toBe(false)
  expect(sshKeyPairDetailResult(reply(), target).status).toBe('mismatch')
})
test('UUID matching is case insensitive and project/name matching remains exact', () => {
  const upper = 'ABCDEFAB-1234-1234-1234-ABCDEFABCDEF'
  expect(sshKeyPairMatchesTarget({ ...key, id: upper.toLowerCase() }, { id: upper })).toBe(true)
  expect(sshKeyPairMatchesTarget({ ...key, account: undefined, projectid: project }, { id: a, projectid: project })).toBe(true)
  expect(sshKeyPairMatchesTarget(key, { name: 'Same' })).toBe(false)
})
test.each([{ target: { id: a }, expected: 'mismatch' }, { target: { name: 'same' }, expected: 'ambiguous' }])('a multi-result response is never selected by position', ({ target, expected }) => {
  expect(sshKeyPairDetailResult(reply([key, { ...key, id: b }]), target).status).toBe(expected)
})
test.each([{}, { listsshkeypairsresponse: { count: 1, sshkeypair: {} } }, { listsshkeypairsresponse: { sshkeypair: [key] } }, { listsshkeypairsresponse: { count: 0, sshkeypair: [key] } }])('malformed reply %p cannot become ready', body => {
  expect(sshKeyPairDetailResult(body, { id: a }).status).toBe('error')
})
test('missing, forbidden, and ready states are distinct', () => {
  expect(sshKeyPairDetailResult(reply([], 0), { id: a }).status).toBe('missing')
  expect(sshKeyPairDetailResult({ errorresponse: { errorcode: 531 } }, { id: a }).status).toBe('forbidden')
  expect(sshKeyPairDetailResult(reply(), { id: a }).status).toBe('ready')
})
test('tab changes retain scope while ownership, project, caller and session changes separate it', () => {
  const getters = { project: {}, userInfo: { id: 'user' }, listAllProjects: false }
  const scope = sshKeyPairDetailScope(route(a), getters, 'session')
  expect(sshKeyPairDetailScope(route(a, { tab: 'comments' }), getters, 'session')).toBe(scope)
  expect(sshKeyPairDetailScope(route(a, { account: 'other' }), getters, 'session')).not.toBe(scope)
  expect(sshKeyPairDetailScope(route(a), { ...getters, project: { id: project } }, 'session')).not.toBe(scope)
  expect(sshKeyPairDetailScope(route(a), { ...getters, userInfo: { id: 'other' } }, 'session')).not.toBe(scope)
  expect(sshKeyPairDetailScope(route(a), getters, 'next')).not.toBe(scope)
})

test('related VM links retain account/domain or explicit project ownership', () => {
  expect(sshKeyPairVmQuery(key)).toEqual({ keypair: key.name, account: key.account, domainid: domain })
  expect(sshKeyPairVmQuery({ ...key, projectid: project })).toEqual({ keypair: key.name, projectid: project, domainid: domain })
})
test('account-key VM lookup ignores an unrelated active or all-projects scope', () => {
  for (const projectid of [project, '-1']) {
    const params = sshKeyPairVmParams({ projectid, page: 2 }, { keypair: key.name, account: key.account, domainid: domain })
    expect(params).toEqual({ page: 2, ignoreproject: true })
  }
})
test('explicit SSH owner project overrides global all-projects injection', () => {
  expect(sshKeyPairVmParams({ projectid: '-1', page: 1 }, { keypair: key.name, projectid: project })).toEqual({ projectid: project, page: 1 })
})
test('unscoped SSH searches and other VM queries retain the requested project scope', () => {
  const params = { projectid: project }
  expect(sshKeyPairVmParams(params, { keypair: key.name })).toBe(params)
  expect(sshKeyPairVmParams(params, { account: key.account, domainid: domain })).toBe(params)
})

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
import InfoCard from '@/components/view/InfoCard.vue'
const domain = '11111111-1111-1111-1111-111111111111'
const project = '22222222-2222-2222-2222-222222222222'
const key = { name: 'same', account: 'admin', domainid: domain }
const query = (resource, item = { name: 'vm', param: 'keypair' }) => InfoCard.methods.getRouterQuery.call({ resource }, item)
test('account key related VM URL preserves account and domain', () => {
  expect(query(key)).toEqual({ keypair: 'same', account: 'admin', domainid: domain })
})
test('project key related VM URL carries its real owner project', () => {
  expect(query({ ...key, projectid: project })).toEqual({ keypair: 'same', projectid: project, domainid: domain })
})
test('literal key names are passed as query values without folding or splitting', () => {
  expect(query({ ...key, name: 'Key/Name ?+#,two' }).keypair).toBe('Key/Name ?+#,two')
})
test('other resource link queries retain their original mapping', () => {
  expect(query({ name: 'owner', domainid: domain }, { param: 'account' })).toEqual({ account: 'owner', domainid: domain })
  expect(query({ id: project }, { param: 'networkid' })).toEqual({ networkid: project })
})

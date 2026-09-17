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

import axios from 'axios'
import store from '@/store'
import '@/utils/request'
jest.mock('axios', () => {
  const service = { interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } } }
  return { create: () => service, service, isCancel: () => false, CancelToken: { source: () => ({ token: {}, cancel: jest.fn() }) } }
})
jest.mock('@/vue-app', () => ({ vueProps: { $localStorage: { get: () => null } } }))
jest.mock('@/router', () => ({ currentRoute: { value: { fullPath: '/vm/test' } }, push: jest.fn() }))
jest.mock('@/utils/axios', () => ({ VueAxios: {} }))
jest.mock('@/locales', () => ({ i18n: { global: { t: x => x } } }))
jest.mock('@/store', () => ({ state: { user: { token: 'original' } }, getters: { countNotify: 0 }, commit: jest.fn(), dispatch: jest.fn(() => Promise.resolve()) }))
jest.mock('ant-design-vue/es/notification', () => ({ error: jest.fn() }))
const prepare = config => axios.service.interceptors.request.use.mock.calls[0][0](config)
const reject = error => axios.service.interceptors.response.use.mock.calls[0][1](error)
describe('background job transport', () => {
  beforeEach(() => { store.state.user.token = 'original'; store.dispatch.mockClear() })
  it('leaves transient network and HTTP errors to the tracker without logging out', async () => {
    for (const response of [undefined, { status: 503 }, { status: 403 }]) {
      const error = { isAxiosError: true, config: prepare({ backgroundJob: true }), response }
      await expect(reject(error)).rejects.toBe(error)
    }
    expect(store.dispatch).not.toHaveBeenCalled()
  })
  it('does not let a stale 401 log out a new session', async () => {
    const config = prepare({ backgroundJob: true }); store.state.user.token = 'new'
    const error = { config, response: { status: 401 } }
    await expect(reject(error)).rejects.toBe(error); expect(store.dispatch).not.toHaveBeenCalled()
  })
  it('preserves logout for an actual current-session 401', async () => {
    const log = jest.spyOn(console, 'log').mockImplementation(() => {})
    const error = { config: prepare({ backgroundJob: true }), response: { status: 401 } }
    await expect(reject(error)).rejects.toBe(error)
    expect(store.dispatch).toHaveBeenCalledWith('Logout'); log.mockRestore()
  })
})

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

import ListView from '@/components/view/ListView.vue'
import Schedules from '@/views/compute/ResourceSchedules.vue'
import Profile from '@/views/compute/AutoScaleVmProfile.vue'
import Up from '@/views/compute/AutoScaleUpPolicyTab.vue'
import Down from '@/views/compute/AutoScaleDownPolicyTab.vue'
import { getAPI } from '@/api'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
jest.mock('@/store', () => ({ getters: { apis: {}, userInfo: {}, project: null } }))
beforeEach(() => jest.clearAllMocks())
test.each([Profile, Up, Down])('limited API metadata never crashes tab initialization', component => {
  expect(() => component.beforeCreate.call({ $store: { getters: { apis: {} } } })).not.toThrow()
})
test('last deploy parameter removal uses explicit clear and preserves omitted counter params', async () => {
  const ctx = {
    loading: false,
    saving: false,
    profileid: 'p',
    allParams: [{ name: 'rootdisksize', value: '120' }],
    counterParams: ['snmpport'],
    deployParams: ['rootdisksize'],
    saveProfile: jest.fn().mockResolvedValue(true)
  }
  await Profile.methods.updateAutoScaleVmProfileWithParam.call(ctx, null, null, 'rootdisksize')
  expect(ctx.saveProfile).toHaveBeenCalledWith({ id: 'p', clearotherdeployparams: true })
})
test('profile template lookup uses project, zone and ready executable scope', async () => {
  getAPI.mockResolvedValue({ listtemplatesresponse: { template: [{ id: 'iso', format: 'ISO' }, { id: 'valid', format: 'QCOW2' }] } })
  const ctx = { resource: { projectid: 'project' }, zoneid: 'zone', $notifyError: jest.fn() }
  await Profile.methods.fetchTemplateData.call(ctx)
  expect(getAPI).toHaveBeenCalledWith('listTemplates', expect.objectContaining({ projectid: 'project', zoneid: 'zone', isready: true, templatefilter: 'executable' }))
  expect(ctx.templatesList.map(row => row.id)).toEqual(['valid'])
})
test.each([Up, Down])('last condition cannot be detached or silently sent as an empty list', component => {
  const ctx = { policy: { conditions: [{ id: 'only' }] }, $message: { error: jest.fn() }, $t: key => key, updateAutoScalePolicy: jest.fn() }
  component.methods.deleteConditionFromAutoScalePolicy.call(ctx, 'only')
  expect(ctx.updateAutoScalePolicy).not.toHaveBeenCalled(); expect(ctx.$message.error).toHaveBeenCalled()
})

test('custom data disk offering and size are sent in the same profile update', async () => {
  const ctx = {
    loading: false,
    saving: false,
    profileid: 'p',
    allParams: [],
    counterParams: [],
    deployParams: ['diskofferingid', 'disksize'],
    newParam: { disksize: '10' },
    saveProfile: jest.fn().mockResolvedValue(true)
  }
  await Profile.methods.updateAutoScaleVmProfileWithParam.call(ctx, 'diskofferingid', 'disk', null)
  expect(ctx.saveProfile).toHaveBeenCalledWith({
    id: 'p',
    'otherdeployparams[0].name': 'diskofferingid',
    'otherdeployparams[0].value': 'disk',
    'otherdeployparams[1].name': 'disksize',
    'otherdeployparams[1].value': '10'
  })
})

test.each(['14 9 11 10 *', '*/5 9-17 * * 1-5', '0 0 1 */2 *'])('opening an existing schedule preserves all cron fields: %s', cron => {
  const ctx = { resetForm: jest.fn(), form: { useCronFormat: false }, showAddModal: jest.fn() }
  Schedules.methods.updateSchedule.call(ctx, { schedule: cron, timezone: 'Asia/Seoul', details: { minmembers: '1', maxmembers: '3' }, startdate: '2026-10-11T09:00:00Z' })
  expect(ctx.form.schedule).toBe(cron)
  expect(ctx.form.useCronFormat).toBe(true)
  expect(ctx.form.startDate.format('YYYY-MM-DD HH:mm:ss')).toBe('2026-10-11 18:00:00')
})
test.each([Schedules, ListView])('Mold Korean locale uses Korean in both schedule editor and list', component => {
  const value = component.methods.generateHumanReadableSchedule.call({ $i18n: { locale: 'ko_KR' } }, '14 9 11 10 *')
  expect(value).toMatch(/[가-힣]/)
  expect(value).not.toMatch(/At |October/)
})

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

import dayjs from 'dayjs'
import { autoScaleScheduleError, schedulePickerDate, scheduleWallInstants } from '@/utils/autoScaleSchedule'
const form = extra => ({ minMembers: 1, maxMembers: 3, timezone: 'UTC', ...extra })
test('schedule editor preserves selected-zone wall time rather than browser time', () => {
  expect(schedulePickerDate('2027-01-01T00:00:00+0000', 'Asia/Seoul').format('YYYY-MM-DD HH:mm:ss')).toBe('2027-01-01 09:00:00')
  expect(schedulePickerDate('2027-01-01T00:00:00+0000', 'UTC').format('YYYY-MM-DD HH:mm:ss')).toBe('2027-01-01 00:00:00')
})
test.each([{ minMembers: 0 }, { minMembers: 4 }, { maxMembers: 1.5 }, { maxMembers: 2147483648 }])('invalid range rejected before submit (%j)', value => {
  expect(autoScaleScheduleError(form(value))).toBe('message.autoscale.schedule.range.invalid')
})
test('timezone typo is not silently converted to GMT', () => {
  expect(autoScaleScheduleError(form({ timezone: 'Asia/Typo' }))).toBe('message.autoscale.schedule.timezone.invalid')
})
test('DST gap and overlap are explicit', () => {
  expect(scheduleWallInstants('2027-03-14 02:30:00', 'America/New_York')).toHaveLength(0)
  expect(scheduleWallInstants('2027-11-07 01:30:00', 'America/New_York')).toHaveLength(2)
  expect(autoScaleScheduleError(form({ timezone: 'America/New_York', startDate: dayjs('2027-11-07 01:30:00') }))).toBe('message.autoscale.schedule.dst.invalid')
})
test('window end must be later in the selected timezone', () => {
  const startDate = dayjs('2027-01-01 01:00:00')
  expect(autoScaleScheduleError(form({ startDate, endDate: startDate }))).toBe('message.autoscale.schedule.window.invalid')
  expect(autoScaleScheduleError(form({ startDate, endDate: dayjs('2027-01-01 01:01:00') }))).toBeNull()
})

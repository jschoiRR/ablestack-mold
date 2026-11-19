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

import moment from 'moment-timezone'
import dayjs from 'dayjs'
import { autoScaleInteger } from '@/utils/autoscaleValidation'

const pattern = 'YYYY-MM-DD HH:mm:ss'
export function schedulePickerDate (value, timezone) {
  return value ? dayjs(moment(value).tz(timezone).format(pattern)) : null
}

export function scheduleWallInstants (value, timezone) {
  const zone = moment.tz.zone(timezone)
  if (!zone) return []
  const raw = moment.utc(value, pattern, true)
  if (!raw.isValid()) return []
  return [...new Set(zone.offsets)].map(offset => raw.valueOf() + offset * 60000)
    .filter(instant => moment.tz(instant, timezone).format(pattern) === value)
}

export function autoScaleScheduleError (form) {
  const min = autoScaleInteger(form.minMembers, 1, 2147483647)
  const max = autoScaleInteger(form.maxMembers, 1, 2147483647)
  if (min === null || max === null || min > max) return 'message.autoscale.schedule.range.invalid'
  if (!moment.tz.zone(form.timezone)) return 'message.autoscale.schedule.timezone.invalid'
  const starts = form.startDate ? scheduleWallInstants(form.startDate.format(pattern), form.timezone) : []
  const ends = form.endDate ? scheduleWallInstants(form.endDate.format(pattern), form.timezone) : []
  if ((form.startDate && starts.length !== 1) || (form.endDate && ends.length !== 1)) return 'message.autoscale.schedule.dst.invalid'
  if (form.startDate && form.endDate && ends[0] <= starts[0]) return 'message.autoscale.schedule.window.invalid'
  return null
}

// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with this
// work for additional information regarding copyright ownership. The ASF
// licenses this file to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the License.
// You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// License for the specific language governing permissions and limitations
// under the License.
import { Buffer } from 'buffer'
import { NO_USER_DATA } from '@/utils/userDataSelection'

export const userDataKeys = item => typeof item?.params === 'string' ? item.params.split(',').map(key => key.trim()).filter(Boolean) : []

export function userDataResetSupport (vm, networks) {
  if (vm.state !== 'Stopped' || vm.hypervisor === 'External' || vm.vmtype === 'sharedfsvm') return 'message.userdata.reset.unsupported.state'
  const providers = (vm.nic || []).flatMap(nic => {
    const network = networks.find(n => n.id === nic.networkid)
    return (network?.service || []).filter(s => s.name === 'UserData').flatMap(s => s.provider || [])
      .filter(p => p.name !== 'ConfigDrive' || nic.isdefault).map(p => p.name)
  })
  if (!providers.length) return 'message.userdata.reset.unsupported.network'
  return providers.every(p => p === 'ConfigDrive') ? 'message.userdata.reset.delivery.configdrive' : 'message.userdata.reset.delivery.provider'
}

// The existing API exposes a Java Map string, not JSON. Split at known key
// boundaries only: commas, equals signs and braces inside a value are data.
export function resetUserDataValues (details, keys) {
  if (!details) return {}
  if (typeof details === 'object' && !Array.isArray(details)) return Object.fromEntries(keys.filter(k => Object.hasOwnProperty.call(details, k)).map(k => [k, String(details[k])]))
  const text = String(details)
  if (!text.startsWith('{') || !text.endsWith('}')) throw new Error('Invalid user data details')
  const body = text.slice(1, -1)
  const matches = [...body.matchAll(/(?:^|, )([A-Za-z_][A-Za-z0-9_]*)=/g)].filter(m => keys.includes(m[1]))
  const result = Object.create(null)
  for (let i = 0; i < matches.length; i++) {
    const match = matches[i]
    if (Object.hasOwnProperty.call(result, match[1])) throw new Error('Ambiguous user data details')
    result[match[1]] = body.slice(match.index + match[0].length, matches[i + 1]?.index ?? body.length)
  }
  return result
}

export function decodeResetUserData (encoded) {
  if (!encoded) return ''
  const data = Buffer.from(decodeURIComponent(encoded), 'base64')
  const text = data.toString('utf8')
  if (!Buffer.from(text, 'utf8').equals(data)) throw new Error('User data is not UTF-8 text')
  return text
}

export function resetTemplateActive (vm) {
  if (!vm.template.userdataid) return false
  if (vm.userdataDefaultOverridePolicy !== 'ALLOWOVERRIDE' || !vm.doUserdataOverride) return true
  return vm.userdataTabKey === 'userdatatext' ? !vm.form.userdata : !vm.form.userdataid || vm.form.userdataid === NO_USER_DATA
}

// Never serialize hidden mode state, None, or template variables that are no
// longer effective. Returning null is an intentional no-op, not an empty reset.
export function resetUserDataPayload (vm, encode) {
  if (!vm.editCurrent) return null
  const params = { id: vm.resource.resetUserDataResourceId || vm.resource.id }
  const templateActive = resetTemplateActive(vm)
  const customActive = !vm.template.userdataid || (vm.userdataDefaultOverridePolicy !== 'DENYOVERRIDE' && (vm.doUserdataOverride || vm.doUserdataAppend))
  const variables = Object.create(null)
  if (templateActive) for (const key of userDataKeys({ params: vm.templateUserDataParams.map(p => p.key).join(',') })) variables[key] = vm.templateUserDataValues[key] ?? ''
  if (customActive && vm.userdataTabKey === 'userdatatext') {
    params.userdata = vm.form.userdata ? encode(vm.form.userdata) : ''
  } else if (customActive && vm.form.userdataid && vm.form.userdataid !== NO_USER_DATA) {
    params.userdataid = vm.form.userdataid
    for (const p of vm.userDataParams) variables[p.key] = vm.userDataValues[p.key] ?? ''
  }
  // An empty reset means detach custom data / return to the template default.
  if (!params.userdataid && params.userdata === undefined) params.userdata = ''
  Object.entries(variables).forEach(([key, value], index) => { params[`userdatadetails[${index}].${key}`] = value })
  return params
}

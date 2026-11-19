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

const fail = key => { throw new Error('message.userdata.validation.' + key) }

export function userDataName (name) {
  const value = (name || '').trim()
  if (!value || value.length > 255) fail('name')
  return value
}

export function userDataParams (params) {
  const keys = (params || []).map(key => key.trim())
  if (keys.some(key => !/^[A-Za-z_][A-Za-z0-9_.-]*$/.test(key)) || new Set(keys).size !== keys.length) fail('params')
  return keys.join(',')
}

export function canonicalUserData (value, isBase64, maxLength = 1048576) {
  let binary
  if (isBase64) {
    let encoded = value || ''
    try {
      if (encoded.includes('%')) encoded = decodeURIComponent(encoded)
      encoded = encoded.replace(/[\t\n\r ]/g, '').replace(/-/g, '+').replace(/_/g, '/')
      if (!/^[A-Za-z0-9+/]+={0,2}$/.test(encoded) || encoded.replace(/=+$/, '').length % 4 === 1 || (encoded.includes('=') && encoded.length % 4 !== 0)) fail('base64')
      binary = atob(encoded.padEnd(Math.ceil(encoded.length / 4) * 4, '='))
    } catch (error) { fail('base64') }
  } else {
    // Plain text is always UTF-8 encoded, even when it happens to look like Base64.
    try { binary = unescape(encodeURIComponent(value || '')) } catch (error) { fail('text') }
  }
  let text
  try { text = decodeURIComponent(escape(binary)) } catch (error) { /* Binary Base64 remains supported. */ }
  if (!binary.length || (text !== undefined && !text.trim())) fail('empty')
  const encoded = btoa(binary)
  if (encoded.length > maxLength) fail('size')
  return encoded
}

// Detail routes are unnamed; their menu identity is inherited through meta.
export function isUserDataRoute (route) { return (route?.meta?.name || route?.name) === 'userdata' }

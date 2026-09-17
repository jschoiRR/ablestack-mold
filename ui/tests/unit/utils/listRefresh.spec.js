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

import { createListRefresh, listRowKey, canRefreshList } from '@/utils/listRefresh'

const flush = async () => { await Promise.resolve(); await Promise.resolve(); await Promise.resolve() }
describe('list refresh scheduling', () => {
  let controller
  beforeEach(() => {
    jest.useFakeTimers()
    Object.defineProperty(document, 'hidden', { configurable: true, value: false })
  })
  afterEach(() => { if (controller) controller.stop(); jest.useRealTimers() })
  it('waits for completion before the next request and coalesces refreshes', async () => {
    let complete
    const refresh = jest.fn(() => new Promise(resolve => { complete = resolve }))
    controller = createListRefresh({ refresh })
    jest.advanceTimersByTime(10000)
    expect(refresh).toHaveBeenCalledTimes(1)
    jest.advanceTimersByTime(60000)
    controller.refresh(); controller.refresh()
    expect(refresh).toHaveBeenCalledTimes(1)
    complete(); await flush()
    expect(refresh).toHaveBeenCalledTimes(2)
    complete(); await flush()
    jest.advanceTimersByTime(9999)
    expect(refresh).toHaveBeenCalledTimes(2)
  })
  it('pauses hidden documents and resumes immediately', async () => {
    const refresh = jest.fn(() => Promise.resolve())
    controller = createListRefresh({ refresh })
    Object.defineProperty(document, 'hidden', { value: true })
    document.dispatchEvent(new Event('visibilitychange'))
    jest.advanceTimersByTime(30000)
    expect(refresh).not.toHaveBeenCalled()
    Object.defineProperty(document, 'hidden', { value: false })
    document.dispatchEvent(new Event('visibilitychange')); await flush()
    expect(refresh).toHaveBeenCalledTimes(1)
    controller.stop(); jest.advanceTimersByTime(30000)
    expect(refresh).toHaveBeenCalledTimes(1)
  })
  it('discovers a newly active component and backs off on failure', async () => {
    let active = false
    const refresh = jest.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue(null)
    controller = createListRefresh({ refresh, active: () => active, random: () => 0 })
    jest.advanceTimersByTime(10000)
    expect(refresh).not.toHaveBeenCalled()
    active = true
    jest.advanceTimersByTime(10000); await flush()
    expect(refresh).toHaveBeenCalledTimes(1)
    jest.advanceTimersByTime(19999)
    expect(refresh).toHaveBeenCalledTimes(1)
    jest.advanceTimersByTime(1); await flush()
    expect(refresh).toHaveBeenCalledTimes(2)
  })
  it('keeps keys stable across status changes and distinguishes scoped names', () => {
    expect(listRowKey({ id: 'a', state: 'Running' })).toBe(listRowKey({ id: 'a', state: 'Stopped' }))
    expect(listRowKey({ name: 'same', account: 'a' })).not.toBe(listRowKey({ name: 'same', account: 'b' }))
    expect(listRowKey({ name: 'x', state: 'Running' })).toBe(listRowKey({ name: 'x', state: 'Stopped' }))
  })
})

describe('editable focus policy', () => {
  it('continues polling after row selection but pauses for text editing and visible modals', () => {
    const root = document.createElement('div')
    root.innerHTML = '<input type="checkbox"><input type="radio"><input type="text">'
    root.getClientRects = () => [{ width: 100, height: 100 }]
    document.body.appendChild(root)
    const [checkbox, radio, text] = root.querySelectorAll('input')
    checkbox.focus()
    expect(canRefreshList(root)).toBe(true)
    radio.focus()
    expect(canRefreshList(root)).toBe(true)
    text.focus()
    expect(canRefreshList(root)).toBe(false)
    text.blur()
    root.setAttribute('data-list-editing', 'true')
    expect(canRefreshList(root)).toBe(false)
    root.removeAttribute('data-list-editing')
    const modal = document.createElement('div')
    modal.className = 'ant-modal-wrap'
    modal.getClientRects = () => [{ width: 100, height: 100 }]
    document.body.appendChild(modal)
    expect(canRefreshList(root)).toBe(false)
    modal.remove()
    root.remove()
  })
})

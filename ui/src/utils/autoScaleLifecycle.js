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

export function autoScaleLifecycleNotice (resource) {
  const retry = (resource.scalingphase || '').endsWith('_RETRY')
  const cleanup = resource.scalingaction === 'CLEANUP'
  return {
    type: retry || resource.state === 'DISABLED' ? 'warning' : 'info',
    message: retry ? 'message.autoscale.lifecycle.retry' : cleanup ? 'message.autoscale.lifecycle.cleanup' : resource.state === 'DISABLED' ? 'message.autoscale.operation.stopping' : 'message.autoscale.operation.running',
    description: cleanup ? 'message.autoscale.lifecycle.cleanup.retry' : 'message.autoscale.lifecycle.members.retained'
  }
}

// Match only known lifecycle API failures; preserve unrelated provider diagnostics.
export function autoScaleLifecycleErrorKey (description) {
  if (typeof description !== 'string') return null
  if (description.startsWith('Cannot delete autoscale vm group') && description.includes('because there are') && description.includes('VMs')) return 'message.autoscale.lifecycle.delete.members'
  if (description === 'Failed to delete autoscale vm group') return 'message.autoscale.lifecycle.delete.failed'
  if (description.startsWith('Cannot perform actions on VM in an AutoScale VM group')) return 'message.autoscale.lifecycle.vm.action.blocked'
  if (description.startsWith('AutoScale VM group has an operation in progress.')) return 'message.autoscale.operation.wait'
  return null
}

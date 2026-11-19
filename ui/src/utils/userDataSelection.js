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

// UUIDs never collide with this language-independent, API-free empty selection.
export const NO_USER_DATA = '0'

export function userDataOwnerParams (owner = {}) {
  return owner.projectid && owner.projectid !== '-1'
    ? { projectid: owner.projectid }
    : { account: owner.account, domainid: owner.domainid }
}

export function applyUserDataSelection (vm, id, item) {
  vm.form.userdataid = id && id !== NO_USER_DATA && item?.id === id ? id : undefined
  const keys = vm.form.userdataid && typeof item.params === 'string' ? item.params.split(',').map(key => key.trim()).filter(Boolean) : []
  vm.userDataParams = keys.map((key, index) => ({ id: index, key }))
  vm.userDataValues = Object.create(null)
}

// Consumers share ownership and the same fail-closed submit contract.
export const userDataSelectionConsumer = {
  data () { return { userDataSelectionReady: false } },
  computed: {
    userDataOwner () {
      const owner = this.owner || this.resource || { ...this.$store.getters.userInfo, projectid: this.$store.getters.project?.id }
      return userDataOwnerParams(owner)
    },
    userDataScopeKey () { return JSON.stringify([this.userDataOwner, this.zoneId || this.resource?.zoneid]) },
    userDataSelectionBlocked () {
      return Boolean(this.userdataTabKey === 'userdataregistered' &&
        (!this.userdataDefaultOverridePolicy || this.doUserdataOverride || this.doUserdataAppend) &&
        !this.userDataSelectionReady)
    }
  },
  watch: {
    userDataScopeKey () {
      this.userDataSelectionReady = false
      this.updateUserData(NO_USER_DATA)
    }
  }
}

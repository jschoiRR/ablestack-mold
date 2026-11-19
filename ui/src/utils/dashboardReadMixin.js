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

import { getAPI } from '@/api'
import { sourceToken } from '@/utils/request'

// Dashboard reads belong to a mounted route/session/project and to the latest
// request with the same arguments. Cancellation remains a rejection at the API
// boundary; this view adapter owns it without inventing a successful response.
export default {
  data: () => ({ dashboardDisposed: false, dashboardReads: {}, dashboardPending: 0, dashboardReadError: false }),
  computed: {
    dashboardScope () {
      return JSON.stringify([this.$route.fullPath, this.$store.state?.user?.token, this.$store.getters.userInfo?.id, this.$store.getters.project?.id])
    }
  },
  watch: {
    dashboardScope () {
      this.dashboardReads = {}; this.dashboardPending = 0; this.loading = false; this.capacityLoading = false; this.dashboardReadError = false
      // Values from a different owner must not be presented while its reads run.
      if (this.data) this.data = Object.fromEntries(Object.keys(this.data).map(key => [key, null]))
      for (const key of ['events', 'alerts', 'zones']) if (Array.isArray(this[key])) this[key] = []
      if (this.statsMap) this.statsMap = {}
      if (this.account) this.account = {}
    }
  },
  beforeUnmount () { this.dashboardDisposed = true; this.dashboardReads = {} },
  methods: {
    async readDashboard (command, args, apply) {
      if (this.dashboardDisposed || this.$route.name !== 'dashboard') return
      const scope = this.dashboardScope
      const key = JSON.stringify([command, args])
      const request = {}; this.dashboardReads[key] = request
      // Vue can proxy data objects: capture the stored identity.
      const token = this.dashboardReads[key]
      const current = () => !this.dashboardDisposed && this.$route.name === 'dashboard' && this.dashboardScope === scope && this.dashboardReads[key] === token
      this.dashboardPending++; this.loading = true
      try {
        const json = await getAPI(command, args, { preserveOnFailure: true })
        if (current()) { apply(json); delete token.failed }
      } catch (error) {
        if (!current() || sourceToken.isCancel(error)) return
        token.failed = true
        this.dashboardReadError = true
      } finally {
        if (!this.dashboardDisposed && this.dashboardScope === scope) {
          this.dashboardPending = Math.max(0, this.dashboardPending - 1)
          this.loading = this.dashboardPending > 0
          if (current()) this.dashboardReadError = Object.values(this.dashboardReads).some(read => read.failed)
        }
      }
    }
  }
}

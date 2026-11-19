<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with this work
for additional information regarding copyright ownership. The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy at
http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or
agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License
for the specific language governing permissions and limitations. -->
<template>
  <MoldDialog class="userdata-delete-dialog" size="lg" :title="$t('label.remove.user.data')" :closable="!busy" :keyboard="!busy" @cancel="close" @keydown.ctrl.enter.stop.prevent>
    <div class="userdata-delete-content" :aria-busy="busy">
      <a-alert type="warning" show-icon :message="$t('message.userdata.delete.scope')" />
      <p><strong>{{ $t('message.userdata.delete.count', { count: rows.length }) }}</strong></p>
      <p v-if="started" role="status" aria-live="polite">{{ $t('message.userdata.delete.results', totals) }}</p>
      <a-alert v-if="!allowed || !active() || !rows.length" type="error" show-icon :message="$t('message.userdata.delete.unavailable')" />
      <a-table :columns="columns" :data-source="rows" :row-key="row => row.target.id" :pagination="{ pageSize: 10, showSizeChanger: false }" :scroll="{ x: 760 }" size="small">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'"><strong>{{ record.target.name }}</strong><div>{{ record.target.id }}</div></template>
          <template v-else-if="column.key === 'owner'">{{ record.target.account }}<div>{{ record.target.domain || record.target.domainid }}</div><div v-if="record.target.projectid">{{ $t('label.project') }}: {{ record.target.project || record.target.projectid }}</div></template>
          <template v-else-if="column.key === 'status'"><a-badge :status="badgeStatus(record.status)" :text="$t('label.userdata.delete.state.' + record.status)" :data-delete-state="record.status" /></template>
          <template v-else-if="column.key === 'reason'">
            <div>{{ record.reason }}</div>
            <div v-if="record.references === null">{{ $t('message.userdata.usage.checking') }}</div>
            <div v-else-if="record.references === false">{{ $t('message.userdata.usage.incomplete') }}</div>
            <div v-for="(count, kind) in record.references || {}" :key="kind">
              <router-link :to="referenceLink(record.target, kind)" :aria-disabled="busy" :tabindex="busy ? -1 : 0" @click.capture="followReference($event)">{{ $t('label.userdata.usage.' + kind) }}: {{ count }}</router-link>
            </div>
          </template>
        </template>
      </a-table>
      <a-button :loading="referencePending" :disabled="busy || referencePending" @click="loadReferences">{{ $t('label.userdata.usage.refresh') }}</a-button>
    </div>
    <template #footer>
      <a-button ref="cancel" :disabled="busy" @click="close">{{ $t(started ? 'label.close' : 'label.cancel') }}</a-button>
      <a-button v-if="checkableRows.length" :disabled="busy || !allowed || !active()" @click="checkUnknown">{{ $t('label.userdata.delete.check') }}</a-button>
      <a-button v-if="eligibleRows.length || busy" type="primary" danger :loading="busy" :disabled="busy || referencePending || !allowed || !active()" @click="execute">{{ $t(started ? 'label.userdata.delete.retry' : 'label.delete') }}</a-button>
    </template>
  </MoldDialog>
</template>
<script>
import { userDataUsageKinds, userDataUsageParams, userDataUsageRows, userDataUsageSupported } from '@/utils/userDataUsage'
import MoldDialog from '@/components/view/MoldDialog'
import { getAPI, postAPI } from '@/api'
import { userDataDeleteParams, userDataDeleteLookup, userDataDeleteIdentity, userDataDeleteError } from '@/utils/userDataDeletion'

export default {
  name: 'DeleteUserData',
  components: { MoldDialog },
  props: { currentAction: { type: Object, required: true } },
  emits: ['close-action', 'refresh-data'],
  data () { return { rows: (this.currentAction.userDataTargets || []).map(target => ({ target, status: userDataDeleteParams(target) ? 'ready' : 'blocked', reason: '', references: null })), referencePending: false, busy: false, started: false, disposed: false, session: '' } },
  computed: {
    allowed () { return ['listUserData', 'deleteUserData'].every(api => api in this.$store.getters.apis) },
    eligibleRows () { return this.rows.filter(row => ['ready', 'failed'].includes(row.status)) },
    checkableRows () { return this.rows.filter(row => ['unknown', 'blocked'].includes(row.status) && userDataDeleteParams(row.target)) },
    totals () { return { success: this.rows.filter(row => ['success', 'absent'].includes(row.status)).length, failed: this.rows.filter(row => ['failed', 'blocked'].includes(row.status)).length, unknown: this.rows.filter(row => row.status === 'unknown').length, pending: this.rows.filter(row => ['ready', 'checking', 'deleting'].includes(row.status)).length } },
    columns () { return [['name', 'label.name', 220], ['owner', 'label.account', 180], ['status', 'label.operation.status', 130], ['reason', 'label.userdata.delete.reason', 230]].map(([key, title, width]) => ({ key, title: this.$t(title), width })) }
  },
  mounted () { this.session = this.sessionKey(); this.loadReferences(); this.$nextTick(() => this.$refs.cancel?.$el?.focus()); window.addEventListener('beforeunload', this.beforeUnload) },
  beforeUnmount () { this.disposed = true; window.removeEventListener('beforeunload', this.beforeUnload) },
  methods: {
    sessionKey () { return JSON.stringify([this.$store.getters.userInfo?.id, this.$store.state?.user?.token, this.$store.getters.project?.id, this.$store.getters.listAllProjects, this.$route.fullPath]) },
    active () { return !this.disposed && this.session === this.sessionKey() },
    badgeStatus (state) { return { success: 'success', absent: 'success', failed: 'error', blocked: 'error', unknown: 'warning', checking: 'processing', deleting: 'processing' }[state] || 'default' },
    referenceLink (target, kind) {
      const { id, ...scope } = userDataDeleteParams(target) || {}
      return { path: '/userdata/' + target.id, query: { ...scope, tab: 'userdata.usage.' + kind } }
    },
    followReference (event) { if (this.busy) event.preventDefault(); else this.close() },
    async loadReferences () {
      if (this.busy || this.referencePending || !this.active()) return
      this.referencePending = true
      try {
        await Promise.all(this.rows.filter(row => !['success', 'absent'].includes(row.status)).map(async row => {
          try {
            const references = {}
            for (const kind of Object.keys(userDataUsageKinds)) {
              if (!this.active() || !userDataUsageSupported(this.$store.getters.apis, kind)) throw new Error('Reference lookup unavailable')
              const json = await getAPI(userDataUsageKinds[kind].api, userDataUsageParams(row.target, kind, this.$store.getters.project, this.$store.getters.userInfo.roletype, { pagesize: 1 }), { preserveOnFailure: true, timeout: 15000 })
              references[kind] = userDataUsageRows(json, row.target, kind).count
            }
            if (this.active()) row.references = references
          } catch (_) { if (this.active()) row.references = false }
        }))
      } finally { this.referencePending = false }
    },
    async identity (row) {
      if (!this.active() || !this.allowed) return 'forbidden'
      const params = userDataDeleteLookup(row.target)
      if (!params) return 'mismatch'
      try {
        let json; let byName = false
        try { json = await getAPI('listUserData', params, { preserveOnFailure: true, timeout: 15000 }) } catch (error) {
          // A removed UUID may be rejected by command parameter conversion. Confirm absence
          // through a complete owner-scoped name lookup instead of treating 431 as absence.
          if (userDataDeleteError(error).code !== 431 || !this.active() || !this.allowed) throw error
          byName = true
          json = await getAPI('listUserData', userDataDeleteLookup(row.target, true), { preserveOnFailure: true, timeout: 15000 })
        }
        return this.active() && this.allowed ? userDataDeleteIdentity(json, row.target, byName) : 'forbidden'
      } catch (error) { return [401, 403, 432, 531].includes(userDataDeleteError(error).code) ? 'forbidden' : 'error' }
    },
    async reconcile (row) {
      const status = await this.identity(row)
      if (!this.active()) return
      if (status === 'missing') { row.status = 'success'; row.reason = this.$t('message.userdata.delete.confirmed.absent') } else if (status === 'ready') { row.status = 'failed'; row.reason = [row.reason, this.$t('message.userdata.delete.confirmed.present')].filter(Boolean).join(' ') } else if (status === 'mismatch') { row.status = 'blocked'; row.reason = this.$t('message.userdata.delete.changed') } else { row.status = 'unknown'; row.reason = [row.reason, this.$t('message.userdata.delete.unknown')].filter(Boolean).join(' ') }
    },
    async execute () {
      if (this.busy || this.referencePending || !this.active() || !this.allowed || !this.eligibleRows.length) return
      this.busy = true; this.started = true
      const pending = [...this.eligibleRows]
      try {
        for (const row of pending) {
          if (!this.active() || !this.allowed) break
          row.status = 'checking'; row.reason = ''
          const status = await this.identity(row)
          if (!this.active() || !this.allowed) break
          if (status === 'missing') { row.status = 'absent'; row.reason = this.$t('message.userdata.delete.already.absent'); continue }
          if (status !== 'ready') { row.status = 'blocked'; row.reason = this.$t(status === 'mismatch' ? 'message.userdata.delete.changed' : 'message.userdata.delete.preflight.failed'); continue }
          row.status = 'deleting'
          try {
            const json = await postAPI('deleteUserData', userDataDeleteParams(row.target), { preserveOnFailure: true, timeout: 30000 })
            if (!this.active()) break
            if (json?.deleteuserdataresponse?.success === true) row.status = 'success'
            else { row.status = 'unknown'; row.reason = this.$t('message.userdata.delete.unknown'); await this.reconcile(row) }
          } catch (error) {
            if (!this.active()) break
            row.reason = userDataDeleteError(error).reason
            // Even a server 530 can mean provider/transport ambiguity; read before retry.
            row.status = 'unknown'; await this.reconcile(row)
          }
        }
      } finally {
        if (!this.active() || !this.allowed) {
          for (const row of pending) {
            if (row.status === 'deleting') row.status = 'unknown'
            else if (['ready', 'checking'].includes(row.status)) row.status = 'blocked'
            if (['unknown', 'blocked'].includes(row.status)) row.reason = this.$t('message.userdata.delete.unavailable')
          }
        }
        this.busy = false
      }
    },
    async checkUnknown () {
      if (this.busy || !this.active() || !this.allowed) return
      this.busy = true
      try { for (const row of [...this.checkableRows]) { if (!this.active() || !this.allowed) break; row.reason = ''; await this.reconcile(row) } } finally { this.busy = false }
    },
    beforeUnload (event) { if (this.busy) { event.preventDefault(); event.returnValue = '' } },
    close () {
      if (this.busy) return
      const removed = this.rows.filter(row => ['success', 'absent'].includes(row.status)).map(row => row.target.id)
      const leavingDetail = this.$route.params?.id && removed.includes(this.$route.params.id)
      this.$emit('close-action', { started: this.started, removed, retained: this.rows.filter(row => !removed.includes(row.target.id)).map(row => row.target.id) })
      if (this.started && !leavingDetail) this.$emit('refresh-data')
    }
  }
}
</script>
<style scoped lang="less">
.userdata-delete-dialog :deep(td) { overflow-wrap: anywhere; word-break: break-word; }
</style>

<style scoped>
.userdata-delete-content { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.userdata-delete-content > p { margin: 0; }
.userdata-delete-content > .ant-btn { align-self: flex-start; }
</style>

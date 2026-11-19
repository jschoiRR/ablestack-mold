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
  <MoldDialog
class="ssh-keypair-delete-dialog"
:size="rows.length > 1 ? 'lg' : 'md'"
    :title="$t('label.ssh.keypair.delete')"
:closable="!busy"
:keyboard="!busy"
@cancel="close"
    @keydown.ctrl.enter.stop.prevent>
    <a-alert type="warning" show-icon :message="$t('message.ssh.keypair.delete.scope')" />
    <div class="mold-dialog-toolbar">
      <strong>{{ $t('message.ssh.keypair.delete.count', { count: rows.length }) }}</strong>
      <a-button v-if="!started" :disabled="busy || usageBusy" :loading="usageBusy" @click="loadUsage">{{ $t('label.ssh.keypair.usage.refresh') }}</a-button>
    </div>
    <p v-if="busy" role="status">{{ $t('message.ssh.keypair.delete.processing') }}</p>
    <p v-if="started" role="status">{{ $t('message.ssh.keypair.delete.results', totals) }}</p>
    <a-alert v-if="!rows.length || !allowed" type="error" :message="$t('message.ssh.keypair.delete.unavailable')" />
    <template v-if="rows.length === 1">
      <a-descriptions :column="1" bordered size="small" class="ssh-keypair-delete-summary">
        <a-descriptions-item :label="$t('label.name')">{{ rows[0].target.name }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.id')">{{ rows[0].target.id }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.fingerprint')">{{ rows[0].target.fingerprint }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.account')">{{ rows[0].target.account }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.domain')">{{ rows[0].target.domain || rows[0].target.domainid }}</a-descriptions-item>
        <a-descriptions-item v-if="rows[0].target.projectid" :label="$t('label.project')">{{ rows[0].target.project || rows[0].target.projectid }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.ssh.keypair.usage')"><span :data-usage-state="rows[0].usage.state">{{ usageText(rows[0]) }}</span></a-descriptions-item>
        <a-descriptions-item :label="$t('label.operation.status')"><span :data-delete-state="rows[0].status">{{ statusText(rows[0]) }}</span></a-descriptions-item>
        <a-descriptions-item v-if="rows[0].reason" :label="$t('label.ssh.keypair.delete.reason')">{{ rows[0].reason }}</a-descriptions-item>
      </a-descriptions>
    </template>
    <a-table
v-else
:columns="columns"
:data-source="rows"
:row-key="row => row.target.id"
:pagination="{ pageSize: 10, showSizeChanger: false }"
:scroll="{ x: 1000 }"
size="small"
class="ssh-keypair-delete-table">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'"><strong>{{ record.target.name }}</strong><div>{{ record.target.id }}</div></template>
        <template v-else-if="column.key === 'fingerprint'">{{ record.target.fingerprint }}</template>
        <template v-else-if="column.key === 'owner'">{{ record.target.account }}<div>{{ record.target.domain || record.target.domainid }}</div><div v-if="record.target.projectid">{{ $t('label.project') }}: {{ record.target.project || record.target.projectid }}</div></template>
        <template v-else-if="column.key === 'usage'"><span :data-usage-state="record.usage.state">{{ usageText(record) }}</span></template>
        <template v-else-if="column.key === 'status'"><span :data-delete-state="record.status">{{ statusText(record) }}</span></template>
        <template v-else-if="column.key === 'reason'">{{ record.reason }}</template>
      </template>
    </a-table>
    <p class="mold-dialog-section">{{ $t(started ? 'message.ssh.keypair.usage.before.delete' : 'message.ssh.keypair.usage.visibility') }}</p>
    <template #footer>
      <a-button ref="cancel" :disabled="busy" @click="close">{{ $t(started ? 'label.close' : 'label.cancel') }}</a-button>
      <a-button v-if="checkableRows.length" :disabled="busy || !allowed" @click="checkUnknown">{{ $t('label.ssh.keypair.delete.check') }}</a-button>
      <a-button v-if="eligibleRows.length || deleting" type="primary" danger :disabled="!allowed || busy || usageBusy" :loading="busy" :aria-label="submitLabel" @click="execute">{{ submitLabel }}</a-button>
    </template>
  </MoldDialog>
</template>
<script>
import MoldDialog from '@/components/view/MoldDialog'
import { getAPI, postAPI } from '@/api'
import { sshKeyPairDeleteParams, sshKeyPairDeleteLookup, sshKeyPairDeleteIdentity, sshKeyPairDeleteError } from '@/utils/sshKeyPairDeletion'
import { sshKeyPairDetailFailure, isSshKeyPairDetailRoute } from '@/utils/sshKeyPairIdentity'
import { sshKeyPairUsageParams, sshKeyPairUsageResult } from '@/utils/sshKeyPairUsage'

export default {
  name: 'DeleteSSHKeyPair',
  components: { MoldDialog },
  props: { currentAction: { type: Object, required: true } },
  emits: ['close-action', 'refresh-data'],
  data () {
    return { rows: (this.currentAction.sshKeyPairTargets || []).map(target => ({ target, status: sshKeyPairDeleteParams(target) ? 'ready' : 'blocked', reason: '', usage: { state: 'loading' } })), busy: false, deleting: false, usageBusy: false, started: false, disposed: false, usageGeneration: 0, session: '' }
  },
  computed: {
    submitLabel () { return this.$t(this.started && !this.deleting ? 'label.ssh.keypair.delete.retry' : 'label.delete') },
    allowed () { return ['listSSHKeyPairs', 'deleteSSHKeyPair'].every(api => api in this.$store.getters.apis) },
    eligibleRows () { return this.rows.filter(row => ['ready', 'failed'].includes(row.status)) },
    unknownRows () { return this.rows.filter(row => row.status === 'unknown') },
    checkableRows () { return this.rows.filter(row => ['unknown', 'blocked'].includes(row.status) && sshKeyPairDeleteParams(row.target)) },
    totals () { return { success: this.rows.filter(row => ['success', 'absent'].includes(row.status)).length, failed: this.rows.filter(row => ['failed', 'blocked'].includes(row.status)).length, unknown: this.unknownRows.length } },
    columns () { return [['name', 200], ['fingerprint', 180], ['owner', 180], ['usage', 150], ['status', 130], ['reason', 230]].map(([key, width]) => ({ key, title: this.$t({ name: 'label.name', fingerprint: 'label.fingerprint', owner: 'label.ssh.keypair.owner', usage: 'label.ssh.keypair.usage', status: 'label.operation.status', reason: 'label.ssh.keypair.delete.reason' }[key]), width })) }
  },
  mounted () {
    this.session = this.sessionKey()
    this.$nextTick(() => this.$refs.cancel?.$el?.focus())
    this.loadUsage()
    window.addEventListener('beforeunload', this.beforeUnload)
  },
  beforeUnmount () { this.disposed = true; this.usageGeneration++; window.removeEventListener('beforeunload', this.beforeUnload) },
  methods: {
    sessionKey () { return JSON.stringify([this.$store.getters.userInfo?.id, this.$store.state?.user?.token]) },
    active () { return !this.disposed && this.session === this.sessionKey() },
    statusText (row) { return this.$t('label.ssh.keypair.delete.state.' + row.status) },
    usageText (row) { return row.usage.state === 'ready' ? this.$t(row.usage.count ? 'message.ssh.keypair.usage.count' : 'message.ssh.keypair.usage.none', { count: row.usage.count }) : this.$t('message.ssh.keypair.usage.' + row.usage.state) },
    async identity (row) {
      if (!this.active() || !this.allowed) return 'forbidden'
      const params = sshKeyPairDeleteLookup(row.target)
      if (!params) return 'mismatch'
      try {
        const json = await getAPI('listSSHKeyPairs', params, { preserveOnFailure: true, timeout: 15000 })
        return this.active() && this.allowed ? sshKeyPairDeleteIdentity(json, row.target) : 'forbidden'
      } catch (error) { return sshKeyPairDetailFailure(error) }
    },
    async loadUsage () {
      if (this.busy || this.usageBusy || this.started) return
      this.usageBusy = true
      const generation = ++this.usageGeneration
      const pending = [...this.rows]
      const worker = async () => {
        while (pending.length && this.active() && generation === this.usageGeneration) {
          const row = pending.shift()
          row.usage = { state: 'loading' }
          if (!('listVirtualMachines' in this.$store.getters.apis)) { row.usage = { state: 'forbidden' }; continue }
          const identity = await this.identity(row)
          if (!this.active() || generation !== this.usageGeneration) return
          if (identity !== 'ready') { row.usage = { state: identity === 'forbidden' ? 'forbidden' : identity === 'missing' ? 'missing' : 'error' }; continue }
          try {
            const json = await getAPI('listVirtualMachines', sshKeyPairUsageParams(row.target), { preserveOnFailure: true, timeout: 15000 })
            const result = sshKeyPairUsageResult(json)
            const current = await this.identity(row)
            if (this.active() && generation === this.usageGeneration) row.usage = current === 'ready' ? result : { state: current === 'forbidden' ? 'forbidden' : 'error' }
          } catch (error) { if (this.active() && generation === this.usageGeneration) row.usage = { state: sshKeyPairDetailFailure(error) === 'forbidden' ? 'forbidden' : 'error' } }
        }
      }
      try { await Promise.all(Array.from({ length: Math.min(4, pending.length) }, worker)) } finally { if (generation === this.usageGeneration) this.usageBusy = false }
    },
    async reconcile (row) {
      const status = await this.identity(row)
      if (!this.active()) return
      if (status === 'missing') { row.status = 'success'; row.reason = this.$t('message.ssh.keypair.delete.confirmed.absent') } else if (status === 'ready') { row.status = 'failed'; row.reason = this.$t('message.ssh.keypair.delete.confirmed.present') } else if (['mismatch', 'ambiguous'].includes(status)) { row.status = 'blocked'; row.reason = this.$t('message.ssh.keypair.delete.changed') } else { row.status = 'unknown'; row.reason = this.$t('message.ssh.keypair.delete.unknown') }
    },
    async execute () {
      if (this.busy || this.usageBusy || !this.active() || !this.allowed) return
      this.busy = true
      this.deleting = true
      this.started = true
      this.usageGeneration++
      const pending = [...this.eligibleRows]
      try {
        for (const row of pending) {
          if (!this.active() || !this.allowed) break
          row.status = 'checking'; row.reason = ''
          const status = await this.identity(row)
          if (!this.active() || !this.allowed) { row.status = 'blocked'; row.reason = this.$t('message.ssh.keypair.delete.preflight.failed'); break }
          if (status === 'missing') { row.status = 'absent'; row.reason = this.$t('message.ssh.keypair.delete.already.absent'); continue }
          if (status !== 'ready') { row.status = 'blocked'; row.reason = this.$t(status === 'mismatch' || status === 'ambiguous' ? 'message.ssh.keypair.delete.changed' : 'message.ssh.keypair.delete.preflight.failed'); continue }
          row.status = 'deleting'
          try {
            const json = await postAPI('deleteSSHKeyPair', sshKeyPairDeleteParams(row.target), { preserveOnFailure: true, timeout: 30000 })
            if (!this.active()) break
            if (json?.deletesshkeypairresponse?.success === true) row.status = 'success'
            else { row.status = 'unknown'; await this.reconcile(row) }
          } catch (error) {
            if (!this.active()) break
            Object.assign(row, { status: sshKeyPairDeleteError(error).state, reason: sshKeyPairDeleteError(error).reason })
            if (row.status === 'unknown') await this.reconcile(row)
          }
        }
      } finally { this.busy = false; this.deleting = false }
    },
    async checkUnknown () {
      if (this.busy || !this.active() || !this.allowed) return
      this.busy = true
      try { for (const row of [...this.checkableRows]) { if (!this.active()) break; await this.reconcile(row) } } finally { this.busy = false }
    },
    beforeUnload (event) { if (this.busy) { event.preventDefault(); event.returnValue = '' } },
    close () {
      if (this.busy) return
      const removed = this.rows.filter(row => ['success', 'absent'].includes(row.status)).map(row => row.target.id)
      const leavingDetail = isSshKeyPairDetailRoute(this.$route) && removed.includes(this.rows[0]?.target.id)
      this.$emit('close-action', { clearSelection: this.started, removed })
      if (this.started && !leavingDetail) this.$emit('refresh-data')
    }
  }
}
</script>
<style scoped lang="less">
.ssh-keypair-delete-summary :deep(.ant-descriptions-item-content),
.ssh-keypair-delete-table :deep(td) { overflow-wrap: anywhere; word-break: break-word; }
.ssh-keypair-delete-table :deep(.ant-table) { background: var(--ui-bg-surface); }
</style>

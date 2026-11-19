<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with this
work for additional information regarding copyright ownership. The ASF
licenses this file under the Apache License, Version 2.0 (the "License");
you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 .
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. -->
<template>
  <MoldDialog class="affinity-dialog" size="md" :title="$t('label.delete.affinity.group')" @cancel="$emit('close-action')">
    <p v-if="progress">{{ progress }}</p>
    <a-descriptions :column="1" bordered>
      <a-descriptions-item :label="$t('label.name')">{{ resource.name }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.affinity.owner')">{{ resource.project || resource.account }} / {{ resource.domain }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.affinitymembercount')">{{ count === null ? $t('label.loading') : count }}</a-descriptions-item>
    </a-descriptions>
    <p>{{ $t('message.affinity.members.delete') }}</p>
    <a-alert v-if="error" type="error" :message="text(error)"><template #description><a-button @click="load">{{ $t('label.retry') }}</a-button></template></a-alert>
    <a-alert v-if="operation.status !== 'idle'" :type="operation.status === 'failed' ? 'error' : 'info'" :message="$t('message.affinity.operation.' + operation.status)">
      <template #description>
        <p v-if="operation.error">{{ text(operation.error) }}</p>
        <p v-if="operation.jobId">{{ $t('label.affinity.job') }}: {{ operation.jobId }}</p>
        <p v-if="operation.status === 'unknown' && !operation.jobId">{{ $t('message.affinity.operation.no.receipt') }}</p>
        <a-button v-if="operation.jobId && ['unknown', 'timeout', 'cancelled'].includes(operation.status)" @click="receipt.recheck()">{{ $t('label.affinity.recheck') }}</a-button>
      </template>
    </a-alert>
    <template #footer>
      <a-button @click="$emit('close-action')">{{ $t('label.cancel') }}</a-button>
      <a-button type="primary" danger :disabled="count === null || !!error || loading || !['idle', 'failed'].includes(operation.status)" :loading="['preparing', 'submitting', 'pending', 'retrying'].includes(operation.status)" @click="submit">{{ $t('label.delete.affinity.group') }}</a-button>
    </template>
  </MoldDialog>
</template>
<script>
import { getAPI, postAPI } from '@/api'
import MoldDialog from '@/components/view/MoldDialog.vue'
import { affinityOwnerKey, affinitySessionScope, affinityApiError, getAffinityOperation } from '@/utils/affinityGroupOperations'
import { affinityMemberCount, affinityGroupQueryParams } from '@/utils/affinityGroupMembers'
export default {
  name: 'DeleteAffinityGroup',
  components: { MoldDialog },
  props: { resource: { type: Object, required: true }, queueMode: { type: Boolean, default: false }, progress: { type: String, default: '' } },
  emits: ['close-action', 'deleted'],
  data: () => ({ count: null, error: '', loading: false, operation: { status: 'idle' } }),
  computed: { scope () { return affinitySessionScope(this.$store) + '|' + this.resource.id } },
  created () {
    this.alive = true
    const store = this.$store; const scope = affinitySessionScope(store)
    this.receipt = getAffinityOperation(scope, 'delete-affinity-' + this.resource.id, { get: getAPI, post: postAPI, getScope: () => affinitySessionScope(store) })
    this.unsubscribe = this.receipt.subscribe(state => {
      if (!this.alive) return
      this.operation = state
      if (state.status === 'succeeded') {
        if (this.queueMode) this.$emit('deleted', this.resource.id)
        else { this.$emit('close-action'); this.$router.push('/affinitygroup') }
      }
    })
    this.load()
  },
  beforeUnmount () { this.alive = false; this.unsubscribe() },
  watch: { scope () { this.$emit('close-action') } },
  methods: {
    text (value) { return value.startsWith('message.') ? this.$t(value) : value },
    async group () {
      const response = await getAPI('listAffinityGroups', affinityGroupQueryParams(this.resource), { preserveOnFailure: true, timeout: 15000 })
      const group = response?.listaffinitygroupsresponse?.affinitygroup?.find(row => row.id === this.resource.id)
      if (!group || affinityOwnerKey(group) !== affinityOwnerKey(this.resource) || group.name !== this.resource.name) throw new Error('message.affinity.owner.changed')
      return group
    },
    async load () {
      this.loading = true; const scope = this.scope
      try { const group = await this.group(); if (this.alive && scope === this.scope) { this.count = affinityMemberCount(group); this.confirmedIds = [...(group.virtualmachineIds || [])].sort(); this.error = '' } } catch (error) { if (this.alive && scope === this.scope) this.error = affinityApiError(error) || error.message } finally { if (this.alive && scope === this.scope) this.loading = false }
    },
    async submit () {
      if (this.count === null || this.error || this.loading) return
      const scope = this.scope
      await this.receipt.run('deleteAffinityGroup', { id: this.resource.id }, async () => {
        const group = await this.group()
        if (!this.alive || scope !== this.scope) throw new Error('message.affinity.owner.changed')
        if (JSON.stringify([...(group.virtualmachineIds || [])].sort()) !== JSON.stringify(this.confirmedIds)) { await this.load(); throw new Error('message.affinity.members.delete.changed') }
      })
    }
  }
}
</script>

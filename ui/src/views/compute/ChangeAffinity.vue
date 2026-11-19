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

<template>
  <MoldDialog class="affinity-dialog" size="lg" :title="$t('label.change.affinity')" @cancel="closeAction">
    <div class="affinity-dialog-summary"><strong>{{ resource.displayname || resource.name }}</strong><span class="affinity-secondary">{{ ownerLabel }}</span></div>
    <a-form class="affinity-change-form" v-ctrl-enter="handleSubmit">

    <p v-html="$t('message.select.affinity.groups')" />

    <a-alert v-if="ownerLabel" type="info" show-icon :message="$t('label.affinity.owner') + ': ' + ownerLabel" />
    <a-alert v-if="!vmSupported" type="warning" show-icon :message="$t('message.affinity.vm.stopped')" />
    <a-alert v-if="fetchError" type="error" show-icon :message="errorText(fetchError)">
      <template #description><a-button size="small" :loading="fetching" @click="fetchData">{{ $t('label.retry') }}</a-button></template>
    </a-alert>
    <a-alert v-if="operation.status !== 'idle'" :type="operationType" show-icon :message="$t('message.affinity.operation.' + operation.status)">
      <template #description>
        <div v-if="operation.error" role="alert">{{ errorText(operation.error) }}</div>
        <div v-if="operation.jobId">{{ $t('label.affinity.job') }}: {{ operation.jobId }}</div>
        <div v-if="operationLocked">{{ $t('message.affinity.operation.close') }}</div>
        <div v-if="operation.status === 'unknown' && !operation.jobId">{{ $t('message.affinity.operation.no.receipt') }}</div>
        <a-button v-if="canRecheck" size="small" style="margin-top: 8px" @click="recheckOperation">{{ $t('label.affinity.recheck') }}</a-button>
      </template>
    </a-alert>

    <affinity-selection-summary
      :key="resourceOwnerKey"
      :ids="selectedRowKeys"
      :groups="[...(resource.affinitygroup || []), ...items]"
      :disabled="operationLocked || !vmSupported"
      @change="onSelectChange" />

    <div class="affinity-dialog-search">
      <a-input-search
        :placeholder="$t('label.search')"
        v-model:value="filter"
        :disabled="operationLocked"
        @search="handleSearch"
        v-focus="true" />
    </div>

    <div class="form__item">
      <p v-if="loaded && !fetching" role="status" class="affinity-result-count">{{ $t('label.total') }} {{ total }} {{ $t('label.items') }} · {{ options.page }} {{ $t('label.page') }}</p>
      <a-table
        size="small"
        :loading="loading"
        :columns="columns"
        :dataSource="items"
        :rowKey="record => record.id || record.name"
        :pagination="{current: options.page, pageSize: options.pageSize, showSizeChanger: true, total: total, disabled: operationLocked}"
        :rowSelection="{selectedRowKeys: selectedRowKeys, preserveSelectedRowKeys: true, onChange: onSelectChange, getCheckboxProps: () => ({ disabled: operationLocked || !vmSupported })}"
        @change="handleTableChange"
        :scroll="{ x: 640 }">
        <template #bodyCell="{ column, record, text }"><AffinityGroupPolicy v-if="column.dataIndex === 'type'" :type="record.type" compact /><span v-else>{{ text || '—' }}</span></template>
      </a-table>
    </div>

    </a-form>
    <template #footer>
      <a-button @click="closeAction">{{ $t('label.cancel') }}</a-button>
      <a-button :loading="busy" :disabled="!canSubmit" ref="submit" type="primary" @click="handleSubmit">{{ $t('label.ok') }}</a-button>
    </template>
  </MoldDialog>
</template>

<script>
import { getAPI, postAPI } from '@/api'
import { genericCompare } from '@/utils/sort.js'
import { affinityOwnerParams, affinityOwnerKey, affinitySessionScope, affinityApiError, affinityVmCanChange, getAffinityOperation } from '@/utils/affinityGroupOperations'
import { affinitySelectionIds } from '@/utils/affinityGroupSelection'
import AffinitySelectionSummary from './wizard/AffinitySelectionSummary.vue'
import MoldDialog from '@/components/view/MoldDialog'
import AffinityGroupPolicy from './AffinityGroupPolicy'

export default {
  name: 'ChangeAffinity',
  components: { AffinitySelectionSummary, MoldDialog, AffinityGroupPolicy },
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  inject: ['parentFetchData'],
  data () {
    return {
      items: [],
      total: 0,
      columns: [
        {
          dataIndex: 'name',
          title: this.$t('label.name'),
          sorter: (a, b) => genericCompare(a?.name || '', b?.name || '')
        },
        {
          dataIndex: 'type',
          title: this.$t('label.type'),
          sorter: (a, b) => genericCompare(a?.type || '', b?.type || '')
        },
        {
          dataIndex: 'description',
          title: this.$t('label.description'),
          sorter: (a, b) => genericCompare(a?.description || '', b?.description || '')
        }
      ],
      selectedRowKeys: [],
      options: {
        page: 1,
        pageSize: 10,
        keyword: ''
      },
      filter: '',
      fetching: false,
      loaded: false,
      fetchError: '',
      operation: { status: 'idle', jobId: '', ids: [], error: '' }
    }
  },
  created () {
    this.alive = true
    this.requestToken = 0
    for (const group of this.resource.affinitygroup || []) {
      this.selectedRowKeys.push(group.id)
    }
    this.bindOperation()
    this.fetchData()
  },
  beforeUnmount () {
    this.alive = false
    this.requestToken++
    if (this.unsubscribeOperation) this.unsubscribeOperation()
  },
  computed: {
    securityScope () { return affinitySessionScope(this.$store) },
    resourceOwnerKey () {
      try { return this.resource.id + '|' + affinityOwnerKey(this.resource) } catch (_) { return this.resource.id + '|unavailable' }
    },
    vmSupported () { return affinityVmCanChange(this.resource) },
    loading () { return this.fetching || this.busy },
    busy () { return ['preparing', 'submitting', 'pending', 'retrying'].includes(this.operation.status) },
    operationLocked () { return this.busy || ['unknown', 'timeout', 'cancelled'].includes(this.operation.status) },
    canRecheck () { return this.operation.jobId && ['unknown', 'timeout', 'cancelled'].includes(this.operation.status) },
    canSubmit () { return this.loaded && !this.fetchError && !this.fetching && !this.operationLocked && this.vmSupported },
    operationType () { return this.operation.status === 'failed' ? 'error' : (['unknown', 'timeout', 'cancelled'].includes(this.operation.status) ? 'warning' : 'info') },
    ownerLabel () {
      return [this.resource.project || this.resource.account, this.resource.domain].filter(Boolean).join(' / ')
    }
  },
  watch: {
    resourceOwnerKey () {
      if (this.unsubscribeOperation) this.unsubscribeOperation()
      this.requestToken++
      this.items = []
      this.total = 0
      this.filter = ''
      this.options = { page: 1, pageSize: 10, keyword: '' }
      this.fetchError = ''
      this.loaded = false
      this.selectedRowKeys = (this.resource.affinitygroup || []).map(group => group.id)
      this.bindOperation()
      this.fetchData()
    },
    securityScope () {
      if (this.unsubscribeOperation) this.unsubscribeOperation()
      this.bindOperation()
      this.$emit('close-action')
    }
  },
  methods: {
    errorText (text) { return text.startsWith('message.') ? this.$t(text) : text },
    bindOperation () {
      const store = this.$store
      this.affinityOperation = getAffinityOperation(this.securityScope, this.resource.id, { get: getAPI, post: postAPI, getScope: () => affinitySessionScope(store) })
      const previous = this.affinityOperation.snapshot()
      // Preserve this editor's reviewed selection. Terminal receipts belonging
      // to a group-centered workflow must not replace fresh VM memberships.
      if (previous.status !== 'idle' &&
        (!previous.context || !['failed', 'succeeded'].includes(previous.status)) &&
        Object.prototype.hasOwnProperty.call(previous.params || {}, 'affinitygroupids')) this.selectedRowKeys = previous.ids
      if (previous.status === 'succeeded' || (previous.status === 'failed' && previous.context)) this.affinityOperation.acknowledge()
      this.announced = false
      const scope = this.securityScope
      this.unsubscribeOperation = this.affinityOperation.subscribe(state => {
        if (!this.alive || this.securityScope !== scope) return
        this.operation = state
        if (state.status === 'succeeded' && !this.announced) {
          this.announced = true
          this.$notification.success({ message: this.$t('message.success.change.affinity.group') })
          try { this.parentFetchData() } finally { this.$emit('close-action') }
        }
      })
    },
    fetchData () {
      if (this.operationLocked && this.loaded) return
      let owner
      try { owner = affinityOwnerParams(this.resource) } catch (error) {
        this.fetchError = error.message
        return
      }
      const token = ++this.requestToken
      const scope = this.securityScope
      this.fetching = true
      this.fetchError = ''
      return getAPI('listAffinityGroups', {
        keyword: this.options.keyword,
        ...owner,
        page: this.options.page,
        pageSize: this.options.pageSize,
        response: 'json'
      }, { preserveOnFailure: true, timeout: 15000 }).then(response => {
        if (!this.alive || token !== this.requestToken || scope !== this.securityScope) return
        const result = response.listaffinitygroupsresponse
        this.total = result.count || 0
        this.items = result.affinitygroup || []
        this.loaded = true
      }).catch(error => {
        if (this.alive && token === this.requestToken && scope === this.securityScope) this.fetchError = affinityApiError(error) || 'message.affinity.lookup.failed'
      }).finally(() => {
        if (this.alive && token === this.requestToken) this.fetching = false
      })
    },
    onSelectChange (selectedRowKeys) {
      if (this.operationLocked) return
      this.selectedRowKeys = affinitySelectionIds(selectedRowKeys)
    },
    handleSearch (keyword) {
      if (this.operationLocked) return
      this.filter = keyword
      this.options.keyword = keyword
      this.options.page = 1
      this.fetchData()
    },
    handleTableChange (pagination, filters, sorter, extra) {
      if (this.operationLocked) return
      if (extra?.action === 'sort' || extra?.action === 'filter') return
      this.options.page = pagination.pageSize !== this.options.pageSize ? 1 : pagination.current
      this.options.pageSize = pagination.pageSize
      this.fetchData()
    },
    closeAction () {
      this.$emit('close-action')
    },
    async handleSubmit () {
      if (!this.canSubmit) return
      const ownerKey = affinityOwnerKey(this.resource)
      const scope = this.securityScope
      const ids = [...this.selectedRowKeys]
      return this.affinityOperation.run('updateVMAffinityGroup', {
        id: this.resource.id,
        affinitygroupids: ids.join(',')
      }, async () => {
        const response = await getAPI('listVirtualMachines', { id: this.resource.id, details: 'min,affgrp', ...(this.resource.projectid ? { projectid: this.resource.projectid } : {}) }, { preserveOnFailure: true, timeout: 15000 })
        const vm = response.listvirtualmachinesresponse?.virtualmachine?.[0]
        if (!vm || !affinityVmCanChange(vm)) throw new Error('message.affinity.vm.stopped')
        if (scope !== this.securityScope || affinityOwnerKey(vm) !== ownerKey) throw new Error('message.affinity.owner.changed')
        // An off-page existing selection is checked through the same owner-scoped API.
        for (const id of ids) {
          const groups = await getAPI('listAffinityGroups', { id, ...affinityOwnerParams(vm) }, { preserveOnFailure: true, timeout: 15000 })
          if (!groups.listaffinitygroupsresponse?.affinitygroup?.some(group => group.id === id)) throw new Error('message.affinity.selection.unavailable')
        }
      })
    },
    recheckOperation () {
      return this.affinityOperation.recheck()
    }
  }
}
</script>

<style scoped lang="scss">
.form {
  width: 90vw;
  @media (min-width: 800px) {
    width: 45vw;
  }
}
</style>

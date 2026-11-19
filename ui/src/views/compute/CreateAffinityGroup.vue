<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with this work
for additional information regarding copyright ownership. The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy at
http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or
agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
or implied. See the License for the specific language governing permissions and
limitations under the License. -->
<template>
  <MoldDialog class="affinity-create-dialog affinity-dialog" size="md" :title="$t('label.add.affinity.group')" @cancel="closeAction">
    <template #title>
      {{ $t('label.add.affinity.group') }}
      <a v-if="currentAction.docHelp" :href="documentationUrl($config.docBase, currentAction.docHelp, $i18n.locale)" :aria-label="$t('label.help')" target="_blank" rel="noopener noreferrer"><question-circle-outlined /></a>
    </template>
    <a-form ref="createForm" :model="form" :rules="rules" layout="vertical" @keydown.ctrl.enter.prevent="handleSubmit" @finish="handleSubmit">
      <a-alert v-if="typesLoading || typesError || !types.length" :type="typesError ? 'error' : 'info'" show-icon :message="$t(typesLoading ? 'message.affinity.types.loading' : typesError ? 'message.affinity.types.failed' : 'message.affinity.types.empty')">
        <template v-if="!typesLoading" #description><a-button size="small" :disabled="operationLocked" @click="loadTypes">{{ $t('label.retry') }}</a-button></template>
      </a-alert>
      <a-alert v-if="operation.status !== 'idle'" :type="operation.status === 'failed' ? 'error' : operationLocked && !busy ? 'warning' : 'info'" show-icon :message="$t('message.affinity.create.' + operation.status)">
        <template #description>
          <div v-if="operation.error" role="alert">{{ errorText(operation.error) }}</div>
          <div v-if="operation.jobId">{{ $t('label.affinity.job') }}: {{ operation.jobId }}</div>
          <div v-if="operationLocked">{{ $t('message.affinity.create.close') }}</div>
          <div v-if="operation.status === 'unknown' && !operation.jobId">{{ $t('message.affinity.create.no.receipt') }}</div>
          <a-button v-if="canRecheck" size="small" @click="recheckOperation">{{ $t('label.affinity.recheck') }}</a-button>
        </template>
      </a-alert>
      <p>{{ $t('message.affinity.create.limits') }}</p>
      <a-form-item name="name" :label="$t('label.name')" required>
        <a-input ref="nameInput" v-model:value="form.name" :disabled="operationLocked" v-focus="true" @keydown.enter.prevent="handleSubmit" />
      </a-form-item>
      <a-form-item name="description" :label="$t('label.description')">
        <a-textarea ref="descriptionInput" v-model:value="form.description" :disabled="operationLocked" :auto-size="{ minRows: 2, maxRows: 4 }" />
      </a-form-item>
      <a-form-item name="type" :label="$t('label.type')" required>
        <a-select ref="typeInput" v-model:value="form.type" :disabled="typesLoading || !!typesError || operationLocked || !types.length" :loading="typesLoading" show-search option-filter-prop="label">
          <a-select-option v-for="type in types" :key="type.id" :value="type.id" :label="type.labelKey ? $t(type.labelKey) : type.name">{{ type.labelKey ? $t(type.labelKey) : type.name }}</a-select-option>
        </a-select>
      </a-form-item>
      <p v-if="form.type" class="affinity-secondary"><AffinityGroupPolicy :type="form.type" /></p>
      <AffinityGroupOwner v-if="isAdmin && !operationLocked" :initial-owner="owner" :disabled="validating" @change="ownerChanged" />
      <p v-else class="affinity-secondary">{{ $t('label.affinity.create.owner') }}: {{ ownerSummary }}</p>
    </a-form>
    <template #footer>
      <a-button @click="closeAction">{{ $t('label.cancel') }}</a-button>
      <a-button type="primary" :loading="busy || validating" :disabled="!canSubmit" @click="handleSubmit">{{ $t('label.ok') }}</a-button>
    </template>
  </MoldDialog>
</template>
<script>
import { getAPI, postAPI } from '@/api'
import MoldDialog from '@/components/view/MoldDialog'
import AffinityGroupOwner from './AffinityGroupOwner'
import AffinityGroupPolicy from './AffinityGroupPolicy'
import { affinityNameValid, affinityTypeOptions } from '@/utils/affinityGroupCreation'
import { affinitySessionScope, getAffinityOperation } from '@/utils/affinityGroupOperations'
import { documentationUrl } from '@/utils/cniFileImport'

export default {
  name: 'CreateAffinityGroup',
  components: { MoldDialog, AffinityGroupOwner, AffinityGroupPolicy },
  props: { currentAction: { type: Object, default: () => ({}) } },
  emits: ['refresh-data', 'close-action'],
  data () {
    const user = this.$store.getters.userInfo || {}; const project = this.$store.getters.project || {}
    return {
      form: { name: '', description: '', type: undefined },
      owner: { domainid: project.domainid || user.domainid, ...(project.id ? { projectid: project.id } : { account: user.account }) },
      ownerReady: !['Admin', 'DomainAdmin'].includes(user.roletype),
      ownerLabels: null,
      validating: false,
      types: [],
      typesLoading: true,
      typesError: false,
      operation: { status: 'idle', jobId: '', error: '' }
    }
  },
  computed: {
    ownerSummary () {
      const user = this.$store.getters.userInfo || {}; const project = this.$store.getters.project || {}
      const labels = this.ownerLabels?.key === JSON.stringify(this.owner) ? this.ownerLabels : {}
      return [labels.name || (this.owner.projectid ? (project.id === this.owner.projectid ? project.name : this.owner.projectid) : this.owner.account), labels.domain || (user.domainid === this.owner.domainid ? user.domain : this.owner.domainid)].filter(Boolean).join(' / ')
    },
    isAdmin () { return ['Admin', 'DomainAdmin'].includes(this.$store.getters.userInfo.roletype) },
    securityScope () { return affinitySessionScope(this.$store) },
    busy () { return ['preparing', 'submitting', 'pending', 'retrying'].includes(this.operation.status) },
    operationLocked () { return this.busy || ['unknown', 'timeout', 'cancelled'].includes(this.operation.status) },
    canRecheck () { return this.operation.jobId && ['unknown', 'timeout', 'cancelled'].includes(this.operation.status) },
    canSubmit () { return !this.validating && !this.operationLocked && this.ownerReady && !this.typesLoading && !this.typesError && this.types.length > 0 },
    rules () {
      return {
        name: [{ validator: (_, name) => affinityNameValid(name) ? Promise.resolve() : Promise.reject(new Error(this.$t('message.affinity.create.name.invalid'))) }],
        description: [{ validator: (_, text) => (text || '').trim().length <= 255 ? Promise.resolve() : Promise.reject(new Error(this.$t('message.affinity.create.description.invalid'))) }],
        type: [{ validator: (_, type) => this.types.some(row => row.id === type) ? Promise.resolve() : Promise.reject(new Error(this.$t('label.required'))) }]
      }
    }
  },
  created () { this.alive = true; this.typesVersion = 0; this.bindOperation(); this.loadTypes() },
  beforeUnmount () { this.alive = false; this.typesVersion++; if (this.unsubscribe) this.unsubscribe() },
  watch: { securityScope () { if (this.unsubscribe) this.unsubscribe(); this.$emit('close-action') } },
  methods: {
    documentationUrl,
    errorText (text) { return text.startsWith('message.') ? this.$t(text === 'message.affinity.operation.failed' ? 'message.affinity.create.failed' : text) : text },
    ownerChanged ({ owner, ready, labels }) { this.owner = owner; this.ownerReady = ready; this.ownerLabels = labels ? { ...labels, key: JSON.stringify(owner) } : null },
    async loadTypes () {
      const version = ++this.typesVersion; const scope = this.securityScope
      this.typesLoading = true; this.typesError = false; this.types = []
      try {
        const json = await getAPI('listAffinityGroupTypes', { ignoreproject: true }, { preserveOnFailure: true, timeout: 15000 })
        if (!this.alive || version !== this.typesVersion || scope !== this.securityScope) return
        this.types = affinityTypeOptions(json)
        if (!this.types.some(row => row.id === this.form.type) && !this.operationLocked) this.form.type = undefined
      } catch (_) { if (this.alive && version === this.typesVersion && scope === this.securityScope) this.typesError = true } finally {
        if (this.alive && version === this.typesVersion && scope === this.securityScope) this.typesLoading = false
      }
    },
    bindOperation () {
      const store = this.$store; const scope = this.securityScope
      this.creation = getAffinityOperation(scope, 'create-affinity-group', { get: getAPI, post: postAPI, getScope: () => affinitySessionScope(store) })
      const previous = this.creation.snapshot()
      if (previous.params?.name) {
        const { name, description, type, account, domainid, projectid } = previous.params
        this.form = { name, description, type }; this.owner = { domainid, ...(projectid ? { projectid } : { account }) }
      }
      this.announced = false
      this.unsubscribe = this.creation.subscribe(state => {
        if (!this.alive || scope !== this.securityScope) return
        this.operation = state
        if (state.status === 'failed') this.$nextTick(() => { if (this.$refs.nameInput) this.$refs.nameInput.focus() })
        if (state.status === 'succeeded' && !this.announced) {
          this.announced = true
          this.$message.success(this.$t('label.add.affinity.group') + ' - ' + state.params.name)
          this.creation.acknowledge(); this.$emit('refresh-data'); this.$emit('close-action')
        }
      })
    },
    async handleSubmit (event) {
      if (event?.preventDefault) event.preventDefault()
      if (!this.canSubmit) return
      this.validating = true
      const owner = JSON.stringify(this.owner); const scope = this.securityScope
      try {
        await this.$refs.createForm.validate()
        if (!this.alive || scope !== this.securityScope || owner !== JSON.stringify(this.owner) || !this.ownerReady || this.typesLoading || this.typesError || !this.types.some(row => row.id === this.form.type)) return
        const params = { name: this.form.name.trim(), description: (this.form.description || '').trim(), type: this.form.type, ...this.owner, ignoreproject: true }
        await this.creation.run('createAffinityGroup', params)
      } catch (error) {
        if (error?.errorFields?.length) {
          const field = error.errorFields[0].name
          this.$refs.createForm.scrollToField(field)
          const input = this.$refs[field[0] + 'Input']
          if (input) input.focus()
        }
      } finally { this.validating = false }
    },
    recheckOperation () { return this.creation.recheck() },
    closeAction () { this.$emit('close-action') }
  }
}
</script>
<style scoped lang="less">
.affinity-create-dialog :deep(.ant-alert) { margin-bottom: 16px; }
</style>

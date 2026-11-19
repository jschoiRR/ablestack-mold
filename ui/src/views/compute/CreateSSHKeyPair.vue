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
  <MoldDialog
    class="ssh-keypair-dialog"
    size="md"
    :title="$t(isSubmitted ? 'label.ssh.privatekey.result' : mode === 'register' ? 'label.register.ssh.key.pair' : 'label.create.ssh.key.pair')"
    :closable="!loading"
    :keyboard="!loading"
    @cancel="closeAction">
    <template #title>
      {{ $t(isSubmitted ? 'label.ssh.privatekey.result' : mode === 'register' ? 'label.register.ssh.key.pair' : 'label.create.ssh.key.pair') }}
      <a v-if="currentAction.docHelp && $config?.docBase" :href="$config.docBase + '/' + currentAction.docHelp" :aria-label="$t('label.help')" target="_blank" rel="noopener noreferrer"><question-circle-outlined /></a>
    </template>
    <div ref="content" class="ssh-keypair-dialog-content">
      <template v-if="!isSubmitted">
        <p>{{ $t(mode === 'register' ? 'message.ssh.keypair.register' : 'message.ssh.keypair.generate') }}</p>
        <p v-if="loading" role="status">{{ $t('message.ssh.keypair.submitting') }}</p>
        <a-spin :spinning="loading">
          <a-form id="ssh-keypair-form" @keydown.ctrl.enter.prevent="handleSubmit" :ref="formRef" :model="form" :rules="rules" @finish="handleSubmit" layout="vertical">
            <a-form-item name="name" ref="name">
              <template #label><tooltip-label :title="$t('label.name')" :tooltip="apiParams.name?.description" /></template>
              <a-input ref="nameInput" v-model:value="form.name" :disabled="loading" :placeholder="apiParams.name?.description" v-focus="true" />
            </a-form-item>
            <a-form-item v-if="mode === 'register'" name="publickey" ref="publickey">
              <template #label><tooltip-label :title="$t('label.publickey')" :tooltip="apiParams.publickey?.description" /></template>
              <a-input ref="publickeyInput" v-model:value="form.publickey" :disabled="loading" :placeholder="apiParams.publickey?.description" />
            </a-form-item>
            <a-form-item name="domainid" ref="domainid" v-if="isAdminOrDomainAdmin()">
              <template #label><tooltip-label :title="$t('label.domainid')" :tooltip="apiParams.domainid?.description" /></template>
              <a-select
id="domain-selection"
v-model:value="form.domainid"
showSearch
optionFilterProp="label"
:disabled="loading"
:filterOption="(input, option) => option.label.toLowerCase().includes(input.toLowerCase())"
:loading="domainLoading"
                :placeholder="apiParams.domainid?.description"
                @change="val => handleDomainChanged(domains[val])">
                <a-select-option v-for="(opt, optIndex) in domains" :key="optIndex" :label="opt.path || opt.name || opt.description || ''">{{ opt.path || opt.name || opt.description }}</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item name="account" ref="account" v-if="isAdminOrDomainAdmin() && selectedDomain.id">
              <template #label>{{ $t('label.account') }}</template>
              <a-input v-model:value="form.account" :disabled="loading" :placeholder="apiParams.account?.description" />
            </a-form-item>
          </a-form>
        </a-spin>
      </template>
      <template v-else>
        <a-alert type="warning" show-icon :message="$t('message.ssh.privatekey.once')" :description="$t('message.ssh.privatekey.save')" />
        <a-descriptions class="mold-dialog-section" :column="1" bordered size="small">
          <a-descriptions-item v-for="item in resultSummary" :key="item.label" :label="$t(item.label)">{{ item.value }}</a-descriptions-item>
        </a-descriptions>
        <p v-if="resultFeedback" class="mold-dialog-section" role="status">{{ resultFeedback }}</p>
        <a-checkbox v-model:checked="savedAcknowledged" class="mold-dialog-section">{{ $t('message.ssh.privatekey.saved') }}</a-checkbox>
        <a-alert v-if="confirmDiscard" ref="discardWarning" tabindex="-1" class="mold-dialog-section" type="warning" show-icon :message="$t('message.ssh.privatekey.discard')" />
      </template>
      <a-alert v-if="error" class="mold-dialog-section" type="error" show-icon :message="error" />
    </div>
    <template #footer>
      <template v-if="!isSubmitted">
        <a-button :disabled="loading" @keydown.ctrl.enter.prevent="handleSubmit" @click="closeAction">{{ $t('label.cancel') }}</a-button>
        <a-button
ref="submit"
html-type="submit"
form="ssh-keypair-form"
@keydown.ctrl.enter.prevent="handleSubmit"
type="primary"
:loading="loading"
:disabled="!allowedApi"
@click="handleSubmit">{{ $t(mode === 'register' ? 'label.ssh.keypair.register' : 'label.create') }}</a-button>
      </template>
      <template v-else-if="confirmDiscard">
        <a-button @click="continueSaving">{{ $t('label.ssh.privatekey.continue') }}</a-button>
        <a-button type="primary" danger @click="finishClose">{{ $t('label.ssh.privatekey.discard') }}</a-button>
      </template>
      <template v-else>
        <a-button @click="closeAction">{{ $t('label.close') }}</a-button>
        <a-button ref="copy" v-clipboard:copy="privateKey" v-clipboard:success="notifyCopied" v-clipboard:error="copyFailed">{{ $t('label.copy.clipboard') }}</a-button>
        <a-button type="primary" @click="downloadKey">{{ $t('label.download') }}</a-button>
      </template>
    </template>
  </MoldDialog>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { getAPI, postAPI } from '@/api'
import { mixinForm } from '@/utils/mixin'
import TooltipLabel from '@/components/widgets/TooltipLabel'
import MoldDialog from '@/components/view/MoldDialog'

export default {
  name: 'CreateSSHKeyPair',
  mixins: [mixinForm],
  props: { currentAction: { type: Object, default: () => ({ api: 'createSSHKeyPair' }) } },
  components: { TooltipLabel, MoldDialog },
  emits: ['refresh-data', 'close-action'],
  data () {
    return { domains: [], domainLoading: false, selectedDomain: {}, loading: false, isSubmitted: false, privateKey: '', result: {}, resultScope: {}, savedAcknowledged: false, confirmDiscard: false, resultFeedback: '', error: '', disposed: false }
  },
  computed: {
    mode () { return this.currentAction.api === 'registerSSHKeyPair' ? 'register' : 'create' },
    apiName () { return this.mode === 'register' ? 'registerSSHKeyPair' : 'createSSHKeyPair' },
    allowedApi () { return this.apiName in this.$store.getters.apis },
    resultSummary () {
      return [
        { label: 'label.name', value: this.result.name },
        { label: 'label.fingerprint', value: this.result.fingerprint },
        { label: 'label.account', value: this.result.account || this.resultScope.account },
        { label: 'label.domain', value: this.result.domain || this.resultScope.domain },
        { label: 'label.project', value: this.result.project || this.resultScope.project }
      ].filter(item => item.value)
    }
  },
  beforeCreate () { this.apiParams = this.$getApiParams('createSSHKeyPair', 'registerSSHKeyPair') },
  created () {
    this.initForm()
    this.domains = [{ id: null, name: '' }]
    this.fetchData()
  },
  mounted () { window.addEventListener('beforeunload', this.beforeUnload) },
  beforeUnmount () {
    this.disposed = true
    window.removeEventListener('beforeunload', this.beforeUnload)
    this.privateKey = ''
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({})
      this.rules = reactive({ name: [{ required: true, message: this.$t('message.error.name') }], ...(this.mode === 'register' ? { publickey: [{ required: true, whitespace: true, message: this.$t('message.ssh.keypair.publickey.required') }] } : {}) })
    },
    fetchData () { if (this.isAdminOrDomainAdmin()) this.fetchDomainData() },
    isAdminOrDomainAdmin () { return ['Admin', 'DomainAdmin'].includes(this.$store.getters.userInfo.roletype) },
    isValidValueForKey (obj, key) { return key in obj && obj[key] != null },
    arrayHasItems (array) { return Array.isArray(array) && array.length > 0 },
    fetchDomainData () {
      this.domainLoading = true
      return getAPI('listDomains', {}, { preserveOnFailure: true }).then(json => {
        if (!this.disposed) this.domains = this.domains.concat(json.listdomainsresponse.domain || [])
      }).catch(error => { if (!this.disposed) this.error = error.message || this.$t('error.fetching.data') }).finally(() => {
        if (this.disposed) return
        this.domainLoading = false
        this.form.domainid = 0
        this.handleDomainChanged(this.domains[0])
      })
    },
    handleDomainChanged (domain) { this.selectedDomain = domain || {} },
    formPosition () { return { focus: document.activeElement, body: this.$refs.content?.closest('.ant-modal-body'), top: this.$refs.content?.closest('.ant-modal-body')?.scrollTop || 0 } },
    restoreFormPosition (position) {
      this.$nextTick(() => {
        if (this.disposed) return
        if (position.body) position.body.scrollTop = position.top
        if (position.focus?.isConnected) position.focus.focus({ preventScroll: true })
      })
    },
    handleSubmit (e) {
      if (e && typeof e.preventDefault === 'function') e.preventDefault()
      if (this.loading || this.isSubmitted || this.disposed) return
      if (!this.allowedApi) { this.error = this.$t('message.ssh.keypair.permission'); return }
      const position = this.formPosition()
      let invalidField = null
      this.loading = true
      this.error = ''
      return this.formRef.value.validate().then(() => {
        if (this.disposed || !this.allowedApi) return
        const values = this.handleRemoveFields(toRaw(this.form))
        const params = { name: values.name }
        if (this.isValidValueForKey(values, 'domainid') && this.domains[values.domainid]?.id) params.domainid = this.domains[values.domainid].id
        if (values.account) params.account = values.account
        if (this.mode === 'register') params.publickey = values.publickey
        const scope = { account: params.account || this.$store.getters.userInfo.account, domain: this.selectedDomain.path || this.$store.getters.userInfo.domain, project: this.$store.getters.project?.name }
        return postAPI(this.apiName, params, { preserveOnFailure: true }).then(json => {
          if (this.disposed) return
          if (this.mode === 'register') {
            this.$message.success(this.$t('message.success.register.keypair') + ' ' + values.name)
            this.$emit('refresh-data')
            this.finishClose()
          } else {
            const result = json.createsshkeypairresponse?.keypair
            this.$emit('refresh-data')
            if (!result?.privatekey) { this.error = this.$t('message.ssh.privatekey.missing'); return }
            this.privateKey = result.privatekey
            const { privatekey, ...summary } = result
            this.result = { ...summary, name: summary.name || values.name }
            this.resultScope = scope
            this.isSubmitted = true
            this.$message.success(this.$t('message.success.create.keypair') + ' ' + values.name)
          }
        }).catch(error => {
          if (!this.disposed) { this.error = error.response?.data?.errorresponse?.errortext || error.message || this.$t('error.fetching.data'); this.$notifyError(error) }
        })
      }).catch(error => {
        if (error.errorFields?.length) {
          invalidField = error.errorFields[0].name
        } else { this.error = error.message || this.$t('error.fetching.data'); this.$notifyError(error) }
      }).finally(() => {
        this.loading = false
        if (this.isSubmitted) this.$nextTick(() => this.$refs.copy?.$el?.focus())
        else if (invalidField) this.$nextTick(() => { this.formRef.value.scrollToField(invalidField); const input = this.$refs[invalidField[0] + 'Input']; if (input) input.focus() })
        else this.restoreFormPosition(position)
      })
    },
    downloadKey () {
      if (!this.privateKey) return
      let url
      try {
        url = URL.createObjectURL(new Blob([this.privateKey], { type: 'text/plain;charset=utf-8' }))
        const anchor = document.createElement('a')
        anchor.href = url
        anchor.download = this.result.name + '.key'
        anchor.click()
        this.resultFeedback = this.$t('message.ssh.privatekey.download.started')
      } catch (error) { this.error = this.$t('message.ssh.privatekey.download.failed') } finally { if (url) URL.revokeObjectURL(url) }
    },
    notifyCopied () { this.error = ''; this.resultFeedback = this.$t('message.success.copy.clipboard') },
    copyFailed () { this.error = this.$t('message.ssh.privatekey.copy.failed'); this.resultFeedback = '' },
    beforeUnload (event) {
      if (this.loading || (this.isSubmitted && !this.savedAcknowledged)) { event.preventDefault(); event.returnValue = '' }
    },
    closeAction () {
      if (this.loading) return
      if (this.isSubmitted && !this.savedAcknowledged) {
        this.confirmDiscard = true
        this.$nextTick(() => this.$refs.discardWarning?.$el?.focus())
      } else this.finishClose()
    },
    continueSaving () { this.confirmDiscard = false; this.$nextTick(() => this.$refs.copy?.$el?.focus()) },
    finishClose () { this.privateKey = ''; this.$emit('close-action') }
  }
}
</script>
<style scoped lang="less">
.ssh-keypair-dialog-content { min-width: 0; overflow-wrap: anywhere; }
</style>

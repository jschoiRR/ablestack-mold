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
  <div class="form-layout" v-ctrl-enter="handleSubmit">
    <a-spin :spinning="loading">
      <a-form
        :ref="formRef"
        :model="form"
        :rules="rules"
        layout="vertical"
        @finish="handleSubmit">
        <a-form-item
          :label="$t('label.iso.name') + ' (' + form.ids.length + ' / ' + attached.length + ')'"
          ref="ids"
          name="ids">
          <a-select
            mode="multiple"
            :loading="loading"
            v-model:value="form.ids"
            v-focus="true">
            <a-select-option
              v-for="iso in attached"
              :key="iso.id"
              :label="iso.displaytext || iso.name">
              {{ (iso.displaytext || iso.name) + ' (' + slotLabel(iso.deviceseq) + ')' }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item
          :label="$t('label.forced')"
          v-if="resource && resource.hypervisor === 'VMware'"
          ref="forced"
          name="forced">
          <a-switch v-model:checked="form.forced" v-focus="true" />
        </a-form-item>
      </a-form>
      <div :span="24" class="action-button">
        <a-button @click="closeAction">{{ $t('label.cancel') }}</a-button>
        <a-button :loading="loading" type="primary" @click="handleSubmit" ref="submit">{{ $t('label.ok') }}</a-button>
      </div>
    </a-spin>
  </div>
</template>
<script>
import { ref, reactive, toRaw } from 'vue'
import { postAPI } from '@/api'
import { detachIsoBatch } from '@/utils/detachIsoBatch'

export default {
  name: 'DetachIso',
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  data () {
    return {
      loading: false,
      attached: []
    }
  },
  created () {
    this.initForm()
    this.populateAttached()
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({ ids: [] })
      this.rules = reactive({
        ids: [{
          required: true,
          type: 'array',
          min: 1,
          message: `${this.$t('label.required')}`
        }]
      })
    },
    populateAttached () {
      if (this.resource.isos && this.resource.isos.length > 0) {
        this.attached = [...this.resource.isos].sort((a, b) => (a.deviceseq || 0) - (b.deviceseq || 0))
      } else if (this.resource.isoid) {
        this.attached = [{
          id: this.resource.isoid,
          name: this.resource.isoname,
          displaytext: this.resource.isodisplaytext,
          deviceseq: 3
        }]
      }
      if (this.attached.length === 1) {
        this.form.ids = [this.attached[0].id]
      }
    },
    slotLabel (deviceseq) {
      // 3 -> hdc, 4 -> hdd, ... matches LibvirtVMDef.getDevLabel for the IDE bus on KVM.
      if (typeof deviceseq !== 'number') return ''
      return 'hd' + String.fromCharCode('a'.charCodeAt(0) + deviceseq - 1)
    },
    closeAction () {
      this.$emit('close-action')
    },
    async handleSubmit (e) {
      if (e && typeof e.preventDefault === 'function') e.preventDefault()
      if (this.loading) return
      try {
        await this.formRef.value.validate()
      } catch (error) {
        if (error.errorFields?.length) this.formRef.value.scrollToField(error.errorFields[0].name)
        return
      }
      const values = toRaw(this.form)
      const ids = [...(values.ids || [])]
      if (!ids.length) return
      const scope = () => [this.$store.state.user.token, this.$store.getters.project?.id].join('|')
      const originalScope = scope()
      const title = this.$t('label.action.detach.iso')
      const key = `detach-iso-${this.resource.id}-${Date.now()}`
      const isoName = id => this.attached.find(iso => iso.id === id)?.name || id
      this.loading = true
      this.$message.loading({ key, content: `${title} 0/${ids.length}`, duration: 0 })
      try {
        const results = await detachIsoBatch({
          ids,
          isCurrent: () => scope() === originalScope,
          submit: id => {
            const params = { virtualmachineid: this.resource.id }
            if (this.attached.length > 1 || ids.length > 1) params.id = id
            if (values.forced) params.forced = values.forced
            return postAPI('detachIso', params).then(json => json.detachisoresponse)
          },
          poll: (jobId, id) => this.$pollJob({
            jobId,
            batchKey: key,
            title,
            description: isoName(id),
            resourceId: this.resource.id,
            showLoading: false,
            showSuccessMessage: false,
            action: { api: 'detachIso', isFetchData: false }
          }),
          onProgress: results => this.$message.loading({ key, content: `${title} ${results.length}/${ids.length}`, duration: 0 })
        })
        if (scope() !== originalScope) return
        const succeeded = results.filter(result => result.jobstatus === 1).map(result => result.id)
        const failed = results.filter(result => result.jobstatus === 2).length
        const unknown = results.filter(result => result.trackingStatus).length
        const pending = ids.length - results.length
        this.$notification.info({
          key,
          message: title,
          description: this.$t('message.iso.detach.summary', { success: succeeded.length, failed, unknown, pending }) + ' ' + results.map(result => `${isoName(result.id)}: ${this.$t(result.jobstatus === 1 ? 'label.success' : result.jobstatus === 2 ? 'label.failed' : 'label.job.check.result')}`).join('; '),
          duration: succeeded.length === ids.length ? 5 : 0
        })
        this.$emit('refresh-data')
        // Keep only explicit failures selected; never repeat an uncertain accepted operation.
        this.attached = this.attached.filter(iso => !succeeded.includes(iso.id))
        this.form.ids = results.filter(result => result.jobstatus === 2).map(result => result.id)
        if (succeeded.length === ids.length || unknown) this.closeAction()
      } finally {
        this.$message.destroy(key)
        this.loading = false
      }
    }
  }
}
</script>
<style lang="scss" scoped>
.form-layout {
  width: 80vw;
  @media (min-width: 700px) {
    width: 600px;
  }
}

.form {
  margin: 10px 0;
}
</style>

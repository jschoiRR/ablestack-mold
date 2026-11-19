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
  <MoldDialog class="cni-clone-dialog" size="lg" :title="$t('label.cni.clone')" :closable="!busy" :keyboard="!busy" @cancel="close">
    <a-alert v-if="!allowed" type="error" show-icon :message="$t('label.forbidden')" />
    <div v-else ref="content" class="cni-clone-content" :aria-busy="busy">
      <a-spin :spinning="busy">
        <a-descriptions v-if="source" size="small" :column="1" class="mold-dialog-summary">
          <a-descriptions-item
            :label="$t('label.cni.clone.source')"
            ><a :href="link(source)" @click="navigate($event, source)">{{ source.name }} ({{ source.id }})</a></a-descriptions-item
          >
          <a-descriptions-item
            :label="$t(source.projectid ? 'label.project' : 'label.account')"
            >{{ source.project || source.account }} / {{ source.domain }}</a-descriptions-item
          >
        </a-descriptions>
        <template v-if="target">
          <a-alert type="success" show-icon :message="$t('message.cni.clone.created')" />
          <p class="cni-clone-target">
            <a :href="link(target)" @click="navigate($event, target)">{{ target.name }} ({{ target.id }})</a>
          </p>
          <a-alert
            :type="saved ? 'success' : 'warning'"
            show-icon
            :message="$t(saved ? 'message.cni.clone.saved' : 'message.cni.clone.pending')"
          />
        </template>
        <a-form
          v-else-if="source"
          ref="formRef"
          :model="form"
          :rules="rules"
          layout="vertical"
          @finish="submit"
          @finishFailed="focusError"
          @keydown.ctrl.enter.stop.prevent="submit"
        >
          <a-form-item
            name="name"
            :label="$t('label.name')"
            validate-first
            ><a-input
            ref="nameInput"
            v-model:value="form.name"
            :disabled="locked"
            @keydown.enter.prevent="submit"
          /></a-form-item>
          <CniFileImport
            :disabled="loading || !!attempt || variablesReading"
            :max-length="maxLength"
            :context="context"
            @reading="fileReading = $event"
            @apply="applyFile"
          >
            <CniVariableTools
              mode="extract"
              :encoded="draft?.encoded || ''"
              :params="form.params"
              :scope="context"
              :disabled="loading || !!attempt || fileReading"
              @reading="variablesReading = $event"
              @apply="
                (keys) => {
                  form.params = keys
                }
              "
            />
          </CniFileImport>
          <a-form-item
            name="cniconfig"
            :label="$t('label.cniconfiguration')"
            validate-first
            ><a-textarea
            ref="cniconfigInput"
            v-model:value="form.cniconfig"
            :disabled="locked"
            :auto-size="{ minRows: 6, maxRows: 12 }"
          /></a-form-item>
          <a-form-item
            :label="$t('label.is.base64.encoded')"
            ><a-checkbox v-model:checked="form.isbase64" :disabled="locked" /><a-button
              class="cni-clone-edit"
              :disabled="locked || draft?.text == null || !form.isbase64"
              @click="editText"
              >{{ $t('label.userdata.clone.edit.text') }}</a-button
            ></a-form-item
          >
          <a-form-item
            name="params"
            :label="$t('label.cniconfigparams')"
            validate-first
            ><a-select
            ref="paramsInput"
            v-model:value="form.params"
            mode="tags"
            :disabled="locked"
          /></a-form-item>
          <a-descriptions
            v-if="difference"
            size="small"
            :column="{ xs: 1, sm: 2 }"
            ><a-descriptions-item :label="$t('label.cni.clone.bytes')">{{ difference.before }} → {{ difference.after }}</a-descriptions-item
            ><a-descriptions-item
            :label="$t('label.cniconfigparams')"
              >{{ source.params || '—' }} → {{ normalizedParams || '—' }}</a-descriptions-item
            ></a-descriptions
          >
          <details>
            <summary>{{ $t('label.cni.clone.source.body') }}</summary>
            <UserDataContent :resource="source" source-type="cni" tab="cni.content" :preview-encoded="source.userdata" />
          </details>
          <details v-if="draft">
            <summary>{{ $t('label.cni.clone.draft.body') }}</summary>
            <UserDataContent :resource="source" source-type="cni" tab="cni.content" :preview-encoded="draft.encoded" />
          </details>
        </a-form>
      </a-spin>
      <a-alert v-if="error" class="mold-dialog-section" type="error" show-icon role="alert" :message="error" />
      <a-button v-if="!source && !busy" @click="load">{{ $t('label.retry') }}</a-button>
    </div>
    <template
#footer
      ><a-button :disabled="busy" @click="close">{{ $t(target ? 'label.close' : 'label.cancel') }}</a-button
      ><a-button
        v-if="!saved"
        type="primary"
        :loading="busy"
        :disabled="!allowed || !source || fileReading || variablesReading"
        @click="submit"
        >{{ $t(target ? 'label.cni.clone.retry' : attempt ? 'label.cni.clone.reconcile' : 'label.cni.clone.create') }}</a-button
      ></template
    >
  </MoldDialog>
</template>
<script>
import { getAPI, postAPI } from '@/api'
import MoldDialog from '@/components/view/MoldDialog'
import UserDataContent from './UserDataContent'
import CniFileImport from './CniFileImport'
import CniVariableTools from './CniVariableTools'
import { canonicalCni, cniName, cniParams } from '@/utils/cniRegistration'
import { userDataContent } from '@/utils/userDataContent'
import {
  cniCloneApis,
  cloneOwner,
  cloneDifference,
  readCniCloneSource,
  findCniCloneAttempt,
  cniCloneRecord,
  saveCniCloneRecord
} from '@/utils/cniVersions'
export default {
  components: { MoldDialog, UserDataContent, CniFileImport, CniVariableTools },
  props: { resource: { type: Object, required: true } },
  emits: ['refresh-data', 'close-action'],
  data () {
    return {
      source: null,
      target: null,
      saved: false,
      record: null,
      attempt: null,
      error: '',
      loading: false,
      fileReading: false,
      variablesReading: false,
      generation: 0,
      disposed: false,
      maxLength: 1048576,
      form: { name: '', cniconfig: '', isbase64: true, params: [] }
    }
  },
  computed: {
    allowed () {
      return cniCloneApis.every((api) => !!this.$store.getters.apis?.[api])
    },
    context () {
      return JSON.stringify([
        this.resource.id,
        this.resource.name,
        this.resource.domainid,
        this.resource.account,
        this.resource.projectid,
        this.$route.path,
        this.$store.getters.project?.id,
        this.$store.getters.listAllProjects,
        this.$store.getters.userInfo?.id,
        this.$store.state?.user?.token
      ])
    },
    busy () {
      return this.loading || this.fileReading || this.variablesReading
    },
    locked () {
      return this.busy || !!this.attempt
    },
    blocksNavigation () {
      return this.busy
    },
    draft () {
      try {
        return userDataContent(canonicalCni(this.form.cniconfig, this.form.isbase64, this.maxLength))
      } catch (_) {
        return null
      }
    },
    normalizedParams () {
      try {
        return cniParams(this.form.params)
      } catch (_) {
        return null
      }
    },
    difference () {
      return this.source && this.draft ? cloneDifference(this.source, this.draft.encoded, this.normalizedParams) : null
    },
    rules () {
      const rule = (fn, required = false) => [
        {
          required,
          validator: async () => {
            try {
              fn()
            } catch (e) {
              throw new Error(this.$t(e.message))
            }
          }
        }
      ]
      return {
        name: rule(() => cniName(this.form.name), true),
        cniconfig: rule(() => canonicalCni(this.form.cniconfig, this.form.isbase64, this.maxLength), true),
        params: rule(() => cniParams(this.form.params))
      }
    }
  },
  watch: {
    context () {
      this.generation++
      this.source = null
      this.target = null
      this.attempt = null
      this.record = null
      this.saved = false
      this.loading = false
      this.form = { name: '', cniconfig: '', isbase64: true, params: [] }
      this.load()
    },
    allowed (value) {
      this.generation++
      this.loading = false
      this.error = value ? '' : this.$t('label.forbidden')
      if (value && !this.source) this.load()
    }
  },
  created () {
    this.load()
    getAPI('listCapabilities', {}, { preserveOnFailure: true })
      .then((json) => {
        const n = json?.listcapabilitiesresponse?.capability?.userdatamaxlength
        if (!this.disposed && Number.isInteger(n) && n > 0) this.maxLength = Math.min(n, 1048576)
      })
      .catch(() => {})
  },
  mounted () {
    window.addEventListener('beforeunload', this.beforeUnload)
  },
  beforeUnmount () {
    this.disposed = true
    this.generation++
    window.removeEventListener('beforeunload', this.beforeUnload)
  },
  methods: {
    message (e) {
      if (e?.message?.startsWith('message.')) return this.$t(e.message)
      return (
        Object.values(e?.response?.data || {}).find((v) => v?.errortext)?.errortext || e.message || this.$t('message.cni.clone.history')
      )
    },
    async load () {
      if (this.loading || !this.allowed || this.disposed) return
      const generation = ++this.generation
      const context = this.context
      const current = () => !this.disposed && generation === this.generation && context === this.context && this.allowed
      this.loading = true
      this.error = ''
      try {
        const source = await readCniCloneSource(getAPI, Object.freeze({ ...this.resource }))
        if (!current()) return
        this.source = source
        this.form = {
          name: source.name.slice(0, 247).replace(/[\uD800-\uDBFF]$/, '') + '-v2',
          cniconfig: source.userdata,
          isbase64: true,
          params: source.params ? source.params.split(',') : []
        }
        this.$nextTick(() => this.$refs.nameInput?.focus())
      } catch (e) {
        if (current()) this.error = this.message(e)
      } finally {
        if (current()) this.loading = false
      }
    },
    applyFile (encoded) {
      if (this.locked) return
      this.form.cniconfig = encoded
      this.form.isbase64 = true
    },
    editText () {
      if (this.locked || this.draft?.text == null) return
      this.form.cniconfig = this.draft.text
      this.form.isbase64 = false
      this.$nextTick(() => this.$refs.cniconfigInput?.focus())
    },
    link (row) {
      return this.$router.resolve('/cniconfiguration/' + row.id).href
    },
    navigate (event, row) {
      if (this.busy) {
        event.preventDefault()
        return
      }
      if (event.button > 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return
      event.preventDefault()
      this.$emit('close-action')
      this.$router.push('/cniconfiguration/' + row.id)
    },
    focusError (e) {
      const field = e?.errorFields?.[0]?.name?.[0]
      if (this.$refs.formRef) this.$refs.formRef.scrollToField(field)
      this.$nextTick(() => this.$refs[field + 'Input']?.focus())
    },
    async submit () {
      if (this.busy || !this.allowed || !this.source) return
      const generation = this.generation
      const context = this.context
      const current = () => !this.disposed && generation === this.generation && context === this.context && this.allowed
      const active = document.activeElement
      const scroll = this.$refs.content?.closest('.ant-modal-body')
      const top = scroll?.scrollTop
      let validationFailure = null
      this.loading = true
      this.error = ''
      try {
        if (!this.target) {
          await this.$refs.formRef.validate()
          if (!current()) return
          const source = await readCniCloneSource(getAPI, this.source)
          if (!current()) return
          if (source.userdata !== this.source.userdata || (source.params || '') !== (this.source.params || '')) { throw new Error('message.cni.clone.changed') }
          let target
          if (this.attempt) {
            target = await findCniCloneAttempt(getAPI, source, this.attempt)
            if (!current()) return
            if (!target) {
              this.attempt = null
              throw new Error('message.cni.clone.notfound')
            }
          } else {
            const params = {
              ...cloneOwner(source),
              name: cniName(this.form.name),
              cniconfig: canonicalCni(this.form.cniconfig, this.form.isbase64, this.maxLength),
              params: cniParams(this.form.params)
            }
            if (await findCniCloneAttempt(getAPI, source, params)) throw new Error('message.cni.clone.conflict')
            if (!current()) return
            this.attempt = Object.freeze({ ...params })
            try {
              const json = await postAPI('registerCniConfiguration', params, { preserveOnFailure: true, timeout: 60000 })
              if (!current()) return
              const row = json?.registercniconfigurationresponse?.cniconfig
              if (!row?.id) throw new Error('message.cni.clone.uncertain')
              target = await findCniCloneAttempt(getAPI, source, params)
              if (!current()) return
              if (!target || target.id !== row.id) throw new Error('message.cni.clone.uncertain')
            } catch (e) {
              if (current()) throw new Error('message.cni.clone.uncertain')
              return
            }
          }
          if (!current()) return
          this.target = target
          this.record = cniCloneRecord(source, target)
          this.$emit('refresh-data')
        }
        if (!current() || this.saved) return
        await saveCniCloneRecord(getAPI, postAPI, this.target, this.record, current)
        if (current()) this.saved = true
      } catch (e) {
        if (!current()) return
        if (e.message === 'message.cni.clone.conflict') this.attempt = null
        if (e.errorFields?.length) {
          validationFailure = e
        } else this.error = this.message(e)
      } finally {
        if (current()) {
          this.loading = false
          this.$nextTick(() => {
            if (!validationFailure && active?.isConnected) active.focus({ preventScroll: true })
            if (!validationFailure && scroll) scroll.scrollTop = top
            if (validationFailure) this.focusError(validationFailure)
          })
        }
      }
    },
    beforeUnload (e) {
      if (this.busy) {
        e.preventDefault()
        e.returnValue = ''
      }
    },
    close () {
      if (!this.busy) this.$emit('close-action')
    }
  }
}
</script>
<style>
.cni-clone-content,
.cni-clone-dialog .ant-descriptions-item-content {
  min-width: 0;
  overflow-wrap: anywhere;
}
.cni-clone-dialog .cni-clone-edit {
  margin-left: 12px;
}
.cni-clone-dialog .content-code {
  max-height: 30vh;
}
.cni-clone-dialog details {
  margin: 12px 0;
}
.cni-clone-dialog summary {
  cursor: pointer;
}
.cni-clone-target {
  margin: 12px 0;
  overflow-wrap: anywhere;
}
</style>

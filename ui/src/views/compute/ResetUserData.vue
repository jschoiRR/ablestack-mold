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
    class="userdata-reset-dialog"
    size="md"
    :title="$t(isAutoScaleProfile ? 'label.reset.user.data.on.autoscale.vm.group' : 'label.reset.user.data.on.vm')"
    :closable="!loadingData"
    :keyboard="!loadingData"
    @cancel="closeAction">
  <a-alert v-if="templateError" type="error" show-icon role="alert" :message="$t('message.request.failed')">
    <template #description><a-button size="small" @click="fetchTemplateData">{{ $t('label.refresh') }}</a-button></template>
  </a-alert>
  <a-alert v-if="submitError" type="error" show-icon role="alert" :message="submitError" />
  <a-spin :spinning="loadingData || templateLoading">
    <template v-if="currentLoaded">
      <a-alert v-if="supportMessage" :type="resetUnsupported ? 'warning' : 'info'" show-icon role="status" :message="$t(supportMessage)" />
      <p class="userdata-current-summary">{{ $t('label.userdata.current') }}: {{ current.userdataname || $t(currentKind) }}</p>
      <a-descriptions v-if="Object.keys(currentValues).length" size="small" :column="1">
        <a-descriptions-item v-for="(value, key) in currentValues" :key="key" :label="key">{{ value }}</a-descriptions-item>
      </a-descriptions>
      <a-collapse v-if="current.userdata" class="userdata-current-content">
        <a-collapse-panel key="current" :header="$t('label.userdata.current.content')">
          <a-textarea :value="currentText" readonly :auto-size="{ minRows: 3, maxRows: 8 }" :aria-label="$t('label.userdata.current.content')" />
        </a-collapse-panel>
      </a-collapse>
      <a-form-item :label="$t('label.userdata.reset.change')">
        <a-switch ref="changeSwitch" v-model:checked="editCurrent" :disabled="loadingData || resetUnsupported" :aria-label="$t('label.userdata.reset.change')" />
      </a-form-item>
    </template>
    <a-form
v-if="currentLoaded && editCurrent"
            :ref="formRef"
            :model="form"
            :rules="rules"
            layout="vertical"
            class="form"
            :class="{ 'autoscale-userdata-form': isAutoScaleProfile }"
            v-ctrl-enter="handleSubmit"
            @finish="handleSubmit">
      <div v-if="template && template.userdataid">
        <a-typography-text>
            <template v-if="isAutoScaleProfile">{{ $t('message.autoscale.userdata.template', { template: template.name, userdata: template.userdataname, policy: template.userdatapolicy }) }}</template>
            <template v-else>{{ $t('message.autoscale.userdata.template', { template: template.name, userdata: template.userdataname, policy: template.userdatapolicy }) }}</template>
        </a-typography-text><br/><br/>
        <div v-if="templateUserDataParams.length > 0 && templateDataActive">
          <a-typography-text v-if="this.template && this.template.userdataid && templateUserDataParams.length > 0">
              <template v-if="isAutoScaleProfile">{{ $t('message.autoscale.userdata.variables') }}</template>
              <template v-else>{{ $t('message.autoscale.userdata.variables') }}</template>
          </a-typography-text>
          <a-input-group>
            <a-table
              size="small"
              style="overflow-y: auto"
              :columns="userDataParamCols"
              :dataSource="templateUserDataParams"
              :pagination="false"
              :rowKey="record => record.key">
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'value'">
                  <a-input v-model:value="templateUserDataValues[record.key]" />
                </template>
              </template>
            </a-table>
          </a-input-group>
        </div><br/><br/>
      </div>
      <div v-if="userdataDefaultOverridePolicy === 'ALLOWOVERRIDE' || userdataDefaultOverridePolicy === 'APPEND' || !userdataDefaultOverridePolicy">
        <span v-if="userdataDefaultOverridePolicy === 'ALLOWOVERRIDE'" >
          {{ $t('label.user.data.do.override') }}
          <a-switch v-model:checked="doUserdataOverride" style="margin-left: 10px"/>
        </span>
        <span v-if="userdataDefaultOverridePolicy === 'APPEND'">
          {{ $t('label.user.data.do.append') }}
          <a-switch v-model:checked="doUserdataAppend" style="margin-left: 10px"/>
        </span>
        <a-step>
          <template #description>
            <div v-if="doUserdataOverride || doUserdataAppend || !userdataDefaultOverridePolicy" style="margin-top: 15px">
              <a-card
                :tabList="userdataTabList"
                :activeTabKey="userdataTabKey"
                @tabChange="key => onUserdataTabChange(key, 'userdataTabKey')">
                <div v-if="userdataTabKey === 'userdataregistered'">
                  <a-step>
                    <template #description>
                      <div>
                        <a-alert type="info" show-icon :message="$t(template.userdataid ? 'message.userdata.reset.none.template' : 'message.userdata.reset.none.clear')" />
                        <user-data-selection
                          :scope="userDataOwner"
                          :value="form.userdataid"
                          :disabled="template.userdatapolicy === 'DENYOVERRIDE'"
                          :preFillContent="dataPreFill"
                          @select-user-data-item="updateUserData"
                          @ready="userDataSelectionReady = $event"
                        />
                        <div v-if="userDataParams.length > 0">
                          <a-input-group>
                            <a-table
                              size="small"
                              style="overflow-y: auto"
                              :columns="userDataParamCols"
                              :dataSource="userDataParams"
                              :pagination="false"
                              :rowKey="record => record.key">
                              <template #bodyCell="{ column, record }">
                                <template v-if="column.key === 'value'">
                                  <a-input v-model:value="userDataValues[record.key]" />
                                </template>
                              </template>
                            </a-table>
                          </a-input-group>
                        </div>
                      </div>
                    </template>
                  </a-step>
                </div>
                <div v-else>
                  <a-alert v-if="appendManualCurrent" type="info" show-icon :message="$t('message.userdata.reset.append.current')" />
                  <a-form-item name="userdata" ref="userdata" >
                    <a-textarea
                      :placeholder="$t('label.user.data')"
                      :aria-label="$t('label.user.data.text')"
                      v-model:value="form.userdata">
                    </a-textarea>
                  </a-form-item>
                </div>
              </a-card>
            </div>
          </template>
        </a-step>
      </div>
    </a-form>
  </a-spin>
    <template #footer>
      <a-button ref="cancel" :disabled="loadingData" @click="closeAction">{{ $t('label.cancel') }}</a-button>
      <a-button :loading="loadingData" :disabled="templateLoading || templateError || !currentLoaded || (editCurrent && (resetUnsupported || userDataSelectionBlocked))" ref="submit" type="primary" @click="handleSubmit">{{ $t('label.ok') }}</a-button>
    </template>
  </MoldDialog>
</template>

<script>
import { ref, reactive } from 'vue'
import { getAPI, postAPI } from '@/api'
import { runAutoScaleMutation, autoScaleMutationDescription, autoScaleOwnerParams } from '@/utils/autoScaleEditing'
import { autoScaleTemplateById } from '@/utils/autoScaleUi'
import UserDataSelection from '@views/compute/wizard/UserDataSelection'
import MoldDialog from '@/components/view/MoldDialog'
import { applyUserDataSelection, userDataSelectionConsumer } from '@/utils/userDataSelection'
import { decodeResetUserData, resetTemplateActive, resetUserDataPayload, resetUserDataValues, userDataKeys, userDataResetSupport } from '@/utils/userDataReset'

export default {
  name: 'ResetUserData',
  mixins: [userDataSelectionConsumer],
  components: {
    MoldDialog,
    UserDataSelection
  },
  props: {
    resource: {
      type: Object,
      required: true
    },
    preFillContent: {
      type: Object,
      default: () => ({})
    }
  },
  data () {
    return {
      loadingData: false,
      templateLoading: false,
      templateError: false,
      currentLoaded: false,
      current: {},
      currentText: '',
      currentKind: 'label.noselect',
      currentValues: {},
      editCurrent: false,
      appendManualCurrent: false,
      restoringSelectionId: null,
      submitError: '',
      supportMessage: '',
      resetUnsupported: false,
      readGeneration: 0,
      readActive: true,
      doUserdataOverride: false,
      doUserdataAppend: false,
      userdataTabList: [],
      userdataDefaultOverridePolicy: 'ALLOWOVERRIDE',
      userData: {},
      userDataParams: [],
      userDataParamCols: [
        {
          title: this.$t('label.key'),
          dataIndex: 'key'
        },
        {
          title: this.$t('label.value'),
          dataIndex: 'value',
          key: 'value'
        }
      ],
      userDataValues: {},
      templateUserDataCols: [
        {
          title: this.$t('label.user.data'),
          dataIndex: 'userdata'
        },
        {
          title: this.$t('label.user.data.policy'),
          dataIndex: 'userdataoverridepolicy'
        }
      ],
      templateUserDataParams: [],
      templateUserDataValues: {},
      template: {},
      userdataTabKey: 'userdataregistered',
      dataPreFill: {},
      userdata: {}
    }
  },
  computed: {
    templateDataActive () { return resetTemplateActive(this) },
    blocksNavigation () { return this.loadingData },
    isUserAllowedToListUserDatas () { return Boolean(this.$store.getters.apis.listUserData) },
    isAutoScaleProfile () { return this.resource.resetUserDataApiName === 'updateAutoScaleVmProfile' }
  },
  created () {
    this.initForm()
    this.loadUserdataTabList()
    this.fetchTemplateData()
  },
  beforeUnmount () { this.readActive = false; this.readGeneration++ },
  watch: {
    templateLoading (loading) {
      if (loading) return
      this.$nextTick(() => {
        const change = this.$refs.changeSwitch?.$el
        const target = change && !change.disabled ? change : this.$refs.cancel?.$el
        if (target?.focus) target.focus()
      })
    },
    'resource.id' () { this.fetchTemplateData() },
    'resource.resetUserDataResourceId' () { this.fetchTemplateData() }
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({})
      this.rules = reactive({})
    },
    async fetchTemplateData () {
      const generation = ++this.readGeneration
      const current = () => this.readActive && generation === this.readGeneration
      this.templateLoading = true
      this.templateError = false
      this.currentLoaded = false
      this.editCurrent = false
      this.supportMessage = ''
      this.resetUnsupported = false
      try {
        const request = (command, args) => getAPI(command, args, { preserveOnFailure: true, timeout: 15000 })
        const owner = { ...autoScaleOwnerParams(this.resource), ignoreproject: true }
        const id = this.resource.resetUserDataResourceId || this.resource.id
        const command = this.isAutoScaleProfile ? 'listAutoScaleVmProfiles' : 'listVirtualMachines'
        const result = await request(command, { ...owner, id, listall: true, ...(this.isAutoScaleProfile ? {} : { userdata: true }) })
        if (!current()) return
        const row = this.isAutoScaleProfile ? result.listautoscalevmprofilesresponse?.autoscalevmprofile?.find(r => r.id === id) : result.listvirtualmachinesresponse?.virtualmachine?.find(r => r.id === id)
        if (!row) throw new Error('Current user data unavailable')
        if (!this.isAutoScaleProfile) {
          const networks = await Promise.all([...new Set((row.nic || []).map(n => n.networkid))].map(async id => {
            const response = await request('listNetworks', { ...owner, id, listall: true })
            const network = response.listnetworksresponse?.network?.find(n => n.id === id)
            if (!network) throw new Error('Network unavailable')
            return network
          }))
          if (!current()) return
          this.supportMessage = userDataResetSupport(row, networks)
          this.resetUnsupported = this.supportMessage.startsWith('message.userdata.reset.unsupported.')
        }
        const templateId = row.templateid || this.resource.templateid
        const json = await autoScaleTemplateById(request, { ...owner, id: templateId, zoneid: row.zoneid || this.resource.zoneid, isready: true })
        if (!current()) return
        const template = json.listtemplatesresponse?.template?.find(item => item.id === templateId)
        if (!template) throw new Error('Template unavailable')
        const find = async id => {
          if (!id || id === '0') return null
          const response = await request('listUserData', { ...owner, id })
          const data = response.listuserdataresponse?.userdata?.find(r => r.id === id)
          if (!data) throw new Error('Linked user data unavailable')
          return data
        }
        // Template metadata carries the declared variables even when its
        // library belongs to another account. Do not require cross-owner reads.
        let templateData = template.userdataid ? { id: template.userdataid, params: template.userdataparams || '' } : null
        if (template.userdataid && template.account === this.resource.account && template.domainid === this.resource.domainid) templateData = await find(template.userdataid)
        const selected = row.userdataid === template.userdataid ? templateData : await find(row.userdataid)
        if (!current()) return
        const keys = [...new Set([...userDataKeys(templateData), ...userDataKeys(selected)])]
        const values = resetUserDataValues(row.userdatadetails, keys)
        this.currentText = decodeResetUserData(row.userdata)
        this.current = row
        this.currentValues = values
        this.template = template
        this.userdataDefaultOverridePolicy = template.userdatapolicy
        this.templateUserDataParams = userDataKeys(templateData).map(key => ({ key }))
        this.templateUserDataValues = Object.fromEntries(userDataKeys(templateData).map(key => [key, values[key] ?? '']))
        this.userDataParams = userDataKeys(selected).map(key => ({ key }))
        this.userDataValues = Object.fromEntries(userDataKeys(selected).map(key => [key, values[key] ?? '']))
        this.form.userdataid = row.userdataid || undefined
        this.restoringSelectionId = row.userdataid || null
        const hasCustom = Boolean(row.userdataid || (row.userdata && row.userdata !== templateData?.userdata))
        this.currentKind = template.userdataid && (!hasCustom || template.userdatapolicy === 'DENYOVERRIDE')
          ? 'label.userdata.template.default'
          : (template.userdataid && !templateData?.userdata && !row.userdataid ? 'label.userdata.current.content' : (row.userdata ? 'label.user.data.text' : 'label.noselect'))
        this.doUserdataOverride = template.userdatapolicy === 'ALLOWOVERRIDE' && hasCustom
        this.doUserdataAppend = template.userdatapolicy === 'APPEND' && hasCustom
        this.appendManualCurrent = Boolean(template.userdatapolicy === 'APPEND' && hasCustom && !row.userdataid)
        // APPEND stores the merged effective body; it is not an append input.
        // Keep it visible above, and require new append content explicitly.
        this.form.userdata = this.appendManualCurrent ? '' : (!row.userdataid ? this.currentText : '')
        this.userdataTabKey = row.userdataid || !hasCustom ? 'userdataregistered' : 'userdatatext'
        this.userDataSelectionReady = false
        this.submitError = ''
        this.currentLoaded = true
      } catch (error) {
        if (current()) this.templateError = true
      } finally {
        if (current()) this.templateLoading = false
      }
    },
    loadUserdataTabList () {
      this.userdataTabList = [{
        key: 'userdataregistered',
        tab: this.$t(this.isAutoScaleProfile ? 'label.autoscale.userdata.registered' : 'label.user.data.registered')
      },
      {
        key: 'userdatatext',
        tab: this.$t(this.isAutoScaleProfile ? 'label.autoscale.userdata.manual' : 'label.user.data.text')
      }]
    },
    onUserdataTabChange (key, type) {
      this[type] = key
    },
    updateUserData (id, item) {
      const restored = id && id === this.form.userdataid ? { ...this.userDataValues } : null
      applyUserDataSelection(this, id, item)
      if (restored) this.userDataValues = Object.fromEntries(this.userDataParams.map(p => [p.key, restored[p.key] ?? '']))
      this.restoringSelectionId = null
    },
    handleSubmit () {
      if (this.loadingData || this.templateLoading || this.templateError || !this.currentLoaded || (this.editCurrent && (this.resetUnsupported || this.userDataSelectionBlocked))) return
      const params = resetUserDataPayload(this, this.$toBase64AndURIEncoded)
      if (!params) { this.closeAction(); return }
      this.loadingData = true
      this.submitError = ''
      const resetUserDataApiName = this.resource.resetUserDataApiName ? this.resource.resetUserDataApiName : 'resetUserDataForVirtualMachine'

      if (resetUserDataApiName === 'updateAutoScaleVmProfile' && !params.userdataid && !params.userdata) params.userdata = ''
      const update = resetUserDataApiName === 'updateAutoScaleVmProfile'
        ? runAutoScaleMutation(postAPI, getAPI, resetUserDataApiName, params)
        : postAPI(resetUserDataApiName, params, { preserveOnFailure: true })
      return update.then(json => {
        this.$message.success({
          content: `${this.$t(this.isAutoScaleProfile ? 'label.reset.user.data.on.autoscale.vm.group' : 'label.reset.user.data.on.vm')} - ${this.$t('label.success')}`,
          duration: 2
        })
        this.$emit('refresh-data')
        this.loadingData = false
        this.closeAction()
      }).catch(error => {
        const commandError = error?.response?.data?.[resetUserDataApiName.toLowerCase() + 'response']?.errortext
        this.submitError = resetUserDataApiName === 'updateAutoScaleVmProfile'
          ? autoScaleMutationDescription(error, this.$t)
          : (commandError || error?.response?.data?.errorresponse?.errortext || error?.message || this.$t('message.request.failed'))
        if (resetUserDataApiName === 'resetUserDataForVirtualMachine' && !error?.response && /Network Error|timeout|ECONNABORTED|ERR_NETWORK/.test(`${error?.message} ${error?.code}`)) {
          this.submitError = this.$t('message.userdata.reset.outcome.unknown')
        }
      }).finally(() => {
        this.loadingData = false
      })
    },
    closeAction () {
      if (this.loadingData) return
      this.$emit('close-action')
    }
  }
}
</script>

<style scoped lang="scss">
.userdata-current-summary, .userdata-current-content, .ant-alert { margin-bottom: 12px; }
.form {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  :deep(.ant-steps-item-description), :deep(.ant-steps-item-content) { width: 100%; min-width: 0; max-width: 100%; }
  :deep(.ant-card), :deep(.ant-card-body), :deep(.ant-table-wrapper) { min-width: 0; max-width: 100%; }
  :deep(.ant-input-search) { width: min(320px, 100%) !important; }
  :deep(.ant-table-cell) { overflow-wrap: anywhere; }
  :deep(.ant-table) { table-layout: fixed; }
}
</style>

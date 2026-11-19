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
  <div class="autoscale-tab">
    <a-space wrap class="autoscale-toolbar">
      <a-button type="primary" @click="editProfileModalVisible = true" :disabled="saving || loading || !('updateAutoScaleVmProfile' in $store.getters.apis) || resource.state !== 'DISABLED' || resource.operationinprogress"><template #icon><edit-outlined /></template>{{ $t('label.autoscale.profile.edit') }}</a-button>
      <a-button @click="showUpdateUserDataForm = true" :disabled="saving || loading || !('updateAutoScaleVmProfile' in $store.getters.apis) || resource.state !== 'DISABLED' || resource.operationinprogress"><template #icon><solution-outlined /></template>{{ $t('label.autoscale.userdata.edit') }}</a-button>
      <a-button @click="openParamModal" :disabled="saving || loading || !('updateAutoScaleVmProfile' in $store.getters.apis) || resource.state !== 'DISABLED' || resource.operationinprogress"><template #icon><plus-outlined /></template>{{ $t('label.add.param') }}</a-button>
      <a-button @click="fetchData" :disabled="saving" :loading="loading || profileRefreshing"><template #icon><reload-outlined /></template>{{ $t('label.refresh') }}</a-button>
    </a-space>
    <div>
      <a-alert type="info" v-if="(resource.state !== 'DISABLED' || resource.operationinprogress)">
        <template #message>
        <div
          v-html="$t(resource.operationinprogress ? 'message.autoscale.operation.wait' : 'message.autoscale.vmprofile.update')" />
        </template>
      </a-alert>

      <a-alert type="info" show-icon :message="$t('message.autoscale.profile.future')" />
      <a-alert v-if="affectedGroups.length > 1" type="warning" show-icon :message="$t('message.autoscale.edit.shared', { groups: affectedGroups.join(', ') })" />
      <a-alert v-if="mutationError" type="error" show-icon :message="mutationError" />
      <a-divider/>
      <div class="form">
        <div class="form__item" v-if="resource.lbprovider === 'Netscaler'">
          <div class="form__label">{{ $t('label.user') }}</div>
          <a-select
            :disabled="true"
            style="width: 100%"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }"
            v-focus="true"
            v-model:value="autoscaleuserid">
            <a-select-option
              v-for="(user, index) in usersList"
              :value="user.id"
              :key="index"
              :label="user.username">
              {{ user.username }}
            </a-select-option>
          </a-select>
        </div>
      </div>
      <div class="form">
        <div class="form__item">
          <div class="form__label">
            <tooltip-label :title="$t('label.expungevmgraceperiod')" :tooltip="$t('message.autoscale.lifecycle.grace')"/>
          </div>
          {{ expungevmgraceperiod }}
        </div>
      </div>
      <div class="form">
        <div class="form__item">
          <div class="form__label">
            <tooltip-label :title="$t('label.templatename')" :tooltip="(createAutoScaleVmProfileApiParams.templateid || {}).description"/>
          </div>
          {{ getTemplateName(templateid) }}
        </div>
      </div>
      <div class="form">
        <div class="form__item">
          <div class="form__label">
            <tooltip-label :title="$t('label.serviceofferingid')" :tooltip="(createAutoScaleVmProfileApiParams.serviceofferingid || {}).description"/>
          </div>
          {{ getServiceOfferingName(serviceofferingid) }}
        </div>
      </div>
      <div class="form" v-if="userdataid">
        <div class="form__item">
          <div class="form__label">
            <tooltip-label :title="$t('label.user.data.id')"/>
          </div>
          {{ userdataid }}
        </div>
      </div>
      <div class="form" v-if="userdataname">
        <div class="form__item">
          <div class="form__label">
            <tooltip-label :title="$t('label.user.data.name')"/>
          </div>
          {{ userdataname }}
        </div>
      </div>
      <div class="form" v-if="userdatadetails">
        <div class="form__item">
          <div class="form__label">
            <tooltip-label :title="$t('label.user.data.details')"/>
          </div>
          {{ userdatadetails }}
        </div>
      </div>
      <div class="form" v-if="userdatapolicy">
        <div class="form__item">
          <div class="form__label">
            <tooltip-label :title="$t('label.user.data.policy')"/>
          </div>
          {{ userdatapolicy }}
        </div>
      </div>
      <div class="form">
        <div class="form__item">
          <div class="form__label">
            <tooltip-label :title="$t('label.user.data')" :tooltip="(createAutoScaleVmProfileApiParams.userdata || {}).description"/>
          </div>
          <a-textarea v-model:value="userdata" rows="5" :disabled="true">
          </a-textarea>
        </div>
      </div>

      <a-divider/>
      <div class="title">
        <div class="form__label">
          <tooltip-label :title="$t('label.params')" :tooltip="(createAutoScaleVmProfileApiParams.otherdeployparams || {}).description"/>
        </div>
      </div>

    </div>

    <a-divider/>
    <a-table
      size="small"
      style="overflow-y: auto"
      :loading="loading"
      :columns="columns"
      :dataSource="allParams"
      :pagination="false"
      :rowKey="record => record.name"
      :scroll="{ x: 640 }">
      <template #bodyCell="{ column, record }"><template v-if="column.key === 'actions'"><div class="autoscale-row-actions">
        <a-popconfirm overlay-class-name="autoscale-confirm" :title="$t('label.delete') + '?'" @confirm="confirmParamRemoval(record)" @cancel="pendingParamRemoval = null" :ok-button-props="{ disabled: loading || saving }" :ok-text="$t('label.yes')" :cancel-text="$t('label.no')"><a-button size="small" :disabled="saving || loading || resource.state !== 'DISABLED' || resource.operationinprogress || !('updateAutoScaleVmProfile' in $store.getters.apis)" @click="prepareParamRemoval(record)"><template #icon><delete-outlined /></template>{{ $t('label.autoscale.param.remove') }}</a-button></a-popconfirm>
      </div></template></template>
    </a-table>

    <a-modal
class="mold-dialog autoscale-dialog"
centered
:width="760"
:title="$t('label.add.param')"
:visible="addParamModalVisible"
:mask-closable="false"
:closable="!saving"
:destroy-on-close="true"
@cancel="closeModal">
      <a-alert v-if="mutationError" type="error" show-icon :message="mutationError" />
            <div class="form" v-ctrl-enter="addParam">
        <div class="form__item" ref="newParamName">
          <div class="form__label"><span class="form__required">*</span>{{ $t('label.param.name') }}</div>
          <a-select
            style="width: 100%"
            showSearch
            optionFilterProp="value"
            :filterOption="(input, option) => {
              return option.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }"
            v-focus="true"
            v-model:value="newParam.name">
            <a-select-option v-for="(param, index) in paramNameList" :value="param" :key="index">
              {{ param }}
            </a-select-option>
          </a-select>
          <span class="error-text">{{ $t('label.required') }}</span>
        </div>
        <div class="form__item" ref="newParamValue">
          <div class="form__label"><span class="form__required">*</span>{{ $t('label.param.value') }}</div>
          <a-input v-model:value="newParam.value"></a-input>
          <span class="error-text">{{ $t('label.required') }}</span>
        </div>
        <div class="form__item" v-if="newParam.name === 'diskofferingid'">
          <div class="form__label">{{ $t('label.disksize') }} (GiB)</div>
          <a-input v-model:value="newParam.disksize" type="number" :placeholder="$t('message.autoscale.profile.data.size')" />
        </div>
      </div>
      <template #footer><div class="action-button"><a-button :disabled="saving" @click="closeModal">{{ $t('label.cancel') }}</a-button><a-button type="primary" :loading="saving" @click="addParam">{{ $t('label.ok') }}</a-button></div></template>
    </a-modal>

    <a-modal
class="mold-dialog autoscale-dialog"
centered
:width="760"
      :title="$t('label.edit.autoscale.vmprofile')"
      :visible="editProfileModalVisible"
      :afterClose="closeModal"
      :maskClosable="false"
      :closable="true"
      @cancel="editProfileModalVisible = false">

      <div class="form">
        <div class="form__item" v-if="resource.lbprovider === 'Netscaler'">
          <div class="form__label">{{ $t('label.user') }}</div>
          <a-select
            style="width: 100%"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
                return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
                }"
            v-focus="true"
            v-model:value="autoscaleuserid">
            <a-select-option
              v-for="(user, index) in usersList"
              :value="user.id"
              :key="index"
              :label="user.username">
              {{ user.username }}
            </a-select-option>
          </a-select>
        </div>
      </div>
      <div class="form">
        <div class="form__item">
          <div class="form__label">
            <tooltip-label :title="$t('label.expungevmgraceperiod')" :tooltip="$t('message.autoscale.lifecycle.grace')"/>
          </div>
          <a-input v-model:value="expungevmgraceperiod" type="number"></a-input>
        </div>
      </div>
      <div class="form">
        <div class="form__item">
          <div class="form__label">
            <tooltip-label :title="$t('label.templatename')" :tooltip="(createAutoScaleVmProfileApiParams.templateid || {}).description"/>
          </div>
          <a-select
            style="width: 100%"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
                return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
              }"
            v-focus="true"
            v-model:value="templateid">
            <a-select-option
              v-for="(template, index) in templatesList"
              :value="template.id"
              :key="index"
              :label="template.name">
              {{ template.name }}
            </a-select-option>
          </a-select>
        </div>
      </div>
      <div class="form">
        <div class="form__item">
          <div class="form__label">
            <tooltip-label :title="$t('label.serviceofferingid')" :tooltip="(createAutoScaleVmProfileApiParams.serviceofferingid || {}).description"/>
          </div>
          <a-select
            style="width: 100%"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
                return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
              }"
            v-focus="true"
            v-model:value="serviceofferingid">
            <a-select-option
              v-for="(offering, index) in serviceOfferingsList"
              :value="offering.id"
              :key="index"
              :label="offering.name">
              {{ offering.name }}
            </a-select-option>
          </a-select>
        </div>
      </div>

      <template #footer>
        <div :span="24" class="action-button">
        <a-button :disabled="saving" @click="closeModal">{{ $t('label.cancel') }}</a-button>
        <a-button :loading="saving" ref="submit" type="primary" @click="updateAutoScaleVmProfile">{{ $t('label.ok') }}</a-button>
      </div>
      </template>
    </a-modal>

      <reset-user-data
        v-if="showUpdateUserDataForm"
        :resource="{ ...resource, ...{ resetUserDataApiName: 'updateAutoScaleVmProfile', resetUserDataResourceId: this.resource.vmprofileid, templateid: this.templateid}}"
        @close-action="onUserDataClose"
      />
  </div>
</template>

<script>
import '@/style/components/view/autoscale.less'
import { autoScaleResourceScope } from '@/utils/autoScaleUi'
import { getAPI, postAPI } from '@/api'
import { isAdminOrDomainAdmin } from '@/role'
import { autoScaleOfferingEligible, autoScaleInteger } from '@/utils/autoscaleValidation'
import { runAutoScaleMutation, autoScaleOwnerParams, autoScaleAffectedGroups, autoScaleMutationDescription } from '@/utils/autoScaleEditing'
import Status from '@/components/widgets/Status'
import TooltipLabel from '@/components/widgets/TooltipLabel'
import ResetUserData from '@views/compute/ResetUserData'

export default {
  name: 'conditionsTab',
  components: {
    ResetUserData,
    Status,
    TooltipLabel
  },
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  data () {
    return {
      filterColumns: ['Actions'],
      loading: true,
      profileRefreshing: false,
      profileReadScope: null,
      profileReadRevision: 0,
      saving: false,
      profile: {},
      affectedGroups: [],
      mutationError: '',
      zoneid: null,
      editProfileModalVisible: false,
      showUpdateUserDataForm: false,
      profileid: null,
      autoscaleuserid: null,
      expungevmgraceperiod: null,
      templateid: null,
      serviceofferingid: null,
      userdata: null,
      userdataid: null,
      userdataname: null,
      userdatadetails: null,
      userdatapolicy: null,
      usersList: [],
      templatesList: [],
      serviceOfferingsList: [],
      allParams: [],
      pendingParamRemoval: null,
      addParamModalVisible: false,
      newParam: {
        name: null,
        value: null,
        disksize: null
      },
      counterParams: [],
      deployParams: [],
      columns: [
        {
          title: this.$t('label.param.name'),
          dataIndex: 'name'
        },
        {
          title: this.$t('label.param.value'),
          dataIndex: 'value'
        },
        { key: 'actions', title: this.$t('label.actions'), fixed: 'right', width: 180 }
      ]
    }
  },
  beforeCreate () {
    this.createAutoScaleVmProfileApi = this.$store.getters.apis.createAutoScaleVmProfile || {}
    this.createAutoScaleVmProfileApiParams = {}
    ;(this.createAutoScaleVmProfileApi.params || []).forEach(param => {
      this.createAutoScaleVmProfileApiParams[param.name] = param
    })
  },
  created () {
    this.fetchInitData()
  },
  watch: {
    resource: {
      deep: true,
      handler (newItem) {
        if (!newItem || !newItem.id) {
          return
        }
        this.fetchData()
      }
    }
  },
  inject: ['parentFetchData'],
  methods: {
    openParamModal () {
      this.newParam = { name: null, value: null, disksize: null }
      this.mutationError = ''
      this.addParamModalVisible = true
    },
    fetchInitData () {
      this.counterParams = ['snmpcommunity', 'snmpport']
      this.deployParams = ['overridediskofferingid', 'rootdisksize', 'diskofferingid', 'disksize',
        'keypairs', 'affinitygroupids', 'networkids', 'securitygroupids']
      if (this.resource.lbprovider === 'Netscaler') {
        this.paramNameList = this.counterParams.concat(this.deployParams)
      } else {
        this.paramNameList = this.deployParams
      }
      this.paramNameList = this.paramNameList.sort()
      if (this.resource.lbprovider === 'Netscaler') this.fetchUserData()
      this.fetchServiceOfferingData()
      this.fetchData()
      autoScaleAffectedGroups(getAPI, this.resource, { profileId: this.resource.vmprofileid })
        .then(groups => { this.affectedGroups = groups }).catch(error => this.$notifyError(error))
    },
    fetchUserData () {
      getAPI('listUsers', {
        domainid: this.resource.domainid,
        account: this.resource.account
      }).then(json => {
        this.usersList = json.listusersresponse?.user || []
      })
    },
    fetchTemplateData () {
      const scope = autoScaleResourceScope(this)
      const params = {
        ...autoScaleOwnerParams(this.resource),
        listall: true,
        isready: true,
        zoneid: this.zoneid
      }
      return Promise.all(['executable', 'sharedexecutable'].map(templatefilter => getAPI('listTemplates', { ...params, templatefilter }))).then(responses => {
        if (scope !== autoScaleResourceScope(this)) return
        const byId = new Map()
        responses.flatMap(json => json.listtemplatesresponse?.template || []).forEach(template => {
          if (template.format !== 'ISO' && !['SYSTEM', 'ROUTING', 'DATADISK', 'ISODISK'].includes(template.templatetype)) byId.set(template.id, template)
        })
        this.templatesList = [...byId.values()]
      }).catch(error => this.$notifyError(error))
    },
    fetchServiceOfferingData () {
      const params = {
        listall: 'true',
        issystem: 'false'
      }
      if (isAdminOrDomainAdmin()) {
        params.isrecursive = 'true'
      }
      getAPI('listServiceOfferings', params).then(json => {
        this.serviceOfferingsList = json.listserviceofferingsresponse?.serviceoffering || []
        this.serviceOfferingsList = this.serviceOfferingsList.filter(autoScaleOfferingEligible)
      })
    },
    fetchData () {
      const scope = autoScaleResourceScope(this)
      const same = this.profileReadScope === scope
      const revision = ++this.profileReadRevision
      const current = () => revision === this.profileReadRevision && scope === autoScaleResourceScope(this)
      if (!same) {
        this.profile = {}; this.allParams = []; this.profileid = null
        for (const key of ['autoscaleuserid', 'expungevmgraceperiod', 'serviceofferingid', 'templateid', 'userdata', 'userdataid', 'userdataname', 'userdatadetails', 'userdatapolicy']) this[key] = null
      }
      this.loading = !same
      this.profileRefreshing = true
      return getAPI('listAutoScaleVmProfiles', {
        ...autoScaleOwnerParams(this.resource),
        listAll: true,
        id: this.resource.vmprofileid
      }).then(async response => {
        if (!current()) return
        this.profile = response.listautoscalevmprofilesresponse?.autoscalevmprofile?.[0] || {}
        this.zoneid = this.profile.zoneid || this.resource.zoneid
        this.profileid = response.listautoscalevmprofilesresponse?.autoscalevmprofile?.[0]?.id
        this.autoscaleuserid = response.listautoscalevmprofilesresponse?.autoscalevmprofile?.[0]?.autoscaleuserid
        this.expungevmgraceperiod = response.listautoscalevmprofilesresponse?.autoscalevmprofile?.[0]?.expungevmgraceperiod
        this.serviceofferingid = response.listautoscalevmprofilesresponse?.autoscalevmprofile?.[0]?.serviceofferingid
        this.templateid = response.listautoscalevmprofilesresponse?.autoscalevmprofile?.[0]?.templateid
        this.userdata = this.decodeUserData(decodeURIComponent(response.listautoscalevmprofilesresponse?.autoscalevmprofile?.[0]?.userdata || ''))
        this.userdataid = response.listautoscalevmprofilesresponse?.autoscalevmprofile?.[0]?.userdataid
        this.userdataname = response.listautoscalevmprofilesresponse?.autoscalevmprofile?.[0]?.userdataname
        this.userdatadetails = response.listautoscalevmprofilesresponse?.autoscalevmprofile?.[0]?.userdatadetails
        this.userdatapolicy = response.listautoscalevmprofilesresponse?.autoscalevmprofile?.[0]?.userdatapolicy

        const counterparam = response.listautoscalevmprofilesresponse?.autoscalevmprofile?.[0]?.counterparam || {}
        const otherdeployparams = response.listautoscalevmprofilesresponse?.autoscalevmprofile?.[0]?.otherdeployparams || {}
        this.finalizeParams(counterparam, otherdeployparams)
        this.profileReadScope = scope
        await this.fetchTemplateData()
      }).catch(error => { if (current()) this.$notifyError(error) }).finally(() => {
        if (!current()) return
        this.loading = false
        this.profileRefreshing = false
      })
    },
    getTemplateName (templateid) {
      for (const template of this.templatesList) {
        if (template.id === templateid) {
          return template.name
        }
      }
      return this.profile.templatename || templateid || ''
    },
    getServiceOfferingName (serviceofferingid) {
      for (const serviceoffering of this.serviceOfferingsList) {
        if (serviceoffering.id === serviceofferingid) {
          return serviceoffering.name
        }
      }
      return this.profile.serviceofferingname || serviceofferingid || ''
    },
    handleCancel () {
      this.parentFetchData()
    },
    finalizeParams (counterparam, otherdeployparams) {
      this.allParams = []
      if (counterparam.snmpcommunity) {
        this.allParams.push({ name: 'snmpcommunity', value: counterparam.snmpcommunity })
      }
      if (counterparam.snmpport) {
        this.allParams.push({ name: 'snmpport', value: counterparam.snmpport })
      }
      if (otherdeployparams.diskofferingid) {
        this.allParams.push({ name: 'diskofferingid', value: otherdeployparams.diskofferingid })
      }
      if (otherdeployparams.securitygroupids) {
        this.allParams.push({ name: 'securitygroupids', value: otherdeployparams.securitygroupids })
      }
      if (otherdeployparams.rootdisksize) {
        this.allParams.push({ name: 'rootdisksize', value: otherdeployparams.rootdisksize })
      }
      if (otherdeployparams.disksize) {
        this.allParams.push({ name: 'disksize', value: otherdeployparams.disksize })
      }
      if (otherdeployparams.overridediskofferingid) {
        this.allParams.push({ name: 'overridediskofferingid', value: otherdeployparams.overridediskofferingid })
      }
      if (otherdeployparams.keypairs) {
        this.allParams.push({ name: 'keypairs', value: otherdeployparams.keypairs })
      }
      if (otherdeployparams.affinitygroupids) {
        this.allParams.push({ name: 'affinitygroupids', value: otherdeployparams.affinitygroupids })
      }
      if (otherdeployparams.networkids) {
        this.allParams.push({ name: 'networkids', value: otherdeployparams.networkids })
      }
    },
    prepareParamRemoval (record) {
      this.pendingParamRemoval = { resourceId: this.resource.id, profileId: this.profileid, name: record?.name }
    },
    confirmParamRemoval (record) {
      const target = this.pendingParamRemoval
      this.pendingParamRemoval = null
      if (record && record.name !== target?.name) {
        this.$message.warning(this.$t('message.autoscale.selection.stale'))
        return
      }
      if (this.loading || this.saving || target?.resourceId !== this.resource.id || target?.profileId !== this.profileid || !this.allParams.some(item => item.name === target?.name)) {
        this.$message.warning(this.$t('message.autoscale.selection.stale'))
        return
      }
      return this.deleteParam(target.name)
    },
    deleteParam (paramName) {
      this.updateAutoScaleVmProfileWithParam(null, null, paramName)
    },
    addParam () {
      if (this.loading || this.saving) return
      if (!this.newParam.name) {
        this.$refs.newParamName.classList.add('error')
      } else {
        this.$refs.newParamName.classList.remove('error')
      }
      if (!this.newParam.value) {
        this.$refs.newParamValue.classList.add('error')
      } else {
        this.$refs.newParamValue.classList.remove('error')
      }
      if (!this.newParam.name || !this.newParam.value) {
        return
      }
      this.updateAutoScaleVmProfileWithParam(this.newParam.name, this.newParam.value, null)
    },
    updateAutoScaleVmProfileWithParam (paramNameToAdd, paramValueToAdd, paramNameToRemove) {
      if (this.loading || this.saving) return
      this.loading = true

      var params = {
        id: this.profileid
      }
      var i = 0
      var j = 0
      for (var index = 0; index < this.allParams.length; index++) {
        var param = { ...this.allParams[index] }
        if (param.name === 'disksize' && (paramNameToAdd === 'diskofferingid' || paramNameToRemove === 'diskofferingid')) continue
        if (this.counterParams.includes(param.name) && param.name !== paramNameToAdd && param.name !== paramNameToRemove) {
          params['counterparam[' + i + '].name'] = param.name
          params['counterparam[' + i + '].value'] = param.value
          i++
        }
        if (this.deployParams.includes(param.name) && param.name !== paramNameToAdd && param.name !== paramNameToRemove) {
          params['otherdeployparams[' + j + '].name'] = param.name
          params['otherdeployparams[' + j + '].value'] = param.value
          j++
        }
      }
      if (paramNameToAdd && this.counterParams.includes(paramNameToAdd) && paramValueToAdd) {
        params['counterparam[' + i + '].name'] = paramNameToAdd
        params['counterparam[' + i + '].value'] = paramValueToAdd
      }

      if (paramNameToAdd && this.deployParams.includes(paramNameToAdd) && paramValueToAdd) {
        params['otherdeployparams[' + j + '].name'] = paramNameToAdd
        params['otherdeployparams[' + j + '].value'] = paramValueToAdd
      }

      if (paramNameToAdd === 'diskofferingid' && this.newParam.disksize !== null && this.newParam.disksize !== '') {
        params['otherdeployparams[' + (j + 1) + '].name'] = 'disksize'
        params['otherdeployparams[' + (j + 1) + '].value'] = this.newParam.disksize
      }
      if (paramNameToRemove && this.deployParams.includes(paramNameToRemove) && j === 0) params.clearotherdeployparams = true
      if (paramNameToRemove && this.counterParams.includes(paramNameToRemove) && i === 0) params.clearcounterparams = true
      this.loading = false
      return this.saveProfile(params).then(saved => {
        if (saved) this.newParam = { name: null, value: null, disksize: null }
      })
    },
    async saveProfile (params) {
      if (this.saving) return false
      this.saving = true
      this.mutationError = ''
      try {
        await runAutoScaleMutation(postAPI, getAPI, 'updateAutoScaleVmProfile', params)
        await this.fetchData()
        this.editProfileModalVisible = false
        this.addParamModalVisible = false
        this.$message.success(this.$t('message.autoscale.edit.saved'))
        return true
      } catch (error) {
        this.mutationError = autoScaleMutationDescription(error, this.$t)
        this.$notification.error({ message: this.$t('label.error'), description: this.mutationError })
        return false
      } finally {
        this.saving = false
      }
    },
    updateAutoScaleVmProfile () {
      if (this.saving) return
      const grace = autoScaleInteger(this.expungevmgraceperiod, 0, 2147483647)
      if (grace === null) {
        this.$message.error(this.$t('message.autoscale.profile.grace.invalid'))
        return
      }
      const params = {
        id: this.profileid,
        expungevmgraceperiod: grace,
        serviceofferingid: this.serviceofferingid,
        templateid: this.templateid
      }
      if (this.autoscaleuserid) params.autoscaleuserid = this.autoscaleuserid
      return this.saveProfile(params)
    },
    onUserDataClose () {
      this.showUpdateUserDataForm = false
      this.fetchData()
    },
    decodeUserData (userdata) {
      const decodedData = Buffer.from(userdata, 'base64')
      return decodedData.toString('utf-8')
    },
    closeModal () {
      if (this.saving) return
      this.editProfileModalVisible = false
      this.addParamModalVisible = false
      this.fetchData()
    }
  }
}
</script>

<style scoped lang="scss">
  .condition {

    &-container {
      display: flex;
      width: 100%;
      flex-wrap: wrap;
      margin-right: -20px;
      margin-bottom: -10px;
    }

    &__item {
      padding-right: 20px;
      margin-bottom: 20px;

      @media (min-width: 760px) {
        flex: 1;
      }

    }

    &__title {
      font-weight: bold;
    }

  }

  .title {
    margin-bottom: 5px;
    font-weight: bold;
  }

  .add-btn {
    width: 100%;
    padding-top: 15px;
    padding-bottom: 15px;
    height: auto;
  }

  .add-actions {
    display: flex;
    justify-content: flex-end;
    margin-right: -20px;
    margin-bottom: 20px;

    @media (min-width: 760px) {
      margin-top: 20px;
    }

    button {
      margin-right: 20px;
    }

  }

  .form {
    display: flex;
    margin-right: -20px;
    margin-bottom: 20px;
    flex-direction: column;
    align-items: flex-start;

    @media (min-width: 760px) {
      flex-direction: row;
    }

    &__item {
      display: flex;
      flex-direction: column;
      flex: 1;
      padding-right: 20px;
      margin-bottom: 20px;

      @media (min-width: 760px) {
        margin-bottom: 0;
      }

      input,
      .ant-select {
        margin-top: auto;
      }

    }

    &__label {
      font-weight: bold;
    }

    &__required {
      margin-right: 5px;
      color: var(--ui-error-text);
    }

    .error-text {
      display: none;
      color: var(--ui-error-text);
      font-size: 0.8rem;
    }

    .error {

      input {
        border-color: var(--ui-error-text);
      }

      .error-text {
        display: block;
      }

    }

  }
  .pagination {
    margin-top: 20px;
  }
</style>

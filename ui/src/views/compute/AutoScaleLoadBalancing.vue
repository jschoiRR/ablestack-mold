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
      <a-button type="primary" @click="openEditRuleModal(currentRule)" :disabled="loading || !currentRule || !('updateLoadBalancerRule' in $store.getters.apis)"><template #icon><edit-outlined /></template>{{ $t('label.edit.rule') }}</a-button>
      <a-button @click="openTagsModal(currentRule.id)" :disabled="loading || !currentRule || !('updateLoadBalancerRule' in $store.getters.apis)"><template #icon><tag-outlined /></template>{{ $t('label.edit.tags') }}</a-button>
      <a-button @click="openStickinessModal(currentRule.id)" :disabled="loading || !currentRule || !('createLBStickinessPolicy' in $store.getters.apis)"><template #icon><link-outlined /></template>{{ $t('label.action.configure.stickiness') }}</a-button>
      <a-button disabled><template #icon><delete-outlined /></template>{{ $t('label.autoscale.rule.remove') }}</a-button>
      <a-button @click="fetchData" :disabled="loading" :loading="loading || lbRefreshing"><template #icon><reload-outlined /></template>{{ $t('label.refresh') }}</a-button>
    </a-space>
    <p v-if="listRefreshFailed" role="status">{{ $t('message.list.refresh.stale') }}</p>
    <a-alert v-if="resource.state !== 'DISABLED' || resource.operationinprogress" type="info" show-icon :message="$t(resource.operationinprogress ? 'message.autoscale.operation.wait' : 'message.autoscale.loadbalancer.update')" />
    <a-card size="small" v-if="currentRule">
      <a-descriptions size="small" :column="{ xs: 1, sm: 2 }">
        <a-descriptions-item v-for="key in ['name', 'publicport', 'privateport', 'algorithm', 'protocol', 'state']" :key="key" :label="$t('label.' + key)"><status v-if="key === 'state'" :text="currentRule.state" :display-text="true" :display-label="ruleStateLabel(currentRule.state)" /><template v-else>{{ key === 'algorithm' ? returnAlgorithmName(currentRule[key]) : key === 'protocol' ? getCapitalise(currentRule[key] || 'tcp') : currentRule[key] }}</template></a-descriptions-item>
        <a-descriptions-item :label="$t('label.action.configure.stickiness')">{{ returnStickinessLabel(currentRule.id) }}</a-descriptions-item>
      </a-descriptions>
    </a-card>
    <a-alert type="info" show-icon :message="$t('message.autoscale.lb.delete.bound')" />
    <a-table
size="small"
:loading="loading"
:columns="instanceColumns"
:data-source="ruleInstances"
:pagination="false"
row-key="key"
:scroll="{ x: 640 }">
      <template #bodyCell="{ column, record }">
        <router-link v-if="column.key === 'name'" :to="'/vm/' + record.instance.loadbalancerruleinstance.id">{{ record.instance.loadbalancerruleinstance.displayname || record.instance.loadbalancerruleinstance.name }}</router-link>
        <status v-else-if="column.key === 'state'" :text="record.instance.loadbalancerruleinstance.state" :display-text="true" />
        <template v-else-if="column.key === 'actions'"><div class="autoscale-row-actions"><a-popconfirm overlay-class-name="autoscale-confirm" :title="$t('message.autoscale.vm.detach.confirm')" @confirm="confirmInstanceRemoval(record)" @cancel="pendingInstanceRemoval = null" :ok-button-props="{ disabled: loading }" :ok-text="$t('label.yes')" :cancel-text="$t('label.no')"><a-button size="small" :disabled="loading || !('removeFromLoadBalancerRule' in $store.getters.apis) || resource.state !== 'DISABLED' || resource.operationinprogress" @click="prepareInstanceRemoval(record)"><template #icon><disconnect-outlined /></template>{{ $t('label.autoscale.vm.detach') }}</a-button></a-popconfirm></div></template>
      </template>
    </a-table>
    <a-modal
class="mold-dialog autoscale-dialog tags-modal"
centered
:width="760"
      v-if="tagsModalVisible"
      :title="$t('label.edit.tags')"
      :visible="tagsModalVisible"
      :closable="true"
      :afterClose="closeModal"
      :maskClosable="false"
      @cancel="tagsModalVisible = false">
      <span v-show="tagsModalLoading" class="modal-loading">
        <loading-outlined />
      </span>

      <a-form
        :ref="formRef"
        :model="form"
        :rules="rules"
        class="add-tags"
        @finish="handleAddTag"
        v-ctrl-enter="handleAddTag"
       >
        <div class="add-tags__input">
          <p class="add-tags__label">{{ $t('label.key') }}</p>
          <a-form-item ref="key" name="key">
            <a-input
              v-focus="true"
              v-model:value="form.key" />
          </a-form-item>
        </div>
        <div class="add-tags__input">
          <p class="add-tags__label">{{ $t('label.value') }}</p>
          <a-form-item ref="value" name="value">
            <a-input v-model:value="form.value" />
          </a-form-item>
        </div>
        <a-button :disabled="!('createTags' in $store.getters.apis)" type="primary" ref="submit" @click="handleAddTag">{{ $t('label.add') }}</a-button>
      </a-form>

      <a-divider />

      <div v-show="!tagsModalLoading" class="tags-container">
        <div class="tags" v-for="(tag, index) in tags" :key="index">
          <a-tag :key="index" :closable="'deleteTags' in $store.getters.apis" @close="() => handleDeleteTag(tag)">
            {{ tag.key }} = {{ tag.value }}
          </a-tag>
        </div>
      </div>

      <template #footer><a-button @click="tagsModalVisible = false">{{ $t('label.done') }}</a-button></template>
    </a-modal>

    <a-modal
class="mold-dialog autoscale-dialog"
centered
:width="760"
      :title="$t('label.configure.sticky.policy')"
      :visible="stickinessModalVisible"
      :afterClose="closeModal"
      :maskClosable="false"
      :closable="true"
      :okButtonProps="{ props: {htmlType: 'submit'}}"
      @cancel="stickinessModalVisible = false">

      <span v-show="stickinessModalLoading" class="modal-loading">
        <loading-outlined />
      </span>

      <a-form
        :ref="formRef"
        :model="form"
        :rules="rules"
        @finish="handleSubmitStickinessForm"
        v-ctrl-enter="handleSubmitStickinessForm"
        layout="vertical"
        class="autoscale-stickiness-form"
       >
        <a-form-item name="methodname" ref="methodname" :label="$t('label.stickiness.method')">
          <a-select
            v-focus="true"
            v-model:value="form.methodname"
            @change="handleStickinessMethodSelectChange"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }" >
            <a-select-option value="LbCookie" :label="$t('label.lb.cookie')">{{ $t('label.lb.cookie') }}</a-select-option>
            <a-select-option value="AppCookie" :label="$t('label.app.cookie')">{{ $t('label.app.cookie') }}</a-select-option>
            <a-select-option value="SourceBased" :label="$t('label.source.based')">{{ $t('label.source.based') }}</a-select-option>
            <a-select-option value="none" :label="$t('label.none')">{{ $t('label.none') }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item
          name="name"
          ref="name"
          :label="$t('label.sticky.name')"
          v-show="stickinessPolicyMethod === 'LbCookie' || stickinessPolicyMethod ===
            'AppCookie' || stickinessPolicyMethod === 'SourceBased'">
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item
          name="cookieName"
          ref="cookieName"
          :label="$t('label.sticky.cookie-name')"
          v-show="stickinessPolicyMethod === 'LbCookie' || stickinessPolicyMethod ===
            'AppCookie'">
          <a-input v-model:value="form.cookieName" />
        </a-form-item>
        <a-form-item
          name="mode"
          ref="mode"
          :label="$t('label.sticky.mode')"
          v-show="stickinessPolicyMethod === 'LbCookie' || stickinessPolicyMethod ===
            'AppCookie'">
          <a-input v-model:value="form.mode" />
        </a-form-item>
        <a-form-item name="nocache" ref="nocache" v-show="stickinessPolicyMethod === 'LbCookie'">
          <a-checkbox v-model:checked="form.nocache">{{ $t('label.sticky.nocache') }}</a-checkbox>
        </a-form-item>
        <a-form-item name="indirect" ref="indirect" v-show="stickinessPolicyMethod === 'LbCookie'">
          <a-checkbox v-model:checked="form.indirect">{{ $t('label.sticky.indirect') }}</a-checkbox>
        </a-form-item>
        <a-form-item name="postonly" ref="postonly" v-show="stickinessPolicyMethod === 'LbCookie'">
          <a-checkbox v-model:checked="form.postonly">{{ $t('label.sticky.postonly') }}</a-checkbox>
        </a-form-item>
        <a-form-item name="domain" ref="domain" :label="$t('label.domain')" v-show="stickinessPolicyMethod === 'LbCookie'">
          <a-input v-model:value="form.domain" />
        </a-form-item>
        <a-form-item name="length" ref="length" :label="$t('label.sticky.length')" v-show="stickinessPolicyMethod === 'AppCookie'">
          <a-input v-model:value="form.length" type="number" />
        </a-form-item>
        <a-form-item name="holdtime" ref="holdtime" :label="$t('label.sticky.holdtime')" v-show="stickinessPolicyMethod === 'AppCookie'">
          <a-input v-model:value="form.holdtime" type="number" />
        </a-form-item>
        <a-form-item name="requestLearn" ref="requestLearn" v-show="stickinessPolicyMethod === 'AppCookie'">
          <a-checkbox v-model:checked="form.requestLearn">{{ $t('label.sticky.request-learn') }}</a-checkbox>
        </a-form-item>
        <a-form-item name="prefix" ref="prefix" v-show="stickinessPolicyMethod === 'AppCookie'">
          <a-checkbox v-model:checked="form.prefix">{{ $t('label.sticky.prefix') }}</a-checkbox>
        </a-form-item>
        <a-form-item name="tablesize" ref="tablesize" :label="$t('label.sticky.tablesize')" v-show="stickinessPolicyMethod === 'SourceBased'">
          <a-input v-model:value="form.tablesize" />
        </a-form-item>
        <a-form-item name="expire" ref="expire" :label="$t('label.sticky.expire')" v-show="stickinessPolicyMethod === 'SourceBased'">
          <a-input v-model:value="form.expire" />
        </a-form-item>

      </a-form>
      <template #footer>
        <div :span="24" class="action-button">
          <a-button @click="stickinessModalVisible = false">{{ $t('label.cancel') }}</a-button>
          <a-button type="primary" ref="submit" @click="handleSubmitStickinessForm">{{ $t('label.ok') }}</a-button>
        </div>
      </template>
    </a-modal>

    <a-modal
class="mold-dialog autoscale-dialog"
centered
:width="760"
      :title="$t('label.edit.rule')"
      :visible="editRuleModalVisible"
      :afterClose="closeModal"
      :maskClosable="false"
      :closable="true"
      @cancel="editRuleModalVisible = false">
      <span v-show="editRuleModalLoading" class="modal-loading">
        <loading-outlined />
      </span>

      <div class="edit-rule" v-if="selectedRule" v-ctrl-enter="handleSubmitEditForm">
        <div class="edit-rule__item">
          <p class="edit-rule__label">{{ $t('label.name') }}</p>
          <a-input v-focus="true" v-model:value="editRuleDetails.name" />
        </div>
        <div class="edit-rule__item">
          <p class="edit-rule__label">{{ $t('label.algorithm') }}</p>
          <a-select
            v-model:value="editRuleDetails.algorithm"
            showSearch
            optionFilterProp="value"
            :filterOption="(input, option) => {
              return option.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }" >
            <a-select-option value="roundrobin">{{ $t('label.lb.algorithm.roundrobin') }}</a-select-option>
            <a-select-option value="leastconn">{{ $t('label.lb.algorithm.leastconn') }}</a-select-option>
            <a-select-option value="source">{{ $t('label.lb.algorithm.source') }}</a-select-option>
          </a-select>
        </div>
        <div class="edit-rule__item">
          <p class="edit-rule__label">{{ $t('label.protocol') }}</p>
          <a-select
            v-model:value="editRuleDetails.protocol"
            showSearch
            optionFilterProp="value"
            :filterOption="(input, option) => {
              return option.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }" >
            <a-select-option value="tcp-proxy">{{ $t('label.tcp.proxy') }}</a-select-option>
            <a-select-option value="tcp">{{ $t('label.tcp') }}</a-select-option>
            <a-select-option value="udp">{{ $t('label.udp') }}</a-select-option>
            <a-select-option value="ssl">{{ $t('label.ssl') }}</a-select-option>
            <a-select-option value="http">{{ $t('label.lb.protocol.http') }}</a-select-option>
          </a-select>
        </div>

      </div>
      <template #footer>
        <div :span="24" class="action-button">
          <a-button @click="() => editRuleModalVisible = false">{{ $t('label.cancel') }}</a-button>
          <a-button type="primary" @click="handleSubmitEditForm">{{ $t('label.ok') }}</a-button>
        </div>
      </template>
    </a-modal>
  </div>
</template>

<script>
import '@/style/components/view/autoscale.less'
import { autoScaleResourceScope } from '@/utils/autoScaleUi'
import { autoScaleOwnerParams } from '@/utils/autoScaleEditing'
import { listRefreshMixin } from '@/utils/listRefreshMixin'

import { ref, reactive, toRaw, nextTick } from 'vue'
import { getAPI, postAPI } from '@/api'
import { mixinForm } from '@/utils/mixin'
import Status from '@/components/widgets/Status'

export default {
  name: 'LoadBalancing',
  mixins: [listRefreshMixin(['fetchLBRules']), mixinForm],
  components: {
    Status
  },
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  inject: ['parentFetchData', 'parentToggleLoading'],
  data () {
    return {
      loading: true,
      lbRefreshing: false,
      lbReadScope: null,
      lbRules: [],
      pendingInstanceRemoval: null,
      tagsModalVisible: false,
      tagsModalLoading: false,
      tags: [],
      selectedRule: null,
      selectedTier: null,
      stickinessModalVisible: false,
      stickinessPolicies: [],
      stickinessModalLoading: false,
      selectedStickinessPolicy: null,
      stickinessPolicyMethod: 'LbCookie',
      editRuleModalVisible: false,
      editRuleModalLoading: false,
      editRuleDetails: {
        name: '',
        algorithm: '',
        protocol: ''
      },
      vms: [],
      nics: [],
      totalCount: 0,
      page: 1,
      pageSize: 10,
      columns: [
        {
          title: this.$t('label.name'),
          dataIndex: 'name'
        },
        {
          title: this.$t('label.publicport'),
          dataIndex: 'publicport'
        },
        {
          title: this.$t('label.privateport'),
          dataIndex: 'privateport'
        },
        {
          title: this.$t('label.algorithm'),
          key: 'algorithm'
        },
        {
          title: this.$t('label.protocol'),
          key: 'protocol'
        },
        {
          title: this.$t('label.state'),
          dataIndex: 'state'
        },
        {
          title: this.$t('label.action.configure.stickiness'),
          key: 'stickiness'
        },
        {
          title: this.$t('label.actions'),
          key: 'actions'
        }
      ],
      tiers: {
        loading: false,
        data: []
      },
      vmColumns: [
        {
          title: this.$t('label.name'),
          dataIndex: 'name',
          key: 'name',
          width: 220
        },
        {
          title: this.$t('label.state'),
          dataIndex: 'state',
          key: 'state'
        },
        {
          title: this.$t('label.displayname'),
          dataIndex: 'displayname'
        },
        {
          title: this.$t('label.account'),
          dataIndex: 'account'
        },
        {
          title: this.$t('label.zonename'),
          dataIndex: 'zonename'
        },
        {
          title: this.$t('label.select'),
          dataIndex: 'actions',
          key: 'actions',
          width: 80
        }
      ],
      vmPage: 1,
      vmPageSize: 10,
      vmCount: 0,
      searchQuery: null
    }
  },
  computed: {
    currentRule () { return this.lbRules[0] },
    ruleInstances () { return (this.currentRule?.ruleInstances || []).flatMap(instance => (instance.lbvmipaddresses || []).map(ip => ({ key: instance.loadbalancerruleinstance.id + '/' + ip, ip, instance }))) },
    instanceColumns () { return ['name', 'state', 'ip', 'actions'].map(key => ({ key, dataIndex: key, title: this.$t(key === 'ip' ? 'label.ipaddress' : 'label.' + key), ...(key === 'actions' ? { fixed: 'right', width: 180 } : {}) })) }
  },
  created () {
    this.initForm()
    this.fetchData()
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
  methods: {
    ruleStateLabel (state) {
      const labels = { Active: 'state.ok', Add: 'label.autoscale.rule.pending', Staged: 'label.autoscale.rule.staged', Revoke: 'label.autoscale.rule.revoking' }
      return labels[state] ? this.$t(labels[state]) : state || this.$t('label.autoscale.not.recorded')
    },
    initForm () {
      this.formRef = ref()
      this.form = reactive({})
      this.rules = reactive({})
    },
    fetchData () {
      this.fetchListTiers()
      this.fetchLBRules()
    },
    fetchListTiers () {
      this.tiers.loading = true

      getAPI('listNetworks', {
        account: this.resource.account,
        domainid: this.resource.domainid,
        supportedservices: 'Lb',
        vpcid: this.resource.vpcid
      }).then(json => {
        this.tiers.data = json.listnetworksresponse.network || []
        this.selectedTier = this.tiers.data?.[0]?.id ? this.tiers.data[0].id : null
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => { this.tiers.loading = false })
    },
    async fetchLBRules () {
      const listRequest = this.listRequestToken('fetchLBRules')
      const scope = autoScaleResourceScope(this)
      const same = this.lbReadScope === scope
      const current = () => this.isListRequestCurrent('fetchLBRules', listRequest) && scope === autoScaleResourceScope(this)
      if (!same) { this.lbRules = []; this.stickinessPolicies = [] }
      this.loading = !same
      this.lbRefreshing = true
      try {
        const response = await getAPI('listLoadBalancerRules', { ...autoScaleOwnerParams(this.resource), listall: true, id: this.resource.lbruleid, page: this.page, pagesize: this.pageSize })
        if (!current()) return
        const previous = new Map(this.lbRules.map(rule => [rule.id, rule]))
        const policies = this.stickinessPolicies
        let partialFailure = false
        const results = await Promise.all((response.listloadbalancerrulesresponse?.loadbalancerrule || []).map(async rule => {
          const params = { ...autoScaleOwnerParams(this.resource), listall: true }
          const reads = await Promise.allSettled([
            getAPI('listLoadBalancerRuleInstances', { ...params, lbvmips: true, id: rule.id }),
            getAPI('listLBStickinessPolicies', { ...params, lbruleid: rule.id }),
            getAPI('listAutoScaleVmGroups', { ...params, lbruleid: rule.id })
          ])
          partialFailure ||= reads.some(read => read.status === 'rejected')
          const old = previous.get(rule.id) || {}
          return {
            rule: {
              ...old,
              ...rule,
              ruleInstances: reads[0].status === 'fulfilled' ? reads[0].value.listloadbalancerruleinstancesresponse?.lbrulevmidip || [] : old.ruleInstances || [],
              autoscalevmgroup: reads[2].status === 'fulfilled' ? reads[2].value.listautoscalevmgroupsresponse?.autoscalevmgroup?.[0] : old.autoscalevmgroup
            },
            policies: reads[1].status === 'fulfilled' ? reads[1].value.listlbstickinesspoliciesresponse?.stickinesspolicies || [] : policies.filter(policy => policy.lbruleid === rule.id)
          }
        }))
        if (!current()) return
        this.lbRules = results.map(result => result.rule)
        this.stickinessPolicies = results.flatMap(result => result.policies)
        this.totalCount = response.listloadbalancerrulesresponse?.count || 0
        this.lbReadScope = scope
        listRequest.failed = partialFailure
        this.listRefreshFailed = partialFailure
      } catch (error) {
        if (!current()) return
        listRequest.failed = true; this.listRefreshFailed = true
        if (!same) this.$notifyError(error)
      } finally {
        if (current()) { this.loading = false; this.lbRefreshing = false }
      }
    },
    returnAlgorithmName (name) {
      switch (name) {
        case 'leastconn':
          return this.$t('label.lb.algorithm.leastconn')
        case 'roundrobin' :
          return this.$t('label.lb.algorithm.roundrobin')
        case 'source':
          return this.$t('label.lb.algorithm.source')
        default :
          return ''
      }
    },
    returnStickinessLabel (id) {
      const match = this.stickinessPolicies.filter(policy => policy.lbruleid === id)
      if (match.length > 0 && match[0].stickinesspolicy.length > 0) {
        return match[0].stickinesspolicy[0].methodname
      }
      return this.$t('label.none')
    },
    getCapitalise (val) {
      if (!val) {
        return
      }
      if (val === 'all') return this.$t('label.all')
      return val.toUpperCase()
    },
    openTagsModal (id) {
      this.initForm()
      this.rules = {
        key: [{ required: true, message: this.$t('message.specify.tag.key') }],
        value: [{ required: true, message: this.$t('message.specify.tag.value') }]
      }
      this.tagsModalLoading = true
      this.tagsModalVisible = true
      this.tags = []
      this.selectedRule = id
      getAPI('listTags', {
        resourceId: id,
        resourceType: 'LoadBalancer',
        listAll: true
      }).then(response => {
        this.tags = response.listtagsresponse.tag
        this.tagsModalLoading = false
      }).catch(error => {
        this.$notifyError(error)
        this.closeModal()
      })
    },
    handleAddTag (e) {
      if (this.tagsModalLoading) return
      this.tagsModalLoading = true

      if (e) e.preventDefault()
      this.formRef.value.validate().then(() => {
        const formRaw = toRaw(this.form)
        const values = this.handleRemoveFields(formRaw)

        postAPI('createTags', {
          'tags[0].key': values.key,
          'tags[0].value': values.value,
          resourceIds: this.selectedRule,
          resourceType: 'LoadBalancer'
        }).then(response => {
          this.$pollJob({
            jobId: response.createtagsresponse.jobid,
            successMessage: this.$t('message.success.add.tag'),
            successMethod: () => {
              this.parentFetchData()
              this.openTagsModal(this.selectedRule)
            },
            errorMessage: this.$t('message.add.tag.failed'),
            errorMethod: () => {
              this.parentFetchData()
              this.closeModal()
            },
            loadingMessage: this.$t('message.add.tag.processing'),
            catchMessage: this.$t('error.fetching.async.job.result'),
            catchMethod: () => {
              this.parentFetchData()
              this.parentFetchData()
              this.closeModal()
            }
          })
        }).catch(error => {
          this.$notifyError(error)
        })
      }).catch(error => {
        this.formRef.value.scrollToField(error.errorFields[0].name)
      }).finally(() => {
        this.tagsModalLoading = false
      })
    },
    handleDeleteTag (tag) {
      this.tagsModalLoading = true
      postAPI('deleteTags', {
        'tags[0].key': tag.key,
        'tags[0].value': tag.value,
        resourceIds: tag.resourceid,
        resourceType: 'LoadBalancer'
      }).then(response => {
        this.$pollJob({
          jobId: response.deletetagsresponse.jobid,
          successMessage: this.$t('message.success.delete.tag'),
          successMethod: () => {
            this.parentFetchData()
            this.openTagsModal(this.selectedRule)
          },
          errorMessage: this.$t('message.delete.tag.failed'),
          errorMethod: () => {
            this.parentFetchData()
            this.closeModal()
          },
          loadingMessage: this.$t('message.delete.tag.processing'),
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.parentFetchData()
            this.parentFetchData()
            this.closeModal()
          }
        })
      }).catch(error => {
        this.$notifyError(error)
      })
    },
    openStickinessModal (id) {
      this.initForm()
      this.rules = {
        methodname: [{ required: true, message: this.$t('message.error.specify.stickiness.method') }]
      }
      this.stickinessModalVisible = true
      this.selectedRule = id
      this.selectedStickinessPolicy = null
      this.stickinessPolicyMethod = 'none'
      this.form.methodname = 'none'
      const match = this.stickinessPolicies.find(policy => policy.lbruleid === id)

      if (match && match.stickinesspolicy.length > 0) {
        this.selectedStickinessPolicy = match.stickinesspolicy[0]
        this.stickinessPolicyMethod = this.selectedStickinessPolicy.methodname
        nextTick().then(() => {
          this.form.methodname = this.selectedStickinessPolicy.methodname
          this.form.name = this.selectedStickinessPolicy.name
          this.form.cookieName = this.selectedStickinessPolicy.params['cookie-name']
          this.form.mode = this.selectedStickinessPolicy.params.mode
          this.form.domain = this.selectedStickinessPolicy.params.domain
          this.form.length = this.selectedStickinessPolicy.params.length
          this.form.holdtime = this.selectedStickinessPolicy.params.holdtime
          this.form.tablesize = this.selectedStickinessPolicy.params.tablesize
          this.form.expire = this.selectedStickinessPolicy.params.expire
          this.form.nocache = !!this.selectedStickinessPolicy.params.nocache
          this.form.indirect = !!this.selectedStickinessPolicy.params.indirect
          this.form.postonly = !!this.selectedStickinessPolicy.params.postonly
          this.form.requestLearn = !!this.selectedStickinessPolicy.params['request-learn']
          this.form.prefix = !!this.selectedStickinessPolicy.params.prefix
        })
      }
    },
    handleAddStickinessPolicy (data, values) {
      postAPI('createLBStickinessPolicy', {
        ...data,
        lbruleid: this.selectedRule,
        name: values.name,
        methodname: values.methodname
      }).then(response => {
        this.$pollJob({
          jobId: response.createLBStickinessPolicy.jobid,
          successMessage: this.$t('message.success.config.sticky.policy'),
          successMethod: () => {
            this.parentFetchData()
            this.fetchData()
            this.closeModal()
          },
          errorMessage: this.$t('message.config.sticky.policy.failed'),
          errorMethod: () => {
            this.parentFetchData()
            this.fetchData()
            this.closeModal()
          },
          loadingMessage: this.$t('message.config.sticky.policy.processing'),
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.parentFetchData()
            this.parentFetchData()
            this.fetchData()
            this.closeModal()
          }
        })
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.closeModal()
      })
    },
    handleDeleteStickinessPolicy () {
      this.stickinessModalLoading = true
      postAPI('deleteLBStickinessPolicy', { id: this.selectedStickinessPolicy.id }).then(response => {
        this.$pollJob({
          jobId: response.deleteLBstickinessrruleresponse.jobid,
          successMessage: this.$t('message.success.remove.sticky.policy'),
          successMethod: () => {
            this.parentFetchData()
            this.fetchData()
            this.closeModal()
          },
          errorMessage: this.$t('message.remove.sticky.policy.failed'),
          errorMethod: () => {
            this.parentFetchData()
            this.fetchData()
            this.closeModal()
          },
          loadingMessage: this.$t('message.remove.sticky.policy.processing'),
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.parentFetchData()
            this.parentFetchData()
            this.fetchData()
            this.closeModal()
          }
        })
      }).catch(error => {
        this.$notifyError(error)
      })
    },
    handleSubmitStickinessForm (e) {
      if (this.stickinessModalLoading) return
      this.stickinessModalLoading = true
      if (e) e.preventDefault()
      this.formRef.value.validate().then(() => {
        const formRaw = toRaw(this.form)
        const values = this.handleRemoveFields(formRaw)
        if (values.methodname === 'none') {
          if (!this.selectedStickinessPolicy) { this.closeModal(); return }
          this.handleDeleteStickinessPolicy()
          return
        }

        if (values.name === null || values.name === undefined || values.name === '') {
          this.$notification.error({
            message: this.$t('label.error'),
            description: this.$t('message.error.specify.sticky.name')
          })
          return
        }

        values.nocache = this.form.nocache
        values.indirect = this.form.indirect
        values.postonly = this.form.postonly
        values.requestLearn = this.form.requestLearn
        values.prefix = this.form.prefix

        let data = {}
        let count = 0
        Object.entries(values).forEach(([key, val]) => {
          if (val && key !== 'name' && key !== 'methodname') {
            if (key === 'cookieName') {
              data = { ...data, ...{ [`param[${count}].name`]: 'cookie-name' } }
            } else if (key === 'requestLearn') {
              data = { ...data, ...{ [`param[${count}].name`]: 'request-learn' } }
            } else {
              data = { ...data, ...{ [`param[${count}].name`]: key } }
            }
            data = { ...data, ...{ [`param[${count}].value`]: val } }
            count++
          }
        })

        this.handleAddStickinessPolicy(data, values)
      }).catch(error => {
        this.formRef.value.scrollToField(error.errorFields[0].name)
      }).finally(() => {
        this.stickinessModalLoading = false
      })
    },
    handleStickinessMethodSelectChange (e) {
      if (this.formRef.value) this.formRef.value.resetFields()
      this.stickinessPolicyMethod = e
      this.form.methodname = e
    },
    prepareInstanceRemoval (record) {
      this.pendingInstanceRemoval = { resourceId: this.resource.id, ruleId: this.currentRule?.id, key: record?.key }
    },
    confirmInstanceRemoval (record) {
      const target = this.pendingInstanceRemoval
      this.pendingInstanceRemoval = null
      if (record && record.key !== target?.key) {
        this.$message.warning(this.$t('message.autoscale.selection.stale'))
        return
      }
      const selected = this.ruleInstances.find(item => item.key === target?.key)
      if (this.loading || target?.resourceId !== this.resource.id || target?.ruleId !== this.currentRule?.id || !selected) {
        this.$message.warning(this.$t('message.autoscale.selection.stale'))
        return
      }
      return this.handleDeleteInstanceFromRule(selected.instance, this.currentRule, selected.ip)
    },
    handleDeleteInstanceFromRule (instance, rule, ip) {
      this.loading = true
      postAPI('removeFromLoadBalancerRule', {
        id: rule.id,
        'vmidipmap[0].vmid': instance.loadbalancerruleinstance.id,
        'vmidipmap[0].vmip': ip
      }).then(response => {
        this.$pollJob({
          jobId: response.removefromloadbalancerruleresponse.jobid,
          successMessage: this.$t('message.success.remove.instance.rule'),
          successMethod: () => {
            this.fetchData()
          },
          errorMessage: this.$t('message.remove.instance.failed'),
          errorMethod: () => {
            this.fetchData()
          },
          loadingMessage: this.$t('message.remove.instance.processing'),
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.fetchData()
          }
        })
      }).catch(error => {
        this.$notifyError(error)
        this.fetchData()
      })
    },
    openEditRuleModal (rule) {
      this.selectedRule = rule
      this.editRuleModalVisible = true
      this.editRuleDetails.name = this.selectedRule.name
      this.editRuleDetails.algorithm = this.selectedRule.algorithm
      this.editRuleDetails.protocol = this.selectedRule.protocol || 'tcp'
    },
    handleSubmitEditForm () {
      if (this.editRuleModalLoading) return
      this.loading = true
      this.editRuleModalLoading = true
      postAPI('updateLoadBalancerRule', {
        ...this.editRuleDetails,
        id: this.selectedRule.id
      }).then(response => {
        this.$pollJob({
          jobId: response.updateloadbalancerruleresponse.jobid,
          successMessage: this.$t('message.success.edit.rule'),
          successMethod: () => {
            this.parentFetchData()
            this.fetchData()
            this.closeModal()
          },
          errorMessage: this.$t('message.edit.rule.failed'),
          errorMethod: () => {
            this.parentFetchData()
            this.fetchData()
            this.closeModal()
          },
          loadingMessage: this.$t('message.edit.rule.processing'),
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.parentFetchData()
            this.parentFetchData()
            this.fetchData()
            this.closeModal()
          }
        })
      }).catch(error => {
        this.$notifyError(error)
        this.loading = false
        this.editRuleModalLoading = false
      })
    },
    closeModal () {
      this.selectedRule = null
      this.tagsModalVisible = false
      this.stickinessModalVisible = false
      this.stickinessModalLoading = false
      this.selectedStickinessPolicy = null
      this.stickinessPolicyMethod = 'LbCookie'
      this.editRuleModalVisible = false
      this.editRuleModalLoading = false
      this.vms = []
      this.nics = []
    }
  }
}
</script>

<style lang="scss" scoped>
  .rule {

    &-container {
      display: flex;
      flex-direction: column;
      width: 100%;

      @media (min-width: 760px) {
        margin-right: -20px;
        margin-bottom: -10px;
      }

    }

    &__row {
      display: flex;
      flex-wrap: wrap;
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

  .form {
    display: flex;
    margin-right: -20px;
    flex-direction: column;
    align-items: flex-start;

    @media (min-width: 760px) {
      flex-direction: row;
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

    &--column {
      flex-direction: column;
      margin-right: 0;
      align-items: flex-end;

      .form__item {
        width: 100%;
        padding-right: 0;
      }

    }

    &__item {
      display: flex;
      flex-direction: column;
      padding-right: 20px;
      margin-bottom: 20px;

      @media (min-width: 1200px) {
        margin-bottom: 0;
        flex: 1;
      }

      input,
      .ant-select {
        margin-top: auto;
      }

      &__input-container {
        display: flex;

        input {

          &:not(:last-child) {
            margin-right: 10px;
          }

        }

      }

    }

    &__label {
      font-weight: bold;
    }

  }

  .rule-action {
    margin-bottom: 10px;
  }

  .tags-modal {

    .ant-divider {
      margin-top: 0;
    }

  }

  .tags {
    margin-bottom: 10px;
  }

  .add-tags {
    display: flex;
    align-items: center;
    justify-content: space-between;

    &__input {
      margin-right: 10px;
    }

    &__label {
      margin-bottom: 5px;
      font-weight: bold;
    }

  }

  .tags-container {
    display: flex;
    flex-wrap: wrap;
    margin-bottom: 10px;
  }

  .add-tags-done {
    display: block;
    margin-left: auto;
  }

  .modal-loading {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: rgba(0,0,0,0.5);
    z-index: 1;
    color: #1890ff;
    font-size: 2rem;
  }

  .ant-list-item {
    display: flex;
    flex-direction: column;
    align-items: flex-start;

    @media (min-width: 760px) {
      flex-direction: row;
      align-items: center;
    }

  }

  .rule-instance-collapse {
    width: 100%;
    margin-left: -15px;

    .ant-collapse-item {
      border: 0;
    }

  }

  .rule-instance-list {
    display: flex;
    flex-direction: column;

    &__item {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;

      div {
        margin-left: 25px;
        margin-bottom: 10px;
      }
    }
  }

  .edit-rule {

    .ant-select {
      width: 100%;
    }

    &__item {
      margin-bottom: 10px;
    }

    &__label {
      margin-bottom: 5px;
      font-weight: bold;
    }

  }

  .vm-modal {

    &__header {
      display: flex;

      span {
        flex: 1;
        font-weight: bold;
        margin-right: 10px;
      }

    }

    &__item {
      display: flex;
      margin-top: 10px;

      span,
      label {
        display: block;
        flex: 1;
        margin-right: 10px;
      }

    }

  }

  .custom-ant-form {
    .ant-form-item-label {
      font-weight: bold;
      line-height: 1;
    }
    .ant-form-item {
      margin-bottom: 10px;
    }
  }

  .custom-ant-list {
    .ant-list-item-action {
      margin-top: 10px;
      margin-left: 0;

      @media (min-width: 760px) {
        margin-top: 0;
        margin-left: 24px;
      }

    }
  }

  .rule-instance-collapse {
    .ant-collapse-header,
    .ant-collapse-content {
      margin-left: -12px;
    }
  }

  .rule {
    .ant-list-item-content-single {
      width: 100%;

      @media (min-width: 760px) {
        width: auto;
      }

    }
  }

  .pagination {
    margin-top: 20px;
    text-align: right;
  }

  .actions {
    button {
      &:not(:last-child) {
        margin-right: 10px;
      }
    }
  }

  .list-view {
    overflow-y: auto;
    display: block;
    width: 100%;
  }

  .filter {
    display: block;
    width: 240px;
    margin-bottom: 10px;
  }

  .input-search {
    margin-bottom: 10px;
    width: 50%;
    float: right;
  }
</style>

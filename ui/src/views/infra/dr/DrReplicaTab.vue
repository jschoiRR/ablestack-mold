<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->
<template>
  <a-spin :spinning="loading">
    <p v-if="listRefreshFailed" role="status">{{ $t('message.list.refresh.stale') }}</p>
    <div class="cross-dr-tab-toolbar">
      <a-button size="small" @click="fetchData">
        <template #icon><ReloadOutlined /></template>
        {{ $t('label.refresh') }}
      </a-button>
    </div>
    <a-table
      size="small"
      :columns="columns"
      :dataSource="replicas"
      :rowKey="record => record.id"
      :pagination="{ pageSize: 10 }"
      :expandRowByClick="true">
      <template #expandedRowRender="{ record }">
        <pre class="cross-dr-code">{{ record.runtimestate || '-' }}</pre>
      </template>
      <template #bodyCell="{ column, record, text }">
        <template v-if="column.key === 'state' || column.key === 'powerstate'">
          <dr-status-pill :status="text" />
        </template>
        <template v-else-if="column.key === 'targetvmname'">
          <router-link v-if="record.targetvmid" :to="{ path: '/vm/' + record.targetvmid }">{{ text || record.targetvmid }}</router-link>
          <span v-else>{{ text || '-' }}</span>
        </template>
      </template>
    </a-table>
  </a-spin>
</template>

<script>
import { listRefreshMixin } from '@/utils/listRefreshMixin'
import DrStatusPill from '@/components/dr/DrStatusPill.vue'
import { listDrReplicas } from '@/api/dr'

export default {
  mixins: [listRefreshMixin(['fetchData'])],
  name: 'DrReplicaTab',
  components: {
    DrStatusPill
  },
  props: {
    planId: {
      type: String,
      required: true
    }
  },
  data () {
    return {
      loading: false,
      replicas: [],
      columns: [
        { key: 'id', title: this.$t('label.id'), dataIndex: 'id' },
        { key: 'targetvmname', title: this.$t('label.dr.target.vm'), dataIndex: 'targetvmname' },
        { key: 'state', title: this.$t('label.state'), dataIndex: 'state' },
        { key: 'powerstate', title: this.$t('label.dr.power.state'), dataIndex: 'powerstate' },
        { key: 'hypervisortype', title: this.$t('label.hypervisor'), dataIndex: 'hypervisortype' },
        { key: 'activeside', title: this.$t('label.ftctl.active.side'), dataIndex: 'activeside' },
        { key: 'created', title: this.$t('label.created'), dataIndex: 'created' }
      ]
    }
  },
  watch: {
    planId () {
      this.fetchData()
    }
  },
  created () {
    this.fetchData()
  },
  methods: {
    fetchData () {
      const listRequest = this.listRequestToken('fetchData')
      if (!this.planId || !('listDrReplicas' in this.$store.getters.apis)) {
        this.replicas = []
        return
      }
      this.loading = !listRequest.loaded
      return listDrReplicas({ planid: this.planId }).then(result => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return
        this.replicas = result.items || []
      }).catch(error => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return
        listRequest.failed = true
        this.listRefreshFailed = true
        if (!listRequest.loaded) this.$notifyError(error)
      }).finally(() => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return

        this.loading = false
      })
    }
  }
}
</script>

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
      :dataSource="restorePoints"
      :rowKey="record => record.id"
      :pagination="{ pageSize: 10 }">
      <template #bodyCell="{ column, text }">
        <template v-if="column.key === 'state'">
          <dr-status-pill :status="text" />
        </template>
        <template v-else-if="column.key === 'targetreadyrposeconds' || column.key === 'sourcerposeconds'">
          {{ formatSeconds(text) }}
        </template>
      </template>
    </a-table>
  </a-spin>
</template>

<script>
import { listRefreshMixin } from '@/utils/listRefreshMixin'
import DrStatusPill from '@/components/dr/DrStatusPill.vue'
import { listDrRestorePoints } from '@/api/dr'

export default {
  mixins: [listRefreshMixin(['fetchData'])],
  name: 'DrRestorePointsTab',
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
      restorePoints: [],
      columns: [
        { key: 'id', title: this.$t('label.id'), dataIndex: 'id' },
        { key: 'state', title: this.$t('label.state'), dataIndex: 'state' },
        { key: 'restorepointtype', title: this.$t('label.dr.restore.point.type'), dataIndex: 'restorepointtype' },
        { key: 'consistencylevel', title: this.$t('label.dr.consistency'), dataIndex: 'consistencylevel' },
        { key: 'sourcecreated', title: this.$t('label.dr.source.created'), dataIndex: 'sourcecreated' },
        { key: 'targetreadyat', title: this.$t('label.dr.target.ready.at'), dataIndex: 'targetreadyat' },
        { key: 'sourcerposeconds', title: this.$t('label.dr.source.rpo'), dataIndex: 'sourcerposeconds' },
        { key: 'targetreadyrposeconds', title: this.$t('label.dr.target.rpo'), dataIndex: 'targetreadyrposeconds' }
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
      if (!this.planId || !('listDrRestorePoints' in this.$store.getters.apis)) {
        this.restorePoints = []
        return
      }
      this.loading = !listRequest.loaded
      return listDrRestorePoints({ planid: this.planId }).then(result => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return
        this.restorePoints = result.items || []
      }).catch(error => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return
        listRequest.failed = true
        this.listRefreshFailed = true
        if (!listRequest.loaded) this.$notifyError(error)
      }).finally(() => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return

        this.loading = false
      })
    },
    formatSeconds (seconds) {
      const value = Number(seconds)
      if (!Number.isFinite(value)) {
        return '-'
      }
      if (value < 60) {
        return `${Math.round(value)}s`
      }
      if (value < 3600) {
        return `${Math.round(value / 60)}m`
      }
      if (value < 86400) {
        return `${Math.round(value / 3600)}h`
      }
      return `${Math.round(value / 86400)}d`
    }
  }
}
</script>

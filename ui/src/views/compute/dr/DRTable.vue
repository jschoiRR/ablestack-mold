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
  <a-alert type="info" :message="$t('message.info.for.dr')" showIcon />
  <br>
  <a-spin :spinning="loading">
    <a-table
      size="small"
      :columns="drColumns"
      :dataSource="this.drCluster"
      :rowKey="item => item.id"
      :pagination="false"
      :expandRowByClick="true">
      <template #expandedRowRender="{ record }">
        <a-table
          style="margin: 10px 0;"
          :columns="innerColumns"
          :dataSource="this.volList"
          :pagination="false"
          :bordered="true"
          :rowKey="record.id">
          <template #bodyCell="{ column, text }">
            <template v-if="column.key === 'state'">
              <status :text="text ? text : ''" displayText />
            </template>
          </template>
        </a-table>
      </template>
      <template #bodyCell="{ column, record, text }">
        <template v-if="column.key === 'state'">
          <status :text="text ? text : ''" displayText />
        </template>
          <template v-if="column.key === 'name'">
          <router-link :to="{ path: '/disasterrecoverycluster/' + record.drId }" >{{ text }}</router-link>
        </template>
        <template v-if="column.key === 'actions'">
        <slot name="actions" :dr="record"/>
        </template>
      </template>
    </a-table>
  </a-spin>
</template>

<script>
import { listRefreshMixin } from '@/utils/listRefreshMixin'
import { getAPI } from '@/api'
import ResourceIcon from '@/components/view/ResourceIcon.vue'
import Status from '@/components/widgets/Status.vue'
export default {
  mixins: [listRefreshMixin(['getDrClusterList'])],
  name: 'DrTable',
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  components: {
    Status,
    ResourceIcon
  },
  inject: ['parentFetchData'],
  data () {
    return {
      loading: false,
      drColumns: [
        {
          key: 'mirroredVmname',
          title: this.$t('label.dr.mirrored.vm.name'),
          dataIndex: 'mirroredVm'
        },
        {
          key: 'state',
          title: this.$t('label.dr.mirrored.vm.status'),
          dataIndex: 'mirroredStatus'
        },
        {
          key: 'name',
          title: this.$t('label.dr.mirrored.cluster.name'),
          dataIndex: 'drName'
        },
        {
          key: 'actions',
          title: '',
          width: 100
        }
      ],
      drClusterList: [],
      drCluster: [],
      drVmName: '',
      combinedArray: [],
      volList: [],
      innerColumns: [
        {
          key: 'type',
          title: this.$t('label.dr.mirrored.volume.type'),
          dataIndex: 'mirroredVmVolType'
        },
        {
          key: 'state',
          title: this.$t('label.dr.mirrored.volume.status'),
          dataIndex: 'mirroredVmVolStatus'
        },
        {
          key: 'path',
          title: this.$t('label.dr.mirrored.volume.path'),
          dataIndex: 'mirroredVmVolPath'
        }
      ]
    }
  },
  watch: {
    resource: {
      deep: true,
      handler (newData, oldData) {
        if (newData !== oldData) {
          this.dataResource = newData
          this.vm = this.resource
          this.fetchData()
        }
      }
    }
  },
  items: {
    deep: true,
    handler (newItem) {
      if (newItem) {
        this.dataSource = newItem
      }
    }
  },
  '$i18n.global.locale' (to, from) {
    if (to !== from) {
      this.fetchData()
    }
  },
  created () {
    this.dataResource = this.resource
    this.vm = this.dataResource
    this.drVmName = this.dataResource.name
    this.getDrClusterList()
  },
  methods: {
    fetchData () {
      this.getDrClusterList()
    },
    getDrClusterList () {
      const request = this.listRequestToken('getDrClusterList')
      this.loading = !request.loaded
      return getAPI('getDisasterRecoveryClusterList').then(json => {
        if (!this.isListRequestCurrent('getDrClusterList', request)) return
        this.drCluster = []
        this.volList = []
        this.drClusterList = json.getdisasterrecoveryclusterlistresponse.disasterrecoverycluster || []
        for (const cluster of this.drClusterList) {
          const clusterId = cluster.id
          const clusterType = cluster.drclustertype
          for (const vm of cluster.drclustervmmap) {
            if (vm.drclustervmname === this.drVmName && clusterType !== 'primary') {
              if (this.drCluster.length === 0) {
                this.drCluster.push({ drName: vm.drclustername, drId: clusterId, drVmId: this.resource.id, mirroredVm: vm.drclustermirrorvmname, mirroredVmId: vm.drclustermirrorvmid, mirroredStatus: vm.drclustermirrorvmstatus })
              }
              this.volList.push({ mirroredVmVolType: vm.drclustermirrorvmvoltype, mirroredVmVolPath: vm.drclustermirrorvmvolpath, mirroredVmVolStatus: vm.drclustermirrorvmvolstatus })
            }
          }
        }
      }).catch(error => {
        if (!this.isListRequestCurrent('getDrClusterList', request)) return
        request.failed = true
        this.listRefreshFailed = true
        if (!request.loaded) this.$notifyError(error)
      }).finally(() => {
        if (!this.isListRequestCurrent('getDrClusterList', request)) return
        this.loading = false
      })
    }
  }
}
</script>

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
  <a-table
    class="table"
    size="small"
    :columns="columns"
    :dataSource="hostAgents"
    :rowKey="item => item.id"
    :pagination="false" >
    <template #name="{ text, record }">
      <router-link v-if="record.type === 'Routing'" :to="{ path: '/host/' + record.id }">{{ text }}</router-link>
      <router-link v-else :to="{ path: '/systemvm/' + record.virtualmachineid }">{{ text }}</router-link>
    </template>
  </a-table>
</template>

<script>
import { listRefreshMixin } from '@/utils/listRefreshMixin'

import { getAPI } from '@/api'
import Status from '@/components/widgets/Status'

export default {
  mixins: [listRefreshMixin(['fetchData'])],
  name: 'ConnectedAgentsTab',
  components: {
    Status
  },
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  data () {
    return {
      hostAgents: [],
      columns: [
        {
          title: this.$t('label.name'),
          dataIndex: 'name',
          slots: { customRender: 'name' }
        },
        {
          title: this.$t('label.ipaddress'),
          dataIndex: 'ipaddress'
        },
        {
          title: this.$t('label.state'),
          dataIndex: 'state'
        }
      ]
    }
  },
  created () {
    this.fetchData()
  },
  watch: {
    resource: function (newItem) {
      this.fetchData()
    }
  },
  methods: {
    fetchData () {
      const listRequest = this.listRequestToken('fetchData')
      return getAPI('listHosts', {
        listall: true,
        managementserverid: this.resource.id
      }).then(json => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return
        this.hostAgents = []
        this.hostAgents = json.listhostsresponse.host || []
      }).catch(error => {
        if (!this.isListRequestCurrent('fetchData', listRequest)) return
        listRequest.failed = true
        this.listRefreshFailed = true
        if (!listRequest.loaded) this.$notifyError(error)
      })
    }
  }
}
</script>

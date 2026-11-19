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
  <div class="affinity-group-selection">
    {{ $t('message.select.affinity.groups') }}
    <a-alert v-if="error" type="error" show-icon :message="error.startsWith('message.') ? $t(error) : error">
      <template #description><a-button size="small" :loading="loading" @click="requestPage">{{ $t('label.retry') }}</a-button></template>
    </a-alert>
    <a-input-search
      style="width: 100%; margin: 10px 0"
      :placeholder="$t('label.search')"
      v-model:value="filter"
      @search="handleSearch" />
    <affinity-selection-summary :key="selectionScope" :ids="selectedRowKeys" :groups="items" @change="onSelectChange" />
    <a-table
      :loading="loading"
      :columns="columns"
      :dataSource="items"
      :rowKey="record => record.id"
      :pagination="false"
      :rowSelection="rowSelection"
      size="middle"
      :scroll="{ x: 640, y: 225 }"
    >
      <template #bodyCell="{ column, record }">
        <AffinityGroupPolicy v-if="column.dataIndex === 'type'" :type="record.type" compact />
        <template v-else>{{ record[column.dataIndex] }}</template>
      </template>
    </a-table>

    <div style="display: block; text-align: right;">
      <a-pagination
        size="small"
        :current="options.page"
        :pageSize="options.pageSize"
        :total="rowCount"
        :showTotal="total => `${$t('label.total')} ${total} ${$t('label.items')}`"
        :pageSizeOptions="['10', '20', '40', '80', '100', '200']"
        @change="onChangePage"
        showSizeChanger>
        <template #buildOptionText="props">
          <span>{{ props.value }} / {{ $t('label.page') }}</span>
        </template>
      </a-pagination>
    </div>
  </div>
</template>

<script>
import { affinitySelectionIds, affinitySelectionScope } from '@/utils/affinityGroupSelection'
import AffinitySelectionSummary from './AffinitySelectionSummary.vue'
import AffinityGroupPolicy from '../AffinityGroupPolicy.vue'

export default {
  name: 'AffinityGroupSelection',
  components: { AffinitySelectionSummary, AffinityGroupPolicy },
  emits: ['select-affinity-group-item', 'handle-search-filter'],
  props: {
    owner: { type: Object, default: () => ({}) },
    error: { type: String, default: '' },
    items: {
      type: Array,
      default: () => []
    },
    rowCount: {
      type: Number,
      default: () => 0
    },
    value: {
      type: Array,
      default: () => []
    },
    loading: {
      type: Boolean,
      default: false
    },
    preFillContent: {
      type: Object,
      default: () => ({})
    },
    zoneId: {
      type: String,
      default: () => ''
    }
  },
  data () {
    return {
      filter: '',
      columns: [
        {
          dataIndex: 'name',
          title: this.$t('label.affinity.groups'),
          width: '30%'
        },
        {
          dataIndex: 'type',
          title: this.$t('label.type'),
          width: '30%'
        },
        {
          dataIndex: 'description',
          title: this.$t('label.description'),
          width: '40%'
        }
      ],
      selectedRowKeys: [],
      prefillApplied: false,
      selectionEdited: false,
      options: {
        page: 1,
        pageSize: 10,
        keyword: null
      }
    }
  },
  computed: {
    selectionScope () { return affinitySelectionScope(this.$store, this.owner, this.zoneId) },
    rowSelection () {
      return {
        type: 'checkbox',
        selectedRowKeys: this.selectedRowKeys,
        preserveSelectedRowKeys: true,
        onChange: this.onSelectChange
      }
    }
  },
  watch: {
    value: {
      immediate: true,
      handler (value) { this.selectedRowKeys = affinitySelectionIds(value) }
    },
    preFillContent: {
      immediate: true,
      deep: true,
      handler (content) {
        if (this.prefillApplied || this.selectionEdited || !content?.affinitygroupids?.length) return
        this.prefillApplied = true
        if (!this.selectedRowKeys.length) {
          this.selectedRowKeys = affinitySelectionIds(content.affinitygroupids)
          this.$emit('select-affinity-group-item', [...this.selectedRowKeys])
        }
      }
    },
    selectionScope (scope, previousScope) {
      const previous = JSON.parse(previousScope)
      const next = JSON.parse(scope)
      const initialZone = !previous[4] && next[4] && JSON.stringify(previous.slice(0, 4)) === JSON.stringify(next.slice(0, 4))
      if (!initialZone || this.selectionEdited) {
        this.prefillApplied = true
        this.onSelectChange([])
      }
      this.filter = ''
      this.options = { page: 1, pageSize: this.options.pageSize, keyword: null }
      this.requestPage()
    }
  },
  methods: {
    onSelectChange (ids) {
      this.selectionEdited = true
      this.selectedRowKeys = affinitySelectionIds(ids)
      this.$emit('select-affinity-group-item', [...this.selectedRowKeys])
    },
    requestPage () { this.$emit('handle-search-filter', { ...this.options }) },
    handleSearch (value) {
      this.filter = value
      this.options.page = 1
      this.options.keyword = this.filter
      this.requestPage()
    },
    onChangePage (page, pageSize) {
      this.options.page = pageSize !== this.options.pageSize ? 1 : page
      this.options.pageSize = pageSize
      this.requestPage()
    }
  }
}
</script>

<style lang="less" scoped>
  .affinity-group-selection { min-width: 0; }
  .affinity-group-selection :deep(.ant-table-cell) { overflow-wrap: anywhere; }
  // Override the common mobile/tablet table minimum only inside this selector.
  .affinity-group-selection :deep(.ant-table-body) { min-width: 0 !important; max-width: 100%; }
  .affinity-group-selection :deep(.ant-pagination) { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 4px; max-width: 100%; }
  .ant-table-wrapper {
    margin: 2rem 0;
  }
</style>

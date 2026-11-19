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
  <a-card class="autoscale-operational-summary" size="small" :title="$t('label.autoscale.operational.summary')">
    <div class="operation-highlights">
      <div class="operation-highlight"><div>{{ $t('label.state') }}</div><strong><status :text="resource.state" :display-text="true" /></strong></div>
      <div class="operation-highlight"><div>{{ $t('label.autoscale.members.range') }}</div><strong>{{ resource.availablevirtualmachinecount ?? '—' }}</strong><div>{{ $t('label.autoscale.range', { min: resource.minmembers ?? '—', max: resource.maxmembers ?? '—' }) }}</div></div>
      <div class="operation-highlight"><div>{{ $t('label.interval') }}</div><strong>{{ resource.interval ?? '—' }} <small>{{ $t('label.seconds') }}</small></strong></div>
    </div>
    <p>{{ $t('message.autoscale.count.meaning') }}</p>
    <dl v-if="summary" class="state-summary">
      <div v-for="(count, name) in summary" :key="name"><dt>{{ $t('label.autoscale.count.' + name) }}</dt><dd>{{ count }}</dd></div>
    </dl>
    <p v-else role="status">{{ $t('message.autoscale.count.unavailable') }}</p>
    <a-descriptions class="state-times" bordered size="small" :column="{ xs: 1, sm: 2 }">
      <a-descriptions-item :label="$t('label.autoscale.status.collected')">{{ time(resource.statuscollectedat) }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.autoscale.last.checked')">{{ time(resource.lastcheckedat) }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.autoscale.last.scaled')">{{ time(resource.lastscaledat) }}</a-descriptions-item>
      <a-descriptions-item v-if="resource.operationdeadline" :label="$t('label.autoscale.drain.deadline')">{{ time(resource.operationdeadline) }}</a-descriptions-item>
    </a-descriptions>
    <a-alert v-if="resource.state === 'DISABLED' && !resource.operationinprogress" type="info" show-icon :message="$t('message.autoscale.operational.disabled')" />
    <a-alert
v-if="resource.operationinprogress"
:type="notice.type"
show-icon
role="status"
      :message="$t(notice.message)"
      :description="$t('label.autoscale.operation.' + (resource.scalingaction || 'RECOVERY')) + ' · ' + $t('label.autoscale.phase.' + (resource.scalingphase || 'RECOVERY')) + '. ' + $t(notice.description)" />
    <p v-if="summary?.cleanup || summary?.error" role="status">{{ $t('message.autoscale.operational.inspect') }}</p>
  </a-card>
</template>
<script>
import Status from '@/components/widgets/Status'
import { autoScaleLifecycleNotice } from '@/utils/autoScaleLifecycle'
import { autoScaleStateSummary, autoScaleTime } from '@/utils/autoScaleOperations'
export default {
  name: 'AutoScaleOperationalSummary',
  components: { Status },
  props: { resource: { type: Object, required: true } },
  computed: {
    summary () { return autoScaleStateSummary(this.resource.memberstatecounts) },
    notice () { return autoScaleLifecycleNotice(this.resource) }
  },
  methods: { time (value) { return autoScaleTime(value, value => this.$toLocaleDate(value), key => this.$t(key)) } }
}
</script>
<style scoped lang="scss">
.autoscale-operational-summary {
  margin-bottom: 16px; color: var(--ui-text-primary); background: var(--ui-bg-surface); overflow-wrap: anywhere;
  dl { display: flex; gap: 16px; flex-wrap: wrap; margin: 12px 0; }
  dt { font-weight: 600; } dd { margin: 0; }
  .operation-highlights { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; margin-bottom: 16px; }
  .operation-highlight { padding: 14px; border: 1px solid var(--ui-border-strong); border-radius: 6px; background: var(--ui-bg-elevated); }
  .operation-highlight strong { display: block; font-size: 24px; margin: 8px 0 4px; }
  .operation-highlight small { font-size: 14px; font-weight: 400; }
  .state-summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(100px, 1fr)); gap: 8px; }
  .state-summary > div { padding: 10px; background: var(--ui-bg-elevated); border: 1px solid var(--ui-border-strong); border-radius: 4px; }
  .state-times { margin: 16px 0; }
  .state-summary dd { font-size: 20px; }
  .ant-alert { margin-bottom: 8px; }
}
</style>

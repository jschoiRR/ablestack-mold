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
  <a-card class="autoscale-evaluation" size="small" :title="$t('label.autoscale.evaluation')">
    <a-alert v-if="resource.state === 'DISABLED'" type="info" show-icon :message="$t('message.autoscale.evaluation.disabled')" />
    <a-alert v-if="resource.evaluationerror" type="warning" show-icon :message="$t('message.autoscale.evaluation.corrupt')" />
    <a-alert v-if="changed" type="info" show-icon :message="$t('message.autoscale.evaluation.changed')" />
    <p v-if="age !== null">{{ $t('label.autoscale.evaluation.age') }}: {{ age }} {{ $t('label.seconds') }}</p>
    <p v-if="!evaluation && !resource.evaluationerror" role="status">{{ $t('message.autoscale.evaluation.none') }}</p>
    <template v-if="evaluation">
      <a-alert
        :type="['WAITING', 'SCALEUP_UNCERTAIN'].includes(evaluation.status) ? 'warning' : 'info'"
        show-icon
        role="status"
        :message="statusLabel(evaluation.status)" />
      <a-descriptions class="evaluation-times" bordered size="small" :column="{ xs: 1, sm: 2 }">
        <a-descriptions-item :label="$t('label.autoscale.evaluated.at')">{{ formatTime(evaluation.evaluatedat) }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.autoscale.collected.at')">{{ formatTime(evaluation.collectedat) }}</a-descriptions-item>
        <a-descriptions-item v-if="evaluation.status === 'COOLDOWN'" :label="$t('label.autoscale.cooldown.until')">{{ formatTime(evaluation.cooldownuntil) }}</a-descriptions-item>
      </a-descriptions>
      <div v-for="policy in evaluation.policies || []" :key="policy.id" class="evaluation-policy">
        <strong>{{ policyName(policy) }} · {{ statusLabel(policy.status) }}</strong>
        <p v-if="policy.reason">{{ $t('message.autoscale.evaluation.' + policy.reason) }}</p>
        <p>{{ $t('label.duration') }}: {{ policy.duration }}</p>
        <a-table size="small" :columns="metricColumns" :data-source="policy.conditions || []" :row-key="(metric, index) => metric.id || index" :pagination="false" :scroll="{ x: 1000 }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'counter'">{{ counterLabel(record.countername) }}</template>
            <template v-else-if="column.key === 'value'">{{ formatValue(record.value) }} {{ record.unit === 'connections/VM' ? $t('label.autoscale.connections.per.vm') : record.unit }}</template>
            <template v-else-if="column.key === 'threshold'">{{ record.operator }} {{ record.threshold }}</template>
            <template v-else-if="column.key === 'collectedat'">{{ formatTime(record.collectedat) }}</template>
            <template v-else-if="column.key === 'result'">{{ record.reason ? $t('message.autoscale.evaluation.' + record.reason) : $t(record.matched ? 'label.autoscale.evaluation.MATCHED' : 'label.autoscale.evaluation.NOT_MATCHED') }}</template>
          </template>
        </a-table>
      </div>
      <a-alert v-if="hasNetworkMetric" type="info" show-icon :message="$t('message.autoscale.evaluation.network.scope')" />
      <a-alert v-if="hasMemoryMetric" type="info" show-icon :message="$t('message.autoscale.evaluation.memory.requirements')" />
    </template>
  </a-card>
</template>

<script>
import { autoScaleEvaluationValue, autoScaleCounterLabel } from '@/utils/autoScalePolicyEvaluation'
import { autoScaleTime, autoScaleEvaluationChanged } from '@/utils/autoScaleOperations'

export default {
  name: 'AutoScaleEvaluation',
  props: { resource: { type: Object, required: true } },
  computed: {
    metricColumns () {
      const titles = { counter: 'label.counter', value: 'label.autoscale.observed.value', threshold: 'label.threshold', samples: 'label.autoscale.samples', resources: 'label.autoscale.resources', collectedat: 'label.autoscale.collected.at', result: 'label.autoscale.metric.result' }
      return Object.entries(titles).map(([key, title]) => ({ key, dataIndex: key, title: this.$t(title) }))
    },
    evaluation () { return this.resource.evaluation || null },
    changed () { return autoScaleEvaluationChanged(this.resource) },
    age () { const time = Number(this.evaluation?.evaluatedat); return Number.isFinite(time) && time > 0 ? Math.max(0, Math.floor((Date.now() - time) / 1000)) : null },
    metrics () { return (this.evaluation?.policies || []).flatMap(policy => policy.conditions || []) },
    hasNetworkMetric () { return this.metrics.some(metric => metric.unit === 'Mbps/VM') },
    hasMemoryMetric () { return this.metrics.some(metric => metric.source === 'MEMORY') }
  },
  methods: {
    formatValue: autoScaleEvaluationValue,
    counterLabel (name) { return autoScaleCounterLabel(name, this.$t) },
    formatTime (time) { return autoScaleTime(time, value => this.$toLocaleDate(value), key => this.$t(key)) },
    statusLabel (status) { const key = 'label.autoscale.evaluation.' + status; const label = this.$t(key); return label !== key ? label : this.$t('label.autoscale.evaluation.unknown') + ' (' + (status || '—') + ')' },
    policyName (policy) {
      const configured = [...(this.resource.scaleuppolicies || []), ...(this.resource.scaledownpolicies || [])].find(item => item.id === policy.id)
      return configured?.name || this.$t('label.autoscale.operation.' + policy.action)
    }
  }
}
</script>

<style lang="scss" scoped>
.autoscale-evaluation {
  margin-bottom: 16px;
  overflow-wrap: anywhere;
  .evaluation-times { margin: 12px 0; }
  dt { font-weight: 600; }
  dd { margin: 0; }
  .evaluation-policy { margin: 16px 0; }
  ul { padding-left: 20px; }
  li { margin-bottom: 12px; }
  .ant-alert { margin-bottom: 8px; }
}
</style>

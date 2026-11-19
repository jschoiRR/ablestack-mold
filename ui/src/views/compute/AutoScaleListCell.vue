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
  <div class="autoscale-list-cell">
    <template v-if="column === 'availablevirtualmachinecount'">
      <strong>{{ resource.availablevirtualmachinecount ?? '—' }}</strong>
      <div>{{ $t('label.autoscale.range', { min: resource.minmembers ?? '—', max: resource.maxmembers ?? '—' }) }}</div>
    </template>
    <template v-else-if="column === 'associatednetworkname'">
      <router-link v-if="resource.associatednetworkid" :to="'/guestnetwork/' + resource.associatednetworkid">{{ resource.associatednetworkname || resource.associatednetworkid }}</router-link>
      <span v-else>{{ resource.associatednetworkname || '—' }}</span>
      <div>{{ resource.publicip || '—' }}:{{ resource.publicport ?? '—' }} → {{ resource.privateport ?? '—' }}</div>
    </template>
    <template v-else-if="column === 'zonename'">
      <span v-if="resource.infrastructurestatus === 'DENIED'">{{ $t('message.autoscale.source.denied') }}</span>
      <span v-else-if="resource.infrastructurestatus === 'FAILED'">{{ $t('message.autoscale.source.failed') }}</span>
      <span v-else>{{ resource.zonename || '—' }}</span>
    </template>
    <template v-else-if="column === 'account'">
      <span>{{ resource.account || resource.project || '—' }}</span>
    </template>
    <template v-else>
      <div>{{ time(resource.lastcheckedat) }}</div>
      <span v-if="resource.operationinprogress" role="status">{{ $t('label.autoscale.operation.' + (resource.scalingaction || 'RECOVERY')) }} · {{ $t('label.autoscale.phase.' + (resource.scalingphase || 'RECOVERY')) }}</span>
      <span v-else-if="resource.evaluationerror">{{ $t('message.autoscale.evaluation.corrupt') }}</span>
      <span v-else-if="resource.evaluation?.status">{{ evaluationLabel(resource.evaluation.status) }}</span>
    </template>
  </div>
</template>
<script>
import { autoScaleTime } from '@/utils/autoScaleOperations'
export default {
  name: 'AutoScaleListCell',
  props: { resource: { type: Object, required: true }, column: { type: String, required: true } },
  methods: {
    time (value) { return autoScaleTime(value, value => this.$toLocaleDate(value), key => this.$t(key)) },
    evaluationLabel (status) { const key = 'label.autoscale.evaluation.' + status; const label = this.$t(key); return label === key ? this.$t('label.autoscale.evaluation.unknown') + ' (' + status + ')' : label }
  }
}
</script>
<style scoped lang="less">
.autoscale-list-cell { min-width: 120px; color: var(--ui-text-primary); overflow-wrap: anywhere;
  > div + span, > div + div, strong + div { margin-top: 4px; display: block; }
}
</style>

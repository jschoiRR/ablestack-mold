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
  <a-modal
class="mold-dialog autoscale-dialog"
centered
wrapClassName="autoscale-creation-modal"
:visible="visible"
:title="$t('message.creating.autoscale.vmgroup')"
:maskClosable="false"
@cancel="$emit('close')"
width="680px">
    <a-alert show-icon :type="alertType" :message="$t('label.autoscale.creation.' + (operation?.status || 'unknown'))" :description="$t('message.autoscale.creation.' + (operation?.failureKind === 'storage' ? 'storage' : 'responsibility'))" />
    <p class="operation-id">{{ $t('label.autoscale.creation.operation') }}: {{ operation?.id }}</p>
    <div class="creation-stages">
      <a-steps direction="vertical" size="small">
        <a-step v-for="stage in operation?.stages || []" :key="stage.key" :title="$t(stageLabel(stage)) + ' (' + stage.key + ')'" :status="stepStatus(stage)">
          <template #description>
            <div>{{ $t('label.autoscale.creation.stage.' + stage.status) }}</div>
            <div v-if="stage.resourceId" class="resource-id">{{ $t('label.id') }}: {{ stage.resourceId }}</div>
            <div v-if="stage.jobId" class="resource-id">{{ $t('label.job.id') }}: {{ stage.jobId }}</div>
            <div v-if="stage.cleanup">{{ $t('label.autoscale.creation.cleanup.' + stage.cleanup) }}</div>
          </template>
        </a-step>
      </a-steps>
    </div>
    <template #footer><div class="form-action">
      <a-button @click="$emit('close')">{{ $t('label.close') }}</a-button>
      <a-button v-if="operation?.status === 'unknown'" type="primary" :loading="busy" :disabled="busy || !canQuery" @click="$emit('resume')">{{ $t('label.autoscale.creation.resume') }}</a-button>
      <a-button v-if="['failed', 'residual', 'unknown'].includes(operation?.status)" :disabled="busy || !canCleanup" @click="$emit('cleanup')">{{ $t('label.autoscale.creation.cleanup.retry') }}</a-button>
      <a-button v-if="operation?.status === 'cleaned'" type="primary" @click="$emit('forget')">{{ $t('label.autoscale.creation.new') }}</a-button>
      <a-button v-if="operation?.status === 'success'" type="primary" @click="$emit('open-group')">{{ $t('label.autoscale.creation.open') }}</a-button>
    </div></template>
  </a-modal>
</template>

<script>
import '@/style/components/view/autoscale.less'
import { creationStageLabel } from '@/utils/autoScaleCreation'
export default {
  props: { visible: Boolean, operation: Object, busy: Boolean, canQuery: Boolean, canCleanup: Boolean },
  emits: ['close', 'resume', 'cleanup', 'forget', 'open-group'],
  computed: {
    alertType () { return this.operation?.status === 'success' || this.operation?.status === 'cleaned' ? 'success' : ['failed', 'residual'].includes(this.operation?.status) ? 'error' : 'info' }
  },
  methods: {
    stageLabel: creationStageLabel,
    stepStatus (stage) { return stage.status === 'success' ? 'finish' : stage.status === 'failed' ? 'error' : stage.status === 'new' ? 'wait' : this.busy && ['pending', 'submitting'].includes(stage.status) ? 'process' : 'wait' }
  }
}
</script>

<style lang="less" scoped>
.creation-stages { flex: 1 1 auto; min-height: 0; max-height: 48vh; overflow-y: auto; margin: 16px 0; }
.operation-id, .resource-id { overflow-wrap: anywhere; }
</style>

<style lang="less">
body.dark-mode .autoscale-creation-modal {
  .ant-steps-item-title,
  .ant-steps-item-description,
  .operation-id,
  .resource-id { color: #d9e2ec !important; }
}
</style>

<style lang="less">
.autoscale-creation-modal {
  .ant-modal { top: 24px; padding-bottom: 24px; }
  .ant-modal-body { display: flex; flex-direction: column; max-height: calc(100vh - 125px); overflow-y: auto; }
  .ant-alert, .operation-id, .form-action { flex: 0 0 auto; }
  .form-action { display: flex; flex-wrap: wrap; gap: 8px; justify-content: flex-end; }
}
</style>

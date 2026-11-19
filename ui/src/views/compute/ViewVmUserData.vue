// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

<template>
  <MoldDialog class="vm-userdata-dialog" size="lg" :title="$t('label.userdata.vm.stored')" @cancel="$emit('close-action')">
    <UserDataContent :resource="target" tab="userdata.content" source-type="vm" />
    <template #footer><a-button ref="close" @click="$emit('close-action')">{{ $t('label.close') }}</a-button></template>
  </MoldDialog>
</template>
<script>
import MoldDialog from '@/components/view/MoldDialog'
import UserDataContent from '@/views/compute/UserDataContent'
export default {
  name: 'ViewVmUserData',
  components: { MoldDialog, UserDataContent },
  props: { resource: { type: Object, required: true } },
  emits: ['close-action'],
  data () { return { target: Object.freeze({ ...this.resource }) } },
  mounted () { this.$nextTick(() => this.$refs.close?.$el?.focus()) },
  watch: { resource (value) { this.target = Object.freeze({ ...value }) } }
}
</script>

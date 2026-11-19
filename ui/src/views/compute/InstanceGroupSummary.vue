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
  <template v-if="summary">
    <router-link v-if="field === 'virtualmachinecount'" :to="{ path: '/vmgroup/' + resource.id, query: { tab: 'members' } }">{{ summary.count }}</router-link>
    <span v-else class="group-member-states"><span v-for="[state, count] in summary.states" :key="state"><Status :text="state" /> {{ $t('label.' + state.toLowerCase()) }} {{ count }}</span><span v-if="!summary.states.length">—</span></span>
  </template>
  <a-tooltip v-else :title="$t('message.vm.group.members.summary.unavailable')">—</a-tooltip>
</template>
<script>
import Status from '@/components/widgets/Status'
import { vmGroupMemberSummary } from '@/utils/vmGroupMembers'
export default {
  name: 'InstanceGroupSummary',
  components: { Status },
  props: { resource: { type: Object, required: true }, field: { type: String, default: 'virtualmachinecount' } },
  computed: { summary () { return vmGroupMemberSummary(this.resource) } }
}
</script>
<style scoped>
.group-member-states { display: flex; flex-wrap: wrap; gap: 4px 12px; }
</style>

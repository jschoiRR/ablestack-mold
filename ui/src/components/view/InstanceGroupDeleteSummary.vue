<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements. See the NOTICE file
distributed with this work for additional information
regarding copyright ownership. The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the License for the
specific language governing permissions and limitations
under the License.
-->
<template>
  <section class="vm-group-delete-summary" :aria-label="$t('label.delete.instance.group')">
    <p>{{ $t('message.vm.group.delete.targets', { count: groups.length }) }}</p>
    <ul>
      <li v-for="group in groups" :key="group.id">
        <strong class="vm-group-delete-name">{{ group.name || $t('label.vm.group.unnamed') }}</strong>
        <div>{{ $t('label.id') }}: {{ group.id }}</div>
        <div v-if="group.project">{{ $t('label.project') }}: {{ group.project }}</div>
        <div v-else>{{ $t('label.account') }}: {{ group.account || $t('label.unknown') }}</div>
        <div>{{ $t('label.domain') }}: {{ group.domain || $t('label.unknown') }}</div>
      </li>
    </ul>
  </section>
</template>
<script>
export default {
  name: 'InstanceGroupDeleteSummary',
  props: { groups: { type: Array, default: () => [] } }
}
</script>
<style scoped>
.vm-group-delete-summary ul { max-height: 240px; overflow-y: auto; padding-left: 24px; }
.vm-group-delete-summary li { margin-bottom: 12px; overflow-wrap: anywhere; }
.vm-group-delete-name { white-space: pre-wrap; }
</style>

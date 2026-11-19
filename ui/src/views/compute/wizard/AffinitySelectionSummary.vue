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
  <section class="affinity-selection-summary" :aria-label="$t('label.affinity.selection')">
    <div class="selection-heading">
      <strong>{{ $t('label.affinity.selection') }}: {{ ids.length }}</strong>
      <a-button size="small" :disabled="disabled || !ids.length" @click="$emit('change', [])">{{ $t('label.affinity.clear.all') }}</a-button>
    </div>
    <p>{{ $t('message.affinity.selection.scope') }}</p>
    <ul v-if="ids.length">
      <li v-for="id in ids" :key="id">
        <a-button size="small" :disabled="disabled" :title="(names[id] || id) + ' (' + id + ')'" :aria-label="$t('label.affinity.remove.selection') + ': ' + (names[id] || id)" @click="$emit('change', ids.filter(value => value !== id))">
          {{ names[id] || id }} ×
        </a-button>
      </li>
    </ul>
  </section>
</template>

<script>
export default {
  name: 'AffinitySelectionSummary',
  emits: ['change'],
  props: {
    ids: { type: Array, default: () => [] },
    groups: { type: Array, default: () => [] },
    disabled: { type: Boolean, default: false }
  },
  data: () => ({ names: {} }),
  watch: {
    groups: {
      immediate: true,
      handler (groups) {
        for (const group of groups) if (group.id && group.name) this.names[group.id] = group.name
      }
    }
  }
}
</script>

<style lang="less" scoped>
.affinity-selection-summary {
  margin: 12px 0;
  .selection-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
  p { margin: 8px 0; }
  ul { display: flex; flex-wrap: wrap; gap: 6px; max-height: 120px; overflow-y: auto; padding: 0; margin: 0; list-style: none; }
  li, button { max-width: 100%; }
  li button { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}
</style>

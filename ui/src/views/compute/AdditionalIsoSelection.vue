<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file for additional information.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License. -->
<template>
  <div class="additional-iso-selection">
    <a-checkbox v-model:checked="enabled" :disabled="!supported" @change="toggle">
      {{ $t('label.additional.iso.connect') }}
    </a-checkbox>
    <div v-if="!supported">{{ $t('message.additional.iso.unsupported') }}</div>
    <template v-if="enabled">
      <a-form-item :label="$t('label.additional.iso')" :validateStatus="error ? 'error' : undefined" :help="error">
        <a-select
v-model:value="selected"
:loading="loading"
allowClear
showSearch
          optionFilterProp="label"
          :options="isos.map(iso => ({ value: iso.id, label: iso.name }))"
          @change="publish" />
      </a-form-item>
      <p>{{ $t('message.additional.iso.boot') }}</p>
    </template>
  </div>
</template>
<script>
import { getAPI } from '@/api'
export default {
  name: 'AdditionalIsoSelection',
  props: {
    zoneId: String,
    primaryId: String,
    supported: Boolean,
    owner: { type: Object, default: () => ({}) },
    projectId: String
  },
  emits: ['change'],
  data () {
    return { enabled: false, selected: undefined, isos: [], loading: false, error: '', generation: 0 }
  },
  watch: {
    zoneId: 'reset',
    primaryId: 'reset',
    supported: 'reset',
    projectId: 'reset',
    owner: { handler: 'reset', deep: true }
  },
  beforeUnmount () { this.generation++ },
  methods: {
    reset () {
      this.generation++
      this.enabled = false
      this.selected = undefined
      this.isos = []
      this.loading = false
      this.error = ''
      this.publish()
    },
    publish () {
      this.$emit('change', {
        enabled: this.enabled,
        ids: this.enabled && this.selected ? [this.selected] : [],
        valid: !this.enabled || (!this.loading && !this.error && this.isos.some(iso => iso.id === this.selected))
      })
    },
    async toggle () {
      if (!this.enabled) {
        this.reset()
        return
      }
      const generation = ++this.generation
      this.loading = true
      this.selected = undefined
      this.error = ''
      this.publish()
      try {
        const response = await getAPI('listIsos', {
          zoneid: this.zoneId,
          bootable: false,
          isready: true,
          isofilter: 'executable',
          listall: true,
          account: this.projectId ? undefined : this.owner.account,
          domainid: this.projectId ? undefined : this.owner.domainid,
          projectid: this.projectId || this.owner.projectid
        })
        if (generation !== this.generation) return
        this.isos = (response.listisosresponse.iso || []).filter(iso => iso.bootable === false && iso.id !== this.primaryId)
        if (!this.isos.length) this.error = this.$t('message.additional.iso.empty')
      } catch (e) {
        if (generation !== this.generation) return
        this.error = this.$t('message.additional.iso.load.failed')
      } finally {
        if (generation === this.generation) {
          this.loading = false
          this.publish()
        }
      }
    }
  }
}
</script>

<style scoped>
.additional-iso-selection {
  margin-top: 24px;
}
</style>

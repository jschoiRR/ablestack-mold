<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with this work
for additional information regarding copyright ownership. The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy at
http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or
agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
or implied. See the License for the specific language governing permissions and
limitations under the License. -->
<template>
  <div class="affinity-group-owner" :aria-busy="loading">
    <a-form-item :label="$t('label.owner.type')">
      <a-select :value="kind" :disabled="disabled" :aria-label="$t('label.owner.type')" @change="changeKind">
        <a-select-option value="account">{{ $t('label.account') }}</a-select-option>
        <a-select-option value="project">{{ $t('label.project') }}</a-select-option>
      </a-select>
    </a-form-item>
    <a-form-item :label="$t('label.domain')" required>
      <a-select :value="domainid" :disabled="disabled || domainsLoading" :loading="domainsLoading" show-search option-filter-prop="label" :aria-label="$t('label.domain')" @change="changeDomain">
        <a-select-option v-for="domain in domains" :key="domain.id" :value="domain.id" :label="domain.path || domain.name">{{ domain.path || domain.name }}</a-select-option>
      </a-select>
    </a-form-item>
    <a-form-item :label="$t('label.' + kind)" required>
      <a-select :value="selection" :disabled="disabled || loading || !!error" :loading="loading" show-search option-filter-prop="label" :aria-label="$t('label.' + kind)" @change="selectOwner">
        <a-select-option v-for="row in options" :key="row.id" :value="kind === 'account' ? row.name : row.id" :label="row.name">{{ row.name }}</a-select-option>
      </a-select>
    </a-form-item>
    <a-alert v-if="error || (!loading && !selection)" type="warning" show-icon :message="$t(error ? 'message.affinity.owner.load.failed' : 'message.affinity.create.owner.empty')">
      <template #description><a-button :disabled="disabled" size="small" @click="retry">{{ $t('label.retry') }}</a-button></template>
    </a-alert>
  </div>
</template>
<script>
import { getAPI } from '@/api'
import { affinityOwnerOptions } from '@/utils/affinityGroupCreation'

export default {
  name: 'AffinityGroupOwner',
  props: { initialOwner: { type: Object, default: () => ({}) }, disabled: Boolean },
  emits: ['change'],
  data () {
    const initial = this.initialOwner
    const project = this.$store.getters.project || {}
    const user = this.$store.getters.userInfo || {}
    const explicit = !!(initial.account || initial.projectid)
    return {
      kind: (explicit ? initial.projectid : project.id) ? 'project' : 'account',
      domainid: initial.domainid || project.domainid || user.domainid,
      selection: explicit ? initial.projectid || initial.account : project.id || user.account,
      domains: [],
      options: [],
      loading: true,
      domainsLoading: true,
      error: false
    }
  },
  created () { this.alive = true; this.version = 0; this.loadDomains() },
  beforeUnmount () { this.alive = false; this.version++ },
  methods: {
    emitOwner () {
      const ready = !this.loading && !this.error && !!this.domainid && !!this.selection
      const option = this.options.find(row => (this.kind === 'project' ? row.id : row.name) === this.selection)
      const domain = this.domains.find(row => row.id === this.domainid)
      this.$emit('change', { ready, owner: ready ? { domainid: this.domainid, ...(this.kind === 'project' ? { projectid: this.selection } : { account: this.selection }) } : {}, labels: ready ? { name: option?.name || this.selection, domain: domain?.path || domain?.name || this.domainid } : null })
    },
    async loadDomains () {
      const version = ++this.version
      this.domainsLoading = true; this.loading = true; this.error = false; this.emitOwner()
      try {
        const domains = await affinityOwnerOptions(getAPI, 'domain')
        if (!this.alive || version !== this.version) return
        this.domains = domains
        if (!domains.some(row => row.id === this.domainid)) { this.domainid = undefined; this.selection = undefined }
        this.domainsLoading = false
        await this.loadOwners()
      } catch (_) {
        if (!this.alive || version !== this.version) return
        this.error = true; this.domainsLoading = false; this.loading = false; this.emitOwner()
      }
    },
    async loadOwners () {
      const version = ++this.version
      const kind = this.kind; const domainid = this.domainid; const previous = this.selection
      this.options = []; this.selection = undefined; this.loading = true; this.error = false; this.emitOwner()
      try {
        if (!domainid) return
        const rows = await affinityOwnerOptions(getAPI, kind, domainid)
        if (!this.alive || version !== this.version) return
        this.options = rows.filter(row => row.domainid === domainid)
        if (this.options.some(row => (kind === 'account' ? row.name : row.id) === previous)) this.selection = previous
      } catch (_) {
        if (this.alive && version === this.version) this.error = true
      } finally {
        if (this.alive && version === this.version) { this.loading = false; this.emitOwner() }
      }
    },
    changeKind (kind) { this.kind = kind; this.selection = undefined; this.loadOwners() },
    changeDomain (domainid) { this.domainid = domainid; this.selection = undefined; this.loadOwners() },
    selectOwner (selection) { this.selection = selection; this.emitOwner() },
    retry () { return this.domains.length ? this.loadOwners() : this.loadDomains() }
  }
}
</script>

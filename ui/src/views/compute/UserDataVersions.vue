<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with this work
for additional information regarding copyright ownership. The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy at
http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or
agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License
for the specific language governing permissions and limitations. -->
<template>
  <section class="userdata-versions" :aria-busy="pending">
    <div class="detail-tab-toolbar"><a-button :loading="pending" :disabled="!allowed" @click="fetchData"><template #icon><reload-outlined /></template>{{ $t('label.refresh') }}</a-button></div>
    <a-alert v-if="error" type="error" show-icon role="status" :message="$t(error)" />
    <a-spin :spinning="pending && !records.length">
      <a-alert v-if="!pending && !error && !records.length" type="info" show-icon role="status" :message="$t('message.userdata.clone.history.empty')" />
      <a-descriptions v-for="(item, index) in records" :key="index" class="mold-dialog-section" :column="1" size="small" bordered>
        <a-descriptions-item :label="$t('label.userdata.clone.source')"><router-link :to="'/userdata/' + item.sourceid">{{ item.sourceid }}</router-link></a-descriptions-item>
        <a-descriptions-item :label="$t('label.userdata.clone.version')">{{ resource.name }} ({{ item.targetid }})</a-descriptions-item>
        <a-descriptions-item :label="$t('label.userdata.clone.difference')">{{ $t('message.userdata.clone.bytes', item) }}</a-descriptions-item>
        <a-descriptions-item :label="$t('label.userdataparams')">{{ resource.params || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-spin>
  </section>
</template>
<script>
import { getAPI } from '@/api'
import { readCloneSource, readCloneNotes, parseCloneRecord } from '@/utils/userDataVersions'
import { userDataDeleteError } from '@/utils/userDataDeletion'
export default {
  name: 'UserDataVersions',
  props: { resource: { type: Object, required: true }, tab: { type: String, default: '' }, loading: Boolean },
  data () { return { pending: false, records: [], error: '', version: 0, disposed: false } },
  computed: {
    allowed () { return ['listUserData', 'listAnnotations'].every(api => api in this.$store.getters.apis) },
    active () { return this.tab === 'userdata.versions' },
    scope () { return JSON.stringify([this.resource.id, this.resource.name, this.resource.account, this.resource.domainid, this.resource.projectid, this.$route.path, this.$store.getters.project?.id, this.$store.getters.listAllProjects, this.$store.getters.userInfo?.id, this.$store.state?.user?.token, this.allowed]) }
  },
  watch: {
    scope () { this.version++; this.pending = false; this.records = []; this.error = ''; this.fetchData() },
    active (value) { this.version++; this.pending = false; if (value) this.fetchData() },
    loading (value) { if (!value) this.fetchData() }
  },
  created () { this.fetchData() },
  beforeUnmount () { this.disposed = true; this.version++ },
  methods: {
    async fetchData () {
      if (!this.active || this.pending || this.disposed) return
      if (!this.allowed) { this.records = []; this.error = 'label.forbidden'; return }
      const version = ++this.version; const scope = this.scope
      const current = () => !this.disposed && this.active && this.version === version && this.scope === scope
      this.pending = true; this.error = ''
      try {
        const target = await readCloneSource(getAPI, Object.freeze({ ...this.resource }))
        if (!current()) return
        const notes = await readCloneNotes(getAPI, target.id, target.projectid)
        if (!current()) return
        const unique = new Map()
        for (const note of notes) { const record = parseCloneRecord(note.annotation, target.id); if (record) unique.set(JSON.stringify(record), record) }
        this.records = [...unique.values()]
      } catch (error) {
        if (!current()) return
        const code = userDataDeleteError(error).code
        this.records = []; this.error = [401, 403, 432, 531].includes(code) ? 'label.forbidden' : error.message?.startsWith('message.userdata.') ? error.message : 'message.userdata.clone.history.error'
      } finally { if (current()) this.pending = false }
    }
  }
}
</script>
<style scoped>
.userdata-versions { min-width: 0; overflow-wrap: anywhere; display: flex; flex-direction: column; gap: 16px; color: var(--ui-text-primary); }
.userdata-versions > .detail-tab-toolbar { margin-bottom: 4px; }
.userdata-versions :deep(.ant-descriptions + .ant-descriptions) { margin-top: 16px; }
.userdata-versions :deep(.ant-descriptions:first-child) { margin-top: 0; }
</style>

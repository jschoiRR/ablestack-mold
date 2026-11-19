<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with this
work for additional information regarding copyright ownership. The ASF
licenses this file under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License. You may
obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required
by applicable law or agreed to in writing, software distributed under the
License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS. -->
<template>
  <div class="affinity-summary">
    <p v-if="policy" class="affinity-secondary">{{ $t('message.affinity.policy.' + policy) }}</p>
    <div class="resource-detail-item">
      <div class="resource-detail-item__label">{{ $t('label.affinitymembercount') }}</div>
      <div class="resource-detail-item__value">{{ count }}</div>
    </div>
  </div>
</template>
<script>
import { affinityMemberCount } from '@/utils/affinityGroupMembers'
import { affinityPolicy } from '@/utils/affinityGroupPresentation'
export default {
  name: 'AffinityGroupSummary',
  props: { resource: { type: Object, required: true } },
  computed: { count () { return affinityMemberCount(this.resource) }, policy () { return affinityPolicy(this.resource.type) } }
}
</script>

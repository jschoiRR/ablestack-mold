<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with this
work for additional information regarding copyright ownership. The ASF
licenses this file under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License. You may
obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required
by applicable law or agreed to in writing, software distributed under the
License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS. -->
<template>
  <span class="affinity-policy" :class="{ 'affinity-policy-compact': compact }">
    <span class="affinity-policy-label" :title="type">{{ policy ? $t('label.affinity.type.' + policy) : type || $t('label.none') }}</span>
    <span v-if="!compact && policy" class="affinity-secondary">{{ $t('message.affinity.policy.' + policy) }}</span>
  </span>
</template>
<script>
import { affinityPolicy } from '@/utils/affinityGroupPresentation'
export default {
  name: 'AffinityGroupPolicy',
  props: { type: { type: String, default: '' }, compact: Boolean },
  computed: { policy () { return affinityPolicy(this.type) } }
}
</script>

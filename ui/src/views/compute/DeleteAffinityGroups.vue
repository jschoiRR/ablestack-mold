<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with this
work for additional information regarding copyright ownership. Licensed under
the Apache License, Version 2.0; obtain a copy at
http://www.apache.org/licenses/LICENSE-2.0 . Unless required by law, software
is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS. -->
<template>
  <DeleteAffinityGroup v-if="target" :key="target.id" :resource="target" queue-mode :progress="(index + 1) + ' / ' + targets.length" @deleted="next" @close-action="close" />
</template>
<script>
import DeleteAffinityGroup from './DeleteAffinityGroup.vue'
export default {
  name: 'DeleteAffinityGroups',
  components: { DeleteAffinityGroup },
  props: { resource: { type: Object, default: () => ({}) }, currentAction: { type: Object, default: () => ({}) } },
  emits: ['close-action', 'refresh-data'],
  data () { return { index: 0, targets: (this.currentAction.affinityTargets || [this.resource]).filter(row => row?.id).map(row => ({ ...row })) } },
  computed: { target () { return this.targets[this.index] } },
  methods: {
    next () {
      this.index++
      if (!this.target) this.close()
    },
    close () {
      if (this.index > 0) {
        if (this.$route.params?.id || this.$route.query?.dataView) this.$router.push('/affinitygroup')
        else this.$emit('refresh-data')
      }
      this.$emit('close-action')
    }
  }
}
</script>

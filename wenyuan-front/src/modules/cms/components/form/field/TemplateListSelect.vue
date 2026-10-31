<template>
  <n-select v-bind="$attrs" :options="options" label-field="label" value-field="value"/>
</template>
<script lang="ts" setup>
import {ref, watchEffect} from 'vue';
import {list} from '../../../api/template'

const props = defineProps({
  siteId: {type: String, default: '1', required: true},
  module: {type: String, required: true},
  prefix: {type: String, default: '', required: true},
})

const options = ref()

watchEffect(() => {
  const {siteId, module, prefix} = props
  if (siteId && module) {
    list({siteId: siteId, module: module, prefix: prefix}).then((res) => {
      options.value = res
    })
  }
})
</script>

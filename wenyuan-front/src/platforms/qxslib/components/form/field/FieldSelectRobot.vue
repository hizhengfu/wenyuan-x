<template>
  <n-select v-bind="$attrs" :options="options" label-field="name" :value-field="valueField"/>
</template>
<script lang="ts" setup>
import {onBeforeMount, ref} from "vue";
import {list} from '../../../api/robot'

const props = defineProps({
  valueField: {type: String, default: 'id'}
})

const options = ref()

onBeforeMount(() => {
  list({pageSize: -1}).then((res) => {
    if (res.data) {
      options.value = res.data.list || []
    }
  })
})
</script>

<template>
  <n-select v-bind="$attrs" :options="options" label-field="name" value-field="id"/>
</template>
<script lang="ts" setup>
import {onBeforeMount, ref} from 'vue';
import {list} from '../../api/third/thirdCulturalRelicBasic'

const options = ref()

onBeforeMount(() => {
  list({pageSize: -1}).then((res: any) => {
    if (res.data && res.data.list) {
      options.value = res.data.list
    }
  })
})
</script>

<template>
  <n-tree-select v-bind="$attrs" :options="options" label-field="name" key-field="id"/>
</template>
<script lang="ts" setup>
import {onBeforeMount, ref} from "vue";
import {tree} from '../../../api/permission'
import {transformToTreeFormat} from '@/utils'

const options = ref()

onBeforeMount(() => {
  tree({}).then((res) => {
    options.value = transformToTreeFormat(res || [], {pIdKey: 'parentId'})
  })
})
</script>

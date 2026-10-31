<template>
  <n-tree-select v-bind="$attrs" :options="options" label-field="name" key-field="id"/>
</template>
<script lang="ts" setup>
import {onBeforeMount, ref} from 'vue';
import {list} from '../../../api/menu'
import {transformToTreeFormat} from '@/utils'

const props = defineProps({
  status: {type: String, default: '0'},
  smartControlId: {type: String}
})

const options = ref()

onBeforeMount(() => {
  const {status, smartControlId} = props
  const params = {status: status, smartControlId: smartControlId, pageSize: 0}
  list(params).then((resp: any) => {
    if (resp.data && resp.data.list) {
      options.value = transformToTreeFormat(resp.data.list || [], {pIdKey: 'parentId'})
    }
  })
})
</script>

<template>
  <n-tree-select v-bind="$attrs" :options="options" label-field="name" key-field="id"/>
</template>
<script lang="ts" setup>
import {onBeforeMount, ref} from 'vue';
import {list} from '../../../api/group'
import {transformToTreeFormat} from '@/utils'

const options = ref()

onBeforeMount(() => {
  list({pageSize: -1}).then((resp: any) => {

    if (resp.data) {
      options.value = transformToTreeFormat(resp.data.list || [], {pIdKey: 'parentId'})
    }

  })
})
</script>

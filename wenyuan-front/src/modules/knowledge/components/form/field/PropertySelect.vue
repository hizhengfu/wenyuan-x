<template>
  <n-select v-bind="$attrs"
            :options="options"
            filterable
            clearable
            :loading="loading"
            remote
            @search="handleSearch"
            label-field="name"
            value-field="id"/>
</template>
<script lang="ts" setup>
import {onBeforeMount, onMounted, ref} from 'vue';
import {list} from '../../../api/node.ts'

const loading = ref(false)
const options = ref()

function handleSearch(query: string) {
  list({pageSize: 30, name: query}).then((res: any) => {
    if (res.data && res.data.list) {
      options.value = res.data.list
    }
  })
}

onMounted(() => {
  handleSearch('')
})

// onBeforeMount(() => {
//   list({pageSize: -1}).then((res: any) => {
//     if (res.data && res.data.list) {
//       options.value = res.data.list
//     }
//   })
// })
</script>

<template>
  <n-tree-select v-bind="$attrs" :options="options" label-field="name" key-field="id"/>
</template>
<script lang="ts" setup>
import {onBeforeMount, ref} from "vue";
import {list} from '../../../api/portaldefine'

const options = ref()

onBeforeMount(() => {
  list({pageSize: -1}).then((res) => {
    options.value = res.data.list || []
  })
})
</script>

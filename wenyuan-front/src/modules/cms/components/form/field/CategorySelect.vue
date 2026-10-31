<template>
  <n-tree-select v-bind="$attrs" :options="options" label-field="name" key-field="id"/>
</template>
<script lang="ts" setup>
import {ref, watchEffect} from "vue";
import {tree} from '../../../api/category'
import {transformToTreeFormat} from '@/utils'
import {useCmsStore} from '../../../store/cms'

const props = defineProps({
  siteId: {type: String},
  module: {type: String},
})
const cmsStore = useCmsStore()
const options = ref()

function loadData(siteId: string | null) {
  const {module} = props
  tree({siteId: siteId, module: module}).then((res) => {
    options.value = transformToTreeFormat(res.data || [], {pIdKey: 'parentId'})
  })
}

watchEffect(() => {
  const {siteId} = props
  if (siteId || cmsStore.siteId) {
    loadData(siteId || cmsStore.siteId)
  }
})
</script>

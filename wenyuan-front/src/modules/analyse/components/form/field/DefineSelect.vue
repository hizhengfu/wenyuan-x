<template>
  <n-select v-bind="$attrs"
            :options="options"
            :loading="loading"
            remote
            filterable
            @search="loadData"
            label-field="label"
            :value-field="valueField"/>
</template>
<script lang="ts" setup>
import {onBeforeMount, ref} from 'vue'
import {list} from '../../../api/analyseDefine'

const props = defineProps({
  valueField: {type: String, default: 'code'}
})

const options = ref()
/**
 * 加载状态
 * */
const loading = ref(false)

/**
 * 数据加载函数
 * */
function loadData(keywords?: string) {
  loading.value = true
  list({pageSize: 30, name: keywords}).then((resp: any) => {
    if (resp.data) {
      options.value = (resp.data.list || []).map((item: any) => {
        item.label = `${item.name}(${item.code})`
        return item
      })
    }
  }).finally(() => {
    loading.value = false
  })
}

onBeforeMount(() => {
  loadData()
})
</script>

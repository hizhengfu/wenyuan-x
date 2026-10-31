<template>
  <n-tree-select v-bind="$attrs"
                 filterable
                 v-model:value="innerValue"
                 :options="options"
                 :loading="loading"
                 @load="handleLoad"
                 label-field="name"
                 key-field="id"/>
</template>
<script lang="ts" setup>
import {computed, onBeforeMount, PropType, ref, watchEffect} from 'vue';
import {cascade} from '../../../api/area'
import {TreeSelectOption} from 'naive-ui'
import {transformToTreeFormat} from '@/utils'

const props = defineProps({
  value: {type: String as PropType<string | null>},
  parentId: {type: String, default: '0'}
})
/**
 * 加载状态
 * */
const loading = ref(false)
const options = ref()

const emit = defineEmits(['update:value'])
const innerValue = computed({
  get() {
    return props.value
  },
  set(value) {
    emit('update:value', value)
  }
})

function handleLoad(option: TreeSelectOption) {
  return new Promise<void>((resolve) => {
    loadData({id: option?.id, init: false, type: 4}).then((resp: any) => {
      option.children = resp || []
      resolve()
    })
  })
}

/**
 * 数据加载函数
 * */
function loadData(params: object) {
  loading.value = true
  return cascade(params).finally(() => {
    loading.value = false
  })
}


onBeforeMount(() => {
  loadData({id: props.value, init: true, type: 4}).then((resp: any) => {
    options.value = transformToTreeFormat(resp || [], {pIdKey: 'parentId'})
  })
})
</script>

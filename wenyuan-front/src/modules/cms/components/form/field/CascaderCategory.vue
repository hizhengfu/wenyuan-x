<template>
  <n-cascader
      v-bind="$attrs"
      v-model:value="innerValue"
      :options="options"
      :cascade="true"
      check-strategy="child"
      :show-path="true"
      :allow-checking-not-loaded="true"
      label-field="name"
      value-field="id"
      remote
      :on-load="handleLoad"
  />
</template>
<script setup lang="ts">
import {computed, onBeforeMount, PropType, ref} from 'vue'
import {CascaderOption} from 'naive-ui'
import {cascade} from '../../../api/category'
import {transformToTreeFormat} from '@/utils'

const props = defineProps({
  value: {type: String as PropType<string | null>},
  module: {type: String as PropType<string | null>},
})

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

/**
 * 数据加载函数
 * */
function loadData(id?: string | null, init: boolean = false) {
  return cascade({id: id, init: init, module: props.module}).then((resp: any) => {
    return (resp || [])
  })
}


function handleLoad(option: CascaderOption) {
  let id = option.id as string
  return new Promise<void>((resolve) => {
    loadData(id, false).then((d) => {
      option.children = d
    })
    resolve()
  })
}

onBeforeMount(() => {
  const {value} = props
  loadData(value, true).then((resp) => {
    options.value = transformToTreeFormat(resp, {pIdKey: 'parentId'})
  })
})
</script>

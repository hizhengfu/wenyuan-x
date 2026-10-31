<template>
  <n-cascader v-bind="$attrs" v-model:value="innerValue" :options="options" :cascade="true"
    :check-strategy="props.checkStrategy" :show-path="true" :allow-checking-not-loaded="true" label-field="name"
    value-field="id" remote :on-load="handleLoad" />
</template>
<script setup lang="ts">
import { onBeforeMount, ref, computed, PropType } from 'vue'
import { CascaderOption } from 'naive-ui'
import { cascade } from '../../../api/area'
import { transformToTreeFormat } from '@/utils'

const props = defineProps({
  value: { type: [String, Array] as PropType<string | string[] | null> },
  type: { type: Number, default: 4 },// 省直辖市 2， 市 3 ，区县 4
  checkStrategy: { type: String as any, default: 'child' }//指定勾选策略
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
function loadData(id?: string | string[] | null, init: boolean = false) {
  const { type } = props
  return cascade({ id: id, init: init, type: type }).then((resp: any) => {
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
  const { value } = props
  loadData(value, true).then((resp) => {
    options.value = transformToTreeFormat(resp, { pIdKey: 'parentId' })
  })
})
</script>

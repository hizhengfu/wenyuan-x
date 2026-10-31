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
import {onBeforeMount, ref, computed, PropType} from 'vue'
import {CascaderOption} from 'naive-ui'
import {tree} from '../../../api/device'
import {transformToTreeFormat} from '@/utils'

const props = defineProps({
  value: {type: String as PropType<string | null>},
  type: {type: String, default: 'device'}
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
function loadData(type: string, id?: string | null, nest: boolean = true) {
  return tree({id: id, type: type, nest: nest}).then((resp: any) => {
    if (resp.data) {
      return (resp.data || [])
    } else {
      return []
    }
  })
}


function handleLoad(option: CascaderOption) {
  let id = option.id as string
  const {type} = props
  return new Promise<void>((resolve) => {
    if (option.type === 'venue') {
      loadData('floor', id,).then((d) => {
        option.children = d.map((item: any) => {
          return Object.assign(item, {isLeaf: item.type === type})
        })
      })
    } else if (option.type === 'floor') {
      loadData('room', id,).then((d) => {
        option.children = d.map((item: any) => {
          return Object.assign(item, {isLeaf: item.type === type})
        })
      })
    } else if (option.type === 'room') {
      loadData('device', id,).then((d) => {
        option.children = d.map((item: any) => {
          return Object.assign(item, {isLeaf: item.type === type})
        })
      })
    }
    resolve()
  })
}

onBeforeMount(() => {
  const {value, type} = props
  loadData(type, value, false).then((resp) => {
    options.value = transformToTreeFormat(resp, {pIdKey: 'parentId'})
  })
})
</script>

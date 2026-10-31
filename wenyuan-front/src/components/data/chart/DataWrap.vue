<template>
  <div class="data-wrap-content" v-bind="$attrs">
    <slot :data="data"></slot>
  </div>
</template>
<script lang="ts" setup>
import {onActivated, onDeactivated, ref, watchEffect} from 'vue';
import request from '@/utils/request';
import {useEventBus} from '@vueuse/core';

/**
 * loadData 数据加载完成调用
 * refreshData 数据自动刷新调用 对未提供更新函数
 * */
const emits = defineEmits(['loadData', 'refreshData'])

const props = defineProps({
  /**
   * 数据自动更新
   * */
  dataAutoUpdate: {type: Boolean, default: false},
  /**
   * 查询参数
   * */
  params: {
    type: Object
  },
  /**
   * 数据获取api
   * */
  fetchData: {
    type: Function, default(params: object) {
      return request({
        url: 'analyse/list',
        params: params,
        method: 'get'
      })
    }
  },
})

/**
 * 组件激活状态，当组件失活后不执行定时数据更新
 * */
const activating = ref(true)
onActivated(() => {
  activating.value = true
})
onDeactivated(() => {
  activating.value = false
})


/**
 * 全屏定时器事件
 * */
const dataIntervalBus = useEventBus<Date>('SCREEN-INTERVAL')
dataIntervalBus.on((now: Date) => {
  if (activating.value) {
    dataWrapFetchData()

    /**
     * 触发数据更新事件
     * */
    emits('refreshData', now)
  }
})

/**
 * 组件内获取数据
 * */
function dataWrapFetchData() {
  const {dataAutoUpdate, fetchData, params} = props
  if (dataAutoUpdate && fetchData && params) {
    loading.value = true
    let p = params || {}
    fetchData(p).then((resp: any) => {
      if (resp.data) {
        data.value = resp.data
      }
      emits('loadData', data.value)
    }).finally(() => {
      loading.value = false
    })
  }
}

/**
 * 数据变量
 * */
const data = ref()
/**
 * 加载状态
 * */
const loading = ref(true)
watchEffect(() => {
  const {fetchData, params} = props
  if (fetchData && params) {
    dataWrapFetchData()
  }
})
</script>
<style scoped>
.data-wrap-content {
  /* height: 100%;
  width: 100%; */
}
</style>


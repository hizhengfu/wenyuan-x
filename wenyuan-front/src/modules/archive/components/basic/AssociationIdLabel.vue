<template>
  <span v-if="loading" class="loading-text">加载中...</span>
  <span v-else-if="displayText" class="display-text">{{ displayText }}</span>
  <span v-else class="empty-text">{{ emptyText }}</span>
</template>

<script lang="ts" setup>
import { ref, computed, watch, onMounted } from 'vue'

const props = defineProps({
  // 选中的ID
  value: { type: String, default: '' },
  // 数据获取函数
  fetchdata: { type: Function, default: () => {} },
  // 标签字段名
  labelField: { type: String, default: 'name' },
  // 额外参数
  params: { type: Object, default: () => ({}) },
  // 空值时显示的文本
  emptyText: { type: String, default: '暂无数据' },
  // 是否在值改变时自动刷新
  autoRefresh: { type: Boolean, default: true }
})

const options = ref<any[]>([])
const loading = ref(false)

// 计算显示文本
const displayText = computed(() => {
  if (!props.value || !options.value.length) return ''
  
  const option = options.value.find(item => item.id?.toString() === props.value.toString())
  return option ? option[props.labelField] : props.value
})

/**
 * 数据加载函数
 */
async function loadData() {
  if (!props.fetchdata || !props.value) {
    options.value = []
    return
  }
  
  loading.value = true
  try {
    const params = {
      pageSize: -1,
      ...props.params
    }
    
    const resp = await props.fetchdata(params)
    options.value = resp?.data?.list || []
  } catch (error) {
    console.error('加载数据失败:', error)
    options.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.value,
  () => {
    if (props.autoRefresh) {
      loadData()
    }
  },
  { immediate: true }
)

watch(
  () => [props.fetchdata, props.params],
  () => {
    if (props.autoRefresh && props.value) {
      loadData()
    }
  },
  { deep: true }
)

const refresh = () => {
  loadData()
}

defineExpose({
  refresh,
  loadData
})

onMounted(() => {
  if (props.value) {
    loadData()
  }
})
</script>

<style lang="scss" scoped>
.display-text {
  color: var(--text-color, #333);
  font-size: 14px;
  line-height: 1.5;
}

.loading-text {
  color: var(--text-color-3, #999);
  font-size: 14px;
  font-style: italic;
}

.empty-text {
  color: var(--text-color-3, #999);
  font-size: 14px;
  font-style: italic;
}
</style>
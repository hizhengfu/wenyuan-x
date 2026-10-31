<template>
  <n-select
    v-bind="$attrs"
    :options="options"
    :loading="loading"
    remote
    filterable
    clearable
    @clear="handleClear"
    @search="handleSearch"
    :label-field="labelField"
    value-field="id"
    :placeholder="loading ? '加载中...' : '请选择'"
    :reset-menu-on-options-change="false"
    @scroll="handleScroll"
  />
</template>

<script lang="ts" setup>
import { onBeforeMount, ref, computed } from 'vue'
import { basicList as list } from '../../../api/group'

const options:any = ref([])  
const loading = ref(false)
const currentPage = ref(1)  
const pageSize = ref(30)  
const hasMoreData = ref(true)  

let requestId = 0  

const labelField = computed(() => 'publishName')

// 加载数据方法
async function loadData(keywords?: string) {
  loading.value = true

  const currentRequestId = ++requestId

  const params: any = {
    pageNum: currentPage.value,
    pageSize: pageSize.value
  }

  // 添加搜索条件
  if (keywords && keywords.trim()) {
    params.publishName = keywords.trim()
  }

  try {
    let resp: any = await list(params)

    if (currentRequestId === requestId) {
      const newData = resp.data?.list || []

      const totalPages = resp.data?.pages || 0
      if (currentPage.value >= totalPages) {
        hasMoreData.value = false  
      }

      if (newData.length === 0) {
        hasMoreData.value = false  
      } else {
        options.value = [
          ...options.value,
          ...newData.filter((item:any) => !options.value.some((existingItem:any) => existingItem.id == item.id))  // 去重
        ]
      }
    }
  
  } finally {
    if (currentRequestId == requestId) {
      loading.value = false
    }
  }
}
const keyWords = ref('')
// 搜索处理方法
function handleSearch(keywords: string) {
  currentPage.value = 1  
  options.value = []  
  hasMoreData.value = true  
  loadData(keywords)  
   
  keyWords.value=keywords
}

// 清空处理方法
function handleClear() {
  currentPage.value = 1  
  options.value = []  
  hasMoreData.value = true  
  loadData()  
}

async function loadMore() {
  if (loading.value || !hasMoreData.value) return  
  currentPage.value += 1  
  await loadData(keyWords.value)  
}

function handleScroll(e: Event) {
  const target = e.target as HTMLElement
  if (!target) return

  const { scrollTop, scrollHeight, clientHeight } = target
  const threshold = 50  

  if (scrollHeight - scrollTop - clientHeight <= threshold) {
    loadMore()
  }
}

onBeforeMount(() => {
  loadData()  
})

</script>

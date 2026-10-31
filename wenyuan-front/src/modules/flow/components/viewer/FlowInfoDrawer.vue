<template>
  <n-drawer v-model:show="show" width="60%" placement="right" @after-enter="onDrawerEnter">
    <n-drawer-content :title="drawerTitle" closable>
      <FlowInfoPanel :data="data" :loading="loading">
        <template v-if="$slots.form" #form="{ data: flowData }">
          <slot name="form" :data="flowData" />
        </template>
      </FlowInfoPanel>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import FlowInfoPanel from './FlowInfoPanel.vue'

const props = defineProps<{
  fetch: (id: string) => Promise<any>
}>()

const show = ref(false)
const loading = ref(false)
const data = ref<any>(null)
const drawerTitle = ref('审批进度')
let pendingId = ''

function open(id: string, title?: string) {
  drawerTitle.value = title ? `审批进度 - ${title}` : '审批进度'
  data.value = null
  loading.value = true
  pendingId = id
  show.value = true
  // 数据请求在 onDrawerEnter 之后发出，确保 Drawer 动画完成、容器有尺寸
}

// Drawer 进入动画结束后再加载数据，避免 LogicFlow 在 0×0 容器中初始化
function onDrawerEnter() {
  if (!pendingId) return
  const id = pendingId
  pendingId = ''
  props.fetch(id)
    .then((resp) => {
      data.value = resp.data
    })
    .finally(() => {
      loading.value = false
    })
}

defineExpose({ open })
</script>

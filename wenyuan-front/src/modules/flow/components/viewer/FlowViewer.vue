<template>
  <div ref="containerRef" class="flow-viewer" />
</template>

<script setup lang="ts">
import { ref, onMounted, watch, onBeforeUnmount, nextTick } from 'vue'
import LogicFlow from '@logicflow/core'
import '@logicflow/core/dist/index.css'
import { registerFlowNodes, registerFlowEdge, NODE_TYPE_MAP } from '../designer/nodes'
import type { FlowDiagramData } from '../../typings/flow'

const props = defineProps<{
  diagramData: FlowDiagramData | null
}>()

const containerRef = ref<HTMLElement>()
let lf: InstanceType<typeof LogicFlow> | null = null

onMounted(async () => {
  // 等待 Vue DOM 更新 + 浏览器完成布局，确保容器有实际尺寸
  // 在 Drawer/Dialog 等动态容器内 v-if 挂载时，不等待会导致 LogicFlow 读到 0×0
  await nextTick()
  if (!containerRef.value) return

  lf = new LogicFlow({
    container: containerRef.value,
    isSilentMode: true,
    background: { color: '#F7F8FA' },
    grid: { size: 20, visible: false },
    edgeType: 'flow-edge',
  })

  registerFlowNodes(lf)
  registerFlowEdge(lf)

  if (props.diagramData) render(props.diagramData)
})

onBeforeUnmount(() => lf?.destroy?.())

watch(() => props.diagramData, (data) => {
  if (data && lf) render(data)
})

function render(data: FlowDiagramData) {
  if (!lf) return
  const { model, currentNodeKeys, completedNodeKeys, rejectedNodeKeys } = data

  // 先将状态合并到 properties，render 时节点 getShape 即可直接读取
  const statusMap: Record<string, string> = {}
  currentNodeKeys?.forEach(key => {
    const nodeId = model.nodes.find(n => n.properties.nodeKey === key)?.id
    if (nodeId) statusMap[nodeId] = 'current'
  })
  completedNodeKeys?.forEach(key => {
    const nodeId = model.nodes.find(n => n.properties.nodeKey === key)?.id
    if (nodeId) statusMap[nodeId] = 'completed'
  })
  rejectedNodeKeys?.forEach(key => {
    const nodeId = model.nodes.find(n => n.properties.nodeKey === key)?.id
    if (nodeId) statusMap[nodeId] = 'rejected'
  })

  const lfData = {
    nodes: model.nodes.map(n => ({
      id: n.id,
      type: NODE_TYPE_MAP[n.properties.nodeType || 'HANDLE'] || 'flow-handle',
      x: n.x,
      y: n.y,
      text: n.properties.name || '',
      properties: {
        ...n.properties,
        // 状态直接注入 properties，getShape 渲染时立即生效，无需 setProperties 二次触发
        ...(statusMap[n.id] ? { _status: statusMap[n.id] } : {}),
      },
    })),
    edges: model.edges.map(e => ({
      id: e.id,
      type: 'flow-edge',
      sourceNodeId: e.sourceNodeId,
      targetNodeId: e.targetNodeId,
      text: e.properties?.name || '',
      properties: e.properties,
    })),
  }

  lf.render(lfData)
  lf.fitView()
}
</script>

<style scoped>
.flow-viewer {
  width: 100%;
  height: 100%;
  min-height: 400px;
}
</style>

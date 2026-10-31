<template>
  <div class="flow-designer">
    <DesignerToolbar ref="toolbarRef" :lf="lf" :readonly="readonly" />

    <div class="designer-body">
      <NodePalette v-if="!readonly" :lf="lf" :containerRef="containerRef" />

      <div
        ref="containerRef"
        class="lf-container"
        tabindex="0"
        @keydown.delete.prevent="toolbarRef?.deleteSelected()"
        @keydown.backspace.prevent="toolbarRef?.deleteSelected()"
        @keydown.ctrl.a.prevent="toolbarRef?.selectAll()"
      />

      <div class="panel-toggle" @click="showPanel = !showPanel" :title="showPanel ? '收起属性面板' : '展开属性面板'">
        {{ showPanel ? '▶' : '◀' }}
      </div>

      <div class="property-panel-wrap" :class="{ collapsed: !showPanel }">
        <PropertyPanel
          v-if="showPanel"
          :selected="selectedElement"
          :all-nodes="allNodes"
          :flow-props="flowProps"
          :readonly="readonly"
          :current-def-key="currentDefKey"
          @update:node="onUpdateNodeProps"
          @update:edge="onUpdateEdgeProps"
          @update:flow-props="onUpdateFlowProps"
          @delete="toolbarRef?.deleteSelected()"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue';
import LogicFlow from '@logicflow/core';
import { Dagre } from '@logicflow/layout';
import { MiniMap, SelectionSelect, Snapshot } from '@logicflow/extension';
import '@logicflow/core/dist/index.css';
import '@logicflow/extension/lib/style/index.css';
import PropertyPanel from './PropertyPanel.vue';
import DesignerToolbar from './DesignerToolbar.vue';
import NodePalette from './NodePalette.vue';
import { registerFlowNodes, registerFlowEdge, NODE_TYPE_MAP } from './nodes';
import type { FlowModel, FlowNodeProperties, FlowEdgeProperties, FlowProperties } from '../../typings/flow';
import { provideAssigneeProvider } from '../../composables/useAssigneeProvider';

provideAssigneeProvider();

const props = defineProps<{
  modelValue?: string;
  title?: string;
  readonly?: boolean;
  /** 当前正在编辑的流程定义 key，透传给 PropertyPanel 用于子流程下拉过滤自身 */
  currentDefKey?: string;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', val: string): void;
  (e: 'save', val: string): void;
}>();

// ─── 实例与 UI 状态 ──────────────────────────────────────────

const containerRef = ref<HTMLElement | undefined>();
const toolbarRef = ref<InstanceType<typeof DesignerToolbar>>();
const lf = ref<any>(null);

const showPanel = ref(true);
const selectedElement = ref<any>(null);
const defaultFlowProps: Required<Pick<FlowProperties, 'allowInitiatorCancel' | 'autoPassRepeat' | 'autoPassEmptyAssignee'>> = {
  allowInitiatorCancel: true,
  autoPassRepeat: false,
  autoPassEmptyAssignee: false,
};
const normalizeFlowProps = (props?: FlowProperties | null): FlowProperties => ({
  ...defaultFlowProps,
  ...(props || {}),
  autoPassEmptyAssignee: props?.autoPassEmptyAssignee === true,
});
const flowProps = ref<FlowProperties>(normalizeFlowProps());

const allNodes = computed(() => {
  if (!lf.value) return [];
  return ((lf.value.getGraphData() as any).nodes || []).map((n: any) => ({
    nodeKey: n.properties?.nodeKey || n.id,
    name: n.properties?.name || n.type,
    nodeType: n.properties?.nodeType || '',
  }));
});

// ─── 初始化 ───────────────────────────────────────────────────

onMounted(() => {
  if (!containerRef.value) return;

  const instance = new LogicFlow({
    container: containerRef.value,
    plugins: [MiniMap, SelectionSelect, Snapshot, Dagre],
    background: { color: '#F7F8FA' },
    grid: { size: 20, visible: true },
    edgeType: 'flow-edge',
    isSilentMode: props.readonly || false,
    style: { outline: { hover: { stroke: '#409EFF' } } },
  });

  // 窄窗口下仍可将完整流程缩放到画布内。
  instance.setZoomMiniSize(0.05);

  registerFlowEdge(instance);
  registerFlowNodes(instance);

  instance.on('node:click', ({ data }: any) => {
    selectedElement.value = {
      type: 'node',
      id: data.id,
      properties: data.properties || {},
      nodeType: data.properties?.nodeType,
    };
    containerRef.value?.focus();
  });
  instance.on('edge:add', ({ data }: any) => {
    if (!data.properties?.conditionType) {
      instance.setProperties(data.id, { ...(data.properties || {}), conditionType: 'NONE' });
    }
  });
  instance.on('edge:click', ({ data }: any) => {
    selectedElement.value = { type: 'edge', id: data.id, properties: data.properties || {} };
    containerRef.value?.focus();
  });
  instance.on('blank:click', () => {
    selectedElement.value = null;
  });
  instance.on('node:delete', ({ data }: any) => {
    if (selectedElement.value?.id === data.id) selectedElement.value = null;
    emitModel(instance);
  });
  instance.on('edge:delete', ({ data }: any) => {
    if (selectedElement.value?.id === data.id) selectedElement.value = null;
    emitModel(instance);
  });
  instance.on('history:change', () => emitModel(instance));

  if (props.modelValue) {
    loadModel(instance, props.modelValue);
  } else {
    initDefaultFlow(instance);
  }

  lf.value = instance;
});

onBeforeUnmount(() => {
  lf.value?.destroy?.();
});

watch(() => props.readonly, (readonly) => {
  lf.value?.updateEditConfig({ isSilentMode: !!readonly });
});

watch(
  () => props.modelValue,
  (val) => {
    if (val && lf.value) loadModel(lf.value, val);
  },
);

// ─── 模型 ─────────────────────────────────────────────────────

function initDefaultFlow(instance: any) {
  flowProps.value = normalizeFlowProps();
  instance.render({
    nodes: [
      {
        id: 'init_start',
        type: 'flow-start',
        x: 160,
        y: 280,
        properties: { nodeType: 'START', name: '开始', nodeKey: 'start' },
      },
      {
        id: 'init_handle',
        type: 'flow-handle',
        x: 380,
        y: 280,
        properties: { nodeType: 'HANDLE', name: '经办', nodeKey: 'handle_1', assigneeType: 'INITIATOR' },
      },
      {
        id: 'init_end',
        type: 'flow-end',
        x: 600,
        y: 280,
        properties: { nodeType: 'END', name: '结束', nodeKey: 'end' },
      },
    ],
    edges: [
      { id: 'init_e1', type: 'flow-edge', sourceNodeId: 'init_start', targetNodeId: 'init_handle', properties: { conditionType: 'NONE' } },
      { id: 'init_e2', type: 'flow-edge', sourceNodeId: 'init_handle', targetNodeId: 'init_end', properties: { conditionType: 'NONE' } },
    ],
  });
  setTimeout(() => {
    instance.fitView();
    emitModel(instance);
  }, 100);
}

function loadModel(instance: any, jsonStr: string) {
  try {
    const model: FlowModel = JSON.parse(jsonStr);
    if (!model.nodes?.length) {
      initDefaultFlow(instance);
      return;
    }
    flowProps.value = normalizeFlowProps(model.properties);
    instance.render({
      nodes: model.nodes.map((n) => ({
        id: n.id,
        type: NODE_TYPE_MAP[n.properties.nodeType || 'HANDLE'] || 'flow-handle',
        x: n.x,
        y: n.y,
        properties: n.properties,
      })),
      edges: model.edges.map((e) => ({
        id: e.id,
        type: 'flow-edge',
        sourceNodeId: e.sourceNodeId,
        targetNodeId: e.targetNodeId,
        text: e.properties?.name || '',
        properties: e.properties,
      })),
    });
    setTimeout(() => {
      model.nodes.forEach((n) => {
        //@ts-ignore
        if (n.properties?._locked) {
          const m = instance.getNodeModelById(n.id) as any;
          if (m) m.draggable = false;
        }
      });
    }, 50);
  } catch (err) {
    console.error('加载流程图失败', err);
    initDefaultFlow(instance);
  }
}

function getModelJson(): string {
  if (!lf.value) return '{}';
  const graphData = lf.value.getGraphData() as any;
  const model: FlowModel = {
    nodes: (graphData.nodes || []).map((n: any) => ({
      id: n.id,
      type: n.type,
      x: n.x,
      y: n.y,
      properties: { ...n.properties, nodeKey: n.properties?.nodeKey || n.id },
    })),
    edges: (graphData.edges || []).map((e: any) => ({
      id: e.id,
      sourceNodeId: e.sourceNodeId,
      targetNodeId: e.targetNodeId,
      properties: {
        conditionType: 'NONE',
        ...e.properties,
        name: e.text?.value || e.properties?.name || '',
      },
    })),
    properties: normalizeFlowProps(flowProps.value),
  };
  return JSON.stringify(model);
}

function emitModel(instance?: any) {
  emit('update:modelValue', getModelJson());
}

// ─── 属性更新 ─────────────────────────────────────────────────

function onUpdateNodeProps(id: string, properties: FlowNodeProperties) {
  lf.value?.setProperties(id, properties);
  emitModel();
}

function onUpdateEdgeProps(id: string, properties: FlowEdgeProperties) {
  lf.value?.setProperties(id, properties);
  if (properties.name !== undefined) lf.value?.updateText(id, properties.name || '');
  emitModel();
}

function onUpdateFlowProps(fp: FlowProperties) {
  flowProps.value = fp;
  emitModel();
}

defineExpose({ getModelJson, loadModel: (json: string) => lf.value && loadModel(lf.value, json) });
</script>

<style scoped>
.flow-designer {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f7f8fa;
  position: relative;
  overflow: hidden;
}

.designer-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.lf-container {
  flex: 1;
  height: 100%;
  overflow: hidden;
  position: relative;
  outline: none;
}

.panel-toggle {
  width: 14px;
  flex-shrink: 0;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  border-right: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #909399;
  font-size: 10px;
  transition:
    background 0.15s,
    color 0.15s;
}

.panel-toggle:hover {
  background: #f0f2f5;
  color: #409eff;
}

.property-panel-wrap {
  width: 300px;
  flex-shrink: 0;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  overflow: hidden;
  transition: width 0.2s ease;
}

.property-panel-wrap.collapsed {
  width: 0;
  border-left-width: 0;
}
</style>

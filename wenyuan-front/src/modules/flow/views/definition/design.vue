<template>
  <div class="flow-design-page">
    <header class="design-header">
      <div class="design-identity">
        <n-button text aria-label="返回流程列表" @click="router.back()">
          <template #icon>
            <n-icon><HIcon icon="mdi:arrow-left" /></n-icon>
          </template>
        </n-button>
        <div class="design-name">
          <span class="def-name" :title="definition?.name">{{ definition?.name }}</span>
          <span v-if="definition?.key" class="def-key">{{ definition.key }}</span>
        </div>
        <span v-if="definition && definition.status !== 'DRAFT'" class="def-version">v{{ definition.version }}</span>
        <n-tag v-if="definition" size="small" :bordered="false" :type="statusTypes[definition.status || ''] as any">
          {{ statusLabels[definition.status || ''] }}
        </n-tag>
      </div>
      <div class="design-actions">
        <n-button-group>
          <n-upload v-if="!isReadonly" :show-file-list="false" accept="application/json,.json" :custom-request="onImport">
            <n-button>
              <template #icon><IconMdiImport /></template>
              导入
            </n-button>
          </n-upload>
          <n-button @click="onExport">
            <template #icon><IconMdiExport /></template>
            导出
          </n-button>
        </n-button-group>
        <template v-if="!isReadonly">
          <n-button @click="onSaveModel" :loading="saving">
            <template #icon><IconMdiContentSave /></template>
            保存
          </n-button>
          <n-button type="primary" @click="onPublish">
            <template #icon><IconMdiCheck /></template>
            发布
          </n-button>
        </template>
      </div>
    </header>
    <FlowDesigner
      v-model="modelData"
      :title="definition?.name"
      :current-def-key="definition?.key"
      ref="designerRef"
      :readonly="isReadonly"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import { confirm } from '@/utils';
import HIcon from '@/components/basic/DynamicIcon.vue';
import FlowDesigner from '../../components/designer/FlowDesigner.vue';
import {
  getDefinition,
  getDefinitionModel,
  saveDefinitionModel,
  publishDefinition,
  listDefinitions,
} from '../../api/definition';
import type { FlowDefinition, FlowModel } from '../../typings/flow';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const definition = ref<FlowDefinition | null>(null);
const modelData = ref<string>('');
const saving = ref(false);
const designerRef = ref<any>();
/** 同 key 下当前 ACTIVE 版本号；无则为 0（首次发布） */
const currentActiveVersion = ref<number>(0);

const statusTypes: Record<string, string> = { DRAFT: 'default', ACTIVE: 'success', DISABLED: 'error' };
const statusLabels: Record<string, string> = { DRAFT: '草稿', ACTIVE: '已发布', DISABLED: '已禁用' };

const isReadonly = computed(() => !!definition.value && definition.value.status !== 'DRAFT');

/** 发布预告：仅 DRAFT 时显示 */
const publishPreview = computed<string | null>(() => {
  const d = definition.value;
  if (!d || d.status !== 'DRAFT') return null;
  const cur = currentActiveVersion.value;
  if (cur === 0) {
    return '首次发布将成为 v1（线上版本）';
  }
  return `发布后将成为 v${cur + 1}，旧 v${cur} 自动禁用`;
});

async function loadData(id: string) {
  definition.value = null;
  modelData.value = '';
  currentActiveVersion.value = 0;
  const [defRes, modelRes] = await Promise.all([getDefinition(id), getDefinitionModel(id)]);
  definition.value = defRes.data?.data || defRes.data;
  const model = modelRes.data?.data || modelRes.data;
  if (model) {
    modelData.value = typeof model === 'string' ? model : JSON.stringify(model);
  }
  // 查同 key 下当前 ACTIVE 版本，用于发布预告
  if (definition.value?.key) {
    try {
      const res = await listDefinitions({ key: definition.value.key, status: 'ACTIVE', pageSize: 0 });
      // 防御：空列表时后端 Jackson NON_EMPTY 配置会剔除 list 字段，必须用 Array.isArray 兜底
      const page = res.data?.data ?? res.data;
      const raw = page?.list ?? page?.data;
      const list: FlowDefinition[] = Array.isArray(raw) ? raw : [];
      currentActiveVersion.value = Math.max(0, ...list.map((d) => d.version ?? 0));
    } catch {
      /* 忽略：发布预告是辅助信息，失败不影响主流程 */
    }
  }
}

watch(
  () => route.query.id as string,
  (id) => {
    if (id) loadData(id);
  },
  { immediate: true },
);

async function onSaveModel() {
  if (isReadonly.value) {
    message.warning('已发布或历史版本不可编辑，请复制为新草稿后修改');
    return;
  }
  const id = route.query.id as string;
  saving.value = true;
  try {
    const json = designerRef.value?.getModelJson();
    await saveDefinitionModel(id, json);
    message.success('保存成功');
  } finally {
    saving.value = false;
  }
}

function onExport() {
  const json = designerRef.value?.getModelJson() || '{}';
  const pretty = JSON.stringify(JSON.parse(json), null, 2);
  const blob = new Blob([pretty], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${definition.value?.name || 'flow'}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

function onImport({ file, onFinish, onError }: any) {
  const rawFile: File = file.file;
  if (!rawFile) return onError();
  const reader = new FileReader();
  reader.onload = () => {
    try {
      const text = String(reader.result || '');
      JSON.parse(text);
      designerRef.value?.loadModel(text);
      modelData.value = text;
      message.success('导入成功');
      onFinish();
    } catch (e) {
      message.error('导入失败：文件格式不正确');
      onError();
    }
  };
  reader.onerror = () => {
    message.error('读取文件失败');
    onError();
  };
  reader.readAsText(rawFile);
}

async function onPublish() {
  if (isReadonly.value) {
    message.warning('只有草稿流程可以发布');
    return;
  }
  const id = route.query.id as string;
  confirm({
    title: '发布确认',
    content: `确认发布流程「${definition.value?.name}」？${publishPreview.value || '发布后流程将生效'}，当前设计将锁定。`,
    onPositiveClick: async () => {
      const json = designerRef.value?.getModelJson();
      const errors = validateFlowModelJson(json);
      if (errors.length) {
        message.error(errors[0]);
        return false;
      }
      await saveDefinitionModel(id, json);
      await publishDefinition(id);
      message.success('发布成功');
      await loadData(id);
    },
  });
}

function validateFlowModelJson(json: string): string[] {
  let model: FlowModel;
  try {
    model = JSON.parse(json || '{}');
  } catch {
    return ['流程图 JSON 格式不正确'];
  }

  const nodes = model.nodes || [];
  const edges = model.edges || [];
  if (!nodes.length) return ['流程图不能为空'];

  const nodeIdSet = new Set(nodes.map((node) => node.id));
  const nodeKeys = new Map<string, string>();
  const idToKey = new Map<string, string>();
  for (const node of nodes) {
    const key = node.properties?.nodeKey || node.id;
    if (!key) return ['存在节点 Key 为空，请检查节点配置'];
    if (nodeKeys.has(key)) return [`节点 Key 重复：${key}`];
    nodeKeys.set(key, node.id);
    idToKey.set(node.id, key);

    if (node.properties?.nodeType === 'SUBPROCESS' && !node.properties?.subDefinitionKey) {
      return [`子流程节点「${node.properties?.name || key}」未选择子流程`];
    }
  }

  const starts = nodes.filter((node) => node.properties?.nodeType === 'START');
  const ends = nodes.filter((node) => node.properties?.nodeType === 'END');
  if (starts.length !== 1) return ['流程图必须有且仅有一个开始节点'];
  if (!ends.length) return ['流程图必须至少有一个结束节点'];

  const adjacency = new Map<string, string[]>();
  const reverse = new Map<string, string[]>();
  for (const edge of edges) {
    if (!nodeIdSet.has(edge.sourceNodeId)) return [`连线「${edge.id}」的源节点不存在`];
    if (!nodeIdSet.has(edge.targetNodeId)) return [`连线「${edge.id}」的目标节点不存在`];
    if (edge.properties?.conditionType === 'EXPRESSION' && !edge.properties?.condition?.trim()) {
      return [`表达式连线「${edge.properties?.name || edge.id}」未填写条件表达式`];
    }
    const source = idToKey.get(edge.sourceNodeId)!;
    const target = idToKey.get(edge.targetNodeId)!;
    adjacency.set(source, [...(adjacency.get(source) || []), target]);
    reverse.set(target, [...(reverse.get(target) || []), source]);
  }

  const startKey = idToKey.get(starts[0].id)!;
  if (!(adjacency.get(startKey) || []).length) return ['开始节点必须至少连接一个后续节点'];

  const reachable = traverse(startKey, adjacency);
  const unreachable = [...nodeKeys.keys()].filter((key) => !reachable.has(key));
  if (unreachable.length) return [`存在无法从开始节点到达的节点：${unreachable.join('、')}`];

  const canReachEnd = new Set<string>();
  for (const end of ends) {
    traverse(idToKey.get(end.id)!, reverse).forEach((key) => canReachEnd.add(key));
  }
  const deadEnds = [...nodeKeys.keys()].filter((key) => !canReachEnd.has(key));
  if (deadEnds.length) return [`存在无法到达结束节点的节点：${deadEnds.join('、')}`];

  return [];
}

function traverse(start: string, adjacency: Map<string, string[]>): Set<string> {
  const visited = new Set<string>();
  const queue = [start];
  while (queue.length) {
    const key = queue.shift()!;
    if (visited.has(key)) continue;
    visited.add(key);
    queue.push(...(adjacency.get(key) || []));
  }
  return visited;
}
</script>

<style scoped lang="scss">
.flow-design-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--n-color, #fff);
  border: 1px solid var(--n-border-color, #e4e7ed);
  border-radius: 4px;
}
.design-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 20px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--n-border-color, #e4e7ed);
}
.design-identity {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1 1 280px;
  min-width: 0;
}
.design-name {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.def-name {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.def-key {
  color: var(--n-text-color-3, #8a8d93);
  font: 12px ui-monospace, 'SF Mono', Monaco, Consolas, monospace;
  overflow-wrap: anywhere;
}
.def-version {
  font-size: 12px;
  color: var(--n-text-color-3, #8a8d93);
  white-space: nowrap;
}
.design-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}
.flow-design-page > .flow-designer {
  flex: 1;
  min-height: 0;
}
</style>

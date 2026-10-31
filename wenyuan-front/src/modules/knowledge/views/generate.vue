<template>
  <h-page-data-table
    ref="refDraftTable"
    title="AI 生成知识图谱"
    :fetch-data="listDrafts"
    :columns="draftColumns"
    :params="queryForm"
    :show-operate="false"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    :is-export="false"
    :show-query-reset-button="true"
  >
    <template #queryForm>
      <n-form-item label="文件名">
        <n-input v-model:value="queryForm.fileName" placeholder="请输入文件名" clearable />
      </n-form-item>
      <n-form-item label="状态">
        <h-dict-select
          v-model:value="queryForm.status"
          type="knowledge_draft_status"
          placeholder="请选择状态"
          clearable
        />
      </n-form-item>
      <n-form-item label="创建时间">
        <n-date-picker v-model:value="queryForm.dateRange" type="daterange" clearable style="width: 260px" />
      </n-form-item>
    </template>
    <template #toolbar-extra>
      <n-button type="primary" @click="showUploadModal = true">
        <template #icon><HIcon icon="mdi:upload" /></template>
        上传文件生成
      </n-button>
    </template>
    <!-- 上传文件弹窗 -->
    <n-modal v-model:show="showUploadModal" title="上传文件生成知识图谱" preset="card" style="width: 540px">
      <n-form :model="uploadForm" label-width="80px">
        <n-form-item label="选择文件" required>
          <n-upload :max="1" accept=".pdf,.doc,.docx,.txt,.md,.xls,.xlsx" @change="handleFileChange">
            <n-button>选择文件（PDF/Word/Excel/TXT）</n-button>
          </n-upload>
        </n-form-item>
        <n-form-item label="默认分类">
          <CategorySelect v-model:value="uploadForm.categoryId" placeholder="AI提取节点使用的默认分类" clearable />
        </n-form-item>
        <n-form-item label="附加提示">
          <n-input
            v-model:value="uploadForm.prompt"
            type="textarea"
            placeholder="可选：告诉 AI 重点提取什么信息，例如：重点提取人物、机构及其关联关系"
            :rows="3"
          />
        </n-form-item>
        <n-form-item label="立即提取">
          <n-space align="center">
            <n-switch v-model:value="uploadForm.generateNow" />
            <n-text depth="3" style="font-size: 12px">
              {{ uploadForm.generateNow ? '上传后立即调用 AI 提取知识图谱' : '仅上传保存，稍后在列表手动触发提取' }}
            </n-text>
          </n-space>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showUploadModal = false">取消</n-button>
          <n-button type="primary" :loading="uploadLoading" :disabled="!uploadForm.file" @click="handleUpload">
            {{ uploadForm.generateNow ? '上传并提取' : '仅上传' }}
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <h-page-form v-model:show="formModel">
      <template #header>
        <span>草稿详情 - {{ currentDraft?.draft?.fileName }}</span>
        <n-tag :type="statusTagType(currentDraft?.draft?.status)" size="small" style="margin-left: 8px">
          {{ currentDraft?.draft?.status }}
        </n-tag>
      </template>
      <template #form-action>
        <!-- DONE 状态：可丢弃 + 可确认入库 -->
        <template v-if="currentDraft?.draft?.status === 'DONE'">
          <n-button type="warning" class="mr-2" @click="handleDiscard">丢弃草稿</n-button>
          <n-button type="primary" class="mr-2" :loading="confirmLoading" @click="handleConfirm">
            <template #icon><HIcon icon="mdi:check" /></template>
            确认入库
          </n-button>
        </template>
        <!-- FAILED 状态：可重新处理 + 可丢弃 -->
        <template v-else-if="currentDraft?.draft?.status === 'FAILED'">
          <n-button type="primary" class="mr-2" @click="currentDraftId && handleTriggerGenerate(currentDraftId)">
            <template #icon><HIcon icon="mdi:refresh" /></template>
            重新处理
          </n-button>
          <n-button type="warning" class="mr-2" @click="handleDiscard">丢弃草稿</n-button>
        </template>
        <!-- PROCESSING：卡住时可强制重置（运行态手动解卡，无需重启） -->
        <template v-else-if="currentDraft?.draft?.status === 'PROCESSING'">
          <n-button type="warning" class="mr-2" @click="currentDraftId && handleForceReset(currentDraftId)">
            <template #icon><HIcon icon="mdi:restart-alert" /></template>
            强制重置
          </n-button>
        </template>
        <!-- CONFIRMED / DISCARDED：无操作按钮，只读查看 -->
      </template>

      <!-- 文件信息 -->
      <n-descriptions bordered :column="3" size="small" style="margin-bottom: 16px">
        <n-descriptions-item label="文件名">
          <a href="#" v-download="{ url: currentDraft?.draft?.filePath, fileName: currentDraft?.draft?.fileName }">
            {{ currentDraft?.draft?.fileName }}
          </a>
        </n-descriptions-item>
        <n-descriptions-item label="AI 模型">{{ currentDraft?.draft?.aiModel ?? '-' }}</n-descriptions-item>
      </n-descriptions>

      <!-- 错误信息（FAILED 时显示） -->
      <n-alert v-if="currentDraft?.draft?.status === 'FAILED'" type="error" style="margin-bottom: 16px">
        {{ currentDraft?.draft?.errorMsg ?? 'AI 提取失败，请重新触发' }}
      </n-alert>

      <!-- 处理中提示 -->
      <n-alert
        v-else-if="currentDraft?.draft?.status === 'PENDING' || currentDraft?.draft?.status === 'PROCESSING'"
        type="info"
        style="margin-bottom: 16px"
      >
        {{ currentDraft?.draft?.status === 'PROCESSING' ? 'AI 正在提取中，请稍候...' : '等待触发 AI 提取' }}
      </n-alert>

      <!-- 默认分类选择（DONE 时） -->
      <n-alert v-else-if="currentDraft?.draft?.status === 'DONE'" type="info" style="margin-bottom: 16px">
        <n-space align="center" :wrap="false">
          <span>默认分类（仅补充节点列表中未单独指定分类的节点）：</span>
          <CategorySelect v-model:value="defaultCategoryId" placeholder="选择默认分类" style="width: 200px" />
        </n-space>
      </n-alert>

      <n-tabs
        type="line"
        animated
        paneWrapperStyle="display: flex;flex: 1;"
        style="display: flex; flex: 1"
        pane-style="display: flex; flex: 1"
      >
        <!-- 节点列表（DONE 时可编辑，其他只读） -->
        <n-tab-pane name="nodes" :tab="`提取的节点（${editableNodes.length}）`">
          <h-page-data-table
            :data="editableNodes"
            :columns="isDraftEditable ? nodeColumns : nodeReadonlyColumns"
            :pagination="false"
            :show-header="false"
            :showToolbar="false"
            :show-operate="false"
            :is-add="isDraftEditable"
            :is-edit="false"
            :is-delete="false"
            :is-export="false"
            :bordered="false"
            @add="addNode"
          />
        </n-tab-pane>

        <!-- 关系列表（DONE 时可编辑，其他只读） -->
        <n-tab-pane name="relations" :tab="`提取的关系（${editableRelations.length}）`">
          <h-page-data-table
            :data="editableRelations"
            :columns="isDraftEditable ? relationColumns : relationReadonlyColumns"
            :pagination="false"
            :show-header="false"
            :show-operate="false"
            :showToolbar="false"
            :is-add="isDraftEditable"
            :is-edit="false"
            :is-delete="false"
            :is-export="false"
            :bordered="false"
            @add="addRelation"
          />
        </n-tab-pane>

        <!-- 图谱预览 -->
        <n-tab-pane name="preview" tab="图谱预览">
          <KnowledgeGraph
            v-if="currentDraftId && !['PENDING', 'PROCESSING'].includes(currentDraft?.draft?.status)"
            :show-legend="true"
            :show-download="true"
            :preview-nodes="editableNodes"
            :preview-relations="editableRelations"
          />
          <n-empty v-else description="AI 处理完成后可预览图谱" />
        </n-tab-pane>

        <!-- 图谱编辑（仅 DONE 状态可用）-->
        <n-tab-pane v-if="isDraftEditable" name="graph-edit" tab="图谱编辑" display-directive="show">
          <GraphEditor v-model:nodes="editableNodes" v-model:relations="editableRelations" />
        </n-tab-pane>
      </n-tabs>
    </h-page-form>
  </h-page-data-table>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted, h } from 'vue';
import { useMessage, useDialog, NButton, NInput, NSpace, NTag, NInputNumber, NSwitch, NAlert } from 'naive-ui';
import {
  generateFromFile,
  getDraft,
  listDrafts,
  confirmDraft,
  discardDraft,
  deleteDraft,
  triggerGenerate,
  resetDraft,
} from '../api/generate';
import CategorySelect from '../components/form/field/CategorySelect.vue';
import KnowledgeGraph from '../components/graph.vue';
import GraphEditor from '../components/GraphEditor.vue';
import { confirm } from '@/utils';

const message = useMessage();
const dialog = useDialog();

// ---------- 草稿列表 ----------
const refDraftTable = ref();
const currentDraftId = ref<string | null>(null);
const currentDraft = ref<any>(null);

const queryForm = ref<{ fileName?: string; status?: string; dateRange?: [number, number] | null }>({});

function statusTagType(status?: string) {
  const map: Record<string, any> = {
    PENDING: 'default',
    PROCESSING: 'info',
    DONE: 'success',
    FAILED: 'error',
    CONFIRMED: 'warning',
    DISCARDED: 'default',
  };
  return map[status ?? ''] ?? 'default';
}

// 可丢弃状态：PENDING / FAILED / DONE
const DISCARDABLE = new Set(['PENDING', 'FAILED', 'DONE']);
// 可删除状态：CONFIRMED / DISCARDED
const DELETABLE = new Set(['CONFIRMED', 'DISCARDED']);
// 可触发提取状态
const TRIGGERABLE = new Set(['PENDING', 'FAILED']);
// 可强制重置状态：卡住的"处理中"（运行态手动解卡）
const RESETTABLE = new Set(['PROCESSING']);

const draftColumns = [
  { title: '文件名', key: 'fileName' },
  { title: '文件类型', key: 'fileType', width: 80 },
  { title: 'AI模型', key: 'aiModel', width: 200, resizable: true, ellipsis: true },
  {
    title: '状态',
    key: 'status',
    width: 120,
    dictType: 'knowledge_draft_status',
    render: (row: any) => h(NTag, { type: statusTagType(row.status), size: 'small' }, () => row.status),
  },
  { title: '创建时间', key: 'createTime', columnType: 'dateTime', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    render: (row: any) =>
      h(NSpace, { size: 'small' }, () => [
        // 查看/编辑：所有状态都可查看；仅 DONE 时显示"查看/编辑"
        h(NButton, { size: 'small', type: 'primary', onClick: () => openDraft(row.id) }, () =>
          row.status === 'DONE' ? '查看/编辑' : '查看',
        ),
        // 开始提取：PENDING / FAILED
        TRIGGERABLE.has(row.status)
          ? h(NButton, { size: 'small', type: 'info', onClick: () => handleTriggerGenerate(row.id) }, () => '开始提取')
          : null,
        // 强制重置：PROCESSING（卡住时手动解卡）
        RESETTABLE.has(row.status)
          ? h(NButton, { size: 'small', type: 'warning', onClick: () => handleForceReset(row.id) }, () => '强制重置')
          : null,
        // 丢弃：PENDING / FAILED / DONE
        DISCARDABLE.has(row.status)
          ? h(NButton, { size: 'small', type: 'warning', onClick: () => quickDiscard(row.id) }, () => '丢弃')
          : null,
        // 删除：CONFIRMED / DISCARDED（草稿已完结，可物理删除）
        DELETABLE.has(row.status)
          ? h(NButton, { size: 'small', type: 'error', onClick: () => handleDelete(row.id) }, () => '删除')
          : null,
      ]),
  },
];

const formModel = ref(false);
async function openDraft(id: string) {
  formModel.value = true;
  currentDraftId.value = id;
  const res = await getDraft(id);
  currentDraft.value = res?.data;
  editableNodes.value = [...(currentDraft.value?.nodes || [])];
  editableRelations.value = [...(currentDraft.value?.relations || [])];
}

async function quickDiscard(id: string) {
  confirm({
    title: '确认丢弃',
    content: '丢弃后不可恢复，确定要丢弃这条草稿吗？',
    positiveText: '丢弃',
    onPositiveClick: async () => {
      await discardDraft(id);
      message.success('草稿已丢弃');
      refDraftTable.value?.handleRefresh();
    },
  });
}

async function handleTriggerGenerate(id: string) {
  try {
    await triggerGenerate(id);
    message.info('AI 提取已触发，请稍候...');
    refDraftTable.value?.handleRefresh();
    pollDraftStatus(id);
  } catch (e: any) {
    message.error('触发失败：' + (e?.message || '未知错误'));
  }
}

async function handleForceReset(id: string) {
  confirm({
    title: '确认强制重置',
    content: '将把该「处理中」草稿重置为失败状态，之后可重新处理或丢弃。若 AI 其实仍在运行，完成后会自动覆盖此次重置。确定继续？',
    positiveText: '强制重置',
    onPositiveClick: async () => {
      try {
        await resetDraft(id);
        message.success('已重置，可重新处理或丢弃');
        refDraftTable.value?.handleRefresh();
        if (currentDraftId.value === id) await openDraft(id);
      } catch (e: any) {
        message.error('重置失败：' + (e?.message || '未知错误'));
      }
    },
  });
}

// ---------- 上传文件 ----------
const showUploadModal = ref(false);
const uploadLoading = ref(false);
const uploadForm = ref<{ file: File | null; categoryId: string | null; prompt: string; generateNow: boolean }>({
  file: null,
  categoryId: null,
  prompt: '',
  generateNow: true,
});

function handleFileChange({ fileList }: any) {
  uploadForm.value.file = fileList[0]?.file ?? null;
}

async function handleUpload() {
  if (!uploadForm.value.file) return;
  uploadLoading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', uploadForm.value.file);
    if (uploadForm.value.categoryId) formData.append('categoryId', uploadForm.value.categoryId);
    if (uploadForm.value.prompt) formData.append('prompt', uploadForm.value.prompt);

    const { generateNow } = uploadForm.value;
    const res = await generateFromFile(formData, generateNow);
    const draft = res?.data;
    showUploadModal.value = false;
    uploadForm.value = { file: null, categoryId: null, prompt: '', generateNow: true };
    refDraftTable.value?.handleRefresh();
    if (generateNow) {
      message.info('文件已提交，AI 处理中，请稍候...');
      if (draft?.id) {
        await openDraft(draft.id);
        pollDraftStatus(draft.id);
      }
    } else {
      message.success('文件已上传，可在列表中手动触发 AI 提取');
    }
  } catch (e: any) {
    message.error('上传失败：' + (e?.message || '未知错误'));
  } finally {
    uploadLoading.value = false;
  }
}

// 轮询草稿状态，直到不再是 PENDING/PROCESSING
let pollTimer: ReturnType<typeof setTimeout> | null = null;
function pollDraftStatus(id: string, interval = 3000, maxTries = 40) {
  let tries = 0;
  const poll = async () => {
    if (tries++ >= maxTries) return;
    const res = await getDraft(id);
    const status = res?.data?.draft?.status;
    if (status === 'PENDING' || status === 'PROCESSING') {
      pollTimer = setTimeout(poll, interval);
    } else {
      // 处理完成，刷新当前草稿视图
      if (currentDraftId.value === id) {
        await openDraft(id);
      }
      refDraftTable.value?.handleRefresh();
      if (status === 'DONE') message.success('AI 提取完成，可以预览编辑了');
      if (status === 'FAILED') message.error('AI 提取失败，请查看草稿详情');
    }
  };
  pollTimer = setTimeout(poll, interval);
}

// 组件卸载时清理轮询
onUnmounted(() => {
  if (pollTimer) clearTimeout(pollTimer);
});

// ---------- 删除草稿 ----------
async function handleDelete(id: string) {
  confirm({
    title: '删除草稿',
    content: '将永久删除草稿记录及上传的文件，确认删除？',
    positiveText: '删除',
    onPositiveClick: async () => {
      await deleteDraft(id);
      message.success('草稿已删除');
      refDraftTable.value?.handleRefresh();
    },
  });
}

// ---------- 节点/关系编辑 ----------
const editableNodes = ref<any[]>([]);
const editableRelations = ref<any[]>([]);

// 仅 DONE 状态下可编辑
const isDraftEditable = computed(() => currentDraft.value?.draft?.status === 'DONE');

function addNode() {
  editableNodes.value.push({ name: '', categoryId: '', keywords: '', content: '', weight: 60, attrs: [] });
}

function removeNode(index: number) {
  editableNodes.value.splice(index, 1);
}

function addRelation() {
  editableRelations.value.push({ name: '', sourceName: '', targetName: '', direction: 'LEFT2RIGHT', content: '' });
}

function removeRelation(index: number) {
  editableRelations.value.splice(index, 1);
}

// 只读节点列（CONFIRMED / DISCARDED / PENDING / PROCESSING / FAILED）
const nodeReadonlyColumns = [
  { title: '名称', key: 'name' },
  { title: '关键字', key: 'keywords' },
  { title: '权重', key: 'weight', width: 80 },
  {
    title: '分类',
    key: 'categoryId',
    width: 140,
    render: (row: any) =>
      h(CategorySelect, { value: row.categoryId || null, disabled: true, size: 'small', placeholder: '未指定' }),
  },
  { title: '简介', key: 'content' },
  {
    title: '属性',
    key: 'attrs',
    width: 80,
    align: 'center',
    render: (row: any) => {
      const count = row.attrs?.length ?? 0;
      return count > 0 ? h(NTag, { size: 'small', type: 'info' }, () => `${count} 个`) : '-';
    },
  },
];

// 只读关系列
const relationReadonlyColumns = [
  { title: '关系名称', key: 'name' },
  { title: '来源节点', key: 'sourceName' },
  { title: '目标节点', key: 'targetName' },
  {
    title: '方向',
    key: 'direction',
    width: 80,
    render: (row: any) => {
      const map: Record<string, string> = { LEFT2RIGHT: '→', RIGHT2LEFT: '←', TWOWAY: '↔' };
      return map[row.direction] ?? row.direction;
    },
  },
];

// 可编辑节点列（DONE 状态）
const nodeColumns = [
  {
    title: '名称',
    key: 'name',
    render: (row: any, index: number) =>
      h(NInput, {
        value: row.name,
        placeholder: '实体名称',
        onUpdateValue: (v: string) => {
          editableNodes.value[index].name = v;
        },
      }),
  },
  {
    title: '关键字',
    key: 'keywords',
    render: (row: any, index: number) =>
      h(NInput, {
        value: row.keywords,
        placeholder: '关键字（逗号分隔）',
        onUpdateValue: (v: string) => {
          editableNodes.value[index].keywords = v;
        },
      }),
  },
  {
    title: '权重',
    key: 'weight',
    width: 120,
    render: (row: any, index: number) =>
      h(NInputNumber, {
        value: row.weight,
        min: 40,
        max: 100,
        //@ts-ignored
        onUpdateValue: (v: number) => {
          editableNodes.value[index].weight = v;
        },
      }),
  },
  {
    title: '分类',
    key: 'categoryId',
    width: 150,
    render: (row: any, index: number) =>
      h(CategorySelect, {
        value: row.categoryId || null,
        size: 'small',
        placeholder: '选择分类',
        clearable: true,
        onUpdateValue: (v: string | null) => {
          editableNodes.value[index].categoryId = v ?? '';
        },
      }),
  },
  {
    title: '简介',
    key: 'content',
    render: (row: any, index: number) =>
      h(NInput, {
        value: row.content,
        placeholder: '实体简介',
        onUpdateValue: (v: string) => {
          editableNodes.value[index].content = v;
        },
      }),
  },
  {
    title: '属性',
    key: 'attrs',
    width: 80,
    align: 'center',
    render: (row: any) => {
      const count = row.attrs?.length ?? 0;
      return count > 0 ? h(NTag, { size: 'small', type: 'info' }, () => `${count} 个`) : '-';
    },
  },
  {
    title: '操作',
    key: 'actions',
    width: 70,
    render: (_: any, index: number) =>
      h(NButton, { size: 'small', type: 'error', onClick: () => removeNode(index) }, () => '删除'),
  },
];

// 可编辑关系列（DONE 状态）
const relationColumns = [
  {
    title: '关系名称',
    key: 'name',
    render: (row: any, index: number) =>
      h(NInput, {
        value: row.name,
        placeholder: '关系名称',
        onUpdateValue: (v: string) => {
          editableRelations.value[index].name = v;
        },
      }),
  },
  {
    title: '来源节点',
    key: 'sourceName',
    render: (row: any, index: number) =>
      h(NInput, {
        value: row.sourceName,
        placeholder: '来源节点名称',
        onUpdateValue: (v: string) => {
          editableRelations.value[index].sourceName = v;
        },
      }),
  },
  {
    title: '目标节点',
    key: 'targetName',
    render: (row: any, index: number) =>
      h(NInput, {
        value: row.targetName,
        placeholder: '目标节点名称',
        onUpdateValue: (v: string) => {
          editableRelations.value[index].targetName = v;
        },
      }),
  },
  {
    title: '方向',
    key: 'direction',
    width: 120,
    render: (row: any) => {
      const map: Record<string, string> = { LEFT2RIGHT: '→', RIGHT2LEFT: '←', TWOWAY: '↔' };
      return map[row.direction] ?? row.direction;
    },
  },
  {
    title: '操作',
    key: 'actions',
    width: 70,
    render: (_: any, index: number) =>
      h(NButton, { size: 'small', type: 'error', onClick: () => removeRelation(index) }, () => '删除'),
  },
];

// ---------- 确认入库 ----------
const confirmLoading = ref(false);
const defaultCategoryId = ref<string | null>(null);

async function handleConfirm() {
  if (!currentDraftId.value) return;
  // 所有节点都没有单独指定分类，且未设置默认分类时才阻断
  const hasUnassigned = editableNodes.value.some((n) => !n.categoryId);
  if (hasUnassigned && !defaultCategoryId.value) {
    message.warning('有节点未指定分类，请在节点列表中逐个选择，或在上方设置一个默认分类');
    return;
  }
  dialog.warning({
    title: '确认入库',
    content: `将 ${editableNodes.value.length} 个节点、${editableRelations.value.length} 条关系写入知识图谱，操作不可撤销，确认继续？`,
    positiveText: '确认入库',
    onPositiveClick: async () => {
      confirmLoading.value = true;
      try {
        const res = await confirmDraft(
          currentDraftId.value!,
          JSON.stringify(editableNodes.value),
          JSON.stringify(editableRelations.value),
          defaultCategoryId.value ?? undefined,
        );
        const result = res?.data;
        message.success(`入库成功：${result?.nodesCreated} 个节点、${result?.relationsCreated} 条关系`);
        currentDraft.value.draft.status = 'CONFIRMED';
      } catch (e: any) {
        message.error('入库失败：' + (e?.message || '未知错误'));
      } finally {
        confirmLoading.value = false;
      }
    },
  });
}

async function handleDiscard() {
  if (!currentDraftId.value) return;
  dialog.warning({
    title: '确认丢弃',
    content: '丢弃后不可再次确认入库，确定要丢弃吗？',
    positiveText: '丢弃',
    onPositiveClick: async () => {
      await discardDraft(currentDraftId.value!);
      message.success('草稿已丢弃');
      // 更新详情页状态（无需重新请求）
      if (currentDraft.value?.draft) currentDraft.value.draft.status = 'DISCARDED';
      refDraftTable.value?.handleRefresh();
    },
  });
}
</script>

<style scoped>
.knowledge-generate {
  padding: 16px;
}
</style>

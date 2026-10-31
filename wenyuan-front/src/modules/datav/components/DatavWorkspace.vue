<!-- DataV 通用工作区只编排文档状态；画布、工具栏和组件库由独立基础组件承担。 -->
<template>
  <section ref="workspaceRef" class="datav-workspace">
    <DatavToolbar
      v-if="editable"
      :container="workspaceRef"
      :editing="editing"
      :project-id="projectId"
      :version="version"
      :needs-publish="needsPublish"
      :can-publish="!!publish"
      :gutter="workingDocument.canvas.gutter"
      :saving="saving"
      :publishing="publishing"
      @edit="beginEdit"
      @add="showLibrary = true"
      @reset="resetLayout"
      @cancel="cancelEdit"
      @save="saveDocument"
      @publish="publishDocument"
      @update:gutter="setGutter"
    >
      <template #start="scope"><slot name="toolbar-start" v-bind="scope" /></template>
      <template #end="scope"><slot name="toolbar-end" v-bind="scope" /></template>
    </DatavToolbar>

    <DatavCanvas v-model="workingDocument" :widget-registry="widgetRegistry" :editing="editing">
      <template #widget="scope">
        <slot name="widget" v-bind="scope">
          <component :is="scope.manifest?.component" v-if="scope.manifest" />
          <div v-else class="missing-widget">组件 {{ scope.widget.widgetKey }}@{{ scope.widget.widgetVersion }} 已不存在</div>
        </slot>
      </template>
    </DatavCanvas>

    <DatavWidgetLibrary
      v-if="editable"
      v-model:show="showLibrary"
      :widget-registry="widgetRegistry"
      :drawer-target="drawerTarget"
      @add="addWidget"
    >
      <slot name="library-footer" />
    </DatavWidgetLibrary>
  </section>
</template>

<script setup lang="ts">
import { inject, onBeforeUnmount, ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { SCREEN_REFRESH_SCHEDULER_KEY } from '@/components/basic/screen/refresh/refreshScheduler';
import { cloneDatavDocument, cloneDatavWidget } from '../core/document';
import type { DatavDocument, DatavProject, DatavWidgetManifest } from '../core/types';
import type { DatavWidgetRegistry } from '../registry/widgetRegistry';
import DatavCanvas from './DatavCanvas.vue';
import DatavToolbar from './DatavToolbar.vue';
import DatavWidgetLibrary from './DatavWidgetLibrary.vue';

type WorkspaceProps = {
  document: DatavDocument;
  widgetRegistry: DatavWidgetRegistry;
  createDefaultDocument: () => DatavDocument;
  saveDraft: (document: DatavDocument) => Promise<DatavProject | void>;
  publish?: () => Promise<DatavProject | void>;
  projectId?: string;
  version?: number;
  needsPublish?: boolean;
  editable?: boolean;
  drawerTarget?: string;
};

const props = withDefaults(defineProps<WorkspaceProps>(), {
  projectId: '', version: 0, needsPublish: false, editable: false, drawerTarget: 'body',
});
const emit = defineEmits<{
  'update:document': [document: DatavDocument];
  'update:editing': [editing: boolean];
}>();
const message = useMessage();
const workspaceRef = ref<HTMLElement>();
const workingDocument = ref(cloneDatavDocument(props.document));
const savedSnapshot = ref(cloneDatavDocument(props.document));
const editing = ref(false);
const saving = ref(false);
const publishing = ref(false);
const showLibrary = ref(false);
const refreshScheduler = inject(SCREEN_REFRESH_SCHEDULER_KEY, null);
const editingPauseReason = Symbol('DATAV-WORKSPACE-EDITING');

function beginEdit() {
  savedSnapshot.value = cloneDatavDocument(workingDocument.value);
  editing.value = true;
  emit('update:editing', true);
}

function cancelEdit() {
  replaceDocument(savedSnapshot.value, true);
  finishEditing();
}

async function saveDocument() {
  saving.value = true;
  try {
    // 平台主题编辑器可能与工作区同时修改，保存前合并业务外壳传入的最新主题。
    workingDocument.value.theme = { ...props.document.theme };
    const result = await props.saveDraft(cloneDatavDocument(workingDocument.value));
    const saved = result?.document || workingDocument.value;
    replaceDocument(saved, true);
    savedSnapshot.value = cloneDatavDocument(saved);
    finishEditing();
    message.success('草稿已保存，发布后展示端才会更新');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '大屏草稿保存失败');
  } finally {
    saving.value = false;
  }
}

async function publishDocument() {
  publishing.value = true;
  try {
    await props.publish?.();
    message.success('大屏已发布');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '大屏发布失败');
  } finally {
    publishing.value = false;
  }
}

function finishEditing() {
  editing.value = false;
  showLibrary.value = false;
  emit('update:editing', false);
}

function resetLayout() {
  workingDocument.value.widgets = props.createDefaultDocument().widgets.map(cloneDatavWidget);
  message.info('已恢复默认布局，点击保存后生效');
}

function setGutter(value: number | null) {
  workingDocument.value.canvas.gutter = Math.min(24, Math.max(0, value ?? 0));
}

function addWidget(widget: DatavWidgetManifest) {
  if (widget.singleton && workingDocument.value.widgets.some((item) => item.widgetKey === widget.key)) {
    message.warning(`${widget.title}只能添加一个`);
    return;
  }
  const canvas = workingDocument.value.canvas;
  const { w, h } = widget.defaultSize;
  const offset = workingDocument.value.widgets.length % 6;
  const zIndex = Math.max(0, ...workingDocument.value.widgets.map((item) => item.zIndex)) + 1;
  workingDocument.value.widgets.push({
    id: `${widget.key}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    widgetKey: widget.key,
    widgetVersion: widget.version,
    rect: {
      x: Math.min(canvas.columns - w, 8 + offset * 0.5),
      y: Math.min(canvas.rows - h, 6 + offset * 0.75),
      w,
      h,
    },
    zIndex,
    locked: false,
    props: {},
  });
  showLibrary.value = false;
}

function replaceDocument(document: DatavDocument, notify = false) {
  workingDocument.value = cloneDatavDocument(document);
  if (notify) emit('update:document', cloneDatavDocument(document));
}

watch(
  () => props.document,
  (document) => {
    if (editing.value) workingDocument.value.theme = { ...document.theme };
    else {
      replaceDocument(document);
      savedSnapshot.value = cloneDatavDocument(document);
    }
  },
  { deep: true },
);

watch(editing, (isEditing) => {
  if (isEditing) refreshScheduler?.pause(editingPauseReason);
  else refreshScheduler?.resume(editingPauseReason);
}, { flush: 'sync' });

onBeforeUnmount(() => refreshScheduler?.resume(editingPauseReason, false));
</script>

<style scoped>
.datav-workspace { position: absolute; inset: 0; z-index: 1; overflow: hidden; }
.missing-widget { display: flex; width: 100%; height: 100%; align-items: center; justify-content: center; color: #ffb6b6; background: rgb(45 10 10 / 65%); border: 1px dashed #d75c5c; }
</style>

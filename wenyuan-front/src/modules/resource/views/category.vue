<template>
  <ResourcePage show-title title="资源分类">
    <template #actions>
      <n-button :loading="loading" @click="loadTree()"><template #icon><n-icon><IconMdiRefresh /></n-icon></template>刷新</n-button>
      <n-button type="primary" @click="openCreate()"><template #icon><n-icon><IconMdiPlus /></n-icon></template>新增分类</n-button>
    </template>
  <div class="category-page">
    <n-card size="small" :bordered="false" class="category-shell">
      <div class="summary-row">
        <div class="summary-item">
          <span class="summary-value">{{ categoryStats.total }}</span>
          <span class="summary-label">全部分类</span>
        </div>
        <div class="summary-item">
          <span class="summary-value">{{ categoryStats.roots }}</span>
          <span class="summary-label">一级分类</span>
        </div>
        <div class="summary-item">
          <span class="summary-value">{{ categoryStats.resources }}</span>
          <span class="summary-label">已归类资源</span>
        </div>
      </div>

      <div class="category-workspace">
        <section class="tree-panel">
          <div class="panel-heading">
            <div>
              <div class="panel-title">分类目录</div>
            </div>
            <n-space size="small">
              <n-button text size="tiny" @click="expandAll">展开全部</n-button>
              <n-button text size="tiny" @click="collapseAll">收起</n-button>
            </n-space>
          </div>

          <n-input v-model:value="filter" placeholder="搜索分类名称" clearable size="small">
            <template #prefix><n-icon><IconMdiMagnify /></n-icon></template>
          </n-input>

          <n-spin :show="loading" class="tree-loading">
            <n-scrollbar class="tree-scrollbar">
              <n-tree
                v-if="treeData.length"
                block-line
                key-field="id"
                label-field="name"
                children-field="children"
                :data="treeData"
                :pattern="filter"
                :selected-keys="selectedKeys"
                :expanded-keys="expandedKeys"
                :render-label="renderLabel"
                :render-suffix="renderSuffix"
                :expand-on-click="false"
                @update:selected-keys="onSelect"
                @update:expanded-keys="expandedKeys = $event as string[]"
              />
              <n-empty v-else-if="!loading" description="还没有分类" class="tree-empty">
                <template #extra>
                  <n-button size="small" type="primary" @click="openCreate()">创建第一个分类</n-button>
                </template>
              </n-empty>
            </n-scrollbar>
          </n-spin>
        </section>

        <section class="detail-panel">
          <n-spin :show="detailLoading">
            <template v-if="selectedNode">
              <div class="detail-heading">
                <div class="detail-title-wrap">
                  <div class="detail-icon"><IconMdiFolderOutline /></div>
                  <div>
                    <div class="detail-title">{{ selectedNode.name }}</div>
                    <div class="detail-path">{{ selectedPath }}</div>
                  </div>
                </div>
                <n-space>
                  <n-button @click="openCreate(selectedNode)">
                    <template #icon><n-icon><IconMdiFolderPlusOutline /></n-icon></template>
                    添加下级
                  </n-button>
                  <n-button type="primary" @click="openEdit">
                    <template #icon><n-icon><IconMdiPencilOutline /></n-icon></template>
                    编辑
                  </n-button>
                </n-space>
              </div>

              <div class="detail-tags">
                <n-tag size="small" :bordered="false" type="info">第 {{ selectedNode.level || 1 }} 级</n-tag>
                <n-tag size="small" :bordered="false" :type="selectedNode.isLeaf ? 'default' : 'success'">
                  {{ selectedNode.isLeaf ? '末级分类' : '包含下级' }}
                </n-tag>
                <n-tag size="small" :bordered="false">{{ selectedNode.resourceCount || 0 }} 个资源</n-tag>
              </div>

              <n-descriptions :column="2" bordered label-placement="left" class="detail-descriptions">
                <n-descriptions-item label="分类名称">{{ selectedNode.name || '-' }}</n-descriptions-item>
                <n-descriptions-item label="分类编码">
                  <n-text code>{{ selectedNode.code || '-' }}</n-text>
                </n-descriptions-item>
                <n-descriptions-item label="上级分类">{{ selectedParentName }}</n-descriptions-item>
                <n-descriptions-item label="显示顺序">{{ selectedNode.sort ?? 0 }}</n-descriptions-item>
                <n-descriptions-item label="图标">{{ selectedNode.icon || '-' }}</n-descriptions-item>
                <n-descriptions-item label="更新时间">{{ formatDateTime(selectedNode.updateTime) }}</n-descriptions-item>
                <n-descriptions-item label="描述" :span="2">
                  {{ selectedNode.description || '暂无描述' }}
                </n-descriptions-item>
              </n-descriptions>

              <n-alert v-if="!canDeleteSelected" type="info" :bordered="false" class="delete-tip">
                {{ deleteDisabledReason }}
              </n-alert>
              <div class="detail-footer">
                <n-button type="error" secondary :disabled="!canDeleteSelected" @click="doDelete(selectedNode)">
                  <template #icon><n-icon><IconMdiDeleteOutline /></n-icon></template>
                  删除分类
                </n-button>
              </div>
            </template>

            <n-empty v-else class="detail-empty" description="从左侧选择一个分类查看详情">
              <template #icon><n-icon size="52"><IconMdiFolderSearchOutline /></n-icon></template>
              <template #extra>
                <n-button type="primary" @click="openCreate()">新建根分类</n-button>
              </template>
            </n-empty>
          </n-spin>
        </section>
      </div>
    </n-card>

    <n-modal
      v-model:show="formVisible"
      preset="card"
      :title="formTitle"
      style="width: min(620px, calc(100vw - 32px))"
      :mask-closable="false"
    >
      <n-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-placement="left"
        label-width="88"
        require-mark-placement="right-hanging"
      >
        <n-form-item label="分类名称" path="name">
          <n-input v-model:value="form.name" placeholder="例如：图片资源" clearable />
        </n-form-item>
        <n-form-item label="上级分类" path="parentId">
          <n-tree-select
            v-model:value="form.parentId"
            :options="parentOptions"
            key-field="id"
            label-field="name"
            children-field="children"
            default-expand-all
            filterable
            clearable
            placeholder="选择上级分类；不选择则为根分类"
          />
        </n-form-item>
        <n-form-item label="分类编码" path="code">
          <n-input v-model:value="form.code" placeholder="可留空，系统将按名称自动生成" clearable />
        </n-form-item>
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="图标" path="icon">
            <n-input v-model:value="form.icon" placeholder="Iconify 名称或 URL" clearable />
          </n-form-item-gi>
          <n-form-item-gi label="显示顺序" path="sort">
            <n-input-number v-model:value="form.sort" :min="0" style="width: 100%" />
          </n-form-item-gi>
        </n-grid>
        <n-form-item label="描述" path="description">
          <n-input
            v-model:value="form.description"
            type="textarea"
            placeholder="说明该分类适合收录哪些资源"
            :autosize="{ minRows: 3, maxRows: 6 }"
            maxlength="500"
            show-count
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="modal-footer">
          <n-button @click="formVisible = false">取消</n-button>
          <n-button type="primary" :loading="submitting" @click="doSubmit">
            {{ formMode === 'add' ? '创建分类' : '保存修改' }}
          </n-button>
        </div>
      </template>
    </n-modal>
  </div>
  </ResourcePage>
</template>

<script lang="ts" setup>
import ResourcePage from '../components/ResourcePage.vue';
import { computed, h, onMounted, ref, watch } from 'vue';
import {
  NButton,
  NDropdown,
  NSpace,
  type DropdownOption,
  type FormInst,
  type FormRules,
  type TreeOption,
  useDialog,
  useMessage,
} from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import { formatDateTime } from '@/utils';
import { add, del, edit, get, tree as fetchTree } from '../api/category';

type CategoryNode = TreeOption & {
  id: string;
  name: string;
  code?: string;
  parentId?: string;
  parentIds?: string;
  level?: number;
  sort?: number;
  icon?: string;
  description?: string;
  updateTime?: string;
  isLeaf?: boolean;
  resourceCount?: number;
  children?: CategoryNode[];
};

const ROOT_ID = '0';
const message = useMessage();
const dialog = useDialog();

const treeData = ref<CategoryNode[]>([]);
const filter = ref('');
const loading = ref(false);
const detailLoading = ref(false);
const selectedKeys = ref<string[]>([]);
const expandedKeys = ref<string[]>([]);
const selectedNode = ref<CategoryNode | null>(null);

const formVisible = ref(false);
const formMode = ref<'add' | 'edit'>('add');
const submitting = ref(false);
const formRef = ref<FormInst | null>(null);
const defaultForm = {
  id: '',
  name: '',
  code: '',
  parentId: ROOT_ID,
  icon: '',
  sort: 0,
  description: '',
};
const form = ref(cloneDeep(defaultForm));

const rules: FormRules = {
  name: { required: true, message: '请输入分类名称', trigger: ['blur', 'input'] },
};

const flatNodes = computed(() => flattenTree(treeData.value));
const nodeMap = computed(() => new Map(flatNodes.value.map(node => [node.id, node])));
const categoryStats = computed(() => ({
  total: flatNodes.value.length,
  roots: treeData.value.length,
  resources: flatNodes.value.reduce((sum, node) => sum + Number(node.resourceCount || 0), 0),
}));
const selectedParentName = computed(() => {
  const parentId = selectedNode.value?.parentId;
  return !parentId || parentId === ROOT_ID ? '根分类' : nodeMap.value.get(parentId)?.name || '未知分类';
});
const selectedPath = computed(() => {
  const node = selectedNode.value;
  if (!node) return '';
  const names: string[] = [node.name];
  let parentId = node.parentId;
  const visited = new Set<string>();
  while (parentId && parentId !== ROOT_ID && !visited.has(parentId)) {
    visited.add(parentId);
    const parent = nodeMap.value.get(parentId);
    if (!parent) break;
    names.unshift(parent.name);
    parentId = parent.parentId;
  }
  return names.join(' / ');
});
const canDeleteSelected = computed(() => {
  const node = selectedNode.value;
  return !!node && !node.children?.length && Number(node.resourceCount || 0) === 0;
});
const deleteDisabledReason = computed(() => {
  if (selectedNode.value?.children?.length) return '该分类包含下级分类，请先移动或删除下级分类。';
  if (Number(selectedNode.value?.resourceCount || 0) > 0) return '该分类已绑定资源，请先将资源移动到其他分类。';
  return '';
});
const formTitle = computed(() => formMode.value === 'add' ? '新建资源分类' : '编辑资源分类');
const parentOptions = computed<CategoryNode[]>(() => [{
  id: ROOT_ID,
  name: '根分类（无上级）',
  children: cloneTreeWithout(treeData.value, formMode.value === 'edit' ? form.value.id : ''),
}]);

function flattenTree(nodes: CategoryNode[]): CategoryNode[] {
  return nodes.flatMap(node => [node, ...flattenTree(node.children || [])]);
}

function cloneTreeWithout(nodes: CategoryNode[], excludedId: string): CategoryNode[] {
  return nodes
    .filter(node => node.id !== excludedId)
    .map(node => ({ ...node, children: cloneTreeWithout(node.children || [], excludedId) }));
}

function extractTree(resp: any): CategoryNode[] {
  const payload = resp?.data?.data ?? resp?.data ?? resp;
  return Array.isArray(payload) ? payload : [];
}

async function loadTree(keepSelection = true) {
  loading.value = true;
  try {
    const resp: any = await fetchTree({});
    treeData.value = extractTree(resp);
    if (!expandedKeys.value.length) expandedKeys.value = treeData.value.map(node => node.id);

    const selectedId = keepSelection ? selectedKeys.value[0] : undefined;
    if (selectedId && nodeMap.value.has(selectedId)) await selectNode(selectedId);
    else if (selectedId) clearSelection();
  } catch (e: any) {
    message.error(e?.message || '分类树加载失败');
  } finally {
    loading.value = false;
  }
}

function clearSelection() {
  selectedKeys.value = [];
  selectedNode.value = null;
}

async function selectNode(id: string) {
  const treeNode = nodeMap.value.get(id);
  if (!treeNode) return;
  selectedKeys.value = [id];
  selectedNode.value = treeNode;
  detailLoading.value = true;
  try {
    const resp: any = await get(id);
    const data = resp?.data?.data ?? resp?.data;
    if (data && typeof data === 'object') selectedNode.value = { ...treeNode, ...data };
  } catch {
    // 树接口已包含详情所需字段，详情接口失败时仍可正常浏览和操作。
  } finally {
    detailLoading.value = false;
  }
}

function onSelect(keys: Array<string | number>) {
  if (!keys.length) {
    clearSelection();
    return;
  }
  selectNode(String(keys[0]));
}

function expandAll() {
  expandedKeys.value = flatNodes.value.filter(node => node.children?.length).map(node => node.id);
}

function collapseAll() {
  expandedKeys.value = [];
}

function openCreate(parent?: CategoryNode | null) {
  formMode.value = 'add';
  form.value = {
    ...cloneDeep(defaultForm),
    parentId: parent?.id || ROOT_ID,
  };
  formVisible.value = true;
}

function openEdit() {
  if (!selectedNode.value) return;
  formMode.value = 'edit';
  form.value = {
    ...cloneDeep(defaultForm),
    ...selectedNode.value,
    parentId: selectedNode.value.parentId || ROOT_ID,
  };
  formVisible.value = true;
}

function doDelete(node: CategoryNode) {
  if (node.children?.length || Number(node.resourceCount || 0) > 0) return;
  dialog.warning({
    title: '删除分类',
    content: `确定删除「${node.name}」？删除后无法恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await del(node.id);
        message.success('删除成功');
        clearSelection();
        await loadTree(false);
      } catch (e: any) {
        message.error(e?.message || '删除失败');
      }
    },
  });
}

async function doSubmit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }

  submitting.value = true;
  try {
    const data = {
      ...form.value,
      name: form.value.name.trim(),
      code: form.value.code?.trim() || null,
      parentId: form.value.parentId || ROOT_ID,
    };
    let targetId = form.value.id;
    if (formMode.value === 'add') {
      delete (data as any).id;
      const resp: any = await add(data);
      const createdId = resp?.data?.data ?? resp?.data;
      if (typeof createdId === 'string') targetId = createdId;
      message.success('创建成功');
    } else {
      await edit(data);
      message.success('保存成功');
    }
    formVisible.value = false;
    await loadTree(false);
    if (targetId && nodeMap.value.has(targetId)) await selectNode(targetId);
  } catch (e: any) {
    message.error(e?.message || '操作失败');
  } finally {
    submitting.value = false;
  }
}

function renderLabel({ option }: { option: TreeOption }) {
  const node = option as CategoryNode;
  return h('div', { class: 'tree-node-label' }, [
    h('span', { class: 'tree-node-name' }, node.name),
    h('span', { class: 'tree-node-count' }, String(node.resourceCount || 0)),
  ]);
}

function renderSuffix({ option }: { option: TreeOption }) {
  const node = option as CategoryNode;
  const disabled = !!node.children?.length || Number(node.resourceCount || 0) > 0;
  const options: DropdownOption[] = [
    { label: '添加下级', key: 'add' },
    { label: '编辑', key: 'edit' },
    { type: 'divider', key: 'divider' },
    { label: '删除', key: 'delete', disabled },
  ];

  return h(NDropdown, {
    trigger: 'click',
    options,
    onSelect: (key: string) => {
      if (key === 'add') openCreate(node);
      if (key === 'edit') {
        selectedNode.value = node;
        selectedKeys.value = [node.id];
        openEdit();
      }
      if (key === 'delete') doDelete(node);
    },
  }, {
    default: () => h(NButton, {
      quaternary: true,
      circle: true,
      size: 'tiny',
      onClick: (event: Event) => event.stopPropagation(),
    }, { default: () => '···' }),
  });
}

watch(filter, value => {
  if (value) expandAll();
});

onMounted(loadTree);
</script>

<style scoped>
.category-page {
  min-height: 100%;
}

.category-shell {
  min-height: calc(100vh - 112px);
}

.page-heading,
.detail-heading,
.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 18px;
  font-weight: 650;
}

.page-subtitle,
.panel-hint,
.detail-path {
  margin-top: 4px;
  color: var(--n-text-color-3);
  font-size: 12px;
}

.summary-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.summary-item {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 12px 16px;
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
  background: var(--n-color);
}

.summary-value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
  color: var(--n-color-target, var(--primary-color, #18a058));
}

.summary-label {
  color: var(--n-text-color-2);
  font-size: 13px;
}

.category-workspace {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  min-height: 560px;
  border: 1px solid var(--n-border-color);
  border-radius: 10px;
  overflow: hidden;
}

.tree-panel,
.detail-panel {
  min-width: 0;
  padding: 18px;
  background: var(--n-color);
}

.tree-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  border-right: 1px solid var(--n-border-color);
  background: color-mix(in srgb, var(--n-color) 96%, var(--n-color-target, var(--primary-color, #18a058)));
}

.panel-title {
  font-weight: 600;
}

.tree-loading,
.tree-loading :deep(.n-spin-content) {
  flex: 1;
  min-height: 0;
}

.tree-scrollbar {
  max-height: 500px;
}

.tree-empty,
.detail-empty {
  padding: 72px 12px;
}

:deep(.tree-node-label) {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
}

:deep(.tree-node-name) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.tree-node-count) {
  min-width: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--n-action-color);
  color: var(--n-text-color-3);
  font-size: 11px;
  line-height: 20px;
  text-align: center;
}

.detail-title-wrap {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
}

.detail-icon {
  display: grid;
  width: 46px;
  height: 46px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 10px;
  background: color-mix(in srgb, var(--n-color-target, var(--primary-color, #18a058)) 12%, transparent);
  color: var(--n-color-target, var(--primary-color, #18a058));
  font-size: 26px;
}

.detail-title {
  font-size: 20px;
  font-weight: 650;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 22px 0 16px;
}

.detail-descriptions {
  margin-bottom: 16px;
}

.delete-tip {
  margin-top: 16px;
}

.detail-footer,
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

@media (max-width: 960px) {
  .category-workspace {
    grid-template-columns: 1fr;
  }

  .tree-panel {
    border-right: 0;
    border-bottom: 1px solid var(--n-border-color);
  }

  .tree-scrollbar {
    max-height: 360px;
  }
}

@media (max-width: 640px) {
  .page-heading,
  .detail-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-row {
    grid-template-columns: 1fr;
  }

  .detail-panel,
  .tree-panel {
    padding: 14px;
  }
}
</style>

<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="listDefinitions"
    :params="searchForm"
    :columns="columns"
    :is-add="true"
    :is-edit="false"
    :is-delete="false"
    :show-query-reset-button="true"
    row-key-field="id"
    @add="onCreate"
  >
    <template #queryForm>
      <n-form-item label="流程名称" path="keyword">
        <n-input v-model:value="searchForm.keyword" placeholder="流程名称" clearable />
      </n-form-item>
      <n-form-item label="状态" path="status">
        <HDictSelect
          v-model:value="searchForm.status"
          type="flow_definition_status"
          clearable
          placeholder="请选择状态"
        />
      </n-form-item>
    </template>

    <!-- 创建/编辑弹窗 -->
    <n-modal v-model:show="editVisible" preset="card" title="流程信息" style="width: 480px" :mask-closable="false">
      <n-form ref="editFormRef" :model="editForm" label-width="80px" :rules="formRules">
        <n-form-item label="流程名称" path="name">
          <n-input v-model:value="editForm.name" placeholder="如：采购申请审批" />
        </n-form-item>
        <n-form-item label="流程Key" path="key">
          <n-input
            v-model:value="editForm.key"
            placeholder="如：purchase_apply（英文唯一）"
            :disabled="!!editForm.id && editForm.status !== 'DRAFT'"
          />
        </n-form-item>
        <n-form-item label="表单Key" :feedback="'对应前端自定义表单组件的注册 Key，留空则使用通用表单'">
          <n-input v-model:value="editForm.formKey" placeholder="如：purchase_apply_form（可选）" />
        </n-form-item>
        <n-form-item label="分类">
          <n-input v-model:value="editForm.category" placeholder="可选，如：行政、财务" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="editForm.description" type="textarea" :rows="2" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="editVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="onSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <VersionHistoryDrawer ref="historyDrawerRef" />
  </h-page-data-table>
</template>

<script setup lang="ts">
import { ref, h } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage, NSpace, NButton, NDropdown, NIcon } from 'naive-ui';
import HIcon from '@/components/basic/DynamicIcon.vue';
import {
  listDefinitions,
  createDefinition,
  updateDefinition,
  publishDefinition,
  disableDefinition,
  copyDefinition,
  deleteDefinition,
} from '../../api/definition';
import type { FlowDefinition } from '../../typings/flow';
import { confirm } from '@/utils';
import VersionHistoryDrawer from '../../components/VersionHistoryDrawer.vue';

const router = useRouter();
const message = useMessage();

const saving = ref(false);
const editVisible = ref(false);
const editFormRef = ref<any>();
const refPageTable = ref();
const historyDrawerRef = ref<InstanceType<typeof VersionHistoryDrawer>>();

const searchForm = ref({ keyword: '', status: null as string | null });
const editForm = ref<FlowDefinition>({});

const formRules = {
  name: [{ required: true, message: '请填写流程名称', trigger: 'blur' }],
  key: [
    { required: true, message: '请填写流程Key', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]*$/, message: 'Key必须以小写字母开头，只能包含字母、数字和下划线', trigger: 'blur' },
  ],
};

const columns = [
  { title: '流程名称', key: 'name', width: 200, ellipsis: { tooltip: true } },
  { title: '流程Key', key: 'key', width: 180 },
  { title: '分类', key: 'category', width: 100 },
  { title: '版本', key: 'version', width: 70, align: 'center' },
  { title: '状态', key: 'status', width: 90, align: 'center', dictType: 'flow_definition_status' },
  {
    title: '更新时间',
    key: 'updateTime',
    width: 160,
    columnType: 'dateTime',
  },
  {
    key: 'operate',
    type: 'operate',
    title: '操作',
    align: 'center',
    fixed: 'right',
    width: 180,
    render: (row: FlowDefinition) => renderRowActions(row),
  },
];

/**
 * 操作列渲染：保留 2 个主操作（设计/查看 + 状态主动作），其余收入"更多"下拉菜单。
 *
 * 主操作选择规则：
 * - DRAFT: 设计 + 发布
 * - ACTIVE: 查看 + 禁用
 * - DISABLED: 查看（无次要主操作，由更多菜单提供新建版本）
 *
 * 更多菜单条目随状态切换，避免在表格里塞太多按钮。
 */
function renderRowActions(row: FlowDefinition) {
  const primary = h(
    NButton,
    { size: 'small', type: 'primary', text: true, onClick: () => toDesign(row) },
    () => (row.status === 'DRAFT' ? '设计' : '查看'),
  );

  const stateAction =
    row.status === 'DRAFT'
      ? h(NButton, { size: 'small', type: 'success', text: true, onClick: () => onPublish(row) }, () => '发布')
      : row.status === 'ACTIVE'
        ? h(NButton, { size: 'small', type: 'warning', text: true, onClick: () => onDisable(row) }, () => '禁用')
        : null;

  // 更多菜单选项：根据状态动态生成
  const moreOptions = [
    row.status === 'DRAFT' && {
      label: '编辑信息',
      key: 'edit',
      icon: () => h(NIcon, null, () => h(HIcon, { icon: 'mdi:pencil-outline' })),
    },
    row.status !== 'DRAFT' && {
      label: '新建版本',
      key: 'newVersion',
      icon: () => h(NIcon, null, () => h(HIcon, { icon: 'mdi:source-branch-plus' })),
    },
    {
      label: '版本历史',
      key: 'history',
      icon: () => h(NIcon, null, () => h(HIcon, { icon: 'mdi:history' })),
    },
    row.status !== 'ACTIVE' && { type: 'divider', key: 'd1' },
    row.status !== 'ACTIVE' && {
      label: '删除',
      key: 'delete',
      props: { style: 'color: #d03050;' },
      icon: () => h(NIcon, { color: '#d03050' }, () => h(HIcon, { icon: 'mdi:trash-can-outline' })),
    },
  ].filter(Boolean);

  const handleMore = (key: string) => {
    switch (key) {
      case 'edit':
        return onEdit(row);
      case 'newVersion':
        return onNewVersion(row);
      case 'history':
        return onShowHistory(row);
      case 'delete':
        return onDelete(row);
    }
  };

  const more = h(
    NDropdown,
    { trigger: 'hover', options: moreOptions as any, onSelect: handleMore, placement: 'bottom-end' },
    () =>
      h(
        NButton,
        { size: 'small', text: true },
        {
          default: () => '更多',
          icon: () => h(NIcon, null, () => h(HIcon, { icon: 'mdi:chevron-down' })),
        },
      ),
  );

  return h(NSpace, { size: 8, align: 'center', wrap: false }, () =>
    [primary, stateAction, more].filter((x) => x != null),
  );
}

function refresh() {
  refPageTable.value?.handleRefresh();
}

function onCreate() {
  editForm.value = {};
  editVisible.value = true;
}

function onEdit(row: FlowDefinition) {
  if (row.status !== 'DRAFT') {
    message.warning('已发布或历史版本不可编辑，请复制为新草稿后修改');
    return;
  }
  editForm.value = { ...row };
  editVisible.value = true;
}

function toDesign(row: FlowDefinition) {
  router.push({ path: '/flow/definition/design', query: { id: row.id, title: row.name } });
}

async function onSave() {
  await editFormRef.value?.validate();
  saving.value = true;
  try {
    if (editForm.value.id) {
      await updateDefinition(editForm.value);
    } else {
      await createDefinition(editForm.value);
    }
    message.success('保存成功');
    editVisible.value = false;
    refresh();
  } finally {
    saving.value = false;
  }
}

async function onPublish(row: FlowDefinition) {
  confirm({
    title: '发布确认',
    content: `确认发布流程「${row.name}」？发布后当前版本将生效，旧版本将被禁用。`,
    onPositiveClick: async () => {
      await publishDefinition(row.id!);
      message.success('发布成功');
      refresh();
    },
  });
}

async function onDisable(row: FlowDefinition) {
  confirm({
    title: '禁用确认',
    content: `确认禁用流程「${row.name}」？禁用后将无法发起新的流程实例。`,
    type: 'warning',
    onPositiveClick: async () => {
      await disableDefinition(row.id!);
      message.success('已禁用');
      refresh();
    },
  });
}

/**
 * 新建版本：基于当前版本创建 DRAFT 草稿。
 *
 * 后端 [FlowDefinitionService.copy] 已实现防呆：
 * - 源是 DRAFT → 返回源
 * - 同 key 已有 DRAFT → 返回已存在的 DRAFT
 * - 否则创建新 DRAFT 并保留 key/name
 *
 * 创建/打开后直接跳到设计页编辑，避免用户在列表里找不到新生成的草稿。
 */
async function onNewVersion(row: FlowDefinition) {
  confirm({
    title: '新建版本',
    content: `基于 v${row.version} 创建新草稿用于修改？保存后发布将自动 +1 版本号并禁用旧版。`,
    onPositiveClick: async () => {
      const res = await copyDefinition(row.id!);
      const draft = (res.data?.data ?? res.data) as FlowDefinition;
      if (!draft?.id) {
        message.error('草稿创建失败');
        return;
      }
      message.success('草稿已就绪');
      router.push({ path: '/flow/definition/design', query: { id: draft.id, title: draft.name } });
    },
  });
}

/** 打开版本历史抽屉，展示同 key 所有版本 + 操作日志 */
function onShowHistory(row: FlowDefinition) {
  if (!row.key) {
    message.warning('该流程缺少 key，无法查看版本历史');
    return;
  }
  historyDrawerRef.value?.open({ key: row.key, name: row.name || row.key });
}

async function onDelete(row: FlowDefinition) {
  confirm({
    title: '删除确认',
    content: `确认删除流程「${row.name}」？此操作不可恢复。`,
    type: 'error',
    onPositiveClick: async () => {
      await deleteDefinition(row.id!);
      message.success('删除成功');
      refresh();
    },
  });
}
</script>

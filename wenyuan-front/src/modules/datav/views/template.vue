<template>
  <h-page-data-table
    ref="pageTableRef"
    :fetch-data="fetchTemplates"
    :params="searchForm"
    :columns="columns"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    :show-query-reset-button="true"
    row-key-field="id"
  >
    <template #queryForm>
      <n-form-item label="模板名称" path="keyword">
        <n-input v-model:value="searchForm.keyword" placeholder="输入名称查询" clearable />
      </n-form-item>
    </template>

    <n-modal v-model:show="applyVisible" preset="card" title="应用大屏模板" style="width: 520px">
      <n-alert type="info" :show-icon="false" class="apply-tip">
        目标业务对象尚未配置大屏时才能应用；应用后生成草稿，需要发布后才会影响展示端。
      </n-alert>
      <n-form label-width="100px">
        <n-form-item label="业务类型" required>
          <n-select
            v-if="subjectOptions.length"
            v-model:value="targetSubjectType"
            :options="subjectOptions"
            placeholder="请选择业务类型"
          />
          <n-input v-else v-model:value="targetSubjectType" placeholder="请输入业务类型标识" />
        </n-form-item>
        <n-form-item :label="activeAdapter?.idLabel || '业务对象 ID'" required>
          <component
            :is="activeAdapter.selector"
            v-if="activeAdapter?.selector"
            v-model="targetSubjectId"
          />
          <n-input
            v-else
            v-model:value="targetSubjectId"
            :placeholder="activeAdapter?.idPlaceholder || '请输入业务对象 ID'"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="applyVisible = false">取消</n-button>
          <n-button type="primary" :loading="applying" @click="applyTemplate">生成草稿</n-button>
        </n-space>
      </template>
    </n-modal>
  </h-page-data-table>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NButton, NSpace, useMessage } from 'naive-ui';
import { confirm } from '@/utils';
import type { DatavProjectSummary } from '../core/types';
import { archiveDatavProject, copyDatavProject, listDatavProjects } from '../api/project';
import { datavSubjectRegistry } from '../registry/subjectRegistry';

const router = useRouter();
const message = useMessage();
const pageTableRef = ref();
const searchForm = ref({ keyword: '', projectType: 'TEMPLATE' as const });
const applyVisible = ref(false);
const applying = ref(false);
const subjectAdapters = datavSubjectRegistry.list();
const subjectOptions = subjectAdapters.map((adapter) => ({ label: adapter.label, value: adapter.type }));
const targetSubjectType = ref(subjectOptions[0]?.value || '');
const targetSubjectId = ref('');
const activeAdapter = computed(() => datavSubjectRegistry.resolve(targetSubjectType.value));
const activeTemplate = ref<DatavProjectSummary>();

function fetchTemplates(params: Record<string, unknown>) {
  return listDatavProjects({ ...params, projectType: 'TEMPLATE' });
}

const columns = [
  { title: '模板名称', key: 'title', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '组件数', key: 'widgetCount', width: 100, align: 'center' },
  { title: '版本', key: 'version', width: 80, align: 'center' },
  { title: '更新时间', key: 'updateTime', width: 180, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 180,
    render: (row: DatavProjectSummary) =>
      h(NSpace, { size: 12 }, () => [
        h(NButton, { text: true, type: 'primary', onClick: () => openApply(row) }, () => '应用模板'),
        h(NButton, { text: true, type: 'error', onClick: () => archive(row) }, () => '归档'),
      ]),
  },
];

function openApply(row: DatavProjectSummary) {
  activeTemplate.value = row;
  targetSubjectType.value = subjectOptions[0]?.value || '';
  targetSubjectId.value = '';
  applyVisible.value = true;
}

async function applyTemplate() {
  const subjectType = targetSubjectType.value.trim();
  const subjectId = targetSubjectId.value.trim();
  if (!activeTemplate.value || !subjectType || !subjectId) return message.warning('请选择业务类型并填写业务对象 ID');
  applying.value = true;
  try {
    await copyDatavProject(activeTemplate.value.id, subjectType, subjectId);
    message.success('模板已应用并生成草稿');
    applyVisible.value = false;
    const editRoute = datavSubjectRegistry.resolve(subjectType)?.editRoute;
    if (editRoute) router.push(editRoute(subjectId));
  } finally {
    applying.value = false;
  }
}

function archive(row: DatavProjectSummary) {
  confirm({
    title: '归档模板',
    content: `确认归档模板「${row.title}」？`,
    type: 'warning',
    onPositiveClick: async () => {
      await archiveDatavProject(row.id);
      message.success('模板已归档');
      pageTableRef.value?.handleRefresh();
    },
  });
}
</script>

<style scoped>
.apply-tip {
  margin-bottom: 16px;
}
</style>

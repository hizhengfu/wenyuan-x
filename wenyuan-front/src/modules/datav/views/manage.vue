<template>
  <h-page-data-table
    ref="pageTableRef"
    :fetch-data="fetchProjects"
    :params="searchForm"
    :columns="columns"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    :show-query-reset-button="true"
    row-key-field="id"
  >
    <template #queryForm>
      <n-form-item label="大屏名称" path="keyword">
        <n-input v-model:value="searchForm.keyword" placeholder="输入名称查询" clearable />
      </n-form-item>
    </template>

    <n-drawer v-model:show="settingsVisible" :width="560">
      <n-drawer-content :title="`${activeProject?.title || ''} · 项目设置`" closable>
        <n-alert type="info" :show-icon="false" class="settings-tip">
          管理员和项目创建人始终可以访问；其他用户根据所属机构判断是否可以访问该大屏。
        </n-alert>
        <n-form label-placement="top">
          <n-form-item label="大屏标题" required>
            <n-input
              v-model:value="settingsForm.title"
              maxlength="100"
              show-count
              placeholder="请输入大屏标题"
              @keydown.enter.prevent="saveSettings"
            />
          </n-form-item>
          <n-form-item label="所属机构" required>
            <HGroupSelect v-model:value="settingsForm.groupId" placeholder="请选择所属机构" />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="settingsVisible = false">取消</n-button>
            <n-button type="primary" :loading="settingsSaving" @click="saveSettings">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-modal v-model:show="templateVisible" preset="card" title="保存为模板" style="width: 480px">
      <n-form label-width="80px">
        <n-form-item label="模板名称" required>
          <n-input v-model:value="templateTitle" maxlength="100" show-count placeholder="请输入模板名称" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="templateVisible = false">取消</n-button>
          <n-button type="primary" :loading="templateSaving" @click="saveAsTemplate">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-drawer v-model:show="revisionVisible" :width="680">
      <n-drawer-content :title="`${activeProject?.title || ''} · 版本记录`" closable>
        <n-alert type="info" :show-icon="false" class="revision-tip">
          恢复历史版本会生成一个新的草稿版本，不会直接覆盖当前展示版本。
        </n-alert>
        <n-data-table :columns="revisionColumns" :data="revisions" :loading="revisionLoading" :pagination="false" />
      </n-drawer-content>
    </n-drawer>
  </h-page-data-table>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NButton, NSpace, NTag, useMessage } from 'naive-ui';
import { confirm, formatDateTime } from '@/utils';
import { useAuthStore } from '@/store';
import HGroupSelect from '../../sys/components/form/field/GroupSelect.vue';
import type { DatavProjectSummary, DatavRevision } from '../core/types';
import { datavSubjectRegistry } from '../registry/subjectRegistry';
import {
  archiveDatavProject,
  createDatavTemplate,
  listDatavProjects,
  listDatavRevisions,
  publishDatavProject,
  restoreDatavRevision,
  updateDatavProject,
} from '../api/project';

const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();
const pageTableRef = ref();
const searchForm = ref({ keyword: '', projectType: 'PROJECT' as const });
const templateVisible = ref(false);
const templateSaving = ref(false);
const templateTitle = ref('');
const activeProject = ref<DatavProjectSummary>();
const revisionVisible = ref(false);
const revisionLoading = ref(false);
const revisions = ref<DatavRevision[]>([]);
const settingsVisible = ref(false);
const settingsSaving = ref(false);
const settingsForm = ref({ title: '', groupId: null as string | null });
const canEditProject = computed(() => authStore.hasPermission('DATAV_ALL,DATAV_EDIT'));

function fetchProjects(params: Record<string, unknown>) {
  return listDatavProjects({ ...params, projectType: 'PROJECT' });
}

function refresh() {
  pageTableRef.value?.handleRefresh();
}

const columns = [
  { title: '大屏名称', key: 'title', minWidth: 180, ellipsis: { tooltip: true } },
  {
    title: '业务对象',
    key: 'subjectId',
    width: 120,
    ellipsis: { tooltip: true },
    resizable: true,
    render: (row: DatavProjectSummary) => {
      const adapter = datavSubjectRegistry.resolve(row.subjectType);
      return `${adapter?.label || row.subjectType || '未绑定'}`;
    },
  },
  { title: '组件数', key: 'widgetCount', width: 80, align: 'center' },
  {
    title: '草稿版本',
    key: 'version',
    width: 90,
    align: 'center',
    render: (row: DatavProjectSummary) => `v${row.version}`,
  },
  {
    title: '发布版本',
    key: 'publishedVersion',
    width: 90,
    align: 'center',
    render: (row: DatavProjectSummary) => (row.publishedVersion ? `v${row.publishedVersion}` : '未发布'),
  },
  {
    title: '发布状态',
    key: 'needsPublish',
    width: 110,
    align: 'center',
    render: (row: DatavProjectSummary) =>
      h(NTag, { size: 'small', type: row.needsPublish ? 'warning' : 'success' }, () =>
        row.publishedVersion ? (row.needsPublish ? '有待发布草稿' : '已发布') : '尚未发布',
      ),
  },
  { title: '更新时间', key: 'updateTime', width: 170, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    fixed: 'right',
    width: 390,
    render: (row: DatavProjectSummary) =>
      h(NSpace, { size: 10, wrap: false }, () =>
        [
          datavSubjectRegistry.resolve(row.subjectType)?.editRoute
            ? h(NButton, { text: true, type: 'primary', onClick: () => edit(row) }, () => '编辑草稿')
            : null,
          row.publishedVersion && datavSubjectRegistry.resolve(row.subjectType)?.previewRoute
            ? h(NButton, { text: true, onClick: () => preview(row) }, () => '预览发布版')
            : null,
          row.needsPublish
            ? h(NButton, { text: true, type: 'success', onClick: () => publish(row) }, () => '发布')
            : null,
          canEditProject.value ? h(NButton, { text: true, onClick: () => openSettings(row) }, () => '设置') : null,
          h(NButton, { text: true, onClick: () => openRevisions(row) }, () => '版本'),
          h(NButton, { text: true, onClick: () => openTemplate(row) }, () => '存为模板'),
          h(NButton, { text: true, type: 'error', onClick: () => archive(row) }, () => '归档'),
        ].filter(Boolean),
      ),
  },
];

function openSettings(row: DatavProjectSummary) {
  activeProject.value = row;
  settingsForm.value = { title: row.title, groupId: row.groupId || null };
  settingsVisible.value = true;
}

async function saveSettings() {
  if (!activeProject.value) return;
  const title = settingsForm.value.title.trim();
  if (!title) return message.warning('请输入大屏标题');
  if (!settingsForm.value.groupId) return message.warning('请选择所属机构');
  settingsSaving.value = true;
  try {
    await updateDatavProject(activeProject.value.id, title, settingsForm.value.groupId);
    activeProject.value.title = title;
    activeProject.value.groupId = settingsForm.value.groupId;
    settingsForm.value.title = title;
    message.success('项目设置保存成功');
    settingsVisible.value = false;
    refresh();
  } finally {
    settingsSaving.value = false;
  }
}

const revisionColumns = [
  { title: '版本', key: 'version', width: 80 },
  {
    title: '类型',
    key: 'revisionType',
    width: 110,
    render: (row: DatavRevision) =>
      h(NTag, { size: 'small', type: row.revisionType === 'PUBLISHED' ? 'success' : 'info' }, () =>
        row.revisionType === 'PUBLISHED' ? '发布版本' : '草稿版本',
      ),
  },
  { title: '组件数', key: 'widgetCount', width: 80 },
  {
    title: '创建时间',
    key: 'createTime',
    width: 170,
    render: (row: DatavRevision) => formatDateTime(row.createTime),
  },
  {
    title: '操作',
    key: 'operate',
    width: 90,
    render: (row: DatavRevision) =>
      h(NButton, { text: true, type: 'primary', onClick: () => restore(row) }, () => '恢复'),
  },
];

function edit(row: DatavProjectSummary) {
  const adapter = datavSubjectRegistry.resolve(row.subjectType);
  if (!row.subjectId || !adapter?.editRoute) return message.warning('当前业务类型没有配置编辑入口');
  const route = router.resolve(adapter.editRoute(row.subjectId));
  window.open(route.href, '_blank', 'noopener,noreferrer');
}

function preview(row: DatavProjectSummary) {
  const adapter = datavSubjectRegistry.resolve(row.subjectType);
  if (!row.subjectId || !adapter?.previewRoute) return message.warning('当前业务类型没有配置预览入口');
  // 预览不打断管理页工作状态；先交给 Vue Router 解析，确保 Hash/History 模式都能生成正确地址。
  const route = router.resolve(adapter.previewRoute(row.subjectId));
  window.open(route.href, '_blank', 'noopener,noreferrer');
}

function publish(row: DatavProjectSummary) {
  if (!row.subjectType || !row.subjectId) return;
  confirm({
    title: '发布大屏',
    content: `确认将「${row.title}」当前草稿发布到展示端？`,
    onPositiveClick: async () => {
      await publishDatavProject(row.subjectType!, row.subjectId!, row.version);
      message.success('发布成功');
      refresh();
    },
  });
}

function openTemplate(row: DatavProjectSummary) {
  activeProject.value = row;
  templateTitle.value = `${row.title}-模板`;
  templateVisible.value = true;
}

async function saveAsTemplate() {
  if (!activeProject.value || !templateTitle.value.trim()) return message.warning('请输入模板名称');
  templateSaving.value = true;
  try {
    await createDatavTemplate(activeProject.value.id, templateTitle.value.trim());
    message.success('模板已创建');
    templateVisible.value = false;
  } finally {
    templateSaving.value = false;
  }
}

async function openRevisions(row: DatavProjectSummary) {
  activeProject.value = row;
  revisionVisible.value = true;
  revisionLoading.value = true;
  try {
    const response = await listDatavRevisions(row.id);
    revisions.value = response.data || [];
  } finally {
    revisionLoading.value = false;
  }
}

function restore(revision: DatavRevision) {
  if (!activeProject.value) return;
  confirm({
    title: '恢复历史版本',
    content: `确认把 v${revision.version} 恢复为新的草稿？当前已发布版本不会立即改变。`,
    onPositiveClick: async () => {
      await restoreDatavRevision(activeProject.value!.id, activeProject.value!.version, revision.version);
      message.success('已恢复为新草稿');
      revisionVisible.value = false;
      refresh();
    },
  });
}

function archive(row: DatavProjectSummary) {
  confirm({
    title: '归档大屏',
    content: `确认归档「${row.title}」？归档后将释放业务绑定，并可重新配置。`,
    type: 'warning',
    onPositiveClick: async () => {
      await archiveDatavProject(row.id);
      message.success('已归档');
      refresh();
    },
  });
}
</script>

<style scoped>
.revision-tip {
  margin-bottom: 14px;
}

.settings-tip {
  margin-bottom: 16px;
}
</style>

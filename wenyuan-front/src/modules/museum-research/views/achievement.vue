<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="achievementApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="编号 / 名称 / 作者 / 关键词" style="width: 240px" />
      </NFormItem>
      <NFormItem label="类型">
        <HDictSelect v-model:value="queryParams.achievementType" type="research_achievement_type" clearable placeholder="成果类型" style="width: 140px" />
      </NFormItem>
      <NFormItem label="方向">
        <HDictSelect v-model:value="queryParams.direction" type="research_direction" clearable placeholder="研究方向" style="width: 140px" />
      </NFormItem>
      <NFormItem label="审核状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable placeholder="状态" style="width: 120px" />
      </NFormItem>
    </template>

    <FlowFormDrawer
      ref="flowDrawerRef"
      :fetch-fn="achievementApi.flow"
      :create-api="achievementApi.save"
      :save-api="saveFlowDraft"
      :submit-api="achievementApi.submit"
      :cancel-api="achievementApi.cancel"
      @success="refreshTable"
    />
    <CollectionDisclosureDrawer ref="disclosureRef" />
    <EvaluationHistoryDrawer ref="evaluationHistoryRef" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import { h, reactive, ref } from 'vue';
import { NButton, NPopconfirm, NSwitch, useMessage } from 'naive-ui';
import { useAuthStore } from '@/store';
import CollectionDisclosureDrawer from '@/modules/collection/components/CollectionDisclosureDrawer.vue';
import { achievementApi } from '../api/achievement';
import EvaluationHistoryDrawer from '../components/EvaluationHistoryDrawer.vue';
import FlowFormDrawer from '../../flow/components/FlowFormDrawer.vue';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import type { ResAchievementVo } from '../typings';

const message = useMessage();
const auth = useAuthStore();
const disclosureRef = ref<InstanceType<typeof CollectionDisclosureDrawer> | null>(null);
const evaluationHistoryRef = ref<InstanceType<typeof EvaluationHistoryDrawer> | null>(null);
const tableRef = ref<any>(null);
const flowDrawerRef = ref<any>(null);
const { flowStatusColumn, handleTitleClick } = useFlowApproval({
  refTable: tableRef,
  drawerRef: flowDrawerRef,
  getLabel: (row: ResAchievementVo) => row.title || row.achievementNo || '成果详情',
});

const queryParams = reactive({
  keyword: null as string | null,
  achievementType: null as string | null,
  direction: null as string | null,
  flowStatus: null as string | null,
});

const columns = [
  { title: '成果编号', key: 'achievementNo', width: 120, ellipsis: { tooltip: true } },
  { title: '成果名称', key: 'title', minWidth: 200, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  { title: '类型', key: 'achievementType', width: 100, dictType: 'research_achievement_type' },
  { title: '方向', key: 'direction', width: 100, dictType: 'research_direction' },
  { title: '作者', key: 'authors', width: 140, ellipsis: { tooltip: true } },
  { title: '关联课题', key: 'topicName', width: 150, ellipsis: { tooltip: true } },
  { title: '发表时间', key: 'publishDate', width: 110 },
  {
    title: '均分',
    key: 'avgScore',
    width: 80,
    render: (row: ResAchievementVo) => (row.avgScore != null ? `${row.avgScore}(${row.evalCount})` : '—'),
  },
  flowStatusColumn,
  {
    title: '内部展示',
    key: 'published',
    width: 70,
    render: (row: ResAchievementVo) =>
      h(NSwitch, {
        value: !!row.published,
        size: 'small',
        disabled: row.flowStatus !== 'PASS' || !auth.hasPermission('ADMIN,RESEARCH_ADMIN,RESEARCH_ACHIEVEMENT_MANAGE'),
        onUpdateValue: (val: boolean) => onPublish(row, val),
      }),
  },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 260,
    fixed: 'right' as const,
    render: (row: ResAchievementVo) => {
      const buttons = [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
      ];
      if (auth.hasPermission('ADMIN,RESEARCH_ADMIN,RESEARCH_SELECT,RESEARCH_ACHIEVEMENT_MANAGE,RESEARCH_ACHIEVEMENT_EVAL')) {
        buttons.push(h(NButton, {
          text: true, type: 'primary', size: 'small', style: 'margin-left:10px', disabled: !row.orgId || !row.id,
          onClick: () => evaluationHistoryRef.value?.open(row),
        }, () => '评价历史'));
      }
      if (auth.hasPermission('ADMIN,COLLECTION_ADMIN,COLLECTION_PUBLISH')) {
        buttons.push(h(NButton, {
          text: true, type: 'primary', size: 'small', style: 'margin-left:10px',
          disabled: !row.orgId || !row.id,
          onClick: () => openDisclosure(row),
        }, () => '公开核查与实发'));
      }
      if (row.flowStatus && ['REJECT', 'CANCEL'].includes(row.flowStatus)) {
        buttons.push(
          h(
            NPopconfirm,
            { onPositiveClick: () => onVoid(row) },
            {
              trigger: () => h(NButton, { text: true, type: 'warning', size: 'small', style: 'margin-left:10px' }, () => '作废'),
              default: () => '作废后不可再发起评审，确定作废？',
            },
          ),
        );
      }
      if (row.flowStatus && ['DRAFT', 'VOID'].includes(row.flowStatus)) {
        buttons.push(
          h(
            NPopconfirm,
            { onPositiveClick: () => onDelete(row) },
            {
              trigger: () => h(NButton, { text: true, type: 'error', size: 'small', style: 'margin-left:10px' }, () => '删除'),
              default: () => '确定删除该成果登记？',
            },
          ),
        );
      }
      return buttons;
    },
  },
];

function openDisclosure(row: ResAchievementVo) {
  if (!row.orgId || !row.id || !auth.hasPermission('ADMIN,COLLECTION_ADMIN,COLLECTION_PUBLISH')) return;
  return disclosureRef.value?.open({ orgId: row.orgId, subject: 'RESEARCH_ACHIEVEMENT', subjectId: row.id });
}

function onAdd() {
  flowDrawerRef.value?.openCreate({
    title: '新增成果登记',
    businessType: 'RESEARCH_ACHIEVEMENT',
    formData: {
      id: undefined,
      title: undefined,
      achievementType: undefined,
      direction: undefined,
      topicId: undefined,
      authors: undefined,
      team: undefined,
      publishOrg: undefined,
      publishDate: undefined,
      keywords: undefined,
      summary: undefined,
      attachmentIds: undefined,
    },
  });
}

function onView(row: ResAchievementVo) {
  handleTitleClick(row);
}

async function onDelete(row: ResAchievementVo) {
  await achievementApi.delete(row.id!);
  message.success('已删除');
  refreshTable();
}

async function onVoid(row: ResAchievementVo) {
  await achievementApi.void(row.id!);
  message.success('已作废');
  refreshTable();
}

async function onPublish(row: ResAchievementVo, published: boolean) {
  await achievementApi.publish(row.id!, published);
  message.success(published ? '已上架内部展示；对外公开须另行核查' : '已下架');
  refreshTable();
}

function saveFlowDraft(id: string, data: Record<string, any>) {
  return achievementApi.save({ ...data, id });
}

function refreshTable() {
  tableRef.value?.handleRefresh();
  tableRef.value?.handleQuery?.();
}
</script>

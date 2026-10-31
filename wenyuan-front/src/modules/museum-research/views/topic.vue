<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="topicApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="编号 / 名称 / 负责人 / 单位" style="width: 240px" />
      </NFormItem>
      <NFormItem label="类别">
        <HDictSelect v-model:value="queryParams.direction" type="research_direction" clearable placeholder="课题类别" style="width: 140px" />
      </NFormItem>
      <NFormItem label="级别">
        <HDictSelect v-model:value="queryParams.level" type="research_topic_level" clearable placeholder="级别" style="width: 130px" />
      </NFormItem>
      <NFormItem label="阶段">
        <HDictSelect v-model:value="queryParams.stage" type="research_topic_stage" clearable placeholder="阶段" style="width: 140px" />
      </NFormItem>
      <NFormItem label="审批状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable placeholder="状态" style="width: 120px" />
      </NFormItem>
    </template>

    <FlowFormDrawer
      ref="flowDrawerRef"
      :fetch-fn="topicApi.flow"
      :create-api="topicApi.save"
      :save-api="saveFlowDraft"
      :submit-api="topicApi.submit"
      :cancel-api="topicApi.cancel"
      @success="refreshTable"
    />
  </HPageDataTable>
</template>

<script setup lang="ts">
import { h, reactive, ref, resolveComponent } from 'vue';
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui';
import { topicApi } from '../api/topic';
import FlowFormDrawer from '../../flow/components/FlowFormDrawer.vue';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import type { ResTopicVo } from '../typings';

const HDictText = resolveComponent('HDictText');

const message = useMessage();
const tableRef = ref<any>(null);
const flowDrawerRef = ref<any>(null);
const { flowStatusColumn, handleTitleClick } = useFlowApproval({
  refTable: tableRef,
  drawerRef: flowDrawerRef,
  getLabel: (row: ResTopicVo) => row.name || row.topicNo || '课题详情',
});

const queryParams = reactive({
  keyword: null as string | null,
  direction: null as string | null,
  level: null as string | null,
  stage: null as string | null,
  flowStatus: null as string | null,
});

const columns = [
  { title: '课题编号', key: 'topicNo', width: 120, ellipsis: { tooltip: true } },
  { title: '课题名称', key: 'name', minWidth: 200, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  { title: '类别', key: 'direction', width: 100, dictType: 'research_direction' },
  { title: '级别', key: 'level', width: 90, dictType: 'research_topic_level' },
  { title: '负责人', key: 'leaderName', width: 90 },
  { title: '承担单位', key: 'orgName', width: 140, ellipsis: { tooltip: true } },
  {
    title: '阶段',
    key: 'stage',
    width: 130,
    render: (row: ResTopicVo) =>
      h('span', {}, [
        h(HDictText, { type: 'research_topic_stage', value: row.stage }),
        row.delayedPlanCount
          ? h(NTag, { type: 'error', size: 'small', bordered: false, style: 'margin-left:6px' }, () => `预警${row.delayedPlanCount}`)
          : null,
      ]),
  },
  flowStatusColumn,
  { title: '申报时间', key: 'createTime', width: 170, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 200,
    fixed: 'right' as const,
    render: (row: ResTopicVo) => {
      const buttons = [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
      ];
      if (row.flowStatus && ['REJECT', 'CANCEL'].includes(row.flowStatus)) {
        buttons.push(
          h(
            NPopconfirm,
            { onPositiveClick: () => onVoid(row) },
            {
              trigger: () => h(NButton, { text: true, type: 'warning', size: 'small', style: 'margin-left:10px' }, () => '作废'),
              default: () => '作废后不可再发起审批，确定作废？',
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
              default: () => '确定删除该课题申请？',
            },
          ),
        );
      }
      if (row.stage === 'RESEARCH' || row.stage === 'CONCLUDING') {
        buttons.push(
          h(
            NPopconfirm,
            { onPositiveClick: () => onTerminate(row) },
            {
              trigger: () => h(NButton, { text: true, type: 'warning', size: 'small', style: 'margin-left:10px' }, () => '终止'),
              default: () => '确定终止该课题？终止后不可恢复',
            },
          ),
        );
      }
      return buttons;
    },
  },
];

function onAdd() {
  flowDrawerRef.value?.openCreate({
    title: '新增课题申请',
    businessType: 'RESEARCH_TOPIC',
    formData: {
      id: undefined,
      name: undefined,
      direction: undefined,
      level: undefined,
      leaderName: undefined,
      orgName: undefined,
      external: false,
      members: undefined,
      purpose: undefined,
      content: undefined,
      expectedResult: undefined,
      budget: undefined,
      startDate: undefined,
      endDate: undefined,
      attachmentIds: undefined,
    },
  });
}

function onView(row: ResTopicVo) {
  handleTitleClick(row);
}

async function onDelete(row: ResTopicVo) {
  await topicApi.delete(row.id!);
  message.success('已删除');
  refreshTable();
}

async function onVoid(row: ResTopicVo) {
  await topicApi.void(row.id!);
  message.success('已作废');
  refreshTable();
}

async function onTerminate(row: ResTopicVo) {
  await topicApi.terminate(row.id!);
  message.success('已终止');
  refreshTable();
}

function saveFlowDraft(id: string, data: Record<string, any>) {
  return topicApi.save({ ...data, id });
}

function refreshTable() {
  tableRef.value?.handleRefresh();
  tableRef.value?.handleQuery?.();
}
</script>

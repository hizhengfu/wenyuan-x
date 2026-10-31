<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="conclusionApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="课题名称" style="width: 220px" />
      </NFormItem>
      <NFormItem label="审核状态">
        <HDictSelect v-model:value="queryParams.conclusionStatus" type="research_conclusion_status" clearable placeholder="状态" style="width: 130px" />
      </NFormItem>
    </template>

    <HPageForm
      v-model:show="formShow"
      :title="editVo.id ? '修改结项报告' : '提交结项报告'"
      :model="editVo"
      :rules="rules"
      :ref-table="tableRef"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="100"
      width="720px"
    >
      <NFormItem label="课题" path="topicId">
        <TopicSelect v-model:value="editVo.topicId" placeholder="选择在研课题" :disabled="!!editVo.id" />
      </NFormItem>
      <NFormItem label="研究过程" path="process">
        <NInput v-model:value="editVo.process" type="textarea" :rows="4" placeholder="研究过程总结" />
      </NFormItem>
      <NFormItem label="研究成果" path="achievement">
        <NInput v-model:value="editVo.achievement" type="textarea" :rows="4" placeholder="研究成果总结" />
      </NFormItem>
      <NFormItem label="经验建议" path="experience">
        <NInput v-model:value="editVo.experience" type="textarea" :rows="3" placeholder="经验与建议" />
      </NFormItem>
      <NFormItem label="报告附件" path="attachmentIds">
        <HUploadInput v-model:value="editVo.attachmentIds" @upload-finish="editVo.attachmentIds = $event.id" clearable placeholder="上传结项报告全文" />
      </NFormItem>
    </HPageForm>

    <HPageView v-model:show="viewShow" title="结项报告详情" width="720px">
      <template v-if="viewRow">
        <NDescriptions label-placement="left" :column="1" bordered size="small">
          <NDescriptionsItem label="课题">{{ viewRow.topicNo }} {{ viewRow.topicName }}</NDescriptionsItem>
          <NDescriptionsItem label="负责人">{{ viewRow.leaderName || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="审核状态">
            <HDictText type="research_conclusion_status" :value="viewRow.conclusionStatus" />
          </NDescriptionsItem>
          <NDescriptionsItem label="研究过程">{{ viewRow.process || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="研究成果">{{ viewRow.achievement || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="经验建议">{{ viewRow.experience || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="审核意见">{{ viewRow.reviewOpinion || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="提交时间">{{ viewRow.createTime || '--' }}</NDescriptionsItem>
        </NDescriptions>
        <NSpace v-if="viewRow.conclusionStatus === 'SUBMITTED'" style="margin-top: 14px" justify="end">
          <NInput v-model:value="auditOpinion" placeholder="审核意见（可选）" style="width: 300px" />
          <NButton type="error" secondary @click="onAudit(false)">退回修改</NButton>
          <NButton type="primary" @click="onAudit(true)">通过并归档</NButton>
        </NSpace>
      </template>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { h, reactive, ref, resolveComponent } from 'vue';
import { NButton, NDescriptions, NDescriptionsItem, NFormItem, NInput, NPopconfirm, NSpace, useMessage } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import { conclusionApi } from '../api/conclusion';
import TopicSelect from '../components/TopicSelect.vue';
import type { ResTopicConclusionVo } from '../typings';

const HDictText = resolveComponent('HDictText');
const message = useMessage();

const tableRef = ref<any>(null);

const queryParams = reactive({
  keyword: null as string | null,
  conclusionStatus: null as string | null,
});

const formShow = ref(false);
const editVo = ref<ResTopicConclusionVo>({});

const rules = {
  topicId: { required: true, message: '请选择课题', trigger: ['blur', 'change'] },
  process: { required: true, message: '请输入研究过程总结', trigger: ['blur', 'input'] },
  achievement: { required: true, message: '请输入研究成果总结', trigger: ['blur', 'input'] },
};

const columns: any[] = [
  { title: '课题编号', key: 'topicNo', width: 110 },
  { title: '课题名称', key: 'topicName', minWidth: 200, columnType: 'title', ellipsis: { tooltip: true } },
  { title: '负责人', key: 'leaderName', width: 90 },
  {
    title: '审核状态', key: 'conclusionStatus', width: 100,
    render: (r: ResTopicConclusionVo) => h(HDictText, { type: 'research_conclusion_status', value: r.conclusionStatus }),
  },
  { title: '审核意见', key: 'reviewOpinion', minWidth: 160, ellipsis: { tooltip: true } },
  { title: '提交时间', key: 'createTime', width: 170, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 190,
    fixed: 'right' as const,
    render: (row: ResTopicConclusionVo) => {
      const buttons = [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
      ];
      if (row.conclusionStatus !== 'PASS') {
        buttons.push(
          h(NButton, { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onEdit(row) }, () => '编辑'),
          h(
            NPopconfirm,
            { onPositiveClick: () => onDelete(row) },
            {
              trigger: () => h(NButton, { text: true, type: 'error', size: 'small', style: 'margin-left:10px' }, () => '删除'),
              default: () => '确定删除该结项报告？课题将退回在研阶段',
            },
          ),
        );
      }
      return buttons;
    },
  },
];

const onAdd = () => {
  editVo.value = {};
  formShow.value = true;
};

const onEdit = (row: ResTopicConclusionVo) => {
  editVo.value = cloneDeep(row);
  formShow.value = true;
};

const viewShow = ref(false);
const viewRow = ref<ResTopicConclusionVo | null>(null);
const auditOpinion = ref('');
const onView = (row: ResTopicConclusionVo) => {
  viewRow.value = row;
  auditOpinion.value = '';
  viewShow.value = true;
};

async function onAudit(pass: boolean) {
  if (!viewRow.value?.id) return;
  await conclusionApi.audit(viewRow.value.id, pass, auditOpinion.value || undefined);
  message.success(pass ? '结项通过，课题已归档' : '已退回修改');
  viewShow.value = false;
  tableRef.value?.handleQuery?.();
}

async function onDelete(row: ResTopicConclusionVo) {
  await conclusionApi.delete(row.id!);
  message.success('已删除');
  tableRef.value?.handleQuery?.();
}

function handleSubmit() {
  return conclusionApi.save(editVo.value);
}
</script>

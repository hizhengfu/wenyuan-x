<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="transformApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    :is-add="mayManage"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="项目 / 成果名称" style="width: 200px" />
      </NFormItem>
      <NFormItem label="类型">
        <HDictSelect v-model:value="queryParams.transformType" type="research_transform_type" clearable placeholder="转化类型" style="width: 130px" />
      </NFormItem>
      <NFormItem label="状态">
        <HDictSelect v-model:value="queryParams.transformStatus" type="research_transform_status" clearable placeholder="状态" style="width: 120px" />
      </NFormItem>
    </template>

    <HPageForm
      v-model:show="formShow"
      :title="editVo.id ? '修改转化申请' : '新增转化申请'"
      :model="editVo"
      :rules="rules"
      :ref-table="tableRef"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="100"
      width="680px"
    >
      <NFormItem label="研究成果" path="achievementId">
        <AchievementSelect v-model:value="editVo.achievementId" placeholder="选择已通过评审的成果" />
      </NFormItem>
      <NFormItem label="项目名称" path="name">
        <NInput v-model:value="editVo.name" placeholder="如：××专题展 / ××研学课程 / ××图录出版" />
      </NFormItem>
      <NFormItem label="转化类型" path="transformType">
        <HDictSelect v-model:value="editVo.transformType" type="research_transform_type" style="width: 220px" />
      </NFormItem>
      <NFormItem label="计划启动" path="startDate">
        <NDatePicker v-model:formatted-value="editVo.startDate" value-format="yyyy-MM-dd" type="date" clearable style="width: 220px" />
      </NFormItem>
      <NFormItem label="转化方案" path="description">
        <NInput v-model:value="editVo.description" type="textarea" :rows="4" placeholder="转化目标、实施方式、预期效果" />
      </NFormItem>
    </HPageForm>

    <HPageView v-model:show="viewShow" title="转化项目详情" width="720px">
      <template v-if="viewRow">
        <NDescriptions label-placement="left" :column="1" bordered size="small">
          <NDescriptionsItem label="项目名称">{{ viewRow.name }}</NDescriptionsItem>
          <NDescriptionsItem label="研究成果">{{ viewRow.achievementNo }} {{ viewRow.achievementTitle }}</NDescriptionsItem>
          <NDescriptionsItem label="转化类型"><HDictText type="research_transform_type" :value="viewRow.transformType" /></NDescriptionsItem>
          <NDescriptionsItem label="状态"><HDictText type="research_transform_status" :value="viewRow.transformStatus" /></NDescriptionsItem>
          <NDescriptionsItem label="起止">{{ viewRow.startDate || '?' }} ~ {{ viewRow.endDate || '进行中' }}</NDescriptionsItem>
          <NDescriptionsItem label="转化方案">{{ viewRow.description || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="过程与效果">{{ viewRow.effect || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="转化报告">{{ viewRow.report || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="审核意见">{{ viewRow.approveOpinion || '--' }}</NDescriptionsItem>
        </NDescriptions>

        <NSpace v-if="viewRow.transformStatus === 'APPLYING' && mayApprove && viewRow.createBy !== authStore.userInfo?.id" style="margin-top: 14px" justify="end">
          <NInput v-model:value="actionOpinion" placeholder="审核意见（可选）" style="width: 280px" />
          <NButton type="error" secondary @click="onApprove(false)">不通过</NButton>
          <NButton type="primary" @click="onApprove(true)">立项</NButton>
        </NSpace>

        <template v-if="viewRow.transformStatus === 'APPROVED' && mayManage">
          <NDivider style="margin: 14px 0 10px">过程跟踪 / 办理完成</NDivider>
          <NSpace vertical>
            <NSpace>
              <NInput v-model:value="progressNote" placeholder="进展说明（如：展览开幕，首周观众 1.2 万人次）" style="width: 420px" />
              <NButton secondary @click="onTrack">登记进展</NButton>
            </NSpace>
            <NInput v-model:value="completeEffect" type="textarea" :rows="2" placeholder="转化效果（观众量/发行量/社会反响等）" />
            <NInput v-model:value="completeReport" type="textarea" :rows="3" placeholder="成果转化报告" />
            <NSpace justify="end">
              <NButton type="primary" @click="onComplete">办理完成</NButton>
            </NSpace>
          </NSpace>
        </template>
      </template>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref, resolveComponent } from 'vue';
import { NButton, NDatePicker, NDescriptions, NDescriptionsItem, NDivider, NFormItem, NInput, NPopconfirm, NSpace, useMessage } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import { useAuthStore } from '@/store';
import { transformApi } from '../api/transform';
import AchievementSelect from '../components/AchievementSelect.vue';
import type { ResTransformVo } from '../typings';

const HDictText = resolveComponent('HDictText');
const message = useMessage();
const authStore = useAuthStore();
const mayManage = computed(() => authStore.hasPermission('ADMIN,RESEARCH_ADMIN,RESEARCH_TRANSFORM_MANAGE'));
const mayApprove = computed(() => authStore.hasPermission('ADMIN,RESEARCH_ADMIN,RESEARCH_TRANSFORM_APPROVE'));

const tableRef = ref<any>(null);

const queryParams = reactive({
  keyword: null as string | null,
  transformType: null as string | null,
  transformStatus: null as string | null,
});

const formShow = ref(false);
const editVo = ref<ResTransformVo>({});

const rules = {
  achievementId: { required: true, message: '请选择成果', trigger: ['blur', 'change'] },
  name: { required: true, message: '请输入项目名称', trigger: ['blur', 'input'] },
  transformType: { required: true, message: '请选择转化类型', trigger: ['blur', 'change'] },
  description: { required: true, message: '请输入转化方案', trigger: ['blur', 'input'] },
};

const columns: any[] = [
  { title: '项目名称', key: 'name', minWidth: 180, columnType: 'title', ellipsis: { tooltip: true } },
  { title: '成果编号', key: 'achievementNo', width: 110 },
  { title: '研究成果', key: 'achievementTitle', minWidth: 170, ellipsis: { tooltip: true } },
  {
    title: '类型', key: 'transformType', width: 90,
    render: (r: ResTransformVo) => h(HDictText, { type: 'research_transform_type', value: r.transformType }),
  },
  {
    title: '状态', key: 'transformStatus', width: 90,
    render: (r: ResTransformVo) => h(HDictText, { type: 'research_transform_status', value: r.transformStatus }),
  },
  { title: '启动', key: 'startDate', width: 105 },
  { title: '完成', key: 'endDate', width: 105 },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 190,
    fixed: 'right' as const,
    render: (row: ResTransformVo) => {
      const buttons = [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
      ];
      if (mayManage.value && (row.transformStatus === 'APPLYING' || row.transformStatus === 'REJECTED')) {
        buttons.push(
          h(NButton, { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onEdit(row) }, () => '编辑'),
          h(
            NPopconfirm,
            { onPositiveClick: () => onDelete(row) },
            {
              trigger: () => h(NButton, { text: true, type: 'error', size: 'small', style: 'margin-left:10px' }, () => '删除'),
              default: () => '确定删除该转化申请？',
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

const onEdit = (row: ResTransformVo) => {
  editVo.value = cloneDeep(row);
  formShow.value = true;
};

const viewShow = ref(false);
const viewRow = ref<ResTransformVo | null>(null);
const actionOpinion = ref('');
const progressNote = ref('');
const completeEffect = ref('');
const completeReport = ref('');

const onView = (row: ResTransformVo) => {
  viewRow.value = row;
  actionOpinion.value = '';
  progressNote.value = '';
  completeEffect.value = row.effect || '';
  completeReport.value = row.report || '';
  viewShow.value = true;
};

async function onApprove(pass: boolean) {
  if (!viewRow.value?.id) return;
  await transformApi.approve(viewRow.value.id, pass, actionOpinion.value || undefined);
  message.success(pass ? '已立项' : '未通过');
  viewShow.value = false;
  tableRef.value?.handleQuery?.();
}

async function onTrack() {
  if (!viewRow.value?.id || !progressNote.value.trim()) {
    message.warning('请填写进展说明');
    return;
  }
  await transformApi.track(viewRow.value.id, progressNote.value.trim());
  message.success('进展已登记');
  progressNote.value = '';
  const resp: any = await transformApi.get(viewRow.value.id);
  viewRow.value = resp?.data ?? viewRow.value;
}

async function onComplete() {
  if (!viewRow.value?.id) return;
  if (!completeEffect.value.trim() || !completeReport.value.trim()) {
    message.warning('请填写转化效果和成果转化报告');
    return;
  }
  await transformApi.complete(viewRow.value.id, completeEffect.value || undefined, completeReport.value || undefined);
  message.success('转化已完成');
  viewShow.value = false;
  tableRef.value?.handleQuery?.();
}

async function onDelete(row: ResTransformVo) {
  await transformApi.delete(row.id!);
  message.success('已删除');
  tableRef.value?.handleQuery?.();
}

function handleSubmit() {
  return transformApi.save(editVo.value);
}
</script>

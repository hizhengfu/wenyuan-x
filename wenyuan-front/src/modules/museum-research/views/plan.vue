<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="planApi.page"
    :fun-api-delete="planApi.delete"
    :params="queryParams"
    row-key-field="id"
    @add="onAdd"
    @row-edit="onEdit"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="课题">
        <TopicSelect v-model:value="queryParams.topicId" style="width: 240px" />
      </NFormItem>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="阶段 / 人员 / 目标" style="width: 190px" />
      </NFormItem>
      <NFormItem label="状态">
        <HDictSelect v-model:value="queryParams.planStatus" type="research_plan_status" clearable placeholder="状态" style="width: 120px" />
      </NFormItem>
      <NFormItem label="仅看预警">
        <NSwitch v-model:value="queryParams.onlyDelayed" />
      </NFormItem>
    </template>

    <HPageForm
      v-model:show="formShow"
      :title="editVo.id ? '变更实施计划' : '新增实施计划'"
      :model="editVo"
      :rules="rules"
      :ref-table="tableRef"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="100"
      width="680px"
    >
      <NFormItem label="课题" path="topicId">
        <TopicSelect v-model:value="editVo.topicId" stage="RESEARCH" placeholder="选择在研课题" />
      </NFormItem>
      <NFormItem label="阶段名称" path="name">
        <NInput v-model:value="editVo.name" placeholder="如：资料收集 / 田野调查 / 初稿撰写" />
      </NFormItem>
      <NFormItem label="开始时间" path="startDate">
        <NDatePicker v-model:formatted-value="editVo.startDate" value-format="yyyy-MM-dd" type="date" clearable style="width: 220px" />
      </NFormItem>
      <NFormItem label="节点时间" path="endDate">
        <NDatePicker v-model:formatted-value="editVo.endDate" value-format="yyyy-MM-dd" type="date" clearable style="width: 220px" />
      </NFormItem>
      <NFormItem label="阶段目标" path="goal">
        <NInput v-model:value="editVo.goal" type="textarea" :rows="2" />
      </NFormItem>
      <NFormItem label="人员分工" path="members">
        <NInput v-model:value="editVo.members" type="textarea" :rows="2" placeholder="张三：资料整理；李四：数据分析" />
      </NFormItem>
      <NFormItem label="进度(%)" path="progress">
        <NInputNumber v-model:value="editVo.progress" :min="0" :max="100" style="width: 160px" />
      </NFormItem>
      <NFormItem label="状态" path="planStatus">
        <HDictSelect v-model:value="editVo.planStatus" type="research_plan_status" clearable style="width: 200px" />
      </NFormItem>
      <NFormItem label="调整说明" path="remark">
        <NInput v-model:value="editVo.remark" type="textarea" :rows="2" placeholder="计划变更/延期原因说明" />
      </NFormItem>
    </HPageForm>

    <HPageView v-model:show="viewShow" title="实施计划详情" width="640px">
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="课题">{{ viewRow.topicNo }} {{ viewRow.topicName }}</NDescriptionsItem>
        <NDescriptionsItem label="阶段名称">{{ viewRow.name || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="时间">{{ viewRow.startDate || '?' }} ~ {{ viewRow.endDate || '?' }}</NDescriptionsItem>
        <NDescriptionsItem label="阶段目标">{{ viewRow.goal || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="人员分工">{{ viewRow.members || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="进度">{{ viewRow.progress ?? 0 }}%</NDescriptionsItem>
        <NDescriptionsItem label="状态"><HDictText type="research_plan_status" :value="viewRow.planStatus" /></NDescriptionsItem>
        <NDescriptionsItem label="实际完成">{{ viewRow.actualDate || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="调整说明">{{ viewRow.remark || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { h, reactive, ref, resolveComponent } from 'vue';
import { NDatePicker, NDescriptions, NDescriptionsItem, NFormItem, NInput, NInputNumber, NProgress, NSwitch, NTag } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import { useRoute } from 'vue-router';
import { planApi } from '../api/plan';
import TopicSelect from '../components/TopicSelect.vue';
import type { ResTopicPlanVo } from '../typings';

const HDictText = resolveComponent('HDictText');
const route = useRoute();

const tableRef = ref<any>(null);

const queryParams = reactive({
  topicId: (route.query.topicId as string) || (null as string | null),
  keyword: null as string | null,
  planStatus: null as string | null,
  onlyDelayed: false,
});

const formShow = ref(false);
const emptyVo: ResTopicPlanVo = { progress: 0, planStatus: 'PENDING' };
const editVo = ref<ResTopicPlanVo>({ ...emptyVo });

const rules = {
  topicId: { required: true, message: '请选择课题', trigger: ['blur', 'change'] },
  name: { required: true, message: '请输入阶段名称', trigger: ['blur', 'input'] },
};

const columns: any[] = [
  { title: '课题编号', key: 'topicNo', width: 110 },
  { title: '课题名称', key: 'topicName', minWidth: 160, ellipsis: { tooltip: true } },
  { title: '阶段名称', key: 'name', minWidth: 140, columnType: 'title', ellipsis: { tooltip: true } },
  { title: '开始', key: 'startDate', width: 105 },
  {
    title: '节点',
    key: 'endDate',
    width: 130,
    render: (r: ResTopicPlanVo) =>
      h('span', {}, [
        r.endDate || '—',
        r.planStatus === 'DELAYED'
          ? h(NTag, { type: 'error', size: 'small', bordered: false, style: 'margin-left:6px' }, () => '逾期')
          : null,
      ]),
  },
  {
    title: '进度',
    key: 'progress',
    width: 130,
    render: (r: ResTopicPlanVo) => h(NProgress, { type: 'line', percentage: r.progress ?? 0, height: 8 }),
  },
  {
    title: '状态', key: 'planStatus', width: 90,
    render: (r: ResTopicPlanVo) => h(HDictText, { type: 'research_plan_status', value: r.planStatus }),
  },
  { title: '人员分工', key: 'members', minWidth: 150, ellipsis: { tooltip: true } },
];

const onAdd = () => {
  editVo.value = { ...cloneDeep(emptyVo), topicId: queryParams.topicId || undefined };
  formShow.value = true;
};

const onEdit = (row: ResTopicPlanVo) => {
  editVo.value = cloneDeep(row);
  formShow.value = true;
};

const viewShow = ref(false);
const viewRow = ref<ResTopicPlanVo | null>(null);
const onView = (row: ResTopicPlanVo) => {
  viewRow.value = row;
  viewShow.value = true;
};

function handleSubmit() {
  return planApi.save(editVo.value);
}
</script>

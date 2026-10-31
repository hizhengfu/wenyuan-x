<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="evaluationApi.page"
    :fun-api-delete="evaluationApi.delete"
    :params="queryParams"
    :is-edit="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="成果">
        <AchievementSelect v-model:value="queryParams.achievementId" style="width: 240px" />
      </NFormItem>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="成果名称" style="width: 180px" />
      </NFormItem>
      <NFormItem label="评价方式">
        <HDictSelect v-model:value="queryParams.evalType" type="research_eval_type" clearable placeholder="方式" style="width: 130px" />
      </NFormItem>
    </template>

    <HPageForm
      v-model:show="formShow"
      title="新增成果评价"
      :model="editVo"
      :rules="rules"
      :ref-table="tableRef"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="90"
      width="640px"
    >
      <NFormItem label="成果" path="achievementId">
        <AchievementSelect v-model:value="editVo.achievementId" placeholder="选择已通过评审的成果" />
      </NFormItem>
      <NFormItem label="评价方式" path="evalType">
        <HDictSelect v-model:value="editVo.evalType" type="research_eval_type" style="width: 220px" />
      </NFormItem>
      <NFormItem label="评分" path="score">
        <NInputNumber v-model:value="editVo.score" :min="0" :max="100" style="width: 220px" />
      </NFormItem>
      <NFormItem label="评价人">
        <span>提交时以当前登录人署名，仅本人可修改或删除评价。</span>
      </NFormItem>
      <NFormItem label="评价意见" path="opinion">
        <NInput v-model:value="editVo.opinion" type="textarea" :rows="3" />
      </NFormItem>
      <NFormItem label="改进建议" path="suggestion">
        <NInput v-model:value="editVo.suggestion" type="textarea" :rows="2" placeholder="为研究人员提供改进方向" />
      </NFormItem>
    </HPageForm>

    <HPageView v-model:show="viewShow" title="评价详情" width="620px">
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="成果">{{ viewRow.achievementTitle || viewRow.achievementId }}</NDescriptionsItem>
        <NDescriptionsItem label="评价方式"><HDictText type="research_eval_type" :value="viewRow.evalType" /></NDescriptionsItem>
        <NDescriptionsItem label="评分">{{ viewRow.score }}</NDescriptionsItem>
        <NDescriptionsItem label="评价人">{{ viewRow.evaluatorName || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="评价意见">{{ viewRow.opinion || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="改进建议">{{ viewRow.suggestion || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="评价时间">{{ viewRow.createTime || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { h, reactive, ref, resolveComponent } from 'vue';
import { NDescriptions, NDescriptionsItem, NFormItem, NInput, NInputNumber } from 'naive-ui';
import { evaluationApi } from '../api/evaluation';
import AchievementSelect from '../components/AchievementSelect.vue';
import type { ResAchievementEvalVo } from '../typings';

const HDictText = resolveComponent('HDictText');

const tableRef = ref<any>(null);

const queryParams = reactive({
  achievementId: null as string | null,
  keyword: null as string | null,
  evalType: null as string | null,
});

const formShow = ref(false);
const emptyVo: ResAchievementEvalVo = { evalType: 'EXPERT', score: 80 };
const editVo = ref<ResAchievementEvalVo>({ ...emptyVo });

const rules = {
  achievementId: { required: true, message: '请选择成果', trigger: ['blur', 'change'] },
  score: { type: 'number' as const, required: true, message: '请填写评分', trigger: ['blur', 'change'] },
};

const columns: any[] = [
  { title: '成果名称', key: 'achievementTitle', minWidth: 200, columnType: 'title', ellipsis: { tooltip: true } },
  {
    title: '评价方式', key: 'evalType', width: 100,
    render: (r: ResAchievementEvalVo) => h(HDictText, { type: 'research_eval_type', value: r.evalType }),
  },
  { title: '评分', key: 'score', width: 70 },
  { title: '评价人', key: 'evaluatorName', width: 100 },
  { title: '评价意见', key: 'opinion', minWidth: 200, ellipsis: { tooltip: true } },
  { title: '评价时间', key: 'createTime', width: 170, columnType: 'dateTime' },
];

const onAdd = () => {
  editVo.value = { ...emptyVo, achievementId: queryParams.achievementId || undefined };
  formShow.value = true;
};

const viewShow = ref(false);
const viewRow = ref<ResAchievementEvalVo | null>(null);
const onView = (row: ResAchievementEvalVo) => {
  viewRow.value = row;
  viewShow.value = true;
};

function handleSubmit() {
  return evaluationApi.save(editVo.value);
}
</script>

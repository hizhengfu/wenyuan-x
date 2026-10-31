<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="dataAccessApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    :is-add="mayApply"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="申请人 / 机构 / 数据范围" style="width: 230px" />
      </NFormItem>
      <NFormItem label="授权状态">
        <HDictSelect v-model:value="queryParams.accessStatus" type="research_data_access_status" clearable placeholder="状态" style="width: 130px" />
      </NFormItem>
    </template>

    <HPageForm
      v-model:show="formShow"
      :title="editVo.id ? '修改数据使用申请' : '新增数据使用申请'"
      :model="editVo"
      :rules="rules"
      :ref-table="tableRef"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="100"
      width="700px"
    >
      <NFormItem label="申请人" path="applicant">
        <NInput v-model:value="editVo.applicant" style="width: 220px" />
      </NFormItem>
      <NFormItem label="申请机构" path="applicantOrg">
        <NInput v-model:value="editVo.applicantOrg" placeholder="合作机构/高校/科研院所" />
      </NFormItem>
      <NFormItem label="联系方式" path="contact">
        <NInput v-model:value="editVo.contact" style="width: 260px" />
      </NFormItem>
      <NFormItem label="数据范围" path="dataScope">
        <NInput v-model:value="editVo.dataScope" type="textarea" :rows="3" placeholder="申请访问的藏品研究数据范围说明" />
      </NFormItem>
      <NFormItem label="关联课题" path="topicId">
        <TopicSelect v-model:value="editVo.topicId" placeholder="（可选）" />
      </NFormItem>
      <NFormItem label="关联成果" path="achievementId">
        <AchievementSelect v-model:value="editVo.achievementId" :passed-only="false" placeholder="（可选）" />
      </NFormItem>
      <NFormItem label="使用目的" path="purpose">
        <NInput v-model:value="editVo.purpose" type="textarea" :rows="2" />
      </NFormItem>
      <NFormItem label="使用协议" path="agreementFileId">
        <HUploadInput v-model:value="editVo.agreementFileId" @upload-finish="editVo.agreementFileId = $event.id" clearable placeholder="上传数据使用协议（明确成果归属与传播范围）" />
      </NFormItem>
    </HPageForm>

    <HPageView v-model:show="viewShow" title="数据使用申请详情" width="700px">
      <template v-if="viewRow">
        <NDescriptions label-placement="left" :column="1" bordered size="small">
          <NDescriptionsItem label="申请人">{{ viewRow.applicant }}（{{ viewRow.applicantOrg || '—' }}）</NDescriptionsItem>
          <NDescriptionsItem label="联系方式">{{ viewRow.contact || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="数据范围">{{ viewRow.dataScope }}</NDescriptionsItem>
          <NDescriptionsItem label="使用目的">{{ viewRow.purpose || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="授权状态">
            <HDictText type="research_data_access_status" :value="viewRow.accessStatus" />
            <NTag v-if="viewRow.expired" type="warning" size="small" :bordered="false" style="margin-left: 8px">已过期</NTag>
          </NDescriptionsItem>
          <NDescriptionsItem label="授权期限">{{ viewRow.validFrom || '?' }} ~ {{ viewRow.validTo || '?' }}</NDescriptionsItem>
          <NDescriptionsItem label="审批意见">{{ viewRow.approveOpinion || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="申请时间">{{ viewRow.createTime || '--' }}</NDescriptionsItem>
        </NDescriptions>

        <NSpace v-if="viewRow.accessStatus === 'APPLYING' && mayApprove && viewRow.createBy !== authStore.userInfo?.id" vertical style="margin-top: 14px">
          <NSpace>
            <NDatePicker v-model:formatted-value="approveForm.validFrom" value-format="yyyy-MM-dd" type="date" placeholder="授权起始日" style="width: 160px" />
            <NDatePicker v-model:formatted-value="approveForm.validTo" value-format="yyyy-MM-dd" type="date" placeholder="授权截止日" style="width: 160px" />
            <NInput v-model:value="approveForm.opinion" placeholder="审批意见（可选）" style="width: 240px" />
          </NSpace>
          <NSpace justify="end">
            <NButton type="error" secondary @click="onApprove(false)">拒绝</NButton>
            <NButton type="primary" @click="onApprove(true)">授权</NButton>
          </NSpace>
        </NSpace>
      </template>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref, resolveComponent } from 'vue';
import { NButton, NDatePicker, NDescriptions, NDescriptionsItem, NFormItem, NInput, NPopconfirm, NSpace, NTag, useMessage } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import { useAuthStore } from '@/store';
import { dataAccessApi } from '../api/dataAccess';
import TopicSelect from '../components/TopicSelect.vue';
import AchievementSelect from '../components/AchievementSelect.vue';
import type { ResDataAccessVo } from '../typings';

const HDictText = resolveComponent('HDictText');
const message = useMessage();
const authStore = useAuthStore();
const mayApply = computed(() => authStore.hasPermission('ADMIN,RESEARCH_ADMIN,RESEARCH_DATA_ACCESS'));
const mayApprove = computed(() => authStore.hasPermission('ADMIN,RESEARCH_ADMIN,RESEARCH_DATA_ACCESS_APPROVE'));

const tableRef = ref<any>(null);

const queryParams = reactive({
  keyword: null as string | null,
  accessStatus: null as string | null,
});

const formShow = ref(false);
const editVo = ref<ResDataAccessVo>({});

const rules = {
  applicant: { required: true, message: '请输入申请人', trigger: ['blur', 'input'] },
  dataScope: { required: true, message: '请输入数据范围', trigger: ['blur', 'input'] },
  purpose: { required: true, message: '请输入使用目的', trigger: ['blur', 'input'] },
};

const columns: any[] = [
  { title: '申请人', key: 'applicant', width: 100, columnType: 'title' },
  { title: '申请机构', key: 'applicantOrg', minWidth: 160, ellipsis: { tooltip: true } },
  { title: '数据范围', key: 'dataScope', minWidth: 200, ellipsis: { tooltip: true } },
  {
    title: '授权状态', key: 'accessStatus', width: 110,
    render: (r: ResDataAccessVo) =>
      h('span', {}, [
        h(HDictText, { type: 'research_data_access_status', value: r.accessStatus }),
        r.expired ? h(NTag, { type: 'warning', size: 'small', bordered: false, style: 'margin-left:6px' }, () => '过期') : null,
      ]),
  },
  { title: '起始', key: 'validFrom', width: 105 },
  { title: '截止', key: 'validTo', width: 105 },
  { title: '申请时间', key: 'createTime', width: 170, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 200,
    fixed: 'right' as const,
    render: (row: ResDataAccessVo) => {
      const buttons = [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
      ];
      if (row.accessStatus === 'APPLYING' && mayApply.value) {
        buttons.push(
          h(NButton, { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onEdit(row) }, () => '编辑'),
        );
      }
      if (row.accessStatus === 'APPROVED' && mayApprove.value) {
        buttons.push(
          h(
            NPopconfirm,
            { onPositiveClick: () => onRevoke(row) },
            {
              trigger: () => h(NButton, { text: true, type: 'warning', size: 'small', style: 'margin-left:10px' }, () => '撤销授权'),
              default: () => '确定撤销该数据使用授权？',
            },
          ),
        );
      } else if (mayApply.value) {
        buttons.push(
          h(
            NPopconfirm,
            { onPositiveClick: () => onDelete(row) },
            {
              trigger: () => h(NButton, { text: true, type: 'error', size: 'small', style: 'margin-left:10px' }, () => '删除'),
              default: () => '确定删除该申请？',
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

const onEdit = (row: ResDataAccessVo) => {
  editVo.value = cloneDeep(row);
  formShow.value = true;
};

const viewShow = ref(false);
const viewRow = ref<ResDataAccessVo | null>(null);
const approveForm = reactive({ validFrom: null as string | null, validTo: null as string | null, opinion: '' });

const onView = (row: ResDataAccessVo) => {
  viewRow.value = row;
  approveForm.validFrom = null;
  approveForm.validTo = null;
  approveForm.opinion = '';
  viewShow.value = true;
};

async function onApprove(pass: boolean) {
  if (!viewRow.value?.id) return;
  if (pass && !approveForm.validTo) {
    message.warning('授权须设定截止日期');
    return;
  }
  await dataAccessApi.approve(
    viewRow.value.id,
    pass,
    approveForm.opinion || undefined,
    approveForm.validFrom || undefined,
    approveForm.validTo || undefined,
  );
  message.success(pass ? '已授权' : '已拒绝');
  viewShow.value = false;
  tableRef.value?.handleQuery?.();
}

async function onRevoke(row: ResDataAccessVo) {
  await dataAccessApi.revoke(row.id!);
  message.success('已撤销授权');
  tableRef.value?.handleQuery?.();
}

async function onDelete(row: ResDataAccessVo) {
  await dataAccessApi.delete(row.id!);
  message.success('已删除');
  tableRef.value?.handleQuery?.();
}

function handleSubmit() {
  return dataAccessApi.save(editVo.value);
}
</script>

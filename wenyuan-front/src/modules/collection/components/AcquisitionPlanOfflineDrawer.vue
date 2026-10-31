<template>
  <HPageForm
    content-only
    :enable-cancel="false"
    v-model:show="show"
    :title="readOnly ? '线下已批准计划档案' : '线下已批准计划归档'"
  >
    <NAlert type="info"
      >仅用于登记已完成线下批准的计划。填写实际批准时间、批准依据并上传签署原件；尚未批准的计划请使用线上审批。归档后可据此发起征集，实际接收和正式登记仍须分别办理。</NAlert
    >
    <NAlert v-if="error" type="error" style="margin-top: 12px">{{ error }}</NAlert>
    <NSpin :show="busy"
      ><NForm v-if="!reading && readReady" label-placement="top" :disabled="readOnly || busy" class="offline-plan-form">
        <NFormItem label="计划名称" required><NInput v-model:value="form.title" /></NFormItem>
        <div class="period-fields">
          <NFormItem label="计划周期" required
            ><NSelect
              v-model:value="form.planType"
              :options="[
                { label: '年度', value: 'YEAR' },
                { label: '月度', value: 'MONTH' },
              ]" /></NFormItem
          ><NFormItem label="年度" required
            ><NInputNumber v-model:value="form.year" :min="1" :max="9999" :precision="0" /></NFormItem
          ><NFormItem v-if="form.planType === 'MONTH'" label="月份" required
            ><NInputNumber v-model:value="form.month" :min="1" :max="12" :precision="0"
          /></NFormItem>
        </div>
        <NFormItem label="征集方向及说明"><NInput v-model:value="form.content" type="textarea" /></NFormItem>
        <NFormItem label="预算（元）"
          ><NInputNumber v-model:value="budgetYuan" :min="0" :precision="yuanInputPrecision(originalBudget)"
        /></NFormItem>
        <NFormItem v-if="!readOnly" label="其他计划材料"
          ><HUploadInput v-model:value="form.attachmentIds" clearable @upload-finish="form.attachmentIds = $event.id"
        /></NFormItem>
        <NFormItem label="实际批准时间" required
          ><NDatePicker v-model:value="approval.approvedAt" type="datetime"
        /></NFormItem>
        <NFormItem label="批准人或批准机构" required><NInput v-model:value="approval.approvedBy" /></NFormItem>
        <NFormItem label="批准文号或批准依据" required
          ><NInput v-model:value="approval.approvalBasis" type="textarea"
        /></NFormItem>
        <NFormItem v-if="!readOnly" label="已签署批准的计划原件" required
          ><HUploadInput
            v-model:value="approval.signedFileIds"
            clearable
            @upload-finish="approval.signedFileIds = $event.id"
        /></NFormItem>
        <NFormItem label="补录原因（批准时间早于建档时间时必填）"
          ><NInput
            v-model:value="approval.supplementaryReason"
            type="textarea"
            placeholder="说明此前已完成线下批准及本次补录原因"
        /></NFormItem> </NForm
    ></NSpin>
    <NText v-if="readOnly" depth="3">批准事实和签署原件已封存。调整应另立计划，保留原批准依据。</NText>
    <template #form-action>
      <NSpace justify="end"
        ><NButton
          v-if="!readOnly && !reading && readReady"
          v-auth="'ADMIN,COLLECTION_ADMIN,COLLECTION_ACQUIRE'"
          type="primary"
          :loading="busy"
          @click="archive"
          >保存批准事实并归档</NButton
        ><OriginalHistoryButton
          v-if="form.id"
          business-type="COLLECTION_ACQUISITION_PLAN"
          :business-id="form.id"
          label="查阅留存原件"
        /><NButton v-if="error && form.id" :disabled="busy" @click="open(form.id)">重新核对计划</NButton
        ><NButton @click="show = false">关闭</NButton></NSpace
      >
    </template>
  </HPageForm>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { useAuthStore } from '@/store';
import { acquisitionPlanApi } from '../api/acquisitionPlan';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { centsToYuan, yuanFormToCents, yuanInputPrecision } from '../utils/moneyYuan';
import OriginalHistoryButton from './OriginalHistoryButton.vue';
import type { AcquisitionPlanVo, AcquisitionPlanOfflineApproval } from '../typings/collection';
const emit = defineEmits<{ (event: 'saved'): void }>(),
  message = useMessage(),
  auth = useAuthStore();
const show = ref(false),
  form = ref<AcquisitionPlanVo>({}),
  approval = ref<AcquisitionPlanOfflineApproval>({}),
  error = ref(''),
  reading = ref(false),
  readReady = ref(false),
  budgetYuan = ref<number | null>(null),
  originalBudget = ref<number | undefined>();
const readOnly = computed(() => form.value.flowStatus === 'PASS' || !!form.value.offlineApproval);
const session = useCatalogOperation(() => {
    form.value = {};
    approval.value = {};
    error.value = '';
    reading.value = false;
    readReady.value = false;
    budgetYuan.value = null;
    originalBudget.value = undefined;
  }),
  busy = session.busy;
watch(
  show,
  (value) => {
    if (!value) session.reset();
  },
  { flush: 'sync' },
);
watch(
  () => [auth.token, auth.userInfo?.id, auth.permissions, auth.roles],
  () => {
    show.value = false;
    session.reset();
  },
  { deep: true, flush: 'sync' },
);
async function open(id?: string) {
  session.reset();
  show.value = true;
  const ticket = session.begin('read');
  if (ticket === null) return;
  reading.value = true;
  try {
    const response: any = id ? await acquisitionPlanApi.get(id) : null;
    if (!session.current(ticket) || !show.value) return;
    if (id && response?.data?.id !== id) throw new Error('计划读取失败，请刷新列表并核对权限');
    form.value = response?.data || { planType: 'YEAR', year: new Date().getFullYear() };
    approval.value = { ...(form.value.offlineApproval || {}) };
    readReady.value = true;
    originalBudget.value = form.value.budget;
    budgetYuan.value = centsToYuan(form.value.budget);
  } catch (e: any) {
    if (session.current(ticket)) error.value = e?.message || '计划读取失败';
  } finally {
    if (session.current(ticket)) reading.value = false;
    session.finish(ticket);
  }
}
async function archive() {
  if (!readReady.value || readOnly.value || busy.value || reading.value) return;
  if (
    !form.value.title?.trim() ||
    !form.value.year ||
    (form.value.planType === 'MONTH' && !(Number(form.value.month) >= 1 && Number(form.value.month) <= 12)) ||
    !approval.value.approvedAt ||
    !approval.value.approvedBy?.trim() ||
    !approval.value.approvalBasis?.trim() ||
    !approval.value.signedFileIds?.trim()
  ) {
    message.warning('请核对计划名称、年度/月度、实际批准信息和签署原件');
    return;
  }
  if (approval.value.approvedAt > Date.now()) {
    message.warning('实际批准时间不能填写未来时间');
    return;
  }
  const createdAt = form.value.createTime == null ? Date.now() : new Date(form.value.createTime).getTime();
  if (!approval.value.supplementaryReason?.trim() && approval.value.approvedAt < createdAt) {
    message.warning('补录此前已经批准的计划，请说明补录原因');
    return;
  }
  const ticket = session.begin('archive');
  if (ticket === null) return;
  error.value = '';
  try {
    const payload = { ...form.value, budget: yuanFormToCents(budgetYuan.value, originalBudget.value) };
    delete payload.offlineApproval;
    const saved: any = await acquisitionPlanApi.save(payload);
    if (!session.current(ticket) || !show.value) return;
    if (typeof saved?.data !== 'string' || !saved.data) throw new Error('草稿保存结果无效，请刷新列表核对');
    form.value.id = saved.data;
    emit('saved');
    const result: any = await acquisitionPlanApi.archiveOfflineApproval(saved.data, { ...approval.value });
    if (!session.current(ticket) || !show.value) return;
    if (result?.data !== saved.data) throw new Error('归档结果不一致，请重新读取计划');
    const detail: any = await acquisitionPlanApi.get(saved.data);
    if (!session.current(ticket) || !show.value) return;
    if (
      detail?.data?.id !== saved.data ||
      !detail.data.offlineApproval ||
      detail.data.flowStatus !== 'PASS' ||
      detail.data.flowInstanceId
    )
      throw new Error('无法核实已归档批准事实，请重新读取计划');
    form.value = detail.data;
    approval.value = { ...detail.data.offlineApproval };
    emit('saved');
    message.success('线下批准计划及原件已归档');
  } catch (e: any) {
    if (session.current(ticket)) error.value = e?.message || '归档失败；已保存的草稿可从列表重新打开核对';
  } finally {
    session.finish(ticket);
  }
}
defineExpose({ open });
</script>
<style scoped>
.offline-plan-form {
  margin-top: 20px;
}
.period-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
@media (max-width: 600px) {
  .period-fields {
    grid-template-columns: 1fr;
  }
}
</style>

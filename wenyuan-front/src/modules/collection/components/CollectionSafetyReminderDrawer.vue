<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="本馆安全待办站内提醒">
    <NSpace vertical :size="16">
      <NAlert type="info"
        >提醒正文仅提示进入安全待办页面。责任人员失效或尚未明确时，提醒所选协调人员落实责任安排。提前天数和重复间隔仅决定催办频率。</NAlert
      >
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NSpin :show="loading">
        <NTabs v-if="data && !error" type="line">
          <NTabPane name="settings" tab="提醒设置">
            <NAlert v-if="!data.transportAvailable" type="warning"
              >当前站内信通道不可用；已启用的提醒将保留待投递状态并重试。</NAlert
            >
            <NForm ref="formRef" :model="form" :rules="rules" label-width="160" label-placement="left" class="mt-4">
              <NFormItem label="启用站内提醒" path="enabled"><NSwitch v-model:value="form.enabled" /></NFormItem>
              <NFormItem label="提醒业务" path="domains"
                ><NSelect v-model:value="form.domains" :options="domainOptions" multiple
              /></NFormItem>
              <NFormItem label="提前提醒天数" path="leadDays"
                ><NInputNumber v-model:value="form.leadDays" :min="0" :max="365" :precision="0"
              /></NFormItem>
              <NFormItem label="重复提醒间隔（天）" path="repeatDays"
                ><NInputNumber v-model:value="form.repeatDays" :min="1" :max="365" :precision="0"
              /></NFormItem>
              <NFormItem label="协调人员"
                ><template #label><CollectionFieldLabel label="协调人员" help="协调人员需属于本馆，并有全部所选业务权限；收到提醒后仍按实际职责办理原件。" /></template><UserSelect v-model:value="form.coordinatorUserIds" multiple clearable
              /></NFormItem>
              <NSpace class="mt-4"
                ><NButton type="primary" :loading="saving" @click="save">保存提醒设置</NButton
                ><NButton :disabled="saving" @click="reload">重新读取</NButton></NSpace
              >
            </NForm>
          </NTabPane>
          <NTabPane name="history" tab="设置历史">
            <NEmpty v-if="!data.history?.length" description="尚未设置提醒" />
            <NCard v-for="row in data.history || []" :key="row.id" :title="`第 ${row.version} 版`" class="mb-3">
              <NText
                >{{ formatDateTime(row.record.recordedAt) }} · {{ row.record.request.enabled ? '启用' : '关闭' }}</NText
              >
              <NText tag="p"
                >{{ row.record.request.domains.map((v: string) => safetyDomains[v] || v).join('、') }} · 提前
                {{ row.record.request.leadDays }} 天 · 每 {{ row.record.request.repeatDays }} 天再次提醒</NText
              >
              <NText>设置人员</NText><UserSelect :value="row.record.actorUserId" disabled /> <NText>协调人员</NText
              ><UserSelect :value="row.record.request.coordinatorUserIds" multiple disabled />
            </NCard>
          </NTabPane>
          <NTabPane name="deliveries" tab="投递与重试记录">
            <NAlert type="info"
              >“站内信已入队”表示收件记录已建立。请在业务原件中确认实际办理结果；阅读消息不会办结任务。办结、换人或设置变化后，未投递的旧提醒会取消。</NAlert
            >
            <div class="mt-3" style="height: 530px">
              <HPageDataTable auto-scroll-x
                :key="openedOrg"
                title="投递记录"
                :columns="deliveryColumns"
                :fetch-data="fetchDeliveries"
                :is-add="false"
                :is-edit="false"
                :is-delete="false"
              />
            </div>
          </NTabPane>
        </NTabs>
      </NSpin>
    </NSpace>
  </HPageForm>
</template>
<script setup lang="ts">
import { validateFormSafely } from '../utils/formValidation';
import CollectionFieldLabel from './CollectionFieldLabel.vue';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import type { FormRules } from 'naive-ui';
import { safetyTaskApi } from '../api/safetyTask';
import { deliveryStatuses, safetyDomains } from '../utils/safetyTask';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import { formatDateTime } from '@/utils';
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  loading = ref(false),
  saving = ref(false),
  error = ref(''),
  data = ref<any>(null),
  openedOrg = ref(''),
  formRef = ref<any>();
const form = ref<any>({});
let token = 0;
const domainOptions = Object.entries(safetyDomains).map(([value, label]) => ({ value, label }));
const rules: FormRules = {
  domains: { required: true, type: 'array', min: 1, message: '请选择提醒业务' },
  leadDays: { required: true, type: 'number', min: 0, max: 365, message: '提前天数为 0 至 365 天' },
  repeatDays: { required: true, type: 'number', min: 1, max: 365, message: '重复间隔为 1 至 365 天' },
};
const deliveryColumns = [
  {
    title: '收件人员',
    key: 'userId',
    width: 200,
    resizable: true,
    minWidth: 160,
    render: (r: any) => h(UserSelect, { value: r.userId, disabled: true }),
  },
  { title: '状态', key: 'state', width: 220, resizable: true, ellipsis: { tooltip: true }, minWidth: 190, render: (r: any) => deliveryStatuses[r.state] || r.state },
  { title: '事项数', key: 'taskCount', width: 90 },
  { title: '批次建立时间', key: 'requestedAt', minWidth: 160, resizable: true, ellipsis: { tooltip: true }, columnType: 'dateTime', width: 190 },
  { title: '已入队时间', key: 'queuedAt', minWidth: 160, resizable: true, ellipsis: { tooltip: true }, columnType: 'dateTime', width: 190 },
  { title: '投递尝试', key: 'attempts', width: 100 },
  {
    title: '下次重试',
    key: 'nextAttemptAt',
    minWidth: 160,
    resizable: true,
    ellipsis: { tooltip: true },
    width: 190,
    render: (r: any) => (r.state === 'RETRY' ? formatDateTime(r.nextAttemptAt) : '—'),
  },
  { title: '最近异常', key: 'lastError', width: 320, resizable: true, ellipsis: { tooltip: true }, minWidth: 250 },
  {
    title: '尝试记录',
    key: 'attemptHistory',
    width: 360,
    resizable: true,
    ellipsis: { tooltip: true },
    minWidth: 260,
    render: (r: any) =>
      (r.attemptHistory || [])
        .map(
          (a: any) =>
            `${formatDateTime(a.attemptedAt)} ${a.result === 'FAILED' ? '入队失败' : deliveryStatuses[a.result] || a.result}`,
        )
        .join('；'),
  },
];
let fetchDeliveries = (params: Record<string, any>) => safetyTaskApi.deliveries(openedOrg.value, params);
async function open(orgId: string) {
  const current = ++token;
  openedOrg.value = orgId;
  data.value = null;
  form.value = {};
  error.value = '';
  show.value = true;
  loading.value = true;
  fetchDeliveries = (params) => safetyTaskApi.deliveries(orgId, params);
  try {
    const res = await safetyTaskApi.settings(orgId);
    if (current === token) {
      data.value = res.data;
      form.value = cloneDeep(res.data.settings);
    }
  } catch (e: any) {
    if (current === token) error.value = e.message || '未能读取提醒设置';
  } finally {
    if (current === token) loading.value = false;
  }
}
function reload() {
  return open(openedOrg.value);
}
async function save() {
  if (!(await validateFormSafely(formRef.value))) return;
  if (!data.value || loading.value) return;
  const current = token,
    org = openedOrg.value,
    previousId = data.value.currentId,
    value = { ...cloneDeep(form.value), previousId };
  saving.value = true;
  try {
    await safetyTaskApi.saveSettings(org, value);
    emit('saved');
    if (current === token) await open(org);
  } finally {
    saving.value = false;
  }
}
defineExpose({ open });
</script>

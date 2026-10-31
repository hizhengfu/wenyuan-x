<template>
  <div class="flex h-full min-h-0 flex-col gap-3">
    <NAlert type="info"
      >汇总检查养护、在用共同检查、借用履行和预案演练事项。请进入对应原件办理；到期时间按实际方案和记录计算。</NAlert
    >
    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <NAlert v-if="context?.enabled && !context?.transportAvailable" type="warning"
      >提醒已启用，但站内投递通道不可用，请联系管理员查看投递记录。</NAlert
    >
    <HPageDataTable auto-scroll-x
      v-if="ready"
      ref="tableRef"
      class="min-h-0"
      title="安全待办"
      :columns="columns"
      :fetch-data="safetyTaskApi.page"
      :params="query"
      :is-add="false"
      :is-edit="false"
      :is-delete="false"
      @row-view="view"
    >
      <template #queryForm>
        <NFormItem label="收藏单位"
          ><NSelect v-model:value="query.orgId" :options="orgs" clearable style="min-width: 190px"
        /></NFormItem>
        <NFormItem label="业务类型"
          ><NSelect v-model:value="query.domain" :options="domains" clearable style="min-width: 190px"
        /></NFormItem>
        <NFormItem label="办理状态"
          ><NSelect v-model:value="query.status" :options="statusOptions" clearable style="min-width: 150px"
        /></NFormItem>
        <NFormItem label="检索内容"
          ><NInput v-model:value="query.keyword" clearable placeholder="事项、藏品或单据名称"
        /></NFormItem>
      </template>
      <template #toolbar-extra
        ><NButton v-if="context?.canManage" @click="openSettings">本馆提醒设置与记录</NButton
        ><NText v-if="context">{{ context.enabled ? '本馆站内提醒已启用' : '本馆站内提醒未启用' }}</NText></template
      >
    </HPageDataTable>
    <CollectionFragilityDrawer ref="fragilityRef" @saved="refresh" />
    <CollectionCareDrawer ref="careRef" @saved="refresh" />
    <CollectionJointSafetyDrawer ref="jointRef" @saved="refresh" />
    <CollectionLoanCaseDrawer ref="loanRef" @saved="refresh" />
    <CollectionEmergencyDrawer ref="emergencyRef" @saved="refresh" />
    <CollectionSafetyReminderDrawer ref="settingsRef" @saved="refresh" />
  </div>
</template>
<script setup lang="ts">
import { h, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { NButton } from 'naive-ui';
import { safetyTaskApi } from '../api/safetyTask';
import { safetyDomains, safetyStatuses, coordinationText } from '../utils/safetyTask';
import { emergencyScenarios } from '../utils/emergencyFields';
import CollectionCareDrawer from '../components/CollectionCareDrawer.vue';
import CollectionFragilityDrawer from '../components/CollectionFragilityDrawer.vue';
import CollectionJointSafetyDrawer from '../components/CollectionJointSafetyDrawer.vue';
import CollectionLoanCaseDrawer from '../components/CollectionLoanCaseDrawer.vue';
import CollectionEmergencyDrawer from '../components/CollectionEmergencyDrawer.vue';
import CollectionSafetyReminderDrawer from '../components/CollectionSafetyReminderDrawer.vue';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
const route = useRoute();
const tableRef = ref<any>(),
  careRef = ref<any>(),
  fragilityRef = ref<any>(),
  jointRef = ref<any>(),
  loanRef = ref<any>(),
  emergencyRef = ref<any>(),
  settingsRef = ref<any>();
const query = reactive({
  orgId: null as string | null,
  domain: null as string | null,
  status: null as string | null,
  keyword: '',
});
const orgs = ref<{ value: string; label: string }[]>([]),
  domains = ref<{ value: string; label: string }[]>([]),
  context = ref<any>(null),
  error = ref(''),
  ready = ref(false);
let version = 0,
  contextVersion = 0;
const statusOptions = Object.entries(safetyStatuses).map(([value, label]) => ({ value, label }));
const columns = [
  { ellipsis: { tooltip: true }, resizable: true, width: 170, title: '收藏单位', key: 'orgName', minWidth: 170 },
  { ellipsis: { tooltip: true }, resizable: true, width: 240, title: '事项', key: 'title', columnType: 'title', minWidth: 240 },
  { ellipsis: { tooltip: true }, resizable: true, width: 180,
    title: '关联藏品或单据',
    key: 'name',
    minWidth: 180,
    render: (r: any) => (r.kind?.startsWith('DRILL_DUE_') ? emergencyScenarios[r.name] || r.name : r.name),
  },
  { ellipsis: { tooltip: true }, resizable: true, title: '业务', key: 'domain', width: 170, render: (r: any) => safetyDomains[r.domain] || r.domain },
  { ellipsis: { tooltip: true }, resizable: true, title: '当前状态', key: 'status', width: 130, render: (r: any) => safetyStatuses[r.status] || r.status },
  { title: '实际期限', key: 'dueAt', columnType: 'dateTime', width: 190 },
  { resizable: true, width: 190,
    title: '当前职责人员',
    key: 'responsibleUserIds',
    minWidth: 190,
    render: (r: any) =>
      r.responsibleUserIds?.length
        ? h(UserSelect, { value: r.responsibleUserIds, multiple: true, disabled: true })
        : '待落实责任安排',
  },
  { ellipsis: { tooltip: true }, resizable: true, width: 240, title: '提醒承接', key: 'coordinationMissing', minWidth: 240, render: coordinationText },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '站内提醒',
    key: 'reminderEnabled',
    width: 150,
    render: (r: any) => (!r.reminderEnabled ? '未启用' : !r.reminderDue ? '尚未进入提醒期' : '已进入提醒期'),
  },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 150,
    render: (r: any) => h(NButton, { text: true, type: 'primary', onClick: () => view(r) }, () => '查阅并办理原件'),
  },
];
async function loadContext() {
  const id = query.orgId,
    token = ++contextVersion;
  context.value = null;
  if (!id) return;
  try {
    const res = await safetyTaskApi.context(id);
    if (token === contextVersion && query.orgId === id) context.value = res.data;
  } catch (e: any) {
    if (token === contextVersion) error.value = e.message || '未能读取本馆提醒状态';
  }
}
async function init() {
  const token = ++version;
  ready.value = false;
  error.value = '';
  context.value = null;
  contextVersion++;
  try {
    const res = await safetyTaskApi.options();
    if (token !== version) return;
    orgs.value = res.data.orgs || [];
    domains.value = (res.data.domains || []).map((value: string) => ({ value, label: safetyDomains[value] || value }));
    const requested = typeof route.query.orgId === 'string' ? route.query.orgId : null;
    if (requested && !orgs.value.some((o) => o.value === requested)) {
      error.value = '提醒所指收藏单位当前不可访问，请核对账号所属单位与权限。';
      return;
    }
    const previous = query.orgId;
    query.orgId = requested || (orgs.value.length === 1 ? orgs.value[0].value : null);
    query.domain = typeof route.query.domain === 'string' && domains.value.some(d => d.value === route.query.domain) ? route.query.domain : null;
    query.status = typeof route.query.status === 'string' && route.query.status in safetyStatuses ? route.query.status : null;
    query.keyword = typeof route.query.keyword === 'string' ? route.query.keyword.slice(0, 200) : '';
    ready.value = true;
    if (previous === query.orgId) await loadContext();
  } catch (e: any) {
    if (token === version) error.value = e.message || '未能读取可访问的安全待办';
  }
}
function view(row: any) {
  if (row.kind?.startsWith('FRAGILE_')) fragilityRef.value?.open(row.collectionId || row.sourceId);
  else if (row.domain === 'CARE') careRef.value?.open(row.collectionId || row.sourceId);
  else if (row.domain === 'JOINT') jointRef.value?.open(row.sourceId);
  else if (row.domain === 'LOAN') loanRef.value?.open(row.sourceId);
  else if (row.domain === 'EMERGENCY') emergencyRef.value?.open(row.orgId);
}
function openSettings() {
  if (query.orgId && context.value?.canManage) settingsRef.value?.open(query.orgId);
}
function refresh() {
  tableRef.value?.handleRefresh?.();
  void loadContext();
}
watch(
  () => query.orgId,
  () => {
    void loadContext();
  },
);
watch(
  () => [route.query.orgId, route.query.domain, route.query.status, route.query.keyword],
  () => {
    void init();
  },
  { immediate: true },
);
</script>

<template>
  <NCard size="small" :bordered="false">
    <template #header>
      <NSpace align="center" :size="6">
        <span>保护方案</span>
        <NTooltip trigger="hover" placement="bottom-start">
          <template #trigger>
            <NButton text size="tiny" class="policy-help" aria-label="查看保护方案说明">
              <template #icon><HIcon icon="mdi:information-outline" /></template>
            </NButton>
          </template>
          <div class="policy-help-content">
            从标准模板创建保护方案，设置适用位置并保存草稿。完成实际共同评估、参与人员本人核对后启用，位置和藏品变化随后自动同步。
            暂无匹配藏品时会显示“等待适用对象”，不会生成空规则。
          </div>
        </NTooltip>
      </NSpace>
    </template>
    <template #header-extra
      ><NSpace
        ><NButton size="small" :disabled="loading || operating" @click="openLegacy">旧阈值转换核对</NButton>
        <NButton size="small" :disabled="!activatableIds.length" @click="batchStatus('ACTIVE')">{{
          hasFilter ? '启用当前筛选' : '全部启用'
        }}</NButton
        ><NButton size="small" :disabled="!suspendableIds.length" @click="batchStatus('SUSPENDED')">{{
          hasFilter ? '停用当前筛选' : '全部停用'
        }}</NButton></NSpace
      ></template
    >
    <NAlert v-if="error" type="error" style="margin-bottom: 12px">{{ error }}</NAlert>
    <NAlert v-if="hasMonitoringContext" type="info" :bordered="false" style="margin-bottom: 12px">
      已按实时监测中的场馆、位置和指标筛选保护方案{{ monitoringContextLabel ? `：${monitoringContextLabel}` : '' }}。
      草稿须完成共同评估、参与人员本人核对后启用。
      <NButton text type="primary" size="small" @click="clearMonitoringContext">清除监测筛选</NButton>
    </NAlert>
    <NSpace style="margin-bottom: 12px">
      <NButton :loading="loading" :disabled="operating || saving" @click="reload">刷新当前方案与目录</NButton>
      <NInput v-model:value="keyword" clearable placeholder="方案编码 / 名称" style="width: 240px" />
      <NSelect
        v-model:value="statusFilter"
        clearable
        placeholder="方案状态"
        :options="statusOptions"
        style="width: 150px"
      />
      <NTag :bordered="false">当前影响 {{ batchTargetIds.length }} 条</NTag>
    </NSpace>
    <NDataTable :columns="columns" :data="filteredRows" :row-key="(r) => r.id" :loading="loading" />
    <NModal
      v-model:show="formShow"
      preset="card"
      title="编辑保护方案"
      style="width: 80%"
      content-style="max-height:min(600px, calc(100vw - 80px));overflow: auto;"
    >
      <NForm label-placement="left" label-width="90">
        <NFormItem label="方案名称"><NInput v-model:value="form.name" /></NFormItem>
        <NGrid :cols="2" :x-gap="12">
          <NFormItemGi label="适用范围">
            <NSelect
              v-model:value="form.targetScope"
              :options="targetScopeOptions"
              @update:value="normalizeTargetScope"
            />
          </NFormItemGi>
          <NFormItemGi v-if="form.targetScope === 'SELECTED_LOCATIONS'" label="适用位置">
            <NSelect v-model:value="form.locationIds" :options="locationOptions" multiple filterable clearable />
          </NFormItemGi>
        </NGrid>
        <NAlert type="info" :bordered="false" style="margin-bottom: 10px"
          >先保存本版保护标准，再核对共同评估原件与参与人员签署。修改阈值、单位或范围后，须重新评估；已运行版本继续监测。</NAlert
        >
        <NCard v-for="(item, index) in form.items" :key="index" size="small" style="margin-bottom: 10px">
          <template #header>保护标准 {{ index + 1 }}</template
          ><template #header-extra
            ><NButton text type="error" @click="form.items.splice(index, 1)">移除</NButton></template
          >
          <NGrid :cols="3" :x-gap="12">
            <NFormItemGi label="生效类型"
              ><NSelect v-model:value="item.scope" :options="scopeOptions" @update:value="normalizeSelector(item)"
            /></NFormItemGi>
            <NFormItemGi label="选择值">
              <HDictSelect v-if="item.scope === 'MATERIAL'" v-model:value="item.selectorKey" type="col_texture" />
              <NSelect
                v-else-if="item.scope === 'LOCATION'"
                v-model:value="item.selectorKey"
                :options="locationOptions"
                filterable
              />
              <NInput v-else value="同指标通用兜底" disabled />
            </NFormItemGi>
            <NFormItemGi label="指标"
              ><NSelect v-model:value="item.metricCode" :options="metricOptions" filterable
            /></NFormItemGi>
          </NGrid>
          <ThresholdRangeInput v-model:model="form.items[index]" :unit="metricUnit(item.metricCode)" />
          <NFormItem label="依据标准"><NInput v-model:value="item.standardRef" /></NFormItem>
          <NFormItem label="专家依据"><NInput v-model:value="item.expertOpinion" type="textarea" /></NFormItem>
        </NCard>
        <NButton block dashed @click="form.items.push({ scope: 'GENERAL', selectorKey: '*' })">新增保护标准</NButton>
      </NForm>
      <template #footer
        ><NSpace justify="end"
          ><NButton @click="formShow = false">取消</NButton
          ><NButton type="primary" :loading="saving" @click="saveDraft">保存草稿并核对评估</NButton></NSpace
        ></template
      >
    </NModal>
    <PreservationPolicyAssessmentDrawer ref="assessmentRef" @saved="reload" />
    <LegacyThresholdImportDrawer ref="legacyRef" @saved="reload" @open-policy="openImportedPolicy" />
  </NCard>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onActivated, onDeactivated, onBeforeUnmount, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useRoute, useRouter } from 'vue-router';
import { hasProtectionPolicyContext, matchesProtectionPolicyContext, readProtectionPolicyContext } from '../utils/protectionPolicyContext';
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NFormItemGi,
  NGrid,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTag,
  NTooltip,
  useDialog,
  useMessage,
} from 'naive-ui';
import { protectionPolicyApi } from '../api/protectionPolicy';
import { sensorApi } from '../api/sensor';
import { sensorPointApi } from '../api/sensorPoint';
import PreservationPolicyAssessmentDrawer from '../components/PreservationPolicyAssessmentDrawer.vue';
import ThresholdRangeInput from '../components/ThresholdRangeInput.vue';
import LegacyThresholdImportDrawer from '../components/LegacyThresholdImportDrawer.vue';
import type {
  ProtectionPolicyStatus,
  ProtectionPolicyVo,
  ProtectionTargetScope,
  ProtectionThresholdItemVo,
  SensorVo,
} from '../typings';

const route = useRoute();
const router = useRouter();
const monitoringContext = computed(() => readProtectionPolicyContext(route?.query));
const hasMonitoringContext = computed(() => hasProtectionPolicyContext(monitoringContext.value));
const monitoringContextLabel = computed(() => {
  const context = monitoringContext.value;
  const points = context.locationIds.map((id) => locations.value.find((point) => point.id === id)?.name || id);
  const metrics = context.metricCodes.map((code) => sensors.value.find((sensor) => sensor.code === code)?.name || code);
  return [points.join('、'), metrics.join('、')].filter(Boolean).join(' / ');
});
function clearMonitoringContext() {
  if (!route || !router) return;
  const { orgId, locationId, metricCode, policyId, ...query } = route.query;
  void router.replace({ path: route.path, query });
}
const dialog = useDialog();
const message = useMessage();
const loading = ref(false);
const error = ref('');
const operating = ref(false);
let active = true,
  disposed = false,
  generation = 0,
  formGeneration = 0,
  confirmationGeneration = 0;
let confirmation: ReturnType<typeof dialog.warning> | undefined;
const available = () => active && !disposed;
const current = (token: number) => available() && generation === token;

const saving = ref(false);
const rows = ref<ProtectionPolicyVo[]>([]);
const keyword = ref('');
const statusFilter = ref<ProtectionPolicyStatus | null>(null);
const sensors = ref<SensorVo[]>([]);
const locations = ref<any[]>([]);
const formShow = ref(false);
const assessmentRef = ref<InstanceType<typeof PreservationPolicyAssessmentDrawer>>();
const legacyRef = ref<InstanceType<typeof LegacyThresholdImportDrawer>>();
const form = ref<ProtectionPolicyVo>({
  targetScope: 'ALL_LOCATIONS',
  locationIds: [],
  affectedLocationCount: 0,
  appliedLocationCount: 0,
  items: [],
});
const scopeOptions = [
  { label: '材质标准', value: 'MATERIAL' },
  { label: '通用兜底', value: 'GENERAL' },
  { label: '位置例外', value: 'LOCATION' },
];
const targetScopeOptions = [
  { label: '全馆有效位置', value: 'ALL_LOCATIONS' },
  { label: '指定位置', value: 'SELECTED_LOCATIONS' },
];
const statusOptions = [
  { label: '运行中', value: 'ACTIVE' },
  { label: '已停用', value: 'SUSPENDED' },
  { label: '已归档', value: 'ARCHIVED' },
];
const metricOptions = computed(() =>
  sensors.value.map((s) => ({ label: `${s.name || s.code} (${s.code || s.id})`, value: s.code || s.id! })),
);
const locationOptions = computed(() =>
  locations.value.map((p) => ({ label: `${p.name}${p.code ? ` (${p.code})` : ''}`, value: p.id })),
);
const hasFilter = computed(() => Boolean(keyword.value.trim() || statusFilter.value || hasMonitoringContext.value));
const filteredRows = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  return rows.value.filter(
    (row) =>
      (!value || `${row.code || ''} ${row.name || ''}`.toLowerCase().includes(value)) &&
      (!statusFilter.value || row.status === statusFilter.value) &&
      matchesProtectionPolicyContext(row, monitoringContext.value),
  );
});
const batchTargetIds = computed(() =>
  filteredRows.value.map((row) => row.id).filter((id): id is string => Boolean(id)),
);
const activatableIds = computed(() =>
  filteredRows.value
    .filter((row) => !loading.value && !operating.value && row.status === 'SUSPENDED' && row.versionStatus !== 'DRAFT')
    .map((row) => row.id)
    .filter((id): id is string => Boolean(id)),
);
const suspendableIds = computed(() =>
  filteredRows.value
    .filter((row) => !loading.value && !operating.value && row.status === 'ACTIVE')
    .map((row) => row.id)
    .filter((id): id is string => Boolean(id)),
);
const columns = [
  { title: '方案编码', key: 'code', minWidth: 170, ellipsis: { tooltip: true }, resizable: true, width: 180 },
  { title: '方案名称', key: 'name', minWidth: 220, ellipsis: { tooltip: true }, resizable: true, width: 220 },
  { title: '保护标准', key: 'items', width: 100, render: (r: ProtectionPolicyVo) => `${r.items.length} 条` },
  {
    title: '适用范围',
    key: 'targetScope',
    minWidth: 160, ellipsis: { tooltip: true }, resizable: true, width: 180,
    render: (r: ProtectionPolicyVo) =>
      r.targetScope === 'SELECTED_LOCATIONS'
        ? `指定 ${r.affectedLocationCount || r.locationIds.length} 个位置`
        : `全馆 ${r.affectedLocationCount} 个位置`,
  },
  {
    title: '状态',
    key: 'status',
    width: 140, ellipsis: { tooltip: true }, resizable: true,
    render: (r: ProtectionPolicyVo) =>
      h(
        NTag,
        {
          type: r.status === 'ACTIVE' ? (r.appliedLocationCount > 0 ? 'success' : 'warning') : 'default',
          bordered: false,
        },
        () =>
          r.status === 'ACTIVE' && r.appliedLocationCount === 0
            ? '等待适用对象'
            : { ACTIVE: '运行中', SUSPENDED: '已停用', ARCHIVED: '已归档' }[r.status || 'SUSPENDED'],
      ),
  },
  {
    title: '操作',
    key: 'action',
    width: 280,
    render: (r: ProtectionPolicyVo) =>
      h(NSpace, { size: 'small' }, () => [
        h(NButton, { text: true, type: 'primary', onClick: () => openAssessment(r) }, () => '共同评估与原件'),
        h(
          NButton,
          {
            text: true,
            type: 'primary',
            disabled: loading.value || operating.value || saving.value || r.status === 'ARCHIVED',
            onClick: () => openForm(r),
          },
          () => '编辑',
        ),
        h(
          NButton,
          {
            text: true,
            onClick: () => setStatus(r, r.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'),
            disabled:
              loading.value ||
              operating.value ||
              saving.value ||
              r.status === 'ARCHIVED' ||
              (r.status !== 'ACTIVE' && r.versionStatus === 'DRAFT'),
          },
          () => (r.status === 'ACTIVE' ? '停用' : '启用'),
        ),
      ]),
  },
];
function normalizeSelector(item: ProtectionThresholdItemVo) {
  item.selectorKey = item.scope === 'GENERAL' ? '*' : undefined;
}
function normalizeTargetScope(value: ProtectionTargetScope) {
  if (value === 'ALL_LOCATIONS') form.value.locationIds = [];
}
function metricUnit(code?: string) {
  return sensors.value.find((s) => (s.code || s.id) === code)?.unit || '';
}
function blankForm(): ProtectionPolicyVo {
  return {
    targetScope: 'ALL_LOCATIONS',
    locationIds: [],
    affectedLocationCount: 0,
    appliedLocationCount: 0,
    items: [],
  };
}
function closeForm() {
  ++formGeneration;
  formShow.value = false;
  form.value = blankForm();
  saving.value = false;
}
function closeConfirmation() {
  ++confirmationGeneration;
  confirmation?.destroy();
  confirmation = undefined;
}
function clearData() {
  rows.value = [];
  sensors.value = [];
  locations.value = [];
  closeForm();
  closeConfirmation();
  operating.value = false;
}
function fail(reason: any) {
  clearData();
  assessmentRef.value?.close();
  legacyRef.value?.close();
  error.value = reason?.message || '当前方案或办理资格读取失败，请刷新后核对';
}
function currentRow(row: ProtectionPolicyVo) {
  return available() && !loading.value && !operating.value && !saving.value && rows.value.includes(row);
}
function openAssessment(row: ProtectionPolicyVo) {
  if (currentRow(row) && row.id) assessmentRef.value?.open(row.id);
}
function openLegacy() {
  if (available() && !loading.value && !operating.value) legacyRef.value?.open();
}
function openImportedPolicy(id: string) {
  // 导入窗口先核验当前原快照；新方案可能尚未出现在主列表中。
  if (available() && !loading.value && !operating.value) assessmentRef.value?.open(id);
}
async function readContext() {
  const [response, s, p]: any[] = await Promise.all([
    protectionPolicyApi.list(),
    sensorApi.all(),
    sensorPointApi.page({ pageSize: 0 }),
  ]);
  const policies = response?.data,
    metrics = s?.data?.list ?? s?.data,
    points = p?.data?.list ?? p?.data;
  if (
    !Array.isArray(policies) ||
    !Array.isArray(metrics) ||
    !Array.isArray(points) ||
    policies.some((r: any) => !r.id || !r.orgId || !Array.isArray(r.items) || !Array.isArray(r.locationIds)) ||
    metrics.some((m: any) => !m.code) ||
    points.some((p: any) => !p.id)
  ) {
    throw new Error('保护方案或指标位置目录不完整，请重新读取');
  }
  return { policies, metrics, points: points.filter((p: any) => !p.status || p.status === 'NORMAL') };
}
function applyContext(value: Awaited<ReturnType<typeof readContext>>) {
  rows.value = cloneDeep(value.policies);
  sensors.value = value.metrics;
  locations.value = value.points;
}
async function reload() {
  if (!available() || operating.value || saving.value) return;
  const token = ++generation;
  clearData();
  error.value = '';
  loading.value = true;
  try {
    const context = await readContext();
    if (current(token)) applyContext(context);
  } catch (reason) {
    if (current(token)) fail(reason);
  } finally {
    if (current(token)) loading.value = false;
  }
}
async function openForm(row: ProtectionPolicyVo) {
  if (!currentRow(row) || !row.id || row.status === 'ARCHIVED') return;
  const id = row.id,
    token = ++generation;
  clearData();
  error.value = '';
  loading.value = true;
  try {
    const [context, response]: any[] = await Promise.all([readContext(), protectionPolicyApi.assessment(id)]);
    if (!current(token)) return;
    const fresh = context.policies.find((r: ProtectionPolicyVo) => r.id === id),
      evidence = response?.data;
    if (
      !fresh ||
      fresh.orgId !== row.orgId ||
      evidence?.policyId !== id ||
      evidence?.orgId !== fresh.orgId ||
      evidence.editable !== true ||
      fresh.status === 'ARCHIVED'
    ) {
      throw new Error('该方案当前不可编辑，请重新核对办理资格和归属');
    }
    const version = evidence.versions?.find((v: any) => v.id === fresh.versionId),
      definition = version?.definition,
      locationIds = definition?.targetScope === 'ALL_LOCATIONS' && definition.locationIds === undefined
        ? []
        : definition?.locationIds;
    if (
      !definition ||
      definition.policyId !== id ||
      definition.orgId !== fresh.orgId ||
      definition.policyVersionId !== fresh.versionId ||
      !['ALL_LOCATIONS', 'SELECTED_LOCATIONS'].includes(definition.targetScope) ||
      !Array.isArray(locationIds) ||
      !Array.isArray(definition.items)
    ) {
      throw new Error('方案版本或实际定义已变化，请重新读取后编辑');
    }
    applyContext(context);
    form.value = cloneDeep({
      ...fresh,
      name: evidence.name,
      targetScope: definition.targetScope,
      locationIds,
      items: definition.items,
    });
    ++formGeneration;
    formShow.value = true;
  } catch (reason) {
    if (current(token)) fail(reason);
  } finally {
    if (current(token)) loading.value = false;
  }
}
async function saveDraft() {
  if (!available() || loading.value || operating.value || saving.value || !formShow.value || !form.value.id) return;
  if (form.value.targetScope === 'SELECTED_LOCATIONS' && !form.value.locationIds.length) {
    message.warning('请至少选择一个适用位置');
    return;
  }
  const body = cloneDeep(form.value),
    id = body.id!,
    token = generation,
    session = formGeneration;
  const isCurrent = () => current(token) && session === formGeneration && formShow.value && form.value.id === id;
  saving.value = true;
  try {
    await protectionPolicyApi.saveDraft(id, body);
    if (!isCurrent()) return;
    message.success('草稿已保存，请核对本版实际共同评估与本人签署');
    closeForm();
    assessmentRef.value?.open(id);
    await reload();
  } catch (reason) {
    if (isCurrent()) fail(reason);
  } finally {
    if (isCurrent()) saving.value = false;
  }
}
async function setStatus(row: ProtectionPolicyVo, status: ProtectionPolicyStatus) {
  if (
    !currentRow(row) ||
    !row.id ||
    row.status === 'ARCHIVED' ||
    (status === 'ACTIVE' && (row.status !== 'SUSPENDED' || row.versionStatus === 'DRAFT')) ||
    (status === 'SUSPENDED' && row.status !== 'ACTIVE') ||
    !['ACTIVE', 'SUSPENDED'].includes(status)
  )
    return;
  const token = generation,
    id = row.id;
  closeConfirmation();
  operating.value = true;
  try {
    await protectionPolicyApi.setStatus(id, status);
    if (!current(token)) return;
    operating.value = false;
    await reload();
  } catch (reason) {
    if (current(token)) fail(reason);
  } finally {
    if (current(token)) operating.value = false;
  }
}
function batchStatus(status: ProtectionPolicyStatus) {
  if (!available() || loading.value || operating.value || saving.value || !['ACTIVE', 'SUSPENDED'].includes(status))
    return;
  const ids = [...(status === 'ACTIVE' ? activatableIds.value : suspendableIds.value)];
  if (!ids.length) {
    message.warning(status === 'ACTIVE' ? '当前范围没有可直接启用的已保存方案' : '当前范围没有可停用方案');
    return;
  }
  closeConfirmation();
  const token = generation,
    confirmationToken = confirmationGeneration;
  const filter = JSON.stringify([keyword.value, statusFilter.value]);
  const isCurrent = () =>
    current(token) &&
    confirmationToken === confirmationGeneration &&
    filter === JSON.stringify([keyword.value, statusFilter.value]);
  let submitted = false;
  const scopeText = hasFilter.value ? '当前筛选' : '全部';
  confirmation = dialog.warning({
    title: status === 'ACTIVE' ? `${scopeText}启用` : `${scopeText}停用`,
    content: `将影响 ${ids.length} 个保护方案；采集设备规则和其他业务预警不受影响。`,
    positiveText: '确认',
    negativeText: '取消',
    onNegativeClick: closeConfirmation,
    onClose: closeConfirmation,
    onMaskClick: closeConfirmation,
    onPositiveClick: async () => {
      if (!isCurrent() || submitted || operating.value || loading.value || saving.value) return;
      submitted = true;
      operating.value = true;
      try {
        await protectionPolicyApi.batchStatus(ids, status);
        if (!isCurrent()) return;
        operating.value = false;
        await reload();
      } catch (reason) {
        if (isCurrent()) fail(reason);
      } finally {
        if (current(token)) operating.value = false;
      }
    },
  });
}
watch(
  formShow,
  (value) => {
    if (!value) closeForm();
  },
  { flush: 'sync' },
);
watch([keyword, statusFilter], closeConfirmation, { flush: 'sync' });
function leave() {
  ++generation;
  clearData();
  loading.value = false;
  error.value = '';
  assessmentRef.value?.close();
  legacyRef.value?.close();
}
onMounted(reload);
onActivated(() => {
  if (!disposed && !active) {
    active = true;
    void reload();
  }
});
onDeactivated(() => {
  active = false;
  leave();
});
onBeforeUnmount(() => {
  disposed = true;
  active = false;
  leave();
});
</script>

<style scoped>
.policy-help {
  color: var(--n-text-color-3);
}
.policy-help-content {
  max-width: 380px;
  line-height: 1.6;
}
</style>

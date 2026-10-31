<template>
  <NCard size="small" :bordered="false">
    <template #header>
      <NSpace align="center" :size="6">
        <span>保护标准模板</span>
        <NTooltip trigger="hover" placement="bottom-start">
          <template #trigger>
            <NButton text size="tiny" class="template-help" aria-label="查看保护标准模板说明">
              <template #icon><HIcon icon="mdi:information-outline" /></template>
            </NButton>
          </template>
          <div class="template-help-content">
            模板是可复用的保护知识，不直接参与实时预警。列表展示正式发布内容；采用后会创建独立保护方案，模板更新不会自动修改既有方案。
          </div>
        </NTooltip>
      </NSpace>
    </template>
    <template #header-extra
      ><NButton
        type="primary"
        size="small"
        :disabled="!management?.canManage || loading || operating || saving"
        @click="openForm()"
        >新建模板</NButton
      ></template
    >
    <NAlert v-if="error" type="error" style="margin-bottom: 12px">{{ error }}</NAlert>
    <NSpace style="margin-bottom: 12px">
      <NButton :loading="loading || reading" :disabled="saving || operating" @click="reload"
        >刷新当前模板与目录</NButton
      >
      <NInput v-model:value="keyword" clearable placeholder="模板名称 / 编码 / 说明" style="width: 260px" />
      <NSelect
        v-model:value="statusFilter"
        clearable
        placeholder="模板状态"
        :options="statusOptions"
        style="width: 150px"
      />
      <NTag :bordered="false">共 {{ filteredRows.length }} 个模板</NTag>
    </NSpace>
    <NDataTable
      :columns="columns"
      :data="filteredRows"
      :loading="loading || reading"
      :row-key="(row) => row.id"
      :scroll-x="1080"
    />

    <NDrawer v-model:show="detailShow" :width="900">
      <NDrawerContent :title="detailRow?.name || '模板详情'" closable :native-scrollbar="false">
        <template v-if="detailRow">
          <div class="detail-heading">
            <div>
              <div class="detail-code">{{ detailRow.code || '--' }}</div>
              <div class="detail-description">{{ detailRow.description || '暂无模板说明' }}</div>
            </div>
            <NSpace>
              <NTag :type="statusTagType(detailRow)" :bordered="false">{{ statusText(detailRow) }}</NTag>
              <NTag v-if="detailRow.publishedVersionId && detailRow.hasDraft" type="warning" :bordered="false">
                有未发布修改
              </NTag>
            </NSpace>
          </div>
          <NSpace v-if="detailRow.publishedVersionId && detailRow.hasDraft" style="margin-bottom: 12px">
            <NButton
              size="small"
              :type="detailMode === 'PUBLISHED' ? 'primary' : 'default'"
              @click="detailMode = 'PUBLISHED'"
            >
              正式发布 V{{ detailRow.publishedVersionNo }}
            </NButton>
            <NButton size="small" :type="detailMode === 'DRAFT' ? 'primary' : 'default'" @click="detailMode = 'DRAFT'">
              未发布草稿 V{{ detailRow.versionNo }}
            </NButton>
          </NSpace>
          <NAlert
            v-if="detailRow.publishedVersionId && detailMode === 'PUBLISHED'"
            type="success"
            :bordered="false"
            style="margin-bottom: 12px"
          >
            采用模板创建保护方案时，将使用此正式版本。
          </NAlert>
          <NAlert v-else-if="detailMode === 'DRAFT'" type="warning" :bordered="false" style="margin-bottom: 12px">
            这是未发布草稿，不会被新保护方案采用。
          </NAlert>
          <NEmpty v-if="!detailGroups.length" description="当前版本没有保护标准" />
          <NCard
            v-for="group in detailGroups"
            :key="group.code"
            size="small"
            class="detail-group"
            :title="`${group.name}${group.unit ? `（${group.unit}）` : ''}`"
          >
            <NDataTable
              :columns="detailColumns"
              :data="group.items"
              :row-key="thresholdRowKey"
              size="small"
              :single-line="false"
              :scroll-x="850"
            />
          </NCard>
        </template>
      </NDrawerContent>
    </NDrawer>

    <NModal
      v-model:show="formShow"
      preset="card"
      :title="form.id ? '编辑保护标准模板' : '新建保护标准模板'"
      style="width: 80%"
      content-style="max-height:min(600px, calc(100vh - 160px));overflow: auto;"
    >
      <NForm label-placement="left" label-width="90">
        <NAlert v-if="form.id && form.status === 'PUBLISHED'" type="info" :bordered="false" style="margin-bottom: 12px">
          保存只会创建或更新未发布草稿，正式发布版本仍可继续被保护方案采用。
        </NAlert>
        <NGrid :cols="2" :x-gap="16">
          <NFormItemGi label="模板编码" required><NInput v-model:value="form.code" /></NFormItemGi>
          <NFormItemGi label="模板名称" required><NInput v-model:value="form.name" /></NFormItemGi>
          <NFormItemGi label="说明" :span="2"><NInput v-model:value="form.description" type="textarea" /></NFormItemGi>
        </NGrid>
        <NSpace justify="space-between" align="center">
          <NDivider title-placement="left">保护标准</NDivider>
          <NButton size="small" @click="addItem">新增保护标准</NButton>
        </NSpace>
        <NEmpty v-if="!form.items.length" description="至少添加一条材质标准或通用兜底" />
        <NCard v-for="(item, index) in form.items" :key="index" size="small" style="margin-bottom: 10px">
          <template #header>{{ itemTitle(item, index) }}</template>
          <template #header-extra>
            <NButton text type="error" @click="form.items.splice(index, 1)">移除</NButton>
          </template>
          <NGrid :cols="3" :x-gap="12">
            <NFormItemGi label="生效类型" required>
              <NSelect
                v-model:value="item.scope"
                :options="templateScopeOptions"
                @update:value="normalizeSelector(item)"
              />
            </NFormItemGi>
            <NFormItemGi label="适用对象" required>
              <HDictSelect v-if="item.scope === 'MATERIAL'" v-model:value="item.selectorKey" type="col_texture" />
              <NInput v-else value="同指标通用兜底" disabled />
            </NFormItemGi>
            <NFormItemGi label="保护指标" required>
              <NSelect v-model:value="item.metricCode" :options="metricOptions" filterable />
            </NFormItemGi>
          </NGrid>
          <ThresholdRangeInput v-model:model="form.items[index]" :unit="metricUnit(item.metricCode)" />
          <NGrid :cols="2" :x-gap="12">
            <NFormItemGi label="依据标准"><NInput v-model:value="item.standardRef" /></NFormItemGi>
            <NFormItemGi label="专家意见"><NInput v-model:value="item.expertOpinion" /></NFormItemGi>
          </NGrid>
        </NCard>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="formShow = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="save">保存草稿</NButton>
        </NSpace>
      </template>
    </NModal>
  </NCard>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onActivated, onDeactivated, onBeforeUnmount, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NDivider,
  NDrawer,
  NDrawerContent,
  NDropdown,
  NEmpty,
  NForm,
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
import { ruleTemplateApi } from '../api/ruleTemplate';
import { protectionPolicyApi } from '../api/protectionPolicy';
import { sensorApi } from '../api/sensor';
import ThresholdRangeInput from '../components/ThresholdRangeInput.vue';
import type { ProtectionThresholdItemVo, RuleTemplateStatus, RuleTemplateVo, SensorVo } from '../typings';

type DetailMode = 'PUBLISHED' | 'DRAFT';
type ThresholdLevel = 'l1' | 'l2' | 'l3' | 'l4';
interface DetailGroup {
  code: string;
  name: string;
  unit: string;
  items: ProtectionThresholdItemVo[];
}

const dialog = useDialog();
const message = useMessage();
const rows = ref<RuleTemplateVo[]>([]);
const sensors = ref<SensorVo[]>([]);
const loading = ref(false);
const reading = ref(false),
  operating = ref(false),
  error = ref('');
const management = ref<{ canManage: boolean; orgId: string | null } | null>(null);
const save = ref<() => Promise<void>>(async () => {});
let active = true,
  disposed = false,
  generation = 0,
  intent = 0;
let confirmation: ReturnType<typeof dialog.warning> | undefined;
const available = () => active && !disposed;
const current = (page: number, token = intent) => available() && page === generation && token === intent;

const formShow = ref(false);
const saving = ref(false);
const form = ref<RuleTemplateVo>({ items: [] });
const keyword = ref('');
const statusFilter = ref<RuleTemplateStatus | null>(null);
const detailShow = ref(false);
const detailRow = ref<RuleTemplateVo | null>(null);
const detailMode = ref<DetailMode>('PUBLISHED');

const templateScopeOptions = [
  { label: '材质标准', value: 'MATERIAL' },
  { label: '通用兜底', value: 'GENERAL' },
];
const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已退役', value: 'RETIRED' },
];
const metricOptions = computed(() =>
  sensors.value.map((sensor) => ({
    label: `${sensor.name || sensor.code} (${sensor.code || sensor.id})${sensor.unit ? ` · ${sensor.unit}` : ''}`,
    value: sensor.code || sensor.id!,
  })),
);
const filteredRows = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  return rows.value.filter(
    (row) =>
      (!value || `${row.name || ''} ${row.code || ''} ${row.description || ''}`.toLowerCase().includes(value)) &&
      (!statusFilter.value || row.status === statusFilter.value),
  );
});
const detailItems = computed(() => {
  const row = detailRow.value;
  if (!row) return [];
  return detailMode.value === 'PUBLISHED' ? row.publishedItems || [] : row.items;
});
const detailGroups = computed<DetailGroup[]>(() => {
  const groups = new Map<string, ProtectionThresholdItemVo[]>();
  detailItems.value.forEach((item) => {
    const code = item.metricCode || '未设置指标';
    groups.set(code, [...(groups.get(code) || []), item]);
  });
  return [...groups.entries()]
    .map(([code, items]) => ({
      code,
      name: metricName(code),
      unit: metricUnit(code),
      items: [...items].sort((a, b) => {
        if (a.scope === 'GENERAL') return 1;
        if (b.scope === 'GENERAL') return -1;
        return (a.selectorKey || '').localeCompare(b.selectorKey || '', 'zh-CN');
      }),
    }))
    .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'));
});

const columns: any[] = [
  {
    title: '模板',
    key: 'name',
    minWidth: 260, ellipsis: { tooltip: true }, resizable: true, width: 260,
    render: (row: RuleTemplateVo) => [row.name || '--', row.code || '--', row.description].filter(Boolean).join(' · '),
  },
  { title: '适用对象', key: 'scope', minWidth: 220, resizable: true, width: 220, render: renderScopeSummary },
  { title: '保护指标与核心范围', key: 'metrics', minWidth: 290, resizable: true, width: 290, render: renderMetricSummary },
  { title: '状态', key: 'status', width: 180, resizable: true, render: renderStatus },
  { title: '操作', key: 'action', width: 250, fixed: 'right', render: renderActions },
];

const detailColumns: any[] = [
  {
    title: '适用对象',
    key: 'selectorKey',
    width: 160, ellipsis: { tooltip: true }, resizable: true,
    render: (item: ProtectionThresholdItemVo) =>
      h(NTag, { type: item.scope === 'GENERAL' ? 'info' : 'default', bordered: false }, () => selectorText(item)),
  },
  { title: '四级关注', key: 'l4', width: 140, ellipsis: { tooltip: true }, resizable: true, render: (item: ProtectionThresholdItemVo) => rangeText(item, 'l4') },
  { title: '三级预警', key: 'l3', width: 140, ellipsis: { tooltip: true }, resizable: true, render: (item: ProtectionThresholdItemVo) => rangeText(item, 'l3') },
  { title: '二级严重', key: 'l2', width: 140, ellipsis: { tooltip: true }, resizable: true, render: (item: ProtectionThresholdItemVo) => rangeText(item, 'l2') },
  { title: '一级紧急', key: 'l1', width: 140, ellipsis: { tooltip: true }, resizable: true, render: (item: ProtectionThresholdItemVo) => rangeText(item, 'l1') },
  {
    title: '依据标准',
    key: 'standardRef',
    minWidth: 190, ellipsis: { tooltip: true }, resizable: true, width: 220,
    render: (item: ProtectionThresholdItemVo) => item.standardRef || '--',
  },
];

function formalItems(row: RuleTemplateVo) {
  return row.publishedVersionId ? row.publishedItems || [] : row.items;
}
function metricName(code?: string) {
  const sensor = sensors.value.find((item) => (item.code || item.id) === code);
  return sensor?.name || code || '未设置指标';
}
function metricUnit(code?: string) {
  return sensors.value.find((item) => (item.code || item.id) === code)?.unit || '';
}
function selectorText(item: ProtectionThresholdItemVo) {
  return item.scope === 'GENERAL' ? '通用兜底' : item.selectorKey || '未设置材质';
}
function rangeValues(
  item: ProtectionThresholdItemVo,
  level: ThresholdLevel,
): [number | null | undefined, number | null | undefined] {
  switch (level) {
    case 'l1':
      return [item.l1MinValue, item.l1MaxValue];
    case 'l2':
      return [item.l2MinValue, item.l2MaxValue];
    case 'l3':
      return [item.l3MinValue, item.l3MaxValue];
    default:
      return [item.l4MinValue, item.l4MaxValue];
  }
}
function rangeText(item: ProtectionThresholdItemVo, level: ThresholdLevel) {
  const [min, max] = rangeValues(item, level);
  const unit = metricUnit(item.metricCode);
  if (min != null && max != null) return `${min}～${max}${unit}`;
  if (min != null) return `≥${min}${unit}`;
  if (max != null) return `≤${max}${unit}`;
  return '--';
}
function coreRange(item: ProtectionThresholdItemVo) {
  const levels: Array<[ThresholdLevel, string]> = [
    ['l4', '四级'],
    ['l3', '三级'],
    ['l2', '二级'],
    ['l1', '一级'],
  ];
  for (const [level, label] of levels) {
    const text = rangeText(item, level);
    if (text !== '--') return `${label} ${text}`;
  }
  return '未设置阈值';
}
function renderScopeSummary(row: RuleTemplateVo) {
  const items = formalItems(row);
  const labels = [
    ...new Set(items.filter((item) => item.scope === 'MATERIAL').map((item) => item.selectorKey || '未设置材质')),
  ];
  if (items.some((item) => item.scope === 'GENERAL')) labels.push('通用兜底');
  if (!labels.length) return h('span', { class: 'muted-text' }, '暂无正式内容');
  const visible = labels.slice(0, 3);
  return h(NSpace, { size: 4, wrap: true }, () => [
    ...visible.map((label) =>
      h(NTag, { size: 'small', type: label === '通用兜底' ? 'info' : 'default', bordered: false }, () => label),
    ),
    labels.length > visible.length
      ? h(NTag, { size: 'small', bordered: false }, () => `+${labels.length - visible.length}`)
      : null,
  ]);
}
function metricSummaries(row: RuleTemplateVo) {
  const groups = new Map<string, ProtectionThresholdItemVo[]>();
  formalItems(row).forEach((item) => {
    const code = item.metricCode || '未设置指标';
    groups.set(code, [...(groups.get(code) || []), item]);
  });
  return [...groups.entries()].map(([code, items]) => {
    const representative = items.find((item) => item.scope === 'MATERIAL') || items[0];
    const extra = items.length > 1 ? ` 等${items.length}项` : '';
    return `${metricName(code)}：${selectorText(representative)} ${coreRange(representative)}${extra}`;
  });
}
function renderMetricSummary(row: RuleTemplateVo) {
  const summaries = metricSummaries(row);
  if (!summaries.length) return h('span', { class: 'muted-text' }, '暂无正式内容');
  return h('div', { class: 'metric-summary' }, [
    ...summaries.slice(0, 3).map((summary) => h('div', { class: 'metric-summary-line' }, summary)),
    summaries.length > 3 ? h('div', { class: 'muted-text' }, `另有 ${summaries.length - 3} 个指标`) : null,
  ]);
}
function statusText(row: RuleTemplateVo) {
  if (row.status === 'RETIRED') return `已退役${row.publishedVersionNo ? ` · V${row.publishedVersionNo}` : ''}`;
  if (row.status === 'PUBLISHED') return `已发布 V${row.publishedVersionNo || row.versionNo || 1}`;
  return `草稿 V${row.versionNo || 1}`;
}
function statusTagType(row: RuleTemplateVo) {
  if (row.status === 'PUBLISHED') return 'success';
  if (row.status === 'DRAFT') return 'info';
  return 'default';
}
function renderStatus(row: RuleTemplateVo) {
  return h(NSpace, { size: 4, vertical: true }, () => [
    h(NTag, { type: statusTagType(row), bordered: false }, () => statusText(row)),
    row.publishedVersionId && row.hasDraft
      ? h(NTag, { type: 'warning', size: 'small', bordered: false }, () => `有未发布修改 · V${row.versionNo}`)
      : null,
  ]);
}
function primaryAction(row: RuleTemplateVo) {
  if (row.status === 'PUBLISHED') return adopt(row);
  if (row.status === 'RETIRED') return copyAsNew(row);
  return openForm(row);
}
function primaryActionText(row: RuleTemplateVo) {
  if (row.status === 'PUBLISHED') return '采用模板';
  if (row.status === 'RETIRED') return '复制新建';
  return '继续编辑';
}
function moreOptions(row: RuleTemplateVo) {
  if (!row.canManage || !management.value?.canManage || row.status === 'RETIRED') return [];
  const options: any[] = [];
  if (row.status === 'PUBLISHED') options.push({ label: row.hasDraft ? '编辑草稿' : '编辑新草稿', key: 'edit' });
  if (row.versionStatus === 'DRAFT') options.push({ label: '发布草稿', key: 'publish' });
  options.push({ label: '复制新建', key: 'copy' });
  options.push({ label: '退役模板', key: 'retire' });
  return options;
}
function handleMoreAction(key: string | number, row: RuleTemplateVo) {
  if (key === 'edit') openForm(row);
  else if (key === 'publish') publish(row);
  else if (key === 'copy') copyAsNew(row);
  else if (key === 'retire') retire(row);
}
function renderActions(row: RuleTemplateVo) {
  const options = moreOptions(row);
  return h(NSpace, { size: 'small' }, () => [
    row.canManage && management.value?.canManage
      ? h(
          NButton,
          { text: true, type: 'primary', disabled: saving.value || operating.value, onClick: () => primaryAction(row) },
          () => primaryActionText(row),
        )
      : null,
    h(NButton, { text: true, onClick: () => openDetail(row) }, () => '查看详情'),
    options.length
      ? h(
          NDropdown,
          { trigger: 'click', options, onSelect: (key: string | number) => handleMoreAction(key, row) },
          { default: () => h(NButton, { text: true }, () => '更多') },
        )
      : null,
  ]);
}
function thresholdRowKey(item: ProtectionThresholdItemVo) {
  return item.id || `${item.scope}:${item.selectorKey}:${item.metricCode}`;
}
function itemTitle(item: ProtectionThresholdItemVo, index: number) {
  const metric = item.metricCode ? metricName(item.metricCode) : `保护标准 ${index + 1}`;
  return `${metric} · ${selectorText(item)}`;
}
function addItem() {
  form.value.items.push({ scope: 'MATERIAL' });
}
function normalizeSelector(item: ProtectionThresholdItemVo) {
  item.selectorKey = item.scope === 'GENERAL' ? '*' : undefined;
}
function clearIntent() {
  ++intent;
  confirmation?.destroy();
  confirmation = undefined;
  formShow.value = false;
  detailShow.value = false;
  form.value = { items: [] };
  detailRow.value = null;
  detailMode.value = 'PUBLISHED';
  save.value = async () => {};
  saving.value = false;
  reading.value = false;
  operating.value = false;
}
function clearData() {
  clearIntent();
  rows.value = [];
  sensors.value = [];
  management.value = null;
}
function fail(reason: any) {
  clearData();
  error.value = reason?.message || '当前模板或办理资格读取失败，请刷新后核对';
}
function validRow(value: any): value is RuleTemplateVo {
  return (
    !!value?.id &&
    typeof value.canManage === 'boolean' &&
    ['DRAFT', 'PUBLISHED', 'RETIRED'].includes(value.status) &&
    Array.isArray(value.items) &&
    Array.isArray(value.publishedItems) &&
    (!value.versionId || !!value.definitionHash)
  );
}
function validContext(value: any) {
  return (
    value &&
    typeof value.canManage === 'boolean' &&
    (!value.canManage || (typeof value.orgId === 'string' && value.orgId.length > 0))
  );
}
function currentRow(row: RuleTemplateVo) {
  return available() && !loading.value && !saving.value && !operating.value && rows.value.includes(row);
}
async function catalog() {
  const [response, context]: any[] = await Promise.all([sensorApi.all(), ruleTemplateApi.context()]);
  const metrics = response?.data?.list ?? response?.data;
  if (!Array.isArray(metrics) || metrics.some((r: any) => !r.code) || !validContext(context?.data))
    throw new Error('模板指标目录或当前办理资格不完整，请重新读取');
  return { metrics, context: context.data };
}
async function reload() {
  if (!available() || saving.value || operating.value) return;
  const page = ++generation;
  clearData();
  loading.value = true;
  error.value = '';
  try {
    const [response, info]: any[] = await Promise.all([ruleTemplateApi.list(), catalog()]);
    if (!(available() && page === generation)) return;
    if (!Array.isArray(response?.data) || response.data.some((r: any) => !validRow(r)))
      throw new Error('模板列表资料不完整，请重新读取');
    rows.value = cloneDeep(response.data);
    sensors.value = info.metrics;
    management.value = info.context;
  } catch (reason) {
    if (available() && page === generation) fail(reason);
  } finally {
    if (available() && page === generation) loading.value = false;
  }
}
async function prepare(
  row: RuleTemplateVo | undefined,
  action: 'detail' | 'edit' | 'copy' | 'publish' | 'adopt' | 'retire',
) {
  if (!available() || loading.value || saving.value || operating.value || (row && !currentRow(row))) return;
  clearIntent();
  const page = generation,
    token = intent;
  const isCurrent = () => current(page, token);
  error.value = '';
  reading.value = true;
  try {
    const [response, info]: any[] = await Promise.all([
      row?.id ? ruleTemplateApi.get(row.id) : Promise.resolve(undefined),
      catalog(),
    ]);
    if (!isCurrent()) return;
    const fresh: RuleTemplateVo | undefined = response?.data;
    if (row && (!validRow(fresh) || fresh.id !== row.id)) throw new Error('模板详情缺失或身份不一致，请重新读取');
    if (action !== 'detail' && (info.context.canManage !== true || (fresh && fresh.canManage !== true)))
      throw new Error('当前仅可查阅模板，不能办理');
    if (fresh && action === 'edit' && fresh.status === 'RETIRED') throw new Error('已退役模板不能再编辑，请复制新建');
    if (
      fresh &&
      action === 'publish' &&
      (fresh.status === 'RETIRED' || fresh.versionStatus !== 'DRAFT' || !fresh.versionId || !fresh.definitionHash)
    )
      throw new Error('当前没有可发布草稿，请重新核对');
    if (fresh && action === 'adopt' && (fresh.status !== 'PUBLISHED' || !fresh.publishedVersionId))
      throw new Error('当前模板没有可采用的正式版本');
    if (fresh && action === 'retire' && fresh.status === 'RETIRED') throw new Error('当前模板已退役');
    sensors.value = info.metrics;
    management.value = info.context;
    if (fresh) rows.value = rows.value.map((r) => (r.id === fresh.id ? cloneDeep(fresh) : r));
    return { fresh: fresh ? cloneDeep(fresh) : undefined, context: cloneDeep(info.context), isCurrent };
  } catch (reason) {
    if (isCurrent()) fail(reason);
  } finally {
    if (isCurrent()) reading.value = false;
  }
}
function bindSave(isCurrent: () => boolean) {
  save.value = async () => {
    if (!isCurrent() || !formShow.value || saving.value || operating.value || !management.value?.canManage) return;
    if (!form.value.code || !form.value.name || !form.value.items.length) {
      message.warning('请完整填写模板和保护标准');
      return;
    }
    const body = cloneDeep(form.value);
    saving.value = true;
    try {
      await ruleTemplateApi.save(body);
      if (!isCurrent() || !formShow.value) return;
      clearIntent();
      await reload();
    } catch (reason) {
      if (isCurrent()) fail(reason);
    } finally {
      if (isCurrent()) saving.value = false;
    }
  };
}
async function openForm(row?: RuleTemplateVo) {
  const prepared = await prepare(row, 'edit');
  if (!prepared?.isCurrent()) return;
  form.value = prepared.fresh || { items: [] };
  formShow.value = true;
  bindSave(prepared.isCurrent);
}
async function copyAsNew(row: RuleTemplateVo) {
  const prepared = await prepare(row, 'copy');
  if (!prepared?.isCurrent() || !prepared.fresh) return;
  const fresh = prepared.fresh;
  form.value = {
    code: '',
    name: `${fresh.name || ''}（复制）`,
    description: fresh.description,
    items: cloneDeep(formalItems(fresh)).map((item) => ({ ...item, id: undefined })),
  };
  formShow.value = true;
  bindSave(prepared.isCurrent);
}
async function openDetail(row: RuleTemplateVo) {
  const prepared = await prepare(row, 'detail');
  if (!prepared?.isCurrent() || !prepared.fresh) return;
  detailRow.value = prepared.fresh;
  detailMode.value = prepared.fresh.publishedVersionId ? 'PUBLISHED' : 'DRAFT';
  detailShow.value = true;
}
async function confirmOperation(row: RuleTemplateVo, action: 'publish' | 'adopt' | 'retire') {
  const prepared = await prepare(row, action);
  if (!prepared?.isCurrent() || !prepared.fresh) return;
  const { fresh, context, isCurrent } = prepared;
  const options = {
    publish: {
      title: '发布模板草稿',
      content: '发布会冻结本次核对的正式知识版本，不会直接创建或更新实时预警规则。',
      positiveText: '发布',
    },
    adopt: {
      title: '采用模板',
      content: `采用 ${fresh.name} V${fresh.publishedVersionNo}，在 ${context.orgName || context.orgId} 创建未启用保护方案。仍须设置适用范围、办理共同评估和本人核对。`,
      positiveText: '创建保护方案',
    },
    retire: {
      title: '退役模板',
      content: '退役后不能恢复或继续采用，但可以复制为使用新编码的模板；既有保护方案不受影响。',
      positiveText: '退役',
    },
  }[action];
  let submitted = false;
  confirmation = (action === 'adopt' ? dialog.info : dialog.warning)({
    ...options,
    negativeText: '取消',
    onNegativeClick: clearIntent,
    onClose: clearIntent,
    onMaskClick: clearIntent,
    onPositiveClick: async () => {
      if (!isCurrent() || submitted || operating.value || saving.value) return;
      submitted = true;
      operating.value = true;
      try {
        if (action === 'publish')
          await ruleTemplateApi.publish(fresh.id!, {
            versionId: fresh.versionId!,
            definitionHash: fresh.definitionHash!,
          });
        else if (action === 'retire') await ruleTemplateApi.retire(fresh.id!);
        else
          await protectionPolicyApi.adopt({
            templateId: fresh.id!,
            code: fresh.code,
            name: fresh.name,
            templateVersionId: fresh.publishedVersionId!,
            expectedOrgId: context.orgId,
          });
        if (!isCurrent()) return;
        if (action === 'adopt') message.success('已创建未启用保护方案，请继续核对适用范围和实际共同评估');
        clearIntent();
        await reload();
      } catch (reason) {
        if (isCurrent()) fail(reason);
      } finally {
        if (isCurrent()) operating.value = false;
      }
    },
  });
}
const publish = (row: RuleTemplateVo) => confirmOperation(row, 'publish');
const adopt = (row: RuleTemplateVo) => confirmOperation(row, 'adopt');
const retire = (row: RuleTemplateVo) => confirmOperation(row, 'retire');
watch(
  formShow,
  (value) => {
    if (!value) clearIntent();
  },
  { flush: 'sync' },
);
watch(
  detailShow,
  (value) => {
    if (!value) clearIntent();
  },
  { flush: 'sync' },
);
watch([keyword, statusFilter], clearIntent, { flush: 'sync' });
function leave() {
  ++generation;
  clearData();
  loading.value = false;
  error.value = '';
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
.template-help {
  color: var(--n-text-color-3);
}
.template-help-content {
  max-width: 360px;
  line-height: 1.6;
}
.template-name-cell {
  min-width: 0;
}
.template-name {
  color: var(--n-text-color);
  font-size: 14px;
  font-weight: 600;
}
.template-meta,
.muted-text {
  color: var(--n-text-color-disabled);
  font-size: 12px;
}
.template-description {
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  color: var(--n-text-color-3);
  font-size: 12px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.metric-summary-line + .metric-summary-line {
  margin-top: 4px;
}
.detail-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.detail-code {
  color: var(--n-text-color-3);
  font-size: 12px;
}
.detail-description {
  margin-top: 6px;
  color: var(--n-text-color-2);
  line-height: 1.6;
}
.detail-group + .detail-group {
  margin-top: 12px;
}
</style>

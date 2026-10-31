<template>
  <main ref="pageElement" class="entity-search" @scroll="rememberScroll">
    <header class="page-heading">
      <h1>藏品检索</h1>
      <NRadioGroup v-model:value="mode" size="small" aria-label="检索内容">
        <NRadioButton value="collections">藏品</NRadioButton><NRadioButton value="records">相关记录</NRadioButton>
      </NRadioGroup>
    </header>
    <CollectionRelatedSearch v-if="mode === 'records'" />
    <template v-else>
      <NAlert v-if="contextError" type="error" :show-icon="false"
        >{{ contextError }} <NButton text @click="bootstrap()">重试</NButton></NAlert
      >
      <NSpin v-if="!ready && !contextError" class="initial-loading" description="正在读取收藏单位和检索权限" />
      <template v-if="ready">
        <NCard size="small" class="criteria-card">
          <form @submit.prevent="applyCriteria">
            <div class="keyword-row">
              <NInput
                v-model:value="draft.keyword"
                clearable
                placeholder="名称、登记号、辅助号、年代、质地；空格分隔多个关键词"
                :input-props="{ 'aria-label': '藏品关键词' }"
                @compositionstart="composing = true"
                @compositionend="composing = false"
              />
              <NButton type="primary" attr-type="submit" :disabled="composing">检索</NButton>
              <NButton @click="resetCriteria">重置</NButton>
            </div>
            <div class="common-filters">
              <label
                >收藏单位<NSelect v-model:value="draft.orgId" :options="orgOptions" placeholder="收藏单位"
              /></label>
              <label>登记范围<NSelect v-model:value="draft.registrationScope" :options="registrationOptions" /></label>
              <label
                >藏品性质<NSelect
                  v-model:value="draft.nature"
                  :options="collectionNatureOptions"
                  clearable
                  placeholder="全部性质"
              /></label>
              <label
                >藏品分类<NSelect
                  v-model:value="draft.categoryId"
                  :options="visibleCategoryOptions"
                  clearable
                  filterable
                  placeholder="全部分类"
              /></label>
              <label>文物级别<HDictSelect v-model:value="draft.level" type="col_relic_level" clearable /></label>
              <label
                >保管状态<HDictSelect
                  v-model:value="draft.lifeStatus"
                  type="col_life_status"
                  clearable
                  placeholder="全部状态"
              /></label>
            </div>
            <div class="criteria-actions">
              <NButton text @click="advanced = !advanced"
                >{{ advanced ? '收起' : '展开' }}高级筛选{{ advancedCount ? `（${advancedCount}）` : '' }}</NButton
              >
              <NPopover trigger="click" placement="bottom-end">
                <template #trigger><NButton text>保存当前条件</NButton></template>
                <NSpace vertical
                  ><NInput v-model:value="savedName" placeholder="条件名称，例如青铜器影像核对" :maxlength="40" />
                  <NButton type="primary" @click="saveCriteria">保存</NButton></NSpace
                >
              </NPopover>
              <NSelect
                v-model:value="savedChoice"
                class="saved-select"
                :options="savedOptions"
                clearable
                placeholder="已保存条件"
                @update:value="loadSaved"
              />
              <NButton v-if="savedChoice" text type="error" @click="removeSaved">删除条件</NButton>
            </div>
            <div v-if="advanced" class="advanced-filters">
              <label
                >总登记号
                <div class="number-filter">
                  <NInput v-model:value="draft.totalRegNo" clearable /><NSelect
                    v-model:value="draft.numberMatch"
                    :options="numberOptions"
                  /></div
              ></label>
              <label>分类号<NInput v-model:value="draft.classNo" clearable /></label>
              <label>藏品年代<NInput v-model:value="draft.era" clearable /></label>
              <label>藏品质地<HDictSelect v-model:value="draft.texture" type="col_texture" tag clearable /></label>
              <label>来源方式<HDictSelect v-model:value="draft.source" type="col_source" clearable /></label>
              <label
                >库房库位<NSelect
                  v-model:value="draft.warehouseId"
                  :options="visibleWarehouseOptions"
                  clearable
                  filterable
                  placeholder="全部库位"
              /></label>
              <label
                >编目状态<HDictSelect v-model:value="draft.catalogStatus" type="col_catalog_status" clearable
              /></label>
              <label
                >完残情况<HDictSelect v-model:value="draft.completeness" type="col_completeness" clearable
              /></label>
              <label>藏品影像<NSelect v-model:value="draft.hasImage" :options="presenceOptions" clearable /></label>
              <label
                >档案附件<NSelect v-model:value="draft.hasAttachment" :options="presenceOptions" clearable
              /></label>
              <label class="date-filter"
                >登记日期<NDatePicker
                  v-model:value="registeredRange"
                  type="daterange"
                  clearable
                  @update:value="setRegisteredDates"
              /></label>
              <label class="date-filter"
                >取得日期<NDatePicker v-model:value="acquireRange" type="daterange" clearable @update:value="setDates"
              /></label>
            </div>
          </form>
          <NSpace v-if="activeCriteria.length" size="small" class="active-criteria" aria-label="已应用筛选条件">
            <NTag
              v-for="item in activeCriteria"
              :key="item.key"
              closable
              size="small"
              @close="removeCriterion(item.key)"
              ><NEllipsis :tooltip="true" style="max-width: 220px">{{ item.label }}</NEllipsis></NTag
            >
          </NSpace>
          <p v-if="metadataError" class="metadata-error">{{ metadataError }}</p>
        </NCard>
        <div class="result-controls">
          <div class="result-summary">
            <strong>{{ total }}</strong><span>条藏品</span>
            <span class="result-scope">{{ appliedScope }}</span>
          </div>
          <NSpace align="center"
            ><NSelect v-model:value="sort" class="sort-select" :options="sortOptions" @update:value="changeSort" />
            <NRadioGroup v-model:value="viewMode" size="small" aria-label="展示方式"
              ><NRadioButton value="table">表格</NRadioButton
              ><NRadioButton value="gallery">图册</NRadioButton></NRadioGroup
            >
          </NSpace>
        </div>
        <NAlert v-if="readError" type="error" :show-icon="false"
          >{{ readError }} <NButton text @click="tableRef?.handleRefresh()">重新读取</NButton></NAlert
        >
        <div v-if="selected.size" class="selection-bar" aria-live="polite">
          <span>已选 {{ selected.size }} 条记录（可跨页，最多 200 条）</span>
          <NSpace size="small" align="center">
            <CollectionActionMenu label="导出与打印" :loading="actionBusy">
              <NButton quaternary size="small" :loading="actionBusy" @click="exportSelection">导出选择清单</NButton>
              <NButton quaternary size="small" :loading="actionBusy" @click="printSelection">{{
                selected.size > 1 ? '批量打印登记表' : '打印登记表'
              }}</NButton>
            </CollectionActionMenu>
            <CollectionActionMenu label="业务办理" v-if="canManage" :loading="actionBusy">
              <NButton quaternary v-if="canManage" size="small" :loading="actionBusy" @click="startBusiness('requisition')"
                >提用申请</NButton
              >
              <NButton quaternary v-if="canManage" size="small" :loading="actionBusy" @click="startBusiness('loan')"
                >借展申请</NButton
              >
              <NButton quaternary v-if="canManage" size="small" :loading="actionBusy" @click="startBusiness('inventory')"
                >创建清点批次</NButton
              >
            </CollectionActionMenu>
            <NButton size="small" text @click="selected.clear()">清空选择</NButton>
          </NSpace>
        </div>
        <HPageDataTable auto-scroll-x
          ref="tableRef"
          class="results-table"
          :show-header="false"
          :is-add="false"
          :is-export="false"
          :is-edit="false"
          :is-delete="false"
          :show-operate="false"
          :fetch-data="fetchCollections"
          :params="committed"
          :columns="columns"
          :row-key="rowKey"
          :initial-page="initialPage"
          :initial-page-size="initialPageSize"
          clear-selection-on-fetch
          :pagination="{ pageSizes: [10, 30, 50] }"
          reset-page-on-size-change
          :column-configurable="viewMode === 'table'"
          :column-config-options="columnOptions"
          :default-visible-column-keys="defaultColumns"
          @load-data="restoreScroll"
          :always-visible-column-keys="['pick', 'totalRegNo', 'name', 'actions']"
          :column-config-key="`collection-entity-search.columns.v1.${accountId}`"
        >
          <template #activeBtn><NButton size="small" @click="selectPage">选择本页</NButton></template>
          <template v-if="viewMode === 'gallery'" #data-view="view">
            <NSpin :show="view.loading" class="gallery-content">
              <div v-if="view.data.length" class="gallery-grid">
                <article v-for="row in view.data" :key="row.id" class="gallery-card">
                  <div class="gallery-image">
                    <HImage
                      :src="row.previewImage || row.coverImage || ''"
                      :width="180"
                      :height="132"
                      type="p"
                      hide-reload
                      :alt="row.name || '藏品影像'"
                    />
                  </div>
                  <div class="gallery-title">
                    <NCheckbox
                      :checked="selected.has(row.id)"
                      :aria-label="`选择 ${row.totalRegNo}`"
                      @update:checked="(value) => selectRow(row, value)"
                    />
                    <NButton text type="primary" @click="openDossier(row)"
                      ><NEllipsis :tooltip="true">{{ row.name }}</NEllipsis></NButton
                    >
                  </div>
                  <p>{{ row.totalRegNo }} · {{ row.era || '年代待核实' }}</p>
                  <p>
                    <NEllipsis :tooltip="true"
                      >{{ dictLabel('col_texture', row.texture) }} · {{ row.warehouseName || '库位待核实' }}</NEllipsis
                    >
                  </p>
                  <div class="gallery-footer">
                    <NTag size="small" :bordered="false">{{ dictLabel('col_life_status', row.lifeStatus) }}</NTag
                    ><NButton text size="small" @click="openFiles(row)">影像与附件</NButton>
                  </div>
                </article>
              </div>
              <NEmpty v-else :description="readError ? '读取失败，请重试' : '没有符合条件的藏品，可放宽筛选条件'" />
            </NSpin>
            <NPagination
              :page="view.pagination.page"
              :page-size="view.pagination.pageSize"
              :item-count="view.pagination.itemCount"
              :page-sizes="view.pagination.pageSizes"
              show-size-picker
              show-quick-jumper
              class="gallery-pagination"
              @update:page="view.onPageChange"
              @update:page-size="view.onPageSizeChange"
            />
          </template>
        </HPageDataTable>
      </template>
    </template>
    <NDrawer v-model:show="filesVisible" :width="'min(640px, 100vw)'" :auto-focus="false">
      <NDrawerContent
        :title="`${fileCollection?.totalRegNo || ''} ${fileCollection?.name || ''} · 影像与附件`"
        closable
      >
        <NAlert v-if="fileError" type="error"
          >{{ fileError }} <NButton text @click="fileCollection && openFiles(fileCollection)">重新读取</NButton></NAlert
        >
        <NSpin :show="fileLoading"
          ><h3>藏品影像</h3>
          <HFileList v-if="imageIds.length" :value="imageIds" /><NEmpty v-else description="暂无影像" />
          <h3>档案附件</h3>
          <HFileList v-if="attachmentIds.length" :value="attachmentIds" /><NEmpty v-else description="暂无附件"
        /></NSpin>
      </NDrawerContent>
    </NDrawer>
    <CollectionRequisitionCaseDrawer ref="requisitionRef" @saved="tableRef?.handleRefresh()" />
    <CollectionLoanCaseDrawer ref="loanRef" @saved="tableRef?.handleRefresh()" />
    <NModal v-model:show="inventoryVisible" preset="card" title="创建清点批次" class="inventory-modal">
      <NAlert :show-icon="false" type="info"
        >已选 {{ inventoryIds.length }} 条藏品记录。创建批次后，到清点核查页面录入实物核查结果。</NAlert
      >
      <NForm label-placement="top"
        ><NFormItem label="批次名称"><NInput v-model:value="inventoryName" :maxlength="100" /></NFormItem>
        <NFormItem label="清点周期（月）"
          ><NInputNumber v-model:value="inventoryCycle" :min="1" :max="120"
        /></NFormItem>
        <NFormItem label="计划日期"><NDatePicker v-model:value="inventoryDates" type="daterange" /></NFormItem
      ></NForm>
      <template #footer
        ><NSpace justify="end"
          ><NButton @click="inventoryVisible = false">取消</NButton
          ><NButton type="primary" :loading="actionBusy" @click="createInventory">创建批次</NButton></NSpace
        ></template
      >
    </NModal>
    <CollectionPrintPreviewModal
      v-model:show="printVisible"
      type="COLLECTION_ARCHIVE"
      :ids="printIds"
      title="藏品登记表"
    />
  </main>
</template>
<script setup lang="ts">
import CollectionActionMenu from '../components/CollectionActionMenu.vue';
import { isRegistered } from '../utils/registrationStatus';
import { saveAs } from 'file-saver';
import {
  computed,
  h,
  nextTick,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  reactive,
  ref,
  resolveComponent,
  watch,
} from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NButton, NCheckbox, useMessage, type DataTableColumns } from 'naive-ui';
import { useAuthStore } from '@/store';
import { frontType } from '@/modules/sys/api/dict';
import { collectionApi } from '../api/collection';
import { orgApi } from '../api/org';
import { categoryApi } from '../api/category';
import { warehouseApi } from '../api/warehouse';
import { inventoryApi } from '../api/inventory';
import type { CollectionVo } from '../typings/collection';
import CollectionRelatedSearch from '../components/CollectionRelatedSearch.vue';
import CollectionRequisitionCaseDrawer from '../components/CollectionRequisitionCaseDrawer.vue';
import CollectionLoanCaseDrawer from '../components/CollectionLoanCaseDrawer.vue';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import { collectionNatureLabel, collectionNatureOptions } from '../utils/collectionNature';
import {
  collectionDossierTarget,
  criteriaKeys,
  csvCell,
  readSearchCriteria,
  registrationLabel,
  registrationOptions,
  sortOptions,
  validateSelectedRows,
  type SearchCriteria,
} from '../utils/collectionEntitySearch';
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore(),
  message = useMessage();
const accountId = computed(() => String(auth.userInfo?.id || ''));
const identity = computed(() => `${accountId.value}:${auth.identityRevision}:${auth.identitySyncing}`);
const mode = ref(route.query.mode === 'records' ? 'records' : 'collections');
const viewMode = ref(route.query.view === 'gallery' ? 'gallery' : 'table');
const initialPage = computed(() => Math.max(1, Number(route.query.page) || 1));
const initialPageSize = computed(() =>
  [10, 30, 50].includes(Number(route.query.pageSize)) ? Number(route.query.pageSize) : 30,
);
const draft = reactive<any>(readSearchCriteria(route.query)),
  committed = ref<SearchCriteria>({});
for (const key of ['hasImage', 'hasAttachment']) if (typeof draft[key] === 'boolean') draft[key] = String(draft[key]);
const sort = ref(String(draft.searchSort)),
  composing = ref(false),
  advanced = ref(false);
const ready = ref(false),
  contextError = ref(''),
  metadataError = ref(''),
  readError = ref('');
const orgOptions = ref<any[]>([]),
  categoryOptions = ref<any[]>([]),
  warehouseOptions = ref<any[]>([]);
const visibleCategoryOptions = computed(() => categoryOptions.value.filter((o) => !o.orgId || o.orgId === draft.orgId));
const visibleWarehouseOptions = computed(() =>
  warehouseOptions.value.filter((o) => !o.orgId || o.orgId === draft.orgId),
);
const defaultOrgId = ref<string | null>(null),
  tableRef = ref<any>(),
  currentRows = ref<CollectionVo[]>([]),
  total = ref(0);
const selected = reactive(new Map<string, CollectionVo>()),
  actionBusy = ref(false);
const requisitionRef = ref<any>(),
  loanRef = ref<any>();
const canManage = computed(
  () => auth.hasPermission('COLLECTION_ADMIN,COLLECTION_STORAGE') && committed.value.orgId === defaultOrgId.value,
);
const numberOptions = [
  { label: '包含', value: 'CONTAINS' },
  { label: '完全匹配', value: 'EXACT' },
];
const presenceOptions = [
  { label: '有', value: 'true' },
  { label: '无', value: 'false' },
];
const acquireRange = ref<[number, number] | null>(
  draft.acquireDateStart && draft.acquireDateEnd
    ? [Number(draft.acquireDateStart), Number(draft.acquireDateEnd)]
    : null,
);
const registeredRange = ref<[number, number] | null>(
  draft.registeredDateStart && draft.registeredDateEnd
    ? [Number(draft.registeredDateStart), Number(draft.registeredDateEnd)]
    : null,
);
const pageElement = ref<HTMLElement>(),
  scrollTop = ref(0);
let restoringScroll = false;
function rememberScroll() {
  if (!restoringScroll) scrollTop.value = pageElement.value?.scrollTop || 0;
}
function restoreScroll() {
  if (restoringScroll)
    void nextTick(() => {
      if (active && pageElement.value) pageElement.value.scrollTop = scrollTop.value;
      restoringScroll = false;
    });
}
const advancedCount = computed(
  () =>
    criteriaKeys.filter(
      (key) =>
        ![
          'keyword',
          'orgId',
          'registrationScope',
          'nature',
          'lifeStatus',
          'searchSort',
          'numberMatch',
          'categoryId',
          'level',
        ].includes(key) &&
        draft[key] !== null &&
        draft[key] !== undefined &&
        draft[key] !== '',
    ).length,
);
const appliedScope = computed(
  () => registrationOptions.find((o) => o.value === committed.value.registrationScope)?.label || '正式馆藏',
);
const dictionaries = reactive<Record<string, Record<string, string>>>({});
function dictLabel(type: string, value: unknown) {
  if (!value) return '—';
  return dictionaries[type]?.[String(value)] || (/^[A-Z_]+$/.test(String(value)) ? '待核实' : String(value));
}
const criteriaLabels: Record<string, string> = {
  keyword: '检索内容',
  nature: '藏品性质',
  registrationScope: '登记范围',
  lifeStatus: '保管状态',
  categoryId: '藏品分类',
  totalRegNo: '总登记号',
  classNo: '分类号',
  era: '藏品年代',
  texture: '藏品质地',
  level: '文物级别',
  source: '来源方式',
  warehouseId: '库房库位',
  catalogStatus: '编目状态',
  completeness: '完残情况',
  hasImage: '藏品影像',
  hasAttachment: '档案附件',
  acquireDateStart: '入藏起始',
  acquireDateEnd: '入藏截止',
  registeredDateStart: '登记起始',
  registeredDateEnd: '登记截止',
};
const activeCriteria = computed(() =>
  criteriaKeys
    .filter(
      (key) =>
        !['orgId', 'searchSort', 'numberMatch'].includes(key) &&
        (key !== 'registrationScope' || committed.value[key] !== 'REGISTERED') &&
        committed.value[key] !== undefined &&
        committed.value[key] !== null &&
        committed.value[key] !== '',
    )
    .map((key) => {
      const value = committed.value[key];
      const display =
        key === 'nature'
          ? collectionNatureLabel(value)
          : key === 'registrationScope'
            ? registrationOptions.find((o) => o.value === value)?.label
            : key === 'hasImage' || key === 'hasAttachment'
              ? value
                ? '有'
                : '无'
              : key.endsWith('DateStart') || key.endsWith('DateEnd')
                ? new Date(Number(value)).toLocaleDateString('sv-SE')
                : key === 'categoryId'
                  ? categoryOptions.value.find((o) => o.value === value)?.label
                  : key === 'warehouseId'
                    ? warehouseOptions.value.find((o) => o.value === value)?.label
                    : dictTypes[key]
                      ? dictLabel(dictTypes[key], value)
                      : value;
      return { key, label: `${criteriaLabels[key] || key}：${display || '待核实'}` };
    }),
);
function removeCriterion(key: string) {
  draft[key] = null;
  if (key.startsWith('acquireDate')) {
    acquireRange.value = null;
    draft.acquireDateStart = null;
    draft.acquireDateEnd = null;
  }
  if (key.startsWith('registeredDate')) {
    registeredRange.value = null;
    draft.registeredDateStart = null;
    draft.registeredDateEnd = null;
  }
  void applyCriteria();
}
function setRegisteredDates(value: [number, number] | null) {
  draft.registeredDateStart = value?.[0] ?? null;
  draft.registeredDateEnd = value?.[1] ?? null;
}
const saved = ref<Array<{ id: string; name: string; criteria: SearchCriteria }>>([]),
  savedName = ref(''),
  savedChoice = ref<string | null>(null);
const savedOptions = computed(() => saved.value.map((x) => ({ value: x.id, label: x.name })));
const storageKey = () => `collection.entity-search.conditions.v1.${accountId.value}`;
const rowKey = (row: CollectionVo) => String(row.id);
const defaultColumns = [
  'pick',
  'coverImage',
  'totalRegNo',
  'name',
  'categoryName',
  'era',
  'texture',
  'level',
  'registrationScope',
  'lifeStatus',
  'orgName',
  'warehouseName',
  'actions',
];
const textColumns = [
  ['totalRegNo', '总登记号', 120],
  ['name', '藏品名称', 190],
  ['classNo', '分类号', 110],
  ['categoryName', '藏品分类', 130],
  ['era', '年代', 110],
  ['texture', '质地', 100],
  ['level', '文物级别', 100],
  ['nature', '藏品性质', 180],
  ['registrationScope', '登记范围', 120],
  ['lifeStatus', '保管状态', 110],
  ['warehouseName', '库位', 150],
  ['orgName', '收藏单位', 160],
  ['quantity', '数量', 90],
  ['source', '来源', 110],
  ['catalogStatus', '编目状态', 110],
  ['acquireDate', '取得日期', 115],
  ['mediaCount', '影像数量', 90],
  ['attachmentCount', '附件数量', 90],
] as const;
const dictTypes: Record<string, string> = {
  texture: 'col_texture',
  level: 'col_relic_level',
  lifeStatus: 'col_life_status',
  source: 'col_source',
  catalogStatus: 'col_catalog_status',
};
function cell(row: CollectionVo, key: string): string {
  if (dictTypes[key]) return dictLabel(dictTypes[key], (row as any)[key]);
  if (key === 'nature') return collectionNatureLabel(row.nature);
  if (key === 'registrationScope') return registrationLabel(row);
  if (key === 'orgName') return orgOptions.value.find((o) => o.value === row.orgId)?.label || '待核实';
  if (key === 'quantity') return `${row.quantity ?? '—'} ${row.unit || ''}`;
  if (key === 'acquireDate') return row.acquireDate ? new Date(row.acquireDate).toLocaleDateString('sv-SE') : '—';
  return String((row as any)[key] ?? '—');
}
const columns: DataTableColumns<CollectionVo> = [
  {
    key: 'pick',
    title: '选择',
    width: 56,
    fixed: 'left',
    render: (row) =>
      h(NCheckbox, {
        checked: selected.has(String(row.id)),
        'aria-label': `选择 ${row.totalRegNo}`,
        'onUpdate:checked': (value: boolean) => selectRow(row, value),
      }),
  },
  {
    key: 'coverImage',
    title: '影像',
    width: 72,
    render: (row) =>
      h(resolveComponent('HImage'), {
        src: row.previewImage || row.coverImage || '',
        width: 44,
        height: 44,
        size: 44,
        type: 'p',
        hideReload: true,
        alt: row.name || '藏品影像',
      }),
  },
  ...textColumns.map(([key, title, width]) => ({
    key,
    title,
    width,
    resizable: true,
    ellipsis: { tooltip: true },
    render:
      key === 'name'
        ? (row: CollectionVo) =>
            h(
              NButton,
              {
                text: true,
                type: 'primary',
                onClick: () => openDossier(row),
                style: 'max-width:100%;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;display:block',
              },
              () => row.name,
            )
        : (row: CollectionVo) => cell(row, key),
  })),
  {
    key: 'actions',
    title: '查看',
    width: 160,
    fixed: 'right',
    render: (row) =>
      h('div', { style: 'display:flex;gap:12px;white-space:nowrap' }, [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => openDossier(row) }, () => '一物一档'),
        h(NButton, { text: true, size: 'small', onClick: () => openFiles(row) }, () => '影像附件'),
      ]),
  },
];
const columnOptions = columns.map((c: any) => ({ value: String(c.key), label: String(c.title) }));
let generation = 0,
  active = true,
  disposed = false,
  request = 0;
function current(version: number, key: string) {
  return !disposed && active && version === generation && key === identity.value;
}
async function bootstrap(preserveSelection = false) {
  const previousIds = preserveSelection === true ? [...selected.keys()] : [];
  const version = ++generation,
    key = identity.value;
  ready.value = false;
  contextError.value = '';
  metadataError.value = '';
  selected.clear();
  currentRows.value = [];
  total.value = 0;
  closeDetails();
  if (!accountId.value || auth.identitySyncing) return;
  try {
    const response: any = await orgApi.businessContext();
    if (!current(version, key)) return;
    orgOptions.value = response.data?.orgs || [];
    defaultOrgId.value = response.data?.defaultOrgId || null;
    if (!orgOptions.value.length) throw new Error('当前账号没有可检索的收藏单位');
    if (!orgOptions.value.some((o) => o.value === draft.orgId))
      draft.orgId = defaultOrgId.value || orgOptions.value[0].value;
    committed.value = readSearchCriteria(draft);
    sort.value = String(committed.value.searchSort);
    try {
      saved.value = JSON.parse(localStorage.getItem(storageKey()) || '[]')
        .filter((x: any) => x && typeof x.name === 'string' && x.criteria)
        .slice(0, 20);
    } catch {
      saved.value = [];
    }
    ready.value = true;
    if (previousIds.length) {
      try {
        const rows = await checkedRows(previousIds);
        if (current(version, key)) rows.forEach((row) => selected.set(String(row.id), row));
      } catch {
        if (current(version, key)) message.warning('部分已选藏品已变化，请重新选择');
      }
    }
    const metadata = await Promise.allSettled([
      categoryApi.list(),
      warehouseApi.list(),
      ...Object.values(dictTypes).map((type) => frontType(type, true)),
    ]);
    if (!current(version, key)) return;
    categoryOptions.value =
      metadata[0].status === 'fulfilled'
        ? ((metadata[0].value as any).data || []).map((x: any) => ({ value: x.id, label: x.name, orgId: x.orgId }))
        : [];
    warehouseOptions.value =
      metadata[1].status === 'fulfilled'
        ? ((metadata[1].value as any).data || []).map((x: any) => ({ value: x.id, label: x.name, orgId: x.orgId }))
        : [];
    Object.values(dictTypes).forEach((type, index) => {
      const entry = metadata[index + 2];
      if (entry.status === 'fulfilled' && Array.isArray((entry.value as any)?.data))
        dictionaries[type] = Object.fromEntries(
          ((entry.value as any).data || []).map((x: any) => [String(x.value), x.label]),
        );
    });
    if (metadata.some((x) => x.status === 'rejected' || !Array.isArray((x.value as any)?.data)))
      metadataError.value = '部分筛选字典或库位读取失败，仍可使用其他条件检索。';
  } catch (error: any) {
    if (current(version, key)) contextError.value = error?.message || '读取检索权限失败';
  }
}
async function fetchCollections(params: any) {
  const version = generation,
    key = identity.value,
    attempt = ++request;
  readError.value = '';
  currentRows.value = [];
  try {
    const response: any = await collectionApi.page(params);
    if (!current(version, key) || attempt !== request) return null;
    currentRows.value = response.data?.list || [];
    total.value = Number(response.data?.total || 0);
    await syncRoute(params.pageNum, params.pageSize);
    return response;
  } catch (error: any) {
    if (!current(version, key) || attempt !== request) return null;
    selected.clear();
    currentRows.value = [];
    total.value = 0;
    readError.value = error?.message || '检索失败，请重新读取';
    return { data: { list: [], total: 0, pageCount: 0 } };
  }
}
async function syncRoute(page = Number(route.query.page) || 1, pageSize = Number(route.query.pageSize) || 30) {
  if (!active || disposed) return;
  const query: any = {
    ...route.query,
    mode: mode.value,
    view: viewMode.value,
    page: String(page),
    pageSize: String(pageSize),
  };
  criteriaKeys.forEach((key) => {
    delete query[key];
    if (committed.value[key] !== undefined && committed.value[key] !== null && committed.value[key] !== '')
      query[key] = String(committed.value[key]);
  });
  await router.replace({ query });
}
async function applyCriteria() {
  if (composing.value) return;
  if (draft.orgId !== committed.value.orgId) {
    selected.clear();
    draft.warehouseId = null;
  }
  committed.value = readSearchCriteria(draft);
  sort.value = String(committed.value.searchSort);
  ++request;
  closeDetails();
  await syncRoute(1);
  await nextTick();
  tableRef.value?.handleQuery();
}
async function resetCriteria() {
  Object.keys(draft).forEach((key) => delete draft[key]);
  Object.assign(draft, readSearchCriteria({}), { orgId: defaultOrgId.value || orgOptions.value[0]?.value });
  acquireRange.value = null;
  registeredRange.value = null;
  savedChoice.value = null;
  await applyCriteria();
}
function setDates(value: [number, number] | null) {
  draft.acquireDateStart = value?.[0] ?? null;
  draft.acquireDateEnd = value?.[1] ?? null;
}
function changeSort(value: string) {
  draft.searchSort = value;
  void applyCriteria();
}
function saveCriteria() {
  const name = savedName.value.trim();
  if (!name) {
    message.warning('请填写条件名称');
    return;
  }
  const criteria = readSearchCriteria(draft),
    existing = saved.value.find((x) => x.name === name);
  if (!existing && saved.value.length >= 20) {
    message.warning('最多保存 20 个条件，请先删除不再使用的条件');
    return;
  }
  if (existing) existing.criteria = criteria;
  else saved.value.push({ id: `${Date.now()}`, name, criteria });
  localStorage.setItem(storageKey(), JSON.stringify(saved.value));
  savedName.value = '';
  message.success('检索条件已保存');
}
function loadSaved(id: string | null) {
  const item = saved.value.find((x) => x.id === id);
  if (!item) return;
  Object.keys(draft).forEach((key) => delete draft[key]);
  Object.assign(draft, readSearchCriteria(item.criteria));
  for (const key of ['hasImage', 'hasAttachment']) if (typeof draft[key] === 'boolean') draft[key] = String(draft[key]);
  if (!orgOptions.value.some((o) => o.value === draft.orgId))
    draft.orgId = defaultOrgId.value || orgOptions.value[0]?.value;
  acquireRange.value =
    draft.acquireDateStart && draft.acquireDateEnd
      ? [Number(draft.acquireDateStart), Number(draft.acquireDateEnd)]
      : null;
  registeredRange.value =
    draft.registeredDateStart && draft.registeredDateEnd
      ? [Number(draft.registeredDateStart), Number(draft.registeredDateEnd)]
      : null;
  advanced.value = advancedCount.value > 0;
  void applyCriteria();
}
function removeSaved() {
  saved.value = saved.value.filter((x) => x.id !== savedChoice.value);
  savedChoice.value = null;
  localStorage.setItem(storageKey(), JSON.stringify(saved.value));
}
function selectRow(row: CollectionVo, checked: boolean) {
  const id = String(row.id);
  if (!checked) selected.delete(id);
  else if (selected.size < 200 || selected.has(id)) selected.set(id, row);
  else message.warning('最多选择 200 条藏品记录，请分批办理');
}
function selectPage() {
  currentRows.value.forEach((row) => selectRow(row, true));
}
async function checkedRows(ids = [...selected.keys()]) {
  if (!ids.length) throw new Error('请先选择藏品');
  const version = generation,
    key = identity.value,
    orgId = committed.value.orgId;
  try {
    const response: any = await collectionApi.page({ collectionIds: ids.join(','), orgId, pageNum: 1, pageSize: 200 });
    if (!current(version, key) || orgId !== committed.value.orgId) throw new Error('账号或收藏单位已变化，请重新选择');
    return validateSelectedRows(ids, response.data?.list || [], orgId);
  } catch (error) {
    if (current(version, key)) {
      selected.clear();
      closeDetails();
      currentRows.value = [];
      total.value = 0;
      tableRef.value?.updateTableData([]);
      readError.value = '已选藏品复核失败，请重新读取并选择当前可办理的藏品。';
    }
    throw error;
  }
}
async function action(run: () => Promise<void>) {
  if (actionBusy.value) return;
  actionBusy.value = true;
  try {
    await run();
  } catch (error: any) {
    message.error(error?.message || '操作失败');
  } finally {
    actionBusy.value = false;
  }
}
function openDossier(row: CollectionVo) {
  void router.push(collectionDossierTarget(row, route.fullPath));
}
function exportSelection() {
  return action(async () => {
    const rows = await checkedRows();
    const keys = [
      'totalRegNo',
      'name',
      'classNo',
      'nature',
      'registrationScope',
      'era',
      'texture',
      'level',
      'lifeStatus',
      'warehouseName',
      'quantity',
    ];
    const header = keys.map((key) => textColumns.find((c) => c[0] === key)?.[1] || key);
    const csv = [
      header.map(csvCell).join(','),
      ...rows.map((row) => keys.map((key) => csvCell(cell(row, key))).join(',')),
    ].join('\r\n');
    saveAs(
      new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' }),
      `藏品选择清单-${new Date().toLocaleDateString('sv-SE')}.csv`,
    );
    message.success(`已生成 ${rows.length} 条藏品的选择清单`);
  });
}
const printVisible = ref(false),
  printIds = ref<string[]>([]);
function printSelection() {
  return action(async () => {
    const rows = await checkedRows();
    printIds.value = rows.map((row) => String(row.id));
    printVisible.value = true;
  });
}
const inventoryVisible = ref(false),
  inventoryIds = ref<string[]>([]),
  inventoryName = ref(''),
  inventoryCycle = ref(12),
  inventoryDates = ref<[number, number] | null>(null);
function startBusiness(type: string) {
  return action(async () => {
    const rows = await checkedRows();
    if (!canManage.value) throw new Error('当前账号没有办理权限');
    if (rows.some((row) => !isRegistered(row) || row.lifeStatus === 'DEREGISTERED'))
      throw new Error('请选择已正式登记且未退出馆藏的藏品');
    if (type !== 'inventory' && rows.some((row) => row.lifeStatus !== 'IN_STORE' || row.inventoryHold))
      throw new Error('提用或借展须选择在库且没有清点异常限制的藏品');
    const ids = rows.map((row) => String(row.id));
    if (type === 'requisition')
      await requisitionRef.value?.open(undefined, { orgId: committed.value.orgId, collectionIds: ids.join(',') });
    else if (type === 'loan')
      await loanRef.value?.open(undefined, { orgId: committed.value.orgId, collectionIds: ids.join(',') });
    else {
      inventoryIds.value = ids;
      inventoryName.value = '';
      inventoryDates.value = null;
      inventoryVisible.value = true;
    }
  });
}
function createInventory() {
  return action(async () => {
    if (!inventoryName.value.trim() || !inventoryDates.value) throw new Error('请填写批次名称和计划日期');
    await checkedRows(inventoryIds.value);
    if (!canManage.value) throw new Error('当前账号没有创建清点批次的权限');
    await inventoryApi.saveBatch({
      orgId: String(committed.value.orgId),
      batchName: inventoryName.value.trim(),
      cycleMonths: inventoryCycle.value,
      collectionIds: inventoryIds.value.join(','),
      startedAt: inventoryDates.value[0],
      finishedAt: inventoryDates.value[1],
    });
    inventoryVisible.value = false;
    message.success('清点批次已创建');
    await router.push({
      path: '/collection/inventory',
      query: { keyword: inventoryName.value.trim(), searchOrigin: 'collection-search' },
    });
  });
}
const filesVisible = ref(false),
  fileCollection = ref<CollectionVo | null>(null),
  imageIds = ref<string[]>([]),
  attachmentIds = ref<string[]>([]),
  fileLoading = ref(false),
  fileError = ref('');
let fileRequest = 0;
async function openFiles(row: CollectionVo) {
  const attempt = ++fileRequest,
    version = generation,
    key = identity.value;
  filesVisible.value = true;
  fileCollection.value = row;
  imageIds.value = [];
  attachmentIds.value = [];
  fileError.value = '';
  fileLoading.value = true;
  try {
    const fresh: any = await collectionApi.get(String(row.id));
    const result = await Promise.all([collectionApi.media(String(row.id)), collectionApi.attachments(String(row.id))]);
    if (!current(version, key) || attempt !== fileRequest || !filesVisible.value) return;
    imageIds.value = [
      ...new Set(
        [fresh.data?.coverImage, ...((result[0] as any).data || []).map((x: any) => x.fileId)].filter(Boolean),
      ),
    ] as string[];
    attachmentIds.value = ((result[1] as any).data || []).map((x: any) => x.fileId).filter(Boolean);
  } catch (error: any) {
    if (current(version, key) && attempt === fileRequest) fileError.value = error?.message || '影像和附件读取失败';
  } finally {
    if (attempt === fileRequest) fileLoading.value = false;
  }
}
function closeDetails() {
  ++fileRequest;
  filesVisible.value = false;
  imageIds.value = [];
  attachmentIds.value = [];
  fileCollection.value = null;
  printVisible.value = false;
  printIds.value = [];
  inventoryVisible.value = false;
  inventoryIds.value = [];
}
watch(
  filesVisible,
  (show) => {
    if (!show) {
      ++fileRequest;
      imageIds.value = [];
      attachmentIds.value = [];
    }
  },
  { flush: 'sync' },
);
watch(
  identity,
  () => {
    ++request;
    selected.clear();
    saved.value = [];
    ready.value = false;
    closeDetails();
    if (active) void bootstrap();
  },
  { flush: 'sync' },
);
watch([mode, viewMode], () => {
  closeDetails();
  void syncRoute();
});
onMounted(() => {
  document.title = '藏品检索 — 藏品管理系统';
  void bootstrap();
});
onDeactivated(() => {
  rememberScroll();
  restoringScroll = true;
  active = false;
  ++generation;
  ++request;
  closeDetails();
});
onActivated(() => {
  if (!active) {
    active = true;
    Object.keys(draft).forEach((key) => delete draft[key]);
    Object.assign(draft, readSearchCriteria(route.query));
    for (const key of ['hasImage', 'hasAttachment'])
      if (typeof draft[key] === 'boolean') draft[key] = String(draft[key]);
    void bootstrap(true);
  }
});
onBeforeUnmount(() => {
  disposed = true;
  active = false;
  ++generation;
  ++request;
  closeDetails();
});
</script>
<style scoped>
.entity-search {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  min-height: 0;
  padding: 16px;
  overflow: auto;
}
.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.page-heading h1 {
  font-size: 21px;
  line-height: 1.4;
  margin: 0;
}
.criteria-card {
  flex: none;
}
.keyword-row {
  display: flex;
  gap: 10px;
}
.keyword-row > .n-input {
  flex: 1;
}
.common-filters,
.advanced-filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}
label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: #697386;
  min-width: 0;
  white-space: nowrap;
}
.criteria-actions {
  display: flex;
  gap: 14px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 12px;
}
.saved-select {
  width: 200px;
}
.advanced-filters {
  border-top: 1px solid #e8ebef;
  padding-top: 14px;
}
.number-filter {
  display: flex;
  gap: 6px;
}
.number-filter > .n-select {
  width: 105px;
  flex: none;
}
.date-filter {
  grid-column: span 2;
}
.metadata-error {
  font-size: 12px;
  color: #986420;
  margin-bottom: 0;
}
.result-controls {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}
.result-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 13px;
}
.result-summary strong {
  font-size: 22px;
}
.result-scope {
  padding-inline-start: 12px;
  border-inline-start: 1px solid var(--n-border-color, #e8ebef);
  color: var(--n-text-color-3, #7a8391);
}
.sort-select {
  width: 150px;
}
.results-table {
  flex: 1;
  min-height: 340px;
}
.selection-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 14px;
  border: 1px solid #bdd8ef;
  background: rgba(47, 125, 180, 0.06);
  border-radius: 6px;
  font-size: 13px;
}
.gallery-content {
  flex: 1;
  min-height: 220px;
  overflow: auto;
}
.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(195px, 1fr));
  gap: 14px;
}
.gallery-card {
  border: 1px solid #e8ebef;
  border-radius: 8px;
  overflow: hidden;
  min-width: 0;
  padding: 12px;
}
.gallery-image {
  display: flex;
  justify-content: center;
  min-height: 132px;
  background: rgba(125, 138, 158, 0.05);
}
.gallery-image :deep(.n-image) {
  width: 100%;
  justify-content: center;
  align-items: center;
}
.gallery-image :deep(img) {
  width: 180px;
  max-width: 100%;
  height: 132px;
  object-fit: contain !important;
}
.gallery-title {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 12px;
  min-width: 0;
}
.gallery-title > .n-button {
  flex: 1;
  min-width: 0;
  justify-content: start;
}
.gallery-card p {
  font-size: 12px;
  color: #697386;
  margin: 8px 0;
}
.gallery-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.gallery-pagination {
  justify-content: flex-end;
  margin-top: 14px;
  flex-wrap: wrap;
}
.initial-loading {
  padding: 60px;
}
.inventory-modal {
  width: min(560px, calc(100vw - 32px));
}
h3 {
  font-size: 15px;
  margin: 20px 0 12px;
}
@media (max-width: 1050px) {
  .common-filters,
  .advanced-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .entity-search {
    padding: 10px;
  }
  .page-heading,
  .result-controls {
    align-items: flex-start;
    flex-direction: column;
  }
  .keyword-row {
    flex-wrap: wrap;
  }
  .keyword-row > .n-input {
    flex-basis: 100%;
  }
  .common-filters,
  .advanced-filters {
    gap: 10px;
  }
  .results-table {
    min-height: 480px;
  }
  .gallery-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  }
  .saved-select {
    width: 170px;
  }
  .date-filter {
    grid-column: span 2;
  }
}
</style>

<style scoped>
.results-table :deep(.page-table-toolbar) {
  position: static;
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.active-criteria {
  margin-top: 12px;
}
</style>

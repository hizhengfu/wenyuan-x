<template>
  <main class="collection-search-page" :style="searchStyle">
    <section class="search-hero" aria-labelledby="collection-search-title">
      <div class="search-heading">
        <h2 id="collection-search-title">业务记录与档案</h2>
        <CollectionFieldLabel label="检索范围" help="仅检索当前账号有权查看的馆藏及相关记录，分类内按记录时间倒序。文件仅检索名称等元信息，不包含附件正文。" />
      </div>

      <div class="search-box" role="search">
        <NInput
          ref="searchInputRef"
          v-model:value="draftKeyword"
          size="large"
          placeholder="输入名称、登记号、年代、人员、单号、事由或内容关键字"
          aria-label="搜索藏品及相关内容"
          @compositionstart="composing = true"
          @compositionend="handleCompositionEnd"
          @keydown.enter="handleEnter"
        >
          <template #prefix><IconMdiSearch aria-hidden="true" /></template>
          <template #suffix>
            <NButton
              v-if="draftKeyword"
              quaternary
              circle
              size="small"
              aria-label="清空搜索关键字"
              @click="clearSearch"
            >
              <IconMdiClose aria-hidden="true" />
            </NButton>
          </template>
        </NInput>
        <NButton type="primary" size="large" :loading="loading" :disabled="!draftKeyword.trim()" @click="commitSearch">
          搜索
        </NButton>
      </div>

      <NSelect
        v-model:value="recordType"
        :options="recordTypeOptions"
        clearable
        placeholder="全部记录类型"
        style="max-width: 240px"
        @update:value="changeRecordType"
      />
      <div class="search-examples" aria-label="搜索示例">
        <span>可尝试：</span>
        <button v-for="example in examples" :key="example" type="button" @click="useExample(example)">
          {{ example }}
        </button>
      </div>
    </section>

    <section v-if="committedKeyword" class="search-workbench" aria-live="polite" :aria-busy="loading">
      <aside class="search-index" aria-label="检索结果分类">
        <div class="index-summary">
          <span>检索结果</span>
          <strong>{{ currentResultTotal }}</strong>
          <small>条可见内容</small>
        </div>
        <nav class="group-nav">
          <button
            type="button"
            :class="{ active: activeGroup === null }"
            :aria-current="activeGroup === null ? 'page' : undefined"
            @click="selectGroup(null)"
          >
            <span>全部内容</span>
            <strong>{{ allResultTotal }}</strong>
          </button>
          <button
            v-for="group in groupNavigation"
            :key="group.key"
            type="button"
            :class="{ active: activeGroup === group.key }"
            :aria-current="activeGroup === group.key ? 'page' : undefined"
            @click="selectGroup(group.key)"
          >
            <span>{{ group.label }}</span>
            <strong>{{ group.total }}</strong>
          </button>
        </nav>
      </aside>

      <div class="search-results">
        <div class="result-heading">
          <div>
            <span>{{ activeGroupLabel }}</span>
            <h2>“<HighlightText :text="committedKeyword" :keyword="committedKeyword" />”的检索结果</h2>
          </div>
          <NButton v-if="activeGroup" text type="primary" @click="selectGroup(null)">查看全部分类</NButton>
        </div>

        <div v-if="loading" class="state-panel loading-state" role="status">
          <NSpin size="large" />
          <span>正在查找藏品及其关联记录…</span>
        </div>

        <NResult
          v-else-if="forbidden"
          status="403"
          title="没有统一检索权限"
          description="请联系管理员分配“藏品查看”权限后再访问此页面。"
        >
          <template #footer><NButton @click="router.push('/collection')">返回藏品首页</NButton></template>
        </NResult>

        <NResult v-else-if="errorMessage" status="500" title="检索暂时不可用" :description="errorMessage">
          <template #footer><NButton type="primary" @click="loadResults">重新检索</NButton></template>
        </NResult>

        <NEmpty
          v-else-if="!visibleGroups.length || !(result?.total ?? 0)"
          class="state-panel"
          description="没有找到匹配内容，请减少关键字或换一种表述"
        >
          <template #extra><NButton @click="clearSearch">清空后重新搜索</NButton></template>
        </NEmpty>

        <div v-else class="result-groups">
          <section
            v-for="group in visibleGroups"
            :key="group.key"
            class="result-group"
            :aria-labelledby="`search-group-${group.key}`"
          >
            <header class="group-heading">
              <div>
                <span class="group-marker" :class="`marker-${group.key.toLowerCase()}`" aria-hidden="true" />
                <div>
                  <h3 :id="`search-group-${group.key}`">{{ group.label }}</h3>
                  <p>{{ group.description }}</p>
                </div>
              </div>
              <NButton
                v-if="!activeGroup && group.total > group.items.length"
                text
                type="primary"
                @click="selectGroup(group.key)"
              >
                查看全部 {{ group.total }} 条
              </NButton>
            </header>

            <div class="result-list">
              <article
                v-for="item in group.items"
                :key="`${item.type}:${item.id}`"
                class="result-card"
                :class="`result-${group.key.toLowerCase()}`"
              >
                <div class="result-card-main">
                  <div class="result-type-line">
                    <NTag size="small" :bordered="false">{{ item.typeLabel }}</NTag>
                    <span v-if="item.occurredAt">{{ formatTime(item.occurredAt) }}</span>
                  </div>
                  <h4><HighlightText :text="displayTitle(item)" :keyword="committedKeyword" /></h4>
                  <p v-if="item.subtitle" class="result-subtitle">
                    <HighlightText :text="displaySubtitle(item)" :keyword="committedKeyword" />
                  </p>
                  <HFileList v-if="item.fileId" :value="[item.fileId]" :image-width="80" :image-height="80" />
                  <p v-if="item.summary" class="result-summary">
                    <HighlightText :text="plainSummary(item.summary, item.type)" :keyword="committedKeyword" />
                  </p>

                  <div v-if="item.matchedFields.length" class="matched-fields" aria-label="命中字段">
                    <span>命中</span>
                    <NTag v-for="field in item.matchedFields" :key="field" size="tiny" type="success" :bordered="false">
                      {{ field }}
                    </NTag>
                  </div>
                </div>

                <div v-if="item.relatedCollections.length && item.type !== 'COLLECTION'" class="related-collections">
                  <span class="related-label">关联藏品</span>
                  <button
                    v-for="collection in item.relatedCollections"
                    :key="collection.id"
                    type="button"
                    class="collection-chip"
                    @click="openCollection(collection)"
                  >
                    <NAvatar
                      v-if="collection.coverImage"
                      :src="fileUrl(collection.coverImage)"
                      :size="34"
                      object-fit="cover"
                      aria-hidden="true"
                    />
                    <span v-else class="collection-monogram" aria-hidden="true">藏</span>
                    <span>
                      <strong><HighlightText :text="collection.name" :keyword="committedKeyword" /></strong>
                      <small><HighlightText :text="collection.totalRegNo" :keyword="committedKeyword" /></small>
                    </span>
                  </button>
                  <small v-if="item.relatedCollectionCount > item.relatedCollections.length" class="related-more">
                    另有 {{ item.relatedCollectionCount - item.relatedCollections.length }} 件
                  </small>
                </div>

                <NButton secondary type="primary" class="open-result" @click="openResult(item)">
                  前往{{ item.typeLabel }}
                  <template #icon><IconMdiArrowRight aria-hidden="true" /></template>
                </NButton>
              </article>
            </div>
          </section>
        </div>

        <NPagination
          v-if="activeGroup && activeGroupResult && activeGroupResult.total > pageSize"
          class="result-pagination"
          :page="pageNum"
          :page-size="pageSize"
          :item-count="activeGroupResult.total"
          show-quick-jumper
          @update:page="changePage"
        />
      </div>
    </section>

    <section v-else class="search-start" aria-label="统一检索范围">
      <ul>
        <li v-for="group in defaultGroups" :key="group.key">
          <span :class="`marker-${group.key.toLowerCase()}`" aria-hidden="true" />
          <strong>{{ group.label }}</strong>
          <small>{{ group.description }}</small>
        </li>
      </ul>
    </section>
    <CollectionSearchFileDrawer ref="fileDrawerRef" />
  </main>
</template>

<script setup lang="ts">
import CollectionFieldLabel from './CollectionFieldLabel.vue';
import {
  computed,
  defineComponent,
  h,
  nextTick,
  onActivated,
  onDeactivated,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
  type CSSProperties,
  type VNodeChild,
} from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/store';
import { frontType } from '@/modules/sys/api/dict';
import CollectionSearchFileDrawer from './CollectionSearchFileDrawer.vue';
import { inventoryPurposes } from '../utils/inventoryEvidenceFields';
import { useThemeVars, type InputInst } from 'naive-ui';
import { fileUrl } from '@/utils';
import { collectionApi } from '../api/collection';
import {
  collectionSearchApi,
  type CollectionSearchCollectionRef,
  type CollectionSearchGroup,
  type CollectionSearchGroupKey,
  type CollectionSearchItem,
  type CollectionUnifiedSearchResult,
} from '../api/search';
import { splitHighlightedText } from '../utils/searchHighlight';

const defaultGroups: Array<Pick<CollectionSearchGroup, 'key' | 'label' | 'description'>> = [
  { key: 'ARCHIVE', label: '藏品档案', description: '总账、编目、影像、附件、套件与登记留痕' },
  { key: 'ACQUISITION', label: '征集登记', description: '征集计划、申请、入馆凭证与鉴定定级' },
  { key: 'CIRCULATION', label: '流转利用', description: '提用、借展、归还、展览、复制、调拨与退出馆藏' },
  { key: 'STORAGE', label: '库房管理', description: '出入库、盘点、库位、日志与入库申请' },
  { key: 'FOUNDATION', label: '基础资料', description: '收藏单位、文物分类与打印模板' },
];

const HighlightText = defineComponent({
  name: 'CollectionSearchHighlightText',
  props: {
    text: { type: String, default: '' },
    keyword: { type: String, default: '' },
  },
  setup(props) {
    return () => renderHighlighted(props.text, props.keyword);
  },
});

const auth = useAuthStore();
const identity = computed(() => `${auth.userInfo?.id}:${auth.identityRevision}:${auth.identitySyncing}`);
const labels = ref<Record<string, Record<string, string>>>({});
const fileDrawerRef = ref<any>();
let active = true;
const route = useRoute();
const router = useRouter();
const themeVars = useThemeVars();
const searchInputRef = ref<InputInst | null>(null);
const draftKeyword = ref(typeof route.query.recordKeyword === 'string' ? route.query.recordKeyword : typeof route.query.keyword === 'string' ? route.query.keyword : '');
const committedKeyword = ref(draftKeyword.value.trim());
const recordType = ref(typeof route.query.recordTypeFilter === 'string' ? route.query.recordTypeFilter : null);
const recordTypeOptions = [
  { value: 'COLLECTION', label: '藏品档案' },
  { value: 'CATALOG', label: '编目记录' },
  { value: 'MEDIA', label: '影像档案' },
  { value: 'ATTACHMENT', label: '档案附件' },
  { value: 'LEDGER_LOG', label: '登记留痕' },
  { value: 'ACQUISITION_PLAN', label: '征集计划' },
  { value: 'ACQUISITION', label: '征集记录' },
  { value: 'ENTRY_VOUCHER', label: '入馆凭证' },
  { value: 'APPRAISAL', label: '鉴定定级' },
  { value: 'REQUISITION', label: '提用申请' },
  { value: 'LOAN', label: '借展申请' },
  { value: 'RETURN', label: '归还记录' },
  { value: 'MOVEMENT', label: '出入库记录' },
  { value: 'EXHIBITION', label: '展览记录' },
  { value: 'REPRODUCTION', label: '复制拓印' },
  { value: 'TRANSFER', label: '调拨记录' },
  { value: 'DEREGISTRATION', label: '退出馆藏' },
  { value: 'INVENTORY', label: '清点记录' },
  { value: 'INVENTORY_BATCH', label: '清点批次' },
  { value: 'WAREHOUSE_ENTRY', label: '人员入库申请' },
  { value: 'ANNUAL_REPORT', label: '年度报表' },
  { value: 'ANNUAL_ARCHIVE', label: '历史年报归档' },
  { value: 'MEDIA_FOLDER', label: '影像目录' },
  { value: 'COLLECTION_SET', label: '套件关系' },
  { value: 'APPRAISAL_MEMBER', label: '鉴定成员意见' },
  { value: 'WAREHOUSE', label: '库位资料' },
  { value: 'WAREHOUSE_LOG', label: '保管日记' },
  { value: 'ORGANIZATION', label: '收藏单位资料' },
  { value: 'CATEGORY', label: '藏品分类资料' },
  { value: 'PRINT_TEMPLATE', label: '打印模板' },
];
const activeGroup = ref<CollectionSearchGroupKey | null>(parseGroup(route.query.recordGroup));
const result = ref<CollectionUnifiedSearchResult | null>(null);
const loading = ref(false);
const forbidden = ref(false);
const errorMessage = ref('');
const composing = ref(false);
const pageNum = ref(Math.max(1, Number(route.query.recordPage) || 1));
const pageSize = 20;
const groupTotals = ref<Record<CollectionSearchGroupKey, number>>(emptyGroupTotals());
let debounceTimer: ReturnType<typeof setTimeout> | null = null;
let requestVersion = 0;

const examples = ['青铜鼎', '总登记号', '借展协议', '修复', '盘点异常'];
const searchStyle = computed(() => ({ '--search-primary': themeVars.value.primaryColor }) as CSSProperties);

const groupNavigation = computed(() => {
  return defaultGroups.map((group) => ({ ...group, total: groupTotals.value[group.key] }));
});
const activeGroupResult = computed(() => result.value?.groups.find((group) => group.key === activeGroup.value));
const allResultTotal = computed(() => Object.values(groupTotals.value).reduce((total, count) => total + count, 0));
const currentResultTotal = computed(() =>
  activeGroup.value ? (activeGroupResult.value?.total ?? 0) : allResultTotal.value,
);
const visibleGroups = computed(() => (result.value?.groups ?? []).filter((group) => group.total > 0));
const activeGroupLabel = computed(() =>
  activeGroup.value
    ? (defaultGroups.find((group) => group.key === activeGroup.value)?.label ?? '分类结果')
    : '全部内容',
);

watch(draftKeyword, (value) => {
  if (composing.value) return;
  if (debounceTimer) clearTimeout(debounceTimer);
  if (!value.trim()) {
    clearCommittedState();
    return;
  }
  debounceTimer = setTimeout(() => commitSearch(), 350);
});

onMounted(() => {
  document.title = '藏品检索 — 藏品管理系统';
  if (committedKeyword.value) {
    void loadResults();
    return;
  }
  const legacyCollectionId = typeof route.query.id === 'string' ? route.query.id.trim() : '';
  if (legacyCollectionId) void resolveLegacyCollectionLink(legacyCollectionId);
});

onDeactivated(() => {
  active = false;
  fileDrawerRef.value?.close();
  requestVersion += 1;
  result.value = null;
  loading.value = false;
  if (debounceTimer) clearTimeout(debounceTimer);
});
onActivated(() => {
  if (!active) {
    active = true;
    if (committedKeyword.value) void loadResults();
  }
});

onBeforeUnmount(() => {
  requestVersion += 1;
  if (debounceTimer) clearTimeout(debounceTimer);
});

function parseGroup(value: unknown): CollectionSearchGroupKey | null {
  const group = typeof value === 'string' ? value.toUpperCase() : '';
  return defaultGroups.some((item) => item.key === group) ? (group as CollectionSearchGroupKey) : null;
}

function handleCompositionEnd() {
  composing.value = false;
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => commitSearch(), 350);
}

function handleEnter(event: KeyboardEvent) {
  if (event.isComposing || composing.value) return;
  commitSearch();
}

function commitSearch() {
  if (composing.value) return;
  const keyword = draftKeyword.value.trim();
  if (!keyword) {
    clearCommittedState();
    return;
  }
  if (debounceTimer) clearTimeout(debounceTimer);
  const changed = keyword !== committedKeyword.value;
  committedKeyword.value = keyword;
  if (changed) {
    activeGroup.value = null;
    pageNum.value = 1;
  }
  void loadResults();
}

function clearSearch() {
  requestVersion += 1;
  if (debounceTimer) clearTimeout(debounceTimer);
  draftKeyword.value = '';
  clearCommittedState();
  void nextTick(() => searchInputRef.value?.focus());
}

function clearCommittedState() {
  committedKeyword.value = '';
  activeGroup.value = null;
  pageNum.value = 1;
  result.value = null;
  groupTotals.value = emptyGroupTotals();
  errorMessage.value = '';
  forbidden.value = false;
  void syncRoute();
}

function useExample(keyword: string) {
  draftKeyword.value = keyword;
  commitSearch();
}

function changeRecordType() {
  activeGroup.value = null;
  groupTotals.value = emptyGroupTotals();
  pageNum.value = 1;
  void loadResults();
}

async function selectGroup(group: CollectionSearchGroupKey | null) {
  if (activeGroup.value === group && result.value) return;
  activeGroup.value = group;
  pageNum.value = 1;
  await loadResults();
}

async function changePage(page: number) {
  pageNum.value = page;
  await loadResults();
  document.querySelector('.result-heading')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function loadResults() {
  const keyword = committedKeyword.value.trim();
  if (!keyword || !active || auth.identitySyncing) return;
  const key = identity.value;
  const version = ++requestVersion;
  loading.value = true;
  forbidden.value = false;
  errorMessage.value = '';
  await syncRoute();
  try {
    const response: any = await collectionSearchApi.search({
      keyword,
      type: recordType.value || undefined,
      group: activeGroup.value ?? undefined,
      pageNum: pageNum.value,
      pageSize,
    });
    if (version !== requestVersion || !active || key !== identity.value) return;
    result.value = normalizeResult(response?.data, keyword);
    rememberGroupTotals(result.value.groups, activeGroup.value === null);
  } catch (error: any) {
    if (version !== requestVersion) return;
    const status = error?.response?.status ?? error?.status;
    forbidden.value = status === 403;
    errorMessage.value = forbidden.value ? '' : '未能完成本次检索，请检查网络或稍后重试。';
    result.value = null;
  } finally {
    if (version === requestVersion) loading.value = false;
  }
}

/** Preserve existing /collection/search?id=... links without reopening the former detail drawer. */
async function resolveLegacyCollectionLink(collectionId: string) {
  const version = ++requestVersion;
  loading.value = true;
  forbidden.value = false;
  errorMessage.value = '';
  try {
    const response: any = await collectionApi.get(collectionId);
    if (version !== requestVersion) return;
    const collection = response?.data;
    const keyword = String(collection?.totalRegNo || collection?.name || '').trim();
    if (!keyword) throw new Error('missing collection identity');
    composing.value = true;
    draftKeyword.value = keyword;
    committedKeyword.value = keyword;
    await nextTick();
    composing.value = false;
    activeGroup.value = null;
    pageNum.value = 1;
    await loadResults();
  } catch (error: any) {
    if (version !== requestVersion) return;
    const status = error?.response?.status ?? error?.status;
    composing.value = true;
    draftKeyword.value = collectionId;
    committedKeyword.value = collectionId;
    await nextTick();
    composing.value = false;
    forbidden.value = status === 403;
    errorMessage.value = forbidden.value ? '' : '无法读取链接中的藏品，请返回藏品台账重新查找。';
  } finally {
    if (version === requestVersion) loading.value = false;
  }
}

function normalizeResult(data: any, keyword: string): CollectionUnifiedSearchResult {
  const groups = Array.isArray(data?.groups) ? data.groups : [];
  return {
    keyword: data?.keyword ?? keyword,
    total: Number(data?.total ?? 0),
    groups: groups.map((group: any) => ({
      ...group,
      total: Number(group?.total ?? 0),
      items: Array.isArray(group?.items)
        ? group.items.map((item: any) => ({
            ...item,
            matchedFields: Array.isArray(item?.matchedFields) ? item.matchedFields : [],
            relatedCollections: Array.isArray(item?.relatedCollections) ? item.relatedCollections : [],
            relatedCollectionCount: Number(item?.relatedCollectionCount ?? item?.relatedCollections?.length ?? 0),
          }))
        : [],
    })),
  };
}

function emptyGroupTotals(): Record<CollectionSearchGroupKey, number> {
  return { ARCHIVE: 0, ACQUISITION: 0, CIRCULATION: 0, STORAGE: 0, FOUNDATION: 0 };
}

function rememberGroupTotals(groups: CollectionSearchGroup[], reset: boolean) {
  const totals = reset ? emptyGroupTotals() : { ...groupTotals.value };
  groups.forEach((group) => {
    totals[group.key] = group.total;
  });
  groupTotals.value = totals;
}

async function syncRoute() {
  const query = { ...route.query };
  query.mode = 'records';
  delete query.id;
  if (committedKeyword.value) query.recordKeyword = committedKeyword.value;
  else delete query.recordKeyword;
  if (activeGroup.value) query.recordGroup = activeGroup.value;
  else delete query.recordGroup;
  query.recordPage = String(pageNum.value);
  if (recordType.value) query.recordTypeFilter = recordType.value;
  else delete query.recordTypeFilter;
  await router.replace({ query });
}

function openCollection(collection: CollectionSearchCollectionRef) {
  void router.push({
    path: '/collection/ledger',
    query: { collectionId: collection.id, keyword: collection.totalRegNo, searchOrigin: 'collection-search' },
  });
}

function openResult(item: CollectionSearchItem) {
  if (['MEDIA', 'ATTACHMENT', 'MEDIA_FOLDER'].includes(item.type)) {
    void fileDrawerRef.value?.open(item);
    return;
  }
  if (item.type === 'COLLECTION_SET') {
    const collection = item.relatedCollections[0];
    if (collection) openCollection(collection);
    return;
  }
  const related = item.relatedCollections[0];
  void router.push({
    path: item.route,
    query: {
      keyword: related?.totalRegNo || committedKeyword.value,
      searchOrigin: 'collection-search',
      recordId: item.type === 'CATALOG' ? related?.id : item.targetId || item.id,
      recordType: item.type === 'APPRAISAL_MEMBER' ? 'APPRAISAL' : item.type,
      ...(item.type === 'COLLECTION' ? { collectionId: related?.id || item.id } : {}),
      ...(item.type === 'MOVEMENT' && related ? { collectionId: related.id } : {}),
      ...(item.targetYear ? { year: String(item.targetYear) } : {}),
    },
  });
}

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date);
}

function renderHighlighted(text: string, keyword: string): VNodeChild {
  return h(
    'span',
    splitHighlightedText(text, keyword).map((segment, index) =>
      segment.highlighted
        ? h('mark', { class: 'search-highlight', key: `${segment.text}-${index}` }, segment.text)
        : segment.text,
    ),
  );
}

const recordDicts = [
  'col_ledger_action',
  'col_life_status',
  'col_catalog_status',
  'col_media_type',
  'col_attachment_type',
  'col_set_relation',
  'col_plan_period',
  'col_voucher_status',
  'col_move_reason',
  'col_inventory_result',
  'col_rep_type',
];
const fieldLabels: Record<string, string> = {
  lifeStatus: '保管状态',
  coverImage: '封面影像',
  catalogStatus: '编目状态',
  registeredAt: '登记时间',
  totalRegNo: '总登记号',
  warehouseId: '库位',
  name: '藏品名称',
  nature: '藏品性质',
  categoryId: '藏品分类',
  orgId: '收藏单位',
  collectionSet: '套件关系',
  setRelation: '套件关系',
};
function replaceCodes(value: string, type: string) {
  return value.replace(/\b[A-Z][A-Z_]+\b/g, (code) => labels.value[type]?.[code] || '待核实');
}
function displayTitle(item: CollectionSearchItem) {
  if (item.type === 'LEDGER_LOG') return replaceCodes(item.title, 'col_ledger_action');
  if (item.type === 'MEDIA') return replaceCodes(item.title, 'col_media_type');
  if (item.type === 'REPRODUCTION') return item.title.replace(/^([A-Z_]+)复制记录/, (_, code) => `${labels.value.col_rep_type?.[code] || '待核实'}记录`);
  return item.title;
}
function displaySubtitle(item: CollectionSearchItem) {
  const text = item.subtitle || '';
  if (item.type === 'LEDGER_LOG') return fieldLabels[text] || '登记信息变更';
  if (item.type === 'INVENTORY_BATCH') return text.replace(/^[A-Z_]+\b/, (code) => inventoryPurposes.find((option) => option.value === code)?.label || '待核实');
  const dicts: Record<string, string> = {
    CATALOG: 'col_catalog_status',
    MEDIA: 'col_media_type',
    ATTACHMENT: 'col_attachment_type',
    COLLECTION_SET: 'col_set_relation',
    ACQUISITION_PLAN: 'col_plan_period',
    ENTRY_VOUCHER: 'col_voucher_status',
    MOVEMENT: 'col_move_reason',
    INVENTORY: 'col_inventory_result',
  };
  return dicts[item.type] ? replaceCodes(text, dicts[item.type]) : text;
}
function plainSummary(text?: string, type?: string) {
  const summary = type === 'INVENTORY_BATCH'
    ? String(text || '').replace(/^[A-Z_]+\b/, (code) => inventoryPurposes.find((option) => option.value === code)?.label || '待核实')
    : String(text || '');
  return summary
    .replace(/\bCOLLECTION_([A-Z_]+)\b/g, (_, type) => recordTypeOptions.find((option) => option.value === type)?.label || '相关业务')
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .trim();
}
onMounted(async () => {
  const key = identity.value;
  const results = await Promise.allSettled(recordDicts.map((type) => frontType(type, true)));
  if (key !== identity.value || !active) return;
  const next: Record<string, Record<string, string>> = {};
  results.forEach((entry, i) => {
    if (entry.status === 'fulfilled' && Array.isArray((entry.value as any)?.data))
      next[recordDicts[i]] = Object.fromEntries(
        (entry.value as any).data.map((row: any) => [String(row.value), row.label]),
      );
  });
  labels.value = next;
});
watch(
  identity,
  () => {
    requestVersion++;
    result.value = null;
    groupTotals.value = emptyGroupTotals();
    loading.value = false;
    labels.value = {};
    fileDrawerRef.value?.close();
    if (active && !auth.identitySyncing && committedKeyword.value) void loadResults();
  },
  { flush: 'sync' },
);
</script>

<style scoped>
.collection-search-page {
  --search-primary: #7c4dff;
  --search-primary-soft: color-mix(in srgb, var(--search-primary) 9%, transparent);
  --search-ink: var(--text-color-1, #17202a);
  --search-muted: var(--text-color-3, #667085);
  --search-border: var(--border-color, #e5e9ef);
  min-height: 100%;
  padding: 20px;
  color: var(--search-ink);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--search-primary) 4%, transparent), transparent 220px),
    var(--body-color, #f5f7f9);
}

.search-hero,
.search-start,
.search-index,
.search-results {
  border: 1px solid var(--search-border);
  background: var(--card-color, #fff);
}

.search-hero {
  position: relative;
  overflow: hidden;
  padding: clamp(24px, 4vw, 46px);
  border-radius: 12px;
}

.search-hero::after {
  position: absolute;
  inset: 0 0 auto auto;
  width: min(32vw, 420px);
  height: 100%;
  background: repeating-linear-gradient(
    90deg,
    transparent 0,
    transparent 23px,
    color-mix(in srgb, var(--search-primary) 8%, transparent) 24px
  );
  content: '';
  pointer-events: none;
  mask-image: linear-gradient(90deg, transparent, #000);
}

.search-heading,
.search-box,
.search-examples {
  position: relative;
  z-index: 1;
  max-width: 920px;
}

.search-kicker {
  display: block;
  margin-bottom: 8px;
  color: var(--search-primary);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.search-heading h2 {
  margin: 0;
  font-family: 'Songti SC', 'Noto Serif CJK SC', 'STSong', serif;
  font-size: clamp(28px, 3.2vw, 42px);
  font-weight: 700;
  line-height: 1.25;
}


.search-box {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  margin-top: 28px;
}

.search-box :deep(.n-input) {
  min-height: 48px;
  box-shadow: 0 6px 20px color-mix(in srgb, var(--search-primary) 9%, transparent);
}

.search-examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 13px;
  color: var(--search-muted);
  font-size: 13px;
}

.search-examples button {
  padding: 3px 9px;
  border: 1px solid var(--search-border);
  border-radius: 999px;
  color: inherit;
  background: var(--card-color, #fff);
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    color 0.2s ease,
    background 0.2s ease;
}

.search-examples button:hover,
.search-examples button:focus-visible {
  border-color: var(--search-primary);
  color: var(--search-primary);
  background: var(--search-primary-soft);
  outline: none;
}

.search-workbench {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
  align-items: start;
}

.search-index {
  position: sticky;
  top: 16px;
  overflow: hidden;
  border-radius: 10px;
}

.index-summary {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: end;
  gap: 2px 8px;
  padding: 18px;
  border-bottom: 1px solid var(--search-border);
}

.index-summary span,
.index-summary small {
  color: var(--search-muted);
}

.index-summary strong {
  grid-row: span 2;
  color: var(--search-primary);
  font-family: 'DIN Alternate', 'Arial Narrow', sans-serif;
  font-size: 32px;
  line-height: 1;
}

.group-nav {
  display: grid;
  gap: 4px;
  padding: 8px;
}

.group-nav button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  padding: 0 11px;
  border: 0;
  border-radius: 7px;
  color: var(--search-muted);
  background: transparent;
  cursor: pointer;
}

.group-nav button:hover,
.group-nav button:focus-visible {
  color: var(--search-ink);
  background: var(--hover-color, #f5f7f9);
  outline: none;
}

.group-nav button.active {
  color: var(--search-primary);
  background: var(--search-primary-soft);
  box-shadow: inset 3px 0 var(--search-primary);
}

.group-nav strong {
  font-variant-numeric: tabular-nums;
}



.search-results {
  min-width: 0;
  padding: 22px;
  border-radius: 10px;
}

.result-heading,
.group-heading,
.group-heading > div,
.result-type-line,
.matched-fields {
  display: flex;
  align-items: center;
}

.result-heading,
.group-heading {
  justify-content: space-between;
  gap: 16px;
}

.result-heading {
  margin-bottom: 22px;
}

.result-heading span {
  color: var(--search-primary);
  font-size: 12px;
  font-weight: 700;
}

.result-heading h2 {
  margin: 4px 0 0;
  font-size: 21px;
}

.state-panel,
.loading-state {
  min-height: 360px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: var(--search-muted);
}

.result-groups {
  display: grid;
  gap: 26px;
}

.result-group + .result-group {
  padding-top: 26px;
  border-top: 1px solid var(--search-border);
}

.group-heading {
  margin-bottom: 12px;
}

.group-heading > div {
  align-items: flex-start;
  gap: 11px;
}

.group-heading h3,
.group-heading p {
  margin: 0;
}

.group-heading h3 {
  font-size: 17px;
}

.group-heading p {
  margin-top: 3px;
  color: var(--search-muted);
  font-size: 13px;
}

.group-marker,
.search-start li > span {
  display: block;
  width: 5px;
  height: 34px;
  border-radius: 999px;
}

.marker-archive {
  background: #0f766e;
}
.marker-acquisition {
  background: #a16207;
}
.marker-circulation {
  background: #1d4ed8;
}
.marker-storage {
  background: #7c3aed;
}
.marker-foundation {
  background: #475569;
}

.result-list {
  display: grid;
  gap: 10px;
}

.result-card {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(190px, 0.48fr) auto;
  gap: 18px;
  align-items: center;
  overflow: hidden;
  padding: 17px 16px 17px 20px;
  border: 1px solid var(--search-border);
  border-radius: 9px;
  background: var(--card-color, #fff);
  transition:
    border-color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.result-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  content: '';
  background: var(--result-accent, var(--search-primary));
}

.result-card:hover {
  border-color: color-mix(in srgb, var(--result-accent, var(--search-primary)) 42%, var(--search-border));
  box-shadow: 0 8px 24px rgb(15 23 42 / 7%);
  transform: translateY(-1px);
}

.result-archive {
  --result-accent: #0f766e;
}
.result-acquisition {
  --result-accent: #a16207;
}
.result-circulation {
  --result-accent: #1d4ed8;
}
.result-storage {
  --result-accent: #7c3aed;
}
.result-foundation {
  --result-accent: #475569;
}

.result-card-main {
  min-width: 0;
}

.result-type-line {
  gap: 8px;
  color: var(--search-muted);
  font-size: 12px;
}

.result-card h4 {
  margin: 8px 0 0;
  font-size: 17px;
  line-height: 1.45;
}

.result-subtitle,
.result-summary {
  margin: 5px 0 0;
  color: var(--search-muted);
  line-height: 1.65;
}

.result-subtitle {
  font-size: 13px;
}

.result-summary {
  display: -webkit-box;
  overflow: hidden;
  font-size: 14px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.matched-fields {
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
  color: var(--search-muted);
  font-size: 12px;
}

.related-collections {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.related-label,
.related-more {
  color: var(--search-muted);
  font-size: 12px;
}

.collection-chip {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 8px;
  width: 100%;
  min-height: 44px;
  padding: 5px;
  border: 1px solid transparent;
  border-radius: 7px;
  text-align: left;
  color: var(--search-ink);
  background: var(--hover-color, #f5f7f9);
  cursor: pointer;
}

.collection-chip:hover,
.collection-chip:focus-visible {
  border-color: var(--search-primary);
  outline: none;
}

.collection-chip > span:last-child {
  min-width: 0;
}

.collection-chip strong,
.collection-chip small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.collection-chip strong {
  font-size: 13px;
}
.collection-chip small {
  margin-top: 2px;
  color: var(--search-muted);
  font-size: 11px;
}

.collection-monogram {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 5px;
  color: var(--search-primary);
  background: var(--search-primary-soft);
  font-family: 'Songti SC', 'STSong', serif;
  font-weight: 700;
}

.open-result {
  justify-self: end;
  white-space: nowrap;
}

.result-pagination {
  justify-content: flex-end;
  margin-top: 24px;
}

:deep(.search-highlight) {
  padding: 0 0.08em;
  border-radius: 2px;
  color: inherit;
  background: color-mix(in srgb, #f5c542 48%, transparent);
}

.search-start {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  margin-top: 16px;
  padding: 26px;
  border-radius: 10px;
}




.search-start ul {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 18px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.search-start li {
  display: grid;
  grid-template-columns: 5px minmax(0, 1fr);
  column-gap: 9px;
  align-content: start;
}

.search-start li > span {
  grid-row: span 2;
}
.search-start li strong {
  font-size: 14px;
}
.search-start li small {
  margin-top: 4px;
  color: var(--search-muted);
  line-height: 1.5;
}

@media (max-width: 1080px) {
  .result-card {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .related-collections {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .related-label,
  .related-more {
    grid-column: 1 / -1;
  }

  .search-start ul {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .collection-search-page {
    padding: 10px;
  }

  .search-hero {
    padding: 22px 18px;
  }

  .search-box {
    grid-template-columns: 1fr;
  }

  .search-workbench {
    grid-template-columns: 1fr;
  }

  .search-index {
    position: static;
  }

  .group-nav {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .group-nav button {
    min-width: 0;
    flex-direction: column;
    justify-content: center;
    gap: 2px;
  }

  .group-nav button.active {
    box-shadow: inset 0 -3px var(--search-primary);
  }

  .search-results {
    padding: 16px;
  }

  .result-heading,
  .group-heading {
    align-items: flex-start;
  }

  .result-card {
    grid-template-columns: 1fr;
  }

  .open-result {
    justify-self: stretch;
  }

  .related-collections {
    grid-template-columns: 1fr;
  }

  .search-start {
    grid-template-columns: 1fr;
  }


  .search-start ul {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .result-card,
  .search-examples button {
    transition-duration: 0.01ms;
  }

  .result-card:hover {
    transform: none;
  }
}
</style>

<style scoped>
.collection-search-page {
  padding: 0;
  min-height: 0;
}
.search-hero {
  padding: 16px;
  gap: 14px;
}
.search-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  max-width: none;
}
.search-heading h2 {
  font-family: inherit;
  font-size: 16px;
}
.search-heading :deep(.collection-field-label) {
  color: var(--search-muted);
  font-size: 13px;
}
.search-box {
  margin-top: 16px;
  max-width: none;
}
.search-kicker,
.search-examples {
  display: none;
}
.search-workbench {
  margin-top: 14px;
}
</style>

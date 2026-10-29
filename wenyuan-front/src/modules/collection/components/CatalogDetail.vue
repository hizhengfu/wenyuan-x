<template>
  <div class="catalog-detail" :style="catalogTheme">
    <section class="record-hero">
      <div class="record-cover">
        <CurrentFileImage v-if="coverSrc" :src="coverSrc" :alt="displayName" :revision="coverRevision" :preview-disabled="true" />
        <div v-else class="cover-placeholder">
          <span>藏品影像</span>
          <small>尚未设置封面</small>
        </div>
      </div>

      <div class="record-identity">
        <div class="identity-heading">
          <div>
            <span class="record-number">{{ historical ? '当前藏品总登记号' : '总登记号' }} · {{ display(catalog.totalRegNo) }}</span>
            <h2>{{ displayName }}</h2>
          </div>
          <NTag :type="statusTone" round :bordered="false">{{ statusLabel }}</NTag>
        </div>

        <div class="identity-tags">
          <NTag v-if="catalog.categoryName || content.categoryId" size="small" :bordered="false">
            {{ catalog.categoryName || content.categoryId }}
          </NTag>
          <NTag v-if="eraSummary" size="small" type="info" :bordered="false">{{ eraSummary }}</NTag>
          <NTag v-if="content.texture" size="small" type="warning" :bordered="false">
            <CollectionCatalogDictText type="col_texture" :value="content.texture" />
          </NTag>
          <NTag v-if="content.level" size="small" type="success" :bordered="false">
            <CollectionCatalogDictText type="col_relic_level" :value="content.level" />
          </NTag>
        </div>

        <p class="record-feature">{{ content.feature || (historical ? '本版本未填写基本特征。' : '暂未填写基本特征，可在编目录入中继续完善。') }}</p>

        <div class="identity-metrics">
          <div class="metric-item">
            <span>{{ historical ? '所选历史版本' : '当前版本' }}</span>
            <strong>V{{ currentVersion }}</strong>
          </div>
          <div class="metric-item">
            <span>实际数量</span>
            <strong>{{ quantitySummary }}</strong>
          </div>
          <div class="metric-item">
            <span>登记库位</span>
            <strong>{{ display(catalog.warehouseName || content.warehouseId) }}</strong>
          </div>
          <div v-if="!historical" class="metric-item">
            <span>资料数量</span>
            <strong>{{ (catalog.mediaCount || 0) + (catalog.attachmentCount || 0) }} 项</strong>
          </div>
        </div>
      </div>

      <aside v-if="!historical" class="completeness-card">
        <NProgress
          type="circle"
          :percentage="catalog.completeness?.percent || 0"
          :status="catalog.completeness?.complete ? 'success' : 'warning'"
          :stroke-width="8"
          :width="88"
        />
        <strong>{{ catalog.completeness?.complete ? '信息完整' : '待补充' }}</strong>
        <span>
          {{
            catalog.completeness?.complete
              ? '具备提交条件'
              : '缺少 ' + (catalog.completeness?.missingFields?.length || 0) + ' 项'
          }}
        </span>
      </aside>
    </section>

    <NAlert
      v-if="catalog.auditOpinion"
      :type="catalog.status === 'REJECTED' ? 'error' : 'info'"
      :title="catalog.status === 'REJECTED' ? '本次审核已驳回' : '审核意见'"
      class="catalog-alert"
    >
      {{ catalog.auditOpinion }}
    </NAlert>

    <NAlert
      v-if="!historical && catalog.completeness && !catalog.completeness.complete"
      type="warning"
      title="提交前仍需补充"
      class="catalog-alert"
    >
      <div class="missing-fields">
        <NTag v-for="field in catalog.completeness.missingFields" :key="field" size="small" type="warning">
          {{ field }}
        </NTag>
      </div>
    </NAlert>

    <NSpace v-if="!historical && catalog.collectionId" size="small">
      <CollectionActionMenu label="检查与养护" v-permission="'COLLECTION_ADMIN,COLLECTION_CARE_MANAGE'">
        <NButton
          quaternary
          v-if="!historical && catalog.collectionId"
          v-permission="'COLLECTION_ADMIN,COLLECTION_CARE_MANAGE'"
          @click="careRef?.open(catalog.collectionId)"
          >日常检查与养护</NButton
        >
        <NButton
          quaternary
          v-if="!historical && catalog.collectionId"
          v-permission="'COLLECTION_ADMIN,COLLECTION_CARE_MANAGE'"
          @click="fragilityRef?.open(catalog.collectionId)"
          >材质脆弱性与使用保护</NButton
        >
      </CollectionActionMenu>
      <OriginalHistoryButton business-type="COLLECTION_MAIN_FILES" :business-id="catalog.collectionId">
        <template #trigger="{ open }">
          <CollectionActionMenu label="版本与材料">
            <NButton quaternary v-if="!historical && catalog.collectionId" size="small" @click="historyRef?.open(catalog)"
              >编目版本正文</NButton
            >
            <NButton quaternary size="small" @click="open">本藏品历史材料</NButton>
          </CollectionActionMenu>
        </template>
      </OriginalHistoryButton>
    </NSpace>
<CollectionCareDrawer v-if="!historical" ref="careRef" />
<CollectionFragilityDrawer v-if="!historical" ref="fragilityRef" />
<CatalogVersionHistoryDrawer v-if="!historical" ref="historyRef" />
    <NTabs v-model:value="activeTab" type="line" animated class="detail-tabs">
      <NTabPane name="basic" tab="基础著录">
        <div class="section-grid">
          <section v-for="section in sections.slice(0, 2)" :key="section.title" class="catalog-section">
            <SectionHeading :section="section" />
            <NDescriptions :column="2" label-placement="left" bordered size="small">
              <NDescriptionsItem v-for="item in section.items" :key="item.label" :label="item.label" :span="item.fullRow ? 2 : 1">
                <CollectionCatalogDictText v-if="item.dict" :type="item.dict" :value="item.value" />
                <span v-else class="detail-text">{{ display(item.value) }}</span>
              </NDescriptionsItem>
            </NDescriptions>
          </section>
        </div>
      </NTabPane>

      <NTabPane name="appraisal" tab="鉴定入藏">
        <div class="section-grid">
          <section v-for="section in sections.slice(2, 4)" :key="section.title" class="catalog-section">
            <SectionHeading :section="section" />
            <NDescriptions :column="2" label-placement="left" bordered size="small">
              <NDescriptionsItem v-for="item in section.items" :key="item.label" :label="item.label" :span="item.fullRow ? 2 : 1">
                <CollectionCatalogDictText v-if="item.dict" :type="item.dict" :value="item.value" />
                <span v-else class="detail-text">{{ display(item.value) }}</span>
              </NDescriptionsItem>
            </NDescriptions>
          </section>
        </div>
      </NTabPane>

      <NTabPane name="research" tab="描述研究">
        <section class="catalog-section">
          <SectionHeading :section="sections[4]" />
          <NDescriptions :column="2" label-placement="left" bordered size="small">
            <NDescriptionsItem v-for="item in sections[4].items" :key="item.label" :label="item.label" :span="item.fullRow ? 2 : 1">
              <CollectionCatalogDictText v-if="item.dict" :type="item.dict" :value="item.value" />
              <span v-else class="detail-text">{{ display(item.value) }}</span>
            </NDescriptionsItem>
          </NDescriptions>
        </section>
      </NTabPane>

      <NTabPane name="attachments" tab="附属资料">
        <section class="catalog-section">
          <div class="section-heading">
            <span class="section-index">04</span>
            <div>
              <h3>附属物与附件</h3>
              <p>集中查看实体附属物、影像和电子附件情况</p>
            </div>
          </div>
          <div class="attachment-summary">
            <div>
              <span>实体附属物</span><strong>{{ content.accessories?.length || 0 }}</strong>
            </div>
            <div v-if="!historical">
              <span>藏品影像</span><strong>{{ catalog.mediaCount || 0 }}</strong>
            </div>
            <div v-if="!historical">
              <span>电子附件</span><strong>{{ catalog.attachmentCount || 0 }}</strong>
            </div>
          </div>
          <NDataTable
            v-if="content.accessories?.length"
            :scroll-x="850"
            :columns="accessoryColumns"
            :data="content.accessories"
            size="small"
            :bordered="false"
          />
          <NEmpty v-else description="未登记实体附件或附属物" size="small" class="empty-block" />
          <div class="attachment-note">
            <span>附件说明</span>
            <p>{{ display(content.attachmentDescription) }}</p>
          </div>
        </section>
      </NTabPane>

      <NTabPane name="workflow" tab="流程留痕">
        <section class="catalog-section">
          <div class="section-heading">
            <span class="section-index">05</span>
            <div>
              <h3>编目流程</h3>
              <p>记录当前编目从录入、提交到审核决定的关键节点</p>
            </div>
          </div>
          <div class="workflow-track">
            <div
              v-for="(step, index) in workflowSteps"
              :key="step.title"
              class="workflow-step"
              :class="'is-' + stepState(index)"
            >
              <span class="step-dot">{{ index + 1 }}</span>
              <div>
                <strong>{{ step.title }}</strong>
                <p>{{ step.description }}</p>
              </div>
            </div>
          </div>
          <div class="flow-cards">
            <div class="flow-card">
              <span>提交记录</span>
              <strong>{{ display(catalog.submittedBy) }}</strong>
              <small>{{ formatDateTime(catalog.submittedAt) }}</small>
            </div>
            <div class="flow-card">
              <span>审核记录</span>
              <strong>{{ display(catalog.auditBy) }}</strong>
              <small>{{ formatDateTime(catalog.auditTime) }}</small>
            </div>
            <div class="flow-card">
              <span>当前状态</span>
              <strong>{{ statusLabel }}</strong>
              <small>{{ statusDescription }}</small>
            </div>
          </div>
        </section>
      </NTabPane>
    <NTabPane name="registration" tab="测量与流传著录"><RegistrationDetailsView :details="content.registrationDetails" /></NTabPane>
    </NTabs>
  </div>
</template>

<script setup lang="ts">
import CollectionActionMenu from './CollectionActionMenu.vue';
import { eraText, eraMeaningLabel, eraNotationLabel } from '../utils/collectionDescription';
import { standardDimensions, displayMass } from '../utils/registrationMeasurements';
import CollectionCatalogDictText from './CollectionCatalogDictText.vue';
import RegistrationDetailsView from './RegistrationDetailsView.vue';
import CurrentFileImage from '@/components/basic/CurrentFileImage.vue';
import { collectionNatureLabel } from '../utils/collectionNature';
import CollectionCareDrawer from './CollectionCareDrawer.vue';
import OriginalHistoryButton from './OriginalHistoryButton.vue';
import { computed, defineAsyncComponent, defineComponent, h, ref, watch, type CSSProperties, type PropType } from 'vue';
import { useThemeVars } from 'naive-ui';
import { fileUrl, formatDateTime, formatLocalDateValue } from '@/utils';
import type { CatalogStatus, CollectionCatalogVo } from '../typings/collection';
import { formatYuan } from '../utils/moneyYuan';

type DetailItem = { label: string; value: unknown; dict?: string; fullRow?: boolean };
type DetailSection = { index: string; title: string; description: string; items: DetailItem[] };
type StepState = 'done' | 'current' | 'pending' | 'error';

const props = withDefaults(defineProps<{ catalog: CollectionCatalogVo; historical?: boolean }>(), { historical: false });
const themeVars = useThemeVars();
const catalogTheme = computed<CSSProperties>(() => ({
  '--catalog-surface': themeVars.value.cardColor,
  '--catalog-text-color-1': themeVars.value.textColor1,
  '--catalog-text-color-2': themeVars.value.textColor2,
  '--catalog-text-color-3': themeVars.value.textColor3,
  '--catalog-border-color': themeVars.value.borderColor,
}));
const activeTab = ref('basic');
const careRef = ref<any>();
const historyRef = ref<any>();
const CatalogVersionHistoryDrawer = defineAsyncComponent(() => import('./CatalogVersionHistoryDrawer.vue'));
watch(() => [props.catalog.id, props.catalog.collectionId, props.catalog.orgId], () => historyRef.value?.close(), { flush: 'sync' });
const fragilityRef = ref<any>();
const CollectionFragilityDrawer = defineAsyncComponent(() => import('./CollectionFragilityDrawer.vue'));
const content = computed(() => props.catalog.content);
const coverSrc = computed(() => (content.value.coverImage ? fileUrl(content.value.coverImage) : ''));
const coverRevision = computed(() => JSON.stringify([props.catalog.id, props.catalog.stateHash, props.catalog.version, props.catalog.updateTime]));
const displayName = computed(
  () => content.value.formalName || content.value.totalName || props.catalog.collectionName || '未定名藏品',
);
const currentVersion = computed(
  () => props.catalog.submittedVersion || props.catalog.approvedVersion || props.catalog.version || 0,
);
const eraSummary = computed(() =>
  eraText(content.value),
);
const quantitySummary = computed(
  () =>
    quantity(
      content.value.actualQuantity,
      content.value.actualQuantityUnit,
    ) || '--',
);
const statusLabel = computed(() => statusMeta(props.catalog.status).label);
const statusTone = computed(() => statusMeta(props.catalog.status).tone);
const statusDescription = computed(() => statusMeta(props.catalog.status).description);
const dimensionText = computed(() => standardDimensions(content.value));

const SectionHeading = defineComponent({
  props: {
    section: { type: Object as PropType<DetailSection>, required: true },
  },
  setup(componentProps) {
    return () =>
      h('div', { class: 'section-heading' }, [
        h('span', { class: 'section-index' }, componentProps.section.index),
        h('div', [h('h3', componentProps.section.title), h('p', componentProps.section.description)]),
      ]);
  },
});

const sections = computed<DetailSection[]>(() => [
  {
    index: '01',
    title: '标识信息',
    description: '藏品登记、定名与分类识别信息',
    items: [
      { label: '总登记号', value: props.catalog.totalRegNo },
      {
        label: '藏品性质',
        value: collectionNatureLabel(content.value.nature),
      },
      { label: '登记号分号', value: content.value.registrationSubNo },
      { label: '分类号', value: content.value.classificationNo },
      { label: '原藏号', value: content.value.originalNo },
      { label: '总品名', value: content.value.totalName },
      { label: '分件名称', value: content.value.componentName },
      { label: '正式定名', value: content.value.formalName },
      { label: '原名', value: content.value.originalName },
      { label: '文物分类', value: props.catalog.categoryName || content.value.categoryId },
      { label: '综合分类', value: content.value.comprehensiveCategory },
      { label: '专题分类', value: content.value.topicCategories?.join('、') },
    ],
  },
  {
    index: '02',
    title: '年代质地',
    description: '年代、地域、人文属性与材质特征',
    items: [
      { label: '年代类型', value: eraMeaningLabel(content.value.registrationDetails?.eraMeaning) },
      { label: '年代表示方式', value: eraNotationLabel(content.value.registrationDetails?.eraNotation) },
      { label: '具体年代', value: content.value.era },
      { label: '地域', value: content.value.region },
      { label: '国别', value: content.value.country },
      { label: '人文类型', value: content.value.culture },
      { label: '文化类型', value: content.value.cultureType },
      { label: '质地类别', value: content.value.textureCategory1 },
      { label: '质地细类', value: content.value.textureCategory2 },
      { label: '质地', value: content.value.texture, dict: 'col_texture' },
      { label: '色泽', value: content.value.color },
    ],
  },
  {
    index: '03',
    title: '鉴定与数量',
    description: '定级结论、数量、尺寸及保存状况',
    items: [
      { label: '藏品级别', value: content.value.level, dict: 'col_relic_level' },
      { label: '鉴定意见', value: content.value.appraisalOpinion },
      { label: '账面数量', value: quantity(content.value.quantity, content.value.unit) },
      { label: '实际数量', value: quantity(content.value.actualQuantity, content.value.actualQuantityUnit) },
      { label: '尺寸', value: dimensionText.value },
      { label: '质量范围', value: content.value.weightRange },
      { label: '具体质量', value: displayMass(content.value.weight, content.value.weightUnit) },
      { label: '容积', value: quantity(content.value.volume, content.value.volumeUnit) },
      { label: '完残程度', value: content.value.completeness, dict: 'col_completeness' },
      { label: '完残状况', fullRow: true, value: content.value.completeStatus },
      { label: '保存状态', value: content.value.preserveState, dict: 'col_preserve' },
    ],
  },
  {
    index: '04',
    title: '来源入藏',
    description: '来源凭证、入藏时间及当前位置',
    items: [
      { label: '来源方式', value: content.value.source, dict: 'col_source' },
      { label: '来源号', value: content.value.sourceNo },
      { label: '来源说明', fullRow: true, value: content.value.sourceDesc },
      { label: '取得日期', value: formatLocalDateValue(content.value.acquireDate) },
      { label: '实收入馆日期', value: formatLocalDateValue(content.value.entryDate) },
      { label: '核收完成时间', value: formatDateTime(content.value.receiptCompletedAt) },
      { label: '取得时间范围', value: content.value.acquireDateRange },
      { label: '取得年度', value: content.value.acquireYear },
      { label: '登记库位', value: props.catalog.warehouseName || content.value.warehouseId },
    ],
  },
  {
    index: '05',
    title: '形态描述与研究',
    description: '器物特征、铭文著录与研究成果',
    items: [
      { label: '基本特征', fullRow: true, value: content.value.feature },
      { label: '铭文款识', fullRow: true, value: content.value.inscription },
      { label: '题跋', fullRow: true, value: content.value.colophon },
      { label: '著录文献', fullRow: true, value: content.value.bibliography },
      { label: '研究说明', fullRow: true, value: content.value.researchNote },
      { label: '著者', value: content.value.author },
      { label: '版本', value: content.value.edition },
      { label: '存卷', value: content.value.volumeNo },
      { label: '估价', value: formatYuan(content.value.valuation) },
      { label: '著作权人', value: content.value.copyrightOwner },
      { label: '权利限制', fullRow: true, value: content.value.rightsRestrictions },
      { label: '备注', fullRow: true, value: content.value.remark },
    ],
  },
]);

const accessoryColumns = [
  { title: '类型', key: 'kind', minWidth: 80, resizable: true, ellipsis: { tooltip: true }, width: 100 },
  { title: '名称', key: 'name', width: 180, resizable: true, ellipsis: { tooltip: true }, minWidth: 140 },
  { title: '状况', key: 'condition', width: 180, resizable: true, ellipsis: { tooltip: true }, minWidth: 140 },
  { title: '数量', key: 'quantity', width: 70 },
  { title: '单位', key: 'unit', minWidth: 60, resizable: true, ellipsis: { tooltip: true }, width: 80 },
  { title: '备注', key: 'remark', width: 240, resizable: true, ellipsis: { tooltip: true }, minWidth: 160 },
];

const workflowSteps = [
  { title: '编目录入', description: '建立工作草稿并补充详细著录' },
  { title: '提交审核', description: '生成版本快照并锁定内容' },
  { title: '审核决定', description: '通过后形成正式编目档案' },
];

function stepState(index: number): StepState {
  const status = props.catalog.status;
  if (status === 'APPROVED') return index < 2 ? 'done' : 'current';
  if (status === 'SUBMITTED' || status === 'PENDING') return index === 0 ? 'done' : index === 1 ? 'current' : 'pending';
  if (status === 'REJECTED') return index === 0 ? 'done' : index === 1 ? 'error' : 'pending';
  return index === 0 ? 'current' : 'pending';
}

function statusMeta(status?: CatalogStatus): {
  label: string;
  description: string;
  tone: 'default' | 'success' | 'warning' | 'error' | 'info';
} {
  const values: Record<
    CatalogStatus,
    { label: string; description: string; tone: 'default' | 'success' | 'warning' | 'error' | 'info' }
  > = {
    DRAFT: { label: '编目草稿', description: '等待补充并提交', tone: 'default' },
    SUBMITTED: { label: '已提交', description: '等待审核决定', tone: 'info' },
    PENDING: { label: '待审核', description: '等待审核决定', tone: 'info' },
    APPROVED: { label: '审核通过', description: '已形成正式版本', tone: 'success' },
    REJECTED: { label: '审核驳回', description: '等待修改后重提', tone: 'error' },
  };
  return status ? values[status] : { label: '状态未知', description: '暂无流程信息', tone: 'default' };
}

function quantity(value: unknown, unit?: string) {
  if (value === undefined || value === null || value === '') return undefined;
  return String(value) + (unit || '');
}

function display(value: unknown) {
  return value === undefined || value === null || value === '' || value === '--' ? '--' : String(value);
}
</script>

<style scoped>
.catalog-detail {
  --detail-border: var(--catalog-border-color);
  color: var(--catalog-text-color-1);
  padding: 4px 2px 28px;
}
.record-hero {
  display: grid;
  grid-template-columns: 156px minmax(0, 1fr) 132px;
  gap: 22px;
  align-items: stretch;
  margin-bottom: 18px;
  padding: 20px;
  border: 1px solid var(--detail-border);
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(54, 112, 88, 0.09), transparent 48%), var(--catalog-surface);
}
.record-cover {
  height: 156px;
  overflow: hidden;
  border: 1px solid var(--detail-border);
  border-radius: 10px;
  background: rgba(128, 128, 128, 0.06);
}
.record-cover :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.record-cover :deep(.current-file-image),
.record-cover :deep(.n-image) { height: 100%; width: 100%; }
.cover-placeholder {
  display: flex;
  height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--catalog-text-color-3);
}
.cover-placeholder span {
  font-size: 15px;
  letter-spacing: 4px;
}
.cover-placeholder small {
  margin-top: 8px;
  font-size: 12px;
}
.record-identity {
  min-width: 0;
}
.identity-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.record-number {
  color: var(--catalog-text-color-3);
  font-size: 12px;
  letter-spacing: 0.8px;
}
.identity-heading h2 {
  margin: 5px 0 0;
  color: var(--catalog-text-color-1);
  font-family: serif;
  font-size: 24px;
  line-height: 1.35;
}
.identity-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.record-feature {
  display: -webkit-box;
  overflow: hidden;
  margin: 14px 0;
  color: var(--catalog-text-color-2);
  line-height: 1.7;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.identity-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.metric-item {
  min-width: 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(128, 128, 128, 0.07);
}
.metric-item span,
.flow-card span,
.attachment-summary span,
.attachment-note > span {
  display: block;
  color: var(--catalog-text-color-3);
  font-size: 12px;
}
.metric-item strong {
  display: block;
  overflow: hidden;
  margin-top: 4px;
  color: var(--catalog-text-color-1);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.completeness-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-left: 1px solid var(--detail-border);
}
.completeness-card strong {
  margin-top: 8px;
  color: var(--catalog-text-color-1);
}
.completeness-card span {
  margin-top: 3px;
  color: var(--catalog-text-color-3);
  font-size: 12px;
}
.catalog-alert {
  margin-bottom: 14px;
}
.missing-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.detail-tabs {
  margin-top: 6px;
}
.section-grid {
  display: grid;
  gap: 18px;
}
.catalog-section {
  padding: 18px;
  border: 1px solid var(--detail-border);
  border-radius: 12px;
  background: var(--catalog-surface);
}
:deep(.section-heading) {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
:deep(.section-index) {
  display: inline-flex;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: rgba(54, 112, 88, 0.11);
  color: #367058;
  font-size: 12px;
  font-weight: 700;
}
:deep(.section-heading h3) {
  margin: 0;
  color: var(--catalog-text-color-1);
  font-size: 15px;
}
:deep(.section-heading p) {
  margin: 3px 0 0;
  color: var(--catalog-text-color-3);
  font-size: 12px;
}
.attachment-summary,
.flow-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.attachment-summary > div,
.flow-card {
  padding: 14px 16px;
  border-radius: 10px;
  background: rgba(128, 128, 128, 0.07);
}
.attachment-summary strong,
.flow-card strong {
  display: block;
  margin-top: 6px;
  color: var(--catalog-text-color-1);
  font-size: 18px;
}
.attachment-note {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 10px;
  background: rgba(128, 128, 128, 0.05);
}
.attachment-note p {
  margin: 6px 0 0;
  line-height: 1.7;
}
.empty-block {
  padding: 28px 0;
}
.workflow-track {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin: 8px 0 22px;
}
.workflow-step {
  position: relative;
  display: flex;
  gap: 10px;
  min-width: 0;
  padding-right: 22px;
}
.workflow-step:not(:last-child)::after {
  position: absolute;
  top: 14px;
  right: 8px;
  left: 42px;
  z-index: 0;
  height: 2px;
  background: var(--detail-border);
  content: '';
}
.step-dot {
  position: relative;
  z-index: 1;
  display: inline-flex;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--detail-border);
  border-radius: 50%;
  background: var(--catalog-surface);
  color: var(--catalog-text-color-3);
  font-size: 12px;
  font-weight: 700;
}
.workflow-step strong {
  display: block;
  margin-top: 4px;
  color: var(--catalog-text-color-1);
}
.workflow-step p {
  margin: 6px 0 0;
  color: var(--catalog-text-color-3);
  font-size: 12px;
  line-height: 1.5;
}
.workflow-step.is-done .step-dot,
.workflow-step.is-current .step-dot {
  border-color: #367058;
  background: #367058;
  color: #fff;
}
.workflow-step.is-done:not(:last-child)::after {
  background: #367058;
}
.workflow-step.is-error .step-dot {
  border-color: #d03050;
  background: #d03050;
  color: #fff;
}
.flow-card small {
  display: block;
  margin-top: 5px;
  color: var(--catalog-text-color-3);
}

@media (max-width: 860px) {
  .record-hero {
    grid-template-columns: 120px minmax(0, 1fr);
  }
  .record-cover {
    height: 120px;
  }
  .completeness-card {
    grid-column: 1 / -1;
    flex-direction: row;
    gap: 14px;
    padding-top: 14px;
    border-top: 1px solid var(--detail-border);
    border-left: 0;
  }
  .identity-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .record-hero {
    grid-template-columns: 1fr;
  }
  .record-cover {
    width: 140px;
    height: 140px;
  }
  .identity-heading {
    flex-direction: column;
  }
  .attachment-summary,
  .flow-cards,
  .workflow-track {
    grid-template-columns: 1fr;
  }
  .workflow-step {
    padding-bottom: 14px;
  }
  .workflow-step:not(:last-child)::after {
    top: 34px;
    bottom: 0;
    left: 14px;
    width: 2px;
    height: auto;
  }
}
</style>

<style scoped>
.detail-text { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>

<template>
  <main class="collection-gallery">
    <section class="gallery-query">
      <NForm inline label-placement="left" :show-feedback="false" class="gallery-form">
        <NFormItem label="检索内容">
          <NInput
            v-model:value="queryParams.keyword"
            clearable
            placeholder="名称 / 总登记号 / 分类号"
            style="width: 230px"
            @keyup.enter="reloadFirstPage"
          />
        </NFormItem>
        <NFormItem label="文物分类">
          <NSelect
            v-model:value="queryParams.categoryId"
            :options="categoryOptions"
            filterable
            clearable
            placeholder="分类"
            style="width: 160px"
          />
        </NFormItem>
        <NFormItem label="藏品级别">
          <HDictSelect
            v-model:value="queryParams.level"
            type="col_relic_level"
            clearable
            placeholder="级别"
            style="width: 130px"
          />
        </NFormItem>
        <NFormItem label="藏品质地">
          <HDictSelect
            v-model:value="queryParams.texture"
            type="col_texture"
            tag
            clearable
            placeholder="质地"
            style="width: 130px"
          />
        </NFormItem>
        <NFormItem label="库房库位">
          <WarehouseTreeSelect v-model:value="queryParams.warehouseId" placeholder="库位" style="width: 160px" />
        </NFormItem>
        <NFormItem label="藏品影像">
          <HDictSelect
            v-model:value="queryParams.hasImage"
            type="col_presence"
            clearable
            placeholder="有无"
            style="width: 110px"
          />
        </NFormItem>
        <NFormItem label="档案附件">
          <HDictSelect
            v-model:value="queryParams.hasAttachment"
            type="col_presence"
            clearable
            placeholder="有无"
            style="width: 110px"
          />
        </NFormItem>
        <NSpace size="small">
          <NButton type="primary" @click="reloadFirstPage">查询</NButton>
          <NButton secondary @click="resetQuery">重置</NButton>
        </NSpace>
      </NForm>
    </section>

    <section class="gallery-summary">
      <strong>{{ total }}</strong><span>条藏品</span>
    </section>

    <NAlert v-if="galleryError" type="error">{{ galleryError }}</NAlert>
    <div v-if="loading" class="gallery-loading" role="status"><NSpin description="正在加载藏品列表" /></div>
    <section v-else-if="rows.length" class="gallery-grid">
      <article
        v-for="item in rows"
        :key="item.id || item.totalRegNo"
        class="gallery-card"
        role="button"
        tabindex="0"
        @click="openDetail(item)"
        @keydown.enter.prevent="openDetail(item)"
        @keydown.space.prevent="openDetail(item)"
      >
        <div class="gallery-image">
          <CurrentFileImage
            v-if="imageFileId(item)"
            :src="fileUrl(imageFileId(item))"
            :alt="item.name || '藏品影像'"
            :revision="galleryRevision"
            :width="480"
            :height="360"
            lazy
            preview-disabled
            hide-reload
          />
          <div v-else class="gallery-image-empty">待设封面</div>
        </div>
        <div class="gallery-card-body">
          <div class="gallery-card-title">
            <h3>{{ item.name || '--' }}</h3>
            <HDictText type="col_relic_level" :value="item.level" />
          </div>
          <dl>
            <template v-for="meta in galleryCardMeta(item)" :key="meta.label">
              <dt>{{ meta.label }}</dt>
              <dd>{{ meta.value }}</dd>
            </template>
          </dl>
        </div>
      </article>
    </section>
    <NEmpty v-else description="暂无图库藏品" class="gallery-empty" />

    <div class="gallery-pagination">
      <NPagination
        v-model:page="pageNum"
        v-model:page-size="pageSize"
        :item-count="total"
        :page-sizes="[12, 24, 48, 96]"
        show-size-picker
        @update:page="loadGallery"
        @update:page-size="reloadFirstPage"
      />
    </div>

    <HPageForm content-only :enable-cancel="false" v-model:show="detailShow" title="图库藏品详情">
      <NSpin v-if="detailLoading" size="small" />
      <NAlert v-if="detailError" type="error">{{ detailError }}<NButton @click="retryDetail">重试</NButton></NAlert>
      <div v-if="detailShow && current" class="gallery-detail">
        <aside class="gallery-detail-media">
          <CurrentFileImage
            v-if="previewImageSrc"
            :src="previewImageSrc"
            :revision="detailRevision"
            :width="960"
            :height="720"
            padding
            :alt="current.name || '藏品影像'"
          />
          <NEmpty v-else description="暂无影像" size="small" />
        </aside>
        <section class="gallery-detail-info">
          <div class="gallery-detail-title">
            <div>
              <h3>{{ current.name || '--' }}</h3>
              <p>{{ current.totalRegNo || '未生成总登记号' }}</p>
            </div>
            <NButton size="small" secondary @click="printArchive">打印登记表</NButton>
          </div>
          <NDescriptions label-placement="left" :column="2" bordered size="small">
            <NDescriptionsItem label="分类号">{{ current.classNo || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="辅助编号">{{ current.auxNo || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="文物分类">{{ current.categoryName || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="年代">{{ current.era || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="质地"
              ><CollectionCatalogDictText type="col_texture" :value="current.texture"
            /></NDescriptionsItem>
            <NDescriptionsItem label="级别"
              ><HDictText type="col_relic_level" :value="current.level"
            /></NDescriptionsItem>
            <NDescriptionsItem label="来源"><HDictText type="col_source" :value="current.source" /></NDescriptionsItem>
            <NDescriptionsItem label="库位">{{ current.warehouseName || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="完残"
              ><HDictText type="col_completeness" :value="current.completeness"
            /></NDescriptionsItem>
            <NDescriptionsItem label="状态"
              ><HDictText type="col_life_status" :value="current.lifeStatus"
            /></NDescriptionsItem>
            <NDescriptionsItem label="影像/附件"
              >{{ current.mediaCount || 0 }} / {{ current.attachmentCount || 0 }}</NDescriptionsItem
            >
            <NDescriptionsItem label="取得日期">{{ formatLocalDateValue(current.acquireDate) }}</NDescriptionsItem>
            <NDescriptionsItem label="特征描述" :span="2" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ current.feature || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="备注" :span="2" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ current.remark || '--' }}</NDescriptionsItem>
          </NDescriptions>
        </section>
      </div>
    </HPageForm>
    <CollectionPrintPreviewModal
      v-model:show="printShow"
      :type="COLLECTION_ARCHIVE_PRINT_TYPE"
      :id="printId"
      :document-no="printDocumentNo"
      title="藏品登记表"
    />
  </main>
</template>

<script setup lang="ts">
import CollectionCatalogDictText from '../components/CollectionCatalogDictText.vue';
import { computed, onMounted, onActivated, onDeactivated, onBeforeUnmount, reactive, ref, watch } from 'vue';
import CurrentFileImage from '@/components/basic/CurrentFileImage.vue';
import { fileUrl, formatLocalDateValue } from '@/utils';
import { categoryApi } from '../api/category';
import { collectionApi } from '../api/collection';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import WarehouseTreeSelect from '../components/WarehouseTreeSelect.vue';
import type { CollectionVo } from '../typings/collection';
import { buildGalleryQuery, galleryCardMeta, galleryImageFileId } from '../utils/collectionGallery';
import { COLLECTION_ARCHIVE_PRINT_TYPE } from '../utils/printDocumentMeta';

const loading = ref(false);
const galleryError = ref(''),
  detailError = ref(''),
  detailLoading = ref(false);
const galleryRevision = ref(0),
  detailRevision = ref(0);
let pageRequest = 0,
  detailRequest = 0,
  active = true,
  disposed = false;
let detailTarget: CollectionVo | undefined;
const rows = ref<CollectionVo[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(24);
const categoryOptions = ref<{ label: string; value: string }[]>([]);
const current = ref<CollectionVo | null>(null);
const detailShow = ref(false);
const printShow = ref(false);
const printId = ref('');
const printDocumentNo = ref('');

const queryParams = reactive({
  keyword: null as string | null,
  categoryId: null as string | null,
  level: null as string | null,
  texture: null as string | null,
  warehouseId: null as string | null,
  hasImage: 'true' as string | null,
  hasAttachment: null as string | null,
});

const previewImageSrc = computed(() => {
  const fileId = current.value ? galleryImageFileId(current.value) : '';
  return fileId ? fileUrl(fileId) : '';
});

onMounted(async () => {
  await Promise.all([loadOptions(), loadGallery()]);
});

async function loadOptions() {
  try {
    const response: any = await categoryApi.list();
    if (!active || disposed) return;
    const categories: any[] = response?.data?.list ?? response?.data ?? [];
    categoryOptions.value = categories.map((item) => ({ label: item.name, value: item.id }));
  } catch {
    if (active && !disposed) galleryError.value = '分类选项读取失败，请重新进入页面。';
  }
}

async function loadGallery() {
  if (!active || disposed) return;
  const ticket = ++pageRequest;
  loading.value = true;
  galleryError.value = '';
  rows.value = [];
  try {
    const response: any = await collectionApi.page(
      buildGalleryQuery({ ...queryParams, pageNum: pageNum.value, pageSize: pageSize.value }),
    );
    if (ticket !== pageRequest || !active || disposed) return;
    const page = response?.data ?? {};
    const items: CollectionVo[] = page.list ?? page.data ?? [];
    rows.value = items;
    total.value = Number(page.total ?? items.length);
    galleryRevision.value++;
  } catch {
    if (ticket === pageRequest && active && !disposed) {
      total.value = 0;
      galleryError.value = '图库读取失败，请重试。';
    }
  } finally {
    if (ticket === pageRequest) loading.value = false;
  }
}

function imageFileId(item: CollectionVo) {
  return galleryImageFileId(item);
}

function reloadFirstPage() {
  pageNum.value = 1;
  loadGallery();
}

function resetQuery() {
  Object.assign(queryParams, {
    keyword: null,
    categoryId: null,
    level: null,
    texture: null,
    warehouseId: null,
    hasImage: 'true',
    hasAttachment: null,
  });
  reloadFirstPage();
}

async function openDetail(row: CollectionVo) {
  if (!row.id || !active || disposed) return;
  const ticket = ++detailRequest;
  detailTarget = row;
  current.value = null;
  detailError.value = '';
  detailLoading.value = true;
  detailShow.value = true;
  try {
    const response: any = await collectionApi.get(row.id);
    if (ticket !== detailRequest || !detailShow.value || !active || disposed) return;
    if (!response?.data || response.data.id !== row.id) throw new Error('藏品身份不一致');
    current.value = response.data;
    detailRevision.value++;
  } catch {
    if (ticket === detailRequest && detailShow.value && active && !disposed)
      detailError.value = '藏品详情读取失败，请核对当前授权后重试。';
  } finally {
    if (ticket === detailRequest) detailLoading.value = false;
  }
}
function retryDetail() {
  if (detailTarget) void openDetail(detailTarget);
}
watch(
  detailShow,
  (show) => {
    if (!show) {
      detailRequest++;
      current.value = null;
      detailLoading.value = false;
      detailError.value = '';
    }
  },
  { flush: 'sync' },
);
function leaveGallery() {
  active = false;
  pageRequest++;
  detailRequest++;
  rows.value = [];
  current.value = null;
  loading.value = false;
  detailLoading.value = false;
  detailShow.value = false;
  printShow.value = false;
}
onDeactivated(leaveGallery);
onBeforeUnmount(() => {
  disposed = true;
  leaveGallery();
});
onActivated(() => {
  if (!active && !disposed) {
    active = true;
    void loadGallery();
  }
});

function printArchive() {
  if (!current.value?.id) return;
  printId.value = current.value.id;
  printDocumentNo.value = current.value.totalRegNo || current.value.id;
  printShow.value = true;
}
</script>

<style scoped>
.collection-gallery {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  color: #172033;
}

.gallery-query {
  border: 1px solid #dbe5ef;
  border-radius: 8px;
  background: #fff;
}

.gallery-query {
  padding: 12px 14px;
}

.gallery-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 12px;
}

.gallery-form :deep(.n-form-item-label) {
  white-space: nowrap;
  flex-shrink: 0;
}

.gallery-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  padding: 0 2px;
  color: #5b6b82;
  font-size: 13px;
}
.gallery-summary strong {
  color: #172033;
  font-size: 22px;
  font-variant-numeric: tabular-nums;
}

.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.gallery-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #dbe5ef;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    transform 0.16s ease;
}

.gallery-card:hover,
.gallery-card:focus-visible {
  border-color: #2f73e0;
  box-shadow: 0 10px 24px rgb(37 74 120 / 12%);
  outline: none;
  transform: translateY(-1px);
}

.gallery-image {
  display: flex;
  aspect-ratio: 4 / 3;
  align-items: center;
  justify-content: center;
  background: #f3f6f9;
}

.gallery-image :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.gallery-image-empty {
  color: #74839a;
  font-size: 13px;
}

.gallery-card-body {
  padding: 12px;
}

.gallery-card-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.gallery-card-title h3 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: #172033;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gallery-card dl {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 5px 8px;
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.35;
}

.gallery-card dt {
  color: #74839a;
}

.gallery-card dd {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: #27354b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gallery-loading {
  display: flex;
  min-height: 240px;
  align-items: center;
  justify-content: center;
}

.gallery-empty {
  margin: 64px 0;
}

.gallery-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 8px 0 0;
}

.gallery-detail {
  display: grid;
  grid-template-columns: minmax(240px, 34%) minmax(0, 1fr);
  gap: 16px;
}

.gallery-image :deep(.current-file-image),
.gallery-image :deep(.n-image) {
  width: 100%;
  height: 100%;
}
.gallery-detail-media :deep(img) {
  max-height: 360px;
  object-fit: contain;
}
.gallery-detail-media {
  display: flex;
  min-height: 320px;
  align-items: center;
  justify-content: center;
  border: 1px solid #dbe5ef;
  border-radius: 8px;
  background: #f6f8fb;
}

.gallery-detail-info {
  min-width: 0;
}

.gallery-detail-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.gallery-detail-title h3 {
  margin: 0 0 4px;
  font-size: 20px;
  line-height: 1.35;
}

.gallery-detail-title p {
  margin: 0;
  color: #6a778b;
  font-size: 13px;
}

@media (max-width: 900px) {
  .gallery-detail {
    grid-template-columns: 1fr;
  }

  .gallery-detail-media {
    min-height: 240px;
  }
}
</style>

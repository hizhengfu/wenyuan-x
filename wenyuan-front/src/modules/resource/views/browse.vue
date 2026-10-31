<template>
  <n-card
    size="small"
    :bordered="false"
    content-style="flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden;"
    class="resource-browse-page h-full flex flex-col"
  >
    <template #header>
      <n-space align="center">
        <n-icon size="20"><IconMdiViewGalleryOutline /></n-icon>
        <span>资源检索</span>
        <n-tag v-if="currentCategoryName" size="small" type="info" :bordered="false">
          当前：{{ currentCategoryName }}
        </n-tag>
      </n-space>
    </template>
    <template #header-extra>
      <div class="browse-filters">
        <n-input
          v-model:value="paramsForm.keyword"
          placeholder="搜索资源名称/标签"
          clearable
          size="small"
          style="width: 220px"
          @keydown.enter="reload"
        >
          <template #prefix
            ><n-icon><IconMdiMagnify /></n-icon
          ></template>
        </n-input>
        <n-select
          v-model:value="paramsForm.resourceType"
          :options="RESOURCE_TYPE_OPTIONS"
          placeholder="全部类型"
          clearable
          size="small"
          style="width: 130px"
          @update:value="reload"
        />
        <n-input
          v-model:value="paramsForm.era"
          placeholder="年代"
          clearable
          size="small"
          style="width: 110px"
          @keydown.enter="reload"
        />
        <n-select
          v-model:value="paramsForm.purpose"
          :options="PURPOSES"
          placeholder="全部用途"
          clearable
          size="small"
          style="width: 130px"
          @update:value="reload"
        />
        <n-button size="small" @click="reload">查询</n-button>
        <n-button-group size="small">
          <n-button
            :type="layout === 'grid' ? 'primary' : 'default'"
            aria-label="网格视图"
            :aria-pressed="layout === 'grid'"
            @click="layout = 'grid'"
          >
            <template #icon
              ><n-icon><IconMdiViewGrid /></n-icon
            ></template>
          </n-button>
          <n-button
            :type="layout === 'list' ? 'primary' : 'default'"
            aria-label="列表视图"
            :aria-pressed="layout === 'list'"
            @click="layout = 'list'"
          >
            <template #icon
              ><n-icon><IconMdiViewList /></n-icon
            ></template>
          </n-button>
        </n-button-group>
      </div>
    </template>

    <n-grid :cols="24" :x-gap="12" :y-gap="12" item-responsive class="browse-columns">
      <!-- 左：分类树 -->
      <n-grid-item span="24 640:6" class="browse-categories">
        <n-card size="small" :segmented="{ content: true }" content-style="padding:8px" class="h-full">
          <template #header>
            <span class="text-sm">分类目录</span>
          </template>
          <template #header-extra>
            <n-button size="tiny" text aria-label="刷新分类" @click="loadTree">
              <template #icon
                ><n-icon><IconMdiRefresh /></n-icon
              ></template>
            </n-button>
          </template>

          <n-input v-model:value="treeFilter" placeholder="过滤分类" size="small" clearable class="mb-2">
            <template #prefix
              ><n-icon><IconMdiMagnify /></n-icon
            ></template>
          </n-input>

          <n-scrollbar class="browse-tree">
            <!-- 全部资源（虚拟根节点） -->
            <div
              class="all-node"
              role="button"
              tabindex="0"
              :class="{ active: !selectedCategoryId }"
              @click="selectAll"
              @keydown.enter="selectAll"
              @keydown.space.prevent="selectAll"
            >
              <n-icon><IconMdiViewGalleryOutline /></n-icon>
              <span class="ml-2">全部资源</span>
            </div>
            <n-tree
              block-line
              :data="treeData"
              :pattern="treeFilter"
              :selected-keys="selectedKeys"
              key-field="id"
              label-field="name"
              children-field="children"
              :default-expand-all="true"
              @update:selected-keys="onSelectCategory"
            />

            <!-- 热门标签云 -->
            <template v-if="hotTags.length">
              <div class="tag-cloud-title">
                <n-icon size="13"><IconMdiTagMultipleOutline /></n-icon>
                <span class="ml-1">热门标签</span>
              </div>
              <div class="tag-cloud">
                <n-tag
                  v-for="tag in hotTags"
                  :key="tag.name"
                  size="small"
                  :bordered="false"
                  :type="selectedTag === tag.name ? 'primary' : 'default'"
                  :style="tagFontStyle(tag.useCount)"
                  class="tag-chip"
                  @click="toggleTag(tag.name)"
                >
                  {{ tag.name }}
                  <span class="tag-count">{{ tag.useCount }}</span>
                </n-tag>
              </div>
            </template>
          </n-scrollbar>
        </n-card>
      </n-grid-item>

      <!-- 右：资源列表 -->
      <n-grid-item span="24 640:18" class="browse-results">
        <n-card size="small" :segmented="{ content: true }" content-style="padding:12px" class="h-full">
          <n-spin :show="loading" class="h-full" content-class="h-full flex flex-col">
            <!-- 网格视图 -->
            <div v-if="layout === 'grid'" class="browse-grid">
              <div class="flex-center flex-1" v-if="!loading && !resources.length">
                <n-empty description="该分类下暂无资源" />
              </div>
              <n-grid v-else class="browse-cards" cols="2 s:3 m:4 l:5 xl:6" :x-gap="12" :y-gap="12" responsive="screen">
                <n-grid-item v-for="row in resources" :key="row.id">
                  <div
                    class="card-item"
                    role="button"
                    tabindex="0"
                    :aria-label="`预览 ${row.name}`"
                    @click="openPreview(row)"
                    @keydown.enter="openPreview(row)"
                    @keydown.space.prevent="openPreview(row)"
                  >
                    <div class="card-thumb">
                      <n-image
                        v-if="row.thumbnailPath"
                        :src="thumbUrl(row.thumbnailPath)"
                        object-fit="cover"
                        :preview-disabled="true"
                        class="w-full h-full"
                      />
                      <div v-else class="card-thumb-empty">
                        <n-icon size="40" depth="3">
                          <component :is="iconOf(row.resourceType)" />
                        </n-icon>
                      </div>
                      <span v-if="row.fileExt" class="card-ext">.{{ row.fileExt }}</span>
                    </div>
                    <div class="card-meta">
                      <div class="card-name" :title="row.name">{{ row.name }}</div>
                      <div class="card-sub">
                        <span>{{
                          RESOURCE_TYPE_OPTIONS.find((o) => o.value === row.resourceType)?.label || row.resourceType
                        }}</span>
                        <span class="text-gray-400">v{{ row.currentVersion || 1 }}</span>
                      </div>
                    </div>
                  </div>
                </n-grid-item>
              </n-grid>
            </div>

            <!-- 列表视图 -->
            <n-data-table
              v-else
              class="browse-table"
              flex-height
              :scroll-x="680"
              :columns="listColumns"
              :data="resources"
              :loading="loading"
              :bordered="false"
              size="small"
              @row-click="(row: any) => openPreview(row)"
              :row-props="(row: any) => ({ style: 'cursor: pointer' })"
            >
            </n-data-table>

            <!-- 分页 -->
            <div class="browse-pagination">
              <n-pagination
                v-model:page="page.pageNum"
                v-model:page-size="page.pageSize"
                :item-count="totalCount"
                :page-sizes="[12, 24, 48, 96]"
                :page-slot="5"
                show-size-picker
                @update:page="reload"
                @update:page-size="reload"
              />
            </div>
          </n-spin>
        </n-card>
      </n-grid-item>
    </n-grid>
    <!-- 预览抽屉 -->
    <ResourcePreview v-model:show="previewShow" :resource-id="previewId" />
  </n-card>
</template>

<script lang="ts" setup>
import { PURPOSES } from '../api/business';
import { h, ref, computed, onMounted, watch } from 'vue';
import { NButton, NImage } from 'naive-ui';
import dayjs from 'dayjs';
import { fileUrl } from '@/utils/path';
import { tree as fetchTree } from '../api/category';
import { list, topTags, RESOURCE_TYPE_OPTIONS } from '../api/resource';
import { formatSize } from '../api/stat';
import ResourcePreview from '../components/ResourcePreview.vue';

// ===== 树 =====
const treeData = ref<any[]>([]);
const treeFilter = ref('');
const selectedKeys = ref<string[]>([]);
const selectedCategoryId = ref<string>('');
const currentCategoryName = computed(() => {
  if (!selectedCategoryId.value) return '';
  return findNodeById(treeData.value, selectedCategoryId.value)?.name || '';
});

function findNodeById(nodes: any[], id: string): any | null {
  for (const n of nodes) {
    if (n.id === id) return n;
    if (n.children?.length) {
      const r = findNodeById(n.children, id);
      if (r) return r;
    }
  }
  return null;
}

async function loadTree() {
  const resp: any = await fetchTree({});
  treeData.value = resp.data || [];
}

function selectAll() {
  selectedKeys.value = [];
  selectedCategoryId.value = '';
  selectedTag.value = '';
  paramsForm.value.tag = '';
  page.value.pageNum = 1;
  reload();
}

function onSelectCategory(keys: string[]) {
  selectedKeys.value = keys;
  selectedCategoryId.value = keys[0] || '';
  page.value.pageNum = 1;
  reload();
}

// ===== 资源列表 =====
const layout = ref<'grid' | 'list'>('grid');
const loading = ref(false);
const resources = ref<any[]>([]);
const totalCount = ref(0);

// status 固定为 PUBLISHED，浏览页只展示已发布内容，不暴露给用户修改
const props = defineProps({
  stationId: { type: String, default: '' },
  resourceType: { type: String, default: '' },
});

const paramsForm = ref<Record<string, any>>({
  keyword: '',
  era: '',
  purpose: null,
  resourceType: props.resourceType || null,
  status: 'PUBLISHED',
  tag: '', // 标签云点击时写入，走后端精确匹配
});

// ===== 标签云 =====
const hotTags = ref<{ name: string; color: string | null; useCount: number }[]>([]);
const selectedTag = ref('');

async function loadHotTags() {
  try {
    const resp: any = await topTags(30);
    hotTags.value = (resp?.data || []).filter((t: any) => t.name);
  } catch {}
}

/** 点击标签：选中 / 取消选中，触发列表刷新 */
function toggleTag(name: string) {
  if (selectedTag.value === name) {
    selectedTag.value = '';
    paramsForm.value.tag = '';
  } else {
    selectedTag.value = name;
    paramsForm.value.tag = name;
  }
  page.value.pageNum = 1;
  reload();
}

/** 标签字号随 useCount 缩放（12px ~ 15px） */
function tagFontStyle(useCount: number) {
  const max = Math.max(...hotTags.value.map((t) => t.useCount || 1), 1);
  const ratio = Math.sqrt((useCount || 1) / max); // 平方根平滑
  const size = 11 + Math.round(ratio * 4);
  return { fontSize: `${size}px`, cursor: 'pointer' };
}
const page = ref({ pageNum: 1, pageSize: 24 });

async function reload() {
  loading.value = true;
  try {
    // 仅传递有值的过滤项，避免后端将空字符串当作精确匹配条件（status='' 等）
    const params: Record<string, any> = {
      pageNum: page.value.pageNum,
      pageSize: page.value.pageSize,
    };
    Object.entries(paramsForm.value).forEach(([k, v]) => {
      if (v !== '' && v !== null && v !== undefined) params[k] = v;
    });
    if (selectedCategoryId.value) params.categoryId = selectedCategoryId.value;
    if (props.stationId) params.stationId = props.stationId;
    const resp: any = await list(params);
    const data = resp?.data || {};
    resources.value = data.list || data.data || [];
    totalCount.value = Number(data.totalCount ?? data.total ?? data.totalRow ?? 0);
  } finally {
    loading.value = false;
  }
}

// ===== 预览 =====
const previewShow = ref(false);
const previewId = ref('');
function openPreview(row: any) {
  previewId.value = row.id;
  previewShow.value = true;
}

// 缩略图 URL（兼容已是绝对地址 / 相对路径两种格式）
function thumbUrl(p: string) {
  if (!p) return '';
  if (/^https?:\/\//i.test(p) || p.startsWith('/userfiles/')) return p;
  return fileUrl(p);
}

// 类型图标
function iconOf(type: string) {
  // 仅返回字符串名称，供 <component :is=""> 自动识别 iconify 全局组件
  switch (type) {
    case 'IMAGE':
      return 'IconMdiFileImageOutline';
    case 'VIDEO':
      return 'IconMdiFileVideoOutline';
    case 'AUDIO':
      return 'IconMdiFileMusicOutline';
    case 'MODEL_3D':
      return 'IconMdiCubeOutline';
    case 'DOCUMENT':
      return 'IconMdiFileDocumentOutline';
    default:
      return 'IconMdiFileQuestionOutline';
  }
}

// 列表视图列定义
const listColumns = [
  {
    key: 'thumbnailPath',
    title: '缩略图',
    width: 70,
    render: (row: any) =>
      row.thumbnailPath
        ? h(NImage, {
            src: thumbUrl(row.thumbnailPath),
            width: 40,
            height: 40,
            previewDisabled: true,
          })
        : h('span', { class: 'text-gray-400' }, '-'),
  },
  {
    key: 'name',
    title: '名称',
    render: (row: any) =>
      h(
        NButton,
        {
          text: true,
          type: 'primary',
          style: 'max-width:100%;justify-content:flex-start',
          onClick: () => openPreview(row),
        },
        {
          default: () =>
            h('span', { style: 'overflow:hidden;text-overflow:ellipsis;white-space:nowrap;display:block' }, row.name),
        },
      ),
  },
  {
    key: 'resourceType',
    title: '类型',
    width: 100,
    render: (row: any) => RESOURCE_TYPE_OPTIONS.find((o) => o.value === row.resourceType)?.label || row.resourceType,
  },
  {
    key: 'fileExt',
    title: '格式',
    width: 80,
    align: 'center' as const,
    render: (row: any) => (row.fileExt ? `.${row.fileExt}` : '-'),
  },
  {
    key: 'currentVersion',
    title: '版本',
    width: 70,
    align: 'center' as const,
    render: (row: any) => `v${row.currentVersion || 1}`,
  },
  { key: 'viewCount', title: '浏览', width: 70, align: 'right' as const },
  { key: 'downloadCount', title: '下载', width: 70, align: 'right' as const },
  {
    key: 'createTime',
    title: '创建时间',
    width: 150,
    render: (row: any) => (row.createTime ? dayjs(row.createTime).format('YYYY-MM-DD HH:mm') : '-'),
  },
];

watch(
  () => paramsForm.value.keyword,
  () => {
    // 关键词输入时不自动搜索，按回车或类型筛选触发
  },
);

onMounted(async () => {
  await loadTree();
  reload();
  loadHotTags();
});
</script>

<style scoped>
.resource-browse-page {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  container-type: inline-size;
}
.resource-browse-page :deep(> .n-card-header) {
  flex: none;
  flex-wrap: wrap;
  gap: 12px;
}
.resource-browse-page :deep(> .n-card-header > .n-card-header__main) {
  flex: none;
}
.resource-browse-page :deep(> .n-card-header > .n-card-header__extra) {
  flex: 1;
  min-width: 0;
}
.browse-filters {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
}
.browse-filters > * {
  flex-shrink: 0;
  max-width: 100%;
}
.resource-browse-page :deep(.browse-columns),
.browse-tree,
.browse-grid,
.browse-table {
  flex: 1;
  min-height: 0;
}
.resource-browse-page :deep(.browse-columns) {
  grid-template-rows: minmax(0, 1fr);
}
.resource-browse-page :deep(.browse-categories),
.resource-browse-page :deep(.browse-results) {
  min-width: 0;
  min-height: 0;
}
.resource-browse-page :deep(.browse-categories > .n-card > .n-card-content),
.resource-browse-page :deep(.browse-results > .n-card > .n-card-content),
.resource-browse-page :deep(.browse-results .n-spin-container),
.resource-browse-page :deep(.browse-results .n-spin-content) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.browse-grid {
  display: flex;
  overflow: auto;
}
.resource-browse-page :deep(.browse-cards) {
  align-content: start;
}
.browse-pagination {
  flex: none;
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
  overflow-x: auto;
}
.resource-browse-page :is(button, [tabindex='0']):focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 2px;
}
@container (max-width: 671px) {
  .resource-browse-page :deep(> .n-card-header > .n-card-header__extra) {
    flex-basis: 100%;
  }
  .resource-browse-page :deep(.browse-columns) {
    grid-template-rows: 180px minmax(0, 1fr);
  }
  .browse-filters {
    justify-content: flex-start;
  }
}

.all-node {
  display: flex;
  align-items: center;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  margin-bottom: 4px;
}
.all-node:hover {
  background: var(--n-color-hover, rgba(0, 0, 0, 0.04));
}
.all-node.active {
  background: var(--n-color-target, rgba(32, 128, 240, 0.1));
  color: var(--n-color-primary, #fff);
}

.card-item {
  background: var(--card-color, #fff);
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
}
.card-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}
.card-thumb {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.card-thumb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: #c0c4cc;
}
.card-status {
  position: absolute;
  top: 6px;
  right: 6px;
}
.card-ext {
  position: absolute;
  bottom: 6px;
  left: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  padding: 1px 6px;
  border-radius: 3px;
  text-transform: uppercase;
  line-height: 1.4;
}
.card-meta {
  padding: 8px 10px;
}
.card-name {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}
.card-sub {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #6c757d;
}

/* ===== 标签云 ===== */
.tag-cloud-title {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #888;
  margin: 14px 0 6px 2px;
}
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  padding: 0 2px;
}
.tag-chip {
  user-select: none;
  transition: opacity 0.15s;
}
.tag-chip:hover {
  opacity: 0.8;
}
.tag-count {
  margin-left: 3px;
  font-size: 10px;
  opacity: 0.55;
}
</style>

<template>
  <ResourcePage title="文件预览">
  <main class="file-viewer-page">
    <div class="viewer-workspace">
      <section class="viewer-hero">
        <div class="viewer-hero-content">
          <form class="viewer-search" role="search" @submit.prevent="search">
            <n-icon size="22" class="viewer-search-icon"><IconMdiMagnify /></n-icon>
            <input v-model="keyword" type="search" aria-label="搜索文件" placeholder="搜索文件名称、编号或标签" />
            <button v-if="keyword" type="button" class="viewer-search-clear" aria-label="清空搜索" @click="clearSearch">
              <n-icon size="18"><IconMdiClose /></n-icon>
            </button>
            <button type="submit" class="viewer-search-submit">
              搜索 <n-icon size="18"><IconMdiArrowRight /></n-icon>
            </button>
          </form>
          <div class="viewer-filters" role="group" aria-label="按文件类型筛选">
            <button
              v-for="filter in typeFilters"
              :key="filter.value"
              type="button"
              :class="{ active: resourceType === filter.value }"
              :aria-pressed="resourceType === filter.value"
              @click="selectType(filter.value)"
            >
              {{ filter.label }}
            </button>
          </div>
        </div>
      </section>

      <section class="viewer-gallery" aria-live="polite">
        <div class="gallery-heading">
          <div>
            <h2>{{ galleryTitle }}</h2>
          </div>
          <span class="gallery-count">{{ totalCount }} 份文件</span>
        </div>

        <n-spin :show="loading && !files.length">
          <div v-if="!loading && loadError && !files.length" class="viewer-empty">
            <div class="viewer-empty-icon">
              <n-icon size="38"><IconMdiFolderSearchOutline /></n-icon>
            </div>
            <h3>文件暂时无法加载</h3>
            <p>请稍后重试。</p>
            <button type="button" @click="loadFiles(1, false)">重新加载</button>
          </div>
          <div v-else-if="!loading && !files.length" class="viewer-empty">
            <div class="viewer-empty-icon">
              <n-icon size="38"><IconMdiFolderSearchOutline /></n-icon>
            </div>
            <h3>{{ appliedKeyword || resourceType ? '没有找到匹配的文件' : '暂时没有可查看的文件' }}</h3>
            <p>{{ appliedKeyword || resourceType ? '换个关键词或类型再试试。' : '这里将展示可查看的文件。' }}</p>
            <button v-if="appliedKeyword || resourceType" type="button" @click="resetFilters">查看全部文件</button>
          </div>
          <div v-else class="file-grid">
            <article
              v-for="file in files"
              :key="file.id"
              class="file-card"
              tabindex="0"
              role="button"
              :aria-label="`预览 ${file.name}`"
              @click="openInPage(file)"
              @keydown.enter="openInPage(file)"
              @keydown.space.prevent="openInPage(file)"
            >
              <div class="file-cover" :class="`file-cover--${toneOf(file.resourceType)}`">
                <n-image
                  v-if="file.thumbnailPath"
                  :src="fileUrl(file.thumbnailPath)"
                  :preview-disabled="true"
                  object-fit="cover"
                />
                <div v-else class="file-cover-placeholder">
                  <n-icon size="60">
                    <IconMdiImageOutline v-if="file.resourceType === 'IMAGE'" />
                    <IconMdiCubeOutline v-else-if="file.resourceType === 'MODEL_3D'" />
                    <IconMdiVideoOutline v-else-if="file.resourceType === 'VIDEO'" />
                    <IconMdiMusicNoteOutline v-else-if="file.resourceType === 'AUDIO'" />
                    <IconMdiFileDocumentOutline v-else />
                  </n-icon>
                </div>
                <span v-if="file.fileExt" class="file-extension">{{ file.fileExt.toUpperCase() }}</span>
                <span class="file-cover-prompt"
                  ><n-icon size="17"><IconMdiFileEyeOutline /></n-icon> 点击预览</span
                >
              </div>
              <div class="file-card-body">
                <h3 :title="file.name">{{ file.name }}</h3>
                <div class="file-card-meta">
                  <span>{{ typeLabel(file.resourceType) }}</span>
                  <span class="meta-dot" aria-hidden="true" />
                  <span>{{ file.fileSize == null ? '大小未知' : formatSize(Number(file.fileSize)) }}</span>
                </div>
                <div class="file-card-bottom">
                  <span
                    >查看文件 <n-icon size="17"><IconMdiArrowRight /></n-icon
                  ></span>
                  <a
                    :href="previewUrl(file.id)"
                    target="_blank"
                    rel="noopener noreferrer"
                    :aria-label="`在新窗口预览 ${file.name}`"
                    @click.stop
                    @keydown.stop
                  >
                    新窗口 <n-icon size="17"><IconMdiOpenInNew /></n-icon>
                  </a>
                </div>
              </div>
            </article>
          </div>
        </n-spin>

        <div v-if="files.length && hasMore" class="viewer-load-more">
          <button type="button" :disabled="loading" @click="loadMore">
            {{ loading ? '正在加载…' : '加载更多文件' }} <n-icon size="18"><IconMdiArrowRight /></n-icon>
          </button>
        </div>
        <div v-else-if="files.length" class="viewer-end">已展示全部文件</div>
      </section>
    </div>

    <n-modal v-model:show="previewShow" :mask-closable="true" class="file-preview-modal">
      <div class="file-preview-dialog">
        <button type="button" class="file-preview-close" aria-label="关闭预览" @click="previewShow = false">
          <n-icon size="20"><IconMdiClose /></n-icon>
        </button>
        <iframe
          v-if="previewShow && selectedFile"
          :src="previewUrl(selectedFile.id)"
          :title="selectedFile.name"
          class="file-preview-frame"
          allow="fullscreen"
          allowfullscreen
        />
      </div>
    </n-modal>
  </main>
  </ResourcePage>
</template>

<script lang="ts" setup>
import ResourcePage from '../components/ResourcePage.vue';
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import { fileUrl } from '@/utils/path';
import { viewerList } from '../api/resource';
import { formatSize } from '../api/stat';

type FileRow = {
  id: string;
  name: string;
  thumbnailPath?: string;
  fileExt?: string;
  fileSize?: number | null;
  resourceType: string;
};
const typeFilters = [
  { label: '全部文件', value: '' },
  { label: '图片', value: 'IMAGE' },
  { label: '文档', value: 'DOCUMENT' },
  { label: '三维', value: 'MODEL_3D' },
  { label: '视频', value: 'VIDEO' },
  { label: '音频', value: 'AUDIO' },
];
const typeLabels: Record<string, string> = {
  IMAGE: '图片',
  DOCUMENT: '文档',
  MODEL_3D: '三维文件',
  VIDEO: '视频',
  AUDIO: '音频',
};
const router = useRouter();
const message = useMessage();
const keyword = ref('');
const appliedKeyword = ref('');
const resourceType = ref('');
const pageNum = ref(1);
const pageSize = 24;
const totalCount = ref(0);
const loading = ref(false);
const loadError = ref(false);
const files = ref<FileRow[]>([]);
const selectedFile = ref<FileRow | null>(null);
const previewShow = ref(false);
const hasMore = computed(() => files.value.length < totalCount.value);
const galleryTitle = computed(() =>
  appliedKeyword.value
    ? '搜索结果'
    : (typeFilters.find((filter) => filter.value === resourceType.value)?.label || '全部') +
      (resourceType.value ? '文件' : ''),
);
let requestSerial = 0;

function toneOf(type: string) {
  return ['IMAGE', 'DOCUMENT', 'MODEL_3D', 'VIDEO', 'AUDIO'].includes(type) ? type.toLowerCase() : 'document';
}

function typeLabel(type: string) {
  return typeLabels[type] || '文件';
}

function previewUrl(id: string) {
  return router.resolve({ path: `/resource-preview/${encodeURIComponent(id)}` }).href;
}

async function loadFiles(page: number, append: boolean) {
  const serial = ++requestSerial;
  if (!append) {
    files.value = [];
    loadError.value = false;
  }
  loading.value = true;
  try {
    const response: any = await viewerList({
      pageNum: page,
      pageSize,
      ...(appliedKeyword.value ? { keyword: appliedKeyword.value } : {}),
      ...(resourceType.value ? { resourceType: resourceType.value } : {}),
    });
    if (serial !== requestSerial) return;
    const result = response?.data || {};
    const rows: FileRow[] = Array.isArray(result.list) ? result.list : [];
    files.value = append ? [...files.value, ...rows] : rows;
    loadError.value = false;
    pageNum.value = page;
    totalCount.value = Number(result.totalCount ?? result.total ?? result.totalRow ?? 0);
  } catch {
    if (serial !== requestSerial) return;
    if (!append) {
      files.value = [];
      totalCount.value = 0;
      loadError.value = true;
    }
    message.error('文件列表加载失败');
  } finally {
    if (serial === requestSerial) loading.value = false;
  }
}

function search() {
  appliedKeyword.value = keyword.value.trim();
  void loadFiles(1, false);
}

function clearSearch() {
  keyword.value = '';
  search();
}

function selectType(value: string) {
  if (resourceType.value === value) return;
  resourceType.value = value;
  void loadFiles(1, false);
}

function resetFilters() {
  keyword.value = '';
  appliedKeyword.value = '';
  resourceType.value = '';
  void loadFiles(1, false);
}

function loadMore() {
  if (loading.value || !hasMore.value) return;
  void loadFiles(pageNum.value + 1, true);
}

function openInPage(file: FileRow) {
  selectedFile.value = file;
  previewShow.value = true;
}

onMounted(() => void loadFiles(1, false));
</script>

<style scoped>
.file-viewer-page {
  min-height: calc(100vh - 110px);
  padding: 18px 24px 42px;
  color: #172033;
  background: #f5f7fb;
}
.viewer-workspace {
  max-width: 1560px;
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid #e4e9f2;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 12px 32px rgba(32, 49, 84, 0.055);
}
.viewer-hero {
  border-bottom: 1px solid #e7ebf4;
  background: linear-gradient(135deg, #fafbff 0%, #f2f5ff 100%);
}
.viewer-hero-content {
  padding: 27px 32px 22px;
}
.viewer-intro {
  display: flex;
  align-items: center;
  gap: 13px;
}
.viewer-intro-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 1px solid #dadcf8;
  border-radius: 13px;
  color: #6453d5;
  background: #ebeafe;
}
.viewer-hero h1 {
  margin: 0;
  color: #26344f;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
}
.viewer-hero p {
  margin: 3px 0 0;
  color: #77849b;
  font-size: 12px;
}
.viewer-search {
  display: flex;
  align-items: center;
  gap: 11px;
  width: min(100%, 760px);
  height: 48px;
  margin-top: 21px;
  padding: 5px 6px 5px 17px;
  border: 1px solid #dfe5f2;
  border-radius: 11px;
  background: #fff;
  box-shadow: 0 3px 12px rgba(44, 59, 102, 0.045);
}
.viewer-search-icon {
  flex: none;
  color: #6f829d;
}
.viewer-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  color: #172033;
  background: transparent;
  font-size: 14px;
}
.viewer-search input::placeholder {
  color: #91a0b4;
}
.viewer-search input::-webkit-search-cancel-button {
  display: none;
}
.viewer-search-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 7px;
  color: #72829b;
  background: transparent;
  cursor: pointer;
}
.viewer-search-clear:hover {
  background: #eef3f9;
}
.viewer-search-submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  flex: none;
  height: 36px;
  padding: 0 17px;
  border: 0;
  border-radius: 10px;
  color: #fff;
  background: #6251d5;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.viewer-search-submit:hover {
  background: #5141bf;
}
.viewer-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 15px;
}
.viewer-filters button {
  padding: 6px 12px;
  border: 1px solid #e2e7f1;
  border-radius: 999px;
  color: #66758e;
  background: rgba(255, 255, 255, 0.72);
  font-size: 12px;
  cursor: pointer;
  transition:
    background-color 160ms ease,
    color 160ms ease;
}
.viewer-filters button:hover {
  border-color: #c7c3f4;
  color: #5749c4;
  background: #f4f3ff;
}
.viewer-filters button.active {
  border-color: #cabff9;
  color: #5744c9;
  background: #fff;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(98, 81, 213, 0.08);
}
.viewer-gallery {
  padding: 23px 32px 32px;
}
.gallery-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin: 0 0 19px;
}
.gallery-heading h2 {
  margin: 0 0 4px;
  color: #26344f;
  font-size: 19px;
  line-height: 1.3;
}
.gallery-heading p {
  margin: 0;
  color: #76859b;
  font-size: 13px;
}
.gallery-count {
  flex: none;
  padding: 6px 10px;
  border-radius: 8px;
  color: #6251d5;
  background: #f3f1ff;
  font-size: 12px;
  font-weight: 600;
}
.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(216px, 1fr));
  gap: 18px;
}
.file-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #e8edf4;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 5px 18px rgba(30, 54, 86, 0.045);
  cursor: pointer;
  transition:
    transform 180ms ease,
    box-shadow 180ms ease,
    border-color 180ms ease;
}
.file-card:hover {
  border-color: #c9c2f4;
  box-shadow: 0 14px 30px rgba(45, 39, 104, 0.11);
  transform: translateY(-3px);
}
.file-card:focus-visible,
.viewer-filters button:focus-visible,
.viewer-search button:focus-visible,
.viewer-load-more button:focus-visible,
.file-card a:focus-visible,
.file-preview-close:focus-visible {
  outline: 2px solid #7d70df;
  outline-offset: 3px;
}
.file-cover {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: #e9f1f8;
}
.file-cover::before {
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 20% 25%, rgba(255, 255, 255, 0.58), transparent 40%);
  content: '';
}
.file-cover--image {
  color: #3e83a4;
  background: linear-gradient(135deg, #d9f3f2, #c4e1f1);
}
.file-cover--document {
  color: #647cae;
  background: linear-gradient(135deg, #e7ecfa, #d7e4f5);
}
.file-cover--model_3d {
  color: #7466aa;
  background: linear-gradient(135deg, #e9e1f7, #d8ddf5);
}
.file-cover--video {
  color: #b47b77;
  background: linear-gradient(135deg, #f6e5e7, #f0dacc);
}
.file-cover--audio {
  color: #4b9a8b;
  background: linear-gradient(135deg, #dbf1e9, #d8e9eb);
}
.file-cover :deep(.n-image),
.file-cover :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.file-cover :deep(.n-image) {
  position: absolute;
  inset: 0;
}
.file-cover-placeholder {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 106px;
  height: 106px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.43);
  box-shadow: 0 12px 24px rgba(50, 75, 110, 0.08);
}
.file-extension {
  position: absolute;
  z-index: 2;
  top: 12px;
  right: 12px;
  padding: 5px 9px;
  border: 1px solid rgba(255, 255, 255, 0.44);
  border-radius: 7px;
  color: #fff;
  background: rgba(23, 42, 69, 0.66);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  backdrop-filter: blur(8px);
}
.file-cover-prompt {
  position: absolute;
  z-index: 2;
  bottom: 13px;
  left: 50%;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 13px;
  border-radius: 999px;
  color: #fff;
  background: rgba(16, 33, 57, 0.78);
  font-size: 12px;
  white-space: nowrap;
  opacity: 0;
  transform: translate(-50%, 6px);
  transition:
    opacity 160ms ease,
    transform 160ms ease;
}
.file-card:hover .file-cover-prompt,
.file-card:focus-visible .file-cover-prompt {
  opacity: 1;
  transform: translate(-50%, 0);
}
.file-card-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 17px 17px 12px;
}
.file-card-body h3 {
  display: -webkit-box;
  min-height: 43px;
  margin: 0;
  overflow: hidden;
  color: #1b2c45;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.file-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 9px 0 15px;
  color: #8a97a9;
  font-size: 12px;
}
.meta-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #b3becc;
}
.file-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
  padding-top: 15px;
  border-top: 1px solid #edf1f6;
  color: #5e4fc4;
  font-size: 12px;
  font-weight: 600;
}
.file-card-bottom > span,
.file-card-bottom a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.file-card-bottom a {
  padding: 3px 0;
  color: #758aa6;
  text-decoration: none;
}
.file-card-bottom a:hover {
  color: #5e4fc4;
}
.viewer-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  border: 1px dashed #d9e3ee;
  border-radius: 19px;
  color: #8a9bb0;
  background: #fff;
  text-align: center;
}
.viewer-empty-icon {
  display: grid;
  place-items: center;
  width: 76px;
  height: 76px;
  border-radius: 22px;
  color: #7da7ca;
  background: #edf5fb;
}
.viewer-empty h3 {
  margin: 17px 0 5px;
  color: #3b526d;
  font-size: 16px;
}
.viewer-empty p {
  margin: 0;
  font-size: 13px;
}
.viewer-empty button {
  margin-top: 18px;
  padding: 9px 16px;
  border: 0;
  border-radius: 9px;
  color: #fff;
  background: #6251d5;
  cursor: pointer;
}
.viewer-load-more {
  display: flex;
  justify-content: center;
  margin-top: 34px;
}
.viewer-load-more button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 12px 22px;
  border: 1px solid #dedaf6;
  border-radius: 999px;
  color: #5e4fc4;
  background: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(73, 58, 147, 0.06);
}
.viewer-load-more button:hover:not(:disabled) {
  border-color: #b9b0ed;
  background: #f8f7ff;
}
.viewer-load-more button:disabled {
  opacity: 0.6;
  cursor: wait;
}
.viewer-end {
  margin-top: 28px;
  color: #a0adbd;
  font-size: 12px;
  text-align: center;
}
.file-preview-dialog {
  position: relative;
  width: min(96vw, 1600px);
  height: min(92vh, 1050px);
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 28px 80px rgba(10, 25, 47, 0.34);
}
.file-preview-frame {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  border-radius: 18px;
  background: #f5f7fa;
}
.file-preview-close {
  position: absolute;
  z-index: 2;
  top: -15px;
  right: -15px;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 2px solid #fff;
  border-radius: 50%;
  color: #fff;
  background: #263d5b;
  box-shadow: 0 5px 15px rgba(11, 29, 56, 0.25);
  cursor: pointer;
}
@media (max-width: 720px) {
  .file-viewer-page {
    padding: 12px 12px 34px;
  }
  .viewer-workspace {
    border-radius: 15px;
  }
  .viewer-hero-content {
    padding: 21px 18px 19px;
  }
  .viewer-hero h1 {
    font-size: 20px;
  }
  .viewer-search {
    height: 46px;
    margin-top: 18px;
    padding-left: 12px;
  }
  .viewer-search-submit {
    height: 34px;
    padding: 0 12px;
  }
  .viewer-filters {
    gap: 6px;
  }
  .viewer-filters button {
    padding: 6px 11px;
  }
  .viewer-gallery {
    padding: 20px 18px 24px;
  }
  .gallery-heading {
    align-items: flex-start;
    margin-bottom: 17px;
  }
  .gallery-heading h2 {
    font-size: 18px;
  }
  .file-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 11px;
  }
  .file-card {
    border-radius: 13px;
  }
  .file-card-body {
    padding: 12px 11px 8px;
  }
  .file-card-body h3 {
    font-size: 12px;
    min-height: 38px;
  }
  .file-card-meta {
    flex-wrap: wrap;
    gap: 5px;
    margin-bottom: 10px;
    font-size: 11px;
  }
  .file-card-bottom {
    padding-top: 10px;
    font-size: 11px;
  }
  .file-cover-placeholder {
    width: 72px;
    height: 72px;
    border-radius: 17px;
  }
  .file-cover-placeholder :deep(.n-icon) {
    font-size: 40px !important;
  }
  .file-preview-dialog {
    width: 96vw;
    height: 90vh;
  }
}
@media (max-width: 430px) {
  .file-grid {
    grid-template-columns: 1fr;
  }
  .file-cover {
    aspect-ratio: 16 / 10;
  }
  .gallery-heading p {
    max-width: 210px;
  }
}
@media (hover: none) {
  .file-cover-prompt {
    opacity: 1;
    transform: translate(-50%, 0);
  }
}
</style>

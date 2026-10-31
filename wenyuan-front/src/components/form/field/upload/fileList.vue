<template>
  <div>
    <slot :fileList="fileList">
      <ul v-if="readOnlyMode" class="readonly-attachments">
        <li v-for="(item, index) in fileList" :key="item.id || `${index}:${item.path}`" class="readonly-attachment">
          <div v-if="!downloadOnly && isImage(item) && (!explicitFiles || item.originalUrl)" class="readonly-attachment-thumbnail">
            <ApiImage v-if="explicitFiles" :src="item.originalUrl!" :width="120" :height="100"
              object-fit="contain" :alt="item.name" :revision="imageRevision" />
            <CurrentFileImage v-else :src="fileUrl(item.url)" :alt="item.name" :width="120" :height="100"
              padding :revision="imageRevision" />
          </div>
          <div v-if="!downloadOnly" class="readonly-attachment-text" :title="item.id ? `文件编号：${item.id}` : undefined">
            <n-ellipsis :tooltip="true">{{ item.name }}</n-ellipsis>
            <n-ellipsis v-if="item.path" :tooltip="true" class="readonly-attachment-path">{{ item.path }}</n-ellipsis>
            <span v-if="explicitFiles && !item.originalUrl" role="status">原件读取入口不可用</span>
          </div>
          <n-button class="readonly-attachment-download" size="small" :disabled="!eligible || (explicitFiles && !item.originalUrl) || downloading"
            @click="handleDownload(item)">下载原件</n-button>
        </li>
      </ul>
      <template v-else-if="type === 'timeline'">
        <n-timeline>
          <n-timeline-item
            v-for="(item, index) in fileList"
            :key="index"
            :type="index == 0 ? 'info' : 'success'"
            :time="item.createTime"
          >
            <template #icon>
              <n-icon>
                <HIcon :icon="fileIcon(item.url)"></HIcon>
              </n-icon>
            </template>
            <div class="flex items-center">
              <n-element
                tag="span"
                style="color: var(--primary-color); transition: 0.3s var(--cubic-bezier-ease-in-out)"
                @click="handlePreview(item)"
              >
                {{ item.name }}
              </n-element>
              <a v-if="showDownloadBtn" href="#" style="text-decoration: none" @click.prevent="handleDownload(item)">
                <n-icon size="18" :depth="3" class="ml-3">
                  <IconMdiDownload />
                </n-icon>
              </a>
            </div>
          </n-timeline-item>
        </n-timeline>
      </template>
      <template v-else-if="type === 'image'">
        <n-space>
          <CurrentFileImage
            v-for="(item, index) in imageFiles"
            :key="item.id || item.url"
            class="attachment-image attachment-image--small"
            :src="fileUrl(item.url)"
            :alt="item.name"
            :width="200"
            padding
            :revision="imageRevision"
            preview-disabled
            group-preview
            @activate="openImage(index)"
          />
        </n-space>
      </template>
      <template v-else-if="type === 'carousel'">
        <n-carousel
          v-if="imageFiles.length"
          class="attachment-carousel"
          v-model:current-index="selectedImage"
          :autoplay="!imagePreviewShow"
          dot-type="line"
          effect="fade"
        >
          <div v-for="(item, index) in imageFiles" :key="item.id || item.url">
            <CurrentFileImage
              v-if="index === selectedImage"
              class="attachment-image"
              :src="fileUrl(item.url)"
              :alt="item.name"
              :width="416"
              :height="280"
              :revision="imageRevision"
              preview-disabled
              group-preview
              @activate="openImage(index)"
            />
          </div>
        </n-carousel>
      </template>
      <template v-else>
        <n-flex>
          <div v-for="(item, index) in imageFiles" :key="item.id || item.url">
            <CurrentFileImage
              class="attachment-image attachment-image--normal"
              :src="fileUrl(item.url)"
              :alt="item.name"
              :width="240"
              :height="200"
              padding
              :revision="imageRevision"
              preview-disabled
              group-preview
              @activate="openImage(index)"
            />
            <div class="text-center" style="max-width: 120px" v-if="showFileTitle">
              <n-ellipsis>{{ item.name?.split('.')[0] }}</n-ellipsis>
            </div>
          </div>
        </n-flex>
        <n-list>
          <n-list-item v-for="item in otherFiles" :key="item.id || item.url">
            <n-element
              :href="item.url"
              @click="handlePreview(item)"
              style="
                color: var(--primary-color);
                transition: 0.3s var(--cubic-bezier-ease-in-out);
                display: flex;
                align-items: center;
              "
            >
              <n-icon>
                <HIcon :icon="fileIcon(item.url)"></HIcon>
              </n-icon>
              {{ item.name }}
            </n-element>
            <template #suffix v-if="showDownloadBtn">
              <a href="#" style="display: flex; justify-items: center" @click.prevent="handleDownload(item)">
                <n-icon size="18" :depth="3" class="ml-3">
                  <IconMdiDownload />
                </n-icon>
              </a>
            </template>
          </n-list-item>
        </n-list>
      </template>
      <CurrentImagePreviewGroup
        v-if="!readOnlyMode"
        v-model:show="imagePreviewShow"
        v-model:current="selectedImage"
        :items="imageItems"
        :revision="props.contentId"
        @denied="imageRevision++"
      />
      <FileDialogPreview
        v-if="previewDialog"
        v-model:show="previewDialog"
        :title="previewTitle"
        :path="activePath"
      ></FileDialogPreview>
    </slot>
    <div v-if="downloading" role="status">正在下载附件</div>
    <div v-if="loading" role="status">正在读取附件</div>
    <div v-else-if="error" role="alert">{{ error }} <n-button @click="refresh">重新读取附件</n-button></div>
    <n-empty v-else-if="fileList.length === 0" description="暂无文件"></n-empty>
  </div>
</template>
<script lang="ts" setup>
import { listContentFiles, readFileMetadata } from '@/modules/sys/api/file';
import { computed, ref, watch, type PropType } from 'vue';
import ApiImage from '@/components/basic/ApiImage.vue';
import { attachmentIsImage, fileReferences, normalizeDisplayAttachment, type HFileListFile, type DisplayAttachment } from './fileList.types';
import CurrentFileImage from '@/components/basic/CurrentFileImage.vue';
import CurrentImagePreviewGroup from '@/components/basic/CurrentImagePreviewGroup.vue';
import { isApplicationApiImageUrl, useCurrentImageLifecycle } from '@/utils/currentImage';
import { ajaxGetDownload, apiUrl, fileUrl, originalFileUrl } from '@/utils';
import dayjs from 'dayjs';

const props = defineProps({
  value: {
    type: [String, Array] as PropType<string | readonly string[] | null>,
  },
  files: { type: Array as PropType<readonly HFileListFile[]> },
  originalUrl: { type: Function as PropType<(fileId: string) => string | undefined> },
  revision: { type: null as unknown as PropType<unknown> },
  contentId: { type: String },
  type: {
    type: String,
  },
  imageWidth: { type: Number, default: 200 },
  imageHeight: { type: Number, default: 200 },
  showFileTitle: { type: Boolean, default: true },
  showDownloadBtn: { type: Boolean, default: false },
  downloadOnly: { type: Boolean, default: false },
});

const explicitFiles = computed(() => props.files !== undefined);
const readOnlyMode = computed(() => explicitFiles.value || props.value !== undefined);
const fileList = ref<DisplayAttachment[]>([]);
const loading = ref(false),
  error = ref('');
const emits = defineEmits(['loadFileList']);
const previewDialog = ref(false),
  imagePreviewShow = ref(false);
const activePath = ref<string>();
const previewTitle = ref('查看文件');
const selectedImage = ref(0),
  imageRevision = ref(0);
let generation = 0;
let abort: AbortController | undefined;
let downloadAbort: AbortController | undefined;
const downloading = ref(false);

function extension(value: string) {
  return value.split(/[?#]/)[0].split('.').pop()?.toLowerCase() || '';
}
function isImage(item: DisplayAttachment) { return attachmentIsImage(item); }
function scopedOriginal(fileId: string): string | undefined {
  if (!fileId || !props.originalUrl) return undefined;
  try {
    const target = props.originalUrl(fileId)?.trim();
    if (!target) return undefined;
    const resolved = isApplicationApiImageUrl(target) ? new URL(target, window.location.href).href : apiUrl(target);
    return isApplicationApiImageUrl(resolved) ? resolved : undefined;
  } catch { return undefined; }
}
const imageFiles = computed(() => fileList.value.filter(isImage));
const otherFiles = computed(() => fileList.value.filter((item) => !isImage(item)));
const imageItems = computed(() =>
  imageFiles.value.map((item) => ({ id: item.id || item.url, src: fileUrl(item.url) })),
);
function fileIcon(url: string) {
  const ext = extension(url);
  if (ext === 'pdf') return 'c:file-pdf';
  if (/^(jpg|jpeg|png|gif|webp)$/.test(ext)) return 'c:file-image';
  if (/^(mp4|mov)$/.test(ext)) return 'c:file-video';
  if (/^(xls|xlsx)$/.test(ext)) return 'c:file-excel';
  if (/^(doc|docx)$/.test(ext)) return 'c:file-word';
  return 'c:file';
}
function closePreviews() {
  previewDialog.value = false;
  imagePreviewShow.value = false;
  activePath.value = undefined;
  selectedImage.value = 0;
}
function invalidate() {
  generation++;
  downloadAbort?.abort();
  downloadAbort = undefined;
  downloading.value = false;
  abort?.abort();
  abort = undefined;
  closePreviews();
  imageRevision.value++;
  fileList.value = [];
  emits('loadFileList', []);
  loading.value = false;
  error.value = '';
}
async function fetchFiles() {
  if (explicitFiles.value) {
    fileList.value = (props.files || []).map((item, index) => {
      const entry = normalizeDisplayAttachment(item, index);
      return { ...entry, originalUrl: scopedOriginal(entry.id) };
    });
    emits('loadFileList', fileList.value);
    return;
  }
  const valueMode = props.value !== undefined;
  const references = valueMode ? fileReferences(props.value) : [];
  if ((valueMode && !references.length) || (!valueMode && !props.contentId)) return;
  const ticket = generation;
  abort = new AbortController();
  loading.value = true;
  try {
    const resp = valueMode ? await readFileMetadata(references, abort.signal) : await listContentFiles(props.contentId!, abort.signal);
    if (ticket !== generation) return;
    const list = valueMode ? resp?.data : resp?.data?.list;
    if ((valueMode && !Array.isArray(list)) || (list != null && !Array.isArray(list))) throw new Error('附件列表格式错误');
    fileList.value = (list || []).map((item: any, index: number) => {
      const display = normalizeDisplayAttachment(item, index);
      if (valueMode && !display.url) throw new Error('附件元数据缺少读取身份');
      return { ...display, url: valueMode ? display.url : item.path || item.id,
        createTime: item.createTime ? dayjs(item.createTime).format('YYYY-MM-DD HH:mm:ss') : '' };
    });
    emits('loadFileList', fileList.value);
  } catch {
    if (ticket === generation) error.value = '附件读取失败，请核对当前授权和文件状态。';
  } finally {
    if (ticket === generation) {
      loading.value = false;
      abort = undefined;
    }
  }
}
const { refresh, eligible } = useCurrentImageLifecycle(
  () => [props.files, props.value, props.contentId, props.originalUrl, props.revision],
  invalidate,
  () => void fetchFiles(),
);
watch(() => props.type, closePreviews, { flush: 'sync' });
function openImage(index: number) {
  if (!imageFiles.value[index]) return;
  selectedImage.value = index;
  imagePreviewShow.value = true;
}
function handlePreview(item: DisplayAttachment) {
  if (isImage(item)) {
    openImage(imageFiles.value.indexOf(item));
  } else {
    previewDialog.value = true;
    activePath.value = item.url;
    previewTitle.value = item.name || '查看文件';
  }
}

async function handleDownload(item: DisplayAttachment) {
  const target = explicitFiles.value ? item.originalUrl : originalFileUrl(item.url);
  if (!eligible.value || downloadAbort || !fileList.value.includes(item) || !target) return;
  const ticket = generation,
    controller = new AbortController();
  downloadAbort = controller;
  downloading.value = true;
  try {
    await ajaxGetDownload(target, item.name || '下载', undefined, {
      signal: controller.signal,
      preferServerFilename: explicitFiles.value,
      isCurrent: () => ticket === generation && eligible.value && fileList.value.includes(item),
    });
  } finally {
    if (downloadAbort === controller) {
      downloadAbort = undefined;
      downloading.value = false;
    }
  }
}
</script>

<style scoped>
.readonly-attachments { list-style: none; padding: 0; margin: 0; display: grid; grid-template-columns: minmax(0, 1fr); gap: 12px; min-width: 0; max-width: 100%; }
.readonly-attachment { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; min-width: 0; }
.readonly-attachment-text { flex: 1 1 180px; min-width: 0; max-width: 100%; display: grid; grid-template-columns: minmax(0, 1fr); gap: 4px; }
.readonly-attachment-text :deep(.n-ellipsis) { min-width: 0; max-width: 100%; }
.readonly-attachment-path { font-size: 12px; opacity: .7; }
.readonly-attachment-thumbnail { flex: 0 1 120px; width: 120px; max-width: 100%; min-height: 100px; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.readonly-attachment-thumbnail :deep(.n-image), .readonly-attachment-thumbnail :deep(img) { max-width: 100%; max-height: 100px; object-fit: contain; }
.readonly-attachment-thumbnail :deep(.current-remote-image-status) { flex-wrap: wrap; justify-content: center; max-width: 100%; }
.readonly-attachment-download { flex: 0 1 auto; max-width: 100%; min-width: 0; height: auto; min-height: 28px; }
.readonly-attachment-download :deep(.n-button__content) { white-space: normal; overflow-wrap: anywhere; }
.attachment-carousel {
  height: 320px;
}
.attachment-image--small {
  width: 100px;
}
.attachment-image--normal {
  width: 120px;
}
.attachment-image :deep(.n-image),
.attachment-image :deep(img) {
  max-width: 100%;
}
</style>

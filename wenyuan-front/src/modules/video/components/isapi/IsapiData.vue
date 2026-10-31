<template>
  <n-space vertical size="large">
    <n-grid :cols="24" :x-gap="16" :y-gap="16" responsive="screen" item-responsive>
      <n-grid-item span="24 l:14">
        <n-card title="设备图像" size="small" class="panel">
          <template #header-extra>
            <n-button v-if="canEdit" :loading="capturing" @click="capture">获取新图像</n-button>
          </template>
          <n-alert v-if="captureNotice" type="info" style="margin-bottom: 12px">{{ captureNotice }}</n-alert>
          <n-spin :show="imageLoading">
            <div class="picture">
              <n-image v-if="imageUrl" :src="imageUrl" object-fit="contain" alt="设备采集图像" />
              <n-empty v-else :description="imageError || '尚未获取图像，可手动获取或等待定时抓图'" />
            </div>
          </n-spin>
          <n-alert v-if="imageError" type="warning" style="margin-top: 12px">{{ imageError }}</n-alert>
          <n-space v-if="selectedImage" align="center" style="margin-top: 12px">
            <n-tag size="small">{{ imageSourceLabel[selectedImage.source] || selectedImage.source }}</n-tag>
            <span>{{ formatTime(selectedImage.capturedAt) }}</span>
            <span>{{ selectedImage.width }} × {{ selectedImage.height }}</span>
          </n-space>
          <n-p v-if="selectedImage?.source === 'CHANNEL_SNAPSHOT'" depth="3">
            通道当前帧用于查看现场，与监测样本不保证同帧。
          </n-p>
          <n-scrollbar x-scrollable style="margin-top: 12px">
            <n-space :wrap="false">
              <n-button
                v-for="image in images"
                :key="image.id"
                size="small"
                :type="image.id === selectedImage?.id ? 'primary' : 'default'"
                @click="viewImage(image)"
              >
                {{ formatTime(image.capturedAt) }}
              </n-button>
            </n-space>
          </n-scrollbar>
        </n-card>
      </n-grid-item>
      <n-grid-item span="24 l:10">
        <n-card title="最近采集值" size="small" class="panel">
          <template #header-extra>
            <n-space align="center">
              <n-switch v-model:value="autoRefresh" size="small">
                <template #checked>自动刷新</template><template #unchecked>暂停刷新</template>
              </n-switch>
              <n-button size="small" :loading="loading" @click="refresh">刷新</n-button>
            </n-space>
          </template>
          <n-alert v-if="error" type="warning" style="margin-bottom: 12px">{{ error }}</n-alert>
          <n-data-table
            :columns="columns"
            :data="preview.samples"
            :loading="loading"
            :scroll-x="415"
            :row-key="(row: BridgeSample) => row.targetId"
          />
          <n-p depth="3">最近接收：{{ formatTime(preview.receivedAt) }}。仅展示每个标靶最新的原始值，单位 mm。</n-p>
        </n-card>
      </n-grid-item>
    </n-grid>
  </n-space>
</template>

<script lang="ts" setup>
import { computed, h, onMounted, onUnmounted, ref, watch } from 'vue';
import type { DataTableColumns } from 'naive-ui';
import { useAuthStore } from '@/store';
import { captureDevice, dataPreview, formatTime, imageBlob, imageSourceLabel, listImages } from '../../api/isapi';
import type { BridgeSample, DataPreview, IsapiImage } from '../../api/isapi';

const props = defineProps<{ deviceId: string }>();
const auth = useAuthStore();
const canEdit = computed(() => auth.hasPermission('VIDEO_ISAPI_ALL,VIDEO_ISAPI_EDIT'));
const preview = ref<DataPreview>({ sampleCount: 0, samples: [] });
const images = ref<IsapiImage[]>([]);
const selectedImage = ref<IsapiImage>();
const imageUrl = ref('');
const loading = ref(false);
const capturing = ref(false);
const imageLoading = ref(false);
const error = ref('');
const imageError = ref('');
const captureNotice = ref('');
const capturePendingAt = ref(0);
const autoRefresh = ref(true);
const columns: DataTableColumns<BridgeSample> = [
  { title: '标靶', key: 'targetId', width: 65 },
  { title: 'X 位移', key: 'x', width: 100, render: (row) => (row.x == null ? '—' : row.x.toFixed(2)) },
  { title: 'Y 位移', key: 'y', width: 100, render: (row) => (row.y == null ? '—' : row.y.toFixed(2)) },
  {
    title: '样本时间',
    key: 'dateTime',
    width: 170,
    render: (row) => {
      const date = new Date(row.dateTime);
      return h(
        'span',
        { style: 'white-space: pre-line' },
        `${date.toLocaleDateString('zh-CN')}\n${date.toLocaleTimeString('zh-CN', { hour12: false })}.${String(date.getMilliseconds()).padStart(3, '0')}`,
      );
    },
  },
];
let revision = 0;
let disposed = false;
let timer: ReturnType<typeof setInterval> | undefined;
let controller: AbortController | undefined;

async function selectImage(image: IsapiImage) {
  controller?.abort();
  const active = new AbortController();
  controller = active;
  const device = props.deviceId;
  imageLoading.value = true;
  try {
    const blob = await imageBlob(image.id, active.signal);
    if (disposed || active !== controller || device !== props.deviceId) return;
    if (imageUrl.value) URL.revokeObjectURL(imageUrl.value);
    imageUrl.value = URL.createObjectURL(blob);
    selectedImage.value = image;
    imageError.value = '';
    if (capturePendingAt.value > 0 && image.capturedAt === capturePendingAt.value) {
      captureNotice.value = '';
      capturePendingAt.value = 0;
    }
  } catch {
    if (!active.signal.aborted && active === controller) imageError.value = '图像读取失败，请检查存储及访问权限';
  } finally {
    if (active === controller) imageLoading.value = false;
  }
}
async function refresh() {
  if (loading.value || !props.deviceId) return;
  const id = props.deviceId;
  const requestId = ++revision;
  loading.value = true;
  try {
    const [values, pictures] = await Promise.all([dataPreview(id), listImages(id)]);
    if (disposed || requestId !== revision || id !== props.deviceId) return;
    preview.value = values;
    images.value = pictures;
    error.value = '';
    if (pictures[0] && (!selectedImage.value || (autoRefresh.value && pictures[0].id !== selectedImage.value.id))) {
      await selectImage(pictures[0]);
    }
  } catch {
    if (requestId === revision) error.value = '最近采集值读取失败，请检查服务及采集存储';
  } finally {
    if (requestId === revision) loading.value = false;
  }
}
function viewImage(image: IsapiImage) {
  autoRefresh.value = false;
  selectImage(image);
}
async function capture() {
  const id = props.deviceId;
  if (!id) return;
  capturing.value = true;
  try {
    const result = await captureDevice(id);
    if (disposed || id !== props.deviceId) return;
    capturePendingAt.value = result.imageIds?.length ? 0 : result.receivedAt;
    captureNotice.value = result.imageIds?.length ? '' : result.imageWarning || '已触发抓拍，等待设备上传图像';
    const pictures = await listImages(id);
    if (disposed || id !== props.deviceId) return;
    images.value = pictures;
    if (pictures[0]) await selectImage(pictures[0]);
  } catch {
    /* 请求层展示获取失败。 */
  } finally {
    capturing.value = false;
  }
}
watch(
  () => props.deviceId,
  () => {
    revision++;
    loading.value = false;
    preview.value = { sampleCount: 0, samples: [] };
    images.value = [];
    selectedImage.value = undefined;
    error.value = '';
    imageError.value = '';
    captureNotice.value = '';
    capturePendingAt.value = 0;
    autoRefresh.value = true;
    controller?.abort();
    if (imageUrl.value) URL.revokeObjectURL(imageUrl.value);
    imageUrl.value = '';
    refresh();
  },
  { immediate: true },
);
onMounted(() => {
  timer = setInterval(() => {
    if (!document.hidden && autoRefresh.value) refresh();
  }, 5000);
});
onUnmounted(() => {
  disposed = true;
  revision++;
  if (timer) clearInterval(timer);
  controller?.abort();
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value);
});
</script>

<style scoped>
.panel {
  height: 100%;
}
.picture {
  min-height: 300px;
  height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #101827;
  border-radius: 6px;
  overflow: hidden;
}
.picture :deep(.n-image) {
  height: 100%;
  width: 100%;
  justify-content: center;
}
.picture :deep(img) {
  max-height: 100%;
  max-width: 100%;
  object-fit: contain;
}
</style>

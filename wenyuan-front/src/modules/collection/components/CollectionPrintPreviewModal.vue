<template>
  <NModal
    :show="visible"
    preset="card"
    :title="modalTitle"
    class="collection-print-preview-modal"
    @update:show="emitShow"
  >
    <div class="print-preview-summary" v-if="visible && primaryPreviewPrint">
      <NDescriptions label-placement="left" :column="2" bordered size="small">
        <NDescriptionsItem label="标题">{{ primaryPreviewPrint.title || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="单号">{{ primaryPreviewPrint.documentNo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="收藏单位">{{ primaryPreviewPrint.orgName || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="生成时间">{{
          formatPrintDateTime(primaryPreviewPrint.generatedAt) || '--'
        }}</NDescriptionsItem>
      </NDescriptions>
    </div>
    <div class="print-preview-frame">
      <NSpin v-if="loading" description="正在生成打印预览，大型表单约需十余秒，请稍候…" />
      <CurrentPdfReader
        v-else-if="preview"
        :key="preview.id"
        :src="preview.url"
        class="print-preview-pdf"
        v-on="{ error: preview.fail }"
      />
      <div v-else-if="previewError" role="alert">
        {{ previewError }} <NButton :disabled="busy" @click="refresh">重新生成预览</NButton>
      </div>
      <NEmpty v-else description="暂无可预览的打印数据" />
    </div>
    <iframe
      v-if="printJob"
      :key="printJob.id"
      :src="printJob.url"
      title="准备打印 PDF"
      class="print-job-frame"
      v-on="{ load: printJob.start, error: printJob.fail }"
    />
    <template #footer>
      <NSpace justify="end">
        <NButton secondary :disabled="!canOperate" :loading="action === 'PDF'" @click="downloadFile('PDF')"
          >下载 PDF</NButton
        >
        <NButton secondary :disabled="!canOperate" :loading="action === 'DOCX'" @click="downloadFile('DOCX')"
          >下载 Word</NButton
        >
        <NButton type="primary" :disabled="!canOperate" :loading="action === 'PRINT'" @click="printPdf">打印</NButton>
        <NButton v-if="action === 'PRINT'" @click="finishPrint">结束打印准备</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
<script setup lang="ts">
import CurrentPdfReader from '@/components/basic/pdf/CurrentPdfReader.vue';
import { saveAs } from 'file-saver';
import { computed, ref, watch } from 'vue';
import { NButton, useMessage } from 'naive-ui';
import { useCurrentImageLifecycle } from '@/utils/currentImage';
import { collectionPrintApi } from '../api/print';
import type { CollectionPrintVo } from '../typings/collection';
import { formatPrintDateTime } from '../utils/printCollectionList';
import { printErrorText } from '../utils/printError';
import {
  buildCollectionPrintBatchFileName,
  buildCollectionPrintFileName,
  buildCollectionPrintRenderRequest,
  collectionPrintDirectFilePath,
  type CollectionPrintFileFormat,
} from '../utils/printFile';
const props = defineProps<{
  show: boolean;
  data?: CollectionPrintVo | null;
  items?: CollectionPrintVo[];
  type?: string;
  id?: string;
  ids?: string[];
  title?: string;
  documentNo?: string;
}>();
const emit = defineEmits<{ (event: 'update:show', value: boolean): void }>();
const message = useMessage();
const loading = ref(false),
  previewError = ref(''),
  dismissed = ref(false);
const action = ref<'PDF' | 'DOCX' | 'PRINT' | null>(null);
const preview = ref<{ id: number; url: string; fail: () => void }>();
const printJob = ref<{ id: number; url: string; start: (event: Event) => void; fail: () => void }>();
let generation = 0;
let previewAbort: AbortController | undefined, actionAbort: AbortController | undefined;
let previewUrl = '',
  printUrl = '';
let detachPrint: (() => void) | undefined;
const primaryPrint = computed(() => props.data || props.items?.[0] || null);
const cleanId = computed(() => String(props.id || '').trim());
const cleanIds = computed(() => (props.ids || []).map((id) => String(id || '').trim()).filter(Boolean));
const directPrintData = computed<CollectionPrintVo | null>(() =>
  !props.type || !cleanId.value
    ? null
    : {
        type: props.type,
        title: props.title || '',
        businessId: cleanId.value,
        documentNo: props.documentNo || cleanId.value,
        fields: [],
        collections: [],
      },
);
const primaryPreviewPrint = computed(() => primaryPrint.value || directPrintData.value);
const canRender = computed(() =>
  Boolean(primaryPrint.value || directPrintData.value || (props.type && cleanIds.value.length)),
);
const modalTitle = computed(
  () => props.title || primaryPreviewPrint.value?.template?.title || primaryPreviewPrint.value?.title || '打印预览',
);
const busy = computed(() => action.value !== null);
watch(
  () => props.show,
  (show) => {
    if (show) dismissed.value = false;
  },
  { flush: 'sync' },
);
const { eligible, refresh } = useCurrentImageLifecycle(
  () => [props.show, props.data, props.items, props.type, props.id, props.ids, props.title, props.documentNo],
  invalidate,
  () => {
    void loadPreview();
  },
);
const visible = computed(() => props.show && eligible.value && !dismissed.value);
const canOperate = computed(() => visible.value && canRender.value && !busy.value && !loading.value);
function clearPreview() {
  preview.value = undefined;
  if (previewUrl) URL.revokeObjectURL(previewUrl);
  previewUrl = '';
}
function finishPrint() {
  // 同时取消尚在读取的打印准备；已交给操作系统的打印任务不由网页撤回。
  actionAbort?.abort();
  actionAbort = undefined;
  detachPrint?.();
  detachPrint = undefined;
  printJob.value = undefined;
  if (printUrl) URL.revokeObjectURL(printUrl);
  printUrl = '';
  if (action.value === 'PRINT') action.value = null;
}
function invalidate() {
  generation++;
  previewAbort?.abort();
  previewAbort = undefined;
  actionAbort?.abort();
  actionAbort = undefined;
  clearPreview();
  finishPrint();
  loading.value = false;
  previewError.value = '';
  action.value = null;
}
function emitShow(value: boolean) {
  if (!value) {
    dismissed.value = true;
    invalidate();
  }
  emit('update:show', value);
}
function readFile(format: CollectionPrintFileFormat, signal: AbortSignal) {
  const path = collectionPrintDirectFilePath(
    {
      data: props.data,
      items: props.items,
      type: props.type,
      id: cleanId.value || undefined,
      ids: cleanIds.value,
      title: props.title,
      documentNo: props.documentNo,
    },
    format,
  );
  if (path) return collectionPrintApi.fileBlob(path, signal);
  return collectionPrintApi.renderBlob(
    buildCollectionPrintRenderRequest({
      data: props.data,
      items: props.items,
      type: props.type,
      id: cleanId.value || undefined,
      ids: cleanIds.value,
    }),
    format,
    signal,
  );
}
async function loadPreview() {
  // visible 在挂载钩子执行前已完成初始化。
  if (!visible.value || !canRender.value) return;
  const ticket = generation,
    controller = new AbortController();
  previewAbort = controller;
  loading.value = true;
  const fail = (reason?: string) => {
    if (ticket !== generation) return;
    clearPreview();
    previewError.value = reason ? `打印预览读取失败：${reason}` : '打印预览读取失败，请核对当前授权与数据后重试。';
  };
  try {
    const blob = await readFile('PDF', controller.signal);
    if (ticket !== generation || controller.signal.aborted) return;
    previewUrl = URL.createObjectURL(blob);
    preview.value = { id: ticket, url: previewUrl, fail: () => fail() };
  } catch (error) {
    if (!controller.signal.aborted) fail(await printErrorText(error));
  } finally {
    if (ticket === generation) {
      loading.value = false;
      previewAbort = undefined;
    }
  }
}
function fileName(format: CollectionPrintFileFormat) {
  return primaryPreviewPrint.value
    ? buildCollectionPrintFileName(primaryPreviewPrint.value, format)
    : buildCollectionPrintBatchFileName(
        props.type || 'PRINT',
        cleanIds.value.length || props.items?.length || 1,
        format,
      );
}
async function downloadFile(format: CollectionPrintFileFormat) {
  if (!canOperate.value) return;
  const ticket = generation,
    controller = new AbortController(),
    name = fileName(format);
  actionAbort = controller;
  action.value = format;
  try {
    const blob = await readFile(format, controller.signal);
    if (ticket === generation && !controller.signal.aborted && visible.value) saveAs(blob, name);
  } catch (error: any) {
    if (ticket === generation && !controller.signal.aborted) message.error(await printErrorText(error, '打印文件下载失败'));
  } finally {
    if (ticket === generation && actionAbort === controller) {
      actionAbort = undefined;
      action.value = null;
    }
  }
}
async function printPdf() {
  if (!canOperate.value) return;
  const ticket = generation,
    controller = new AbortController();
  actionAbort = controller;
  action.value = 'PRINT';
  const current = () => ticket === generation && !controller.signal.aborted && visible.value;
  const fail = () => {
    if (!current()) return;
    finishPrint();
    message.error('打印窗口打开失败，请核对浏览器支持后重试。');
  };
  try {
    const blob = await readFile('PDF', controller.signal);
    if (!current()) return;
    printUrl = URL.createObjectURL(blob);
    let started = false;
    printJob.value = {
      id: ticket,
      url: printUrl,
      fail,
      start: (event: Event) => {
        if (!current() || started) return;
        started = true;
        const frame = event.target as HTMLIFrameElement,
          target = frame.contentWindow;
        if (!target) {
          fail();
          return;
        }
        const complete = () => {
          if (current()) finishPrint();
        };
        try {
          window.addEventListener('afterprint', complete);
          detachPrint = () => {
            window.removeEventListener('afterprint', complete);
            try {
              target.removeEventListener('afterprint', complete);
            } catch {
              /* PDF 阅读器可能已离开同源框架。 */
            }
          };
          target.addEventListener('afterprint', complete);
          target.focus();
          target.print();
        } catch {
          fail();
        }
      },
    };
  } catch (error: any) {
    if (current()) {
      finishPrint();
      message.error(await printErrorText(error, '打印文件生成失败'));
    }
  }
}
</script>
<style scoped>
.print-job-frame {
  position: fixed;
  width: 1px;
  height: 1px;
  opacity: 0;
  border: 0;
  pointer-events: none;
}

.print-preview-summary {
  margin-bottom: 10px;
}

.print-preview-frame {
  position: relative;
  height: min(68vh, 720px);
  min-height: 420px;
  overflow: auto;
  border: 1px solid #d6e0ea;
  background: #f6f8fb;
}

.print-preview-frame :deep(.n-spin-container),
.print-preview-pdf {
  min-height: 100%;
}

:global(.collection-print-preview-modal) {
  width: min(1080px, calc(100vw - 32px));
}
</style>

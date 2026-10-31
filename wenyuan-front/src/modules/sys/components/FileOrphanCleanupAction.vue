<template>
  <n-tooltip trigger="hover" placement="top">
    <template #trigger>
      <n-button type="warning" :loading="previewLoading" aria-label="检查无效文件" @click="handlePreview">
        <template #icon>
          <n-icon :size="18">
            <HIcon icon="mdi:file-search-outline" />
          </n-icon>
        </template>
      </n-button>
    </template>
    检查无效文件
  </n-tooltip>

  <n-modal
    :show="confirmVisible"
    preset="card"
    :title="previewResult?.action === 'QUARANTINE' ? '隔离处理结果' : '确认隔离候选文件'"
    style="width: min(860px, calc(100vw - 32px))"
    :mask-closable="!quarantineLoading"
    :close-on-esc="!quarantineLoading"
    :closable="!quarantineLoading"
    @update:show="handleModalVisibility"
  >
    <n-scrollbar style="max-height: min(68vh, 640px)">
      <div v-if="previewResult" class="orphan-cleanup-content">
        <n-alert
          v-if="previewResult.action !== 'QUARANTINE' && previewResult.candidateCount > 0"
          type="warning"
          title="请核对候选文件后再继续"
          :bordered="false"
        >
          确认后会重新扫描，仅隔离没有文件记录、不受业务原件保护且 {{ previewResult.minimumAgeHours }}
          小时未变化的文件；不会直接删除，并会保留相对路径、生成 manifest.csv 清单。
        </n-alert>
        <n-alert
          v-else-if="previewResult.action === 'QUARANTINE'"
          :type="previewResult.failedCount ? 'warning' : 'info'"
          title="请核对处理结果"
          :bordered="false"
        >
          已移动 {{ previewResult.movedCount }} 个文件；因引用或原件保护跳过
          {{ previewResult.skippedReferencedCount }} 个，因文件变化跳过 {{ previewResult.skippedChangedCount }} 个，失败
          {{ previewResult.failedCount }} 个。
          <p v-if="previewResult.manifestPath">隔离清单：{{ previewResult.manifestPath }}</p>
        </n-alert>
        <n-empty
          v-else
          :description="
            previewResult.failedCount ? '尚有路径未完成核对，不能确认全部文件状态' : '本次没有可隔离的候选文件'
          "
        />

        <div class="orphan-cleanup-summary" aria-label="扫描统计">
          <div class="orphan-cleanup-summary__item">
            <n-text depth="3">已扫描</n-text>
            <n-text strong class="orphan-cleanup-summary__value">{{ previewResult.scannedFileCount }} 个</n-text>
          </div>
          <div class="orphan-cleanup-summary__item">
            <n-text depth="3">候选文件</n-text>
            <n-text strong class="orphan-cleanup-summary__value">{{ previewResult.candidateCount }} 个</n-text>
          </div>
          <div class="orphan-cleanup-summary__item">
            <n-text depth="3">候选大小</n-text>
            <n-text strong class="orphan-cleanup-summary__value">
              {{ formatFileSize(previewResult.candidateBytes) }}
            </n-text>
          </div>
          <div class="orphan-cleanup-summary__item">
            <n-text depth="3">最小年龄</n-text>
            <n-text strong class="orphan-cleanup-summary__value">{{ previewResult.minimumAgeHours }} 小时</n-text>
          </div>
        </div>

        <n-alert v-if="previewResult.failedCount > 0" type="error" title="部分路径未完成核对" :bordered="false">
          有
          {{ previewResult.failedCount }}
          项未完成读取、原件身份或隔离清单核对。本次结果不完整，请结合已移动数量、隔离目录和下列原因逐项核对。
          <ul v-if="previewResult.failures.length">
            <li v-for="(failure, index) in previewResult.failures" :key="index">
              {{ failure.relativePath || '存储目录' }}：{{ failure.message }}
            </li>
          </ul>
        </n-alert>

        <section
          v-if="previewResult.action !== 'QUARANTINE' && previewResult.candidates.length > 0"
          class="orphan-cleanup-list"
        >
          <div class="orphan-cleanup-list__header">
            <n-text strong>候选文件明细</n-text>
            <n-text depth="3"
              >显示 {{ previewResult.candidates.length }} / 共 {{ previewResult.candidateCount }} 项</n-text
            >
          </div>
          <n-data-table
            :columns="columns"
            :data="previewResult.candidates"
            :row-key="(row: FileOrphanCandidate) => row.relativePath"
            :pagination="false"
            :max-height="360"
            size="small"
          />
          <n-text v-if="previewResult.previewTruncated" class="orphan-cleanup-list__hint" depth="3">
            候选文件较多，弹窗仅展示前 {{ previewResult.candidates.length }} 项，确认后仍会按相同规则重新扫描。
          </n-text>
        </section>

        <n-alert v-if="executionError" type="error" title="未能确认隔离结果" :bordered="false">
          {{ executionError }}
        </n-alert>
      </div>
    </n-scrollbar>

    <template #footer>
      <n-space justify="end">
        <n-button :disabled="quarantineLoading" @click="closeModal">
          {{ previewResult?.action !== 'QUARANTINE' && previewResult?.candidateCount ? '取消' : '关闭' }}
        </n-button>
        <n-button
          v-if="previewResult?.action !== 'QUARANTINE' && previewResult?.candidateCount"
          type="warning"
          :loading="quarantineLoading"
          @click="handleQuarantine"
        >
          移入隔离目录
        </n-button>
      </n-space>
    </template>
  </n-modal>

  <HFileDialogPreview
    v-model:show="candidatePreviewVisible"
    :title="candidatePreviewTitle"
    :path="candidatePreviewPath"
    width="min(960px, calc(100vw - 32px))"
  />
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue';
import type { DataTableColumns } from 'naive-ui';
import { NAlert, NButton, NDataTable, NEmpty, NIcon, NModal, NScrollbar, NSpace, NText, NTooltip } from 'naive-ui';
import HIcon from '@/components/basic/DynamicIcon.vue';
import HFileDialogPreview from '@/components/basic/FileDialogPreview.vue';
import { formatDateTime } from '@/utils/datetime';
import {
  previewOrphanFiles,
  quarantineOrphanFiles,
  type FileOrphanCandidate,
  type FileOrphanCleanupRequest,
  type FileOrphanCleanupResult,
} from '../api/file';

const emit = defineEmits<{
  completed: [result: FileOrphanCleanupResult];
}>();

const scanRequest: FileOrphanCleanupRequest = {
  minimumAgeHours: 24,
  maxPreviewItems: 200,
};

const previewLoading = ref(false);
const quarantineLoading = ref(false);
const confirmVisible = ref(false);
const previewResult = ref<FileOrphanCleanupResult>();
const executionError = ref('');
const candidatePreviewVisible = ref(false);
const candidatePreviewPath = ref('');
const candidatePreviewTitle = ref('候选文件预览');

const columns = computed<DataTableColumns<FileOrphanCandidate>>(() => [
  {
    title: '相对路径',
    key: 'relativePath',
    ellipsis: { tooltip: true },
    minWidth: 320,
  },
  {
    title: '大小',
    key: 'size',
    align: 'right',
    width: 110,
    render: (row) => formatFileSize(row.size),
  },
  {
    title: '最后修改时间',
    key: 'lastModifiedAt',
    width: 170,
    render: (row) => formatDateTime(row.lastModifiedAt),
  },
  {
    title: '操作',
    key: 'preview',
    align: 'center',
    width: 64,
    render: (row) =>
      h(
        NTooltip,
        { trigger: 'hover', placement: 'top' },
        {
          trigger: () =>
            h(
              NButton,
              {
                type: 'primary',
                quaternary: true,
                circle: true,
                size: 'small',
                'aria-label': '预览候选文件',
                onClick: () => handleCandidatePreview(row),
              },
              {
                icon: () => h(NIcon, { size: 18 }, () => h(HIcon, { icon: 'mdi:eye-outline' })),
              },
            ),
          default: () => '预览文件',
        },
      ),
  },
]);

function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / 1024 ** index;
  return `${value >= 10 || index === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[index]}`;
}

function handleCandidatePreview(candidate: FileOrphanCandidate) {
  candidatePreviewPath.value = candidate.relativePath;
  const fileName = candidate.relativePath.split('/').pop() || candidate.relativePath;
  candidatePreviewTitle.value = `候选文件预览：${fileName}`;
  candidatePreviewVisible.value = true;
}

async function handlePreview() {
  if (previewLoading.value || quarantineLoading.value) return;
  previewLoading.value = true;
  executionError.value = '';
  candidatePreviewVisible.value = false;
  try {
    const response = await previewOrphanFiles(scanRequest);
    previewResult.value = response.data;
    confirmVisible.value = true;
  } catch {
    previewResult.value = undefined;
    confirmVisible.value = false;
  } finally {
    previewLoading.value = false;
  }
}

async function handleQuarantine() {
  if (quarantineLoading.value || previewResult.value?.action === 'QUARANTINE' || !previewResult.value?.candidateCount)
    return;
  quarantineLoading.value = true;
  executionError.value = '';
  try {
    const response = await quarantineOrphanFiles({ ...scanRequest, confirm: true });
    previewResult.value = response.data;
    candidatePreviewVisible.value = false;
    emit('completed', response.data);
  } catch {
    executionError.value = '请求未完成，无法判断文件是否已移动。请重新预览当前状态，核对后再重试。';
  } finally {
    quarantineLoading.value = false;
  }
}

function handleModalVisibility(show: boolean) {
  if (!show) closeModal();
}

function closeModal() {
  if (quarantineLoading.value) return;
  confirmVisible.value = false;
  candidatePreviewVisible.value = false;
  executionError.value = '';
}
</script>

<style scoped>
.orphan-cleanup-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-right: 12px;
}

.orphan-cleanup-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 20px;
  padding: 2px 0;
}

.orphan-cleanup-summary__item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.orphan-cleanup-summary__value {
  white-space: nowrap;
}

.orphan-cleanup-list {
  min-width: 0;
}

.orphan-cleanup-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.orphan-cleanup-list__hint {
  display: block;
  margin-top: 8px;
}

@media (max-width: 640px) {
  .orphan-cleanup-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

<template>
  <n-drawer
    v-model:show="innerShow"
    :width="standalone ? '100%' : 820"
    placement="right"
    :mask-closable="!standalone"
    :close-on-esc="!standalone"
  >
    <n-drawer-content
      :title="data?.name || '资源预览'"
      :closable="!standalone"
      :header-style="standalone ? { display: 'none' } : undefined"
      :body-content-style="standalone ? { padding: '0' } : undefined"
    >
      <n-spin :show="loading">
        <!-- 抽屉内返回按钮（从关联资源跳转后显示，位于 Tab 上方） -->
        <div v-if="!standalone && navStack.length" class="nav-back">
          <n-button text size="small" type="primary" @click="navigateBack">
            <template #icon><n-icon><IconMdiArrowLeft /></n-icon></template>
            返回上一个资源
          </n-button>
        </div>

        <n-tabs
          v-model:value="activeTab"
          type="line"
          size="small"
          :animated="!standalone"
          class="preview-tabs"
          :class="{ 'preview-tabs--standalone': standalone }"
        >

          <!-- ─── Tab 1：预览 ─────────────────────────────────── -->
          <n-tab-pane name="preview" tab="预览" display-directive="show">
            <div ref="previewShellRef" class="preview-shell" :class="{ 'preview-shell--standalone': standalone }">
              <div ref="toolbarRef" class="preview-toolbar" role="toolbar" aria-label="文件预览工具栏" :style="toolbarStyle">
                <n-tooltip trigger="hover" :to="isFullscreen ? (previewShellRef || undefined) : undefined">
                  <template #trigger>
                    <button
                      type="button"
                      class="preview-drag-handle"
                      aria-label="拖动工具栏，或使用方向键移动"
                      @mousedown="startToolbarMouseDrag"
                      @pointerdown="startToolbarDrag"
                      @pointermove="moveToolbarDrag"
                      @pointerup="stopToolbarDrag"
                      @pointercancel="stopToolbarDrag"
                      @lostpointercapture="stopToolbarDrag"
                      @keydown="moveToolbarWithKeyboard"
                    ><n-icon size="18"><IconMdiDrag /></n-icon></button>
                  </template>
                  拖动工具栏 · 方向键微调
                </n-tooltip>
                <div class="preview-file-summary">
                  <n-icon size="17" class="preview-file-symbol"><IconMdiFileOutline /></n-icon>
                  <span class="preview-file-name" :title="data?.name">{{ previewFileName }}</span>
                  <span v-if="previewFileSize" class="preview-file-size">{{ formatSize(previewFileSize) }}</span>
                </div>
                <div class="preview-action-dock">
                  <n-tooltip v-if="canSaveView" trigger="hover" :to="isFullscreen ? (previewShellRef || undefined) : undefined">
                    <template #trigger>
                      <button type="button" class="preview-tool-button" :disabled="savingView" aria-label="保存当前视角" @click="handleSaveView">
                        <n-icon size="19" :class="{ 'preview-icon-spin': savingView }"><IconMdiLoading v-if="savingView" /><IconMdiContentSave v-else /></n-icon>
                      </button>
                    </template>
                    保存当前视角
                  </n-tooltip>
                  <n-tooltip v-if="is3dKind" trigger="hover" :to="isFullscreen ? (previewShellRef || undefined) : undefined">
                    <template #trigger>
                      <span class="preview-tool-trigger">
                        <button type="button" class="preview-tool-button" :disabled="!hasSavedView" aria-label="回到保存视角" @click="handleRestoreView">
                          <n-icon size="19"><IconMdiRestore /></n-icon>
                        </button>
                      </span>
                    </template>
                    {{ hasSavedView ? '回到保存视角' : '尚未保存视角' }}
                  </n-tooltip>
                  <n-tooltip v-if="canScreenshot" trigger="hover" :to="isFullscreen ? (previewShellRef || undefined) : undefined">
                    <template #trigger>
                      <button type="button" class="preview-tool-button" aria-label="截图" @click="handleScreenshot">
                        <n-icon size="19"><IconMdiCamera /></n-icon>
                      </button>
                    </template>
                    截图
                  </n-tooltip>
                  <span v-if="canSaveView || is3dKind || canScreenshot" class="preview-tool-divider" aria-hidden="true" />
                  <n-tooltip v-if="originUrl" trigger="hover" :to="isFullscreen ? (previewShellRef || undefined) : undefined">
                    <template #trigger>
                      <button type="button" class="preview-tool-button preview-tool-button--label" :disabled="downloading" aria-label="下载原文件" @click="handleDownload">
                        <n-icon size="19" :class="{ 'preview-icon-spin': downloading }"><IconMdiLoading v-if="downloading" /><IconMdiDownload v-else /></n-icon><span>下载原文件</span>
                      </button>
                    </template>
                    下载原文件
                  </n-tooltip>
                  <button v-if="packageFiles.length > 1" type="button" class="preview-tool-button preview-tool-button--label" :disabled="downloading" @click="handleDownloadPackage">下载资源包 ZIP</button>
                  <n-tooltip v-if="!standalone && standalonePreviewUrl" trigger="hover" :to="isFullscreen ? (previewShellRef || undefined) : undefined">
                    <template #trigger>
                      <a class="preview-tool-button" :href="standalonePreviewUrl" target="_blank" rel="noopener noreferrer" aria-label="新窗口预览">
                        <n-icon size="19"><IconMdiOpenInNew /></n-icon>
                      </a>
                    </template>
                    新窗口预览
                  </n-tooltip>
                  <span class="preview-tool-divider" aria-hidden="true" />
                  <n-tooltip trigger="hover" :to="isFullscreen ? (previewShellRef || undefined) : undefined">
                    <template #trigger>
                      <button type="button" class="preview-tool-button" :class="{ 'preview-tool-button--active': isFullscreen }" :aria-label="isFullscreen ? '退出全屏' : '全屏'" :aria-pressed="isFullscreen" @click="toggleFullscreen">
                        <n-icon size="19"><IconMdiFullscreenExit v-if="isFullscreen" /><IconMdiFullscreen v-else /></n-icon>
                      </button>
                    </template>
                    {{ isFullscreen ? '退出全屏' : '全屏' }}
                  </n-tooltip>
                  <n-popover trigger="click" placement="bottom-end" :width="280" :to="isFullscreen ? (previewShellRef || undefined) : undefined">
                    <template #trigger>
                      <button type="button" class="preview-tool-button" aria-label="查看帮助">
                        <n-icon size="19"><IconMdiHelpCircleOutline /></n-icon>
                      </button>
                    </template>
                    <div class="preview-help-title">{{ helpTitle }}</div>
                    <div class="preview-help-text">{{ helpText }}</div>
                    <div v-if="is3dKind" class="preview-help-text">调整到合适位置后可保存视角；下次打开默认使用该位置。</div>
                    <div v-if="!canScreenshot" class="preview-help-text">此格式由浏览器或外部查看器绘制，暂不支持页面内截图。</div>
                  </n-popover>
                </div>
              </div>
              <div class="preview-main">
              <!-- 文件路径缺失提示 -->
              <n-alert
                v-if="!loading && data && !originUrl"
                type="warning"
                title="文件不可访问"
                class="mb-3"
              >
                该资源未关联到有效的文件记录，无法预览。请尝试上传新版本以恢复文件。
              </n-alert>

              <!-- 图片（含 tif/psd/tga，后端 ImageIO 自动转 JPEG 输出）-->
              <ResourcePanorama v-if="kind === 'PANORAMA' && originUrl" :src="originUrl" />
              <div v-else-if="kind === 'IMAGE' && originUrl" class="img-wrap">
                <n-image
                  :src="imageUrl"
                  :preview-src="imageUrl"
                  object-fit="contain"
                  class="preview-image"
                />
              </div>

              <!-- 视频（浏览器原生支持的格式）-->
              <video
                v-else-if="kind === 'VIDEO' && originUrl"
                :src="originUrl"
                controls
                preload="metadata"
                class="w-full bg-black rounded preview-video"
              />

              <!-- 音频 -->
              <div
                v-else-if="kind === 'AUDIO' && originUrl"
                class="flex flex-col items-center p-6 bg-gray-50 rounded"
              >
                <n-icon size="64" depth="3"><IconMdiMusicCircle /></n-icon>
                <div class="mt-3 text-gray-500">{{ data?.name }}</div>
                <audio :src="originUrl" controls preload="metadata" class="mt-3 w-full" />
              </div>

              <!-- PDF：iframe 直显 -->
              <iframe
                v-else-if="kind === 'PDF' && originUrl"
                :src="originUrl"
                class="w-full rounded border-0 preview-iframe"
              />

              <!-- XLSX：@vue-office/excel（前端直渲，无需后端转换）-->
              <VueOfficeExcel
                v-else-if="kind === 'XLSX' && originUrl"
                :src="originUrl"
                :key="`xlsx-${originUrl}`"
                :requestOptions="officeReqOptions"
                style="min-height: 600px"
              />

              <!--
                ===== 需后端转换预览块（DOC_CONVERT：caj/doc/docx/rtf/odt/pptx/ppt/xls）=====
                流程：
                  1. 打开时先查询 /file/preview/cached → 判断是否有缓存
                  2. 缓存命中 → convertState = 'loading'（快速）
                  3. 缓存未命中 → convertState = 'converting'（慢速，首次转换）
                  4. 不支持或检测失败 → convertState = 'unsupported'，引导下载原文件
                  5. iframe @load 触发 → convertState = 'ready'，隐藏覆盖层
              -->
              <div
                v-else-if="isConversionKind && docPreviewUrl"
                class="doc-preview-wrap"
              >
                <!-- 转换状态覆盖层：ready 之前始终显示 -->
                <div v-if="convertState !== 'ready'" class="convert-overlay">
                  <n-spin size="large" />
                  <div class="mt-5 text-base font-medium text-gray-700">{{ convertMsg }}</div>
                  <template v-if="convertState === 'converting'">
                    <n-progress
                      class="mt-4 convert-progress"
                      type="line"
                      :percentage="convertProgress"
                      :indicator-placement="'inside'"
                      processing
                    />
                    <div class="mt-3 text-sm text-gray-400 text-center">
                      首次预览需要服务端转换，{{ convertWorkLabel }}可能需要数十秒<br />
                      转换完成后将缓存到磁盘，下次打开即时加载，无需等待
                    </div>
                  </template>
                  <div v-else-if="convertState === 'loading'" class="mt-2 text-sm text-gray-400">
                    已命中缓存，正在加载文档…
                  </div>
                  <div v-else-if="convertState === 'unsupported'" class="mt-3 text-sm text-gray-400 text-center">
                    {{ unsupportedPreviewMessage }}
                  </div>
                </div>

                <!-- iframe：始终挂载（保证转换期间 HTTP 连接不中断），ready 前隐藏 -->
                <iframe
                  v-if="convertState !== 'unsupported'"
                  :src="docPreviewUrl"
                  class="w-full rounded border-0 preview-iframe"
                  :style="{ visibility: convertState === 'ready' ? 'visible' : 'hidden' }"
                  @load="onIframeLoad"
                />
              </div>

              <!-- 纯文本 / 结构化文本：前端 fetch 后 <pre> 展示 -->
              <TextViewer
                v-else-if="kind === 'TEXT' && originUrl"
                :src="originUrl"
                :ext="fileExt"
              />

              <!-- Marmoset Viewer 自包含场景 -->
              <div v-else-if="kind === 'MVIEW' && originUrl" class="viewer-3d-wrap">
                <MarmosetViewer :src="originUrl" />
              </div>

              <!-- 3D Gaussian Splat PLY：由后端读取文件头识别，避免和普通 PLY 网格混淆 -->
              <div v-else-if="kind === 'GAUSSIAN_SPLAT' && originUrl" class="viewer-3d-wrap">
                <GaussianSplatViewer
                  ref="gaussianViewerRef"
                  v-if="innerShow"
                  :src="originUrl"
                  :file-size="Number(data?.fileSize || 0)"
                  :descriptor="data?.preview"
                  :preview-config="data?.previewConfig"
                />
              </div>

              <!-- 3D Gaussian Splat SOG：单文件、目录版或 Streamed SOG，由 PlayCanvas 渲染 -->
              <div v-else-if="(kind === 'SOG_SPLAT' || kind === 'STREAMED_SOG') && originUrl" class="viewer-3d-wrap">
                <SogSplatViewer
                  ref="sogViewerRef"
                  v-if="innerShow"
                  :src="originUrl"
                  :ext="fileExt"
                  :file-size="previewFileSize"
                  :descriptor="data?.preview"
                  :package-files="data?.files || []"
                  :resource-id="(data?.files?.length || 0) <= 1 ? String(data?.id || '') : ''"
                  :resource-version="Number(data?.currentVersion || 0)"
                  :resource-status="String(data?.status || '')"
                  :streamed="kind === 'STREAMED_SOG'"
                  :preview-config="data?.previewConfig"
                />
              </div>

              <!-- 3D：three.js 直渲（stl/obj/glb/gltf/ply/fbx）-->
              <div v-else-if="kind === 'MODEL_3D_VIEWABLE' && originUrl" class="viewer-3d-wrap">
                <ThreeViewer
                  ref="threeViewerRef"
                  :src="originUrl"
                  :ext="fileExt"
                  :package-files="data?.files || []"
                  :preview-config="data?.previewConfig"
                />
              </div>

              <!-- 3D Tiles：tileset.json 入口（3d-tiles-renderer）-->
              <div v-else-if="kind === 'TILES_3D' && originUrl" class="viewer-3d-wrap">
                <Tiles3DViewer
                  ref="tilesViewerRef"
                  :src="originUrl"
                  :package-files="data?.files || []"
                  :preview-config="data?.previewConfig"
                />
              </div>

              <!-- 3D：非浏览器可渲格式（osgb/dae/pmx/3ds 等） -->
              <div v-else-if="kind === 'MODEL_3D_DOWNLOAD'" class="notice-box">
                <n-icon size="64" depth="3"><IconMdiCubeOutline /></n-icon>
                <div class="mt-3">3D 模型（{{ fileExt || '未知' }} 格式）</div>
                <div class="mt-1 text-xs text-gray-400">
                  该格式暂不支持浏览器在线预览，请下载后使用 Blender / MeshLab / CesiumLab 等专业软件打开<br />
                  如需在线查看，可将 3D Tiles 格式（tileset.json）上传以支持浏览器渲染
                </div>
              </div>

              <!-- 视频：非浏览器原生格式（avi/wmv/mkv/flv 等） -->
              <div v-else-if="kind === 'VIDEO_DOWNLOAD'" class="notice-box">
                <n-icon size="64" depth="3"><IconMdiFileVideoOutline /></n-icon>
                <div class="mt-3">视频文件（{{ fileExt || '未知' }} 格式）</div>
                <div class="mt-1 text-xs text-gray-400">
                  浏览器不支持在线播放该格式，请下载后使用 VLC 等播放器查看
                </div>
              </div>

              <!-- 影像原始格式（raw/cr2/nef/arw 等） -->
              <div v-else-if="kind === 'RAW_IMAGE'" class="notice-box">
                <n-icon size="64" depth="3"><IconMdiImageOffOutline /></n-icon>
                <div class="mt-3">相机原始影像（{{ fileExt || '未知' }} 格式）</div>
                <div class="mt-1 text-xs text-gray-400">
                  需使用 Photoshop / Lightroom / digiKam 等软件处理
                </div>
              </div>

              <!-- 其他/未知 -->
              <div v-else-if="originUrl" class="notice-box">
                <n-icon size="64" depth="3"><IconMdiFileQuestionOutline /></n-icon>
                <div class="mt-2 text-gray-500">该类型暂不支持在线预览</div>
                <div class="mt-1 text-xs text-gray-400">请使用上方"下载原文件"按钮</div>
              </div>
              </div>
              </div>
          </n-tab-pane>

          <!-- ─── Tab 2：元信息 ───────────────────────────────── -->
          <n-tab-pane v-if="!standalone" name="info" tab="元信息">
            <n-descriptions :column="2" size="small" bordered label-placement="left">
              <n-descriptions-item label="名称">{{ data?.name || '-' }}</n-descriptions-item>
              <n-descriptions-item label="编号">{{ data?.code || '-' }}</n-descriptions-item>
              <n-descriptions-item label="类型">
                {{ RESOURCE_TYPE_OPTIONS.find(o => o.value === data?.resourceType)?.label || data?.resourceType || '-' }}
              </n-descriptions-item>
              <n-descriptions-item label="预览类型">
                {{ RESOURCE_PREVIEW_TYPE_OPTIONS.find(o => o.value === data?.previewType)?.label || data?.previewType || '自动识别' }}
              </n-descriptions-item>
              <n-descriptions-item label="状态">
                <n-tag size="small" :type="RESOURCE_STATUS_TYPE_MAP[data?.status] || 'default'" :bordered="false">
                  {{ RESOURCE_STATUS_OPTIONS.find(o => o.value === data?.status)?.label || data?.status }}
                </n-tag>
              </n-descriptions-item>
              <n-descriptions-item label="当前版本">v{{ data?.currentVersion ?? 1 }}</n-descriptions-item>
              <n-descriptions-item label="文件大小">{{ formatSize(previewFileSize) }}</n-descriptions-item>
              <n-descriptions-item label="扩展名">{{ data?.fileExt || '-' }}</n-descriptions-item>
              <n-descriptions-item label="浏览/下载">
                {{ data?.viewCount ?? 0 }} / {{ data?.downloadCount ?? 0 }}
              </n-descriptions-item>
              <n-descriptions-item label="文件路径" :span="2">
                <span class="break-all text-xs text-gray-500">{{ originUrl || '（无）' }}</span>
              </n-descriptions-item>
              <n-descriptions-item label="标签" :span="2">
                <n-space size="small">
                  <n-tag v-for="t in tagList" :key="t" size="small" :bordered="false">{{ t }}</n-tag>
                  <span v-if="!tagList.length" class="text-gray-400 text-xs">无</span>
                </n-space>
              </n-descriptions-item>
              <n-descriptions-item label="描述" :span="2">{{ data?.description || '-' }}</n-descriptions-item>
            </n-descriptions>

            <template v-if="metadataEntries.length">
              <n-divider title-placement="left">扩展元数据</n-divider>
              <div class="metadata-field-list">
                <div v-for="item in metadataEntries" :key="item.key" class="metadata-field">
                  <span class="metadata-label">{{ item.label }}</span>
                  <span class="metadata-value">{{ item.value }}</span>
                </div>
              </div>
            </template>
          </n-tab-pane>

          <n-tab-pane v-if="!standalone && packageFiles.length && (kind !== 'STREAMED_SOG' || packageFiles.length > 1)" name="packageFiles">
            <template #tab>
              <span>包内文件</span>
              <n-badge
                :value="packageFiles.length"
                :max="99"
                type="info"
                :offset="[8, -2]"
                style="margin-left: 6px"
              />
            </template>
            <div class="package-file-list">
              <div
                v-for="item in visiblePackageFiles"
                :key="item.relativePath || item.fileId"
                class="package-file-row"
                :class="{ 'is-main': item.role === 'MAIN' }"
              >
                <n-tag size="small" :type="item.role === 'MAIN' ? 'success' : 'default'" :bordered="false">
                  {{ item.role || 'AUX' }}
                </n-tag>
                <div class="package-file-info">
                  <div class="package-file-path">{{ item.relativePath || item.originalName }}</div>
                  <div class="package-file-meta">
                    <span>{{ item.fileExt || '-' }}</span>
                    <span>{{ formatSize(Number(item.fileSize || 0)) }}</span>
                  </div>
                </div>
              </div>
              <div v-if="packageFiles.length > visiblePackageFiles.length" class="package-file-truncated">
                包内共有 {{ packageFiles.length }} 个文件，仅展示前 {{ visiblePackageFiles.length }} 项；
                全部文件仍参与资源包预览、下载和完整性校验。
              </div>
            </div>
          </n-tab-pane>

          <!-- ─── Tab 3：关联资源（带数量角标）─────────────────── -->
          <n-tab-pane v-if="!standalone && (kind !== 'STREAMED_SOG' || packageFiles.length > 1)" name="relations">
            <template #tab>
              <span>关联资源</span>
              <n-badge
                v-if="relations.length"
                :value="relations.length"
                :max="99"
                type="info"
                :offset="[8, -2]"
                style="margin-left: 6px"
              />
            </template>

            <n-empty v-if="!loading && !relationGroupEntries.length" description="暂无关联资源" class="py-10" />

            <div v-for="[type, items] in relationGroupEntries" :key="type" class="relation-group">
              <div class="relation-type-label">{{ RELATION_TYPE_LABEL[type] }}</div>
              <div class="relation-cards">
                <div
                  v-for="item in items"
                  :key="item.id"
                  class="relation-card"
                  :title="item.description || item.targetName"
                  @click="navigateTo(item.targetId)"
                >
                  <div class="relation-thumb">
                    <n-image
                      v-if="item.targetThumbnailPath"
                      :src="thumbOf(item.targetThumbnailPath)"
                      :preview-disabled="true"
                      object-fit="cover"
                      class="w-full h-full"
                    />
                    <div v-else class="relation-thumb-empty">
                      <n-icon size="18" depth="3">
                        <component :is="iconOf(item.targetResourceType)" />
                      </n-icon>
                    </div>
                  </div>
                  <div class="relation-info">
                    <div class="relation-name">{{ item.targetName }}</div>
                    <div v-if="item.description" class="relation-desc">{{ item.description }}</div>
                  </div>
                </div>
              </div>
            </div>
          </n-tab-pane>

          <n-tab-pane v-if="!standalone && showFlowLog" name="flow" tab="审批进度">
            <ResourceApprovalProgress v-if="currentDisplayId" :business-id="currentDisplayId" business-type="RESOURCE_IMPORT" :fetch-flow="fetchFlowInfo" />
          </n-tab-pane>

        </n-tabs>
      </n-spin>
    </n-drawer-content>
  </n-drawer>
</template>

<script lang="ts" setup>
import { getAuthenticationHeaders } from '@/utils/auth';
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue';
import { ajaxGetDownload } from '@/utils/download';
import ResourcePanorama from './ResourcePanorama.vue';
import ResourceApprovalProgress from './ResourceApprovalProgress.vue';
import html2canvas from 'html2canvas';
import { useMessage } from 'naive-ui';
import { useRouter } from 'vue-router';
import { fileUrl, apiUrl } from '@/utils/path';
import { useAuthStore } from '@/store';
import request from '@/utils/request';
import VueOfficeExcel from '@vue-office/excel/lib/v3/index.js';
import '@vue-office/excel/lib/v3/index.css';
import { get, getPreview, streamedSogFileUrl, savePreviewView, recordView, recordDownload, flowInfo as fetchFlowInfo, RESOURCE_TYPE_OPTIONS, RESOURCE_PREVIEW_TYPE_OPTIONS, RESOURCE_STATUS_OPTIONS, RESOURCE_STATUS_TYPE_MAP } from '../api/resource';
import { configuredCameraView, type ResourcePreviewConfig } from '../utils/previewConfig';
import { formatSize } from '../api/stat';
import TaskHistoryTimeline from '../../flow/components/task/TaskHistoryTimeline.vue';
import ThreeViewer from './ThreeViewer.vue';
import Tiles3DViewer from './Tiles3DViewer.vue';
import MarmosetViewer from './MarmosetViewer.vue';
import GaussianSplatViewer from './GaussianSplatViewer.vue';
import SogSplatViewer from './SogSplatViewer.vue';
import TextViewer from './TextViewer.vue';

const authStore = useAuthStore();
const router = useRouter();
const message = useMessage();
// @vue-office 组件拉取原文件时使用的 headers（带 token，保证鉴权通过）
const officeReqOptions = computed(() => {
  return { headers: getAuthenticationHeaders(authStore.getToken, authStore.authorizationName), credentials: 'include' as const };
});

const props = defineProps({
  show: { type: Boolean, default: false },
  resourceId: { type: String, default: '' },
  showFlowLog: { type: Boolean, default: false },
  standalone: { type: Boolean, default: false },
});
const emit = defineEmits(['update:show']);

const innerShow = computed({
  get: () => props.show,
  set: (v) => emit('update:show', v),
});

// ===== 资源数据加载 =====
const data = ref<any>(null);
const loading = ref(false);
const flowLoading = ref(false);
const flowData = ref<any>(null);

// ===== 关联资源 & 抽屉内导航 =====
const relations = ref<any[]>([]);
const navStack = ref<string[]>([]);   // 历史 ID 栈，支持"返回上一个"
const currentDisplayId = ref('');    // 当前实际展示的资源 ID（与 props.resourceId 可能不同）
const activeTab = ref('preview');    // 当前激活的 Tab
const flowLogs = computed(() => flowData.value?.history || []);
const standalonePreviewUrl = computed(() => {
  if (!currentDisplayId.value) return '';
  return router.resolve({
    path: `/resource-preview/${encodeURIComponent(currentDisplayId.value)}`,
  }).href;
});

const RELATION_TYPE_LABEL: Record<string, string> = {
  ATTACHMENT: '附件',
  DERIVED: '衍生资源',
  RELATED: '相关资源',
  REFERENCE: '引用资料',
};
const RELATION_TYPE_ORDER = ['ATTACHMENT', 'DERIVED', 'RELATED', 'REFERENCE'];

/** 按预定义顺序分组，过滤掉空组 */
const relationGroupEntries = computed<[string, any[]][]>(() => {
  const groups: Record<string, any[]> = {};
  for (const r of relations.value) {
    if (!groups[r.relationType]) groups[r.relationType] = [];
    groups[r.relationType].push(r);
  }
  return RELATION_TYPE_ORDER.filter((t) => groups[t]?.length).map((t) => [t, groups[t]]);
});

/** 关联卡片缩略图 URL（兼容绝对地址 / 相对路径） */
function thumbOf(p: string) {
  if (!p) return '';
  if (/^https?:\/\//i.test(p) || p.startsWith('/userfiles/')) return p;
  return fileUrl(p);
}

function downloadUrlOf(p: string) {
  if (!p) return '';
  if (/^https?:\/\//i.test(p) || p.startsWith('//')) return p;
  if (p.startsWith('/userfiles/')) return p;
  const frontPath = APP_API_FRONT_PATH.replace(/^\/?/, '/');
  const frontPathWithoutSlash = frontPath.slice(1);
  if (p.startsWith(frontPath) || p.startsWith(frontPathWithoutSlash)) {
    return `${APP_BASE_URL.replace(/\/$/, '')}/${p.replace(/^\//, '')}`;
  }
  return fileUrl(p);
}

/** 按资源类型返回图标名（供 <component :is> 自动识别） */
function iconOf(type: string) {
  switch (type) {
    case 'IMAGE':    return 'IconMdiFileImageOutline';
    case 'VIDEO':    return 'IconMdiFileVideoOutline';
    case 'AUDIO':    return 'IconMdiFileMusicOutline';
    case 'MODEL_3D': return 'IconMdiCubeOutline';
    case 'DOCUMENT': return 'IconMdiFileDocumentOutline';
    default:         return 'IconMdiFileQuestionOutline';
  }
}

/** 加载指定 ID 的资源详情 + 关联列表（同时切回预览 Tab） */
async function loadResource(id: string) {
  currentDisplayId.value = id;
  activeTab.value = 'preview';
  loading.value = true;
  data.value = null;
  flowData.value = null;
  relations.value = [];
  resetConvertState();
  try {
    const resp: any = await (props.showFlowLog ? get(id) : getPreview(id));
    data.value = resp.data || null;
    relations.value = data.value?.relations || [];
    // 前台浏览接口只接受“已发布且公开”的资源；受控资源的管理端预览不计入公开浏览量。
    if (data.value?.status === 'PUBLISHED' && data.value?.visibility === 'PUBLIC') {
      recordView(id).catch(() => {});
    }

  } finally {
    loading.value = false;
  }
}

async function loadFlowInfo(id: string) {
  flowLoading.value = true;
  try {
    const resp: any = await fetchFlowInfo(id);
    flowData.value = resp?.data || null;
  } catch {
    flowData.value = null;
  } finally {
    flowLoading.value = false;
  }
}

/** 抽屉内跳转到关联资源 */
function navigateTo(targetId: string) {
  if (!targetId) return;
  // 压入当前正在显示的 ID（而非 props.resourceId，避免多级跳转回退错误）
  navStack.value = [...navStack.value, currentDisplayId.value];
  loadResource(targetId);
}

/** 返回上一个资源 */
function navigateBack() {
  const stack = [...navStack.value];
  const prev = stack.pop();
  if (!prev) return;
  navStack.value = stack;
  loadResource(prev);
}

const tagList = computed(() =>
  (data.value?.tags || '').split(',').map((s: string) => s.trim()).filter(Boolean),
);
const metadataEntries = computed(() => parseMetadataEntries(data.value?.metadata));
const packageFiles = computed<any[]>(() => data.value?.files || []);
const visiblePackageFiles = computed(() => packageFiles.value.slice(0, 500));
const fileExt = computed<string>(() => (data.value?.fileExt || '').toLowerCase());

const originUrl = computed(() => {
  if (data.value?.preview?.kind === 'STREAMED_SOG' && (data.value?.files?.length || 0) <= 1 &&
      data.value?.id && data.value?.fileId) {
    return streamedSogFileUrl(
      String(data.value.id), String(data.value.fileId),
      Number(data.value.currentVersion || 0), String(data.value.status || ''),
    );
  }
  const p = data.value?.filePath;
  return fileUrl(p);
});

// 图像 URL：对 tif/psd/tga 加宽高参数强制走后端缩放管线输出 jpeg
const imageUrl = computed(() => {
  if (!originUrl.value) return '';
  if (['tif', 'tiff', 'psd', 'tga'].includes(fileExt.value)) {
    const sep = originUrl.value.includes('?') ? '&' : '?';
    return `${originUrl.value}${sep}w=1920&f`;
  }
  return originUrl.value;
});

// 后端转换输出格式：xls → html（表格），其余 → pdf
const docConvertFormat = computed(() => fileExt.value === 'xls' ? 'html' : 'pdf');

// 后端文档转换预览 URL
const docPreviewUrl = computed(() => {
  if (!data.value?.fileId) return '';
  return apiUrl(`file/preview?fileId=${data.value.fileId}&format=${docConvertFormat.value}`);
});

// ===== 渲染分类 =====
const IMAGE_EXT           = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg', 'ico', 'tif', 'tiff', 'psd', 'tga'];
const RAW_EXT             = ['raw', 'cr2', 'nef', 'arw', 'dng', 'orf', 'rw2', 'pef', 'srw'];
const VIDEO_EXT           = ['mp4', 'webm', 'ogv', 'mov', 'm4v'];
const VIDEO_DOWNLOAD_EXT  = ['avi', 'wmv', 'mkv', 'flv', 'rmvb', 'rm', 'ts', 'mpeg', 'mpg', '3gp'];
const AUDIO_EXT           = ['mp3', 'wav', 'ogg', 'oga', 'aac', 'flac', 'm4a', 'opus', 'weba'];
const MVIEW_EXT           = 'mview';
const MODEL_3D_VIEWABLE_EXT = ['stl', 'obj', 'glb', 'gltf', 'ply', 'fbx'];
const MODEL_3D_DOWNLOAD_EXT = ['osgb', 'osg', 'dae', 'pmx', '3ds', 'jd', 'ifc', 'step', 'stp', 'iges', 'igs'];
const TEXT_EXT = [
  'txt', 'md', 'markdown', 'csv', 'tsv', 'json', 'xml', 'yml', 'yaml',
  'log', 'ini', 'conf', 'properties', 'sql', 'js', 'ts', 'css', 'html', 'htm',
  'py', 'java', 'kt', 'go', 'rs', 'sh',
];
const XLSX_EXT = 'xlsx';
// 后端转换格式：
//   CajFileConverter:   caj              → PDF（外部命令）
//   WordFileConverter:  doc/docx/rtf/odt → PDF
//   PptxFileConverter:  pptx             → PDF
//   PptFileConverter:   ppt              → PDF（HSLFSlideShow，poi-scratchpad）
//   ExcelFileConverter: xls              → HTML
// 均走 /file/preview?fileId=&format=xxx → iframe 展示，支持磁盘缓存
const DOC_CONVERT_EXT = ['caj', 'doc', 'docx', 'rtf', 'odt', 'pptx', 'ppt', 'xls'];

const EXPLICIT_PREVIEW_KIND: Record<string, string> = {
  IMAGE: 'IMAGE',
  VIDEO: 'VIDEO',
  AUDIO: 'AUDIO',
  PDF: 'PDF',
  OFFICE_DOCUMENT: 'DOC_CONVERT',
  SPREADSHEET: 'XLSX',
  TEXT: 'TEXT',
  MVIEW: 'MVIEW',
  MODEL_3D: 'MODEL_3D_VIEWABLE',
  POINT_CLOUD: 'MODEL_3D_VIEWABLE',
  TILES_3D: 'TILES_3D',
  GAUSSIAN_SPLAT_PLY: 'GAUSSIAN_SPLAT',
  GAUSSIAN_SPLAT_SOG: 'SOG_SPLAT',
  GAUSSIAN_SPLAT_SOG_LOD: 'STREAMED_SOG',
  DOWNLOAD_ONLY: 'OTHER',
};

const mainPackagePath = computed(() => String(
  packageFiles.value.find((item: any) => item.role === 'MAIN')?.relativePath || '',
).replace(/\\/g, '/').toLowerCase());

const kind = computed(() => {
  const ext = fileExt.value;
  const t   = data.value?.resourceType;
  if (t === 'PANORAMA') return 'PANORAMA';
  const configured = String(data.value?.previewType || 'AUTO').toUpperCase();
  if (configured !== 'AUTO' && EXPLICIT_PREVIEW_KIND[configured]) {
    if (configured === 'SPREADSHEET' && ext === 'xls') return 'DOC_CONVERT';
    return EXPLICIT_PREVIEW_KIND[configured];
  }
  if (IMAGE_EXT.includes(ext))           return 'IMAGE';
  if (RAW_EXT.includes(ext))             return 'RAW_IMAGE';
  if (VIDEO_EXT.includes(ext))           return 'VIDEO';
  if (VIDEO_DOWNLOAD_EXT.includes(ext))  return 'VIDEO_DOWNLOAD';
  if (AUDIO_EXT.includes(ext))           return 'AUDIO';
  if (ext === MVIEW_EXT)                 return 'MVIEW';
  if (ext === 'ply' && data.value?.preview?.kind === 'GAUSSIAN_SPLAT') return 'GAUSSIAN_SPLAT';
  // AUTO 只用于兼容旧数据；SOG 目录版必须由后端内容契约确认，不再见到 meta.json 就推断。
  if (ext === 'sog' || data.value?.preview?.kind === 'SOG_SPLAT') return 'SOG_SPLAT';
  if (data.value?.preview?.kind === 'STREAMED_SOG' || data.value?.packageAuditSummary?.packageType === 'STREAMED_SOG') return 'STREAMED_SOG';
  if (MODEL_3D_VIEWABLE_EXT.includes(ext)) return 'MODEL_3D_VIEWABLE';
  if (MODEL_3D_DOWNLOAD_EXT.includes(ext)) return 'MODEL_3D_DOWNLOAD';
  if (data.value?.packageAuditSummary?.packageType === 'TILES_3D' || mainPackagePath.value.endsWith('/tileset.json') || mainPackagePath.value === 'tileset.json') return 'TILES_3D';
  if (ext === 'pdf')                    return 'PDF';
  if (ext === XLSX_EXT)                return 'XLSX';
  if (DOC_CONVERT_EXT.includes(ext))   return 'DOC_CONVERT'; // 后端转换 → iframe
  if (TEXT_EXT.includes(ext))          return 'TEXT';
  // 兜底：按 resourceType
  if (t === 'IMAGE')    return 'IMAGE';
  if (t === 'VIDEO')    return 'VIDEO_DOWNLOAD';
  if (t === 'AUDIO')    return 'AUDIO';
  if (t === 'MODEL_3D') return 'MODEL_3D_DOWNLOAD';
  return 'OTHER';
});

const previewFileSize = computed(() => {
  if ((kind.value === 'SOG_SPLAT' || kind.value === 'STREAMED_SOG') && packageFiles.value.length > 1) {
    return packageFiles.value.reduce((sum: number, file: any) => sum + Number(file.fileSize || 0), 0);
  }
  return Number(data.value?.fileSize || 0);
});

type CameraViewer = { captureView: () => ResourcePreviewConfig | null; restoreSavedView: () => void };
const threeViewerRef = ref<CameraViewer | null>(null);
const tilesViewerRef = ref<CameraViewer | null>(null);
const gaussianViewerRef = ref<CameraViewer | null>(null);
const sogViewerRef = ref<CameraViewer | null>(null);
const previewShellRef = ref<HTMLElement | null>(null);
const toolbarRef = ref<HTMLElement | null>(null);
const toolbarPosition = ref<{ left: number; top: number } | null>(null);
const toolbarStyle = computed(() => toolbarPosition.value
  ? { left: `${toolbarPosition.value.left}px`, top: `${toolbarPosition.value.top}px`, right: 'auto', bottom: 'auto', transform: 'none' }
  : undefined);
const previewFileName = computed(() => {
  const name = String(data.value?.name || '文件预览');
  const maxLength = 28;
  if (Array.from(name).length <= maxLength) return name;
  const dot = name.lastIndexOf('.');
  const extension = dot > 0 && name.length - dot <= 10 ? name.slice(dot) : '';
  return `${Array.from(name).slice(0, maxLength - extension.length - 1).join('')}…${extension}`;
});
type ToolbarDrag = { pointerId: number; startX: number; startY: number; left: number; top: number };
let toolbarDrag: ToolbarDrag | null = null;
let toolbarMouseDrag: Omit<ToolbarDrag, 'pointerId'> | null = null;

function boundedToolbarPosition(left: number, top: number) {
  const shell = previewShellRef.value;
  const toolbar = toolbarRef.value;
  if (!shell || !toolbar) return { left, top };
  return {
    left: Math.max(0, Math.min(Math.round(left), shell.clientWidth - toolbar.offsetWidth)),
    top: Math.max(0, Math.min(Math.round(top), shell.clientHeight - toolbar.offsetHeight)),
  };
}

function clampToolbarPosition() {
  if (!toolbarPosition.value) return;
  toolbarPosition.value = boundedToolbarPosition(toolbarPosition.value.left, toolbarPosition.value.top);
}

function currentToolbarPosition() {
  const shell = previewShellRef.value;
  const toolbar = toolbarRef.value;
  if (!shell || !toolbar) return null;
  const shellRect = shell.getBoundingClientRect();
  const toolbarRect = toolbar.getBoundingClientRect();
  return {
    left: toolbarRect.left - shellRect.left + shell.scrollLeft,
    top: toolbarRect.top - shellRect.top + shell.scrollTop,
  };
}

function startToolbarDrag(event: PointerEvent) {
  if (event.pointerType === 'mouse') return;
  const position = currentToolbarPosition();
  if (!position) return;
  event.preventDefault();
  toolbarDrag = { pointerId: event.pointerId, startX: event.clientX, startY: event.clientY, ...position };
  (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
}

function startToolbarMouseDrag(event: MouseEvent) {
  const position = currentToolbarPosition();
  if (event.button !== 0 || !position) return;
  event.preventDefault();
  toolbarMouseDrag = {
    startX: event.clientX,
    startY: event.clientY,
    ...position,
  };
  window.addEventListener('mousemove', moveToolbarMouseDrag);
  window.addEventListener('mouseup', stopToolbarMouseDrag);
}

function moveToolbarMouseDrag(event: MouseEvent) {
  if (!toolbarMouseDrag) return;
  toolbarPosition.value = boundedToolbarPosition(
    toolbarMouseDrag.left + event.clientX - toolbarMouseDrag.startX,
    toolbarMouseDrag.top + event.clientY - toolbarMouseDrag.startY,
  );
}

function stopToolbarMouseDrag() {
  toolbarMouseDrag = null;
  window.removeEventListener('mousemove', moveToolbarMouseDrag);
  window.removeEventListener('mouseup', stopToolbarMouseDrag);
}

function moveToolbarDrag(event: PointerEvent) {
  if (!toolbarDrag || event.pointerId !== toolbarDrag.pointerId) return;
  toolbarPosition.value = boundedToolbarPosition(
    toolbarDrag.left + event.clientX - toolbarDrag.startX,
    toolbarDrag.top + event.clientY - toolbarDrag.startY,
  );
}

function stopToolbarDrag(event: PointerEvent) {
  if (!toolbarDrag || event.pointerId !== toolbarDrag.pointerId) return;
  toolbarDrag = null;
  const handle = event.currentTarget as HTMLElement;
  if (handle.hasPointerCapture(event.pointerId)) handle.releasePointerCapture(event.pointerId);
}

function moveToolbarWithKeyboard(event: KeyboardEvent) {
  if (event.key === 'Home') {
    event.preventDefault();
    toolbarPosition.value = null;
    return;
  }
  const direction: Record<string, [number, number]> = {
    ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1],
  };
  const delta = direction[event.key];
  const position = currentToolbarPosition();
  if (!delta || !position) return;
  event.preventDefault();
  const step = event.shiftKey ? 40 : 12;
  toolbarPosition.value = boundedToolbarPosition(
    position.left + delta[0] * step,
    position.top + delta[1] * step,
  );
}

watch([data, kind], () => { void nextTick(clampToolbarPosition); });
const isFullscreen = ref(false);
const savingView = ref(false);
const is3dKind = computed(() => ['MODEL_3D_VIEWABLE', 'TILES_3D', 'GAUSSIAN_SPLAT', 'SOG_SPLAT', 'STREAMED_SOG'].includes(kind.value));
const hasSavedView = computed(() => Boolean(configuredCameraView(data.value?.previewConfig)));
const canSaveView = computed(() => is3dKind.value && Boolean(data.value?.canEditData) && authStore.hasPermission('RESOURCE_ALL,RESOURCE_EDIT'));
const canScreenshot = computed(() => Boolean(originUrl.value) && ['IMAGE', 'VIDEO', 'TEXT', 'XLSX', 'MODEL_3D_VIEWABLE', 'TILES_3D', 'GAUSSIAN_SPLAT', 'SOG_SPLAT', 'STREAMED_SOG'].includes(kind.value));
const helpTitle = computed(() => is3dKind.value ? '三维查看' : ({ IMAGE: '图片查看', VIDEO: '视频查看', AUDIO: '音频播放', PDF: 'PDF 查看', XLSX: '表格查看', TEXT: '文本查看', DOC_CONVERT: '文档查看' } as Record<string, string>)[kind.value] || '文件查看');
const helpText = computed(() => {
  if (kind.value === 'STREAMED_SOG') return '先显示粗层，再按视角渐进加载；左上角可调整显示精度。点击画面后：默认正立环绕，左拖旋转、右拖平移、滚轮缩放；V 切换自由飞行以查看场景下方或内部，W/A/S/D/Q/E 移动，右上角可调整移动速度，Shift/Alt 临时加速或减速，F 聚焦，Shift+F 重置。单指旋转、双指平移和捏合缩放。截图只保存画面。';
  if (kind.value === 'SOG_SPLAT') return '点击画面后：默认正立环绕，左拖旋转、右拖平移、滚轮缩放；V 切换自由飞行以查看场景下方或内部，W/A/S/D/Q/E 移动，右上角可调整移动速度，Shift/Alt 临时加速或减速，F 聚焦，Shift+F 重置。单指旋转、双指平移和捏合缩放。截图只保存高斯场景画面。';
  if (kind.value === 'GAUSSIAN_SPLAT') return '左键拖动旋转，右键拖动平移，滚轮缩放。若模型侧倒，可用左上角“方向校正”切换上方向或逐步滚转，再用下方工具栏保存当前视角。截图只保存三维画面。';
  if (is3dKind.value) return '左键拖动旋转，右键拖动平移，滚轮缩放。截图只保存三维画面，不包含工具栏。';
  if (kind.value === 'IMAGE') return '点击图片可放大查看；可截图或切换全屏。';
  if (kind.value === 'VIDEO') return '使用播放器控制播放；截图保存当前画面。';
  if (kind.value === 'PDF' || kind.value === 'DOC_CONVERT') return '使用文档内置控件翻页、缩放或打印；可切换全屏。';
  if (kind.value === 'AUDIO') return '使用播放器控制播放进度和音量。';
  return '可在当前页面查看文件，也可切换全屏；支持的格式可保存截图。';
});

function currentCameraViewer(): CameraViewer | null {
  switch (kind.value) {
    case 'MODEL_3D_VIEWABLE': return threeViewerRef.value;
    case 'TILES_3D': return tilesViewerRef.value;
    case 'GAUSSIAN_SPLAT': return gaussianViewerRef.value;
    case 'SOG_SPLAT': return sogViewerRef.value;
    case 'STREAMED_SOG': return sogViewerRef.value;
    default: return null;
  }
}

async function handleSaveView() {
  if (!data.value?.id) return;
  const view = currentCameraViewer()?.captureView();
  if (!view) {
    message.warning('场景尚未加载完成，暂时无法保存视角');
    return;
  }
  savingView.value = true;
  try {
    const response: any = await savePreviewView(data.value.id, view);
    data.value = { ...data.value, previewConfig: response?.data || view };
    message.success('已保存展示视角');
  } catch {
    message.error('保存视角失败，请检查资源编辑权限');
  } finally {
    savingView.value = false;
  }
}

function handleRestoreView() {
  currentCameraViewer()?.restoreSavedView();
}

function onFullscreenChange() {
  isFullscreen.value = document.fullscreenElement === previewShellRef.value;
  void nextTick(clampToolbarPosition);
}

async function toggleFullscreen() {
  try {
    if (document.fullscreenElement === previewShellRef.value) await document.exitFullscreen();
    else await previewShellRef.value?.requestFullscreen();
  } catch {
    message.warning('当前浏览器无法进入全屏模式');
  }
}

async function handleScreenshot() {
  const main = previewShellRef.value?.querySelector<HTMLElement>('.preview-main');
  if (!main) return;
  try {
    let canvas: HTMLCanvasElement;
    if (is3dKind.value) {
      const rendered = main.querySelector<HTMLCanvasElement>('canvas');
      if (!rendered || !currentCameraViewer()?.captureView()) throw new Error('场景尚未完成加载');
      canvas = rendered;
    } else if (kind.value === 'VIDEO') {
      const video = main.querySelector<HTMLVideoElement>('video');
      if (!video || video.readyState < 2) throw new Error('视频尚未准备好');
      canvas = document.createElement('canvas');
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      canvas.getContext('2d')?.drawImage(video, 0, 0);
    } else {
      canvas = await html2canvas(main, {
        backgroundColor: '#ffffff',
        useCORS: true,
        scale: Math.min(window.devicePixelRatio || 1, 2),
        ignoreElements: (element) => element instanceof HTMLElement && element.classList.contains('viewer-toolbar'),
      });
    }
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
    if (!blob || !blob.size) throw new Error('浏览器未生成截图');
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${String(data.value?.name || '预览').replace(/[\\/:*?"<>|]/g, '_')}-截图.png`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (error: any) {
    message.warning(`截图失败：${error?.message || '当前资源可能不允许跨域绘制'}`);
  }
}

document.addEventListener('fullscreenchange', onFullscreenChange);
window.addEventListener('resize', clampToolbarPosition);
onBeforeUnmount(() => {
  stopToolbarMouseDrag();
  document.removeEventListener('fullscreenchange', onFullscreenChange);
  window.removeEventListener('resize', clampToolbarPosition);
});

// 是否属于"后端转换预览"类型（DOC_CONVERT 包含 doc/docx/rtf/odt/pptx）
const isConversionKind = computed(() => kind.value === 'DOC_CONVERT');

// ===== 转换状态机 =====
// idle        - 未启动（非转换类型或数据未就绪）
// checking    - 正在查询后端缓存状态
// converting - 缓存未命中，后端正在转换（慢，首次）
// loading     - 缓存命中，iframe 正在加载（快）
// ready       - iframe 已加载完成
// unsupported - 后端未配置该格式转换，提示下载原文件
type ConvertState = 'idle' | 'checking' | 'converting' | 'loading' | 'ready' | 'unsupported';
type PreviewCapability = {
  supported: boolean;
  cached: boolean;
  error?: string;
};
const convertState = ref<ConvertState>('idle');

// 模拟进度条：converting 状态下缓慢推进，给用户视觉反馈
const convertProgress = ref(0);
let progressTimer: ReturnType<typeof setInterval> | null = null;

function startProgress() {
  convertProgress.value = 5;
  progressTimer = setInterval(() => {
    // 越接近 90% 推进越慢，避免跑满后卡住
    if (convertProgress.value < 90) {
      convertProgress.value += Math.max(0.5, (90 - convertProgress.value) * 0.03);
    }
  }, 800);
}
function stopProgress(done = false) {
  if (progressTimer) {
    clearInterval(progressTimer);
    progressTimer = null;
  }
  if (done) convertProgress.value = 100;
}

function resetConvertState() {
  stopProgress();
  convertProgress.value = 0;
  convertState.value = 'idle';
}

const convertWorkLabel = computed(() => {
  const ext = fileExt.value;
  if (ext === 'caj') return 'CAJ 文档转换';
  if (ext === 'pptx' || ext === 'ppt') return 'PPT/PPTX 幻灯片渲染';
  if (ext === 'xls') return 'Excel 表格渲染';
  return '文档渲染';
});

const unsupportedPreviewMessage = computed(() => '当前格式暂不可在线预览，请下载原文件查看');

const convertMsg = computed(() => {
  switch (convertState.value) {
    case 'checking':   return '检查缓存状态…';
    case 'converting': return `${convertWorkLabel.value}中，请耐心等待…`;
    case 'loading':    return '已命中缓存，文档加载中…';
    default:           return '加载中…';
  }
});

async function probePreviewCapability(fileId: string, format: string): Promise<PreviewCapability> {
  const resp: any = await (request as any)({
    url: 'file/preview/cached',
    method: 'get',
    params: { fileId, format },
  });
  return {
    supported: resp?.data?.supported !== false,
    cached: Boolean(resp?.data?.cached),
    error: resp?.data?.error,
  };
}

function applyPreviewCapability(capability: PreviewCapability) {
  stopProgress();
  if (!capability.supported) {
    convertState.value = 'unsupported';
    return;
  }
  if (capability.cached) {
    convertState.value = 'loading';
    return;
  }
  convertState.value = 'converting';
  startProgress();
}

// 监听"需转换类型 + fileId"的组合，触发缓存检查
const conversionTarget = computed(() =>
  isConversionKind.value && data.value?.fileId ? data.value.fileId : '',
);

watch(conversionTarget, async (fileId) => {
  stopProgress();
  convertProgress.value = 0;

  if (!fileId) {
    convertState.value = 'idle';
    return;
  }

  convertState.value = 'checking';
  const format = docConvertFormat.value;
  try {
    const capability = await probePreviewCapability(fileId as string, format);
    if (data.value?.fileId !== fileId || docConvertFormat.value !== format) return;
    applyPreviewCapability(capability);
  } catch {
    applyPreviewCapability({ supported: false, cached: false });
  }
});

function onIframeLoad() {
  stopProgress(true);
  // 短暂延迟让进度条动画跑满再隐藏覆盖层
  setTimeout(() => {
    convertState.value = 'ready';
  }, 300);
}

function parseMetadataEntries(input: any) {
  if (!input) return [];
  try {
    const metadata = typeof input === 'string' ? JSON.parse(input) : input;
    if (!metadata || typeof metadata !== 'object') {
      return [{ key: 'value', label: '值', value: formatMetadataValue(metadata) }];
    }
    const entries = Array.isArray(metadata)
      ? metadata.map((value, index) => [`${index + 1}`, value])
      : Object.entries(metadata);
    return entries
      .filter(([, value]) => value !== null && value !== undefined && value !== '')
      .map(([key, value]) => ({
        key,
        label: String(key),
        value: formatMetadataValue(value),
      }));
  } catch {
    return [];
  }
}

function formatMetadataValue(value: any): string {
  if (Array.isArray(value)) return value.map(formatMetadataValue).join('、');
  if (value && typeof value === 'object') return JSON.stringify(value);
  return String(value ?? '-');
}

// ===== 下载（记录下载次数后触发浏览器下载）=====
const downloading = ref(false);
async function handleDownload() {
  if (!data.value?.id) return;
  downloading.value = true;
  try { await ajaxGetDownload(`resource/${data.value.id}/original`, data.value.name || 'resource', undefined, { preferServerFilename: true }); }
  catch { message.error('下载失败，请确认当前账号拥有原文件下载权限'); }
  finally { downloading.value = false; }
}
async function handleDownloadPackage() {
  if (!data.value?.id) return;
  downloading.value = true;
  try { await ajaxGetDownload(`resource/${data.value.id}/package.zip`, `${data.value.name || 'resource'}.zip`, undefined, { preferServerFilename: true }); }
  catch { message.error('资源包下载失败，请确认下载权限与文件完整性'); }
  finally { downloading.value = false; }
}

// immediate 监听必须在 setup 状态全部初始化后注册；否则首次加载会在
// progressTimer 等 let/const 进入初始化前调用 resetConvertState，触发 TDZ 异常。
watch(
  () => [props.show, props.resourceId],
  ([show, id]) => {
    // 外部切换资源时重置导航栈和 Tab
    navStack.value = [];
    activeTab.value = 'preview';
    currentDisplayId.value = id as string;
    if (show && id) loadResource(id as string);
  },
  { immediate: true },
);
</script>

<style scoped>
/*
  工具栏悬浮，不再占预览高度。抽屉仅需预留头部、Tab 和内边距。
*/
/* CSS 变量声明在 :global 里，避免 scoped 哈希导致 :root 失效 */
:global(:root) {
  --preview-content-h: calc(100vh - 140px);
}

.preview-main {
  /* 无内容时保证最小占位 */
  min-height: 120px;
}
.preview-shell {
  position: relative;
  background: #fff;
}
.preview-toolbar {
  position: absolute;
  z-index: 30;
  top: 14px;
  right: 14px;
  display: flex;
  align-items: center;
  gap: 3px;
  max-width: calc(100% - 28px);
  min-height: 44px;
  padding: 3px 5px;
  border: 1px solid rgba(255, 255, 255, 0.17);
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.88);
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.24);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}
.preview-drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 27px;
  height: 36px;
  padding: 0;
  border: 0;
  border-radius: 9px;
  color: #93a8c2;
  background: transparent;
  cursor: grab;
  touch-action: none;
}
.preview-drag-handle:hover,
.preview-drag-handle:focus-visible {
  color: #fff;
  background: rgba(96, 165, 250, 0.2);
}
.preview-drag-handle:active { cursor: grabbing; }
.preview-drag-handle:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
}
.preview-file-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  max-width: min(33vw, 280px);
  height: 36px;
  padding: 0 12px 0 5px;
  border-right: 1px solid rgba(226, 232, 240, 0.23);
  color: #f8fafc;
}
.preview-file-symbol {
  flex: none;
  color: #93c5fd;
}
.preview-file-name {
  min-width: 0;
  max-width: 22ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
}
.preview-file-size {
  flex: none;
  color: #cbd5e1;
  font-size: 11px;
  white-space: nowrap;
}
.preview-action-dock {
  display: flex;
  align-items: center;
  gap: 3px;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}
.preview-action-dock::-webkit-scrollbar { display: none; }
.preview-tool-trigger {
  display: inline-flex;
}
.preview-tool-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 0;
  border-radius: 10px;
  color: #e2e8f0;
  background: transparent;
  cursor: pointer;
  text-decoration: none;
  transition: color 160ms ease, background-color 160ms ease, transform 160ms ease;
}
.preview-tool-button--label {
  width: auto;
  gap: 6px;
  padding: 0 10px;
  white-space: nowrap;
}
.preview-tool-button:hover:not(:disabled),
.preview-tool-button--active {
  color: #fff;
  background: rgba(96, 165, 250, 0.25);
}
.preview-tool-button:active:not(:disabled) {
  transform: scale(0.94);
}
.preview-tool-button:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
}
.preview-tool-button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.preview-tool-divider {
  flex: none;
  width: 1px;
  height: 19px;
  margin: 0 4px;
  background: rgba(226, 232, 240, 0.23);
}
.preview-icon-spin {
  animation: preview-spin 900ms linear infinite;
}
@keyframes preview-spin {
  to { transform: rotate(360deg); }
}
.preview-help-text {
  color: #888;
  font-size: 12px;
}
.preview-help-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.preview-help-text + .preview-help-text {
  margin-top: 8px;
}
.preview-shell--standalone {
  --preview-content-h: 100vh;
}
.preview-shell:fullscreen {
  --preview-content-h: 100vh;
  height: 100vh;
  overflow: auto;
}
@media (max-width: 720px) {
  .preview-toolbar {
    top: 10px;
    right: 10px;
    width: calc(100% - 20px);
    max-width: calc(100% - 20px);
    flex-wrap: wrap;
  }
  .preview-file-summary {
    flex: 1;
    max-width: calc(100% - 34px);
    border-right: 0;
  }
  .preview-action-dock {
    flex: 0 0 100%;
    padding-top: 3px;
    border-top: 1px solid rgba(226, 232, 240, 0.23);
  }
}
.preview-shell--standalone .preview-toolbar {
  top: auto;
  right: auto;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
}
.img-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;
  height: var(--preview-content-h);
  overflow: hidden;
}
.preview-image {
  max-width: 100%;
  max-height: 100%;
}
.preview-image :deep(img) {
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: auto;
  object-fit: contain;
}
.preview-video {
  width: 100%;
  height: var(--preview-content-h);
  display: block;
}
.preview-iframe {
  height: var(--preview-content-h);
  background: #fff;
  display: block;
}
/* 3D 查看器容器：给 canvas height:100% 提供定高父级 */
.viewer-3d-wrap {
  height: var(--preview-content-h);
  width: 100%;
  overflow: hidden;
  border-radius: 6px;
}
.notice-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px;
  background: #f5f7fa;
  border-radius: 6px;
  color: #6c757d;
}

/* ===== 文档转换预览 ===== */
.doc-preview-wrap {
  position: relative;
  height: var(--preview-content-h);
}
/* 覆盖层：absolute 盖在 iframe 上方 */
.convert-overlay {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 32px 24px;
}
/* 模拟进度条 */
.convert-progress {
  width: 280px;
}

.break-all {
  word-break: break-all;
}

.metadata-field-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px;
}
.metadata-field {
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
  background: var(--card-color, #fff);
}
.metadata-label {
  display: block;
  margin-bottom: 4px;
  color: #888;
  font-size: 12px;
}
.metadata-value {
  display: block;
  word-break: break-word;
  color: #333;
  font-size: 13px;
  line-height: 1.5;
}

.package-file-list {
  display: grid;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
  overflow: hidden;
}
.package-file-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
  padding: 9px 10px;
  border-bottom: 1px solid var(--n-border-color, #efeff5);
}
.package-file-row:last-child {
  border-bottom: 0;
}
.package-file-row.is-main {
  background: rgba(24, 160, 88, 0.08);
}
.package-file-info {
  min-width: 0;
  flex: 1;
}
.package-file-path {
  word-break: break-all;
  color: #333;
  font-size: 13px;
  line-height: 1.5;
}
.package-file-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
  color: #999;
  font-size: 12px;
}
.package-file-truncated {
  padding: 10px;
  color: #999;
  font-size: 12px;
  line-height: 1.5;
}

/* ===== Tabs ===== */
.preview-tabs :deep(.n-tabs-pane-wrapper) {
  padding-top: 12px;
}

/* 独立窗口只保留文件预览本身，不展示抽屉里的 Tab 导航和额外留白。 */
.preview-tabs--standalone {
  --preview-content-h: 100vh;
}
.preview-tabs--standalone :deep(.n-tabs-nav) {
  display: none;
}
.preview-tabs--standalone :deep(.n-tabs-pane-wrapper) {
  padding-top: 0;
}
.preview-tabs--standalone :deep(.n-tab-pane) {
  padding-top: 0;
}
.preview-tabs--standalone .preview-main {
  min-height: 100vh;
}
.preview-shell:fullscreen .preview-main {
  min-height: 100vh;
}

/* ===== 抽屉内导航返回 ===== */
.nav-back {
  margin-bottom: 8px;
}

/* ===== 关联资源 ===== */
.relation-group {
  margin-bottom: 10px;
}
.relation-type-label {
  font-size: 12px;
  color: #888;
  font-weight: 500;
  margin-bottom: 6px;
}
.relation-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.relation-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px 6px 6px;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  max-width: 230px;
  background: var(--card-color, #fff);
}
.relation-card:hover {
  border-color: var(--n-color-primary, #2080f0);
  box-shadow: 0 2px 8px rgba(32, 128, 240, 0.12);
}
.relation-thumb {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  overflow: hidden;
  flex-shrink: 0;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
}
.relation-thumb-empty {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}
.relation-info {
  min-width: 0;
  flex: 1;
}
.relation-name {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
}
.relation-desc {
  font-size: 11px;
  color: #999;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
  margin-top: 2px;
}
</style>

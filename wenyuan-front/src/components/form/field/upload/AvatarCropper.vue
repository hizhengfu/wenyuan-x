<template>
  <n-upload
    :action="targetUploadUrl"
    list-type="image-card"
    v-bind="$attrs"
    :data="postData"
    ref="avatarRef"
    :default-upload="false"
    v-model:file-list="fileList"
    @before-upload="handleBeforeUpload"
    @error="handleError"
    @finish="handleUploadFinish"
    accept="image/*"
    :custom-request="uploadDefaultRequest"
    :max="1"
  />
  <!-- 裁剪弹窗 -->
  <n-modal
    v-model:show="showCropper"
    :mask-closable="false"
    :trap-focus="false"
    :block-scroll="false"
    :auto-focus="false"
    preset="card"
    title="裁剪图像"
    size="small"
    style="width: 800px; position: relative"
    content-style="margin:0;padding:0;position: relative;"
  >
    <div style="position: relative; width: 800px; height: 610px">
      <div :id="cropperContainerId" class="cropper-container-wrapper">
        <img ref="cropperImgRef" v-show="false" class="cropper-image" />
        <div v-if="!imageLoaded" style="display: flex; align-items: center; justify-content: center; height: 100%">
          <n-spin size="large" />
        </div>
      </div>
      <n-float-button-group position="absolute" bottom="20" right="10">
        <n-tooltip trigger="hover" placement="right">
          <template #trigger>
            <n-float-button size="small" @click="handleZoomImagePlus">
              <n-icon>
                <IconCPlus></IconCPlus>
              </n-icon>
            </n-float-button>
          </template>
          放大图像
        </n-tooltip>
        <n-tooltip trigger="hover" placement="right">
          <template #trigger>
            <n-float-button size="small" @click="handleZoomImageMinus">
              <n-icon>
                <IconCMinus></IconCMinus>
              </n-icon>
            </n-float-button>
          </template>
          缩小图像
        </n-tooltip>
      </n-float-button-group>
    </div>

    <template #footer>
      <div style="display: flex; justify-content: space-between; gap: 12px; align-items: center">
        <div style="width: 600px">{{ tips }}</div>
        <div>
          <n-button size="small" @click="handleCancelCrop" class="mr-3">取消</n-button>
          <n-button size="small" type="primary" @click="confirmCrop">确定</n-button>
        </div>
      </div>
    </template>
  </n-modal>
</template>

<script lang="ts" setup>
import { uploadDefaultRequest, uploadUrl } from './utils';
import { fileUrl, uuid } from '@/utils';
import { computed, nextTick, ref, watchEffect } from 'vue';
import { UploadFileInfo } from 'naive-ui';
import { useAppStore } from '@/store';
import Cropper from 'cropperjs';

const useApp = useAppStore();

const cropperContainerId = 'container-' + uuid();

const props = defineProps({
  value: String,
  accept: String,
  fileUploadUrl: String,
  contentId: String,
  module: String,
  tips: {
    type: String,
    default: '图像与剪裁框可以选中移动、通过鼠标滚轮或按钮控制图像放大缩小，移动剪裁框或放大缩小图像以便获取最佳的显示效果。',
  },
  cropWidth: {
    type: Number,
    default: 358,
  },
  cropHeight: {
    type: Number,
    default: 441,
  },
});

const emits = defineEmits(['update:value']);

const avatarRef = ref();
const cropperImgRef = ref<HTMLImageElement>();

const showCropper = ref(false);
const imageLoaded = ref(false);
let cropper: Cropper | null = null;
let currentUploadFile: UploadFileInfo | null = null;
let currentObjectUrl: string | null = null;

// 上传地址
const targetUploadUrl = computed(() => {
  return props.fileUploadUrl || uploadUrl;
});

// 附加参数
const postData = computed(() => ({
  contentId: props.contentId || '',
  module: props.module || '',
}));

// ================= 裁剪拦截 =================

async function handleBeforeUpload(data: { file: UploadFileInfo; fileList: UploadFileInfo[] }) {
  // 拦截上传
  currentUploadFile = data.file;

  const rawFile = data.file.file as File;

  // 清理旧的 URL
  if (currentObjectUrl) {
    URL.revokeObjectURL(currentObjectUrl);
  }

  currentObjectUrl = URL.createObjectURL(rawFile);

  // 先销毁旧的 cropper
  if (cropper) {
    cropper.destroy();
    cropper = null;
  }

  // 打开模态框
  showCropper.value = true;

  // 等待模态框渲染完成
  await nextTick();

  const imgElement = cropperImgRef.value;
  if (!imgElement) return false;

  // 设置图片源
  imgElement.src = currentObjectUrl;

  imgElement.onload = async () => {
    imageLoaded.value = true;

    // 等 modal 动画 + dom 完成
    await nextTick();
    await new Promise((r) => setTimeout(r, 300));

    cropper = new Cropper(imgElement, {
      container: '#' + cropperContainerId,
      template: `
      <cropper-canvas background>
        <cropper-image scalable translatable rotatable></cropper-image>
        <cropper-shade hidden></cropper-shade>
        <cropper-handle action='move' plain></cropper-handle>
        <cropper-selection width='${props.cropWidth}' height='${props.cropHeight}' initialAspectRatio='0.5' movable>
          <cropper-grid role='grid' bordered covered></cropper-grid>
          <cropper-crosshair centered></cropper-crosshair>
          <cropper-handle action='move' theme-color='rgba(255, 255, 255, 0.35)'></cropper-handle>
        </cropper-selection>
      </cropper-canvas>
      `,
    });

    const selection = cropper.getCropperSelection();
    selection?.$center();
  };

  imgElement.onerror = () => {
    imageLoaded.value = false;
  };

  // 阻止原始上传
  return false;
}

// ================= 取消裁剪 =================

function handleCancelCrop() {
  showCropper.value = false;
  imageLoaded.value = false;

  if (cropper) {
    cropper.destroy();
    cropper = null;
  }

  if (currentObjectUrl) {
    URL.revokeObjectURL(currentObjectUrl);
    currentObjectUrl = null;
  }

  currentUploadFile = null;

  // 清空上传列表
  setTimeout(() => {
    avatarRef.value?.clear();
  });
}

// ================= 确认裁剪 =================

function confirmCrop() {
  if (!cropper || !currentUploadFile) return;

  cropper
    .getCropperSelection()
    ?.$toCanvas({
      width: props.cropWidth,
      height: props.cropHeight,
    })
    .then((canvas: HTMLCanvasElement) => {
      canvas.toBlob(
        (blob) => {
          if (!blob) return;

          // 构造新文件
          const file = new File([blob], currentUploadFile!.name, {
            type: 'image/png',
          });

          fileList.value = [
            {
              ...currentUploadFile,
              file, // 替换 file
              status: 'pending',
            },
          ];

          // 关闭弹窗
          showCropper.value = false;

          // 清理资源
          if (cropper) {
            cropper.destroy();
            cropper = null;
          }

          if (currentObjectUrl) {
            URL.revokeObjectURL(currentObjectUrl);
            currentObjectUrl = null;
          }

          nextTick(() => {
            // 手动触发上传
            avatarRef.value?.submit();
          });
        },
        'image/png',
        1,
      );
    });
}

/**
 * 缩放图像
 * */
function handleZoomImagePlus() {
  if (cropper) {
    cropper.getCropperImage()?.$zoom(0.1);
  }
}

function handleZoomImageMinus() {
  if (cropper) {
    cropper.getCropperImage()?.$zoom(-0.1);
  }
}

// ================= 原有逻辑 =================

function handleError() {
  useApp.uploading = false;
  setTimeout(() => {
    avatarRef.value?.clear();
  });
}

function handleUploadFinish({ file }: { file: UploadFileInfo }) {
  useApp.uploading = false;
  emits('update:value', file.fullPath);
}

// 默认回显
const fileList = ref();

watchEffect(() => {
  if (props.value) {
    fileList.value = [
      {
        thumbnailUrl: fileUrl(props.value) + '?w=300',
        url: fileUrl(props.value),
        status: 'finished',
      },
    ];
  }
});
</script>

<style scoped>
.cropper-container-wrapper {
  width: 800px;
  height: 600px;
  margin: 0;
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  bottom: 0;
}

.cropper-image {
  max-width: 100%;
  display: block;
}

.cropper-container-wrapper :deep(cropper-canvas) {
  width: 100% !important;
  height: 100% !important;
}
</style>

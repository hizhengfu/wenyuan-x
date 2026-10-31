<template>
  <n-input-group>
    <n-input-group-label v-for="(fileId, index) in originalFileIds" :key="fileId" v-download="downloadTarget(fileId)">
      {{ originalFileIds.length > 1 ? `点击查看原件 ${index + 1}` : '点击查看' }}
    </n-input-group-label>
    <n-input v-model:value="innerValue" v-bind="$attrs" :disabled="props.disabled" />
    <n-upload
      v-if="!props.disabled"
      style="width: 84px"
      :action="uploadUrl"
      :accept="accept"
      :data="postData"
      :disabled="props.disabled"
      @before-upload="handleBeforeUpload"
      @error="handleError"
      @finish="handleUploadFinish"
      :custom-request="uploadCallback"
      :show-file-list="false"
    >
      <n-button type="primary" :loading="loading" :disabled="props.disabled">
        <slot>上传文件</slot>
      </n-button>
    </n-upload>
  </n-input-group>
</template>
<script lang="ts" setup>
import { uploadRequest, uploadUrl, uploadedFileId } from './utils';
import { UploadCustomRequestOptions, UploadFileInfo } from 'naive-ui';
import { computed, PropType, ref } from 'vue';
import { useAppStore } from '@/store';

const useApp = useAppStore();

const props = defineProps({
  value: { type: [String, Array] as PropType<string | null> },
  accept: { type: String },
  contentId: { type: String },
  module: { type: String }, //模块id
  params: { type: Object }, //参数
  enableQuickUpload: { type: Boolean, default: true }, //是否允许快传
  enableChunk: { type: Boolean, default: true }, //是否分片
  disabled: { type: Boolean, default: false },
  originalUrl: { type: Function as PropType<(fileId: string) => string | undefined> },
});
const emits = defineEmits(['update:value', 'uploadFinish']);

const loading = ref(false);

const innerValue = computed<string | null>({
  get: () => props.value || null,
  set: (val) => {
    emits('update:value', val);
  },
});

function downloadTarget(fileId: string) {
  const scoped = props.originalUrl?.(fileId);
  return { url: scoped || fileId, isUserFile: !scoped, fileName: fileId, preferServerFilename: !!scoped };
}

const originalFileIds = computed(() => {
  if (!innerValue.value) return [];
  if (!props.originalUrl) return [innerValue.value];
  return [...new Set(innerValue.value.split(/[,，;；\r\n]/).map((id) => id.trim()).filter(Boolean))];
});

/**
 * 上传的数据
 * */
const postData = computed(() => {
  const { params } = props;

  let p = {
    contentId: props.contentId || '',
    module: props.module || '',
  };
  if (params) {
    Object.assign(p, params);
  }

  return p;
});

/**
 * 创建默认更改参数的默认回调
 * */
async function uploadCallback(uploadCustomRequestOptions: UploadCustomRequestOptions) {
  if (props.disabled) return;
  return uploadRequest(uploadCustomRequestOptions, {
    enableQuickUpload: props.enableQuickUpload,
    enableChunk: props.enableChunk,
  });
}

/**
 * 文件上传前事件
 * */
async function handleBeforeUpload(data: { file: UploadFileInfo; fileList: UploadFileInfo[] }) {
  if (props.disabled) return false;
  loading.value = true;
  useApp.uploading = true;
  const { accept } = props;
  //未指定文件类型可以上传任意文件
  if (!accept) return true;
  /*if (!data.file.file?.type || accept?.indexOf(data.file.file?.type) === -1) {
    notification({
      type: 'success',
      title: '请注意',
      content: `只能上传${accept}格式的文件，请重新上传。`
    })
    loading.value = false
    return false
  }*/
  return true;
}

function handleError() {
  loading.value = false;
  useApp.uploading = false;
}

/**
 * 上传完成
 * */
function handleUploadFinish({ file, event }: { file: UploadFileInfo; event?: ProgressEvent }) {
  const id = uploadedFileId(file);
  if (!id) {
    handleError();
    return;
  }
  innerValue.value = file.fullPath || null;
  loading.value = false;
  useApp.uploading = false;

  emits('uploadFinish', { ...file, id });
}
</script>

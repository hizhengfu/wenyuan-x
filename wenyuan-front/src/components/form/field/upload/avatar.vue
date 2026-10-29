<template>
  <n-upload
    :action="targetUploadUrl"
    list-type="image-card"
    v-bind="$attrs"
    :data="postData"
    ref="avatarRef"
    :default-file-list="fileList"
    @before-upload="handleBeforeUpload"
    @error="handleError"
    @finish="handleUploadFinish"
    @remove="handleRemove"
    accept="image/*"
    :custom-request="uploadDefaultRequest"
    :max="1"
  >
  </n-upload>
</template>
<script lang="ts" setup>
import { uploadDefaultRequest, uploadUrl } from './utils';
import { fileUrl } from '@/utils';
import { computed, ref, watchEffect } from 'vue';
import { UploadFileInfo } from 'naive-ui';
import { useAppStore } from '@/store';

const useApp = useAppStore();

const props = defineProps({
  value: {
    type: String,
  }, //自定义文件上传路径
  accept: { type: String },
  fileUploadUrl: {
    type: String,
  },
  contentId: { type: String }, //关联内容id
  module: { type: String }, //模块id
});
const emits = defineEmits(['update:value']);

const avatarRef = ref();

const targetUploadUrl = computed(() => {
  if (props.fileUploadUrl) {
    return props.fileUploadUrl;
  } else {
    return uploadUrl;
  }
});

/**
 * 上传的数据
 * */
const postData = computed(() => {
  let p = {
    contentId: props.contentId || '',
    module: props.module || '',
  };
  return p;
});

/**
 * 文件上传前事件
 * */
async function handleBeforeUpload(data: { file: UploadFileInfo; fileList: UploadFileInfo[] }) {
  useApp.uploading = true;
  const { accept } = props;
  //未指定文件类型可以上传任意文件
  if (!accept) return true;
  return true;
}

function handleError() {
  useApp.uploading = false;
  setTimeout(() => {
    avatarRef.value?.clear();
  }, 0);
}

function handleUploadFinish({ file, event }: { file: UploadFileInfo; event?: ProgressEvent }) {
  useApp.uploading = false;
  emits('update:value', file.fullPath);
}

function handleRemove() {
  emits('update:value', '');
  return true;
}

const fileList = ref();
watchEffect(() => {
  if (props.value) {
    fileList.value = [{ thumbnailUrl: fileUrl(props.value) + '?w=300', url: fileUrl(props.value), status: 'finished' }];
  }
});
</script>

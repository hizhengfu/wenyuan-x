<template>
  <t-upload
    v-bind="$attrs"
    v-if="!loading"
    :multiple="multiple"
    :allow-upload-duplicate-file="props.allowUploadDuplicateFile"
    :headers="headers"
    :with-credentials="true"
    :default-files="files"
    :formatResponse="handleFormatResponse"
    :data="data"
    :sizeLimit="sizeLimit"
    :action="targetUploadUrl"
    @success="handleSuccess"
    @fail="handleFail"
    @remove="handleRemove"
    @validate="onValidate"
  ></t-upload>
</template>
<script lang="ts" setup>
import { getAuthenticationHeaders } from '@/utils/auth';
import { fileUrl, notification } from '@/utils';
import { computed, ref, watch } from 'vue';
import { FormatResponseContext, SuccessContext, UploadFailContext, UploadRemoveContext } from 'tdesign-mobile-vue';
import { AxiosResponse } from 'axios';
import { del, listContentFiles } from '@/modules/sys/api/file.ts';
import { debounce, isArray, without } from 'lodash-es';
import { useAuthStore } from '@/store';

const authStore = useAuthStore();

const props = defineProps({
  multiple: { type: Boolean, default: true },
  allowUploadDuplicateFile: { type: Boolean, default: true },
  contentId: { type: String },
  sizeLimit: {
    type: Object,
    default() {
      return { size: 50, unit: 'MB', message: '图片大小不超过 {sizeLimit} MB' };
    },
  },
  form: {
    //提交的表单
    type: Object,
  },
  uploadField: { type: String, default: 'files' }, //表单中存储上传文件的字段
  //自定义文件上传路径
  fileUploadUrl: { type: String },
});

const emits = defineEmits(['update:value', 'fileChange', 'uploadFinish']);

const headers = computed(() => {
  return getAuthenticationHeaders(authStore.getToken, authStore.authorizationName);
});

const data = computed(() => {
  const { contentId } = props;
  return { contentId: contentId };
});

const onValidate = (context: any) => {
  if (context.type === 'FILE_OVER_SIZE_LIMIT') {
    notification({ type: 'fail', content: '文件大小超出上限' });
    return;
  }

  if (context.type === 'FILTER_FILE_SAME_NAME') {
    notification({ type: 'fail', content: '检测到同名图片，当前已允许重复上传；如果仍未成功，请重试一次' });
  }
};

const targetUploadUrl = computed(() => {
  if (props.fileUploadUrl) {
    return props.fileUploadUrl;
  } else {
    return APP_BASE_URL + APP_API_PATH + '/file/upload';
  }
});

const files = ref();
const loading = ref(false);

function setFormFileIds(fileList: any[] = []) {
  if (!props.form) return;
  const field = props.uploadField;
  const fileIds = fileList
    .map((item: any) => item?.response?.id || item?.id)
    .filter((id: string | undefined) => !!id);
  // eslint-disable-next-line vue/no-mutating-props
  props.form[field] = fileIds;
}

function fetchSourceFiles() {
  const { contentId } = props;
  files.value = [];

  if (contentId) {
    setFormFileIds([]);
    loading.value = true;
    listContentFiles(contentId)
      .then((resp: AxiosResponse<any>) => {
        files.value =
          resp.data?.list?.map((file: UploadResponseFile) => {
            return { url: fileUrl(file.path), name: file.name, id: file.id, status: 'success' };
          }) || [];
        setFormFileIds(files.value);
      })
      .finally(() => {
        loading.value = false;
      });
  }
}

const fetchFiles = debounce(fetchSourceFiles, 100);

/**
 * 格式化返回数据格式
 * */
function handleFormatResponse(response: AxiosResponse<UploadResponseFile>, context: FormatResponseContext) {
  if (response.status !== 200) {
    return { status: 'fail' };
  }
  let file = response.data;
  return { url: fileUrl(file.path), name: file.name, status: 'success', id: file.id, path: file.path };
}

function handleSuccess({ file, fileList, response, e }: SuccessContext) {
  const currentFile: any = file ?? (fileList && fileList.length > 0 ? fileList[fileList.length - 1] : undefined);
  if (!currentFile) {
    return;
  }
  //@ts-ignore
  emits('update:value', currentFile.path);
  //增加返回的文件服务器id到表单中
  if (props.form) {
    let field = props.uploadField;
    const currentFileIds = isArray(props.form[field]) ? [...props.form[field]] : [];
    const uploadedFileId = currentFile.response?.id || currentFile.id;
    if (uploadedFileId && !currentFileIds.includes(uploadedFileId)) {
      currentFileIds.push(uploadedFileId);
    }
    // eslint-disable-next-line vue/no-mutating-props
    props.form[field] = currentFileIds;
    emits('fileChange', props.form[field], currentFile);
  }
  emits('uploadFinish', currentFile);
}

/**
 * 上传失败
 * */
function handleFail(context: UploadFailContext) {
  let message;
  if (context.XMLHttpRequest?.response != null) {
    message = JSON.parse(context.XMLHttpRequest?.response)?.message;
  }
  notification({ type: 'fail', content: message });
  return { status: 'fail', error: message };
}

/**
 * 删除
 * */
function handleRemove(context: UploadRemoveContext) {
  let fileId = context.file?.response?.id || context.file?.id;
  let contentId = props.contentId;
  let field = props.uploadField;

  if (!fileId) {
    notification({ type: 'fail', content: '不能删除文件' });
    context.e.preventDefault();
    return false;
  }
  if (props.form && props.form[field]) {
    // eslint-disable-next-line vue/no-mutating-props
    props.form[field] = without(props.form[field], fileId);
  }
  if (fileId && contentId) {
    del(fileId, contentId);
  }
  emits('fileChange', props.form?.[field] || [], context.file);
}

watch(
  () => props.contentId,
  () => {
    fetchFiles();
  },
  {
    immediate: true,
  },
);
</script>

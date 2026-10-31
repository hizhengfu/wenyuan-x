<template>
  <n-upload
    :action="targetUploadUrl"
    :accept="accept"
    v-model:file-list="fileList"
    :listType="listType"
    file-list-style="max-height: 300px;overflow: auto"
    v-bind="$attrs"
    :data="postData"
    :multiple="multiple"
    @remove="handleRemove"
    @before-upload="handleBeforeUpload"
    @error="handleError"
    @finish="handleFinish"
    :custom-request="uploadCallback"
  >
    <n-upload-dragger v-if="allowDragger">
      <n-spin :show="loading">
        <slot></slot>
        <template #description> 正在上传，请稍候 </template>
      </n-spin>
    </n-upload-dragger>
    <template v-if="!allowDragger && listType !== 'image-card'">
      <slot>
        <n-button type="primary" :loading="loading">
          <n-icon>
            <IconMdiUpload />
          </n-icon>
          {{ label }}
        </n-button>
      </slot>
    </template>
  </n-upload>
</template>
<script lang="ts" setup>
import { uploadRequest, uploadUrl, uploadedFileId } from './utils';
import { confirm, fileUrl } from '@/utils';
import { del, listContentFiles } from '@/modules/sys/api/file';
import { computed, onDeactivated, onUnmounted, PropType, ref, watchEffect } from 'vue';
import { NUploadDragger, UploadCustomRequestOptions, UploadFileInfo } from 'naive-ui';
import { debounce, isArray, without } from 'lodash-es';
import { useAppStore } from '@/store';

const useApp = useAppStore();

const props = defineProps({
  value: {
    type: String,
  },
  listType: {
    type: String as PropType<'text' | 'image' | 'image-card'>,
    default: 'image',
  },
  //自定义文件上传路径
  fileUploadUrl: {
    type: String,
  },
  label: { type: String, default: '上传文件' },
  multiple: { type: Boolean, default: true },
  enableQuickUpload: { type: Boolean, default: true }, //是否允许快传
  enableChunk: { type: Boolean, default: true }, //是否分片
  allowDragger: { type: Boolean, default: false }, //拖动上传
  contentId: { type: String }, //关联内容id
  module: { type: String }, //模块id
  params: { type: Object }, //参数
  form: {
    //提交的表单
    type: Object,
  },
  uploadField: { type: String, default: 'files' }, //表单中存储上传文件的字段
  accept: { type: String }, //上传文件类型
  // 仅在浏览器本地处理文件，不上传到文件服务。适用于 Excel 解析后调用业务批量接口等场景。
  localFileHandler: { type: Function as PropType<(file: File) => any | Promise<any>> },
});
const emits = defineEmits(['update:value', 'fileChange', 'uploadFinish', 'uploadError']);
const loading = ref(false);
const localUploadResults = new Map<string, unknown>();

const fileList = ref();

const targetUploadUrl = computed(() => {
  if (props.fileUploadUrl) {
    return props.fileUploadUrl;
  } else {
    return uploadUrl;
  }
});

const wrapperComponent = computed(() => {
  if (props.allowDragger) {
    return NUploadDragger;
  } else {
    return null;
  }
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
  if (props.localFileHandler) {
    try {
      const source = uploadCustomRequestOptions.file.file;
      if (!(source instanceof File)) throw new Error('无法读取所选文件，请重新选择。');
      const result = await props.localFileHandler(source);
      localUploadResults.set(uploadCustomRequestOptions.file.id, result);
      uploadCustomRequestOptions.onProgress({ percent: 100 });
      uploadCustomRequestOptions.onFinish();
    } catch (error) {
      uploadCustomRequestOptions.onError();
      emits('uploadError', error);
    }
    return;
  }
  const customisePath = !props.fileUploadUrl || uploadUrl === props.fileUploadUrl;

  return uploadRequest(uploadCustomRequestOptions, {
    enableQuickUpload: props.enableQuickUpload && customisePath,
    enableChunk: props.enableChunk && customisePath,
  });
}

/**
 * 文件上传前事件
 * */
async function handleBeforeUpload(data: { file: UploadFileInfo; fileList: UploadFileInfo[] }) {
  loading.value = true;
  useApp.uploading = true;
  return true;
}

function handleError() {
  loading.value = false;
  useApp.uploading = false;
}

function handleFinish({ file, event }: { file: UploadFileInfo; event?: ProgressEvent }) {
  if (props.localFileHandler) {
    const result = localUploadResults.get(file.id);
    localUploadResults.delete(file.id);
    emits('uploadFinish', result);
    loading.value = false;
    useApp.uploading = false;
    return;
  }
  const id = uploadedFileId(file);
  if (!id) {
    handleError();
    return;
  }
  emits('update:value', file.fullPath);
  //增加返回的文件服务器id到表单中
  if (props.form) {
    let field = props.uploadField;
    if (!isArray(props.form[field])) {
      // eslint-disable-next-line vue/no-mutating-props
      props.form[field] = [];
    }
    //@ts-ignore
    // eslint-disable-next-line vue/no-mutating-props
    props.form[field].push(id);
    emits('fileChange', props.form['files'], { ...file, id });
  }
  emits('uploadFinish', { ...file, id });
  loading.value = false;
  useApp.uploading = false;
}

function handleUploadChange(data: { fileList: UploadFileInfo[] }) {
  fileList.value = data.fileList;
}

/**
 * 删除回调
 * */
function handleRemove(data: { file: UploadFileInfo; fileList: UploadFileInfo[] }) {
  return new Promise((resolve) => {
    confirm({
      type: 'warning',
      content: `即将删除文件「${data.file.name || data.file.url}」。`,
      onNegativeClick: () => {
        resolve(false);
      },
      onPositiveClick: () => {
        const id = uploadedFileId(data.file) || data.file.id;
        if (id && props.contentId) {
          del(id, props.contentId)
            .then(() => {
              resolve(true);
            })
            .catch(() => {
              resolve(false);
            });
        } else {
          let field = props.uploadField;
          if (props.form && props.form[field]) {
            //@ts-ignore
            // eslint-disable-next-line vue/no-mutating-props
            props.form[field] = without(props.form[field], id);
          }
          resolve(true);
        }
      },
    });
  });
}

/**
 * 获取文件列表
 * */
function fetchSourceFiles(id: string) {
  fileList.value = [];
  listContentFiles(id).then((resp) => {
    if (resp.data.list) {
      let temp: any[] = [];
      let fileIds: string[] = [];
      resp.data.list.forEach((item: any) => {
        temp.push({ id: item.id, name: item.name, url: fileUrl(item.path), status: 'finished' });
        fileIds.push(item.id);
      });
      fileList.value = temp;

      if (props.form) {
        let field = props.uploadField;
        //@ts-ignore
        // eslint-disable-next-line vue/no-mutating-props
        props.form[field] = fileIds;
      }
    }
  });
}

const fetchFiles = debounce(fetchSourceFiles, 100);

onUnmounted(() => {
  fileList.value = [];
});
onDeactivated(() => {
  fileList.value = [];
});

watchEffect(() => {
  if (props.contentId) {
    fetchFiles(props.contentId);
  }
});
</script>

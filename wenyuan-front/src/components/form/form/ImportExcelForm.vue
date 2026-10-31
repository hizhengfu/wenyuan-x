<template>
  <n-tooltip trigger="hover">
    <template #trigger>
      <slot name="trigger" :openDialog="openDialog">
        <n-button @click="openDialog">
          <n-icon size="20px">
            <IconMdiDatabaseImport />
          </n-icon>
        </n-button>
      </slot>
    </template>
    {{ tooltipText }}
  </n-tooltip>
  <h-dialog-form
    v-model:show="dialog"
    title="批量导入数据"
    v-bind="$attrs"
    :enable-cancel="false"
    size="small"
    height="320px"
    width="50%"
  >
    <n-spin :show="finishing" description="文件上传完成，正在处理导入数据...">
      <n-alert v-if="errorMessage" type="error" closable style="margin-bottom: 12px" @close="errorMessage = ''">
        {{ errorMessage }}
      </n-alert>
      <n-space justify="center">
        <h-upload
          :fileUploadUrl="fileUploadUrl"
          :local-file-handler="localFileHandler"
          :enableQuickUpload="enableQuickUpload"
          :enableChunk="enableChunk"
          :disabled="finishing"
          v-bind="$attrs"
          allowDragger
          @upload-finish="handleUploadFinish"
          @upload-error="handleUploadError"
        >
          <div style="margin-bottom: 12px">
            <n-icon size="96" :depth="3">
              <IconMdiCloudUpload />
            </n-icon>
          </div>
          <n-text style="font-size: 16px">
            {{ uploadLabel }}
          </n-text>
          <n-p depth="3" style="margin: 8px 0 0 0">
            {{ uploadDesc }}
          </n-p>
        </h-upload>
      </n-space>
      <n-flex justify="center" align="center">
        <n-form-item v-if="exportTemplateUrl || templateHandler">
          <n-button :loading="downloading" @click="downloadFile" type="info">
            <n-icon>
              <IconMdiDownload></IconMdiDownload>
            </n-icon>
            {{ exportLabel }}
          </n-button>
        </n-form-item>
        <slot></slot>
      </n-flex>
    </n-spin>
  </h-dialog-form>
</template>
<script lang="ts" setup>
import { PropType, ref } from 'vue';
import { ajaxGetDownload } from '@/utils';

const props = defineProps({
  //自定义文件上传路径
  fileUploadUrl: { type: String },
  uploadLabel: { type: String, default: '点击或者拖动文件到该区域来上传' },
  uploadDesc: { type: String, default: '请确认上传文件是否涉密。' },
  tooltipText: { type: String, default: '点击导入' },
  exportLabel: { type: String, default: '下载模版' },
  exportFileName: { type: String, default: '导出文件.xlsx' },
  exportTemplateUrl: { type: String },
  enableQuickUpload: { type: Boolean, default: false },
  enableChunk: { type: Boolean, default: false },
  finishHandler: { type: Function as PropType<(file: any) => any | Promise<any>> },
  localFileHandler: { type: Function as PropType<(file: File) => any | Promise<any>> },
  templateHandler: { type: Function as PropType<() => any | Promise<any>> },
  exportParams: { type: Object }, //导出参数
});
const emits = defineEmits(['update:show', 'finish', 'uploadFinish', 'finishError']);

const dialog = ref(false);
const finishing = ref(false);
const downloading = ref(false);
const errorMessage = ref('');

/**
 * 下载文件
 * */
async function downloadFile() {
  const { exportTemplateUrl, exportParams, exportFileName } = props;
  downloading.value = true;
  try {
    errorMessage.value = '';
    if (props.templateHandler) {
      await props.templateHandler();
    } else if (exportTemplateUrl) {
      ajaxGetDownload(exportTemplateUrl, exportFileName, exportParams);
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '模板下载失败，请稍后重试。';
    emits('finishError', error);
  } finally {
    downloading.value = false;
  }
}

function handleClose() {
  dialog.value = false;
}

function openDialog() {
  errorMessage.value = '';
  dialog.value = true;
}

async function handleUploadFinish(file: any) {
  finishing.value = true;
  try {
    errorMessage.value = '';
    const result = props.finishHandler ? await props.finishHandler(file) : file;
    dialog.value = false;
    emits('update:show', false);
    emits('finish', result);
    emits('uploadFinish', result);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '导入处理失败，请核对文件后重试。';
    emits('finishError', error);
  } finally {
    finishing.value = false;
  }
}

function handleUploadError(error: unknown) {
  errorMessage.value = error instanceof Error ? error.message : '导入处理失败，请核对文件后重试。';
  emits('finishError', error);
}

defineExpose({ handleClose, openDialog });
</script>

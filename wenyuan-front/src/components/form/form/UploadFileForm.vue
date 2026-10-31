<template>
  <n-tooltip trigger="hover">
    <template #trigger>
      <n-button @click="show = true">
        <n-icon size="20px">
          <IconMdiUpload />
        </n-icon>
      </n-button>
    </template>
    点击上传
  </n-tooltip>
  <h-dialog-form
    v-model:show="show"
    @update:show="closeDialog"
    title="批量导入数据"
    size="small"
    height="300px"
    width="40%"
  >
    <n-space justify="center">
      <n-form-item>
        <h-upload :label="uploadLabel" v-bind="$attrs" :module="module" :params="params" allowDragger>
          <div style="margin-bottom: 12px">
            <n-icon size="56">
              <IconMdiUpload />
            </n-icon>
          </div>
          <n-text style="font-size: 16px"> 点击或者拖动文件到该区域来上传</n-text>
          <n-p depth="3" style="margin: 8px 0 0 0">
            请不要上传敏感数据，比如你的银行卡号和密码，信用卡号有效期和安全码
          </n-p>
        </h-upload>
      </n-form-item>
    </n-space>
  </h-dialog-form>
</template>
<script lang="ts" setup>
import { ref, watch } from 'vue';
import { ajaxGetDownload } from '@/utils';
import { MapDoubleIcon } from 'tdesign-icons-vue-next';

const props = defineProps({
  uploadLabel: { type: String, default: '上传文件' },
  module: { type: String }, //模块id
  params: { type: Object }, //参数
});

const show = ref(false);

const emits = defineEmits(['update:show']);

function closeDialog(show: boolean) {
  emits('update:show', show);
}
</script>

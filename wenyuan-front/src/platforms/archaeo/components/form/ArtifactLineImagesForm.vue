<template>
  <HUpload
    :contentId="'lineDiagram_' + contentId"
    label="上传文物线图"
    accept="image/*"
    list-type="image-card"
    @preview="handlePreview"
  ></HUpload>
  <n-modal
    v-model:show="previewModal"
    preset="card"
    size="small"
    draggable
    :to="modelWrapId"
    style="width: 330px"
    :trap-focus="false"
  >
    <template #header> {{ previewImageTitle }} <span class="font-12">（可以拖动查看）</span></template>
    <n-image :src="previewImageUrl" width="300px"></n-image>
  </n-modal>
</template>
<script lang="ts" setup>
import { ref } from 'vue';
import { UploadFileInfo } from 'naive-ui';

const props = defineProps({ contentId: { type: String }, modelWrapId: { type: String, default: 'body' } });


const previewModal = ref(false);
const previewImageTitle = ref();
const previewImageUrl = ref();

function handlePreview(file: UploadFileInfo) {
  previewImageUrl.value = file.url;
  previewImageTitle.value = file.name;
  previewModal.value = true;
}
</script>
<style scoped>
:deep(.n-tab-pane) {
  box-sizing: border-box;
  min-height: 160px;
  border: 1px solid var(--n-tab-border-color);
  border-left: none;
  padding: 15px !important;
}

:deep(.n-modal-scroll-content) {
  pointer-events: none;
}
</style>

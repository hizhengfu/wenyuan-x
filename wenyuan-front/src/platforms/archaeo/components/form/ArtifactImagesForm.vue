<template>
  <n-tabs v-model:value="activeTab" type="card" v-bind="$attrs">
    <n-tab-pane v-for="panel in panels" :key="panel.value" :tab="panel.name" :name="panel.value">
      <HUpload
        accept="image/*"
        listType="image-card"
        :contentId="contentId + '_' + panel.value"
        label="上传附件"
        @preview="handlePreview"
      ></HUpload>
    </n-tab-pane>
  </n-tabs>
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

const activeTab = ref();
const panels = ref([
  {
    name: '前视角',
    value: 'front',
  },
  {
    name: '后视角',
    value: 'back',
  },
  {
    name: '左视角',
    value: 'left',
  },
  {
    name: '右视角',
    value: 'right',
  },
  {
    name: '顶视角',
    value: 'top',
  },
  {
    name: '底视角',
    value: 'bottom',
  },
  {
    name: '特写视角',
    value: 'other',
  },
]);

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

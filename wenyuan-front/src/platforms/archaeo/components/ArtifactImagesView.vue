<template>
  <n-tabs
    v-model:value="activeTab"
    type="card"
    style="display: flex; flex: 1"
    pane-style="flex:1;display: flex;box-sizing: border-box;min-height: 160px;border: 1px solid var(--n-tab-border-color);border-top:none;padding:15px;"
  >
    <n-tab-pane v-for="panel in panels" :key="panel.value" :tab="panel.name" :name="panel.value">
      <HFileList listType="image-card" :contentId="contentId + '_' + panel.value"></HFileList>
    </n-tab-pane>
  </n-tabs>
</template>
<script lang="ts" setup>
import { ref } from 'vue';

const props = defineProps({ contentId: { type: String } });

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
</script>
<style scoped>
:deep(.n-tabs-pad) {
  width: 0;
}
</style>

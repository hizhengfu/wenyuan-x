<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="220" bordered>
        <n-layout-header bordered style="padding: 10px;">
          <div style="font-weight: 600; font-size: 16px; margin-left: 10px;">
            {{ fourTitle }}
          </div>
        </n-layout-header>
        <n-layout-content position="absolute" style="top: 60px">
          <FourTree @select="handleSelect" :code="code"></FourTree>
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content position="absolute" style="left: 220px;" content-style="padding:15px;">
        <component :fourCulturalRecordId="innerFourCulturalRecordId" :is="pageFourComponent" :showOperate="false"
          :isAdd="false" :showHeader="false" :showToolbar="false" />
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script setup lang="ts">
import FourTree from './FourTree.vue';
import { computed, onMounted, ref, watchEffect } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute()
const pageFourComponent = ref();

const props = defineProps(
  {
    fourCulturalRecordId: { type: String, required: true },//四有档案id
    code: { type: String, default: 'Fm' },//菜单code
    title: { type: String },
  },
)

/**
 * 四有档案id
 * */
const innerFourCulturalRecordId = computed(() => {
  return props.fourCulturalRecordId || route.query.fourCulturalRecordId
})
const fourTitle = ref();

onMounted(() => {
  fourTitle.value = route.query.title || props.title;
});

/**
 * 构建渲染组件
 * */
function buildFourComponent(code?: string) {
  if (code) {
    if (innerFourCulturalRecordId.value) {
      pageFourComponent.value = 'FourPage' + code;
    } else {
      pageFourComponent.value = 'FourPageEmpty';
    }
  }
}

function handleSelect(key: string, item: any) {
  if (item && item.type) {
    buildFourComponent(item.type)
  }
}

watchEffect(() => {
  const { code } = props
  buildFourComponent(code)
})
</script>

<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="220" bordered>
        <n-layout-header bordered style="padding: 10px;">
          <div style="font-weight: 600; font-size: 20px; margin-left: 10px;">
            {{ thirdTitle }}
          </div>
        </n-layout-header>
        <n-layout-content position="absolute" style="top: 60px;">
          <ThirdTree :code="menuCode" @select="handleSelect"></ThirdTree>
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content position="absolute" style="left: 220px;" content-style="padding:15px;">
        <component :is="pageThirdComponent" v-if="thirdCulturalRecordId" :title="route.query.MenuTitle"
          :thirdCulturalRecordId="thirdCulturalRecordId" />
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script setup lang="ts">
import { computed, onActivated, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ThirdTree from '../../components/third/ThirdTree.vue';

const props = defineProps({
  thirdCulturalRecordId: { type: String },
});

const menuCode = ref();

const pageThirdComponent = ref('ThirdPageEmpty');

const route = useRoute();
const router = useRouter();

const thirdTitle = ref();

onActivated(() => {
  thirdTitle.value = route.query.title
});

/**
 * 三普数据id
 * */
const thirdCulturalRecordId = ref(route.query.thirdCulturalRecordId);

/**
 * 构建渲染组件
 * */
function buildThirdComponent(code?: string) {
  if (code) {
    if (thirdCulturalRecordId.value || props.thirdCulturalRecordId) {
      pageThirdComponent.value = 'ThirdPage' + code;
    } else {
      pageThirdComponent.value = 'ThirdPageEmpty';
    }
  }
}

function handleSelect(key: string, item: any) {
  router.push({
    query: {
      ...route.query,
      code: item.type,
      MenuTitle: item.name,
    },
  });
  buildThirdComponent(item.type);
  menuCode.value = item.type;
}

/**
 * 监听三普数据id的变化
 * */
watch(
  () => route.query.thirdCulturalRecordId,
  (code) => {
    thirdCulturalRecordId.value = code;
  },
  { immediate: true },
);

watch(
  () => route.query.code,
  (code) => {
    //节点不一致处理展开
    if (code && code != menuCode.value) {
      buildThirdComponent(code as string);
      menuCode.value = code;
    }
  },
  { immediate: true },
);
</script>

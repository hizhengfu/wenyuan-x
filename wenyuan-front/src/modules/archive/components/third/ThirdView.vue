<template>
  <n-card class="h-full" content-style="padding:0;" >
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="220" bordered>
        <n-layout-header bordered style="padding: 10px;">
          <div style="font-weight: 600; font-size: 16px; margin-left: 10px;">
            {{ thirdTitle }}
          </div>
        </n-layout-header>
        <n-layout-content position="absolute" style="top: 60px;">
          <ThirdTree :code="menuCode" @select="handleSelect"></ThirdTree>
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content position="absolute" style="left: 220px;" content-style="padding:15px;">
        <component
          v-bind="$attrs"
          :is="pageThirdComponent"
          v-if="thirdCulturalRecordId"
          :title="currentMenuTitle"
          :thirdCulturalRecordId="thirdCulturalRecordId"
          :showList="false"
          :showOperate="false"
          :isAdd="false"
          :showHeader="false"
          :showToolbar="false"
        />
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ThirdTree from '../../components/third/ThirdTree.vue';

const props = defineProps({
  thirdCulturalRecordId: { type: String },
  title: { type: String },
  code: {
    type: String,
  },
  isInTab: {
    type: Boolean,
    default: false,
  },
});

const menuCode = ref();

const pageThirdComponent = ref('ThirdPageFm');

const route = useRoute();
const router = useRouter();

const thirdTitle = ref();
const currentMenuTitle = ref<string | undefined>();

onMounted(() => {
  thirdCulturalRecordId.value = props.thirdCulturalRecordId || route.query.thirdCulturalRecordId;
  thirdTitle.value = props.title || route.query.title;
  currentMenuTitle.value = (route.query.MenuTitle as string) || undefined;
});

/**
 * 三普数据id
 * */
const thirdCulturalRecordId = ref();

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
  currentMenuTitle.value = item.name;
  if (!props.isInTab) {
    router.push({
      query: {
        ...route.query,
        code: item.type,
        MenuTitle: item.name,
      },
    });
  }
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
    if (props.isInTab) return;
    //节点不一致处理展开
    if (code && code != menuCode.value) {
      buildThirdComponent(code as string);
      menuCode.value = code;
    }
  },
  { immediate: true },
);
watch(
  () => route.query.MenuTitle,
  (title) => {
    if (props.isInTab) return;
    currentMenuTitle.value = title as string;
  },
  { immediate: true },
);
watch(
  () => props.code,
  (code) => {
    //节点不一致处理展开
    if (code && code != menuCode.value) {
      buildThirdComponent(code as string);
      menuCode.value = code;
      if (props.isInTab && !currentMenuTitle.value) {
        currentMenuTitle.value = code as string;
      }
    }
  },
  { immediate: true },
);
</script>

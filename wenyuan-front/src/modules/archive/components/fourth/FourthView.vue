<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="220" bordered>
        <n-layout-header bordered style="padding: 10px;">
          <div style="font-weight: 600; font-size: 16px; margin-left: 10px;">
            {{ fourthTitle }}
          </div>
        </n-layout-header>
        <n-layout-content position="absolute" style="top: 60px;">
          <fourthTree :code="menuCode" @select="handleSelect" :manage="manage"></fourthTree>
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content position="absolute" style="left: 220px;" content-style="padding:15px;">
        <component
          v-bind="$attrs"
          :is="pageFourthComponent"
          v-if="fourthCulturalRecordId"
          :title="currentMenuTitle"
          :fourthCulturalRecordId="fourthCulturalRecordId"
          :showOperate="false"
          :showList="false"
          :isAdd="false"
          :showHeader="false"
          :showToolbar="false"
        />
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script setup lang="ts">
import { onActivated, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import fourthTree from './FourthTree.vue';

const props = defineProps({
  fourthCulturalRecordId: { type: String },
  title: { type: String },
  manage: { type: Boolean, default: true },
  code: {
    type: String,
  },
  isInTab: {
    type: Boolean,
    default: false,
  },
});

const menuCode = ref();

const pageFourthComponent = ref('fourthPageFm');

const route = useRoute();
const router = useRouter();

const fourthTitle = ref();
const currentMenuTitle = ref<string | undefined>();
const manage = ref();

onMounted(() => {
  fourthCulturalRecordId.value = props.fourthCulturalRecordId || route?.query?.fourthCulturalRecordId;
  fourthTitle.value = props.title || route.query.title;
  currentMenuTitle.value = (route.query.MenuTitle as string) || undefined;
  manage.value = props.manage || route.query.manage;
});

/**
 * 四普数据id
 * */
const fourthCulturalRecordId = ref();

/**
 * 构建渲染组件
 * */
function buildThirdComponent(code?: string) {
  if (code) {
    if (fourthCulturalRecordId.value || props.fourthCulturalRecordId) {
      pageFourthComponent.value = 'fourthPage' + code;
    } else {
      pageFourthComponent.value = 'fourthPageEmpty';
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
 * 监听四普数据id的变化
 * */
watch(
  () => route.query.fourthCulturalRecordId,
  (code) => {
    fourthCulturalRecordId.value = code;
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

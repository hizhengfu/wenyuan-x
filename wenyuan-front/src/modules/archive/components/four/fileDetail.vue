<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="320" bordered>
        <n-layout-header bordered style="padding: 10px">
          <h3>档案卷宗</h3>
        </n-layout-header>
        <n-layout-content position="absolute" style="top: 60px">
          <FourFileTree
            :code="menuCode"
            @select="handleSelect"
            :fourCulturalRecordId="actualFourCulturalRecordId"
          ></FourFileTree>
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content position="absolute" style="left: 320px" content-style="padding:15px;">
        <FourFile v-bind="$attrs" :fourCulturalRecordId="actualFourCulturalRecordId" :menuCode="menuCode"></FourFile>
      </n-layout-content>
    </n-layout>
  </n-card>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import FourFileTree from './FourFileTree.vue';
import FourFile from './FourFile.vue';
import { get } from '@/modules/archive/api/four/fourCulturalRecord.ts';

// 定义 props
const props = defineProps<{
  fourCulturalRecordId?: string;
  isInTab?: boolean;
  params?: Object;
}>();

const menuCode = ref('Fm');

const route = useRoute();
const router = useRouter();

// 计算实际的 fourCulturalRecordId
const actualFourCulturalRecordId = computed(() => {
  // 如果在标签页中，优先使用 props 传入的 ID
  if (props.fourCulturalRecordId) {
    return props.fourCulturalRecordId;
  }
  // 否则使用路由参数
  return route.query.fourCulturalRecordId as string;
});

function fetchFourCulturalRecord(id: string) {
  get(id).then((res) => {
    if (res.data) {
      // 处理数据
    }
  });
}

function handleSelect(key: string, item: any) {
  // 如果在标签页中，不更新路由
  if (props.isInTab) {
    menuCode.value = item.type;
    return;
  }

  // 否则更新路由
  router.push({
    query: {
      ...route.query,
      code: item.type,
      menuTitle: item.name,
      fourMenuId: item.id,
    },
  });
  menuCode.value = item.type;
}

// 监听 ID 变化
watch(
  () => actualFourCulturalRecordId.value,
  (id) => {
    if (id) {
      fetchFourCulturalRecord(id);
    }
  },
  { immediate: true },
);

// 监听路由参数 code
watch(
  () => route.query.code,
  (code: any) => {
    if (code && code != menuCode.value) {
      menuCode.value = code;
    }
  },
  { immediate: true },
);
</script>

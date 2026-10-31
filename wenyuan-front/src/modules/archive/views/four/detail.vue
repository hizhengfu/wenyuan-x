<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="220" bordered>
        <n-layout-header bordered style="padding: 10px;">
          <div style="font-weight: 600; font-size: 20px; margin-left: 20px;">
            {{ fourTitle }}
          </div>
        </n-layout-header>
        <n-layout-content position="absolute" style="top: 60px;">
          <FourTree :code="menuCode" @select="handleSelect"></FourTree>
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content position="absolute" style="left: 220px;" content-style="padding:15px;">
        <component
          :is="pageFourComponent"
          v-if="fourCulturalRecordId"
          :fourCulturalRecordId="fourCulturalRecordId"
          addPermission="FOUR_CULTURAL_RECORD_ALL,FOUR_CULTURAL_RECORD_CREATE"
          editPermission="FOUR_CULTURAL_RECORD_ALL,FOUR_CULTURAL_RECORD_EDIT"
          deletePermission="FOUR_CULTURAL_RECORD_ALL,FOUR_CULTURAL_RECORD_DELETE"
        />
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script setup lang="ts">
import { get } from '../../api/four/fourCulturalRecord.ts';
import { computed, onActivated, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import FourTree from '../../components/four/FourTree.vue';

const menuCode = ref();

const pageFourComponent = ref('FourPageEmpty');

const route = useRoute();
const router = useRouter();

const fourTitle = ref();

onActivated(() => {
  const titleId = route.query.fourCulturalRecordId as string;
  handleTitle(titleId);
});

function handleTitle(id: string) {
  get(id).then((res) => {
    if (res.data) {
      fourTitle.value = res.data.name;
    }
  });
}

/**
 * 四有档案id
 * */
const fourCulturalRecordId = ref(route.query.fourCulturalRecordId);

/**
 * 构建渲染组件
 * */
function buildFourComponent(code?: string) {
  if (code) {
    if (fourCulturalRecordId.value) {
      pageFourComponent.value = 'FourPage' + code;
    } else {
      pageFourComponent.value = 'FourPageEmpty';
    }
  }
}

function handleSelect(key: string, item: any) {
  router.push({
    query: {
      ...route.query,
      code: item.type,
      title: item.name,
    },
  });
  buildFourComponent(item.type);
  menuCode.value = item.type;
}

/**
 * 监听四有档案id的变化
 * */
watch(
  () => route.query.fourCulturalRecordId,
  (code) => {
    fourCulturalRecordId.value = code;
  },
  { immediate: true },
);

watch(
  () => route.query.code,
  (code) => {
    //节点不一致处理展开
    if (code && code != menuCode.value) {
      buildFourComponent(code as string);
      menuCode.value = code;
    }
  },
  { immediate: true },
);
</script>

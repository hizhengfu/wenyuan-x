<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="220" bordered>
        <n-layout-header bordered style="padding: 10px;">
          <div style="font-weight: 600; font-size: 20px; margin-left: 10px;">
            {{ fourthTitle }}
          </div>
        </n-layout-header>
        <n-layout-content position="absolute" style="top: 60px;">
          <fourthTree :code="menuCode" @select="handleSelect"></fourthTree>
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content position="absolute" style="left: 220px;" content-style="padding:15px;">
        <component :is="pagefourthComponent" v-if="fourthCulturalRecordId" :title="route.query.MenuTitle"
          :fourthCulturalRecordId="fourthCulturalRecordId"  :culturalType="heritageType"   @heritageType="handleheritageType"/>
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script setup lang="ts">
import { computed, onActivated, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import fourthTree from '../../components/fourth/FourthTree.vue';

const menuCode = ref();

const pagefourthComponent = ref('fourthPageEmpty');

const route = useRoute();
const router = useRouter();

const fourthTitle = ref();

onActivated(() => {
  fourthTitle.value = route.query.title
});

/**
 * 四普数据id
 * */
const fourthCulturalRecordId = ref(route.query.fourthCulturalRecordId);

/**
 * 构建渲染组件
 * */
function buildfourthComponent(code?: string) {
  if (code) {
    if (fourthCulturalRecordId.value) {
      pagefourthComponent.value = 'fourthPage' + code;
      
    } else {
      pagefourthComponent.value = 'fourthPageEmpty';
    }
  }
}
const heritageType =ref()
function handleheritageType(type:string){
  if(type){
    heritageType.value = type
  }

}
function handleSelect(key: string, item: any) {
  console.log(item,'----0000',heritageType);
  
  router.push({
    query: {
      ...route.query,
      code: item.type,
      MenuTitle: item.name,
      type:heritageType.value ? heritageType.value : ''
    },
  });
  buildfourthComponent(item.type);
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
  { immediate: true,deep:true },
);

watch(
  () => route.query.code,
  (code) => {
    //节点不一致处理展开
    if (code && code != menuCode.value) {
      buildfourthComponent(code as string);
      menuCode.value = code;
    }
  },
  { immediate: true },
);
</script>

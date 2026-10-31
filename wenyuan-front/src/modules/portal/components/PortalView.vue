<template>
  <div style="width: 100%">
    <template v-if="groups && groups.length > 0">
      <fieldset
        v-for="(group, index) in groups"
        class="group_border"
        :style="{ 'border-color': group.color }"
        :key="index"
      >
        <legend v-if="group.name" style="margin-left: 10px; font-size: 20px; font-weight: bolder; color: #111">
          {{ group.name }}
        </legend>
        <grid-layout
          :layout="group.portals"
          :colNum="12"
          :rowHeight="1"
          :is-draggable="false"
          :is-resizable="false"
          :auto-size="true"
          :vertical-compact="true"
          style="position: relative"
        >
          <template #gridItemContent="slotProps">
            <portal-content
              v-bind="slotProps.item"
              :params="params"
            ></portal-content>
          </template>
        </grid-layout>
      </fieldset>
    </template>
    <div v-else class="flex-center wh-full" style="min-height: 300px">
      <n-empty description="暂无配置" size="huge">
        <template #extra> 暂未配置或无Portal权限，请联系管理员配置或授予权限。</template>
      </n-empty>
    </div>
  </div>
</template>
<script setup lang="ts">
import '@noction/vue-draggable-grid/styles';
import { GridLayout } from '@/components/basic/vueDraggableGrid';
import PortalContent from './PortalContent.vue';
import { ref, watchEffect, watch } from 'vue';
import { loadSelf } from '../api/portalTemplate.js';
import { adopterPortal } from '../utils';
import { usePortalStore } from './usePortal.ts';

const groups = ref();
const loading = ref(false);

const usePortal = usePortalStore();

/**
 * portal id 根据此id加载数据
 * */
const props = defineProps({
  portalId: { type: String },
  params: { type: Object }, //外部参数
});


watchEffect(() => {
  const { portalId } = props;
  if (portalId) {
    loadConfigPortal(portalId);
  }
});

watch(
  () => props.params,
  (newValue: any) => {
    usePortal.initialize(newValue);
  },
  { immediate: true },
);

function loadConfigPortal(sourceId: string) {
  let params = { sourceId: sourceId, isGroup: true };
  loading.value = true;
  loadSelf(params)
    .then((res) => {
      groups.value = [];
      if (res.data) {
        res.data.forEach((i: any, k: number) => {
          let groupItem = {
            name: i.group.name,
            id: i.group.id,
            color: i.group.color || '',
            portals: [],
          };
          i.portals.forEach((item: any, index: number) => {
            // @ts-ignore
            groupItem.portals.push(adopterPortal(item, index));
          });
          groups.value.push(groupItem);
        });
      }
    })
    .finally(() => {
      loading.value = false;
    });
}
</script>
<style scoped>
.group_border {
  border: 1px solid #eeeeee;
  background-color: #ffffff;
  border-radius: 5px;
  margin: 5px;
}
</style>

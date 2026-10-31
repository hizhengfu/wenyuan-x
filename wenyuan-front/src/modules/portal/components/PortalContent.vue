<template>
  <template v-if="isExpose === true">
    <n-card
      class="portal-content"
      style="height: 100%"
      size="small"
      hoverable
      :content-style="{ overflow: 'auto', display: 'flex', flexDirection: 'column' }"
      :segmented="{ content: true, footer: 'soft' }"
    >
      <template #header>
        {{ name }}
      </template>
      <template v-if="columns && columns.length < 4" #header-extra>
        <PortalQuery :columns="columns as PortalColumn[]" v-model:params="innerParams"></PortalQuery>
      </template>
      <template v-if="columns && columns.length >= 4">
        <PortalQuery :columns="columns as PortalColumn[]" v-model:params="innerParams"></PortalQuery>
        <n-divider style='margin: 15px 0;'></n-divider>
      </template>
      <component
        :is="widgetComponent"
        v-bind="$attrs"
        :url="url"
        :columns="columns"
        :params="innerParams"
        :id="id"
      ></component>
    </n-card>
  </template>
  <template v-else>
    <component
      :is="widgetComponent"
      v-bind="$attrs"
      :url="url"
      :columns="columns"
      :params="innerParams"
      :id="id"
    ></component>
  </template>
</template>

<script lang="ts" setup>
import * as AllWidgets from './portal/index';
import PortalQuery from './PortalQuery.vue';
import { computed, ref, watch } from 'vue';

const widgets = new Map();

/**
 * 注册portal渲染组件
 * */
Object.entries(AllWidgets).forEach(([key, component]) => {
  widgets.set(key.toUpperCase(), component);
});

const props = defineProps({
  name: { type: String },
  type: { type: String },
  isDesign: { type: Boolean, default: false },
  id: { type: String },
  url: { type: String },
  isBorder: { type: Boolean },
  isExpose: { type: Boolean },
  columns: { type: Array },
  group: { type: Object },
  params: { type: Object },
});

const widgetComponent = computed(() => {
  const { type } = props;
  const widget = widgets.get('PORTALWIDGETS' + type) || widgets.get('PORTALWIDGETS404');
  return widget;
});

const innerParams = ref({});

watch(
  () => props.params,
  (newValue, oldValue) => {
    if (newValue) {
      Object.assign(innerParams.value, newValue);
    }
  },
  { deep: true },
);
</script>

<style lang="scss" scoped>
.portal-content img {
  max-width: 100%;
}
</style>

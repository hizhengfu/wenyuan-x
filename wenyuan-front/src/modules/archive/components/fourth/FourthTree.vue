<template>
  <n-menu
    :options="menus"
    ref="menuInstRef"
    v-bind="$attrs"
    label-field="name"
    key-field="type"
    :default-expanded-keys="defaultExpandedMenuId"
    :watch-props="['defaultExpandedKeys']"
    accordion
    v-model:value="selectCode"
    @update:value="handleUpdateValue"
  />
</template>
<script setup lang="ts">
import setupFourthPageComponents from './page';
import { MenuOption, TreeOption } from 'naive-ui';
import { nextTick, ref, watch, getCurrentInstance, App, computed } from 'vue';

setupFourthPageComponents(getCurrentInstance()?.appContext.app as App);

const props = defineProps({
  code: {
   type:  String,
   default:'Fm'
  },
  manage: {
    type: Boolean,
    default: true,
  },
});

const emits = defineEmits(['select']);

const menuInstRef = ref();
/**
 * 默认展开项目
 * */
const defaultExpandedMenuId = ref<string[]>([]);

const menus = computed(() => [
  {
    name: '封面',
    type: 'Fm',
  },
  // {
  //   name: '基础登记表',
  //   type: 'Fourthdetail'
  // },
  {
    name: '基础登记表',
    type: props.manage ? 'Basic' : 'Fourthdetail',
  },
  {
    name: 'gps测点登记表',
    type: 'Gps',
  },
  {
    name: '文物标本登记表',
    type: 'Specimen',
  },
  {
    name: '其他资料登记表',
    type: 'Other',
  },
  {
    name: '文物图纸登记表',
    type: props.manage ? 'Drawing' : 'DrawingView',
  },
  {
    name: '照片页册登记表',
    type: props.manage ? 'Photograph' : 'PhotographView',
  },
  {
    name: '单体文物登记表',
    type: 'Monomer',
  },
  {
    name: '附属文物登记表',
    type: 'Subsidiary',
  },
]);

const selectCode = ref();

function handleUpdateValue(key: string, item: MenuOption) {
  emits('select', key, item);
}

/**
 * 菜单改变后同步高亮
 * */
watch(
  () => props.code,
  (code?: string) => {
    nextTick(() => {
      selectCode.value = code;
      if (code) {
        menuInstRef.value?.showOption(code);
      }
    });
  },
  { immediate: true },
);
</script>

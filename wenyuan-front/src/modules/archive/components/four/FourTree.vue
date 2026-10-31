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
import setupFourPageComponents from './page';
import { tree } from '../../api/four/fourCulturalRecordMenu.ts';
import { transformToTreeFormat, getAllParentNodeByParam } from '@/utils';
import { MenuOption, TreeOption } from 'naive-ui';
import { nextTick, ref, watch, getCurrentInstance, App } from 'vue';
import { useRoute, useRouter } from 'vue-router';

setupFourPageComponents(getCurrentInstance()?.appContext.app as App);

const props = defineProps({ code: { type: String }, showFile: { type: Boolean, default: null } });

const emits = defineEmits(['select', 'loadSuccess']);

const menuInstRef = ref();
/**
 * 默认展开项目
 * */
const defaultExpandedMenuId = ref<string[]>([]);

const menus = ref();

const selectCode = ref();
const route = useRoute();
const router = useRouter();

function nodeProps({ option }: { option: TreeOption }) {
  return {
    onClick() {
      //处理选中与取消选择的情况
      if (!option.children && selectCode.value !== option.type) {
        router.push({
          query: {
            ...route.query,
            code: option.type as string,
            title: option.name as string,
          },
        });
      }
    },
  };
}

function handleUpdateValue(key: string, item: MenuOption) {
  emits('select', key, item);
}

/**
 * 加载菜单
 * */
function handleLoadData() {
  tree({ showFile: props.showFile }).then((resp: any) => {
    if (resp.data) {
      menus.value = transformToTreeFormat(resp.data || [], { pIdKey: 'parentId' });
      emits('loadSuccess', menus.value);
      if (selectCode.value) {
        //展开选中项目
        let parents = getAllParentNodeByParam(menus.value, selectCode.value, 'type');
        defaultExpandedMenuId.value = parents?.map((item: any) => item.type) || [];
      }
    }
  });
}

handleLoadData();

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

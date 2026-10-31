<template>
  <n-menu :options="menus" ref="menuInstRef" v-bind="$attrs" label-field="name" key-field="type"
    :default-expanded-keys="defaultExpandedMenuId" :watch-props="['defaultExpandedKeys']" accordion
    v-model:value="selectCode" @update:value="handleUpdateValue"  />
</template>
<script setup lang="ts">
import setupThirdPageComponents from './page';
import { MenuOption, TreeOption } from 'naive-ui';
import { nextTick, ref, watch, getCurrentInstance, App } from 'vue'

setupThirdPageComponents(getCurrentInstance()?.appContext.app as App);

const props = defineProps(
  { code: String }//选择菜单编码
)

const emits = defineEmits(['select'])

const menuInstRef = ref();
/**
 * 默认展开项目
 * */
const defaultExpandedMenuId = ref<string[]>([])

const menus = [
  {
    name: '封面',
    type: 'Fm'
  },
  {
    name: '基础登记表',
    type: 'Basic'
  },
  {
    name: 'GPS测点登记表',
    type: 'Gps'
  },
  {
    name: '标本登记表',
    type: 'Specimen'
  },
  {
    name: '其他资料登记表',
    type: 'Other'
  },
  {
    name: '图纸页册',
    type: 'Drawing'
  },
  {
    name: '照片页册',
    type: 'Photograph'
  },
  {
    name: '消失文物登记',
    type: 'Disappear'
  },
];

const selectCode = ref()

function handleUpdateValue(key: string, item: MenuOption) {
  emits('select', key, item)
}

/**
 * 菜单改变后同步高亮
 * */
watch(() => props.code, (code?: string) => {
  nextTick(() => {
    selectCode.value = code
    if (code) {
      menuInstRef.value?.showOption(code)
    }
  })
}, { immediate: true })
</script>

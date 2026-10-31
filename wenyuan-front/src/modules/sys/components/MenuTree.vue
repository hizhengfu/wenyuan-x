<template>
  <n-tree
    block-node
    v-bind="$attrs"
    :data="menus"
    label-field="name"
    key-field="id"
    :node-props="nodeProps"
    selectable
  />
  <n-dropdown
    trigger="manual"
    placement="bottom-start"
    :show="sidebarShowDropdown"
    :options="sidebarDropdownOptions"
    :x="dropdownX"
    :y="dropdownY"
    @select="handleDropdownSelect"
    @clickoutside="handleDropdownClickOutside"
  />
</template>
<script lang="ts" setup>
import { treeAll } from '../api/menu';
import { h, ref, useAttrs, watchEffect, watch } from 'vue';
import { DropdownOption, NIcon, TreeOption } from 'naive-ui';
import EditIcon from '~icons/mdi/square-edit-outline';
import DeleteIcon from '~icons/mdi/delete';
import { debounce } from 'lodash-es';

const props = defineProps({
  params: {
    type: Object,
    default() {
      return {};
    },
  },
});

const attrs = useAttrs();
const menus = ref();
const emits = defineEmits(['update:value', 'update:option', 'edit', 'delete']);

const activeTreeOption = ref();

function nodeProps({ option }: { option: TreeOption }) {
  return {
    onClick() {
      //处理选中与取消选择的情况
      if (attrs.value !== option.id) {
        emits('update:value', option.id);
      } else {
        emits('update:value', '');
      }
    },
    onContextmenu(e: MouseEvent): void {
      activeTreeOption.value = option;
      sidebarShowDropdown.value = true;
      dropdownX.value = e.clientX;
      dropdownY.value = e.clientY;
      e.preventDefault();
    },
  };
}

const fetchList = debounce(fetchSourceList, 100);

function fetchSourceList() {
  treeAll(props.params).then((resp: any) => {
    if (resp) {
      menus.value = resp || [];
    }
  });
}

//侧边拦下拉菜单逻辑
const sidebarShowDropdown = ref(false);
const sidebarDropdownOptions = ref<DropdownOption[]>([
  {
    icon() {
      return h(NIcon, null, {
        default: () => h(EditIcon),
      });
    },
    label: '编辑',
    key: 'edit',
  },
  {
    icon() {
      return h(NIcon, null, {
        default: () => h(DeleteIcon),
      });
    },
    label: '删除',
    key: 'delete',
  },
]);
const dropdownX = ref(0);
const dropdownY = ref(0);

function handleDropdownSelect(key: string) {
  sidebarShowDropdown.value = false;
  if (key === 'delete') {
    emits('delete', activeTreeOption.value);
  } else if (key === 'edit') {
    emits('edit', activeTreeOption.value);
  }
}

function handleDropdownClickOutside() {
  sidebarShowDropdown.value = false;
}

watch(
  () => props.params,
  () => {
    fetchList();
  },
  {
    immediate: true,
    deep: true,
  },
);

/**
 * 刷新
 * */
function handleReload() {
  fetchList();
}

defineExpose({ handleReload });
</script>

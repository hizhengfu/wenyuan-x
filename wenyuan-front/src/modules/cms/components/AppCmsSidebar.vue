<template>
  <n-layout-sider
    bordered
    collapse-mode="width"
    :collapsed-width="64"
    :width="240"
    :native-scrollbar="false"
    show-trigger="bar"
    v-bind="$attrs"
    @update:collapsed="handleCollapsed"
  >
    <n-layout-header bordered style="padding: 5px">
      <n-form-item label="站点" label-placement="left" :show-feedback="false" v-show="!appStore.sideCollapse">
        <SiteSelect v-model:value="siteId" readonly></SiteSelect>
      </n-form-item>
    </n-layout-header>
    <n-menu
      ref="menuInstRef"
      v-model:value="selectMenuId"
      :collapsed-width="64"
      :collapsed-icon-size="22"
      :render-icon="renderMenuIcon"
      :render-label="renderMenuLabel"
      label-field="name"
      key-field="id"
      :default-expanded-keys="defaultExpandedKeys"
      :inverted="inverted"
      accordion
      :options="menus"
    />
  </n-layout-sider>
</template>
<script lang="ts" setup>
import { MenuInst, MenuOption, NIcon, NLayoutSider } from 'naive-ui';
import { useAppStore, useAuthStore } from '@/store';
import { computed, h, nextTick, ref, VNodeChild, watchEffect } from 'vue';
import SiteSelect from '../components/form/field/SiteSelect.vue';
import { getNodeByParam, transformToTreeFormat } from '@/utils/tree';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import DynamicIcon from '@/components/basic/DynamicIcon.vue';
import { tree } from '../api/category';

import { useCmsStore } from '../store/cms';

const cmsStore = useCmsStore();
const authStore = useAuthStore();
const appStore = useAppStore();
const route = useRoute();
const router = useRouter();

const props = defineProps({
  inverted: { type: Boolean, default: false },
});

/**
 * 菜单id
 * */
const selectMenuId = ref();
const defaultExpandedKeys = ref<any[]>([]);

/**
 * 当前选中的站点
 * */
const siteId = computed<string>({
  get() {
    return cmsStore.siteId as string;
  },
  set(value) {
    cmsStore.siteId = value;
  },
});
/**
 * 菜单实例
 * */
const menuInstRef = ref<MenuInst | null>(null);

/**
 * 获取菜单数据
 * */
const menus = ref();

function loadCategory(params: any) {
  tree(params || {}).then((res) => {
    if (res) {
      menus.value = transformToTreeFormat(res.data || [], { pIdKey: 'parentId' });
      if (menus.value && menus.value.length === 1) {
        defaultExpandedKeys.value.push(menus.value[0].id);
      }
      cmsStore.categoryList = menus.value;
      nextTick(() => {
        if (selectMenuId.value) {
          menuInstRef.value?.showOption(selectMenuId.value);
        }
      });
    }
  });
}

/**
 * 渲染目录菜单
 * */
function renderMenuIcon(option: MenuOption) {
  let icon = 'mdi:text-box-outline';
  if (option.children) {
    icon = 'mdi:folder-outline';
  }
  return h(NIcon, null, { default: () => h(DynamicIcon, { icon: icon }) });
}

/**
 * 渲染显示标签
 * */
function renderMenuLabel(option: MenuOption): VNodeChild {
  let name = option.name as string;
  let module = option.module as string;
  let categoryId = option.id as string;
  if (!option.children) {
    return h(
      RouterLink,
      {
        to: {
          path: '/publish/' + module?.toLowerCase(),
          query: {
            categoryId: categoryId,
            title: name,
          },
        },
      },
      { default: () => name },
    );
  } else {
    return h('span', {}, name as string);
  }
}

/**
 * 根据参数初始化页面
 * */
function initPage() {
  if (selectMenuId.value) {
    let current = getNodeByParam(menus.value, selectMenuId.value);
    if (current) {
      router.push({
        path: '/publish/' + current.module?.toLowerCase(),
        query: {
          categoryId: current.id,
          title: current.name,
        },
      });
    }
  }
}

/**
 *  边栏折叠事件
 * */
function handleCollapsed(collapsed: boolean) {
  appStore.sideCollapse = collapsed;
}

watchEffect(() => {
  if (siteId.value) {
    loadCategory({ siteId: siteId.value });
  } else {
    menus.value = [];
  }
});

watchEffect(() => {
  selectMenuId.value = route.query.categoryId;
});
</script>

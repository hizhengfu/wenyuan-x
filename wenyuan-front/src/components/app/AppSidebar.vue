<template>
  <n-layout-sider
    bordered
    collapse-mode="width"
    :collapsed-width="64"
    :width="width"
    class="print-hidden"
    v-model:collapsed="appStore.sideCollapse"
    :native-scrollbar="false"
    :inverted="inverted"
    show-trigger="bar"
    v-bind="$attrs"
    @update:collapsed="handleCollapsed"
  >
    <n-layout-header v-if="showHeader" class="bg-transparent" :bordered="!inverted">
      <n-text
        tag="a"
        class="flex flex-center decoration-none h-60px"
        :type="inverted ? 'default' : 'primary'"
        href="./"
      >
        <slot name="sidebar-logo">
          <icon-c-logo v-if="showLogo" class="h-60px w-60px mr-5px inline"></icon-c-logo>
        </slot>
        <slot name="sidebar-title" v-if="showTitle">
          <span
            class="text-xl text-center block px-3 animate-bounce-in"
            :class="[inverted ? 'c-white' : '']"
            :title="title"
            v-show="!appStore.sideCollapse"
          >
            {{ abbrTitle }}
          </span>
        </slot>
      </n-text>
    </n-layout-header>
    <n-layout-content position="absolute" :style="{ top: showHeader ? '64px' : '0' }" class="bg-transparent">
      <n-menu
        ref="menuInstRef"
        :collapsed-width="64"
        :collapsed-icon-size="22"
        :render-icon="renderMenuIcon"
        :render-label="renderMenuLabel"
        :default-expanded-keys="defaultExpandedMenuId"
        :watch-props="['defaultExpandedKeys']"
        label-field="name"
        :inverted="inverted"
        key-field="id"
        v-model:value="selectMenuId"
        accordion
        :options="menus"
      />
    </n-layout-content>
    <!--    <n-layout-footer style="bottom: 0;" bordered position="absolute">-->
    <!--      <n-button @click="appStore.sideCollapse=!appStore.sideCollapse">-->
    <!--        <n-icon size="large">-->
    <!--          <IconMdiChevronDoubleLeft></IconMdiChevronDoubleLeft>-->
    <!--        </n-icon>-->
    <!--      </n-button>-->
    <!--    </n-layout-footer>-->
  </n-layout-sider>
</template>
<script lang="ts" setup>
import { MenuOption, NIcon, NLayoutSider } from 'naive-ui';
import { useAppStore, useAuthStore } from '@/store';
import { computed, h, nextTick, ref, VNodeChild, watch } from 'vue';
import { getAllParentNodeByParam, getNodeByParam } from '@/utils/tree';
import { RouterLink, useRoute } from 'vue-router';
import qs from 'qs';
import DynamicIcon from '../basic/DynamicIcon.vue';

const authStore = useAuthStore();
const appStore = useAppStore();
const route = useRoute();

const props = defineProps({
  inverted: { type: Boolean, default: false },
  showHeader: { type: Boolean, default: true },
  showLogo: { type: Boolean, default: true },
  showTitle: { type: Boolean, default: true },
  width: { type: Number, default: 260 },
  directoryIconsOnly: { type: Boolean, default: false },
});

/**
 * 菜单id
 * */
const selectMenuId = ref('');

const title = appStore.title;
const abbrTitle = appStore.abbrTitle;
/**
 * 默认展开项目
 * */
const defaultExpandedMenuId = ref<string[]>([]);
/**
 * 菜单实例
 * */
const menuInstRef = ref();

/**
 * 获取菜单数据
 * */
const menus = computed(() => {
  /*let m = authStore.menus && authStore.menus.map((item: any) => {
      delete item.icon
      return item
  })*/
  //如果是portal首页，则仅仅显示当前模块下的数据
  const portalId = authStore.portalId;
  if (portalId) {
    let current = getNodeByParam(authStore.menus, portalId);
    if (current) {
      // selectMenuId.value = authStore.portalId
      defaultExpandedMenuId.value = [portalId];
      return [current];
    } else {
      return [];
    }
  }
  return authStore.menus;
});

function normalizePath(path?: string) {
  return (path || '').split('?')[0];
}

function isLeafMenu(node: any) {
  return Boolean(node?.path) && (!node.children || node.children.length === 0);
}

function collectLeafMenus(nodes: any[] = []): any[] {
  return nodes.flatMap((node) => {
    if (isLeafMenu(node)) {
      return [node];
    }
    return collectLeafMenus(node.children || []);
  });
}

function findMenuIdByPath(path?: string) {
  const currentPath = normalizePath(path);
  if (!currentPath) {
    return '';
  }

  const leafMenus = collectLeafMenus((menus.value as any[]) || []);
  const exactMatch = leafMenus.find((item) => normalizePath(item.path) === currentPath);
  if (exactMatch?.id) {
    return exactMatch.id as string;
  }

  const prefixMatch = leafMenus
    .filter((item) => {
      const menuPath = normalizePath(item.path);
      return menuPath && (currentPath === menuPath || currentPath.startsWith(menuPath + '/'));
    })
    .sort((a, b) => normalizePath(b.path).length - normalizePath(a.path).length)[0];

  return (prefixMatch?.id as string) || '';
}

function syncMenuSelection() {
  const queryMenuId = route.query.menuId as string;
  const matchedMenuId = findMenuIdByPath(route.path);
  const cachedMenuId = getNodeByParam(menus.value as any[] | undefined, selectMenuId.value as string)?.id || '';
  const shouldClear = route.path === '/' || /^\/login$/.test(route.path) || /^\/error\//.test(route.path);
  const resolvedMenuId = queryMenuId || matchedMenuId || (shouldClear ? '' : cachedMenuId);

  selectMenuId.value = resolvedMenuId;

  if (authStore.portalId) {
    defaultExpandedMenuId.value = [authStore.portalId];
  } else if (resolvedMenuId) {
    defaultExpandedMenuId.value = (getAllParentNodeByParam(menus.value as any[] | undefined, resolvedMenuId) || []).map(
      (item: any) => item.id,
    );
  } else {
    defaultExpandedMenuId.value = [];
  }

  nextTick(() => {
    if (resolvedMenuId) {
      menuInstRef.value?.showOption(resolvedMenuId);
    }
  });
}

/**
 * 渲染目录菜单
 * */
const menuIconRules: Array<[string, string]> = [
  ['首页', 'mdi:home-outline'],
  ['看板', 'mdi:view-dashboard-outline'],
  ['统计', 'mdi:chart-box-outline'],
  ['报表', 'mdi:file-chart-outline'],
  ['报告', 'mdi:file-document-outline'],
  ['预警', 'mdi:alert-outline'],
  ['告警', 'mdi:bell-alert-outline'],
  ['监测', 'mdi:radar'],
  ['指标', 'mdi:target'],
  ['承诺', 'mdi:clipboard-check-outline'],
  ['日常', 'mdi:calendar-check-outline'],
  ['年度', 'mdi:calendar-month-outline'],
  ['定期', 'mdi:clipboard-clock-outline'],
  ['基础', 'mdi:database-outline'],
  ['资料', 'mdi:folder-text-outline'],
  ['图纸', 'mdi:draw'],
  ['手册', 'mdi:book-open-page-variant-outline'],
  ['帮助', 'mdi:help-circle-outline'],
  ['机构', 'mdi:office-building-outline'],
  ['能力', 'mdi:account-supervisor-outline'],
  ['格局', 'mdi:vector-polygon'],
  ['遗产', 'mdi:bank-outline'],
  ['要素', 'mdi:shape-outline'],
  ['病害', 'mdi:virus-outline'],
  ['自然', 'mdi:leaf-outline'],
  ['气象', 'mdi:weather-partly-cloudy'],
  ['建设', 'mdi:crane'],
  ['控制', 'mdi:tune-variant'],
  ['社会', 'mdi:account-group-outline'],
  ['旅游', 'mdi:map-marker-path'],
  ['巡查', 'mdi:clipboard-search-outline'],
  ['维护', 'mdi:tools'],
  ['安消防', 'mdi:fire-extinguisher'],
  ['消防', 'mdi:fire-extinguisher'],
  ['考古', 'mdi:shovel'],
  ['保护', 'mdi:shield-check-outline'],
  ['规划', 'mdi:map-outline'],
  ['技术', 'mdi:ruler-square'],
  ['线索', 'mdi:lightbulb-on-outline'],
  ['案件', 'mdi:briefcase-outline'],
  ['移送', 'mdi:send-outline'],
  ['督办', 'mdi:clipboard-alert-outline'],
  ['投诉', 'mdi:message-alert-outline'],
  ['举报', 'mdi:bullhorn-outline'],
  ['执行', 'mdi:play-circle-outline'],
  ['交换', 'mdi:swap-horizontal'],
  ['任务', 'mdi:format-list-checks'],
  ['内容', 'mdi:file-document-edit-outline'],
  ['执法', 'mdi:gavel'],
  ['人员', 'mdi:account-tie-outline'],
  ['队伍', 'mdi:account-group-outline'],
  ['列表', 'mdi:format-list-bulleted'],
  ['管理', 'mdi:cog-outline'],
];

function resolveMenuIcon(option: MenuOption) {
  if (option.iconText) return option.iconText as string;

  const name = String(option.name || '');
  const path = String(option.path || '');
  const matched = menuIconRules.find(([keyword]) => name.includes(keyword) || path.includes(keyword));
  return matched?.[1] || '';
}

function renderMenuIcon(option: MenuOption) {
  if (props.directoryIconsOnly && (!option.children || option.children.length === 0)) {
    return null;
  }

  const icon = resolveMenuIcon(option);
  return icon ? h(NIcon, null, { default: () => h(DynamicIcon, { icon }) }) : null;
}

/**
 * 渲染显示标签
 * */
function renderMenuLabel(option: MenuOption): VNodeChild {
  let path = option.path as string;
  let name = option.name as string;
  let menuId = option.id as string;

  let node = null;
  if (path && !option.children) {
    //针对链接的情况直接跳转
    if (/^http/.test(path)) {
      node = h('a', { href: option.path, target: '_blank' }, name as string);
    } else {
      let query = { menuId: menuId, title: name };
      if (authStore.portalId) {
        //@ts-ignored
        query.portalId = authStore.portalId;
      }
      if (path && path.indexOf('?') > -1) {
        let q = path.split('?').pop();
        if (q) {
          Object.assign(query, qs.parse(q));
        }
      }

      node = h(
        RouterLink,
        {
          to: {
            path,
            query: query,
          },
        },
        { default: () => name },
      );
    }
  } else {
    node = h('span', {}, name as string);
  }
  return node;
}

/**
 *  边栏折叠事件
 * */
function handleCollapsed(collapsed: boolean) {
  appStore.sideCollapse = collapsed;
}

/**
 * 菜单改变后同步高亮
 * */
watch([() => route.path, () => route.query.menuId, () => menus.value], syncMenuSelection, { immediate: true });
</script>

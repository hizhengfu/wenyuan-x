<template>
  <n-breadcrumb>
    <n-breadcrumb-item v-for="breadcrumb in breadcrumbs" :href="breadcrumb.to">
      <n-icon>
        <DynamicIcon :icon="breadcrumb.icon"></DynamicIcon>
      </n-icon>
      {{ breadcrumb.title }}
    </n-breadcrumb-item>
  </n-breadcrumb>
</template>
<script lang="ts" setup>
import { useAuthStore } from '@/store';
import { getAllParentNodeByParam } from '@/utils/tree';
import { useRoute } from 'vue-router';
import { computed } from 'vue';
import DynamicIcon from '../basic/DynamicIcon.vue';

const authStore = useAuthStore();

const route = useRoute();

const breadcrumbs = computed(() => {
  let matched = route.matched;
  let menuId = route.query.menuId;
  let breadcrumbs: any[] = [];
  if (menuId) {
    let menus = getAllParentNodeByParam(authStore.menus, menuId);
    if (menus) {
      breadcrumbs.push({
        title: '首页',
        icon: 'mdi:home',
        to: `${APP_HOME_URL || './'}`,
        exact: true,
      });

      menus.forEach((item, index) => {
        let path;
        if (menuId === item.id) {
          path = `${item.path}?menuId=${item.id}&name=${item.name}`;
          // path = {path: item.path, query: {menuId: item.id, name: item.name}}
        } else {
          path = `/menu?menuId=${item.id}`;
          // path = {path: '/menu', query: {menuId: item.id}}
        }

        breadcrumbs.push({
          title: item.name,
          // to: path,
          icon: item.iconText,
          exact: true,
          disabled: true,
        });
      });
    }
  } else {
    let path = route.path;
    if (!/^\/error\//.test(path)) {
      matched.forEach((item) => {
        let breadcrumb: any = null;
        if (item.path === '/') {
          breadcrumb = {
            title: '首页',
            icon: 'mdi:home',
            to: `${APP_HOME_URL || './'}`,
            exact: true,
            disabled: path === item.path,
          };
        } else {
          let title = item.meta.title;
          let path = item.path.replace(/\//, '');
          if (title) {
            breadcrumb = {
              title: title,
              // to: item.path,
              exact: true,
              disabled: path === item.path,
            };
          }
        }
        if (breadcrumb) {
          if (!breadcrumbs.find((item) => item.title == breadcrumb.title)) {
            breadcrumbs.push(breadcrumb);
          }
        }
      });
      if (route.path !== '/') {
        breadcrumbs.unshift({ title: '首页', icon: 'mdi:home', to: `${APP_HOME_URL || './'}` });
      }
    }
  }
  return breadcrumbs;
});
</script>

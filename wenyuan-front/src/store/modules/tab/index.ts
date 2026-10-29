import type { RouteLocationNormalizedLoaded } from 'vue-router';
import { defineStore } from 'pinia';
import { getIndexInTabRoutes, getTabRoute, getTabRouteByVueRoute, isInTabRoutes, routerPush } from './helpers';
import { useStorage } from '@vueuse/core';
import { GlobalTabRoute } from '@/interface';

export const useTabStore = defineStore('tab-store', {
  state: () => ({
    /** 多页签数据 */
    tabs: useStorage<GlobalTabRoute[]>('__TAB_ROUTES__' + APP_TARGET.toUpperCase(), []),
    /** 当前激活状态的页签(路由path) */
    activeTab: '',
  }),
  getters: {
    /** 当前激活状态的页签索引 */
    activeTabIndex(state) {
      const { tabs, activeTab } = state;
      return tabs.findIndex((tab) => tab.path === activeTab);
    },
  },
  actions: {
    /**
     * 设置当前路由对应的页签为激活状态
     * @param path - 路由path
     */
    setActiveTab(path: string) {
      this.activeTab = path;
    },
    /**
     * 添加多页签
     * @param route - 路由
     */
    addTab(route: RouteLocationNormalizedLoaded) {
      if (route.query.title || route.path === '/') {
        let index = getIndexInTabRoutes(this.tabs, route.path);
        if (index === -1) {
          this.tabs.push(getTabRouteByVueRoute(route));
        } else {
          this.tabs[index]['query'] = route.query;
        }
        this.setActiveTab(route.path);
      }
      // tabStore.value = this.tabs
    },
    /**
     * 删除多页签
     * @param path - 路由path
     */
    removeTab(path: string) {
      const isActive = this.activeTab === path;
      const updateTabs = this.tabs.filter((tab) => tab.path !== path);
      this.tabs = updateTabs;
      // tabStore.value = this.tabs
      if (isActive && updateTabs.length) {
        const activePath = updateTabs[updateTabs.length - 1].path;
        this.setActiveTab(activePath);

        routerPush(getTabRoute(this.tabs, activePath));
      }
    },
    /**
     * 删除当前tab
     * */
    removeActiveTab() {
      this.removeTab(this.activeTab);
    },
    /**
     * 清空多页签(多页签首页保留)
     * @param excludes - 保留的多页签path
     */
    clearTab(excludes: string[] = []) {
      const homePath = '/';
      const remain = [homePath, ...excludes];
      const hasActive = remain.includes(this.activeTab);
      const updateTabs = this.tabs.filter((tab) => remain.includes(tab.path));
      this.tabs = updateTabs;
      if (!hasActive && updateTabs.length) {
        const activePath = updateTabs[updateTabs.length - 1].path;
        this.setActiveTab(activePath);
        routerPush(getTabRoute(this.tabs, activePath));
      }
      // tabStore.value = this.tabs
    },
    /**
     * 清除左边多页签
     * @param path - 路由path
     */
    clearLeftTab(path: string) {
      const index = getIndexInTabRoutes(this.tabs, path);
      if (index > -1) {
        const excludes = this.tabs.slice(index).map((item) => item.path);
        this.clearTab(excludes);
      }
    },
    /**
     * 清除右边多页签
     * @param path - 路由path
     */
    clearRightTab(path: string) {
      const index = getIndexInTabRoutes(this.tabs, path);
      if (index > -1) {
        const excludes = this.tabs.slice(0, index + 1).map((item) => item.path);
        this.clearTab(excludes);
      }
    },
    /**
     * 点击单个tab
     * @param path - 路由path
     */
    handleClickTab(path: string) {
      const isActive = this.activeTab === path;
      if (!isActive) {
        this.setActiveTab(path);
        let route = getTabRoute(this.tabs, path);
        routerPush(route);
      }
    },
    /**
     * 记录tab滚动位置
     * @param path - 路由path
     * @param position - tab当前页的滚动位置
     */
    recordTabScrollPosition(path: string, position: { left: number; top: number }) {
      const index = getIndexInTabRoutes(this.tabs, path);
      if (index > -1) {
        this.tabs[index].scrollPosition = position;
      }
    },
    /**
     * 获取tab滚动位置
     * @param path - 路由path
     */
    getTabScrollPosition(path: string) {
      const position = {
        left: 0,
        top: 0,
      };
      const index = getIndexInTabRoutes(this.tabs, path);
      if (index > -1) {
        Object.assign(position, this.tabs[index].scrollPosition);
      }
      return position;
    },
    /** 初始化Tab状态 */
    initTabStore(currentRoute: RouteLocationNormalizedLoaded) {
      const tabs: GlobalTabRoute[] = this.tabs;
      const hasCurrent = isInTabRoutes(tabs, currentRoute.path);
      if (!hasCurrent) {
        tabs.push(getTabRouteByVueRoute(currentRoute));
      }
      this.tabs = tabs;
      this.setActiveTab(currentRoute.path);
    },
  },
});

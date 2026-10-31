import { defineStore } from 'pinia';
import { AppState } from './types';
import { nextTick } from 'vue';
import { useStorage } from '@vueuse/core';
import { modules as fetchModules } from '@/modules/sys/api/config';

/**
 * 通知中心后端模块技术码（= 后端 io.hizhengfu.cloud.message.domain.MODULE_NOTIFY = sys_config.module）。
 * installedModules 来自 /config/modules（已安装且有系统参数的模块 id 列表），这里据此判断通知中心是否可用。
 */
const MODULE_NOTIFY = 'notify';

export const useAppStore = defineStore('app-store', {
  state: (): AppState => ({
    title: APP_TITLE,
    applicationId: '1', //应用id
    abbrTitle: APP_ABBR_TITLE,
    copyright: APP_COPYRIGHT,
    path: '/',
    uploading: false, //文件上传中
    reloadFlag: false,
    silent: false,
    settingDrawerVisible: false,
    fullscreen: false,
    tableSize: useStorage<'small' | 'medium' | 'large'>('__APP_TABLE_SIZE__', 'medium'),
    sideCollapse: useStorage<boolean>('__APP_SIDE_COLLAPSE__', false),
    mixSideFixed: false,
    installedModules: [],
  }),
  getters: {
    /** 通知中心模块是否已装载 → 决定通知中心入口/铃铛是否渲染 */
    messageEnabled(state): boolean {
      return state.installedModules.includes(MODULE_NOTIFY);
    },
  },
  actions: {
    /**
     * 重载页面
     * @param duration - 重载的延迟时间(ms)
     */
    async reloadPage(duration = 0) {
      this.reloadFlag = true;
      await nextTick();
      if (duration) {
        setTimeout(() => {
          this.reloadFlag = false;
        }, duration);
      } else {
        this.reloadFlag = false;
      }
      setTimeout(() => {
        document.documentElement.scrollTo({ left: 0, top: 0 });
      }, 100);
    },
    /** 设置应用id */
    setApplicationId(applicationId: string) {
      this.applicationId = applicationId;
    },
    /** 打开设置抽屉 */
    openSettingDrawer() {
      this.settingDrawerVisible = true;
    },
    /** 关闭设置抽屉 */
    closeSettingDrawer() {
      this.settingDrawerVisible = false;
    },
    /** 切换抽屉可见状态 */
    toggleSettingdrawerVisible() {
      this.settingDrawerVisible = !this.settingDrawerVisible;
    },
    /** 设置侧边栏折叠状态 */
    setSiderCollapse(collapse: boolean) {
      this.sideCollapse = collapse;
    },
    /** 折叠/展开 侧边栏折叠状态 */
    toggleSiderCollapse() {
      this.sideCollapse = !this.sideCollapse;
    },
    /** 设置 vertical-mix模式下 侧边栏的固定状态 */
    setMixSiderIsFixed(isFixed: boolean) {
      this.mixSideFixed = isFixed;
    },
    /** 设置 vertical-mix模式下 侧边栏的固定状态 */
    toggleMixSiderFixed() {
      this.mixSideFixed = !this.mixSideFixed;
    },
    /**
     * 拉取后端已装载的模块 id 列表。
     *
     * 调用时机：登录成功 / 应用 boot（已登录态 reload）。失败时降级为空数组，
     * 所有依赖 messageEnabled 的入口将不渲染，避免 404 噪音。
     */
    async loadInstalledModules() {
      try {
        const resp: any = await fetchModules();
        // /config/modules 现返回 [{id,label}]；兼容旧版 string[]，统一抽成 id 列表作特性开关
        const data = (resp?.data as Array<{ id: string } | string>) || [];
        this.installedModules = data.map((m) => (typeof m === 'string' ? m : m.id));
      } catch {
        this.installedModules = [];
      }
    },
  },
});

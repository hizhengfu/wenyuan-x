import { defineStore } from 'pinia';
import { nextTick } from 'vue';
import axios from 'axios';
import { getCurrentSharedToken, getSessionLoginPath, isOAuth2Authentication, onSharedTokenChange } from '@/utils/auth';
import { startOAuth2Login, startOAuth2Logout } from '@/utils/oauth2Browser';
import { login, logout as logoutApi, userInfo } from '@/modules/sys/api/auth';
import {
  getToken,
  notification,
  removeToken,
  rsaEncrypt,
  setRefreshToken,
  setToken,
  setTokenExpAt,
  transformToTreeFormat,
} from '@/utils';
import { globalRouter } from '@/router';
import { menuTree } from '@/modules/sys/api/menu';
import { AuthState } from './types';
import { isEmpty } from 'lodash-es';
import { useAppStore, useTabStore } from '@/store';

let logoutPromise: Promise<void> | null = null;
let unauthorizedRedirectPromise: Promise<void> | null = null;

export const useAuthStore = defineStore('auth-store', {
  state: (): AuthState => {
    return {
      identityRevision: 0,
      identitySyncing: false,
      showSidebar: false,
      roles: undefined,
      roleCodes: undefined,
      permissions: undefined,
      loginLoading: false,
      menus: undefined,
      portalId: undefined,
      isEncrypt: true,
      resetPassword: false,
      authorizationName: 'Authorization',
      authLogin: login,
      token: getToken(),
    };
  },

  getters: {
    /**
     * 返回拼装好的token
     * */
    getToken(state: AuthState): string | null {
      if (isOAuth2Authentication() || !state.token) return null;
      if (/^Bearer/.test(state.token)) return state.token;
      return 'Bearer ' + state.token;
    },
    /**
     * 判断是否有token
     * */
    hasToken(state: AuthState): boolean {
      return state.token !== '';
    },
    /**
     * 判断是否有对应菜单
     * */
    hasMenu: (state) => (path: string) => {
      return state.menus && state.menus.find((r) => r.path === path);
    },
    /** 按角色主键 ID 判断。 */
    hasRoleId:
      (state) =>
      (roleId: string): boolean => {
        return state.roles?.includes(roleId) ?? false;
      },
    /** 按角色 code 判断；功能授权应使用 hasPermission。 */
    hasRoleCode:
      (state) =>
      (roleCode: string): boolean => {
        return state.roleCodes?.includes(roleCode) ?? false;
      },
    /**
     * 判断权限列表
     * */
    hasPermissionList: (state) => (permission?: string[]) => {
      //无权限信息直接返回
      if (!state.permissions || !permission) {
        return false;
      }
      //权限中有admin返回true
      if (state.permissions.find((p) => p === 'ADMIN')) {
        return true;
      }
      //根据权限查询是否存在对应权限
      return state.permissions.find((p) => permission.includes(p)) != null;
    },
    /**
     * 判断是否有对应权限
     * */
    hasPermission() {
      return (permission?: string | readonly string[]) => {
        const codes = (typeof permission === 'string'
          ? permission.split(',')
          : Array.isArray(permission) ? [...permission] : [])
          .filter((code): code is string => typeof code === 'string')
          .map((code) => code.trim())
          .filter(Boolean);
        return codes.length > 0 && this.hasPermissionList(codes);
      };
    },
    hasPermissionByCode() {
      return (code: string, permission?: string) => {
        //无权限信息直接返回
        if (!this.permissions || !permission) {
          return false;
        }

        return this.hasPermission(`${permission}_ALL,${permission}_${code}`);
      };
    },
    hasPermissionByCodeList() {
      return (code: string[], permission?: string): boolean => {
        //无权限信息直接返回
        if (!this.permissions || !permission) {
          return false;
        }

        let p: string[] = [`${permission}_ALL`];

        code.forEach((c) => {
          p.push(`${permission}_${c}`);
        });
        return this.hasPermissionList(p);
      };
    },
    getUserInfo(state: AuthState): Auth.UserInfo | undefined {
      return state.userInfo;
    },
    getRoles(state: AuthState): string[] {
      return state.roles || [];
    },
    getRoleCodes(state: AuthState): string[] {
      return state.roleCodes || [];
    },
  },

  actions: {
    reconcileSharedIdentity() {
      const token = getCurrentSharedToken();
      if (token !== undefined) this.invalidateSharedIdentity(token);
    },
    startSharedIdentitySynchronization() {
      if (typeof window === 'undefined') return () => {};
      return onSharedTokenChange((token) => this.invalidateSharedIdentity(token));
    },
    invalidateSharedIdentity(token: string) {
      if ((this.token || '') === token) return;
      const revision = ++this.identityRevision;
      this.token = token || undefined;
      this.userInfo = undefined;
      this.permissions = undefined;
      this.roles = undefined;
      this.roleCodes = undefined;
      this.menus = undefined;
      this.showSidebar = false;
      this.resetPassword = false;
      const loginPage = globalRouter?.currentRoute.value.path === '/login';
      this.identitySyncing = !loginPage;
      // Do not use clearUserInfo/clearTab: those erase the shared token or navigate.
      const tabs = useTabStore();
      tabs.tabs = [];
      tabs.activeTab = '';
      if (loginPage) return;
      void nextTick().then(() => {
        if (this.identityRevision !== revision) return;
        try { window.location.reload(); }
        catch {
          notification({ type: 'warning', title: '登录身份已变化', content: '请刷新页面后再办理；旧窗口已关闭。' });
        }
        // Keep the tree blocked if reload is unavailable; never revive the previous form.
      });
    },
    /**
     * 更新登录函数
     * */
    setAuthLogin(fn: Function) {
      this.authLogin = fn;
    },
    /**
     * 设置密码是否加密
     * */
    setAuthEncrypt(isEncrypt: boolean) {
      this.isEncrypt = isEncrypt;
    },
    /** 进入统一认证；多请求同时失效时只发起一次整页跳转。 */
    beginOAuth2Login() {
      const route = globalRouter.currentRoute.value;
      return startOAuth2Login((route.query.redirect as string) || (route.path === '/login' ? '/' : route.fullPath));
    },
    //登陆
    login(data: any) {
      if (isOAuth2Authentication()) {
        this.beginOAuth2Login();
        return Promise.reject(new axios.CanceledError('正在进入统一认证'));
      }
      let encryptData = Object.assign({}, data);
      if (this.isEncrypt) {
        //对密码加密，应对工具扫描的明文漏洞
        Object.assign(encryptData, { password: rsaEncrypt(data.password), mfaCode: data.mfaCode?.join('') });
      }

      const revision = this.identityRevision;
      return this.authLogin(encryptData).then((resp: any) => {
        if (revision !== this.identityRevision) throw new axios.CanceledError('登录身份已变化');
        //需要验证码
        if (resp.captchaRequired === true) return resp;
        //当为true时表示登录认证完成，否则还需要等待二次认证
        if (!resp.step) {
          ++this.identityRevision;
          if (resp.token) {
            setToken(resp.token);
            this.token = resp.token;
            // 兼容 jwt：响应无 refreshToken 时显式清空残留
            setRefreshToken(resp.refreshToken || '');
            setTokenExpAt(resp.expiration ? Date.now() + resp.expiration * 1000 : 0);
          }

          if (resp.user) {
            this.userInfo = resp.user;

            if (resp.user.permissions) {
              this.permissions = resp.user.permissions;
            }
            if (resp.user.roles) {
              this.roles = resp.user.roles;
            }
            if (resp.user.roleCodes) {
              this.roleCodes = resp.user.roleCodes;
            }
          }

          let redirect = globalRouter.currentRoute.value.query.redirect as string;
          // globalRouter.push(redirect || '/')
          globalRouter.replace(redirect || '/');

          notification({
            type: 'success',
            title: '登录成功!',
            content: `欢迎回来，${resp.user.username}!`,
            duration: 3000,
          });
        }

        return resp;
      });
    },
    //清除用户信息
    clearUserInfo() {
      ++this.identityRevision;
      this.userInfo = undefined;
      this.showSidebar = false;
      removeToken();
      let appTab = useTabStore();

      this.token = undefined;
      this.permissions = undefined;
      this.roles = undefined;
      this.roleCodes = undefined;
      this.menus = undefined;
      this.resetPassword = false;

      appTab.clearTab();
    },
    //接口返回未认证时仅清理本地登录态，不重复调用服务端退出接口
    handleUnauthorized(): boolean {
      const route = globalRouter.currentRoute.value;
      const redirect = (route.query.redirect as string) || route.fullPath || '/';

      const loginPath = getSessionLoginPath() || '/login';
      const oauth2 = isOAuth2Authentication();
      this.clearUserInfo();
      if (oauth2) return startOAuth2Login(redirect);

      if (route.path === loginPath || unauthorizedRedirectPromise) {
        return false;
      }

      unauthorizedRedirectPromise = globalRouter
        .replace({ path: loginPath, query: { redirect } })
        .then(() => undefined)
        .catch(() => undefined)
        .finally(() => {
          unauthorizedRedirectPromise = null;
        });
      return true;
    },
    //登出：管理端与独立终端分别结束自己的认证，不清除其他来源的会话。
    logout() {
      const route = globalRouter.currentRoute.value;
      const revision = this.identityRevision;
      const redirect = (route.query.redirect as string) || route.fullPath || '/';
      const sessionLogin = getSessionLoginPath();
      if (sessionLogin) {
        this.clearUserInfo();
        return globalRouter.replace({ path: sessionLogin });
      }
      if (isOAuth2Authentication()) {
        this.clearUserInfo();
        startOAuth2Logout();
        return Promise.resolve();
      }
      if (!logoutPromise) {
        logoutPromise = logoutApi()
          .catch(() => undefined)
          .then(async () => {
            if (this.identityRevision !== revision) return;
            this.clearUserInfo();
            await globalRouter.replace({ path: '/login', query: { redirect } });
          })
          .catch(() => undefined)
          .finally(() => { logoutPromise = null; });
      }
      return logoutPromise;
    },
    //加载用户信息
    fetchInfo() {
      const revision = this.identityRevision;
      return userInfo().then((resp: any) => {
        if (revision !== this.identityRevision || this.identitySyncing) throw new axios.CanceledError('登录身份已变化');
        this.userInfo = resp;
        if (resp.permissions) {
          this.permissions = resp.permissions;
        }
        if (resp.roles) {
          this.roles = resp.roles;
        }
        if (resp.roleCodes) {
          this.roleCodes = resp.roleCodes;
        }
        this.resetPassword = resp.resetPassword || false;

        //无权限的情况下跳转到空界面
        if (!this.permissions || 0 === this.permissions.length) {
          globalRouter.push({ path: '/error/empty' });
        }
        return resp;
      });
    },
    //加载菜单
    fetchMenu() {
      const revision = this.identityRevision;
      let appsStore = useAppStore();
      // 顺带探测已装载后端模块（如 message2 → 'notify'），用于驱动前端通知中心入口的渲染。
      // 失败已在 store 内部静默兜底，不影响菜单加载。
      appsStore.loadInstalledModules();
      const front = APP_TARGET === 'yyjxwbH5' ? 1 : 0;
      return menuTree({ applicationId: appsStore.applicationId, front }).then((resp: any) => {
        if (revision !== this.identityRevision || this.identitySyncing) throw new axios.CanceledError('登录身份已变化');
        let list = resp.map((item: any) => {
          return {
            id: String(item.id),
            parentId: String(item.parentId),
            name: item.name,
            iconText: item.icon,
            path: item.path,
          };
        });
        this.menus = transformToTreeFormat(list, { pIdKey: 'parentId' });
        if (isEmpty(list)) {
          this.showSidebar = false;
        } else {
          this.showSidebar = true;
        }
        return resp;
      });
    },
  },
});

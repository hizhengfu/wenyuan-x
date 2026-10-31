<template>
  <div class="header-wrap">
    <div v-if="showHeader" class="header">
      <div class="header_left">
        <n-icon
          v-if="authStore.showSidebar && showCollapse"
          class="mr-2"
          size="26px"
          @click="appStore.sideCollapse = !appStore.sideCollapse"
        >
          <IconMdiMenuOpen :class="{ 'rotate-180': appStore.sideCollapse }"></IconMdiMenuOpen>
        </n-icon>
        <slot>
          <n-text tag="a" class="flex flex-y-center decoration-none" type="primary" href="/">
            <slot name="header-logo">
              <icon-c-logo v-if="showLogo" class="h-60px w-60px mr-5px inline"></icon-c-logo>
            </slot>
            <span class="text-2xl text-primary" v-if="showTitle">
              {{ appTitle }}
            </span>
          </n-text>
        </slot>
      </div>
      <div class="header_title"></div>
      <div class="header_right" v-if="authStore.userInfo != null">
        <AppSearch v-if="showSearch" class="mr-3 max-lg:hidden"></AppSearch>
        <slot name="header_right">
          <n-tooltip trigger="hover">
            <template #trigger>
              <n-button href="./" text tag="a" class="mr-3">
                <n-icon size="26px">
                  <icon-mdi-home></icon-mdi-home>
                </n-icon>
              </n-button>
            </template>
            返回首页
          </n-tooltip>
        </slot>
        <full-screen class="mr-3 max-md:hidden" text></full-screen>
        <app-notification :refresh-interval="30000"></app-notification>
        <app-user-info></app-user-info>
      </div>
    </div>
    <div class="header-extend" v-if="showTab && authStore.showSidebar && tabStore.tabs.length > 1">
      <AppPageTab></AppPageTab>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { useAppStore, useAuthStore, useTabStore } from '@/store';
import FullScreen from '@/components/basic/FullScreen.vue';
import AppSearch from './AppSearch.vue';
import AppNotification from './AppNotification.vue';
import AppPageTab from './AppPageTab.vue';
import AppUserInfo from './AppUserInfo.vue';

const props = defineProps({
  showHeader: { type: Boolean, default: true },
  showLogo: { type: Boolean, default: true },
  showCollapse: { type: Boolean, default: true }, //是否显示切换按钮
  showSearch: { type: Boolean, default: true }, //是否功能搜索
  showTitle: { type: Boolean, default: true }, //是否显示标题
  showTab: { type: Boolean, default: true },
});
const appStore = useAppStore();
const authStore = useAuthStore();
const tabStore = useTabStore();

const appTitle = appStore.title;
</script>
<style scoped>
.header {
  display: flex;
  height: 60px;
  border-bottom: 1px solid var(--n-border-color);
}

.header_left,
.header_right {
  display: flex;
  align-items: center;
  justify-content: center;
}

.header_left a,
.header_right a {
  display: flex;
  align-items: center;
  justify-content: center;
}

.header_left {
  padding-left: 12px;
  left: 0;
  top: 0;
}

.header_right {
  padding-right: 12px;
  right: 0;
  top: 0;
}

.header_title {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
}

.header-extend {
  margin-top: 5px;
  height: 43px;
  user-select: none;
}
</style>

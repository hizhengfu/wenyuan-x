<template>
  <n-layout class="h-full" has-sider>
    <app-sidebar
      v-if="authStore.showSidebar && !resourceMobile"
      :inverted="inverted"
      :show-header="showSidebarHeader"
      :show-title="showSidebarTitle"
      :show-logo="showSidebarLogo"
      :width="sidebarWidth"
      :directory-icons-only="directoryIconsOnly"
    >
      <template v-for="(item, key) in slots" v-slot:[key]>
        <slot :name="key"></slot>
      </template>
    </app-sidebar>
    <n-layout content-style="display: flex;flex-direction: column;">
      <n-layout-header :bordered="!showTab">
        <app-header :show-header="showHeader" :show-tab="showTab" :show-logo="showHeaderLogo" :show-collapse="!resourceMobile">
          <template v-for="(item, key) in slots" v-slot:[key]>
            <slot :name="key"></slot>
          </template>
          <n-button v-if="resourceMobile && authStore.showSidebar" quaternary circle aria-label="打开导航" @click="resourceNavigationOpen = true">
            <template #icon><n-icon size="22"><IconMdiMenu /></n-icon></template>
          </n-button>
          <span v-if="!authStore.showSidebar" class="text-2xl text-primary">
            {{ appStore.title }}
          </span>
          <AppPageBreadcrumb class="max-md:hidden"></AppPageBreadcrumb>
        </app-header>
      </n-layout-header>
      <n-layout class="flex-auto bg-white" :class="resourceRoute ? 'p-0' : 'p-5'" content-style="display: flex;flex-direction: column;">
        <!--        <n-layout-header bordered>-->
        <!--&lt;!&ndash;          <app-page-header></app-page-header>&ndash;&gt;-->
        <!--        </n-layout-header>-->
        <n-layout-header v-if="slots['main-header']">
          <slot name="main-header"></slot>
        </n-layout-header>
        <n-layout-content class="flex-auto">
          <slot name="main-content">
            <router-view v-slot="{ Component, route }" v-if="init && !appStore.reloadFlag && !authStore.identitySyncing">
              <transition name="fade" mode="out-in" appear>
                <keep-alive>
                  <AppRoutePage :component="Component" :key="route.path" class="flex-auto h-full" />
                </keep-alive>
              </transition>
            </router-view>
          </slot>
          <n-spin v-if="appStore.reloadFlag || authStore.identitySyncing" size="large" style="height: 100%; width: 100%">
            <template #description> 正在刷新</template>
          </n-spin>
        </n-layout-content>
        <n-back-top :right="100" />
      </n-layout>
      <n-layout-footer bordered class="bg-grey py-3">
        <app-footer></app-footer>
      </n-layout-footer>
    </n-layout>
    <n-drawer v-if="resourceMobile" v-model:show="resourceNavigationOpen" placement="left" :width="280">
      <n-drawer-content title="导航" closable :body-content-style="{ padding: '0', height: '100%' }">
        <app-sidebar :show-header="false" :width="280" :collapsed="false" :show-trigger="false" :inverted="false" :directory-icons-only="directoryIconsOnly" style="height:100%" />
      </n-drawer-content>
    </n-drawer>
    <ModelEditPassword
      :show="authStore.resetPassword"
      :enableCancel="false"
      title="密码已过有效期，请立即修改"
    ></ModelEditPassword>
    <!--    <n-watermark
          :content="authStore.userInfo.name"
          fullscreen
          :font-size="16"
          :line-height="16"
          :width="384"
          :height="384"
          :x-offset="12"
          :y-offset="60"
          :rotate="-15"
        />-->
  </n-layout>
</template>
<script lang="ts" setup>
import AppRoutePage from '@/components/app/AppRoutePage.vue';
import { useAppStore, useAuthStore } from '@/store';
import AppHeader from './AppHeader.vue';
import AppSidebar from './AppSidebar.vue';
import AppFooter from './AppFooter.vue';
import AppPageBreadcrumb from './AppPageBreadcrumb.vue';
import ModelEditPassword from '../../modules/sys/components/form/EditPassword.vue';
import { computed, onMounted, ref, useSlots, watch } from 'vue';
import { useMediaQuery } from '@vueuse/core';
import { useRoute } from 'vue-router';

const slots = useSlots();
const currentRoute = useRoute();
const narrowScreen = useMediaQuery('(max-width: 767px)');
const resourceRoute = computed(() => currentRoute.path === '/resource' || currentRoute.path.startsWith('/resource/'));
const resourceMobile = computed(() => resourceRoute.value && narrowScreen.value);
const resourceNavigationOpen = ref(false);
watch([() => currentRoute.fullPath, resourceMobile], () => { resourceNavigationOpen.value = false; });
const props = defineProps({
  inverted: { type: Boolean, default: true },
  showHeader: { type: Boolean, default: true },
  showTab: { type: Boolean, default: true },
  showSidebarTitle: { type: Boolean, default: true },
  showHeaderLogo: { type: Boolean, default: true },
  showSidebarHeader: { type: Boolean, default: true },
  showSidebarLogo: { type: Boolean, default: true },
  sidebarWidth: { type: Number, default: 260 },
  directoryIconsOnly: { type: Boolean, default: false },
});
const appStore = useAppStore();
const authStore = useAuthStore();

const init = ref(false);
onMounted(() => {
  init.value = true;
});
</script>

<template>
  <n-layout class="h-full" position="absolute">
    <n-layout-header bordered style="height: 64px">
      <AppHeader :show-tab="false"></AppHeader>
    </n-layout-header>
    <n-layout has-sider position="absolute" style="top: 64px">
      <app-cms-sidebar></app-cms-sidebar>
      <n-layout class="flex-auto p-5 p-t-0 bg-gray-100" content-style="display: flex;flex-direction: column">
        <n-layout-header class="bg-transparent">
          <app-page-cms-header></app-page-cms-header>
        </n-layout-header>
        <n-layout-content class="flex-auto">
          <router-view v-slot="{ Component, route }" v-if="!appStore.reloadFlag">
            <transition name="fade" mode="out-in" appear>
              <keep-alive>
                <AppRoutePage :component="Component" :key="route.path" class="flex-auto h-full" />
              </keep-alive>
            </transition>
          </router-view>
        </n-layout-content>
        <n-back-top :right="100" />
      </n-layout>
    </n-layout>
    <app-footer></app-footer>
  </n-layout>
</template>
<script lang="ts" setup>
import AppRoutePage from '@/components/app/AppRoutePage.vue';
import { useAppStore } from '@/store';
import AppCmsSidebar from '../../components/AppCmsSidebar.vue';
import AppHeader from '@/components/app/AppHeader.vue';
import AppFooter from '@/components/app/AppFooter.vue';
import AppPageCmsHeader from '../../components/AppCmsPageHeader.vue';

const appStore = useAppStore();
</script>

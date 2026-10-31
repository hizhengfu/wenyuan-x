<template>
  <div class="main-wrap">
    <app-header>
      <template #header_left>
        <clock></clock>
      </template>
      <template #header_right>
        <app-notice></app-notice>
        <app-user-info></app-user-info>
      </template>
    </app-header>
    <div class="main">
      <n-layout has-sider position="absolute" style="display: flex;flex: 1;border-radius: 5px;margin-top: 15px">
        <app-sidebar></app-sidebar>
        <n-layout content-style="display: flex;flex-direction: column;">
          <n-layout-header bordered>
            <div style="padding: 10px;font-size: 18px;font-weight: bold">
              <span style="border-right: 3px;border-style: solid;border-color: #4B99D7;margin-right: 5px"></span>
              {{ $route.query.title || $route.path }}
            </div>
          </n-layout-header>
          <n-layout-content content-style="display: flex;">
            <router-view v-slot="{ Component }">
              <keep-alive>
                <component :is="Component"/>
              </keep-alive>
            </router-view>
          </n-layout-content>
        </n-layout>
      </n-layout>
    </div>
  </div>
</template>
<script lang="ts" setup>
import Clock from '@/components/basic/Clock.vue'
import AppUserInfo from './AppUserInfo.vue'
import AppNotice from './AppNotice.vue'
import AppHeader from './AppHeader.vue'
import AppSidebar from '@/components/app/AppSidebar.vue'
import type {Component} from 'vue'



</script>

<style scoped>
.info-left, .info-right {
  position: absolute;
  font-size: 18px;
  display: flex;
  align-items: center;
  top: -18px;
  user-select: none;
  z-index: 10;
}

.info-left {
  left: 12px;
}

.info-right {
  right: 12px;
}

</style>

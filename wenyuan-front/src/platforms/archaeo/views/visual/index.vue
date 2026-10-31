<template>
  <n-config-provider :theme="darkTheme" :theme-overrides="themeOverrides" :abstract="true">
    <h-screen-wrap :width="1920" :height="1080" bgcolor="#0d0f14" zoom-mode="auto">
      <HHeader></HHeader>
      <router-view v-slot="{ Component }">
        <keep-alive>
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </h-screen-wrap>
  </n-config-provider>
</template>
<script lang="ts" setup>
import { darkTheme, GlobalThemeOverrides } from 'naive-ui';
import HScreenWrap from '@/components/basic/screen/ScreenWrap.vue';
import HHeader from '../../components/widgets/Header.vue';

const themeOverrides: GlobalThemeOverrides = {
  common: {
    fontSize: '16px',
    primaryColor: '#3dabfd',
    primaryColorHover: '#3dabfd',
    primaryColorPressed: '#3dabfd',
    primaryColorSuppl: '#3dabfd',
  },
  Card: {
    color: '#0E1317',
  },
  List: {
    color: '#0E131766',
  },
  Tree: {
    fontSize: '18px',
    nodeHeight: '50px',
  },
  Tabs: {
    tabFontSizeLarge: '18px',
  },
  Button: {
    fontSizeLarge: '18px',
    textColorPrimary: '#ffffff',
    textColorHoverPrimary: '#eeeeee',
  },
  DataTable: {
    fontSizeLarge: '18px',
  },
  Radio: {
    buttonTextColorActive: '#fff',
    buttonColorActive: '#3dabfd',
    buttonBorderColor: '#3dabfd',
    buttonTextColorHover: '#3dabfd',
    buttonBoxShadowHover: 'rgba(61,171,253,0.5)',
    buttonBoxShadowFocus: '#3dabfd',
    buttonColor: '#00000055',
    textColor: '#3dabfd',
    buttonTextColor: '#3dabfd',
    buttonBorderColorActive: '#3dabfd',
  },
};
</script>

<style lang="scss" scoped></style>

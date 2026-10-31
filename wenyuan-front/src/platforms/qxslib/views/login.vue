<template>
  <div class="login">
    <app-header></app-header>
    <div class="login_body flex-center">
      <n-config-provider :theme-overrides="themeOverrides">
        <login-form :style="cardStyle" :bordered="false" autocomplete="off"></login-form>
      </n-config-provider>
    </div>
  </div>
</template>
<script lang="ts" setup>
import AppHeader from '../components/AppHeader.vue'
import LoginForm from '@/modules/sys/components/form/loginForm.vue'
import {NConfigProvider, GlobalThemeOverrides} from 'naive-ui'

const themeOverrides: GlobalThemeOverrides = {
  Card: {
    titleTextColor: '#ffffff',
    borderColor: '#1883e0'
  },
  Form: {
    labelTextColor: '#ffffff'
  },
  Input: {
    border: 'border: 1px solid #1e4d85',
    borderHover: 'border: 1px solid #1e4d85',
    borderFocus: 'border: 1px solid #1e4d85',
    boxShadowFocus: '0 0 0 2px rgba(30, 77, 133, 0.2)',
    textColor: '#ffffff',
    color: '#1a2b44',
    colorFocus: '#0f223d'
  },
  Button: {
    colorPrimary: '#59b2ff',
    colorHoverPrimary: '#1883e0',
    colorPressedPrimary: '#1883e0',
    colorFocusPrimary: '#1883e0',
    borderPrimary: 'none',
    borderHoverPrimary: 'none',
  }
}


const cardStyle = {
  backgroundImage: 'linear-gradient(0deg, #0a192e 0%, #142b4b 100%)',
  backgroundColor: 'transparent',
  boxShadow: '0 3px 24px 0 #000000',
  borderRadius: '10px',
  color: '#ffffff'
}

</script>
<style scoped>
.login {
  height: 100%;
  background-image: url("../assets/image/bg.png");
  background-color: #083956;
  background-repeat: no-repeat;
  background-position: bottom;
  background-size: cover;
  min-height: 680px;
  display: flex;
  flex-direction: column;

}

.login_body {
  flex: 1;
}
</style>

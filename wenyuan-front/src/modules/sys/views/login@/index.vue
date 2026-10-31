<template>
  <div class="relative wh-full flex-center">
    <LoginBg></LoginBg>
    <LoginForm size="large" :title="loginTitle"></LoginForm>
  </div>
</template>
<script setup lang="ts">
import LoginForm from '../../components/form/loginForm.vue'
import LoginBg from '../../components/loginBg.vue'

const loginTitle = APP_TITLE
</script>

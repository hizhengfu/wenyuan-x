<template>
  <div class="wh-full flex-center flex flex-col">
    <slot></slot>
    {{ route.query.redirect }}
    <button @click="handleRefresh">刷新页面</button>
  </div>
</template>
<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';
const route = useRoute();
const router = useRouter();
function handleRefresh() {
  if (route.query.redirect) {
    router.replace(route.query.redirect as string);
  } else {
    router.replace('/');
  }
}
</script>
<style scoped>
button {
  display: inline-block;
  margin: 30px auto;
  padding: 10px 20px;
  font-size: 16px;
  color: #ffffff;
  background: linear-gradient(135deg, #6a11cb, #2575fc);
  border: none;
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.3s ease;
}
</style>

<template>
  <router-view></router-view>
</template>
<script lang="ts" setup>
import { useVersionCheck } from '../../composable/useVersionCheck.ts';
useVersionCheck();
</script>

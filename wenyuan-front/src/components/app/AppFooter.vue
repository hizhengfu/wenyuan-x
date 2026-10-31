<template>
  <div class="flex-center">
    &copy; {{ year }} {{ appStore.copyright || appStore.title }} 版权所有
  </div>
</template>
<script lang="ts" setup>
import dayjs from 'dayjs';
import {useAppStore} from '@/store';

const appStore = useAppStore()

const year = dayjs().year()

</script>

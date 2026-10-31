<template>
  <NTreeSelect v-bind="$attrs" :options="options" label-field="name" key-field="id" filterable clearable />
</template>

<script setup lang="ts">
import { onBeforeMount, ref } from 'vue';
import { NTreeSelect } from 'naive-ui';
import { transformToTreeFormat } from '@/utils';
import type { MuseumVenueVo } from '../typings';
import { loadVenueCatalog } from '../utils/venueCatalog';

defineOptions({ inheritAttrs: false });

const options = ref<MuseumVenueVo[]>([]);

onBeforeMount(async () => {
  options.value = transformToTreeFormat(await loadVenueCatalog(), { pIdKey: 'parentId' });
});
</script>

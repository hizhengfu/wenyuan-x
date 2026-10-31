<template>
  <n-tree-select v-bind="$attrs" :options="options" label-field="name" key-field="id" />
</template>
<script lang="ts" setup>
import { onBeforeMount, ref, watch } from 'vue';
import { tree, treeAll } from '../../../api/menu';
import { transformToTreeFormat } from '@/utils';

const props = defineProps({
  allMenu: { type: Boolean, default: false },
  applicationId: { type: String },
  front: { type: [Number, String] },
});

const options = ref();

function fetchData() {
  const { allMenu, front, applicationId } = props;
  const params = { front: front, applicationId: applicationId };
  if (allMenu) {
    treeAll(params).then((res) => {
      options.value = transformToTreeFormat(res || [], { pIdKey: 'parentId' });
    });
  } else {
    tree(params).then((res) => {
      options.value = transformToTreeFormat(res || [], { pIdKey: 'parentId' });
    });
  }
}

onBeforeMount(() => {
  fetchData();
});
</script>

<template>
  <n-input-group>
    <n-select filterable :options="funMenus"
              @updateValue="handleSelect"
              clearable
              placeholder="请选择功能项"
              label-field="name" value-field="id"/>
    <n-button type="primary">
      <template #icon>
        <n-icon size="20">
          <IconMdiSearch></IconMdiSearch>
        </n-icon>
      </template>
    </n-button>
  </n-input-group>
</template>
<script setup lang="ts">
import {useAuthStore} from '@/store';
import {globalRouter} from '@/router';
import {transformToArrayFormat} from '@/utils';

const authStore = useAuthStore()

const funMenus = transformToArrayFormat(authStore.menus)?.filter((item) => {
  return !item.children || item.children.length === 0
})

function handleSelect(value: string, item: any) {
  if (item) {
    globalRouter.push({path: item.path, query: {menuId: item.id, title: item.name}})
  }
}
</script>

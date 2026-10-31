<template>
  <n-tooltip trigger="hover">
    <template #trigger>
      <n-button @click="handleReload" v-bind="$attrs">
        <template #icon>
          <n-icon size="20px">
            <IconMdiReload/>
          </n-icon>
        </template>
      </n-button>
    </template>
    点击刷新
  </n-tooltip>
</template>
<script lang="ts" setup>
function handleReload() {
  location.reload()
}
</script>

<template>
  <n-drawer v-model:show="visible" :width="360" placement="right" :to="drawerTarget">
    <n-drawer-content title="大屏组件库" closable>
      <n-alert type="info" :show-icon="false" class="library-tip">
        组件由业务模块注册；标记为单例的组件只能添加一次。
      </n-alert>
      <n-list hoverable clickable>
        <n-list-item
          v-for="widget in widgetRegistry.list()"
          :key="`${widget.key}@${widget.version}`"
          @click="emit('add', widget)"
        >
          <n-thing
            :title="widget.title"
            :description="`${widget.key}@${widget.version} · 默认 ${widget.defaultSize.w}×${widget.defaultSize.h}`"
          />
          <template #suffix><n-button size="small" type="primary">添加</n-button></template>
        </n-list-item>
      </n-list>
      <slot />
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { DatavWidgetManifest } from '../core/types';
import type { DatavWidgetRegistry } from '../registry/widgetRegistry';

const props = defineProps<{ show: boolean; widgetRegistry: DatavWidgetRegistry; drawerTarget: string }>();
const emit = defineEmits<{ 'update:show': [show: boolean]; add: [widget: DatavWidgetManifest] }>();
const visible = computed({ get: () => props.show, set: (show) => emit('update:show', show) });
</script>

<style scoped>
.library-tip { margin-bottom: 12px; }
</style>

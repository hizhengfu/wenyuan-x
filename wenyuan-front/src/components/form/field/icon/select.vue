<template>
  <n-popover placement="bottom-end" trigger="click">
    <template #trigger>
      <n-input v-model:value="modelValue" placeholder="点击选择图标">
        <template #suffix>
          <Icon :icon="modelValue ? modelValue : emptyIcon" class="text-22px p-5px"/>
        </template>
      </n-input>
    </template>
    <template #header>
      <n-input v-model:value="searchValue" placeholder="搜索图标"></n-input>
    </template>
    <div v-if="iconsList.length > 0" class="grid grid-cols-9 h-auto overflow-auto">
      <template v-for="iconItem in iconsList" :key="iconItem">
        <Icon
          :icon="iconItem"
          class="border-1px border-[#d9d9d9] text-22px m-2px p-5px"
          :style="{ 'border-color': modelValue === iconItem ? '#ff0000': '' }"
          @click="handleChange(iconItem)"
        />
      </template>
    </div>
    <n-empty v-else class="w-306px" description="暂无图标"/>
  </n-popover>
</template>

<script lang="ts" setup>
import {ref, computed} from 'vue';
import {Icon} from '@iconify/vue';
import {icons} from './icon'

interface Props {
  /** 选中的图标 */
  value: string;
  /** 图标列表 */
  icons?: string[];
  /** 未选中图标 */
  emptyIcon?: string;
}

const props = withDefaults(defineProps<Props>(), {
  emptyIcon: 'mdi:apps',
  icons: () => {
    return icons
  }
});

interface Emits {
  (e: 'update:value', val: string): void;
}

const emit = defineEmits<Emits>();
const searchValue = ref('');
const iconsList = computed(() => props.icons.filter(v => v.includes(searchValue.value)));
const modelValue = computed({
  get() {
    return props.value;
  },
  set(val: string) {
    emit('update:value', val);
  }
});

function handleChange(iconItem: string) {
  modelValue.value = iconItem;
}
</script>


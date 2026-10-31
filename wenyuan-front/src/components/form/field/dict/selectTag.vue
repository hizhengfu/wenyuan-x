<template>
  <div class="dict-select-tag">
    <n-space :size="[6, 6]" :wrap="true" align="center">
      <n-tag
        v-for="opt in options"
        :key="String(opt.value)"
        round
        :type="isChecked(opt.value) ? 'primary' : 'default'"
        :checkable="!disabled"
        :checked="isChecked(opt.value)"
        :disabled="disabled"
        @update:checked="(c: boolean) => onTagToggle(opt.value, c)"
      >
        {{ opt.label }}
      </n-tag>

      <!-- 选项为空时的占位，避免空元素让父级看不出这是个选择器 -->
      <span v-if="options.length === 0" class="placeholder">{{ emptyText }}</span>

      <!-- 字典管理按钮：与 DictSelect 行为一致，依赖 DICT 权限 -->
      <n-button
        v-if="showAdd && authStore.hasPermissionByCodeList(['CREATE', 'EDIT'], 'DICT')"
        size="tiny"
        type="primary"
        ghost
        @click="refFormModel = true"
      >
        <n-icon size="14">
          <IconMdiPlus />
        </n-icon>
      </n-button>
    </n-space>

    <HDialogForm v-model:show="refFormModel" title="字典管理" size="small">
      <DictList
        :showHeader="false"
        :showToolbar="false"
        :isTree="isTree"
        @update="fetchData(type)"
        :type="type"
      />
    </HDialogForm>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref, shallowRef, triggerRef, watchEffect } from 'vue';
import { type as fetchType } from '@/modules/sys/api/dict';
import type { SelectMixedOption } from 'naive-ui/es/select/src/interface';
import { useAuthStore } from '@/store';
import DictList from './DictList.vue';

/**
 * 字典选择器（Tag 形态）。
 *
 * 与 [DictSelect](./select.vue) 保持完全一致的 props / emits 契约——使用方无需改动：
 *  - value: String | Number | Array
 *  - multiple: 多选；single 时点击已选 tag 可取消
 *  - returnArray: multiple=true 下决定回传 string[] 还是逗号串
 *  - type: 必填，字典 type
 *  - showAdd: 显示字典管理弹窗按钮（DICT 权限）
 *
 * 渲染层从 `<n-select>` 改为 `<n-tag :checkable>` 平铺，**字典项一目了然**，
 * 适合 enum 量不大的场景（通道、业务类型、状态等）；filterable / isTree 在
 * 选项极多 / 树形场景下意义有限，保留为占位 prop（不影响渲染）。
 */
const authStore = useAuthStore();

const props = defineProps({
  value: { type: [String, Number, Array] as any },
  multiple: { type: Boolean, default: false },
  /** 兼容 DictSelect API：tag 形态下用不到 */
  filterable: { type: Boolean, default: true },
  type: { type: String, required: true },
  isTree: { type: Boolean, default: false },
  returnArray: { type: Boolean, default: false },
  showAdd: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无字典项' },
});

const emit = defineEmits(['update:value']);

const options = shallowRef<SelectMixedOption[]>([]);
const refFormModel = ref(false);

/** 把 props.value 归一化为字符串数组，便于 contains 判断 */
const selectedKeys = computed<string[]>(() => {
  const v = props.value;
  if (v == null || v === '') return [];
  if (Array.isArray(v)) return v.map(String);
  return String(v).split(',').filter(Boolean);
});

function isChecked(value: any): boolean {
  return selectedKeys.value.includes(String(value));
}

function onTagToggle(value: any, checked: boolean) {
  const key = String(value);
  if (props.multiple) {
    const next = new Set(selectedKeys.value);
    if (checked) next.add(key);
    else next.delete(key);
    const arr = Array.from(next);
    emit('update:value', props.returnArray ? arr : arr.join(','));
  } else {
    // 单选：点击未选中 → 选中；点击已选中 → 取消
    emit('update:value', checked ? key : null);
  }
}

function fetchData(type: string) {
  fetchType(type, true).then((resp) => {
    options.value = resp?.data || [];
    triggerRef(options);
  });
}

watchEffect(() => {
  fetchData(props.type);
});
</script>

<style scoped>
.dict-select-tag {
  width: 100%;
}
.placeholder {
  font-size: 12px;
  color: #999;
}
</style>

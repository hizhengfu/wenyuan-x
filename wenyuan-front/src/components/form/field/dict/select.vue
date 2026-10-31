<template>
  <n-input-group :class="$attrs.class" :style="$attrs.style">
    <n-select
      :options="options"
      v-model:value="innerValue"
      v-bind="$attrs"
      :multiple="multiple"
      :filterable="filterable"
    />
    <n-button
      type="primary"
      v-if="showAdd && authStore.hasPermissionByCodeList(['CREATE', 'EDIT'], 'DICT')"
      @click="refFormModel = true"
    >
      <n-icon size="20">
        <IconMdiPlus></IconMdiPlus>
      </n-icon>
    </n-button>
    <HDialogForm v-model:show="refFormModel" title="字典管理" size="small">
      <DictList :showHeader="false" :showToolbar="false" :isTree="isTree" @update="fetchData" :type="type"></DictList>
    </HDialogForm>
  </n-input-group>
</template>
<script lang="ts" setup>
import { computed, ref, shallowRef, watchEffect, type PropType } from 'vue';
import { type as fetchType } from '@/modules/sys/api/dict';
import type { SelectMixedOption } from 'naive-ui/es/select/src/interface';
import { useAuthStore } from '@/store';
import DictList from './DictList.vue';

const authStore = useAuthStore();

defineOptions({ inheritAttrs: false });

type DictSelectValue = string | number | Array<string | number> | null | undefined;

const props = defineProps({
  value: { type: [String, Number, Array] as PropType<DictSelectValue> },
  multiple: { type: Boolean, default: false },
  filterable: { type: Boolean, default: true },
  type: { type: String, required: true },
  isTree: { type: Boolean, default: false },
  returnArray: { type: Boolean, default: false },
  showAdd: { type: Boolean, default: false },
  includeValues: { type: Array as PropType<Array<string | number>>, default: undefined },
  fallbackOptions: { type: Array as PropType<SelectMixedOption[]>, default: () => [] },
});

const remoteOptions = shallowRef<SelectMixedOption[]>([]);
const options = computed(() => {
  const source = remoteOptions.value.length ? remoteOptions.value : props.fallbackOptions;
  if (!props.includeValues?.length) return source;
  const included = new Set(props.includeValues.map(String));
  return source.filter((item) => included.has(String(item.value)));
});

const emit = defineEmits(['update:value']);

const innerValue = computed({
  get() {
    let value = props.value;
    if (props.multiple) {
      if (props.returnArray) {
        return value || [];
      } else {
        return (
          value
            ?.toString()
            ?.split(',')
            .filter((item) => item !== '') || []
        );
      }
    } else {
      return value?.toString() || null;
    }
  },
  set(value) {
    if (value instanceof Array) {
      if (props.returnArray) {
        emit('update:value', value);
      } else {
        emit('update:value', value.length ? value.join(',') : undefined);
      }
    } else {
      emit('update:value', value);
    }
  },
});

function fetchData(type: string) {
  fetchType(type as string, true).then((resp) => {
    let data = resp.data;
    remoteOptions.value = data || [];
  });
}

watchEffect(() => {
  const { type } = props;
  fetchData(type);
});

const refFormModel = ref(false);
</script>

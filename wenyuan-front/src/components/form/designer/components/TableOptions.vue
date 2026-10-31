<template>
  <div class="_td-table-opt">
    <n-data-table border :size="size || 'small'" :data="value" :columns="localColumn"></n-data-table>
    <div class="_td-table-opt-handle">
      <n-button link type="primary" @click="add" v-if="!max || max > value.length">
        <i class="fc-icon icon-add"></i> {{ t('tableOptions.add') }}
      </n-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, inject, h } from 'vue';
import { copy } from '@form-create/utils/lib/extend';
import { NButton, NInput, NIcon } from 'naive-ui';
import DeleteIcon from '~icons/mdi/delete';

const designer = inject('designer');

const emit = defineEmits(['update:modelValue', 'change']);

const t = computed(() => designer.setupState.t);

const props = defineProps({
  modelValue: [Array, Object],
  column: {
    type: Array,
  },
  valueType: String,
  max: Number,
  size: String,
});

const value = ref(tidyModelValue(props.modelValue));

const localColumn = computed(() => {
  if (props.column && props.column.length > 0) {
    return props.column;
  }
  return [
    {
      title: designer.setupState.t('tableOptions.label'),
      key: 'label',
      render: (row) =>
        h(NInput, {
          value: row.label,
          size: 'small',
          onUpdateValue(val) {
            row.label = val;
            onInput(row);
          },
        }),
    },
    {
      title: designer.setupState.t('tableOptions.value'),
      key: 'value',
      render: (row) =>
        h(NInput, {
          value: row.value,
          size: 'small',
          onUpdateValue(val) {
            row.value = val;
            onInput(row);
          },
        }),
    },
    {
      title: designer.setupState.t('tableOptions.handle'),
      key: 'handle',
      render: (row, index) =>
        h(
          NButton,
          {
            text: true,
            type: 'error',
            size: 'small',
            onClick: () => del(index),
          },
          {
            icon: () => h(NIcon, null, { default: () => h(DeleteIcon) }),
          },
        ),
    },
  ];
});

watch(
  () => props.modelValue,
  () => {
    value.value = tidyModelValue(props.modelValue);
  },
);

function tidyModelValue(modelValue) {
  if (props.valueType === 'string') {
    return (modelValue || []).map((value) => ({ value: '' + value }));
  } else if (props.valueType === 'object') {
    return Object.keys(modelValue || {}).map((label) => ({ label, value: modelValue[label] }));
  } else {
    return [...(modelValue || [])].map((v) => copy(v));
  }
}

function tidyValue() {
  if (props.valueType === 'object') {
    const obj = {};
    value.value.forEach((v) => {
      if (v.label && v.value) {
        obj[v.label] = v.value;
      }
    });
    return obj;
  } else {
    return value.value.map((v) => {
      if (props.valueType === 'string') {
        return v.value;
      }
      return { ...v };
    });
  }
}

function onInput(item) {
  if (localColumn.value.length === 1 && !item[localColumn.value[0].key]) {
    return;
  }
  const flag = localColumn.value.every((v) => {
    if (v.required === false) {
      return true;
    }
    if (['object', 'string'].indexOf(props.valueType) > -1) {
      return item[v.key] !== undefined && item[v.key] !== '' && item[v.key] !== null;
    }
    return item[v.key] !== undefined;
  });
  if (flag) {
    input();
  }
}

function input() {
  const tidiedValue = tidyValue();
  emit('update:modelValue', tidiedValue);
  emit('change', tidiedValue);
}

function add() {
  value.value.push(
    localColumn.value.reduce((initial, v) => {
      initial[v.key] = '';
      return initial;
    }, {}),
  );
}

function del(idx) {
  value.value.splice(idx, 1);
  input();
}
</script>

<style scoped>
._td-table-opt {
  width: 100%;
}

._td-table-opt-handle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-right: 5px;
}
</style>

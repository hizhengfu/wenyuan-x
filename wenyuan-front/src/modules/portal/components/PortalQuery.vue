<template>
  <div class="portal-query" v-if="columns && columns.length > 0">
    <n-form :model="form" inline label-placement="left" :show-feedback="false">
      <n-form-item v-for="column in columns" :label="column.name" :path="column.value">
        <n-date-picker v-model:value="form[column.value]" v-if="column.type == 4" type="date"></n-date-picker>
        <n-date-picker v-model:value="form[column.value]" v-else-if="column.type == 5" type="datetime"></n-date-picker>
        <n-date-picker
          v-model:value="form[column.value]"
          v-else-if="column.type == 6"
          type="datetimerange"
        ></n-date-picker>
        <n-date-picker v-model:value="form[column.value]" v-else-if="column.type == 7" type="month"></n-date-picker>
        <n-date-picker
          v-model:value="form[column.value]"
          v-else-if="column.type == 8"
          type="monthrange"
        ></n-date-picker>
        <n-date-picker
          v-model:value="form[column.value]"
          :default-value="defaultYear"
          v-else-if="column.type == 9"
          type="year"
        ></n-date-picker>
        <n-date-picker
          v-model:value="form[column.value]"
          v-else-if="column.type == 10"
          type="yearrange"
        ></n-date-picker>
        <HDictSelect
          v-model:value="form[column.value]"
          v-else-if="column.type == 11"
          clearable
          filterable
          :placeholder="'请选择' + column.name"
          :type="column.dictType"
        ></HDictSelect>
        <n-input v-model:value="form[column.value]" :placeholder="column.name" v-else></n-input>
      </n-form-item>
    </n-form>
  </div>
</template>
<script lang="ts" setup>
import { PropType, ref, watch } from 'vue';

const props = defineProps({
  columns: { type: Array as PropType<PortalColumn[]> },
  params: { type: Object },
});

const form = ref<any>({});

const emits = defineEmits(['update:params']);

const defaultYear = ref(new Date().getTime()); // 当前年份

/**
 * 解析组件
 * */
function parseComponent(column: PortalColumn) {
  if (column.type == 11) {
    return 'HDictSelect';
  } else if (column.type == 10) {
    return 'NDatePicker';
  } else {
    return 'NInput';
  }
}

watch(
  () => form,
  (newValue, oldValue) => {
    emits('update:params', newValue.value);
  },
  { deep: true },
);
</script>
<style lang="scss" scoped>
.portal-query {
  .n-select {
    width: 220px;
  }
}
</style>

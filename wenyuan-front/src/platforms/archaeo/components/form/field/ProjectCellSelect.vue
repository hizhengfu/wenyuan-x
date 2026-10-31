<template>
  <n-input-group>
    <n-select :options="options" v-model:value="value" v-bind="$attrs" label-field="name" value-field="id" />
    <n-button
      type="primary"
      v-if="showAdd && authStore.hasPermissionByCodeList(['CREATE', 'EDIT'], 'WMS_PROJECT')"
      @click="refFormModel = true"
    >
      <n-icon size="20">
        <IconMdiPlus></IconMdiPlus>
      </n-icon>
    </n-button>
    <HDialogForm v-model:show="refFormModel" title="出土单位列表" size="small">
      <ProjectCellList
        :projectId="projectId"
        :showHeader="false"
        :showToolbar="false"
        style="height: 800px"
        @update="fetchData"
      ></ProjectCellList>
    </HDialogForm>
  </n-input-group>
</template>
<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
import { list } from '../../../api/projectCell.ts';
import ProjectCellList from '../../ProjectCellList.vue';
import { useAuthStore } from '@/store';

const authStore = useAuthStore();

const refFormModel = ref(false);

const props = defineProps({
  value: { type: [String, Number] },
  projectId: { type: String },
  multiple: { type: Boolean, default: false },
  showAdd: { type: Boolean, default: false },
});
const options = ref<any>([]);

const emit = defineEmits(['update:value']);
const value = computed({
  get() {
    let value = props.value?.toString();
    if (props.multiple) {
      return value?.split(',') || [];
    } else {
      return value;
    }
  },
  set(value) {
    if (value instanceof Array) {
      emit('update:value', value ? value.join(',') : value);
    } else {
      emit('update:value', value);
    }
  },
});

function fetchData() {
  const { projectId } = props;
  list({ projectId: projectId }).then((resp) => {
    let data = resp.data.list || [];
    if (data) {
      options.value = data;
    }
  });
}

watch(
  () => props.projectId,
  () => {
    fetchData();
  },
  {
    immediate: true,
  },
);
</script>

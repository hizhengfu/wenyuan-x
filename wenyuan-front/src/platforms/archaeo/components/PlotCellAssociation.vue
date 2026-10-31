<template>
  <HDialogForm v-model:show="dialog" title="探方与遗迹单位关联" :handleSubmit="handleSubmit" size="small">
    <n-transfer
      v-model:value="selects"
      source-filterable
      :options="options"
      class="wh-full"
      :renderSourceList="renderSourceList"
    ></n-transfer>
  </HDialogForm>
</template>

<script lang="ts" setup>
import { computed, h, ref, watch } from 'vue';
import { plot, tree, updatePlot } from '../api/projectCell.ts';
import { NTree, TransferOption, TransferRenderSourceList, TreeOption } from 'naive-ui';

const emits = defineEmits(['update:show']);

const props = defineProps({
  show: { type: Boolean },
  projectId: { type: String, required: true },
  plotId: { type: String, required: true },
});

const dialog = computed<boolean>({
  get() {
    return props.show as boolean;
  },
  set(value) {
    emits('update:show', value);
  },
});

const options = ref<TransferOption[]>([]);
const treeOptions = ref<TransferOption[]>([]);

const selects = ref<string[]>([]);

interface Option {
  id: string;
  name: string;
  children?: Option[];
}

function flattenTree(list: undefined | Option[]): Option[] {
  const result: Option[] = [];

  const { plotId } = props;

  function flatten(_list: Option[] = []) {
    _list.forEach((item: any) => {
      if (item.plotId && item.plotId != plotId) {
        item.disabled = true;
      }
      result.push(item);
      flatten(item.children);
    });
  }

  flatten(list);
  return result;
}

/**
 * 选择区域的树
 * */
const renderSourceList: TransferRenderSourceList = function ({ onCheck, pattern }) {
  return h(NTree, {
    style: 'margin: 0 4px;',
    checkable: true,
    selectable: false,
    blockLine: true,
    checkOnClick: true,
    keyField: 'id',
    labelField: 'name',
    data: treeOptions.value as unknown as TreeOption[],
    pattern,
    checkedKeys: selects.value,
    onUpdateCheckedKeys: (checkedKeys: Array<string | number>) => {
      onCheck(checkedKeys);
    },
  });
};

/**
 * 获取全部的值
 * */
function fetchAllOptions() {
  const { projectId, plotId } = props;
  tree({ projectId: projectId }).then((resp) => {
    let list = resp.data || [];
    treeOptions.value = list;
    let fList = flattenTree(list);

    options.value = fList.map((item: any) => {
      return {
        label: item.name,
        value: item.id,
      };
    });
    fetchAssociation();
  });
}

/**
 * 获取绑定的值
 * */
function fetchAssociation() {
  const { plotId } = props;
  plot({ plotId: plotId }).then((resp) => {
    selects.value = resp.data || [];
  });
}

function handleSubmit() {
  const { plotId } = props;
  return updatePlot({ plotId: plotId, ids: selects.value });
}

fetchAllOptions();

watch(
  () => props.plotId,
  () => {
    fetchAllOptions();
  },
);
</script>

<template>
  <n-card size="small" :segmented="{ content: 'soft' }">
    <template #header> 已选文物列表,共计{{ data.length }}条,{{ sumQuantity }}件</template>
    <HDataTable
      v-bind="$attrs"
      :columns="columns"
      :pagination="false"
      minHeight="300px"
      :data="data"
      scroll-x="1200"
      :showOperate="false"
      @update:checked-row-keys="handleCheck"
    ></HDataTable>
  </n-card>
</template>
<script lang="ts" setup>
import { computed, h, onMounted, ref, watch } from 'vue';
import { sumBy } from 'lodash-es';
import { NInputNumber, DataTableRowKey } from 'naive-ui';
import { DictSelect } from '@/components/form/field/dict';

const props = defineProps({
  data: { type: Array, required: true },
  selectData: { type: Array },
  allowEdit: { type: Boolean, default: false },
});

const selectDataRef = ref([]);

const columns = [
  {
    type: 'selection',
    fixed: 'left',
    disabled(row: any) {
      return row.flowStatus === 1;
    },
  },
  { key: 'code', title: '编号', width: '160px', resizable: true, fixed: 'left', ellipsis: true },
  { key: 'image', title: '缩略图', columnType: 'image', width: '80px' },
  {
    key: 'name',
    title: '文物名称',
    width: '220px',
    resizable: true,
    ellipsis: true,
  },
  { key: 'project.name', title: '遗址名称', width: '160px', resizable: true, ellipsis: true },
  {
    key: 'material',
    title: '质地',
    width: '160px',
    dictType: 'wms_artifact_material_type',
    resizable: true,
    ellipsis: true,
  },
  { key: 'projectCellName', title: '遗迹单位', width: '160px', resizable: true, ellipsis: true },
  {
    key: 'quantity',
    title: '数量',
    width: '100px',
    align: 'right',
    render(row: any, index: number) {
      if (props.allowEdit) {
        return h(NInputNumber, {
          value: row.quantity,
          min: 1,
          showButton: false,
          onUpdateValue(v: any) {
            //@ts-ignore
            props.data[index].quantity = v;
            //@ts-ignore
            let a = selectDataRef.value.find((item: object) => item.artifactId === row.id);
            if (a) {
              //@ts-ignore
              a.quantity = v;
              emits('update:selectData', selectDataRef.value);
            }
          },
        });
      } else {
        return row.quantity;
      }
    },
  },
  {
    key: 'preservation',
    title: '保存状况',
    width: '120px',
    align: 'center',
    resizable: true,
    ellipsis: true,
    render(row: any, index: number) {
      if (props.allowEdit) {
        return h(DictSelect, {
          value: row.preservation, //默认写入出土的状态
          type: 'wms_artifact_save_status',
          onUpdateValue(v: any) {
            //@ts-ignore
            props.data[index].preservation = v;
            //@ts-ignore
            let a = selectDataRef.value.find((item: object) => item.artifactId === row.id);
            if (a) {
              //@ts-ignore
              a.saveStatus = v;
              emits('update:selectData', selectDataRef.value);
            }
          },
        });
      } else {
        return row.preservation;
      }
    },
  },
  { key: 'recorderBy', title: '记录人', width: '100px', resizable: true, ellipsis: true },
];

const sumQuantity = computed(() => {
  return sumBy(props.data, 'quantity');
});

const emits = defineEmits(['update:selectData']);

function handleCheck(rowKeys: DataTableRowKey[], rows: Object[]) {
  //@ts-ignore
  selectDataRef.value = rows.map((item: any) => {
    return { artifactId: item.id, saveStatus: item.preservation, quantity: item.quantity };
  });
  emits('update:selectData', selectDataRef.value);
}

watch(
  () => props.data,
  (n) => {
    //@ts-ignore
    selectDataRef.value = n.map((item: any) => {
      return { artifactId: item.id, saveStatus: item.preservation, quantity: item.quantity };
    });
    emits('update:selectData', selectDataRef.value);
  },
  { immediate: true },
);
</script>

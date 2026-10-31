<template>
  <h-dialog-form :model="commandForm" v-bind="$attrs" :handleSubmit="handleSubmit" style="min-height: 300px">
    <n-dynamic-input
      v-model:value="commandForm.list"
      show-sort-button
      :on-create="handleCreate"
      :on-remove="handleRemove"
    >
      <template #default="{ value, index }">
        <n-grid x-gap="6" cols="24">
          <n-grid-item span="5">
            <n-form-item
              label="操作方式"
              :show-label="index === 0"
              :path="`list[${index}].type`"
              :rule="{ required: true, message: '请选择操作方式' }"
            >
              <h-dict-select
                v-model:value="value.type"
                :type="getCommandDictType()"
                clearable
                filterable
              ></h-dict-select>
            </n-form-item>
          </n-grid-item>
          <n-grid-item span="4">
            <n-form-item
              label="名称"
              :path="`list[${index}].name`"
              :rule="{ required: true, message: '请输入指令名称' }"
              :show-label="index === 0"
            >
              <n-input v-model:value="value.name" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item span="7">
            <n-form-item label="指令" :path="`list[${index}].command`" :show-label="index === 0">
              <n-input v-model:value="value.command" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item span="4">
            <n-form-item label="默认值" :path="`list[${index}].defaultValue`" :show-label="index === 0">
              <n-input :disabled="value.type < 2" v-model:value="value.defaultValue" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item span="4">
            <n-form-item
              label="状态"
              :show-label="index === 0"
              :path="`list[${index}].status`"
              :rule="{ required: true, message: '请选择状态' }"
            >
              <h-dict-select v-model:value="value.status" type="content_status" clearable filterable></h-dict-select>
            </n-form-item>
          </n-grid-item>
        </n-grid>
      </template>
    </n-dynamic-input>
  </h-dialog-form>
</template>
<script lang="ts" setup>
import { list, saveBatch, del } from '../api/command';
import { ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';

const props = defineProps({
  deviceId: { type: String, required: true },
  deviceType: { type: [String, Number], required: true },
});

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref();

const formTitle = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  type: '0',
  deviceId: '',
  name: '',
  command: '',
  defaultValue: '',
  status: '0',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

const commandForm = ref({ list: [] });

function getCommandDictType() {
  const { deviceType } = props;
  const type = deviceType.toString();
  if (type === '1') {
    return 'cc_command_lamp_type';
  } else if (type === '6') {
    return 'cc_command_air_type';
  } else if (type === '5') {
    return 'cc_command_media_type';
  } else {
    return 'cc_command_type';
  }
}

/**
 * 创建默认数据
 * */
function handleCreate() {
  return Object.assign(cloneDeep(defaultForm), { deviceId: props.deviceId });
}

/**
 * 删除数据
 * */
function handleRemove(index: number) {
  let item = commandForm.value.list[index] as any;
  if (item && item.id) {
    del(item.id);
  }
}

/**
 * 加载数据
 * */
function loadData() {
  commandForm.value.list = [];
  list({ deviceId: props.deviceId, pageSize: 0 }).then((resp) => {
    if (resp.data && resp.data.list) {
      commandForm.value.list = resp.data.list || [];
    }
  });
}

watch(
  () => props.deviceId,
  (newValue, oldValue) => {
    if (newValue) {
      loadData();
    }
  },
  { immediate: true },
);

/**
 * 提交
 * */
function handleSubmit(data: any) {
  return saveBatch(data.list);
}
</script>
<style scoped>
.n-dynamic-input :deep(.n-dynamic-input-item:first-child .n-dynamic-input-item__action) {
  justify-content: center;
  align-self: center;
}
</style>

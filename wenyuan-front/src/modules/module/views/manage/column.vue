<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="2000"
    :params="paramsForm"
    :init-query="false"
    :fun-api-delete="del"
    :columns="columns"
    @updateSort="handleUpdateSort"
    @add="handleAdd"
    @rowEdit="handleEdit"
  >
    <template #queryForm>
      <n-form-item label="字段名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="字段名称"></n-input>
      </n-form-item>
    </template>
    <h-dialog-form
      v-model:show="formModel"
      :rules="rules"
      :model="form"
      label-width="auto"
      :ref-table="refPageTable"
      :handle-submit="handleSubmit"
    >
      <n-grid :x-gap="12">
        <n-form-item-gi label="字段名称" path="name" :span="12">
          <n-input v-model:value="form.name" placeholder="请输入字段名称" :disabled="isUpdate"></n-input>
        </n-form-item-gi>
        <n-form-item-gi label="数据类型" path="type" :span="12">
          <h-dict-select
            v-model:value="form.type"
            :disabled="isUpdate"
            type="module_data_type"
            placeholder="请选择数据类型"
          ></h-dict-select>
        </n-form-item-gi>
      </n-grid>
      <n-grid :x-gap="12">
        <n-form-item-gi label="是否为关联表" path="isReference" :span="6">
          <n-switch v-model:value="form.isReference"></n-switch>
        </n-form-item-gi>
        <template v-if="form.isReference">
          <n-form-item-gi label="外键表" path="refTableName" :span="9">
            <h-module-table-select
              v-model:value="form.refTableName"
              placeholder="请选择外键表"
              filterable
              clearable
              @update:value="form.refColumnField = ''"
            ></h-module-table-select>
          </n-form-item-gi>
          <n-form-item-gi label="外键列" path="refColumnField" feedback="请选择展示字段，默认使用id关联" :span="9">
            <h-module-column-select
              v-model:value="form.refColumnField"
              :table-name="form.refTableName"
              filterable
              clearable
              placeholder="请选择列字段"
            ></h-module-column-select>
          </n-form-item-gi>
        </template>
      </n-grid>
      <n-grid :x-gap="12">
        <n-form-item-gi label="显示名称" path="fieldLabel" :span="12">
          <n-input v-model:value="form.fieldLabel" placeholder="请输入显示名称"></n-input>
        </n-form-item-gi>
        <n-form-item-gi label="Size" path="size" :span="6">
          <n-input-number v-model:value="form.size" :min="0" placeholder="请输入长度"></n-input-number>
        </n-form-item-gi>
        <n-form-item-gi label="小数位" path="scale" :span="6">
          <n-input-number v-model:value="form.scale" :min="0" placeholder="请输入小数位"></n-input-number>
        </n-form-item-gi>
      </n-grid>
      <n-grid :x-gap="12">
        <n-form-item-gi label="默认值" path="default" :span="12">
          <n-input v-model:value="form.default" placeholder="请输入默认值"></n-input>
        </n-form-item-gi>
        <n-form-item-gi label="验证规则" path="fieldRules" :span="12">
          <n-input v-model:value="form.fieldRules" placeholder="请输入验证规则"></n-input>
        </n-form-item-gi>
      </n-grid>
      <n-grid :x-gap="12">
        <n-form-item-gi label="表单类型" path="fieldType" :span="12">
          <h-dict-select
            v-model:value="form.fieldType"
            type="module_field_type"
            filterable
            placeholder="请选择表单类型"
          ></h-dict-select>
        </n-form-item-gi>
        <n-form-item-gi label="查询类型" path="queryType" :span="12">
          <h-dict-select
            v-model:value="form.queryType"
            type="module_query_type"
            filterable
            placeholder="请选择查询类型"
          ></h-dict-select>
        </n-form-item-gi>
      </n-grid>
      <n-grid :x-gap="12">
        <n-form-item-gi label="字典类型" path="fieldDict" :span="12">
          <h-dict-type-select
            v-model:value="form.fieldDict"
            filterable
            placeholder="请选择字典类型"
          ></h-dict-type-select>
        </n-form-item-gi>
        <n-form-item-gi label="排序" path="sort" :span="12">
          <n-input-number v-model:value="form.sort" placeholder="请输入排序"></n-input-number>
        </n-form-item-gi>
      </n-grid>
      <div class="flex flex-column">
        <n-form-item label="隐藏" path="isHidden" class="mr-2">
          <n-switch v-model:value="form.isHidden"></n-switch>
        </n-form-item>
        <n-form-item label="插入" path="isInsert" class="mr-2">
          <n-switch v-model:value="form.isInsert"></n-switch>
        </n-form-item>
        <n-form-item label="更新" path="isUpdate" class="mr-2">
          <n-switch v-model:value="form.isUpdate"></n-switch>
        </n-form-item>
        <n-form-item label="列表" path="isList" class="mr-2">
          <n-switch v-model:value="form.isList"></n-switch>
        </n-form-item>
        <n-form-item label="查询" path="isQuery" class="mr-2">
          <n-switch v-model:value="form.isQuery"></n-switch>
        </n-form-item>
        <n-form-item label="导出" path="isExport" class="mr-2">
          <n-switch v-model:value="form.isExport"></n-switch>
        </n-form-item>
        <n-form-item label="标题" path="isTitle" class="mr-2">
          <n-switch v-model:value="form.isTitle"></n-switch>
        </n-form-item>
        <n-form-item label="排序" path="isSortable" class="mr-2">
          <n-switch v-model:value="form.isSortable"></n-switch>
        </n-form-item>
      </div>
      <n-form-item label="注释" path="remarks">
        <n-input v-model:value="form.remarks" type="textarea" placeholder="注释"></n-input>
      </n-form-item>
    </h-dialog-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { HModuleColumnSelect, HModuleTableSelect } from '../../components/form/field';
import { add, del, edit, list, partialUpdate, updateSort } from '../../api/moduleColumn';
import { computed, h, nextTick, ref, watch, watchEffect } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useRoute } from 'vue-router';
import { NSwitch } from 'naive-ui';

/**
 * 表格ref
 * */
const refPageTable = ref();

const route = useRoute();
const columns = [
  {
    key: 'name',
    title: '名称',
    fixed: 'left',
    rowDrag: true,
    width: '200px',
    ellipsis: {
      tooltip: true,
      width: '30%',
    },
  },
  { key: 'fieldLabel', title: '显示名称', fixed: 'left' },
  { key: 'type', title: '数据类型', dictType: 'module_data_type' },
  { key: 'size', title: 'Size', align: 'right' },
  { key: 'scale', title: 'Scale', width: '80px', align: 'right' },
  { key: 'isReference', title: '外键', dictType: 'sys_ok', width: '80px', align: 'center' },
  {
    key: 'isList',
    title: '列表',
    width: '80px',
    align: 'center',
    render: (row: any) => {
      return h(NSwitch, {
        round: false,
        value: row.isList,
        size: 'small',
        onUpdateValue(value: boolean) {
          row.isList = value;
          handleToggleFun(row.id, 'isList', value);
        },
      });
    },
  },
  {
    key: 'isHidden',
    title: '隐藏',
    width: '80px',
    align: 'center',
    render: (row: any) => {
      return h(NSwitch, {
        round: false,
        value: row.isHidden,
        size: 'small',
        onUpdateValue(value: boolean) {
          row.isHidden = value;
          handleToggleFun(row.id, 'isHidden', value);
        },
      });
    },
  },
  {
    key: 'isQuery',
    title: '查询',
    width: '80px',
    align: 'center',
    render: (row: any) => {
      return h(NSwitch, {
        round: false,
        value: row.isQuery,
        size: 'small',
        onUpdateValue(value: boolean) {
          row.isQuery = value;
          handleToggleFun(row.id, 'isQuery', value);
        },
      });
    },
  },
  {
    key: 'isInsert',
    title: '插入',
    width: '80px',
    align: 'center',
    render: (row: any) => {
      return h(NSwitch, {
        round: false,
        value: row.isInsert,
        size: 'small',
        onUpdateValue(value: boolean) {
          row.isInsert = value;
          handleToggleFun(row.id, 'isInsert', value);
        },
      });
    },
  },
  {
    key: 'isUpdate',
    title: '更新',
    width: '80px',
    align: 'center',
    render: (row: any) => {
      return h(NSwitch, {
        round: false,
        size: 'small',
        value: row.isUpdate,
        onUpdateValue(value: boolean) {
          row.isUpdate = value;
          handleToggleFun(row.id, 'isUpdate', value);
        },
      });
    },
  },
  {
    key: 'isExport',
    title: '导出',
    width: '80px',
    align: 'center',
    render: (row: any) => {
      return h(NSwitch, {
        round: false,
        size: 'small',
        value: row.isExport,
        onUpdateValue(value: boolean) {
          row.isExport = value;
          handleToggleFun(row.id, 'isExport', value);
        },
      });
    },
  },
  {
    key: 'isNull',
    title: '可空',
    width: '80px',
    align: 'center',
    render: (row: any) => {
      return h(NSwitch, {
        round: false,
        size: 'small',
        value: row.isNull,
        onUpdateValue(value: boolean) {
          row.isNull = value;
          handleToggleFun(row.id, 'isNull', value);
        },
      });
    },
  },
  {
    key: 'isPk',
    title: '主键',
    width: '80px',
    align: 'center',
    render: (row: any) => {
      return h(NSwitch, {
        round: false,
        size: 'small',
        value: row.isPk,
        onUpdateValue(value: boolean) {
          row.isPk = value;
          handleToggleFun(row.id, 'isPk', value);
        },
      });
    },
  },
  {
    key: 'isTitle',
    title: '标题',
    width: '80px',
    align: 'center',
    render: (row: any) => {
      return h(NSwitch, {
        round: false,
        size: 'small',
        value: row.isTitle,
        onUpdateValue(value: boolean) {
          row.isTitle = value;
          handleToggleFun(row.id, 'isTitle', value);
        },
      });
    },
  },
  {
    key: 'isSortable',
    title: '排序',
    width: '80px',
    align: 'center',
    render: (row: any) => {
      return h(NSwitch, {
        round: false,
        size: 'small',
        value: row.isSortable,
        onUpdateValue(value: boolean) {
          row.isSortable = value;
          handleToggleFun(row.id, 'isSortable', value);
        },
      });
    },
  },
  {
    key: 'comments',
    title: '注释',
    width: '220px',
    ellipsis: {
      tooltip: true,
      width: '30%',
    },
  },
];

const tableId = computed(() => {
  return route.query.tableId as string;
});

/**
 * 编辑表单相关
 * */
const formModel = ref(false);

/**
 * 表单默认值
 * */
const defaultForm = {
  name: '',
  tableId: '',
  type: '',
  size: 0,
  scale: 0,
  sort: 0,
  isPk: false,
  index: false,
  isNull: false,
  comments: '',
  isHidden: false,
  isInsert: true,
  isUpdate: true,
  isList: true,
  isQuery: false,
  isExport: false,
  isTitle: false,
  isSortable: false,
  isReference: false,
  refTableName: '',
  refColumnField: '',
  queryType: null,
  fieldRules: '',
  fieldType: '',
  fieldLabel: '',
  fieldDict: '',
  default: '',
  remarks: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  name: {
    required: true,
    message: '请输入名称',
  },
  type: {
    required: true,
    message: '请选择数据类型',
  },
  fieldType: {
    required: true,
    message: '请选择表单类型',
  },
  sort: {
    required: true,
    message: '请输入排序序号',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  tableId: '',
});

watch(
  () => route.query.tableId,
  (nTableId) => {
    if (nTableId) {
      paramsForm.value.tableId = nTableId as string;
      nextTick(() => {
        // @ts-ignore
        refPageTable.value?.handleQuery();
      });
    }
  },
  {
    immediate: true,
  },
);

const formType = ref('add');

const isUpdate = computed(() => {
  return formType.value === 'edit';
});

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  formModel.value = true;
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formModel.value = true;
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  Object.assign(form.value, row);
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === 'add') {
    return add(Object.assign({}, data, { tableId: tableId.value }));
  } else {
    return edit(data);
  }
}

/**
 * 切换功能开关
 * @param id 列ID
 * @param funField 功能字段
 * @param value 值
 * */
function handleToggleFun(id: string, funField: string, value: boolean) {
  let form = { id: id };
  // @ts-ignore
  form[funField] = value;
  partialUpdate(form);
}

function handleUpdateSort(items: any) {
  let sorts = items.map((item: any, index: number) => {
    return { id: item.id, sort: index };
  });
  updateSort(sorts).finally(() => {
    refPageTable.value.handleRefresh();
  });
}
</script>

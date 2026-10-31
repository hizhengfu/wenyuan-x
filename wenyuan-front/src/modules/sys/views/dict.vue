<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="360" bordered>
        <n-layout-header bordered style="padding: 10px">
          <n-input-group>
            <n-input v-model:value="pattern" clearable placeholder="类型关键字" />
            <n-button @click="handleDictTypeAdd">
              <n-icon>
                <IconMdiAdd />
              </n-icon>
            </n-button>
          </n-input-group>
        </n-layout-header>
        <n-layout-content position="absolute" style="top: 55px">
          <n-tree
            block-line
            :pattern="pattern"
            :show-irrelevant-nodes="false"
            :data="treeDictType"
            :node-props="treeNodeProps"
          />
          <n-dropdown
            trigger="manual"
            placement="bottom-start"
            :show="sidebarShowDropdown"
            :options="sidebarDropdownOptions"
            :x="dropdownX"
            :y="dropdownY"
            @select="handleDropdownSelect"
            @clickoutside="handleDropdownClickOutside"
          />
        </n-layout-content>
        <h-dialog-form
          :rules="rulesType"
          :model="dictTypeForm"
          label-width="auto"
          autocomplete="off"
          :title="formDictTitle"
          :disabled="eventDictType === 'view'"
          :handleSubmit="handleDictTypeSubmit"
          v-model:show="formDictTypeModel"
        >
          <n-form-item label="类型" path="type">
            <n-input v-model:value="dictTypeForm.type" placeholder="请输入类型"></n-input>
          </n-form-item>
          <n-form-item label="名称" path="name">
            <n-input v-model:value="dictTypeForm.name" placeholder="请输入名称"></n-input>
          </n-form-item>
          <n-form-item label="备注" path="remarks">
            <n-input type="textarea" v-model:value="dictTypeForm.remarks" placeholder="请输入备注"></n-input>
          </n-form-item>
        </h-dialog-form>
      </n-layout-sider>
      <n-layout-content content-style="padding:15px;">
        <h-page-data-table
          ref="refPageTable"
          :fetch-data="list"
          isTree
          :enableDrag="enableDrag"
          :params="paramsForm"
          @updateSort="handleUpdateSort"
          @add="handleAdd"
          @rowEdit="handleEdit"
          @rowView="handleView"
          :funApiDelete="del"
          :columns="columns"
        >
          <template #queryForm>
            <n-form-item label="字典类型" path="type" style="width: 300px">
              <dict-type-select
                v-model:value="paramsForm.type"
                filterable
                clearable
                placeholder="请选择字典类型"
              ></dict-type-select>
            </n-form-item>
          </template>
          <h-page-form
            :rules="rules"
            :model="form"
            label-width="auto"
            autocomplete="off"
            :title="formTitle"
            :disabled="formType === 'view'"
            :refTable="refPageTable"
            :handleSubmit="handleSubmit"
            v-model:show="formModel"
          >
            <n-form-item label="字典类型" path="type">
              <dict-type-select
                v-model:value="form.type"
                filterable
                clearable
                placeholder="请选择字典类型"
              ></dict-type-select>
            </n-form-item>
            <n-form-item v-if="form.type" label="父亲字典" path="parentId">
              <DictTreeSelect
                v-model:value="form.parentId"
                :type="form.type"
                keyField="id"
                filterable
                clearable
                placeholder="请选择父级别字典"
              ></DictTreeSelect>
            </n-form-item>
            <n-grid x-gap="12">
              <n-grid-item span="12">
                <n-form-item label="LABEL" path="label">
                  <n-input v-model:value="form.label" placeholder="请输入名称"></n-input>
                </n-form-item>
              </n-grid-item>
              <n-grid-item span="12">
                <n-form-item label="VALUE" path="value">
                  <n-input v-model:value="form.value" placeholder="请输入值"></n-input>
                </n-form-item>
              </n-grid-item>
            </n-grid>
            <n-form-item label="备注" path="remarks">
              <n-input type="textarea" v-model:value="form.remarks"></n-input>
            </n-form-item>
            <n-form-item label="启用状态" path="enabled">
              <n-switch v-model:value="form.enabled"></n-switch>
            </n-form-item>
          </h-page-form>
        </h-page-data-table>
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script lang="ts" setup>
import { add, del, edit, list, updateSort } from '../api/dict';
import { add as addDictType, del as delDicType, edit as editDictType, list as dictTypeList } from '../api/dictType';
import { DictTreeSelect, DictTypeSelect } from '@/components/form/field/dict';
import { computed, h, ref } from 'vue';
import { cloneDeep, isEmpty } from 'lodash-es';
import { DropdownOption, NButton, NIcon, NSwitch, TreeOption } from 'naive-ui';
import EditIcon from '~icons/mdi/square-edit-outline';
import DeleteIcon from '~icons/mdi/delete';
import { confirm } from '@/utils';

const columns = [
  {
    key: 'label',
    title: 'LABEL',
    columnType: 'title',
    rowDrag: true,
    fixed: 'left',
    width: '160px',
    resizable: true,
    ellipsis: true,
  },
  { key: 'value', title: 'VALUE', minWidth: '100px', resizable: true, ellipsis: true },
  { key: 'type', title: '类型', width: '120px', resizable: true, ellipsis: true },
  { key: 'remarks', title: '备注', width: '160px', resizable: true, ellipsis: true },
  { key: 'sort', title: '排序', width: '100px', align: 'right' },
  { key: 'isSys', title: '系统字典', dictType: 'sys_ok', width: '100px', align: 'center' },
];

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
  parentId: '',
  type: '',
  label: '',
  value: '',
  remarks: '',
  enabled: true,
  sort: 0,
};
/**
 * 验证规则
 * */
const rules = {
  type: {
    required: true,
    message: '请选择类型',
  },
  label: {
    required: true,
    message: '请输入标签',
  },
  value: {
    required: true,
    message: '请输入值',
  },
  enabled: {
    required: true,
    message: '请选择状态',
  },
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 查询表单
 * */
const paramsForm = ref({
  type: '',
});

const formType = ref('add');

const enableDrag = computed(() => {
  return !isEmpty(paramsForm.value.type);
});

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  if (paramsForm.value.type) {
    form.value.type = paramsForm.value.type;
  }
  formTitle.value = '添加';
  formModel.value = true;
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  Object.assign(form.value, row);
  formTitle.value = `编辑「${row.label}」`;
  formModel.value = true;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  formTitle.value = `查看「${row.label}」`;
  formModel.value = true;
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === 'add') {
    return add(data);
  } else {
    return edit(data);
  }
}

function handleUpdateSort(items: any) {
  let sorts = items.map((item: any, index: number) => {
    return { id: item.id, sort: index };
  });
  updateSort(sorts).finally(() => {
    refPageTable.value.handleRefresh();
  });
}

/**
 * 侧边栏相关逻辑
 * */
const pattern = ref();
const treeDictType = ref<any[]>([]);
const activeTreeOption = ref();

function treeNodeProps({ option }: { option: TreeOption }) {
  return {
    onClick() {
      paramsForm.value.type = option.value as string;
      refPageTable.value.handleRefresh();
    },
    onContextmenu(e: MouseEvent): void {
      activeTreeOption.value = option;
      sidebarShowDropdown.value = true;
      dropdownX.value = e.clientX;
      dropdownY.value = e.clientY;
      e.preventDefault();
    },
  };
}

function handleTreeLoad(node?: TreeOption) {
  return dictTypeList({ pageSize: -1 }).then((resp) => {
    treeDictType.value = (resp?.data?.list || []).map((item: any) => {
      return {
        label: `${item.name}(${item.type})`,
        key: item.type,
        value: item.type,
        id: item.id,
        name: item.name,
        remarks: item.remarks,
        type: item.type,
      };
    });
  });
}

handleTreeLoad();
// 字典类型表单
const formDictTypeModel = ref(false);
const formDictTitle = ref('添加');
/**
 * 表单默认值
 * */
const defaultDictTypeForm = {
  id: '',
  type: undefined,
  name: '',
  remarks: '',
  sort: 0,
};
/**
 * 验证规则
 * */
const rulesType = {
  type: {
    required: true,
    message: '请选择类型',
  },
  name: {
    required: true,
    message: '请输入名称',
  },
  sort: {
    required: true,
    message: '请输入序号',
  },
};

/**
 * 字典类型表单
 * */
const dictTypeForm = ref(cloneDeep(defaultDictTypeForm));
const eventDictType = ref('add');

/**
 * 添加
 * */
function handleDictTypeAdd() {
  eventDictType.value = 'add';
  dictTypeForm.value = cloneDeep(defaultDictTypeForm);
  formDictTitle.value = '添加';
  formDictTypeModel.value = true;
}

/**
 * 编辑事件
 * */
function handleDictTypeEdit(row: any) {
  eventDictType.value = 'edit';
  dictTypeForm.value = cloneDeep(defaultDictTypeForm);
  Object.assign(dictTypeForm.value, row);
  formDictTitle.value = `编辑「${row.name}」`;
  formDictTypeModel.value = true;
}

/**
 * 编辑事件
 * */
function handleDictTypeDel(row: any) {
  confirm({
    content: `清注意，即将删除「${row.label}」`,
    onPositiveClick: () => {
      delDicType(row.id).finally(() => {
        handleTreeLoad();
      });
    },
  });
}

/**
 * 提交
 * */
function handleDictTypeSubmit(data: any) {
  if (eventDictType.value === 'add') {
    return addDictType(data).finally(() => {
      handleTreeLoad();
    });
  } else {
    return editDictType(data).finally(() => {
      handleTreeLoad();
    });
  }
}

//侧边拦下拉菜单逻辑
const sidebarShowDropdown = ref(false);
const sidebarDropdownOptions = ref<DropdownOption[]>([
  {
    icon() {
      return h(NIcon, null, {
        default: () => h(EditIcon),
      });
    },
    label: '编辑',
    key: 'edit',
  },
  {
    icon() {
      return h(NIcon, null, {
        default: () => h(DeleteIcon),
      });
    },
    label: '删除',
    key: 'delete',
  },
]);
const dropdownX = ref(0);
const dropdownY = ref(0);

function handleDropdownSelect(key: string) {
  sidebarShowDropdown.value = false;
  if (key === 'delete') {
    handleDictTypeDel(activeTreeOption.value);
  } else if (key === 'edit') {
    handleDictTypeEdit(activeTreeOption.value);
  }
}

function handleDropdownClickOutside() {
  sidebarShowDropdown.value = false;
}
</script>
<style scoped>
:deep(.n-tree-node-content) {
  padding: 10px 6px;
}
</style>

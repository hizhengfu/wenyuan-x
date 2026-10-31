<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="220" bordered>
        <n-layout-header bordered style="padding: 10px">
          <n-input-group>
            <n-input v-model:value="pattern" clearable placeholder="区域关键字" />
          </n-input-group>
        </n-layout-header>
        <n-layout-content position="absolute" style="top: 60px">
          <n-tree
            :pattern="pattern"
            block-node
            :show-irrelevant-nodes="false"
            :default-expanded-keys="['1']"
            :data="treeArea"
            :cascade="true"
            :on-load="handleTreeLoad"
            key-field="id"
            label-field="name"
            :node-props="treeNodeProps"
          />
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content content-style="padding:15px;">
        <h-page-data-table
          ref="refPageTable"
          :fetch-data="list"
          :params="paramsForm"
          :default-expanded-row-keys="['1']"
          @updateSort="handleUpdateSort"
          @add="handleAdd"
          @rowEdit="handleEdit"
          @rowView="handleView"
          :funApiDelete="del"
          :columns="columns"
        >
          <template #queryForm>
            <n-form-item label="区域名称" path="name">
              <n-input v-model:value="paramsForm.name" placeholder="区域名称" clearable></n-input>
            </n-form-item>
          </template>
          <h-page-form
            :rules="rules"
            :model="form"
            label-width="auto"
            :title="formTitle"
            :disabled="formType === 'view'"
            :refTable="refPageTable"
            :handleSubmit="handleSubmit"
            v-model:show="formModel"
          >
            <n-grid x-gap="10">
              <n-form-item-gi :span="12" label="名称" path="name">
                <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="父级" path="parentId">
                <h-area-select v-model:value="form.parentId" clearable placeholder="请选择父级区域"></h-area-select>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="名称首字母" path="nameEn">
                <n-input v-model:value="form.nameEn" placeholder="请输入名称首字母"></n-input>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="代码" path="code">
                <n-input v-model:value="form.code" placeholder="请输入代码"></n-input>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="类型" path="type">
                <h-dict-select type="area_type" v-model:value="form.type" placeholder="请选择打开类型"></h-dict-select>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="排序" path="sort">
                <n-input-number v-model:value="form.sort" min="0" placeholder="请输入排序序号"></n-input-number>
              </n-form-item-gi>
              <n-form-item-gi :span="6" label="启用" path="enabled">
                <n-switch v-model:value="form.enabled"></n-switch>
              </n-form-item-gi>
            </n-grid>
          </h-page-form>
        </h-page-data-table>
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script lang="ts" setup>
import HAreaSelect from '../components/form/field/AreaSelect.vue';
import { add, del, edit, list, tree, updateSort } from '../api/area';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { TreeOption } from 'naive-ui';

const columns = [
  { key: 'name', title: '名称', columnType: 'title', rowDrag: true, tree: false, resizable: true, minWidth: '320px' },
  { key: 'nameEn', title: '英文名称', resizable: true, width: '120px' },
  { key: 'code', title: '代码', resizable: true, width: '120px' },
  { key: 'type', title: '类型', dictType: 'area_type', width: '120px', align: 'center' },
  { key: 'enabled', title: '启用', dictType: 'sys_enabled', width: '100px', align: 'center' },
];

const pattern = ref();
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
  name: '',
  nameEn: '',
  code: '',
  type: '',
  remarks: '',
  sort: 0,
  enabled: true,
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
  nameEn: {
    required: true,
    message: '请输入名称',
  },
  type: {
    required: true,
    message: '请选择类型',
  },
  code: {
    required: true,
    message: '请输入代码',
  },
  sort: {
    required: true,
    message: '请输入序号',
  },
  enabled: {
    required: true,
    message: '请选择状态',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  parentId: '0',
});

const formType = ref('add');

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
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
  formTitle.value = `编辑「${row.name}」`;
  formModel.value = true;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  formTitle.value = `查看「${row.name}」`;
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
 * 加载数据处理数据中存在叶子节点标记导致有树形表格
 * */
function loadData(params: object) {
  return list(params).then((resp) => {
    if (resp.data.list) {
      resp.data.list.forEach((item: any) => {
        delete item.isLeaf;
      });
    }
    return resp;
  });
}

/**
 * 侧边栏相关逻辑
 * */
const treeAreaId = ref('0');
const treeArea = ref([]);

function treeNodeProps({ option }: { option: TreeOption }) {
  return {
    onClick() {
      let id = option.id as string;
      if (paramsForm.value.parentId === id) {
        paramsForm.value.parentId = '0';
      } else {
        paramsForm.value.parentId = id;
      }
      refPageTable.value.handleRefresh();
      formModel.value = false;
    },
  };
}

function handleTreeLoad(node?: TreeOption) {
  return list({ parentId: (node?.id || '0') as string, pageSize: -1 }).then((resp) => {
    let list = resp.data.list || [];
    if (node) {
      node.children = list;
    } else {
      treeArea.value = list;
    }
  });
}

handleTreeLoad();
</script>

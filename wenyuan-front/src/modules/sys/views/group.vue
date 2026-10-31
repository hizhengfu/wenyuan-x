<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="220" bordered>
        <n-layout-header bordered style="padding: 10px">
          <n-input-group>
            <n-input v-model:value="pattern" clearable placeholder="机构关键字" />
          </n-input-group>
        </n-layout-header>
        <n-layout-content position="absolute" style="top: 60px">
          <n-tree
            block-node
            :pattern="pattern"
            :show-irrelevant-nodes="false"
            :data="treeGroup"
            :default-expanded-keys="defaultExpandedKeys"
            key-field="id"
            label-field="name"
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
      </n-layout-sider>
      <n-layout-content content-style="padding:15px;">
        <h-page-data-table
          ref="refPageTable"
          :fetch-data="loadData"
          :params="paramsForm"
          @add="handleAdd"
          @rowEdit="handleEdit"
          @rowView="handleView"
          @rowCopy="handleRefresh"
          @rowDelete="handleRefresh"
          @updateSort="handleUpdateSort"
          :funApiDelete="del"
          :funApiCopy="copy"
          :columns="columns"
        >
          <template #queryForm>
            <n-form-item label="机构名称" path="name">
              <n-input v-model:value="paramsForm.name" placeholder="请输入机构名称" clearable></n-input>
            </n-form-item>
            <n-form-item label="机构编码" path="code">
              <n-input v-model:value="paramsForm.code" placeholder="请输入机构编码" clearable></n-input>
            </n-form-item>
          </template>
          <template #toolbar-extra>
            <h-dialog-import-excel-form
              export-label="组织机构"
              exportFileName="组织机构.xlsx"
              export-template-url="group/export"
              file-upload-url="group/import"
            ></h-dialog-import-excel-form>
          </template>
          <h-page-view v-model:show="formModel_view" :title="formTitle" :show-action="false" :enable-cancel="false">
            <n-table :single-line="false">
              <tbody>
                <tr>
                  <td>名称</td>
                  <td colspan="2">{{ form.name }}</td>
                  <td>简称</td>
                  <td colspan="2">{{ form.simpleName }}</td>
                </tr>
                <tr>
                  <td>父级</td>
                  <td colspan="2">
                    <h-select-text
                      v-model:value="form.parentId"
                      :fetchFun="groupList"
                      :params="{ pageSize: -1 }"
                    ></h-select-text>
                  </td>
                  <td>类型</td>
                  <td colspan="2">
                    <h-dict-text v-model:value="form.type" type="group_type"></h-dict-text>
                  </td>
                </tr>
                <tr>
                  <td>编码</td>
                  <td colspan="2">{{ form.code }}</td>
                  <td>域名</td>
                  <td colspan="2">{{ form.domain }}</td>
                </tr>
                <tr>
                  <td>负责人</td>
                  <td colspan="2">{{ form.leader }}</td>
                  <td>邮箱</td>
                  <td colspan="2">{{ form.email }}</td>
                </tr>
                <tr>
                  <td>所属区域</td>
                  <td colspan="5">
                    <HCascadeAreaName v-model:value="form.areaId" />
                  </td>
                </tr>
                <tr>
                  <td>备注</td>
                  <td colspan="5">{{ form.remarks }}</td>
                </tr>
                <tr>
                  <td>启用状态</td>
                  <td colspan="5">{{ form.enabled ? '已启用' : '已停用' }}</td>
                </tr>
              </tbody>
            </n-table>
          </h-page-view>
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
            <n-grid x-gap="10">
              <n-form-item-gi label="名称" path="name" :span="12">
                <n-input v-model:value="form.name" placeholder="请输入机构名称"></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="简称" path="simpleName" :span="12">
                <n-input v-model:value="form.simpleName" placeholder="请输入简称"></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="父级" path="parentId" :span="12">
                <h-group-select v-model:value="form.parentId" clearable placeholder="请选择父级机构"></h-group-select>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="类型" path="type">
                <h-dict-select type="group_type" v-model:value="form.type" placeholder="请选择类型"></h-dict-select>
              </n-form-item-gi>
              <n-form-item-gi label="编码" path="code" :span="12">
                <n-input v-model:value="form.code" placeholder="请输入编码"></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="网站" path="domain" :span="12">
                <n-input v-model:value="form.domain" placeholder="请输入网站"></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="负责人" path="leader" :span="12">
                <HUserSelect  v-model:value="form.leader" placeholder="请输入负责人" ></HUserSelect>
<!--                <n-input v-model:value="form.leader" placeholder="请输入负责人"></n-input>-->
              </n-form-item-gi>
              <n-form-item-gi label="邮箱" path="email" :span="12">
                <n-input v-model:value="form.email" placeholder="请输入联系邮箱"></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="所属区域" path="areaId" :span="12">
                <h-area-select v-model:value="form.areaId" placeholder="请选择区域" clearable></h-area-select>
              </n-form-item-gi>
              <n-form-item-gi label="备注" path="remarks" :span="24">
                <n-input type="textarea" v-model:value="form.remarks" placeholder="请输入备注"></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="启用状态" path="enabled" :span="12">
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
import { add, copy, del, edit, list, tree, updateSort, get } from '../api/group';
import HGroupSelect from '../components/form/field/GroupSelect.vue';
import HUserSelect from '../components/form/field/UserSelect.vue';
import HAreaSelect from '../components/form/field/AreaSelect.vue';
import HSelectText from '@/components/form/field/dict/selectText.vue';
import HCascadeAreaName from '@/modules/sys/components/form/field/CascadeAreaName.vue';
import { ref, h } from 'vue';
import { cloneDeep } from 'lodash-es';
import { TreeOption, DropdownOption, NIcon } from 'naive-ui';
import { transformToTreeFormat, confirm } from '@/utils';
import { list as groupList } from '../api/group';

import EditIcon from '~icons/mdi/square-edit-outline';
import DeleteIcon from '~icons/mdi/delete';
import { del as delDicType } from '@/modules/sys/api/dictType.ts';

const columns = [
  { key: 'name', title: '名称', width: '320px', ellipsis: true, rowDrag: true, resizable: true },
  {
    key: 'friendlyName',
    title: '层级名称',
    columnType: 'title',
    width: '320px',
    ellipsis: true,
    resizable: true,
    tree: false,
  },
  { key: 'simpleName', title: '简称', width: '120px', ellipsis: true, resizable: true },
  { key: 'code', title: '机构代码', width: '200px', ellipsis: true, resizable: true },
  { key: 'leader', width: '120px', title: '负责人', ellipsis: true, resizable: true },
  { key: 'type', title: '机构类型', dictType: 'group_type', align: 'center', width: '120px' },
  { key: 'enabled', title: '启用', dictType: 'sys_enabled', width: '120px', align: 'center' },
];

const pattern = ref();
/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const formModel_view = ref(false); //查看详情
/**
 * 表格ref
 * */
const refPageTable = ref();

const formTitle = ref();

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
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  name: '',
  simpleName: '',
  code: '',
  type: null,
  email: '',
  domain: '',
  leader: '',
  parentId: '',
  areaId: '',
  remarks: '',
  enabled: true,
};
/**
 * 验证规则
 * */
const rules = {
  name: {
    required: true,
    message: '请输入用户名',
  },
  type: {
    required: true,
    message: '请选择类型',
  },
  code: {
    required: true,
    message: '请输入编码',
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
  name: '',
  parentId: '',
  code: '',
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

function handleRemove(row: any) {
  confirm({
    content: `清注意，即将删除「${row.name}」`,
    onPositiveClick: () => {
      del(row.id).finally(() => {
        handleTreeLoad();
      });
    },
  });
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  get(row.id).then((resp: any) => {
    let data = resp.data;
    formTitle.value = `编辑「${data.name}」`;
    Object.assign(form.value, data);
    formModel.value = true;
  });
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  form.value = cloneDeep(defaultForm);
  Object.assign(form.value, row);
  formTitle.value = `查看「${row.name}」`;
  formModel_view.value = true;
}

/**
 * 删除事件
 * */
function handleRefresh() {
  handleTreeLoad();
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

/**
 * 侧边栏相关逻辑
 * */
const treeGroupId = ref('0');
const treeGroup = ref<any[]>([]);
const defaultExpandedKeys = ref([]);
const activeTreeOption = ref();

function treeNodeProps({ option }: { option: TreeOption }) {
  return {
    onClick() {
      formModel.value = false;
      let id = option.id as string;
      if (paramsForm.value.parentId === id) {
        paramsForm.value.parentId = '0';
      } else {
        paramsForm.value.parentId = id;
      }
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

function handleUpdateSort(items: any) {
  let sorts = items.map((item: any, index: number) => {
    return { id: item.id, sort: index };
  });
  updateSort(sorts).finally(() => {
    refPageTable.value.handleRefresh();
  });
}

function handleTreeLoad(node?: TreeOption) {
  /*return list({parentId: (node?.id || '0') as string, pageSize: 0}).then((resp) => {
    console.log(11, node,resp)
    let list = resp.data.list || []
    if (node) {
      node.children = list
    } else {
      treeGroup.value = list
    }
  })*/
  return tree({}).then((resp) => {
    treeGroup.value = transformToTreeFormat(resp || [], { pIdKey: 'parentId' });
    if (treeGroup.value) {
      //@ts-ignore
      defaultExpandedKeys.value = [treeGroup.value[0].id];
    }
  });
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
    handleRemove(activeTreeOption.value);
  } else if (key === 'edit') {
    handleEdit(activeTreeOption.value);
  }
}

function handleDropdownClickOutside() {
  sidebarShowDropdown.value = false;
}

handleTreeLoad();
</script>

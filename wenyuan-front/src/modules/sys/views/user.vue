<template>
  <n-card class="h-full" content-style="padding: 0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="240" bordered>
        <n-layout-header bordered class="user-group-tree-header">
          <n-input v-model:value="groupTreeFilter" clearable placeholder="机构关键字">
            <template #prefix>
              <n-icon><IconMdiMagnify /></n-icon>
            </template>
          </n-input>
        </n-layout-header>
        <n-layout-content position="absolute" class="user-group-tree-content">
          <n-button
            class="all-users-button"
            size="small"
            :type="selectedGroupKeys.length === 0 ? 'primary' : 'default'"
            :secondary="selectedGroupKeys.length === 0"
            :quaternary="selectedGroupKeys.length > 0"
            block
            @click="clearGroupSelection"
          >
            全部用户
          </n-button>
          <n-scrollbar class="user-group-tree-scroll">
            <n-tree
              block-line
              :data="groupTreeData"
              :pattern="groupTreeFilter"
              :show-irrelevant-nodes="false"
              :selected-keys="selectedGroupKeys"
              :default-expanded-keys="defaultExpandedGroupKeys"
              :loading="groupTreeLoading"
              key-field="id"
              label-field="name"
              children-field="children"
              :render-suffix="renderGroupTreeSuffix"
              :expand-on-click="true"
              @update:selected-keys="handleGroupSelect"
            />
          </n-scrollbar>
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content content-style="padding: 15px; min-width: 0; display: flex;">
        <h-page-data-table
          ref="refPageTable"
          :fetch-data="loadUserData"
          scroll-x="100%"
          :params="paramsForm"
          permission="USER"
          :fun-api-delete="del"
          :fun-api-copy="copy"
          :columns="columns"
          @update:data="currentPageUsers = $event"
          @add="handleAdd"
          @rowEdit="handleEdit"
          @rowView="handleView"
        >
          <template #queryForm>
            <n-form-item label="用户名称" path="name">
              <n-input v-model:value="paramsForm.name" placeholder="请输入用户名称" clearable></n-input>
            </n-form-item>
            <n-form-item label="登录名称" path="loginName">
              <n-input v-model:value="paramsForm.loginName" placeholder="请输入登录名称" clearable></n-input>
            </n-form-item>
            <n-form-item label="用户邮箱" path="email">
              <n-input v-model:value="paramsForm.email" placeholder="请输入用户邮箱" clearable></n-input>
            </n-form-item>
            <n-form-item label="手机号码" path="phone">
              <n-input v-model:value="paramsForm.phone" placeholder="请输入手机号码" clearable></n-input>
            </n-form-item>
            <n-form-item label="所属角色" path="roleId">
              <h-role-select
                v-model:value="paramsForm.roleId"
                placeholder="请选择所属角色"
                clearable
                filterable
              ></h-role-select>
            </n-form-item>
            <n-form-item label="登录时间" path="dateTimeList" clearable>
              <n-date-picker
                clearable
                v-model:value="paramsForm.lastLoginTimeList"
                placeholder="请选择最后登录时间"
                type="datetimerange"
              />
            </n-form-item>
            <n-form-item label="内容状态" path="status">
              <h-dict-select
                v-model:value="paramsForm.status"
                clearable
                type="content_status"
                placeholder="请选择状态"
              ></h-dict-select>
            </n-form-item>
          </template>
          <template #toolbar-extra>
            <!-- 平台可通过 #toolbar-extra 插槽向工具栏注入按钮（如一键同步等） -->
            <slot name="toolbar-extra" :users="currentPageUsers"></slot>
            <h-dialog-import-excel-form
              v-if="authStore.hasPermissionByCode('CREATE', 'USER')"
              export-template-url="user/export"
              file-upload-url="user/import"
            ></h-dialog-import-excel-form>
          </template>
          <h-page-view
            v-model:show="formModel"
            v-if="formType === 'view'"
            :title="formTitle"
            :show-action="false"
            :enable-cancel="false"
          >
            <n-table :single-line="false" bordered>
              <tbody>
                <tr>
                  <td>用户名称</td>
                  <td colspan="5">{{ form.name }}</td>
                </tr>
                <tr>
                  <td>登录名称</td>
                  <td colspan="2">{{ form.loginName }}</td>
                  <td>用户邮箱</td>
                  <td colspan="2">{{ form.email }}</td>
                </tr>
                <tr>
                  <td>用户编码</td>
                  <td colspan="2">{{ form.code }}</td>
                  <td>用户性别</td>
                  <td colspan="2">
                    <h-dict-text v-model:value="form.sex" type="sex_type"></h-dict-text>
                  </td>
                </tr>
                <tr>
                  <td>归属机构</td>
                  <td colspan="2">
                    <h-select-text v-model:value="form.groupId" :fetchFun="groupList"></h-select-text>
                  </td>
                  <td>关联机构</td>
                  <td colspan="2">
                    <h-select-text v-model:value="form.groupIds" :fetchFun="groupList"></h-select-text>
                  </td>
                </tr>
                <tr>
                  <td>过期时间</td>
                  <td colspan="5">{{ form.expireTime ? dayjs(form.expireTime).format('YYYY-MM-DD HH:mm:ss') : '' }}</td>
                </tr>
                <tr>
                  <td>用户座机</td>
                  <td colspan="2">{{ form.tel }}</td>
                  <td>手机号码</td>
                  <td colspan="2">{{ form.phone }}</td>
                </tr>
                <tr>
                  <td>用户岗位</td>
                  <td colspan="2">
                    <h-select-text
                      v-model:value="form.postIds"
                      :fetchFun="postList"
                      :params="{ pageSize: -1 }"
                    ></h-select-text>
                  </td>
                  <td>用户角色</td>
                  <td colspan="2">
                    <h-role-select
                      v-model:value="form.roleIds"
                      multiple
                      placeholder="请选择角色"
                      disabled
                    ></h-role-select>
                    <h-select-text v-model:value="form.roleIds" :fetchFun="roleList"></h-select-text>
                  </td>
                </tr>
                <tr>
                  <td>启用状态</td>
                  <td colspan="5">{{ form.enabled ? '已启用' : '已停用' }}</td>
                </tr>
                <tr>
                  <td colspan="6">用户备注</td>
                </tr>
                <tr>
                  <td colspan="6">{{ form.remarks }}</td>
                </tr>
              </tbody>
            </n-table>
          </h-page-view>
          <h-page-form
            v-model:show="formModel"
            v-else
            :rules="rules"
            :model="form"
            label-width="auto"
            autocomplete="off"
            :title="formTitle"
            :disabled="formType === 'view'"
            :ref-table="refPageTable"
            :handle-submit="handleSubmit"
          >
            <n-grid x-gap="10">
              <n-form-item-gi label="用户名称" path="name" :span="12">
                <n-input v-model:value="form.name" placeholder="请输入用户名" @change="handleNameChange"></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="登录名称" path="loginName" :span="12">
                <n-input v-model:value="form.loginName" placeholder="请输入登录名"></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="用户邮箱" path="email" :span="12">
                <n-input v-model:value="form.email" placeholder="请输入邮箱"></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="用户编码" path="code" :span="6">
                <n-input v-model:value="form.code" placeholder="请输入用户编码"></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="用户性别" path="sex" :span="6">
                <h-dict-radio v-model:value="form.sex" type="sex_type" placeholder="请选择性别"></h-dict-radio>
              </n-form-item-gi>
              <n-form-item-gi label="归属机构" path="groupId" :span="12">
                <h-group-select :enabled="false" v-model:value="form.groupId" placeholder="请选择机构"></h-group-select>
              </n-form-item-gi>
              <n-form-item-gi label="关联机构" path="groupIds" :span="12">
                <h-group-select v-model:value="form.groupIds" multiple placeholder="请选择关联机构"></h-group-select>
              </n-form-item-gi>
              <n-form-item-gi label="过期时间" path="expireTime" :span="12">
                <n-date-picker v-model:value="form.expireTime" type="datetime" clearable placeholder="请选择过期时间" />
              </n-form-item-gi>
              <n-form-item-gi label="手机号码" path="phone" :span="12">
                <n-input v-model:value="form.phone" placeholder="请输入手机号码"></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="用户座机" path="tel" :span="12">
                <n-input
                  v-model:value="form.tel"
                  placeholder="请输入座机"
                  :input-props="{ autocomplete: 'new-password' }"
                ></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="用户角色" path="roleIds" :span="12">
                <h-role-select v-model:value="form.roleIds" multiple placeholder="请选择角色"></h-role-select>
              </n-form-item-gi>
              <n-form-item-gi label="用户岗位" path="postIds" :span="12">
                <h-post-select v-model:value="form.postIds" multiple placeholder="请选择岗位"></h-post-select>
              </n-form-item-gi>
            </n-grid>
            <n-grid x-gap="10">
              <n-form-item-gi v-if="formType !== 'add'" label="是否修改密码" :span="24">
                <n-switch v-model:value="form.editPassword"></n-switch>
              </n-form-item-gi>
              <n-form-item-gi v-if="form.editPassword || formType === 'add'" label="密码" path="password" :span="12">
                <HFieldPassword v-model:value="form.password"></HFieldPassword>
              </n-form-item-gi>
              <n-form-item-gi
                v-if="form.editPassword || formType === 'add'"
                label="确认密码"
                path="password_confirmation"
                :span="12"
              >
                <n-input
                  v-model:value="form.password_confirmation"
                  type="password"
                  show-password-on="click"
                  :input-props="{ autocomplete: 'new-password' }"
                  placeholder="请输入确认密码"
                ></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="启用状态" path="enabled" :span="12">
                <n-switch v-model:value="form.enabled"></n-switch>
              </n-form-item-gi>
              <n-form-item-gi label="用户备注" path="remarks" :span="24">
                <n-input type="textarea" v-model:value="form.remarks"></n-input>
              </n-form-item-gi>
              <n-form-item-gi label="数据状态" path="status" :span="24">
                <h-dict-radio
                  v-model:value="form.status"
                  clearable
                  type="content_status"
                  placeholder="请选择状态"
                ></h-dict-radio>
              </n-form-item-gi>
            </n-grid>
          </h-page-form>
          <loginLog v-model:show="logModel" :id="logId" :title="formTitle"></loginLog>
        </h-page-data-table>
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script lang="ts" setup>
import { get, add, copy, del, edit, list, updateStatus } from '../api/user';
import { tree as groupTree } from '../api/group';
import { pinyin } from '../api/system';
import { HFieldPassword } from '@/components/form/field';
import HRoleSelect from '../components/form/field/RoleSelect.vue';
import HPostSelect from '../components/form/field/PostSelect.vue';
import HGroupSelect from '../components/form/field/GroupSelect.vue';
import HSelectText from '@/components/form/field/dict/selectText.vue';
import { h, onMounted, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { FormItemRule, NSwitch, NButton, NTag } from 'naive-ui';
import { useAuthStore } from '@/store';
import { confirm, transformToTreeFormat } from '@/utils';
import dayjs from 'dayjs';
import { tree as roleList } from '../api/role';
import { list as postList } from '../api/post';
import { list as groupList } from '../api/group';
import loginLog from '@/modules/sys/components/form/loginLog.vue';

const authStore = useAuthStore();
const currentPageUsers = ref<any[]>([]);

const columns = [
  { key: 'id', title: 'id', show: false, width: '100px' },
  { key: 'name', title: '用户名称', columnType: 'title', fixed: 'left', width: '220px', resizable: true },
  { key: 'loginName', title: '登录名称', width: '220px', resizable: true, ellipsis: true },
  { key: 'code', title: '用户编码', width: '100px', show: false },
  { key: 'email', title: '用户邮箱', width: '220px', resizable: true, ellipsis: true },
  { key: 'phone', title: '用户手机', width: '220px', resizable: true, ellipsis: true },
  { key: 'fullGroupName', title: '归属机构', width: '220px', resizable: true, ellipsis: true },
  { key: 'fullRoleName', title: '角色', width: '220px', resizable: true, ellipsis: true },
  { key: 'fullPostsName', title: '岗位', width: '220px', resizable: true, ellipsis: true },
  {
    key: 'enabled',
    title: '启用',
    align: 'center',
    width: '80px',
    render: (row: any) => {
      if (authStore.hasPermission('USER_ALL,USER_EDIT,USER_AUDIT')) {
        return h(NSwitch, {
          round: false,
          value: row.enabled,
          size: 'small',
          onUpdateValue(value: boolean) {
            confirm({
              content: `点击确认后将切换用户状态为${value ? '启用' : '禁用'}，如禁用后用户将不能登录。`,
              onPositiveClick() {
                updateStatus({ userId: row.id, enable: value }).then((resp: any) => {
                  row.enabled = value;
                });
              },
            });
          },
        });
      } else {
        return row.enabled ? '启用' : '禁用';
      }
    },
  },
  { key: 'createTime', title: '创建时间', columnType: 'dateTime', width: '220px', align: 'center' },
  { key: 'lastLoginTime', title: '最后登录时间', columnType: 'dateTime', width: '220px', align: 'center' },
  { key: 'lastLoginIp', title: '最后登录IP', width: '220px', align: 'center' },
  { key: 'externalId', show: false, title: '外部系统ID', width: '220px', align: 'center' },
  { key: 'status', title: '用户状态', dictType: 'content_status', width: '100px', align: 'center' },
  {
    key: 'operate',
    type: 'operate',
    title: '操作',
    align: 'center',
    width: '260px',
    fixed: 'right',
    render(row: any) {
      return h(
        NButton,
        {
          size: 'tiny',
          type: 'info',
          class: 'mr-3',
          onClick: () => {
            handleLog(row);
          },
        },
        {
          default: () => '登录记录',
        },
      );
    },
  },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref<{ handleQuery?: () => void } | null>(null);

const formTitle = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  name: '',
  loginName: '',
  groupId: '',
  expireTime: null,
  sex: '',
  code: '',
  roleIds: [],
  postIds: [],
  groupIds: [],
  email: '',
  phone: '',
  tel: '',
  password: '',
  type: '',
  remarks: '',
  dataType: 7,
  password_confirmation: '',
  status: 'NORMAL',
  editPassword: false,
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
    message: '请输入用户名',
  },
  loginName: {
    required: true,
    message: '请输入登录名',
  },
  roleIds: {
    required: true,
    message: '请选择角色',
  },
  sex: {
    required: true,
    message: '请选择性别',
  },
  groupId: {
    required: true,
    message: '请选择机构',
  },
  password_confirmation: [
    {
      validator(rule: FormItemRule, value: string) {
        return value === form.value.password;
      },
      message: '两次密码输入不一致',
      trigger: ['blur', 'password-input'],
    },
  ],
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  loginName: '',
  email: '',
  phone: '',
  status: 'NORMAL',
  roleId: null,
  lastLoginTimeList: null,
});

const groupTreeFilter = ref('');
const groupTreeLoading = ref(false);
const groupTreeData = ref<any[]>([]);
const selectedGroupKeys = ref<string[]>([]);
const selectedGroupId = ref('');
const defaultExpandedGroupKeys = ref<string[]>([]);

function loadUserData(params: Record<string, any>) {
  return list({
    ...params,
    ...(selectedGroupId.value ? { groupId: selectedGroupId.value } : {}),
  });
}

async function loadGroupTree() {
  groupTreeLoading.value = true;
  try {
    const groups = (await groupTree({})) || [];
    groupTreeData.value = normalizeGroupTree(transformToTreeFormat(groups, { pIdKey: 'parentId' }));
    defaultExpandedGroupKeys.value = groupTreeData.value.map((item: any) => String(item.id));

    if (selectedGroupId.value && !groups.some((item: any) => String(item.id) === selectedGroupId.value)) {
      clearGroupSelection();
    }
  } finally {
    groupTreeLoading.value = false;
  }
}

function normalizeGroupTree(nodes: any[]): any[] {
  return nodes.map((node) => {
    const children = Array.isArray(node.children) ? normalizeGroupTree(node.children) : [];
    return {
      ...node,
      isLeaf: children.length === 0,
      ...(children.length ? { children } : { children: undefined }),
    };
  });
}

function renderGroupTreeSuffix({ option }: { option: any }) {
  const userCount = Number(option.userCount) || 0;
  if (userCount <= 0) return null;
  return h(
    NTag,
    {
      size: 'small',
      type: 'info',
      round: true,
      bordered: false,
      title: '直接归属该机构的用户数',
    },
    { default: () => String(userCount) },
  );
}

function handleGroupSelect(keys: Array<string | number>) {
  const groupId = keys[0] == null ? '' : String(keys[0]);
  selectedGroupId.value = groupId;
  selectedGroupKeys.value = groupId ? [groupId] : [];
  refPageTable.value?.handleQuery?.();
}

function clearGroupSelection() {
  selectedGroupId.value = '';
  selectedGroupKeys.value = [];
  refPageTable.value?.handleQuery?.();
}

const formType = ref('add');

/**
 * 自动生成用户名
 * */
function handleNameChange(name: string) {
  if (!form.value.loginName) {
    pinyin({ str: name, abbr: true }).then((resp) => {
      //@ts-ignore
      form.value.loginName = resp?.toLowerCase();
    });
  }
}

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  form.value.groupId = selectedGroupId.value;
  formTitle.value = '添加';
  formModel.value = true;
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  formTitle.value = `编辑「${row.name}」`;
  formModel.value = true;
  get(row.id).then((res) => {
    if (res.data) {
      form.value = res.data;
    }
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
  formModel.value = true;
  get(row.id).then((res) => {
    if (res.data) {
      form.value = res.data;
    }
  });
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

const logId = ref();
const logModel = ref(false);

/**
 * 查看登录记录
 * */
function handleLog(row: any) {
  Object.assign(form.value, row);
  formTitle.value = `查看「${row.name}」登录记录`;
  logId.value = row.id;
  logModel.value = true;
}

onMounted(loadGroupTree);
</script>

<style scoped>
.user-group-tree-header {
  padding: 10px;
}

.user-group-tree-content {
  top: 60px;
  padding: 8px 10px 12px;
}

.all-users-button {
  justify-content: flex-start;
  margin-bottom: 8px;
}

.user-group-tree-scroll {
  height: calc(100% - 40px);
}
</style>

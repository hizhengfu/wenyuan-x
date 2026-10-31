<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    :is-add="false"
    :show-operate="false"
    :columns="columns"
    @row-view="handleView"
  >
    <template #queryForm>
      <n-form-item label="文件名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="文件名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="HASH" path="hash">
        <n-input v-model:value="paramsForm.hash" placeholder="HASH" clearable></n-input>
      </n-form-item>
    </template>
    <template #toolbar-extra>
      <n-space align="center">
        <FileChunkTasksAction />
        <FileOrphanCleanupAction v-if="authStore.hasPermission('ADMIN')" />
        <HDialogUploadFileForm @update:show="handleFileFormClose"></HDialogUploadFileForm>
      </n-space>
    </template>
    <h-dialog-view-form
      v-model:show="formModel"
      label-width="auto"
      :title="formTitle"
      :disabled="formType === 'view'"
      :ref-table="refPageTable"
    >
      <n-descriptions label-placement="top" bordered :column="3">
        <n-descriptions-item label="名称">
          {{ form.name }}
        </n-descriptions-item>
        <n-descriptions-item label="文件后缀">
          {{ form.ext }}
        </n-descriptions-item>
        <n-descriptions-item label="上传日期">
          <n-time :time="form.createTime"></n-time>
        </n-descriptions-item>
        <n-descriptions-item label="HASH" :span="3">
          {{ form.hash }}
        </n-descriptions-item>
        <n-descriptions-item label="路径">
          <a :href="fileUrl(form.path)" v-download="{ url: form.path, fileName: form.name }" target="_blank">{{
            form.path
          }}</a>
        </n-descriptions-item>
      </n-descriptions>
    </h-dialog-view-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, updateSecure } from '../api/file';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { fileUrl } from '@/utils';
import { useAuthStore } from '@/store';
import { NButton, NSpace, NSwitch } from 'naive-ui';
import FileOrphanCleanupAction from '../components/FileOrphanCleanupAction.vue';
import FileChunkTasksAction from '../components/FileChunkTasksAction.vue';

const authStore = useAuthStore();

const columns = [
  {
    key: 'name',
    title: '名称',
    columnType: 'title',
    fixed: 'left',
    resizable: true,
    ellipsis: true,
    width: '320px',
  },
  { key: 'ext', title: '文件后缀', width: '100px' },
  { key: 'path', title: '文件预览', columnType: 'preview', width: '100px', align: 'center' },
  { key: 'hash', title: 'hash', width: '300px' },
  { key: 'size', title: '文件大小', align: 'right', width: '120px' },
  { key: 'download', title: '下载次数', align: 'right', width: '120px' },
  { key: 'contentCount', title: '关联内容数', align: 'right', width: '120px' },
  {
    key: 'toggle',
    title: '登录保护',
    align: 'center',
    width: '100px',
    render: (row: any) => {
      return h(NSwitch, {
        round: false,
        value: row.secure,
        size: 'small',
        onUpdateValue(value: boolean) {
          row.secure = value;
          updateSecure(row.id, value);
        },
      });
    },
  },
  { key: 'createTime', title: '上传日期', columnType: 'dateTime', align: 'center', width: '220px' },
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
  name: '',
  hash: '',
  path: '',
  ext: '',
  createTime: undefined,
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
  code: {
    required: true,
    message: '请输入代码',
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
  hash: '',
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

function handleFileFormClose(show: boolean) {
  refPageTable.value.handleRefresh();
}
</script>

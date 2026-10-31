<template>
  <h-page-data-table ref="refPageTable" :fetch-data="list" :scroll-x="1200" :params="paramsForm" :fun-api-delete="del"
    :columns="columns" @add="handleAdd" @rowEdit="handleEdit" @rowView="handleView">
    <template #queryForm>
      <n-form-item label="服务名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入服务名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="设备类型" path="type">
        <h-dict-select v-model:value="paramsForm.type" type="video_server_type" clearable filterable></h-dict-select>
      </n-form-item>
      <n-form-item label="内容状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="content_status" clearable filterable></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form v-model:show="formModel" :rules="rules" :model="form" label-width="auto" :title="formTitle"
      :ref-table="refPageTable" :disabled="formType === 'view'" :handle-submit="handleSubmit">
      <n-form-item label="服务名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入服务名称" clearable></n-input>
      </n-form-item>

      <n-grid x-gap="6">
        <n-grid-item span="6">
          <n-form-item label="设备类型" path="type">
            <h-dict-select v-model:value="form.type" type="video_server_type" clearable filterable></h-dict-select>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="服务地址" path="ip">
            <n-input v-model:value="form.ip" placeholder="请输入服务地址" clearable></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="6">
          <n-form-item label="服务端口" path="port">
            <n-input-number v-model:value="form.port" placeholder="请输入服务端口" clearable></n-input-number>
          </n-form-item>
        </n-grid-item>
      </n-grid>

      <n-form-item label="APP_KEY" path="appKey">
        <n-input v-model:value="form.appKey" placeholder="请输入APP_KEY" clearable></n-input>
      </n-form-item>
      <n-form-item label="APP_SECRET" path="appSecret">
        <n-input v-model:value="form.appSecret" placeholder="请输入APP_SECRET" clearable></n-input>
      </n-form-item>
      <n-grid x-gap="6" cols="2">
        <n-grid-item>
          <n-form-item label="指定群组" path="rootGroupCode" feedback="如只需要某个群组，可以先指定">
            <n-input v-model:value="form.rootGroupCode" placeholder="请输入指定群组" clearable></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item>
          <n-form-item label="预览地址" path="previewIp" feedback="某些场景下需要考虑内外网访问不同的访问地址，可以使用此参数">
            <n-input v-model:value="form.previewIp" placeholder="请输入预览视频地址，如果需要支持多个请用英文逗号分隔" clearable></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>

      <n-form-item label="服务备注" path="content">
        <n-input v-model:value="form.content" type="textarea"></n-input>
      </n-form-item>

      <n-form-item label="内容状态" path="status">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>
    <VideoEventConfig v-if="serverId" v-model:show="eventConfigModel" :serverId="serverId"></VideoEventConfig>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get, sync, clearData } from '../api/serverConfig';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton } from 'naive-ui';
import { confirm } from '@/utils';
import VideoEventConfig from '../components/form/VideoEventConfig.vue';

const columns = [
  {
    key: 'name',
    title: '服务名称',
    columnType: 'title',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
  },
  { key: 'type', title: '设备类型', dictType: 'video_server_type', align: 'center', width: '120px' },
  { key: 'ip', title: '服务地址' },
  { key: 'port', title: '服务端口', width: '120px', align: 'right' },
  {
    key: 'name',
    title: '同步参数',
    width: '240px',
    align: 'center',
    render(row: any) {
      return [
        h(
          NButton,
          {
            size: 'tiny',
            type: 'success',
            class: 'mr-3',
            onClick: () => {
              handleSync(row);
            },
          },
          {
            default: () => '同步参数',
          },
        ),
        h(
          NButton,
          {
            size: 'tiny',
            type: 'warning',
            class: 'mr-3',
            onClick: () => {
              serverId.value = row.id;
              eventConfigModel.value = true;
            },
          },
          {
            default: () => '事件订阅',
          },
        ),
        h(
          NButton,
          {
            size: 'tiny',
            type: 'error',
            onClick: () => {
              handleClearData(row);
            },
          },
          {
            default: () => '清理数据',
          },
        ),
      ];
    },
  },
  { key: 'status', title: '内容状态', dictType: 'content_status', align: 'center', width: '100px' },
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
const serverId = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  name: '',
  type: '0',
  ip: '',
  port: 443,
  appKey: '',
  appSecret: '',
  rootGroupCode: '',
  content: '',
  status: '0',
  previewIp:""
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  type: {
    required: true,
    message: '请选择类型',
  },
  name: {
    required: true,
    message: '请输入名称',
  },
  ip: {
    required: true,
    message: '请输入服务地址',
  },
  port: {
    required: true,
    message: '请输入服务端口',
  },
  appKey: {
    required: true,
    message: '请输入app_key',
  },
  appSecret: {
    required: true,
    message: '请输入app_secret',
  },
  status: {
    required: true,
    message: '请选择服务状态',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  type: '',
  name: '',
  status: '',
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
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  formTitle.value = `编辑「${row.name}」`;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  formTitle.value = `查看「${row.name}」`;
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
 * 同步视频群组与视频信息
 * */
function handleSync(row: any) {
  confirm({
    title: '请注意',
    content: '是否要同步对应的视频和群组信息，同步后将标记之前同步的设备信息',
    onPositiveClick: () => {
      sync(row.id);
    },
  });
}

/**
 * 清理同步数据
 * */
function handleClearData(row: any) {
  confirm({
    title: '请注意',
    type: 'error',
    content: '是否要清理数据，确认后将删除所有此服务关联的数据',
    onPositiveClick: () => {
      clearData(row.id);
    },
  });
}

const eventConfigModel = ref(false);
</script>

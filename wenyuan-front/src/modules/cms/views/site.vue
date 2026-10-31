<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    :default-expanded-row-keys="['1']"
    is-tree
    :fun-api-delete="del"
    :columns="columns"
    @add="handleAdd"
    permission="SITE"
    @rowEdit="handleEdit"
    @rowView="handleView"
  >
    <template #queryForm>
      <n-form-item label="站点名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="站点名称"></n-input>
      </n-form-item>
      <n-form-item label="归属机构" path="groupId">
        <n-input v-model:value="paramsForm.groupId" placeholder="归属机构"></n-input>
      </n-form-item>
      <n-form-item label="站点状态" path="enabled">
        <h-dict-select
          v-model:value="paramsForm.enabled"
          type="sys_enabled"
          clearable
          placeholder="请选择站点状态"
        ></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form
      v-model:show="formModel"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :disabled="formType === 'view'"
      :ref-table="refPageTable"
      :handle-submit="handleSubmit"
    >
      <n-grid x-gap="10">
        <n-gi span="18">
          <n-form-item-gi :span="12" label="站点名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入站点名称"></n-input>
          </n-form-item-gi>
          <n-form-item-gi :span="12" label="站点标题" path="title">
            <n-input v-model:value="form.title" placeholder="请输入站点标题"></n-input>
          </n-form-item-gi>
          <n-form-item-gi :span="12" label="归属机构" path="groupId">
            <GroupSelect v-model:value="form.groupId" placeholder="请输入选择归属机构"></GroupSelect>
          </n-form-item-gi>
        </n-gi>
        <n-gi span="4" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <h-avatar v-model:value="form.image"></h-avatar>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-form-item-gi :span="12" label="域名" path="domain">
          <n-input v-model:value="form.domain" placeholder="请输入域名"></n-input>
        </n-form-item-gi>
        <n-form-item-gi :span="12" label="启用" path="enabled">
          <n-switch v-model:value="form.enabled"></n-switch>
        </n-form-item-gi>
        <n-gi span="12" class="flex">
          <n-form-item label="主题" path="theme" class="flex-1">
            <h-theme-select v-model:value="form.theme" placeholder="请选择主题"></h-theme-select>
          </n-form-item>
          <n-form-item label="黑白主题" path="enableBlack" class="px-3">
            <n-switch v-model:value="form.enableBlack"></n-switch>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="前端模版" path="customView">
            <h-template-select v-model:value="form.customView" placeholder="请选择前端模版"></h-template-select>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="关键字" path="keywords">
        <h-field-keywords v-model:value="form.keywords" placeholder="请输入关键字"></h-field-keywords>
      </n-form-item>
      <n-form-item label="描述" path="description">
        <n-input v-model:value="form.description" type="textarea" placeholder="请输入描述"></n-input>
      </n-form-item>
      <n-form-item label="内容" path="content">
        <n-input v-model:value="form.content" type="textarea" placeholder="请输入内容"></n-input>
      </n-form-item>
      <n-form-item label="版权信息" path="copyright">
        <n-input v-model:value="form.copyright" type="textarea" placeholder="请输入版权信息"></n-input>
      </n-form-item>
      <CmsSitePluginPanel v-if="form.id && formModel" :site-id="form.id" :disabled="formType === 'view'" class="mb-3" />
      <HUpload :contentId="form.id" :form="form" label="上传附件" :disabled="formType === 'view'"></HUpload>
    </h-page-form>
    <h-page-form
      v-model:show="detailModel"
      class="project"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :ref-table="refPageTable"
      :disabled="formType === 'view'"
      :enable-cancel="false"
    >
      <div class="page-detail" ref="refDetail">
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>站点名称</td>
            <td colSpan="3">{{ form.name || '--' }}</td>
            <td rowspan="2">缩略图</td>
            <td rowspan="2" colspan="3"><h-image :src="form.image" alt="" :size="50"></h-image></td>
          </tr>
          <tr>
            <td>站点标题</td>
            <td colSpan="3" style="border-right: 1px solid var(--n-border-color)">
              {{ form.title || '--' }}
            </td>
          </tr>
          <tr>
            <td>归属机构</td>
            <td colSpan="3">{{ form.group.name || '--' }}</td>
            <td>域名</td>
            <td colSpan="3">{{ form.domain || '--' }}</td>
          </tr>
          <tr>
            <td>启用与否</td>
            <td colSpan="3">{{ form.enabled !== null ? (form.enabled ? '是' : '否') : '--' }}</td>
            <td>黑白主题</td>
            <td colSpan="3">{{ form.enableBlack !== null ? (form.enableBlack ? '是' : '否') : '--' }}</td>
          </tr>
          <tr>
            <td>主题</td>
            <td colSpan="3">{{ form.theme || '--' }}</td>
            <td>前端模版</td>
            <td colSpan="3">{{ form.customView || '--' }}</td>
          </tr>
          <tr>
            <td>关键字</td>
            <td colSpan="7">{{ form.keywords || '' }}</td>
          </tr>
          <tr>
            <td>描述</td>
            <td colSpan="7">{{ form.description || '' }}</td>
          </tr>
          <tr>
            <td>内容</td>
            <td colSpan="7">
              <div v-html="form.content"></div>
            </td>
          </tr>
          <tr>
            <td>版权信息</td>
            <td colSpan="7">{{ form.copyright || '' }}</td>
          </tr>
          <tr>
            <td>查看附件</td>
            <td colspan="7">
              <h-file-list :contentId="form.id"></h-file-list>
            </td>
          </tr>
        </n-table>
        <CmsSitePluginPanel v-if="form.id && detailModel" :site-id="form.id" disabled class="mt-3" />
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import CmsSitePluginPanel from '../components/CmsSitePluginPanel.vue';
import { HFieldKeywords } from '@/components/form/field';
import HThemeSelect from '../components/form/field/ThemeSelect.vue';
import HTemplateSelect from '../components/form/field/TemplateSelect.vue';
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import { add, del, edit, exportServerFile, list, toggleBlack } from '../api/site';
import { ref, h } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton, NSwitch } from 'naive-ui';
import { ajaxGetDownload } from '@/utils';

const columns = [
  { key: 'name', title: '站点名称', columnType: 'title', resizable: true, ellipsis: { width: '30%' } },
  { key: 'title', title: '站点标题', resizable: true, ellipsis: { width: '30%' } },
  { key: 'logo', title: 'logo', columnType: 'image', width: '80px' },
  { key: 'domain', title: '域名', ellipsis: { width: '30%' } },
  { key: 'group.name', title: '归属机构', resizable: true, ellipsis: { width: '30%' } },
  { key: 'theme', title: '主题', width: '120px' },
  {
    key: 'export',
    title: '导出',
    width: '220px',
    align: 'center',
    render: (row: any) => {
      return [
        h(
          NButton,
          {
            size: 'small',
            class: 'mr-3',
            loading: row.downloading,
            disabled: row.downloading,
            onClick() {
              row.downloading = true;
              ajaxGetDownload('cms/site/json/' + row.id, row.name).finally(() => {
                row.downloading = false;
              });
            },
          },
          {
            default: () => '导出',
          },
        ),
        h(
          NButton,
          {
            size: 'small',
            loading: row.downloading,
            disabled: row.downloading,
            onClick() {
              row.downloading = true;
              exportServerFile(row.id).finally(() => {
                row.downloading = false;
              });
            },
          },
          {
            default: () => '服务端导出',
          },
        ),
      ];
    },
  },
  {
    key: 'enableBlack',
    title: '黑白',
    align: 'center',
    width: '80px',
    render: (row: any) => {
      return h(NSwitch, {
        round: false,
        value: row.enableBlack,
        size: 'small',
        onUpdateValue(value: boolean) {
          row.enableBlack = value;
          toggleBlack(row.id);
        },
      });
    },
  },
  { key: 'enabled', title: '启用', dictType: 'sys_enabled', width: '100px', align: 'center' },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const detailModel = ref(false);

/**
 * 表格ref
 * */
const refPageTable = ref(null);

const formTitle = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  name: '',
  title: '',
  groupId: '',
  logo: '',
  image: '',
  domain: '',
  description: '',
  keywords: '',
  content: '',
  theme: '',
  customView: '',
  copyright: '',
  enableBlack: false,
  enabled: false,
  group: { name: '' },
};

/**
 * 表单
 * */
const form: any = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  name: {
    required: true,
    message: '请输入名称',
  },
  title: {
    required: true,
    message: '请输入标题',
  },
  groupId: {
    required: true,
    message: '请选择归属机构',
  },
  theme: {
    required: true,
    message: '请选择主题',
  },
  customView: {
    required: true,
    message: '请选择模版',
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
  groupId: '',
  enabled: null,
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
  detailModel.value = true;
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
</script>

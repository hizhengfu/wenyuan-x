<template>
  <h-page-data-table ref="refPageTable" :fetch-data="list" :scroll-x="1600" :params="paramsForm"
    :default-expanded-row-keys="['1']" is-tree :fun-api-delete="del" :columns="columns" @updateSort="handleUpdateSort"
    @add="handleAdd" @rowEdit="handleEdit" @rowView="handleView" permission="CATEGORY">
    <template #queryForm>
      <n-form-item label="栏目名称" path="name">
        <n-input v-model:value="paramsForm.name" clearable placeholder="栏目名称"></n-input>
      </n-form-item>
      <n-form-item label="所属站点" path="siteId">
        <h-site-select v-model:value="paramsForm.siteId" clearable filterable placeholder="请选择站点"></h-site-select>
      </n-form-item>
    </template>
    <h-page-form v-model:show="formModel" :rules="rules" :model="form" label-width="auto" :title="formTitle"
      :disabled="formType === 'view'" :ref-table="refPageTable" :handle-submit="handleSubmit">
      <n-grid x-gap="10">
        <n-gi span="18">
          <n-form-item label="栏目名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入栏目名称"></n-input>
          </n-form-item>
          <n-grid x-gap="10">
            <n-grid-item :span="12">
              <n-form-item label="所属站点" path="siteId">
                <h-site-select v-model:value="form.siteId" placeholder="请选择所属站点"></h-site-select>
              </n-form-item>
            </n-grid-item>
            <n-grid-item :span="12">
              <n-form-item v-if="form.siteId" label="父级栏目" path="parentId">
                <h-category-select v-model:value="form.parentId" :site-id="form.siteId" clearable
                  placeholder="请选择父级栏目"></h-category-select>
              </n-form-item>
            </n-grid-item>
          </n-grid>
        </n-gi>
        <n-gi span="4" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <h-avatar v-model:value="form.image"></h-avatar>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-form-item-gi :span="8" label="栏目编码" path="code">
          <n-input v-model:value="form.code" placeholder="请输入栏目编码"></n-input>
        </n-form-item-gi>
        <n-form-item-gi :span="8" label="栏目外链" path="href">
          <n-input v-model:value="form.href" placeholder="请输入栏目外链"></n-input>
        </n-form-item-gi>
        <n-form-item-gi :span="8" label="打开方式" path="target">
          <n-select v-model:value="form.target" :options="targetOptions"></n-select>
        </n-form-item-gi>

        <n-gi :span="8">
          <n-form-item label="栏目模型" path="module" class="flex-1">
            <n-select v-model:value="form.module" :options="moduleOptions" clearable filterable placeholder="请选择栏目模型"
              @update:value="handleModuleChange"></n-select>
          </n-form-item>
        </n-gi>
        <n-gi v-if="form.siteId" :span="8">
          <n-form-item v-if="form.module" :label="form.module === 'PAGE' ? '单页模板' : '列表模板'" path="customListView">
            <h-template-list-select v-model:value="form.customListView" :site-id="form.siteId" :module="form.module"
              :prefix="form.module === 'PAGE' ? '' : 'list'"></h-template-list-select>
          </n-form-item>
        </n-gi>
        <n-gi v-if="form.siteId" :span="8">
          <n-form-item v-if="form.customListView && form.module !== 'PAGE'" label="内容模板" path="customListView">
            <h-template-list-select v-model:value="form.customContentView" :site-id="form.siteId" :module="form.module"
              prefix="detail"></h-template-list-select>
          </n-form-item>
        </n-gi>
      </n-grid>
      <div class="flex">
        <n-form-item label="菜单显示" path="inMenu" class="mr-3">
          <n-switch v-model:value="form.inMenu"></n-switch>
        </n-form-item>
        <n-form-item label="列表显示" path="inList">
          <n-switch v-model:value="form.inList"></n-switch>
        </n-form-item>
        <n-form-item label="允许评论" path="allowComment">
          <n-switch v-model:value="form.allowComment"></n-switch>
        </n-form-item>

        <n-form-item label="是否启用" path="enabled">
          <n-switch v-model:value="form.enabled"></n-switch>
        </n-form-item>
      </div>
      <n-form-item label="关键字" path="keywords">
        <h-field-keywords v-model:value="form.keywords" placeholder="请输入关键字"></h-field-keywords>
      </n-form-item>
      <n-form-item label="描述" path="description">
        <n-input v-model:value="form.description" type="textarea" placeholder="请输入描述"></n-input>
      </n-form-item>
      <n-form-item label="详细内容" path="content">
        <n-input v-model:value="form.content" type="textarea" placeholder="请输入详细内容"></n-input>
      </n-form-item>
    </h-page-form>
    <h-page-form v-model:show="detailModel" class="project" :model="form" label-width="auto" :title="formTitle"
      :ref-table="refPageTable" :disabled="formType === 'view'" :enable-cancel="false">
      <div class="page-detail" ref="refDetail">
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>站点名称</td>
            <td colSpan="3">{{ form.name || '--' }}</td>
            <td rowspan="2">缩略图</td>
            <td rowspan="2" colspan="3"><h-image :src="form.image" alt="" :size="50"></h-image></td>
          </tr>
          <tr>
            <td>所属站点</td>
            <td colSpan="3" style="border-right: 1px solid var(--n-border-color)">
              {{ form.siteName || '--' }}
            </td>
          </tr>
          <tr>
            <td>父级栏目</td>
            <td colSpan="3">{{ form.parentName || '--' }}</td>
            <td>栏目编码</td>
            <td colSpan="3">{{ form.code || '--' }}</td>
          </tr>
          <tr>
            <td>栏目外链</td>
            <td colSpan="3">{{ form.href || '--' }}</td>
            <td>打开方式</td>
            <td colSpan="3">{{ form.target || '' }}
            </td>
          </tr>
          <tr>
            <td>栏目模型</td>
            <td colSpan="3"> {{ moduleLabel(form.module) }}
            </td>
            <td>{{ form.module === 'PAGE' ? '单页模板' : '列表模板' }}</td>
            <td colSpan="3">{{ form.customListView || '' }}
            </td>
          </tr>
          <tr>
            <td>菜单显示</td>
            <td colSpan="1">{{ form.inMenu !== null ? (form.inMenu ? '是' : '否') : '--' }}</td>
            <td>列表显示</td>
            <td colSpan="1">{{ form.inList !== null ? (form.inList ? '是' : '否') : '--' }}</td>
            <td>允许评论</td>
            <td colSpan="1">{{ form.allowComment !== null ? (form.allowComment ? '是' : '否') : '--' }}</td>
            <td>是否启用</td>
            <td colSpan="1">{{ form.enabled !== null ? (form.enabled ? '是' : '否') : '--' }}</td>
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
            <td>详细内容</td>
            <td colSpan="7">{{ form.content || '' }}</td>
          </tr>
        </n-table>
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { HFieldKeywords } from '@/components/form/field';
import HTemplateListSelect from '../components/form/field/TemplateListSelect.vue';
import HSiteSelect from '../components/form/field/SiteSelect.vue';
import HCategorySelect from '../components/form/field/CategorySelect.vue';
import { add, del, edit, list, updateSort } from '../api/category';
import { ref, h, onMounted } from 'vue';
import { cmsPlugins } from '../api/plugins';
import { cloneDeep } from 'lodash-es';

import { useRoute } from 'vue-router';
import { NButton, SelectOption } from 'naive-ui';
import { ajaxGetDownload } from '@/utils';

const $route = useRoute();
const moduleOptions = ref<{ label: string; value: string }[]>([]);
function moduleLabel(code: string) { return moduleOptions.value.find(item => item.value === code)?.label || code; }
onMounted(async () => { const definitions = await cmsPlugins(); moduleOptions.value = definitions.map(item => ({ label: item.label, value: item.code })); });
const columns = [
  {
    key: 'name',
    title: '栏目名称',
    columnType: 'title',
    fixed: 'left',
    width: '320px',
    rowDrag: true,
    resizable: true,
    ellipsis: { width: '30%' },
  },
  { key: 'siteName', title: '所属站点', resizable: true, ellipsis: { width: '30%' } },
  { key: 'code', title: '栏目编码', resizable: true, ellipsis: { width: '30%' } },
  { key: 'logo', title: '缩略图', columnType: 'image', width: '100px', align: 'center' },
  { key: 'href', title: '栏目外链', resizable: true, ellipsis: { width: '30%' } },
  { key: 'module', title: '栏目模型', render: (row: any) => moduleLabel(row.module), width: '100px', align: 'center' },
  { key: 'inMenu', title: '菜单显示', dictType: 'sys_ok', width: '100px', align: 'center' },
  { key: 'inList', title: '列表显示', dictType: 'sys_ok', width: '100px', align: 'center' },
  { key: 'allowComment', title: '允许评论', dictType: 'sys_ok', width: '100px', align: 'center' },
  {
    key: 'export',
    title: '导出',
    width: '80px',
    render: (row: any) => {
      return h(
        NButton,
        {
          size: 'small',
          loading: row.downloading,
          disabled: row.downloading,
          onClick() {
            row.downloading = true;
            ajaxGetDownload('cms/category/json/' + row.id, row.name).finally(() => {
              row.downloading = false;
            });
          },
        },
        {
          default: () => '导出',
        },
      );
    },
  },
  { key: 'enabled', title: '是否启用', dictType: 'sys_ok', width: '100px', align: 'center' },
];

const targetOptions = [
  { label: '当前', value: '_self' },
  { label: '新窗口', value: '_blank' },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const detailModel = ref(false);

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
  code: '',
  siteId: null,
  image: '',
  href: '',
  target: '_self',
  module: null,
  customListView: null,
  customContentView: null,
  remarks: '',
  keywords: '',
  description: '',
  content: '',
  sort: 0,
  inMenu: true,
  inList: true,
  allowComment: false,
  saveRemoteImage: false,
  enabled: true,
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
  siteId: {
    required: true,
    message: '请选择站点',
  },
  code: {
    required: true,
    message: '请输入编码',
  },
  module: {
    required: true,
    message: '请选择模型',
  },
  customListView: {
    required: true,
    message: '请选择模版',
  },
  customContentView: {
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
  siteId: undefined,
  
});

const formType = ref('add');

function handleModuleChange(value: string, option: SelectOption) {
  form.value.customListView = null;
  form.value.customContentView = null;
}

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  if (paramsForm.value.siteId) {
    form.value.siteId = paramsForm.value.siteId;
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
  if (!data.categoryId) {
    data.categoryId = $route.query.categoryId;
  }
  if (formType.value === 'add') {
    return add(data);
  } else {
    return edit(data);
  }
}

/**
 * 更新排序
 * */
function handleUpdateSort(items: any) {
  let sorts = items.map((item: any, index: number) => {
    return { id: item.id, sort: index };
  });
  updateSort(sorts).finally(() => {
    refPageTable.value.handleRefresh();
  });
}
</script>

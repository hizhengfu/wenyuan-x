<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1500"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="标题" path="title">
        <n-input v-model:value="paramsForm.title" placeholder="请输入标题" clearable></n-input>
      </n-form-item>
      <n-form-item label="状态" path="status">
        <n-input v-model:value="paramsForm.status" placeholder="请输入状态" clearable></n-input>
      </n-form-item>
    </template>
    <h-page-form
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :refTable="refPageTable"
      :disabled="formType === 'view'"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
    >
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="标准标题" path="title">
            <n-input v-model:value="form.title" placeholder="请输入标准标题" clearable></n-input>
          </n-form-item>
          <n-form-item label="标准分类" path="category">
            <n-input v-model:value="form.category" placeholder="请输入标准分类" clearable></n-input>
          </n-form-item>
          <n-form-item label="发布日期" path="publishDate">
            <n-date-picker clearable v-model:value="form.publishDate" type="date" placeholder="请输入发布日期" />
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="标准编号" path="number">
            <n-input v-model:value="form.number" placeholder="请输入标准编号" clearable></n-input>
          </n-form-item>
          <n-form-item label="标准级别" path="level">
            <n-input v-model:value="form.level" placeholder="请输入标准级别" clearable></n-input>
          </n-form-item>
          <n-form-item label="实施日期" path="effectiveDate">
            <n-date-picker clearable v-model:value="form.effectiveDate" type="date" placeholder="请输入实施日期" />
          </n-form-item>
        </n-gi>
      </n-grid>

      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="废止日期" path="expiryDate">
            <n-date-picker clearable v-model:value="form.expiryDate" type="date" placeholder="请输入废止日期" />
          </n-form-item>
          <n-form-item label="关键词" path="keywords">
            <n-input v-model:value="form.keywords" placeholder="请输入关键词" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="标准状态" path="status">
            <h-dict-select v-model:value="form.status" placeholder="请输入标准状态" type="time_line" clearable></h-dict-select>
          </n-form-item>
           <n-form-item label="发布单位" path="publishingOrganization">
            <n-input v-model:value="form.publishingOrganization" placeholder="请输入发布单位" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="内容摘要" path="summary">
        <n-input v-model:value="form.summary" placeholder="请输入summary" clearable></n-input>
      </n-form-item>
      <n-form-item label="标准内容" path="content">
        <n-input v-model:value="form.content" type="textarea"></n-input>
      </n-form-item>

      <n-form-item label="上传链接" path="attachmentUrl">
        <h-upload-input
          v-model:value="form.attachmentUrl"
          placeholder="请输入链接或者上传文件"
          clearable
        ></h-upload-input>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="替代标准" path="replaceStandard">
            <n-input v-model:value="form.replaceStandard" placeholder="请输入替代标准" clearable></n-input>
          </n-form-item>
            <n-form-item label="下载次数" path="downloadCount">
            <n-input-number v-model:value="form.downloadCount" placeholder="请输入下载次数" clearable></n-input-number>
          </n-form-item>
          <n-form-item label="排序" path="sort">
            <n-input-number v-model:value="form.sort" placeholder="请输入排序" clearable></n-input-number>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="被替代标准" path="replaceByStandard">
            <n-input v-model:value="form.replaceByStandard" placeholder="请输入被替代标准" clearable></n-input>
          </n-form-item>
          <n-form-item label="查看次数" path="viewCount">
            <n-input-number v-model:value="form.viewCount" placeholder="请输入查看次数" clearable></n-input-number>
          </n-form-item>
          <n-form-item label="是否推荐" path="isRecommended">
            <h-dict-select v-model:value="form.isRecommended" type="is_isRecommended" placeholder="请输入是否推荐" clearable></h-dict-select>
          </n-form-item>
        </n-gi>
      </n-grid>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../api/laws/standard';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';

const columns = [
  { key: 'title', title: '标题', columnType: 'title', width: '200px', resizable: true, ellipsis: { tooltip: true } ,fixed: 'left' },
  { key: 'number', title: '标准编号',width:'150px' },
  { key: 'category', title: '标准分类',width:'150px' },
  { key: 'level', title: '标准级别',width:'150px' },
  { key: 'publishDate', title: '发布日期', columnType: 'date',width:'150px' },
  { key: 'effectiveDate', title: '实施日期', columnType: 'date',width:'150px' },
  { key: 'expiryDate', title: '废止日期', columnType: 'date' ,width:'150px'},
  { key: 'status', title: '状态',dictType: 'time_line', width:'150px' },
  { key: 'publishingOrganization', title: '发布单位' ,width:'200px'},




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
  title: '',
  number: '',
  category: '',
  level: '',
  publishDate: null,
  effectiveDate: null,
  expiryDate: null,
  status: '',
  publishingOrganization: '',
  replaceStandard: '',
  replaceByStandard: '',
  content: '',
  summary: '',
  keywords: '',
  attachmentUrl: '',
  viewCount: null,
  downloadCount: null,
  isRecommended: '',
  sort: null,




};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  title: {
    required: true,
    message: '请输入title',
  },
  
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  title: '',
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
</script>

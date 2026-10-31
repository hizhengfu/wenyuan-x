<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1500"
    :params="paramsForm"
    add-permission="FOUR_CULTURAL_RECORD_ALL,FOUR_CULTURAL_RECORD_CREATE"
    edit-permission="FOUR_CULTURAL_RECORD_ALL,FOUR_CULTURAL_RECORD_EDIT"
    delete-permission="FOUR_CULTURAL_RECORD_ALL,FOUR_CULTURAL_RECORD_DELETE"
    :fun-api-delete="del"
    :columns="columns"
    @add="handleAdd"
    @row-edit="handleEdit"
    @row-view="handleView"
  >
    <template #queryForm>
      <n-form-item label="案卷提名" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入案卷提名" clearable></n-input>
      </n-form-item>
      <n-form-item label="文物保护单位" path="bhdw">
        <n-input v-model:value="paramsForm.bhdw" placeholder="请输入文物保护单位名称" clearable></n-input>
      </n-form-item>
      <!-- <n-form-item label="机构" path="groupId">
        <group-select v-model:value="paramsForm.groupId" clearable></group-select>
      </n-form-item> -->
      <n-form-item label="公布编号" path="publishNo">
             <n-input v-model:value="paramsForm.publishNo" placeholder="请输入公布编号" clearable></n-input>
      </n-form-item>
      <n-form-item label="关联状态" path="fourId" v-if="showEdit">
        <h-dict-select
          v-model:value="paramsForm.fourId"
          placeholder="请选择关联状态"
          type="sys_ok"
          clearable
        ></h-dict-select>
      </n-form-item>
      <slot name="extendQueryForm" :form="paramsForm"></slot>
    </template>
    <template #toolbar-extra>
      <h-dialog-import-excel-form
        file-upload-url="four/cultural/relic/import"
        accept="application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document
"
      >
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button type="primary" @click="handleBatchImport">服务器批量导入</n-button>
          </template>
          需要先上传文件到服务器目录doc/four
        </n-tooltip>
      </h-dialog-import-excel-form>
    </template>
    <h-page-form
      v-model:show="formModel"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :ref-table="refPageTable"
      :disabled="formType === 'view'"
      :handle-submit="handleSubmit"
    >
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="编码" path="code">
            <n-input v-model:value="form.code" placeholder="请输入编码" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="保护单位名称" path="bhdw">
            <n-input v-model:value="form.bhdw" placeholder="请输入保护单位名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="机构选择" path="groupId">
            <group-select v-model:value="form.groupId"></group-select>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="立卷日期" path="ljrq">
            <n-input v-model:value="form.ljrq" clearable placeholder="请输入立卷日期"></n-input>
          </n-form-item>
          <n-form-item label="区域选择" path="areaId">
            <CascadeArea v-model:value="form.areaId" filterable></CascadeArea>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="内容" path="content">
        <n-input v-model:value="form.content" type="textarea"></n-input>
      </n-form-item>
    </h-page-form>
    <h-page-form
      v-model:show="detailModel"
      class="project"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :ref-table="refPageTable"
      :disabled="formType === 'view'"
      :enable-cancel="false"
    >
      <FourView :four-cultural-record-id="fourId" :title="fourTitle"></FourView>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get, batchImport } from '../../api/four/fourCulturalRecord.ts';
import { ref, h, watchEffect } from 'vue';
import { cloneDeep } from 'lodash-es';
import CascadeArea from '@/modules/sys/components/form/field/CascadeArea.vue';
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import FourView from '@/modules/archive/components/four/FourView.vue';
import { RouterLink } from 'vue-router';
import { NButton } from 'naive-ui';
import { confirm } from '@/utils';
const props = defineProps({
  showEdit: {
    type: Boolean,
    default: true,
  },
  queryParams: {
    type: Object,
    default: () => {},
  },
});
const columns = [
  {
    key: 'name',
    title: '名称',
    columnType: 'title',
    fixed: 'left',
    resizable: true,
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    titleAlign: 'center',
    width: '220px',
  },
  {
    key: 'ljrq',
    title: '立卷日期',
    titleAlign: 'center',
    width: '160px',
    align: 'center',
    ellipsis: { tooltip: true },
  },
  {
    key: 'code',
    title: '编码',
    titleAlign: 'center',
    width: '200px',
    ellipsis: { tooltip: true },
  },
  {
    key: 'publishNo',
    title: '公布编号',
    titleAlign: 'center',
    width: '200px',
    ellipsis: { tooltip: true },
  },
  {
    key: 'area.name',
    title: '区域',
    titleAlign: 'center',
    width: '220px',
    ellipsis: { tooltip: true },
  },
  {
    key: 'group.name',
    title: '机构',
    titleAlign: 'center',
    width: '220px',
    ellipsis: { tooltip: true },
  },
  {
    key: 'path',
    title: '查看四有档案',
    align: 'center',
    fixed: 'right',
    show: props.showEdit,
    width: '300px',
    render(row: any) {
      return [
        h(
          RouterLink,
          {
            to: { path: './detail', query: { fourCulturalRecordId: row.id, title: row.name, code: 'Fm' } },
            class: 'mx-3',
          },
          { default: () => '四有档案' },
        ),
        h(
          RouterLink,
          {
            to: { path: './fileDetail', query: { fourCulturalRecordId: row.id, title: row.name, code: 'Fm' } },
            class: 'mx-3',
          },
          { default: () => '档案文件' },
        ),
        // h(
        //   RouterLink,
        //   {
        //     to: {
        //       path: './ZjWz9',
        //       query: {
        //         fourCulturalRecordId: row.id,
        //         title: `「${row.name}」本体及两划线`,
        //         code: 'Fm',
        //       },
        //     },
        //     class: 'mx-3',
        //   },
        //   { default: () => '两划线' },
        // ),
      ];
    },
  },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const detailModel = ref(false);

const fourId = ref();
const fourTitle = ref();
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
  bhdw: '',
  ljrq: '',
  areaId: '',
  groupId: '',
  content: '',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
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
  bhdw:{
    required:true,
    message:'请输入保护单位名称'
  },
  ljrq:{
    required:true,
    message:'请输入立卷日期'
  }
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  bhdw: '',
  groupId: '',
  fourId: '',
  publishNo:''
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
  fourId.value = row.id;
  fourTitle.value = row.name;
  detailModel.value = true;
  formTitle.value = `查看「${row.name}」`;
}

/**
 * 服务器批量导入
 * */
function handleBatchImport() {
  confirm({
    content: '清注意，即将服务器导入数据',
    onPositiveClick: () => {
      batchImport().finally(() => {
        refPageTable.value.handleRefresh();
      });
    },
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

watchEffect(() => {
  const { queryParams } = props;
  if (queryParams) {
    Object.assign(paramsForm.value, queryParams);
  }
});
</script>

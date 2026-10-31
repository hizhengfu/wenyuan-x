<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1700"
    :params="paramsForm"
    :fun-api-delete="del"
    :columns="columns"
    @add="handleAdd"
    @row-edit="handleEdit"
    @row-view="handleView"
    
  >
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="编号" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="请输入编号" clearable></n-input>
      </n-form-item>
      <n-form-item label="级别" path="level">
        <h-dict-select
          v-model:value="paramsForm.level"
          type="third_cultural_relic_level"
          placeholder="请选择级别"
          filterable
          clearable
        ></h-dict-select>
      </n-form-item>
      <n-form-item label="类别" path="type">
        <h-dict-select
          v-model:value="paramsForm.type"
          type="third_cultural_relic_type"
          placeholder="请选择类别"
          filterable
          clearable
        ></h-dict-select>
      </n-form-item>
      <n-form-item label="状态" path="thirdId" v-if="showEdit">
        <h-dict-select
          v-model:value="paramsForm.thirdId"
          placeholder="请选择关联状态"
          type="sys_ok"
          clearable
        ></h-dict-select>
      </n-form-item>
      <slot name="extendQueryForm" :form="paramsForm"></slot>
    </template>
    <template #toolbar-extra>
      <h-dialog-import-excel-form
        file-upload-url="archive/third/cultural/relic/cover/import"
        accept="application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document
"
      >
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button type="primary" @click="handleBatchImport">服务器批量导入</n-button>
          </template>
          需要先上传文件到服务器目录doc/third下
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
          <n-form-item label="调查人" path="investigaBy">
            <n-input v-model:value="form.investigaBy" placeholder="请输入调查人" clearable></n-input>
          </n-form-item>
          <n-form-item label="审定人" path="reviewBy">
            <n-input v-model:value="form.reviewBy" placeholder="请输入审定人" clearable></n-input>
          </n-form-item>
          <n-form-item label="抽查人" path="checkBy">
            <n-input v-model:value="form.checkBy" placeholder="请输入抽查人" clearable></n-input>
          </n-form-item>
          <n-form-item label="机构选择" path="groupId">
            <group-select v-model:value="form.groupId"></group-select>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="编码" path="code">
            <n-input v-model:value="form.code" placeholder="请输入编码" clearable></n-input>
          </n-form-item>
          <n-form-item label="调查日期" path="investigaTime">
            <n-date-picker v-model:value="form.investigaTime" clearable type="datetime" placeholder="请输入调查日期" />
          </n-form-item>
          <n-form-item label="审定日期" path="reviewTime">
            <n-date-picker v-model:value="form.reviewTime" clearable type="datetime" placeholder="请输入审定日期" />
          </n-form-item>
          <n-form-item label="抽查日期" path="checkTime">
            <n-date-picker v-model:value="form.checkTime" clearable type="datetime" placeholder="请输入抽查日期" />
          </n-form-item>
          <n-form-item label="区域选择" path="areaId">
            <CascadeArea v-model:value="form.areaId" filterable></CascadeArea>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="8">
          <n-form-item label="省" path="province">
            <n-input v-model:value="form.province" placeholder="请输入省" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="市" path="city">
            <n-input v-model:value="form.city" placeholder="请输入市" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="县" path="county">
            <n-input v-model:value="form.county" placeholder="请输入县" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="档案类型" path="type">
        <h-dict-radio
          v-model:value="form.type"
          placeholder="请输入档案类型"
          clearable
          type="third_cultural_relic_archive_type"
        ></h-dict-radio>
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
      <ThirdView :third-cultural-record-id="thirdId" :title="thirdTitle"></ThirdView>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get, batchImport,culturalCreate } from '../../api/third/thirdCulturalRelicCover';
import { ref, h ,watchEffect} from 'vue';
import {NButton,useMessage} from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import CascadeArea from '@/modules/sys/components/form/field/CascadeArea.vue';
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import ThirdView from '@/modules/archive/components/third/ThirdView.vue';
import { RouterLink } from 'vue-router';
import { confirm } from '@/utils';
const message = useMessage();
const props =defineProps({
   showEdit:{
    type:Boolean,
    default:true

   },
    queryParams: {
    type: Object,
    default: () => {},
  },
})
const columns = [
  {
    key: 'name',
    title: '名称',
    fixed: 'left',
    columnType: 'title',
    resizable: true,
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    titleAlign: 'center',
    width: '200px',
  },
  { key: 'code', title: '编码', titleAlign: 'center', width: '120px', ellipsis: { tooltip: true }, resizable: true },
  {
    key: 'thirdCulturalRelicBasic.level',
    title: '级别',
    titleAlign: 'center',
    resizable: true,
    ellipsis: { tooltip: true },
    width: '120px',
  },
  {
    key: 'thirdCulturalRelicBasic.type',
    title: '类别',
    titleAlign: 'center',
    resizable: true,
    ellipsis: { tooltip: true },
    width: '120px',
  },
  { key: 'thirdCulturalRelicBasic.year', title: '年代', titleAlign: 'center', width: '120px' },

  // { key: 'area.name', title: '区域', titleAlign: 'center', width: '150px' },
  // { key: 'group.name', title: '机构', titleAlign: 'center', width: '150px' },
  { key: 'category', title: '档案类型', align: 'center', width: '100px' },
  { key: 'investigaBy', title: '调查人', titleAlign: 'center', width: '100px' },
  { key: 'investigaTime', title: '调查日期', columnType: 'date', width: '100px' },
  {
    key: 'path',
    title: '编辑三普数据',
    align: 'center',
    width: '220px',
    show:props.showEdit,
    render(row: any) {
      return h(
        RouterLink,
        {
          to: { path: '/archive/third/detail', query: { thirdCulturalRecordId: row.id, title: row.name, code: 'Fm' } },
          class: 'mx-3',
        },
        { default: () => '编辑三普数据' },
      );
    },
  },
  {
    key:'operate',
    type: 'operate',
    show:props.showEdit,
    title: '操作',
    align: 'center',
    width: '220px',
    fixed: 'right',
    render(row: any) {
      return h(
        NButton,
        {
          size:'tiny',
          type:'info',
          class:'mx-3',
          onClick:()=>{
          culturalCreate({id:row.id})
          }
        },
         {
          default: () => '同步',
        },
      )

  }
}
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const detailModel = ref(false);

const thirdId = ref();
const thirdTitle = ref();
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
  type: '',
  name: '',
  code: '',
  province: '',
  city: '',
  county: '',
  areaId: '',
  groupId: '',
  investigaBy: '',
  investigaTime: null,
  reviewBy: '',
  reviewTime: null,
  checkBy: '',
  checkTime: null,
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
  province: {
    required: true,
    message: '请输入省',
  },
  city: {
    required: true,
    message: '请输入市',
  },
  county: {
    required: true,
    message: '请输入县',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  code: '',
  level: '',
  type: '',
  yearList: [],
  thirdId: '',
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
  thirdId.value = row.id;
  thirdTitle.value = row.name;
  detailModel.value = true;
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

watchEffect(() => {
  const { queryParams } = props;
  if (queryParams) {
    Object.assign(paramsForm.value, queryParams);
  }
});
</script>

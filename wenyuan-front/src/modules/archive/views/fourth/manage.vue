<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1800"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="编码" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="请输入编号" clearable></n-input>
      </n-form-item>
      <n-form-item label="级别" path="level">
        <h-dict-select
          v-model:value="paramsForm.level"
          type="fourth_heritage_level"
          placeholder="请选择级别"
          filterable
          clearable
        ></h-dict-select>
      </n-form-item>
      <n-form-item label="类别" path="type">
        <h-dict-select v-model:value="paramsForm.type" type="fourth_cultural_relics_type_one" placeholder="请选择类别"
          filterable clearable></h-dict-select>
      </n-form-item>
      <n-form-item label="年代" path="code">
        <!-- <h-dict-select :multiple="true" clearable :returnArray="true" v-model:value="paramsForm.yearList" type="four_cultural_relic_tjnd" placeholder="请选择年代"
          filterable ></h-dict-select> -->
        <n-input v-model:value="paramsForm.year" placeholder="请输入年代" clearable></n-input>
      </n-form-item>
      <n-form-item label="状态" path="fourthId">
        <h-dict-select
          v-model:value="paramsForm.fourthId"
          placeholder="请选择关联状态"
          type="sys_ok"
          clearable
        ></h-dict-select>
      </n-form-item>
    </template>
    <template #toolbar-extra>
      <h-dialog-import-excel-form
        file-upload-url="fourth/cultural/relic/cover/import"
        accept="application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document
"
      >
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button type="primary" @click="handleBatchImport">服务器批量导入</n-button>
          </template>
          需要先上传文件到服务器目录doc/fourth
        </n-tooltip>
      </h-dialog-import-excel-form>
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
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="档案类型" path="category">
            <h-dict-select
              v-model:value="form.category"
              type="fourth_cultural_archives_type"
              placeholder="请选择档案类型"
              clearable
            ></h-dict-select>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="8">
          <n-form-item label="省(自治区、直辖市)" path="province">
            <CascadeChild v-model:value="form.province" filterable :type="2"></CascadeChild>
          </n-form-item>
        </n-gi>
        <n-gi span="8" v-if="form.province">
          <n-form-item label="市(地区、州、盟)" path="city">
            <CascadeChild v-model:value="form.city" filterable :parentId="form.province" :type="3"></CascadeChild>
          </n-form-item>
        </n-gi>
        <n-gi span="8" v-if="form.city">
          <n-form-item label="县(区、市、旗)" path="county">
            <CascadeChild v-model:value="form.county" filterable :parentId="form.city" :type="4"></CascadeChild>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="调查人" path="investigaBy">
            <n-input v-model:value="form.investigaBy" placeholder="请输入调查人" clearable></n-input>
          </n-form-item>

          <n-form-item label="审定人" path="reviewBy">
            <n-input v-model:value="form.reviewBy" placeholder="请输入审定人" clearable></n-input>
          </n-form-item>
          <n-form-item label="抽查人" path="checkBy">
            <n-input v-model:value="form.checkBy" placeholder="请输入抽查人" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="调查日期" path="investigaTime">
            <n-date-picker clearable v-model:value="form.investigaTime" type="date" placeholder="请输入调查日期" />
          </n-form-item>
          <n-form-item label="审定日期" path="reviewTime">
            <n-date-picker clearable v-model:value="form.reviewTime" type="date" placeholder="请输入审定日期" />
          </n-form-item>
          <n-form-item label="抽查日期" path="checkTime">
            <n-date-picker clearable v-model:value="form.checkTime" type="date" placeholder="请输入抽查日期" />
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="文物类别" path="type">
            <h-dict-select v-model:value="form.type" type="fourth_cultural_relics_type_one" clearable></h-dict-select>
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
          <n-form-item label="机构选择" path="groupId">
            <group-select v-model:value="form.groupId"></group-select>
          </n-form-item>
          <n-form-item label="区域选择" path="areaId">
            <CascadeArea v-model:value="form.areaId" filterable></CascadeArea>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="编制单位" path="productionCompany">
            <n-input v-model:value="form.productionCompany" placeholder="请输入编制单位" clearable></n-input>
          </n-form-item>
          <n-form-item label="专题标签" path="tag">
            <h-dict-select type="jiangxi_basic_heritage_tag" v-model:value="form.tag" placeholder="请输入专题标签" clearable>
            </h-dict-select>
          </n-form-item>
        </n-gi>
      </n-grid>
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
      <fourthView :fourthCulturalRecordId="fourthId" :title="fourthTitle"></fourthView>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get, batchImport, culturalCreate } from '../../api/fourth/fourthCulturalRelicCover';
import { ref, h, watch } from 'vue';
import { NButton } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import CascadeArea from '@/modules/sys/components/form/field/CascadeArea.vue';
import CascadeChild from '@/modules/sys/components/form/field/CascadeChild.vue';
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import fourthView from '@/modules/archive/components/fourth/FourthView.vue';
import { RouterLink, useRoute } from 'vue-router';
import { confirm } from '@/utils';

const route = useRoute();

const columns = [
  {
    key: 'name',
    title: '名称',
    width: '200px',
    resizable: true,
    fixed: 'left',
    columnType: 'title',
    ellipsis: { tooltip: true },
    // render(row: any) {
    //   return h(
    //     RouterLink,
    //     {
    //       to: { path: './detailView', query: { fourthCulturalRecordId: row.id, title: row.name } },
    //       class: 'mx-3',
    //     },
    //     { default: () => row.name },
    //   );
    // },
  },
  {
    key: 'code',
    title: '编码',
    width: '200px',
    resizable: true,
    ellipsis: true,
  },
  {
    key: 'type',
    title: '类别',
    width: '200px',
    resizable: true,
    ellipsis: true,
  },
  {
    key: 'fourthCulturalRelicBasic.level',
    title: '级别',
    width: '180px',
    textAlign: 'center',
    resizable: true,
    ellipsis: true,
    align: 'center',
  },
  {
    key: 'fourthCulturalRelicBasic.year',
    title: '年代',
    width: '120px',
    resizable: true,
    ellipsis: true,
    textAlign: 'center',
    align: 'center',
  },


  { key: 'productionCompany', title: '编制单位', width: '120px', resizable: true, ellipsis: true },
  { key: 'investigaBy', title: '调查人', width: '120px', resizable: true, ellipsis: true },
  { key: 'investigaTime', title: '调查日期', columnType: 'date', width: '120px' },
  {
    key: 'fourthCulturalRelicBasic.address',
    title: '地址',
    width: '120px',
    resizable: true,
    ellipsis: true,
    textAlign: 'center',
    align: 'center',
  },
  {
    key: 'path',
    title: '编辑四普数据',
    align: 'center',
    width: '220px',
    render(row: any) {
      return h(
        RouterLink,
        {
          to: {
            path: '/archive/fourth/detail',
            query: { fourthCulturalRecordId: row.id, title: row.name, code: 'Fm' },
          },
          class: 'mx-3',
        },
        { default: () => '编辑四普数据' },
      );
    },
  },
  {
    key:'operate',
    type: 'operate',
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
const fourthId = ref();
const fourthTitle = ref();
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
  category: '',
  name: '',
  code: '',
  province: '',
  city: '',
  county: '',
  areaId: '',
  groupId: '',
  productionCompany: '',
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
  tag: ''
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
  type: {
    required: true,
    message: '请选择文物类别',
  },
  category: {
    required: true,
    message: '请选择档案类型',
  },
  code: {
    required: true,
    message: '请输入编码',
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
  fourthId: '',
  year: '',
  tag: route.query.tag || '' as any,
});

watch(
  () => route.query.tag,
  (v) => {
    paramsForm.value.tag = v
    refPageTable.value?.handleRefresh();
  },
  { immediate: true },
);

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
  // Object.assign(form.value, row);
  // get(row.id).then((resp) => {
  //   Object.assign(form.value, resp.data);
  fourthId.value = row.id;
  fourthTitle.value = row.name;
  detailModel.value = true;
  // });
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
    content: `清注意，即将服务器导入数据`,
    onPositiveClick: () => {
      batchImport().finally(() => {
        refPageTable.value.handleRefresh();
      });
    },
  });
}
</script>

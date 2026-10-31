<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    v-bind="$attrs"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    @loadData="handleLoad"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="安全保卫组织" path="aqbhzz">
        <n-input v-model:value="paramsForm.aqbhzz" placeholder="请输入安全保卫组织" clearable></n-input>
      </n-form-item>
      <n-form-item label="类型" path="type">
        <h-dict-select
          v-model:value="paramsForm.type"
          type="four_cultural_relic_aqbw_type"
          placeholder="请选择类别"
        ></h-dict-select>
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
          <n-form-item label="四有档案" path="fourCulturalRecordId">
            <CulturalRecordSelect
              disabled
              v-model:value="form.fourCulturalRecordId"
              placeholder="请选择四有档案"
              clearable
            ></CulturalRecordSelect>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="类型" path="type">
            <h-dict-select
              v-model:value="form.type"
              type="four_cultural_relic_aqbw_type"
              placeholder="请选择类别"
            ></h-dict-select>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="安全保卫组织" path="aqbhzz">
        <n-input v-model:value="form.aqbhzz" placeholder="请输入安全保卫组织" clearable></n-input>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="监控设施" path="jkss">
            <n-input v-model:value="form.jkss" placeholder="请输入监控设施" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="防盗设施" path="fdss">
            <n-input v-model:value="form.fdss" placeholder="请输入防盗设施" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="消防设施" path="xfss">
            <n-input v-model:value="form.xfss" placeholder="请输入消防设施" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="通讯设施" path="txss">
            <n-input v-model:value="form.txss" placeholder="请输入通讯设施" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="详细内容" path="content">
        <n-input v-model:value="form.content" type="textarea" rows="7"></n-input>
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
      <div class="page-detail" ref="refDetail">
        <h2 class="text-center">安全保卫工作情况</h2>
        <div style="position: absolute; right: 10px; top: 10px">
          <Print :printable="refDetail" size="large"></Print>
        </div>
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>四有档案</td>
            <td colSpan="3">{{ form.fourCulturalRecordFourCulturalRecord.name || '--' }}</td>
            <td>类型</td>
            <td colSpan="4">
              <h-dict-text v-model:value="form.type" type="four_cultural_relic_aqbw_type"></h-dict-text>
            </td>
          </tr>
          <tr>
            <td>名称</td>
            <td colSpan="8">{{ form.name || '--' }}</td>
          </tr>
          <tr>
            <td>安全保卫组织</td>
            <td colSpan="8">{{ form.aqbhzz || '--' }}</td>
          </tr>
          <tr>
            <td>监控设施</td>
            <td colSpan="4">{{ form.jkss || '--' }}</td>
            <td>防盗设施</td>
            <td colSpan="3">{{ form.fdss || '--' }}</td>
          </tr>
          <tr>
            <td>消防设施</td>
            <td colSpan="4">{{ form.xfss || '--' }}</td>
            <td>通讯设施</td>
            <td colSpan="3">{{ form.txss || '--' }}</td>
          </tr>
          <tr>
            <td>详细内容</td>
            <td colSpan="8">{{ form.content || '--' }}</td>
          </tr>
        </n-table>
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/four/fourCulturalRecordMainText12';
import { onMounted, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRecordSelect from '../CulturalRecordSelect.vue';
import Print from '@/components/basic/print/Print.vue';
import { name } from 'mars3d';

const columns = [
  {
    key: 'name',
    title: '名称',
    ellipsis: { tooltip: true },
    titleAlign: 'center',
    columnType: 'title',
    width: '170px',
  },
  {
    key: 'aqbhzz',
    title: '安全保卫组织',
    ellipsis: { tooltip: true },
    titleAlign: 'center',
    width: '190px',
  },
  {
    key: 'type',
    title: '类型',
    titleAlign: 'center',
    ellipsis: { tooltip: true },
    dictType: 'four_cultural_relic_aqbw_type',
  },
  { key: 'jkss', title: '监控设施', titleAlign: 'center', ellipsis: { tooltip: true } },
  { key: 'fdss', title: '防盗设施', titleAlign: 'center', ellipsis: { tooltip: true } },
  { key: 'xfss', title: '消防设施', titleAlign: 'center', ellipsis: { tooltip: true } },
  { key: 'txss', title: '通讯设施', titleAlign: 'center', ellipsis: { tooltip: true } },
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
const props = defineProps({ fourCulturalRecordId: { type: String, required: true } });

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  fourCulturalRecordId: props.fourCulturalRecordId,
  name: '',
  aqbhzz: '',
  type: '',
  jkss: '',
  fdss: '',
  xfss: '',
  txss: '',
  content: '',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
  fourCulturalRecordFourCulturalRecord: { name: '' },
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  fourCulturalRecordId: {
    required: true,
    message: '请选择四有档案',
  },
  name: {
    required: true,
    message: '请输入名称',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  fourCulturalRecordId: props.fourCulturalRecordId,
  name: '',
  aqbhzz: '',
  type: '',
});

const formType = ref('add');
const refDetail = ref();
const detailModel = ref(false);

function handleLoad(resp: any) {
  if (resp.data && resp.data.total === 1) {
    handleView(resp.data.list[0]);
  }
}

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
  formTitle.value = `编辑安全保卫工作情况`;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    detailModel.value = true;
  });
  formTitle.value = `查看安全保卫工作情况`;
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
<style lang="scss" scoped>
.page-detail {
  table {
    table-layout: fixed;

    th {
      white-space: normal;
    }
  }
}
</style>

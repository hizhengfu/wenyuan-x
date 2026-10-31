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
      <n-form-item label="序号" path="serialNumber">
        <n-input v-model:value="paramsForm.serialNumber" placeholder="请输入序号" clearable></n-input>
      </n-form-item>
      <n-form-item label="拓片号" path="rubbingNumber">
        <n-input v-model:value="paramsForm.rubbingNumber" placeholder="请输入拓片号" clearable></n-input>
      </n-form-item>
      <n-form-item label="题名" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入题名" clearable></n-input>
      </n-form-item>
      <n-form-item label="锤拓人" path="ctr">
        <n-input v-model:value="paramsForm.ctr" placeholder="请输入锤拓人" clearable></n-input>
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
      <n-form-item label="四有档案" path="fourCulturalRecordId">
        <CulturalRecordSelect
          disabled
          v-model:value="form.fourCulturalRecordId"
          placeholder="请选择四有档案"
          clearable
        ></CulturalRecordSelect>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="序号" path="serialNumber">
            <n-input-number v-model:value="form.serialNumber" placeholder="请输入序号" clearable></n-input-number>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="拓片号" path="rubbingNumber">
            <n-input v-model:value="form.rubbingNumber" placeholder="请输入拓片号" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="题名" path="name">
        <n-input v-model:value="form.name" placeholder="请输入题名" clearable></n-input>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="张数" path="sheetsNumber">
            <n-input-number v-model:value="form.sheetsNumber" placeholder="请输入张数" clearable></n-input-number>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="规格" path="specification">
            <n-input v-model:value="form.specification" placeholder="请输入规格" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="现存状态" path="xczt">
        <n-input v-model:value="form.xczt" placeholder="请输入现存状态" clearable></n-input>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="锤拓人" path="ctr">
            <n-input v-model:value="form.ctr" placeholder="请输入锤拓人" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="锤拓时间" path="ctsj">
            <n-date-picker clearable v-model:value="form.ctsj" placeholder="请选择锤拓时间" />
            <!-- <n-input v-model:value="form.ctsj" placeholder="请输入锤拓时间" clearable></n-input> -->
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="录文" path="content">
        <n-input v-model:value="form.content" type="textarea" placeholder="请输入录文" rows="7"></n-input>
      </n-form-item>
      <n-form-item label="备注" path="remarks">
        <n-input v-model:value="form.remarks" type="textarea" placeholder="请输入备注"></n-input>
      </n-form-item>
      <HUpload :contentId="form.id" :form="form" label="上传文件"></HUpload>
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
        <h2 class="text-center">拓片登记表</h2>
        <h3 class="text-center">摩崖石刻、碑碣、重要铭刻等拓片</h3>
        <div style="position: absolute; right: 10px; top: 10px">
          <Print :printable="refDetail" size="large"></Print>
        </div>
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>四有档案</td>
            <td colSpan="8">{{ form.fourCulturalRecordFourCulturalRecord.name || '--' }}</td>
          </tr>
          <tr>
            <td>序号</td>
            <td colSpan="3">{{ form.serialNumber || '--' }}</td>
            <td>拓片号</td>
            <td colSpan="4">{{ form.rubbingNumber || '--' }}</td>
          </tr>
          <tr>
            <td>题名</td>
            <td colSpan="8">{{ form.name || '--' }}</td>
          </tr>
          <tr>
            <td>张数</td>
            <td colSpan="3">{{ form.sheetsNumber || '--' }}</td>
            <td>规格</td>
            <td colSpan="4">{{ form.specification || '--' }}</td>
          </tr>
          <tr>
            <td>现存状态</td>
            <td colSpan="8">{{ form.xczt || '--' }}</td>
          </tr>
          <tr>
            <td>锤拓人</td>
            <td colSpan="4">{{ form.ctr || '--' }}</td>
            <td>锤拓时间</td>
            <td colSpan="3">{{ form.ctsj ? dayjs(form.ctsj).format('YYYY-MM-DD') : '' }}</td>
          </tr>
          <tr>
            <td>录文</td>
            <td colSpan="8">{{ form.content || '--' }}</td>
          </tr>
          <tr>
            <td>备注</td>
            <td colSpan="8">{{ form.remarks || '--' }}</td>
          </tr>
          <tr>
            <td>上传文件</td>
            <td colSpan="8"><h-file-list :contentId="form.id"></h-file-list></td>
          </tr>
        </n-table>
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/four/culturalRecordMainTapian';
import { onMounted, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRecordSelect from '../CulturalRecordSelect.vue';
import Print from '@/components/basic/print/Print.vue';
import dayjs from 'dayjs';

const props = defineProps({ fourCulturalRecordId: { type: String, required: true } });

const columns = [
  { key: 'serialNumber', title: '序号', titleAlign: 'center', ellipsis: { tooltip: true }, width: '80px' },
  { key: 'rubbingNumber', title: '拓片及摹本号', titleAlign: 'center', ellipsis: { tooltip: true }, width: '130px' },
  {
    key: 'name',
    title: '题名',
    columnType: 'title',
    ellipsis: { tooltip: true },
    titleAlign: 'center',
    width: '200px',
  },
  { key: 'sheetsNumber', title: '张数', titleAlign: 'center', ellipsis: { tooltip: true }, width: '80px' },
  { key: 'ctr', title: '锤拓人', titleAlign: 'center', ellipsis: { tooltip: true }, width: '100px' },
  {
    key: 'ctsj',
    title: '锤拓时间',
    align: 'center',
    ellipsis: { tooltip: true },
    width: '100px',
    columnType: 'date',
  },
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
  fourCulturalRecordId: props.fourCulturalRecordId,
  serialNumber: null,
  rubbingNumber: '',
  name: '',
  sheetsNumber: null,
  specification: '',
  xczt: '',
  ctr: '',
  ctsj: null,
  content: '',
  remarks: '',
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
    message: '请输入题名',
  },
  serialNumber: {
    required: true,
    message: '请输入序号',
  },
  rubbingNumber: {
    required: true,
    message: '请输入拓片号',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  fourCulturalRecordId: props.fourCulturalRecordId,
  serialNumber: '',
  rubbingNumber: '',
  name: '',
  ctr: '',
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
  formTitle.value = `编辑摩崖石刻、碑碣、重要铭刻等拓片`;
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
  formTitle.value = `查看摩崖石刻、碑碣、重要铭刻等拓片`;
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

<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    @add="handleAdd"
    v-if="props.showList"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm v-if="props.queryShow">
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
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
          <n-form-item label="序号" path="serialNumber">
            <n-input v-model:value="form.serialNumber" placeholder="请输入序号" clearable></n-input>
          </n-form-item>
          <n-form-item label="图号" path="drawingNo">
            <n-input v-model:value="form.drawingNo" placeholder="请输入图号/照片号" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="比例" path="proportion">
            <n-input v-model:value="form.proportion" placeholder="请输入比例" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="绘制人" path="drawingBy">
            <n-input v-model:value="form.drawingBy" placeholder="请输入绘制人" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="绘制时间" path="drawingTime">
            <n-date-picker clearable v-model:value="form.drawingTime" type="datetime" placeholder="请选择绘制时间" />
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="图纸路径" path="path">
        <n-input v-model:value="form.path" placeholder="请输入图纸路径" clearable></n-input>
      </n-form-item>
      <n-form-item label="文字说明" path="remark">
        <n-input v-model:value="form.remark" placeholder="请输入文字说明" clearable type="textarea"></n-input>
      </n-form-item>
      <HUpload :contentId="form.id" :form="form" label="上传附件"></HUpload>
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
      <n-table class="info-table" :single-line="false">
        <tr>
          <td>序号</td>
          <td colspan="3">{{ form.serialNumber || '--' }}</td>
          <td>名称</td>
          <td colspan="3">{{ form.name || '--' }}</td>
          <td rowspan="2">图纸</td>
          <td colspan="3" rowspan="2">
            <h-image v-if="form.path" :src="form.path" size="50"></h-image>
          </td>
        </tr>
        <tr>
          <td>图号</td>
          <td colspan="3">{{ form.drawingNo || '--' }}</td>

          <td>绘制人</td>
          <td colspan="3" style="border-right: 1px solid #efeff5">{{ form.drawingBy || '--' }}</td>
        </tr>
        <tr>
          <td>比例</td>
          <td colspan="3">{{ form.proportion || '--' }}</td>
          <td>绘制时间</td>
          <td colspan="7">{{ form.drawingTime ? dayjs(form.drawingTime).format('YYYY-MM-DD') : '--' }}</td>
        </tr>
        <tr>
          <td>文字说明</td>
          <td colspan="8">{{ form.remark || '--' }}</td>
        </tr>
      </n-table>
    </h-page-form>
  </h-page-data-table>
  <HDataIterator :title="'图纸册页'" :fetchData="list" :params="paramsForm" size="small" :showHeader="false" v-else>
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
    </template>
    <template v-slot="{ list }">
      <div class="card-grid">
        <n-card
          class="card"
          hoverable
          embedded
          header-style="padding: 10px;"
          v-for="(item, index) in list"
          :key="index"
        >
          <template #header>
            <n-space align="center">
              <span style="font-weight: 700">
                {{ item.name }}
              </span>
            </n-space>
          </template>
          <div style="display: flex">
            <n-image
              :src="fileUrl(item.path) + '?w=200&h=300'"
              :preview-src="fileUrl(item.path)"
              width="100px"
              height="150px"
              v-if="item.path"
            />
            <div class="ml-5">
              <p>
                <b>序号:</b>
                {{ item.serialNumber ? item.serialNumber : '暂无数据' }}
              </p>
              <p>
                <b>图号:</b>
                {{ item.drawingNo ? item.drawingNo : '暂无数据' }}
              </p>
              <p>
                <b>比例:</b>
                {{ item.proportion ? item.proportion : '暂无数据' }}
              </p>
              <p>
                <b>绘制人:</b>
                {{ item.drawingBy ? item.drawingBy : '暂无数据' }}
              </p>
              <p>
                <b>绘制时间:</b>
                {{ item.drawingTime ? dayjs(item.drawingTime).format('YYYY-MM-DD') : '暂无数据' }}
              </p>
              <p>
                <b>文字说明:</b>
                {{ item.remark ? item.remark : '暂无数据' }}
              </p>
            </div>
          </div>
        </n-card>
      </div>
    </template>
  </HDataIterator>
</template>

<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/third/thirdCulturalRelicDrawing';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import dayjs from 'dayjs';
import { fileUrl } from '@/utils';

const props = defineProps({
  queryShow: {
    type: Boolean,
    default: true,
  },
  thirdCulturalRecordId: { type: String, required: true },
  showList: {
    type: Boolean,
    default: true,
  },
});

const columns = [
  { key: 'thirdCulturalRelicCoverVo.name', title: '不可移动文物', align: 'center' },
  { key: 'serialNumber', title: '序号', titleAlign: 'center' },
  {
    key: 'name',
    title: '名称',

    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    titleAlign: 'center',
  },
  { key: 'drawingNo', title: '图号', titleAlign: 'center' },
  { key: 'proportion', title: '比例', titleAlign: 'center' },
  { key: 'drawingBy', title: '绘制人', align: 'center' },
  { key: 'drawingTime', title: '绘制时间', columnType: 'dateTime', align: 'center' },
  // { key: 'image', title: '图纸', columnType: 'image', align: 'center' },
  // { key: 'path', title: '图纸路径' },
  // { key: 'remark', title: '文字说明' },
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
  culturalRelicId: props.thirdCulturalRecordId,
  serialNumber: '',
  name: '',
  drawingNo: '',
  proportion: '',
  drawingBy: '',
  drawingTime: null,
  path: '',
  remark: '',
  createTime: null,
  createBy: '',
  updateTime: null,
  updateBy: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {};

/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  culturalRelicId: props.thirdCulturalRecordId,
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
    detailModel.value = true;
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

<style lang="scss" scoped>
.card {
  max-height: 390px;
  flex: 25%;
  cursor: pointer;
  overflow: scroll;
  margin-left: 7px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.card {
  max-height: 390px;
  cursor: pointer;
  overflow: scroll;
}
</style>

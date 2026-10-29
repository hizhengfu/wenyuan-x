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
      <n-form-item label="库房名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入库房名称" clearable></n-input>
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
      <n-grid x-gap="15">
        <n-grid-item span="16">
          <n-form-item label="库房名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入库房名称" clearable></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="库房编号(留空自动生成)" path="code">
            <n-input v-model:value="form.code" placeholder="请输入库房编号"></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="15">
        <n-grid-item span="12">
          <n-form-item label="所属库区" path="areaId">
            <WmsAreaSelect v-model:value="form.areaId" placeholder="请输入所属库区" clearable></WmsAreaSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="安防等级" path="securityLevel">
            <h-dict-select
              v-model:value="form.securityLevel"
              clearable
              type="wms_storage_security_level"
              placeholder="请选择安防等级"
            ></h-dict-select>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="15">
        <n-grid-item span="12">
          <n-form-item label="温度控制范围（℃）" path="temperatureRange">
            <n-input v-model:value="form.temperatureRange" placeholder="请输入温度控制范围（℃）" clearable></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="湿度控制范围（%）" path="humidityRange">
            <n-input v-model:value="form.humidityRange" placeholder="请输入湿度控制范围（%）" clearable></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="15">
        <n-grid-item span="16">
          <n-form-item label="库房位置" path="location">
            <n-input v-model:value="form.location" placeholder="请输入库房位置" clearable></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="库房经纬度" path="latlng">
            <HFieldLatlng v-model:value="form.latlng" placeholder="请输入库房经纬度" clearable></HFieldLatlng>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="缩略图" path="image">
        <n-input v-model:value="form.image" placeholder="请输入缩略图" clearable></n-input>
      </n-form-item>
      <n-form-item label="库房描述" path="remarks">
        <n-input type="textarea" rows="10" placeholder="请输入库房描述" v-model:value="form.remarks"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/storageRoom';
import { WmsAreaSelect } from '../../../components/form/field';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';

const columns = [
  {
    key: 'name',
    title: '库房名称',
    columnType: 'title',
    width: '220px',
    fixed: 'left',
    resizable: true,
    ellipsis: true,
  },
  { key: 'code', title: '库房编号', width: '160px', resizable: true, ellipsis: true },
  { key: 'areaWmsStorageArea.name', title: '所属库区', width: '200px', resizable: true, ellipsis: true },
  { key: 'location', title: '库房位置', width: '200px', resizable: true, ellipsis: true },
  { key: 'temperatureRange', title: '温度控制范围（℃）', width: '160px', resizable: true, ellipsis: true },
  { key: 'humidityRange', title: '湿度控制范围（%）', width: '160px', resizable: true, ellipsis: true },
  { key: 'securityLevel', title: '安防等级', width: '160px', resizable: true, ellipsis: true },
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
  areaId: '',
  code: '',
  image: '',
  name: '',
  temperatureRange: '',
  humidityRange: '',
  securityLevel: '',
  latlng: '',
  location: '',
  remarks: '',
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
    message: '请输入库房名称（如：青铜器库房）',
  },
  createTime: {
    required: true,
    message: '请输入创建时间',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
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

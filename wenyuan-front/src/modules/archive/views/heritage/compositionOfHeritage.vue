<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
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
          <n-form-item label="所在省份" path="areaId">
            <n-input v-model:value="form.areaId" placeholder="请输入所在省份" clearable></n-input>
          </n-form-item>
          <n-form-item label="遗产级别" path="level">
            <n-input v-model:value="form.level" placeholder="请输入遗产级别" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="详细地址" path="address">
            <n-input v-model:value="form.address" placeholder="请输入详细地址" clearable></n-input>
          </n-form-item>
          <n-form-item label="遗产类别" path="category">
            <n-input v-model:value="form.category" placeholder="请输入遗产类别" clearable></n-input>
          </n-form-item>
          <n-form-item label="遗产面积" path="space">
            <n-input v-model:value="form.space" placeholder="请输入面积" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="东经" path="latitude">
            <n-input v-model:value="form.latitude" placeholder="请输入东经" clearable></n-input>
          </n-form-item>
          <n-form-item label="经度" path="lng">
            <n-input v-model:value="form.lng" placeholder="请输入经度" clearable></n-input>
          </n-form-item>
 <n-form-item label="年代" path="year">
            <n-input v-model:value="form.year" placeholder="请输入年代" clearable></n-input>
          </n-form-item>
         
        </n-gi>
        <n-gi span="12">
          <n-form-item label="北纬" path="longitude">
            <n-input v-model:value="form.longitude" placeholder="请输入北纬" clearable></n-input>
          </n-form-item>
          <n-form-item label="纬度" path="lat">
            <n-input v-model:value="form.lat" placeholder="请输入纬度" clearable></n-input>
          </n-form-item>

         
        </n-gi>
      </n-grid>

      <n-form-item label="保护范围" path="bhfwData">
        <n-input v-model:value="form.bhfwData" type="textarea" clearable></n-input>
      </n-form-item>
      <n-form-item label="本体范围" path="btfwData">
         <n-input v-model:value="form.btfwData" type="textarea" clearable></n-input>
      </n-form-item>

      <n-form-item label="控制地带" path="jskzdzData">
     <n-input v-model:value="form.jskzdzData" type="textarea" clearable></n-input>
      </n-form-item>

      <n-form-item label="备注" path="remarks">
        <n-input v-model:value="form.remarks" placeholder="请输入备注" clearable type="textarea"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../api/heritage/compositionOfHeritage';
import { ref ,watch,computed} from 'vue';
import { cloneDeep } from 'lodash-es';
import {useRoute} from 'vue-router'
const route = useRoute()

const columns = [
  { key: 'name', title: '名称', columnType: 'title', width: '200px', resizable: true, ellipsis: { tooltip: true } },
  { key: 'areaId', title: '所在省份' },
  { key: 'address', title: '详细地址' },
  { key: 'level', title: '遗产级别' },
  { key: 'category', title: '遗产类别' },
  { key: 'year', title: '遗产年代' },
  { key: 'space', title: '遗产面积' },
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
 const heritageId = computed(() => {
  return route.query.heritageId
});
/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
 heritageId: heritageId.value,
  name: '',
  areaId: '',
  address: '',
  level: '',
  category: '',
  year: '',
  space: '',
  remarks: '',
  lat: '',
  lng: '',
  latitude: '',
  longitude: '',
  btfwData: '',
  status: '',
  bhfwData: '',
  jskzdzData: '',
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
  heritageId: {
    required: true,
    message: '请输入关联遗产',
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
  name: '',
   heritageId: heritageId.value,
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
    data.latlng=''
    return edit(data);
  }
}
watch(() => route.query.heritageId, (newVal,oldVal) => {
     if(newVal){
       paramsForm.value.heritageId = newVal;
      form.value.heritageId = newVal;
      refPageTable.value?.handleQuery();
     }
},{deep: true,immediate: true})
</script>

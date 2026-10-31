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
            <n-input label="名称" v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
           <n-form-item label="关联遗产构成" path="composeId">
            <AssociationIdSelect
              label="关联遗产构成"
              v-model:value="form.composeId"
              :fetchdata="composeList"
              :params="{heritageId: heritageId}"
              placeholder="请选择关联遗产构成"
              clearable
            ></AssociationIdSelect>
          </n-form-item>
          <!-- <n-form-item label="遗产构成名称" path="heritageComposeName">
            <n-input
              label="遗产构成名称"
              v-model:value="form.heritageComposeName"
              placeholder="请输入遗产构成名称"
              clearable
            ></n-input>
          </n-form-item> -->
        </n-gi>
        <n-gi span="10">
         
          <n-form-item label="年代" path="year">
            <n-input label="年代" v-model:value="form.year" placeholder="请输入年代" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="内容" path="content">
        <h-editor label="内容" v-model:value="form.content" type="textarea" clearable></h-editor>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../api/heritage/historicalEvolution';
import {list  as composeList} from '../../api/heritage/compositionOfHeritage';
import AssociationIdSelect from '../../components/basic/AssociationIdSelect.vue';
import { ref, h, computed, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { RouterLink, useRoute } from 'vue-router';
const route = useRoute();
const columns = [
  {
    key: 'name',
    title: '历史沿革名称',
    fixed: 'left',
    resizable: true,
    ellipsis: { tooltip: true },
    columnType: 'title',
    titleAlign: 'center',
    width: '180px',
  },
    {
    key: 'composeName',
    title: '遗产构成名称',
    fixed: 'left',
    resizable: true,
    ellipsis: { tooltip: true },
    titleAlign: 'center',
    width: '250px',
  },
   {
    key: 'heritageName',
    title: '遗产名称',
    fixed: 'left',
    resizable: true,
    ellipsis: { tooltip: true },
    titleAlign: 'center',
    width: '250px',
  },
  { key: 'year', title: '年代' },
];
const heritageId = computed(() => {
  return route.query.heritageId;
});
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
 heritageId: heritageId.value,
  heritageComposeName: '', //遗产构成名称
  year: '', //年代
  content: '', //内容
  evolutionName: '',
  composeId: '', //关联遗产构成
  name:''
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
 heritageId: heritageId.value
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
  formTitle.value = `编辑「${row.evolutionName}」`;
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
  formTitle.value = `查看「${row.evolutionName}」`;
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
watch(
  () => route.query.heritageId,
  (newVal, oldVal) => {
    if (newVal) {
      paramsForm.value.heritageId = newVal;
      form.value.heritageId = newVal;
      refPageTable.value?.handleQuery();
    }
  },
  { deep: true, immediate: true },
);
</script>

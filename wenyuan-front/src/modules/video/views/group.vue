<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    is-tree
    :scroll-x="1200"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="群组名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入群组名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="群组编码" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="请输入群组编码" clearable></n-input>
      </n-form-item>
      <n-form-item label="地区代码" path="areaId">
        <AreaSelect v-model:value="paramsForm.areaId" placeholder="请选择地区代码" clearable></AreaSelect>
      </n-form-item>
      <n-form-item label="归属服务" path="serverId">
        <VideoServeSelect
          v-model:value="paramsForm.serverId"
          placeholder="请选择视频群组"
          clearable
          filterable
        ></VideoServeSelect>
      </n-form-item>
      <n-form-item label="内容状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="content_status" clearable filterable></h-dict-select>
      </n-form-item>
      <n-form-item label="是否关联数据" path="sourceId">
         <!-- <VideoAssociated v-model:value="paramsForm.sourceId"></VideoAssociated> -->
          <h-dict-select v-model:value="paramsForm.sourceFlag"  type="sys_ok" clearable></h-dict-select>
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
      <n-form-item label="群组名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入群组名称" clearable></n-input>
      </n-form-item>
      <n-grid x-gap="6">
        <n-grid-item span="12">
          <n-form-item label="父级群组" path="parentId">
            <VideoGroupSelect v-model:value="form.parentId" placeholder="请选择父级群组" clearable></VideoGroupSelect>
          </n-form-item>
          <n-form-item label="地区代码" path="areaId">
            <AreaSelect v-model:value="form.areaId" placeholder="请选择地区代码" clearable></AreaSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="归属服务" path="serverId">
            <VideoServeSelect
              v-model:value="form.serverId"
              placeholder="请选择视频群组"
              clearable
              filterable
            ></VideoServeSelect>
          </n-form-item>
          <n-form-item label="群组编码" path="code">
            <n-input v-model:value="form.code" placeholder="请输入群组编码" clearable></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <slot name="sourceId" :form="form">
        <n-form-item label="关联数据" path="sourceId">
          <n-input v-model:value="form.sourceId" placeholder="请输入关联数据" clearable></n-input>
        </n-form-item>
      </slot>

      <n-form-item label="群组备注" path="content">
        <n-input type="textarea" v-model:value="form.content"></n-input>
      </n-form-item>

      <n-form-item label="群组状态" path="status">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../api/group';
import { ref,h } from 'vue';
import { cloneDeep } from 'lodash-es';
import VideoGroupSelect from '../components/form/field/VideoGroupSelect.vue';
import VideoServeSelect from '../components/form/field/VideoServeSelect.vue';
import AreaSelect from '@/modules/sys/components/form/field/AreaSelect.vue';
import VideoAssociated from '../components/form/field/videoAssociated.vue';

const columns = [
  {
    key: 'name',
    title: '群组名称',
    // columnType: 'title',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    resizable: true,
   render(row: any) {
  const handleClick = () => {
    handleView(row); 
  };

  // 使用蓝色字体样式
  const blueTextStyle = { color: '#5298d4', cursor: 'pointer' };
  // 使用黑色字体样式
  const blackTextStyle = { color: 'red' };

  if (row.sourceId) {
    return h(
      'span',
      {
        style: blueTextStyle,
        onClick: handleClick, // 添加点击事件
      },
      {
        default: () => {
          return [
            `${row.name}`, // 显示 name 部分
            h('span', { style: blackTextStyle }, '(已关联)'), // 显示已关联，并且是黑色
          ];
        },
      },
    );
  } else {
    return h(
      'span',
      {
        style: blueTextStyle,
        onClick: handleClick, // 添加点击事件
      },
      {
        default: () => {
          return row.name; // 仅显示 name
        },
      },
    );
  }
}
  
  },
  { key: 'areaId', title: '归属地区', width: '200px', resizable: true },
  { key: 'code', title: '群组编码', width: '360px,resizable: true' },
  { key: 'status', title: '内容状态', dictType: 'content_status', width: '100px', align: 'center' },
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
  parentId: '',
  areaId: '',
  serverId: '',
  name: '',
  code: '',
  content: '',
  sourceId: '',
  status: '',
    sourceType:'1'
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  parentId: {
    required: true,
    message: '请选择父级群组',
  },
  name: {
    required: true,
    message: '请输入群组名称',
  },
  status: {
    required: true,
    message: '请选择群组状态',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  code: '',
  serverId: '',
  areaId: '',
  status: '',
  sourceFlag:'',
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

<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    @add="handleAdd"
    @updateSort="handleUpdateSort"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="场景名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="所属场所" path="venueId">
        <HVenueSelect v-model:value="paramsForm.venueId" placeholder="请选择场所" clearable></HVenueSelect>
      </n-form-item>
      <n-form-item label="内容状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="content_status" clearable filterable></h-dict-select>
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
      <n-grid x-gap="6" :cols="24">
        <n-grid-item :span="18">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name"></n-input>
          </n-form-item>
          <n-form-item label="场馆" path="venueId">
            <HVenueSelect v-model:value="form.venueId" placeholder="请选择场馆"></HVenueSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="6" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <h-avatar v-model:value="form.image" size="120"></h-avatar>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="描述信息" path="content">
        <n-input type="textarea" v-model:value="form.content"></n-input>
      </n-form-item>
      <n-form-item label="状态" path="status">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>
    <h-dialog-form v-model:show="formConfigModel" :title="formSceneTitle" :handleSubmit="handleSaveScene">
      <n-form-item label="场景设备配置" :show-label="false" :show-feedback="false" path="scene_device">
        <n-transfer
          ref="scene_transfer"
          v-model:value="sceneDevice"
          virtual-scroll
          :options="deviceSourceOptions"
          :render-source-list="renderSourceList"
          :render-target-label="renderLabel"
          source-filterable
        />
      </n-form-item>
    </h-dialog-form>
    <HSceneCommand
      v-if="selectSceneId"
      :sceneId="selectSceneId"
      v-model:show="formCommandModel"
      :title="formCommandTitle"
    ></HSceneCommand>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { add, del, edit, getSceneDevice, list, saveSceneDevice, updateSort } from '../../api/scene';
import { treeAll } from '../../api/device';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import HVenueSelect from '../../components/form/field/VenueSelect.vue';
import { NTree, NIcon, TransferRenderSourceList, TransferRenderTargetLabel } from 'naive-ui';
import Icon from '@/components/basic/DynamicIcon.vue';
import { transformToTreeFormat } from '@/utils';
import HSceneCommand from '../../components/SceneCommand.vue';

const columns = [
  {
    key: 'name',
    title: '场景名称',
    columnType: 'title',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    fixed: 'left',
    width: '320px',
    resizable: true,
    rowDrag: true,
  },
  { key: 'venue.name', title: '场所位置', width: '220px', resizable: true },
  { key: 'image', title: '缩略图', width: '80px' },
  { key: 'status', title: '内容状态', dictType: 'content_status', align: 'center', width: '80px' },
  {
    key: 'select',
    title: '关联设备',
    fixed: 'right',
    align: 'center',
    width: '80px',
    render(row: any) {
      return h(
        NIcon,
        {
          size: '22px',
          onClick() {
            handleLoadScene(row);
          },
        },
        { default: () => h(Icon, { icon: 'c:FileTree' }) },
      );
    },
  },
  {
    key: 'config',
    title: '配置指令',
    align: 'center',
    fixed: 'right',
    width: '80px',
    render(row: any) {
      return h(
        NIcon,
        {
          size: '22px',
          onClick() {
            selectSceneId.value = row.id;
            formCommandModel.value = true;
            formCommandTitle.value = `配置「${row.name}」场景指令`;
          },
        },
        { default: () => h(Icon, { icon: 'mdi:cog-transfer' }) },
      );
    },
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
  name: '',
  image: '',
  content: '',
  path: '',
  venueId: '',
  status: '0',
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
  venueId: {
    required: true,
    message: '请选择场馆',
  },
  status: {
    required: true,
    message: '请选择状态',
  },
  path: {
    required: true,
    message: '请输入路径',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  venueId: null,
  status: '',
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
  Object.assign(form.value, row);
  formTitle.value = `编辑「${row.name}」`;
  formModel.value = true;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  formTitle.value = `查看「${row.name}」`;
  formModel.value = true;
}

function handleUpdateSort(items: any) {
  let sorts = items.map((item: any, index: number) => {
    return { id: item.id, sort: index };
  });
  updateSort(sorts).finally(() => {
    refPageTable.value.handleRefresh();
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

// 下面代码为场景配置相关
const formConfigModel = ref(false);
// 已选场景设备
const sceneDevice = ref();
// 场景id
const sceneId = ref();
const formSceneTitle = ref();
//
const deviceOptions = ref();
// 原始数据
const deviceSourceOptions = ref();

//构建树形
const renderSourceList: TransferRenderSourceList = function ({ onCheck, pattern }) {
  return h(NTree, {
    style: 'margin: 0 4px;',
    keyField: 'value',
    checkable: true,
    selectable: false,
    ellipsis: true,
    blockLine: true,
    showIrrelevantNodes: false,
    checkOnClick: true,
    data: deviceOptions.value,
    pattern,
    checkedKeys: sceneDevice.value,
    onUpdateCheckedKeys: (checkedKeys: Array<string | number>) => {
      onCheck(checkedKeys);
    },
  });
};
/**
 * 构建穿梭狂的label
 * */
const renderLabel: TransferRenderTargetLabel = function ({ option }) {
  return h(
    'div',
    {
      style: {
        display: 'flex',
      },
    },
    {
      default: () => [
        h(
          NIcon,
          {
            class: 'mr-1 sortHandle',
            style: {
              display: 'flex',
              cursor: 'move',
              alignSelf: 'center',
            },
          },
          () => {
            return h(Icon, { icon: 'mdi-drag-vertical' });
          },
        ),
        h(
          'div',
          {
            style: {
              display: 'flex',
              marginLeft: '6px',
              userSelect: 'none',
              alignSelf: 'center',
            },
          },
          { default: () => option.label },
        ),
      ],
    },
  );
};

/**
 * 加载场景
 * */
function handleLoadScene(row: any) {
  formConfigModel.value = true;
  sceneId.value = row.id;
  formSceneTitle.value = `配置「${row.name}」场景`;

  treeAll({ type: 'venue', id: row.venueId }).then((resp: any) => {
    let data = (resp.data || []).map((item: any) => {
      return { label: item.name, disabled: item.type !== 'device', value: item.id, ...item };
    });

    deviceOptions.value = transformToTreeFormat(data, { pIdKey: 'parentId' });
    deviceSourceOptions.value = data;
  });

  // 根据当前场景获取已勾选的视频
  getSceneDevice(row.id).then((res: any) => {
    sceneDevice.value = res.data?.map((item: any) => {
      return item.id;
    });
  });
}

function handleSaveScene() {
  return saveSceneDevice(sceneId.value, sceneDevice.value).then((res: any) => {
    // console.log(res);
  });
}

/**
 * 指令表单相关
 * */
const formCommandModel = ref(false);
const formCommandTitle = ref();
const selectSceneId = ref();
</script>

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
      <n-form-item label="所属场馆" path="venueId">
        <HCascaderDevice
          type="venue"
          clearable
          v-model:value="paramsForm.venueId"
          placeholder="请选择场馆"
        ></HCascaderDevice>
      </n-form-item>
      <n-form-item label="配置名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="配置类型" path="type">
        <h-dict-select
          v-model:value="paramsForm.type"
          type="cc_network_type"
          clearable
          filterable
          placeholder="请选择类型"
        ></h-dict-select>
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
      <n-form-item label="名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
      </n-form-item>

      <n-grid x-gap="6" :cols="24">
        <n-grid-item :span="8">
          <n-form-item label="场馆" path="venueId">
            <HVenueSelect v-model:value="form.venueId" placeholder="请选择场馆"></HVenueSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="8">
          <n-form-item label="通信协议" path="type">
            <h-dict-select v-model:value="form.type" type="cc_network_type" clearable filterable></h-dict-select>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="8">
          <n-form-item label="主控协议" path="protocolId">
            <HProtocolSelect v-model:value="form.protocolId" type="0" placeholder="请选择主控协议"></HProtocolSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="5">
          <n-form-item label="IP地址" path="ip">
            <n-input v-model:value="form.ip" placeholder="请输入IP地址"></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="4">
          <n-form-item label="端口号" path="port">
            <n-input-number v-model:value="form.port" placeholder="请输入端口号"></n-input-number>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="10">
          <n-form-item label="MAC" path="mac">
            <n-input v-model:value="form.mac" placeholder="请输入MAC地址"></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="5">
          <n-form-item label="广播地址" path="broadcastAddress">
            <n-input v-model:value="form.broadcastAddress" placeholder="请输入广播地址"></n-input>
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
    <h-dialog-form v-model:show="formConfigModel" :title="formConfigTitle" :handleSubmit="handleSaveDeviceConfig">
      <n-form-item label="中控设备配置" :show-label="false" :show-feedback="false" path="scene_video">
        <n-transfer
          ref="config_transfer"
          v-model:value="configDevice"
          virtual-scroll
          :options="deviceSourceOptions"
          :render-source-list="renderSourceList"
          :render-target-label="renderLabel"
          source-filterable
        />
      </n-form-item>
    </h-dialog-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del } from '../../api/config';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import HProtocolSelect from '../../components/form/field/ProtocolSelect.vue';
import HVenueSelect from '../../components/form/field/VenueSelect.vue';
import HCascaderDevice from '../../components/form/field/CascaderDevice.vue';
import { NIcon, NTree, TransferRenderSourceList, TransferRenderTargetLabel } from 'naive-ui';
import Icon from '@/components/basic/DynamicIcon.vue';
import { treeAll, list as deviceList, updateConfig } from '@/modules/control/api/device';
import { transformToTreeFormat } from '@/utils';

const columns = [
  {
    key: 'name',
    title: '配置名称',
    columnType: 'title',
    width: '320px',
    resizable: true,
    fixed: 'left',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
  },
  {
    key: 'venue.name',
    title: '场所位置',
    width: '220px',
    resizable: true,
  },
  { key: 'type', title: '网络类型', dictType: 'cc_network_type', width: '120px', align: 'center' },
  { key: 'ip', title: 'IP地址', width: '160px' },
  { key: 'port', title: '端口号', width: '100px', align: 'center' },
  { key: 'status', title: '状态', dictType: 'content_status', width: '80px', align: 'center' },
  {
    key: 'config',
    title: '配置',
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
        { default: () => h(Icon, { icon: 'c:CogTransfer' }) },
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
  type: '',
  ip: '',
  port: 9000,
  mac: '',
  broadcastAddress: '',
  protocolId: '',
  venueId: '',
  content: '',
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
  type: {
    required: true,
    message: '请输入类型',
  },
  ip: {
    required: true,
    message: '请输入ip地址',
  },
  port: {
    required: true,
    message: '请输入端口号',
  },
  venueId: {
    required: true,
    message: '请输入场馆id',
  },
  status: {
    required: true,
    message: '请输入状态',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  venueId: null,
  name: '',
  type: '',
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

//以下代码用于配置设备和配置的关连
const formConfigModel = ref(false);
// 配置id
const configId = ref();
// 已选设备
const configDevice = ref();
// 名称
const formConfigTitle = ref();
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
    blockLine: true,
    checkOnClick: true,
    data: deviceOptions.value,
    pattern,
    checkedKeys: configDevice.value,
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
 * 加载配置设备
 * */
function handleLoadScene(row: any) {
  formConfigModel.value = true;
  configId.value = row.id;
  deviceOptions.value = [];
  formConfigTitle.value = `配置「${row.name}」关联设备`;
  treeAll({ type: 'venue', id: row.venueId }).then((resp: any) => {
    let data = (resp.data || []).map((item: any) => {
      return {
        label: item.name,
        disabled: item.type !== 'device' || (item.extend && item.extend != row.id),
        value: item.id,
        ...item,
      };
    });

    deviceOptions.value = transformToTreeFormat(data, { pIdKey: 'parentId' });
    deviceSourceOptions.value = data;
  });

  // 根据当前场景获取已勾选的视频
  deviceList({ venueId: row.venueId, configId: row.id, pageSize: -1 }).then((res: any) => {
    configDevice.value = res.data.list?.map((item: any) => {
      return item.id;
    });
  });
}

function handleSaveDeviceConfig() {
  return updateConfig({ configId: configId.value, list: configDevice.value });
}
</script>

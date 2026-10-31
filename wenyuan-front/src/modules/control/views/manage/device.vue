<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiCopy="copy"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="所属场所" path="venueId">
        <HCascaderDevice
          type="venue"
          clearable
          v-model:value="paramsForm.venueId"
          placeholder="请选择场所"
        ></HCascaderDevice>
      </n-form-item>
      <n-form-item label="所属楼层" path="floorId">
        <HCascaderDevice
          type="floor"
          clearable
          v-model:value="paramsForm.floorId"
          placeholder="请选择楼层"
        ></HCascaderDevice>
      </n-form-item>
      <n-form-item label="所属房间" path="roomId">
        <HCascaderDevice
          type="room"
          clearable
          v-model:value="paramsForm.roomId"
          placeholder="请选择房间"
        ></HCascaderDevice>
      </n-form-item>
      <n-form-item label="主控端口" path="masterPort">
        <n-input-number
          :show-button="false"
          v-model:value="paramsForm.masterPort"
          placeholder="请输入主控端口"
        ></n-input-number>
      </n-form-item>
      <n-form-item label="中继端口" path="trunkPort">
        <n-input-number
          :show-button="false"
          v-model:value="paramsForm.trunkPort"
          placeholder="请输入中继端口"
        ></n-input-number>
      </n-form-item>
      <n-form-item label="设备名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="设备类型" path="type">
        <h-dict-select
          type="cc_device_type"
          v-model:value="paramsForm.type"
          placeholder="请输入设备类型"
          clearable
        ></h-dict-select>
      </n-form-item>
      <n-form-item label="设备状态" path="status">
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
          <n-form-item label="设备类型" path="type">
            <h-dict-radio type="cc_device_type" v-model:value="form.type" placeholder="请输入设备类型"></h-dict-radio>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="6" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <h-avatar v-model:value="form.image" size="120"></h-avatar>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="12">
          <n-form-item label="房间" path="roomId">
            <HCascaderDevice
              type="room"
              v-model:value="form.roomId"
              clearable
              placeholder="请选择房间"
            ></HCascaderDevice>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="12">
          <n-form-item label="设备规约" path="protocolId">
            <HProtocolSelect
              v-model:value="form.protocolId"
              type="1"
              clearable
              placeholder="请选择设备规约"
            ></HProtocolSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="12">
          <n-form-item label="启用日期" path="activateTime">
            <n-date-picker v-model:value="form.activateTime" placeholder="请选择启用日期"></n-date-picker>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="12">
          <n-form-item label="维保周期" path="maintenanceCycle">
            <n-input-number min="0" v-model:value="form.maintenanceCycle" placeholder="请输入维保周期">
              <template #suffix>月</template>
            </n-input-number>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="6">
          <n-form-item label="主控端口" path="masterPort">
            <n-input-number v-model:value="form.masterPort" placeholder="请输入主控端口"></n-input-number>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="6">
          <n-form-item label="中继端口" path="trunkPort">
            <n-input-number v-model:value="form.trunkPort" placeholder="请输入中继端口"></n-input-number>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="6">
          <n-form-item label="设备地址" path="addr">
            <n-input v-model:value="form.addr" placeholder="请输入设备地址"> </n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="6">
          <n-form-item label="功率" path="power">
            <n-input-number v-model:value="form.power" placeholder="请输入功率" min="0">
              <template #suffix>kWh</template>
            </n-input-number>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="描述信息" path="content">
        <n-input type="textarea" v-model:value="form.content"></n-input>
      </n-form-item>
      <n-form-item label="是否群控" path="globalControl">
        <n-switch v-model:value="form.globalControl"></n-switch>
      </n-form-item>
      <n-form-item label="状态" path="status">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>
    <HCommand
      v-if="selectDeviceId"
      :deviceId="selectDeviceId"
      :deviceType="selectDeviceType"
      v-model:show="formCommandModel"
      :title="formCommandTitle"
    ></HCommand>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { add, del, edit, copy, list } from '../../api/device';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import HProtocolSelect from '../../components/form/field/ProtocolSelect.vue';
import HCascaderDevice from '../../components/form/field/CascaderDevice.vue';
import { NIcon } from 'naive-ui';
import Icon from '@/components/basic/DynamicIcon.vue';
import HCommand from '../../components/command.vue';
import HCommandType from '../../components/commandType.vue';

const columns = [
  {
    key: 'name',
    title: '设备名称',
    fixed: 'left',
    width: '320px',
    resizable: true,
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
  },
  {
    key: 'venue.name',
    title: '场所位置',
    width: '220px',
    resizable: true,
    render(row: any) {
      return h('span', `${row.venue.name}/${row.floor.name}/${row.room.name}`);
    },
  },
  // {'key': 'floor.name', 'title': '场馆'},
  // {'key': 'room.name', 'title': '房间'},
  { key: 'type', title: '设备类型', dictType: 'cc_device_type', width: '120px', align: 'center' },
  {
    key: 'masterPort',
    title: '主控端口',
    width: '100px',
    render(row: any) {
      return h(
        'span',
        {},
        {
          default: () => {
            return `${row.masterPort || ''}-${row.trunkPort || ''}`;
          },
        },
      );
    },
  },
  { key: 'addr', title: '设备地址', width: '100px', align: 'right' },
  // {'key': 'image', 'title': '缩略图'},
  { key: 'power', title: '功率', width: '100px', align: 'right' },
  { key: 'protocol.name', title: '规约', width: '160px', ellipsis: { tooltip: true } },
  { key: 'globalControl', title: '群控', dictType: 'sys_ok', width: '100px', align: 'center' },
  { key: 'status', title: '状态', dictType: 'content_status', width: '80px', align: 'center' },
  {
    key: 'config',
    title: '配置',
    align: 'center',
    fixed: 'right',
    width: '80px',
    render(row: any) {
      return h(
        NIcon,
        {
          size: '22px',
          onClick() {
            formCommandModel.value = true;
            formCommandTitle.value = `配置「${row.name}」控制指令`;
            selectDeviceId.value = row.id;
            selectDeviceType.value = row.type;
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
  type: '',
  name: '',
  addr: '',
  masterPort: null,
  trunkPort: null,
  image: '',
  power: 0,
  content: '',
  activateTime: null,
  maintenanceCycle: 12,
  venueId: '',
  roomId: '',
  protocolId: '',
  globalControl: true,
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
  type: {
    required: true,
    message: '请输入设备类型',
  },
  name: {
    required: true,
    message: '请输入名称',
  },
  venueId: {
    required: true,
    message: '请选择场馆',
  },
  roomId: {
    required: true,
    message: '请选择房间',
  },
  power: {
    required: true,
    message: '请输入功率',
  },
  activateTime: {
    required: true,
    message: '请选择设备启用日期',
  },
  maintenanceCycle: {
    required: true,
    message: '请输入设备维保周期',
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
  type: '',
  venueId: null,
  floorId: null,
  roomId: null,
  masterPort: null,
  trunkPort: null,
  name: '',
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

/**
 * 指令表单相关
 * */
const formCommandModel = ref(false);
const formCommandTitle = ref();
const selectDeviceId = ref();
const selectDeviceType = ref();
</script>

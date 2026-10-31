<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="fetchData"
    :params="queryParams"
    :fun-api-delete="gatewayApi.delete"
    row-key-field="id"
    @add="onAdd"
    @rowEdit="onEdit"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="所属场馆">
        <VenueSelect v-model:value="queryParams.orgId" clearable placeholder="全部可见场馆" style="width: 240px" />
      </NFormItem>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="网关名 / 编码" style="width: 240px" />
      </NFormItem>
    </template>

    <HPageForm
      v-model:show="formShow"
      :title="formType === 'add' ? '注册网关' : '编辑网关'"
      :model="form"
      :rules="rules"
      :ref-table="tableRef"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="90"
      width="60%"
    >
      <NFormItem label="所属场馆" path="orgId">
        <VenueSelect :value="form.orgId" placeholder="请选择所属场馆" @update:value="handleVenueChange" />
      </NFormItem>
      <NFormItem label="名称" path="name"><NInput v-model:value="form.name" placeholder="如 一号库房网关" /></NFormItem>
      <NFormItem label="编码" path="code"><NInput v-model:value="form.code" placeholder="网关内部编号" /></NFormItem>
      <NFormItem label="接入方式" path="protocol">
        <NSelect v-model:value="form.protocol" :options="protocolOptions" placeholder="接入方式" />
      </NFormItem>
      <NFormItem label="厂商" path="vendor"><NInput v-model:value="form.vendor" placeholder="厂商" /></NFormItem>
      <NFormItem label="备注" path="remarks"
        ><NInput v-model:value="form.remarks" type="textarea" :rows="2"
      /></NFormItem>
    </HPageForm>

    <HPageView v-model:show="viewShow" title="网关详情" width="60%" :show-action="false">
      <NAlert v-if="viewRow?.protocol === 'MQTT'" type="info" :bordered="false" style="margin-bottom: 12px">
        MQTT 客户端 Username 使用网关编码，Password 使用创建或重置时返回的一次性凭据。完整 MQTTX
        参数可在“采集配置”中查看。
      </NAlert>
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="所属场馆"><VenueText :value="viewRow.orgId" /></NDescriptionsItem>
        <NDescriptionsItem label="名称">{{ viewRow.name || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="编码">{{ viewRow.code || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="接入方式">{{ protocolLabel(viewRow.protocol) }}</NDescriptionsItem>
        <NDescriptionsItem v-if="viewRow.kind !== 'BUSINESS_SOURCE'" label="网关凭据">
          <NSpace>
            <span>{{ viewRow.tokenHint ? `已配置（末尾 ${viewRow.tokenHint}）` : '未配置' }}</span>
            <NButton size="tiny" @click="regen(viewRow!)">重置</NButton>
          </NSpace>
        </NDescriptionsItem>
        <NDescriptionsItem label="上次上报"
          ><span style="white-space: nowrap">{{ formatDateTime(viewRow.lastReportTime) }}</span></NDescriptionsItem
        >
        <NDescriptionsItem label="厂商">{{ viewRow.vendor || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="备注">{{ viewRow.remarks || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { h, reactive, ref } from 'vue';
import {
  NAlert,
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NFormItem,
  NInput,
  NSelect,
  NSpace,
  useDialog,
  useMessage,
} from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import { formatDateTime } from '@/utils';
import { gatewayApi } from '../api/gateway';
import VenueSelect from '../components/VenueSelect.vue';
import VenueText from '../components/VenueText.vue';
import type { GatewayVo } from '../typings';

const message = useMessage();
const dialog = useDialog();
const tableRef = ref<any>(null);
const queryParams = reactive({ orgId: null as string | null, keyword: null as string | null });
const formShow = ref(false);
const formType = ref<'add' | 'edit'>('add');
const defaultForm: GatewayVo = { protocol: 'HTTP' };
const protocolOptions = [
  { label: 'HTTP', value: 'HTTP' },
  { label: 'MQTT', value: 'MQTT' },
  { label: 'TCP', value: 'TCP' },
  { label: 'UDP', value: 'UDP' },
  { label: '串口', value: 'SERIAL' },
  { label: 'CAN', value: 'CAN' },
];
const form = ref<GatewayVo>(cloneDeep(defaultForm));
const viewShow = ref(false);
const viewRow = ref<GatewayVo | null>(null);

const rules = {
  orgId: { required: true, message: '请选择所属场馆', trigger: 'change' },
  name: { required: true, message: '请输入名称', trigger: ['blur', 'input'] },
};

async function fetchData(params: any) {
  return await gatewayApi.page({ ...params, orgId: queryParams.orgId, keyword: queryParams.keyword });
}

const columns = [
  {
    title: '所属场馆',
    key: 'orgId',
    width: 180,
    render: (row: GatewayVo) => h(VenueText, { value: row.orgId }),
  },
  { title: '名称', key: 'name', minWidth: 160, columnType: 'title', ellipsis: { tooltip: true } },
  { title: '编码', key: 'code', width: 160, ellipsis: { tooltip: true } },
  { title: '接入方式', key: 'protocol', width: 110, render: (row: GatewayVo) => protocolLabel(row.protocol) },
  { title: '厂商', key: 'vendor', width: 160, ellipsis: { tooltip: true } },
  {
    title: '上次上报',
    key: 'lastReportTime',
    width: 190,
    columnType: 'dateTime',
    ellipsis: { tooltip: true },
  },
];

function onAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  formShow.value = true;
}
function onEdit(row: GatewayVo) {
  formType.value = 'edit';
  form.value = { ...cloneDeep(defaultForm), ...cloneDeep(row) };
  formShow.value = true;
}
function onView(row: GatewayVo) {
  viewRow.value = row;
  viewShow.value = true;
}

function handleVenueChange(orgId: string | null) {
  if (!orgId) {
    if (formType.value === 'add') form.value.orgId = undefined;
    return;
  }
  if (orgId === form.value.orgId) return;
  if (formType.value === 'add' || !form.value.orgId) {
    form.value.orgId = orgId;
    return;
  }
  dialog.warning({
    title: '确认变更所属场馆',
    content:
      '将同步迁移采集路由、设备、指标绑定、待注册设备及待处理数据；设备当前安装位置会被清空，历史采集事实仍属于原场馆，实时数据将在新上报后重建。',
    positiveText: '确认变更',
    negativeText: '取消',
    onPositiveClick: () => {
      form.value.orgId = orgId;
    },
  });
}

async function handleSubmit() {
  if (form.value.id) return await gatewayApi.update(form.value.id, { ...form.value });
  const response: any = await gatewayApi.create({ ...form.value });
  const token = response?.data?.token;
  if (token) showOneTimeToken(token, form.value.protocol);
  return response;
}

function protocolLabel(value?: string) {
  return protocolOptions.find((item) => item.value === value)?.label || value || '--';
}

function regen(row: GatewayVo) {
  if (!row.id) return;
  dialog.warning({
    title: '重置网关 Token',
    content: `确认重置“${row.name || row.code}”的 token？MQTT 客户端使用的旧 Password 将立即失效。`,
    positiveText: '确认重置',
    negativeText: '取消',
    onPositiveClick: async () => {
      const resp: any = await gatewayApi.regenerateToken(row.id!);
      const token = resp?.data?.token;
      if (token) {
        row.tokenHint = `••••${token.slice(-4)}`;
        showOneTimeToken(token, row.protocol);
      }
      message.success('Token 已重置');
    },
  });
}

function showOneTimeToken(token: string, protocol?: string) {
  dialog.success({
    title: protocol === 'MQTT' ? '请保存 MQTT Password' : '请保存网关 Token',
    content: () =>
      h('div', { style: 'display:flex;flex-direction:column;gap:12px' }, [
        h('div', '该凭据只显示一次，关闭后无法再次查看，只能重置。'),
        h(NSpace, { wrap: false }, () => [
          h(NInput, { value: token, readonly: true, style: 'width:320px' }),
          h(NButton, { onClick: () => copy(token) }, () => '复制'),
        ]),
      ]),
    positiveText: '我已保存',
    maskClosable: false,
  });
}

async function copy(text?: string) {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    message.success('已复制');
  } catch {
    message.error('浏览器未授权访问剪贴板');
  }
}
</script>

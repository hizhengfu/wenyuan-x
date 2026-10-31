<template>
  <n-space vertical size="large">
    <n-card title="采集设备" size="small">
      <n-p depth="3"
        >设备通过 ISUP 5.0 主动注册，按注册 ID
        识别。保存后等待设备接入，移除后注销连接；已有数据和图像继续处理与留存。</n-p
      >
      <template #header-extra>
        <n-space>
          <n-button :loading="loading" @click="emit('changed')">刷新</n-button>
          <n-button v-if="canCreate" type="primary" @click="edit()">新增设备</n-button>
        </n-space>
      </template>
      <n-alert
        v-if="listing.devices.some((device) => !device.registrationId || !device.hasRegistrationKey)"
        type="warning"
        style="margin-bottom: 12px"
      >
        部分历史设备尚未配置主动注册，请编辑并填写设备注册 ID 和 EHomeKey。已有监测数据和归档图像继续保留。
      </n-alert>
      <n-data-table
        :columns="columns"
        :data="listing.devices"
        :loading="loading"
        :row-key="(row: IsapiDevice) => row.id!"
        :scroll-x="1180"
        :pagination="{ pageSize: 15 }"
      />
    </n-card>
    <n-modal
      v-model:show="showForm"
      preset="card"
      :title="form.id ? '编辑采集设备' : '新增采集设备'"
      style="width: 880px; max-width: calc(100vw - 32px)"
    >
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
        <n-grid :cols="2" :x-gap="20">
          <n-form-item-gi label="设备名称" path="name"><n-input v-model:value="form.name" /></n-form-item-gi>
          <n-form-item-gi label="设备注册 ID" path="registrationId">
            <n-input v-model:value="form.registrationId" placeholder="与摄像机平台接入中的设备 ID 一致" />
          </n-form-item-gi>
          <n-form-item-gi label="注册密钥（EHomeKey）" path="registrationKey">
            <n-input
              v-model:value="form.registrationKey"
              type="password"
              show-password-on="click"
              autocomplete="new-password"
              :placeholder="form.hasRegistrationKey ? '留空保留原密钥' : '8至32位可见ASCII字符'"
            />
          </n-form-item-gi>
          <n-form-item-gi label="监测通道号"
            ><n-input-number v-model:value="form.channelId" :min="1" :max="64"
          /></n-form-item-gi>
          <n-form-item-gi label="通道抓图码流号"
            ><n-input-number v-model:value="form.snapshotStreamId" :min="1" :max="65535"
          /></n-form-item-gi>
          <n-form-item-gi label="定时抓图间隔（秒，0为关闭）"
            ><n-input-number v-model:value="form.snapshotIntervalSeconds" :min="0" :max="86400"
          /></n-form-item-gi>
          <n-form-item-gi label="设备时间所属时区"
            ><n-input v-model:value="form.timeZone" placeholder="Asia/Shanghai"
          /></n-form-item-gi>
          <n-form-item-gi label="标靶丢失预警级别">
            <n-select v-model:value="form.targetLossAlarmLevel" :options="alarmLevels" />
          </n-form-item-gi>
        </n-grid>
        <n-form-item label="图像监测指标ID（可选）" path="cameraLineId">
          <n-input
            v-model:value="form.cameraLineId"
            :disabled="!listing.cameraBindingAvailable"
            placeholder="已有图像监测指标ID"
            clearable
          />
          <template #feedback>
            {{
              listing.cameraBindingAvailable
                ? '关联图像监测指标后，图像会进入监测管理的相机日期浏览和对比查看。请先维护该指标的相机编号。'
                : '当前平台未提供监测相机归档适配，采集图像仍可在数据页查看。'
            }}
          </template>
        </n-form-item>
        <n-divider title-placement="left">标靶与监测指标关联</n-divider>
        <n-p depth="3"
          >关联后，位移数据和标靶丢失预警进入对应监测指标。位移阈值在监测管理中维护；未关联的标靶仅在本模块留存采集记录。</n-p
        >
        <n-space vertical>
          <n-grid
            v-for="(binding, index) in form.bindings"
            :key="index"
            :cols="listing.bindingAvailable ? 9 : 13"
            :x-gap="10"
          >
            <n-grid-item :span="2"
              ><n-input-number v-model:value="binding.targetId" :min="1" :max="20" placeholder="标靶ID"
            /></n-grid-item>
            <n-grid-item :span="6"><n-input v-model:value="binding.lineId" placeholder="已有监测指标ID" /></n-grid-item>
            <n-grid-item v-if="!listing.bindingAvailable" :span="4"
              ><n-input v-model:value="binding.stationId" placeholder="监测点ID"
            /></n-grid-item>
            <n-grid-item
              ><n-button type="error" quaternary @click="form.bindings.splice(index, 1)">移除</n-button></n-grid-item
            >
          </n-grid>
          <n-button :disabled="form.bindings.length >= 20" dashed @click="addBinding">添加标靶关联</n-button>
        </n-space>
      </n-form>
      <template #footer
        ><n-space justify="end">
          <n-button @click="showForm = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="save">保存</n-button>
        </n-space></template
      >
    </n-modal>
    <n-modal
      v-model:show="showTest"
      preset="card"
      title="设备能力与已有标靶参数"
      style="width: 800px; max-width: calc(100vw - 32px)"
    >
      <pre class="capabilities">{{ testResult }}</pre>
    </n-modal>
  </n-space>
</template>

<script lang="ts" setup>
import { computed, h, ref } from 'vue';
import { NButton, NSpace, NPopconfirm, useMessage } from 'naive-ui';
import type { DataTableColumns, FormInst } from 'naive-ui';
import { useAuthStore } from '@/store';
import { deleteDevice, saveDevice, testDevice } from '../../api/isapi';
import type { DeviceList, IsapiDevice } from '../../api/isapi';

const auth = useAuthStore();
const props = defineProps<{ listing: DeviceList; loading: boolean }>();
const emit = defineEmits<{ changed: []; select: [id: string, tab: 'data' | 'status'] }>();
const listing = computed(() => props.listing);
const loading = computed(() => props.loading);
const message = useMessage();
const saving = ref(false);
const busy = ref('');
const showForm = ref(false);
const showTest = ref(false);
const testResult = ref('');
const formRef = ref<FormInst | null>(null);
const canCreate = computed(() => auth.hasPermission('VIDEO_ISAPI_ALL,VIDEO_ISAPI_CREATE'));
const canEdit = computed(() => auth.hasPermission('VIDEO_ISAPI_ALL,VIDEO_ISAPI_EDIT'));
const canDelete = computed(() => auth.hasPermission('VIDEO_ISAPI_ALL,VIDEO_ISAPI_DELETE'));
const emptyForm = (): IsapiDevice => ({
  name: '',
  registrationId: '',
  registrationKey: '',
  channelId: 1,
  snapshotStreamId: 101,
  timeZone: 'Asia/Shanghai',
  snapshotIntervalSeconds: 60,
  bindings: [],
  cameraLineId: '',
  targetLossAlarmLevel: 'BLUE',
});
const alarmLevels = [
  { label: '一级（红色）', value: 'RED' },
  { label: '二级（橙色）', value: 'ORANGE' },
  { label: '三级（黄色）', value: 'YELLOW' },
  { label: '四级（蓝色）', value: 'BLUE' },
];
const form = ref<IsapiDevice>(emptyForm());
const rules = {
  registrationId: {
    validator: (_rule: unknown, value: string) =>
      /^[A-Za-z0-9_.-]{1,100}$/.test(value || '') ||
      new Error('请输入1至100位注册ID，仅限字母、数字、点、下划线、短横线'),
  },
  registrationKey: {
    validator: (_rule: unknown, value: string) =>
      (!value && form.value.hasRegistrationKey) ||
      /^[!-~]{8,32}$/.test(value || '') ||
      new Error('请输入8至32位可见ASCII字符密钥'),
  },
  name: { required: true, message: '请输入设备名称' },
};

const columns = computed<DataTableColumns<IsapiDevice>>(() => [
  { title: '设备名称', key: 'name', width: 170, fixed: 'left' },
  {
    title: '设备注册 ID',
    key: 'registrationId',
    width: 230,
    ellipsis: { tooltip: true },
    render: (row) => row.registrationId || '待配置',
  },
  { title: '监测通道', key: 'channelId', width: 90 },
  { title: '关联标靶', key: 'bindings', width: 90, render: (row) => `${row.bindings.length} 个` },
  {
    title: '操作',
    key: 'actions',
    width: 390,
    fixed: 'right',
    render(row) {
      const button = (label: string, action: () => unknown, disabled = false) =>
        h(NButton, { size: 'small', disabled: disabled || busy.value === row.id, onClick: action }, () => label);
      return h(NSpace, { size: 6 }, () => [
        button('数据', () => emit('select', row.id!, 'data')),
        button('状态', () => emit('select', row.id!, 'status')),
        button('注册通道测试', () =>
          operate(row, async () => {
            testResult.value = JSON.stringify(await testDevice(row.id!), null, 2);
            showTest.value = true;
          }),
        ),
        ...(canEdit.value ? [button('编辑', () => edit(row))] : []),
        ...(canDelete.value
          ? [
              h(
                NPopconfirm,
                {
                  onPositiveClick: () =>
                    operate(row, async () => {
                      await deleteDevice(row.id!);
                      emit('changed');
                      message.success('设备已移除，采集连接将停止');
                    }),
                },
                {
                  trigger: () => button('移除', () => undefined),
                  default: () => '移除设备将停止采集，已有监测数据和归档文件继续保留。',
                },
              ),
            ]
          : []),
      ]);
    },
  },
]);

function edit(row?: IsapiDevice) {
  form.value = row
    ? {
        ...row,
        targetLossAlarmLevel: row.targetLossAlarmLevel || 'BLUE',
        registrationId: row.registrationId || '',
        registrationKey: '',
        cameraLineId: row.cameraLineId || '',
        bindings: row.bindings.map((b) => ({ ...b })),
      }
    : emptyForm();
  showForm.value = true;
}
function addBinding() {
  const used = new Set(form.value.bindings.map((b) => b.targetId));
  const targetId = Array.from({ length: 20 }, (_, index) => index + 1).find((id) => !used.has(id));
  if (targetId) form.value.bindings.push({ targetId, lineId: '', stationId: '' });
}
async function save() {
  if (
    ![
      form.value.channelId,
      form.value.snapshotStreamId,
      form.value.snapshotIntervalSeconds,
      ...form.value.bindings.map((binding) => binding.targetId),
    ].every(Number.isInteger)
  ) {
    message.error('请完整填写通道、抓图间隔和标靶ID');
    return;
  }
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    await saveDevice(form.value);
    message.success('设备已保存，将自动采集');
    showForm.value = false;
    emit('changed');
  } catch {
    /* 通用请求层展示保存错误。 */
  } finally {
    saving.value = false;
  }
}
async function operate(row: IsapiDevice, operation: () => Promise<void>) {
  busy.value = row.id!;
  try {
    await operation();
  } catch {
    /* 通用请求层展示设备错误。 */
  } finally {
    busy.value = '';
  }
}
</script>

<style scoped>
.capabilities {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 65vh;
  overflow: auto;
  margin: 0;
}
</style>

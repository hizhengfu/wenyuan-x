<template>
  <div class="sms-panel">
    <NAlert type="info" :bordered="false" class="sms-tip">
      严重或紧急预警首次触发、等级升级时发送短信。同级重复命中不发送；已恢复、过期或接收人变更的待发任务会取消。
    </NAlert>
    <NTabs type="line" class="sms-tabs" pane-wrapper-style="display:flex;flex:1;min-height:0;overflow:hidden"
      pane-style="display:flex;flex:1;min-height:0;flex-direction:column;overflow:hidden">
      <NTabPane name="policies" tab="通知策略" display-directive="show">
        <HPageDataTable ref="policyTable" title="短信通知策略" :columns="policyColumns" :fetch-data="alarmSmsApi.policies"
          :params="policyQuery" :scroll-x="1000" row-key-field="id" :is-add="true" :is-edit="false" :is-copy="false"
          :is-delete="false" :is-export="false" :show-operate="false" @add="edit()">
          <template #queryForm><NFormItem label="场馆"><VenueSelect v-model:value="policyQuery.orgId" clearable style="width:240px" /></NFormItem></template>
        </HPageDataTable>
      </NTabPane>
      <NTabPane name="tasks" tab="投递记录" display-directive="show:lazy">
        <NAlert type="warning" :bordered="false" class="sms-tip">“已受理”表示短信平台接收了请求；“待核实”需到短信平台核查，系统不会自动重发。</NAlert>
        <HPageDataTable title="短信投递记录" :columns="taskColumns" :fetch-data="alarmSmsApi.tasks" :params="taskQuery"
          :scroll-x="1850" row-key-field="id" :is-add="false" :is-edit="false" :is-copy="false" :is-delete="false" :is-export="false" :show-operate="false">
          <template #queryForm>
            <NFormItem label="场馆"><VenueSelect v-model:value="taskQuery.orgId" clearable style="width:220px" /></NFormItem>
            <NFormItem label="状态"><NSelect v-model:value="taskQuery.status" :options="statusOptions" clearable style="width:160px" /></NFormItem>
          </template>
        </HPageDataTable>
      </NTabPane>
    </NTabs>
    <NModal v-model:show="show" preset="card" :title="form.id ? '编辑短信通知策略' : '新增短信通知策略'"
      style="width:min(900px, calc(100vw - 32px))">
      <NAlert type="info" :bordered="false" class="sms-tip">保存后只处理新产生的预警，原策略的待发任务会取消。接收用户与角色均限于所选场馆；同一手机号只投递一次。</NAlert>
      <NForm label-placement="left" label-width="130">
        <NGrid :cols="2" :x-gap="16">
          <NFormItemGi label="所属场馆" required :span="2"><VenueSelect :value="form.orgId || null" :disabled="!!form.id" @update:value="changeVenue" /></NFormItemGi>
          <NFormItemGi label="策略名称" required><NInput v-model:value="form.name" :maxlength="100" /></NFormItemGi>
          <NFormItemGi label="启用策略"><NSwitch v-model:value="form.enabled" /></NFormItemGi>
          <NFormItemGi label="最低预警等级"><NSelect v-model:value="form.minSeverity" :options="severityOptions" /></NFormItemGi>
          <NFormItemGi label="短信模板" :required="form.enabled"><NSelect v-model:value="form.templateCode" :options="templateOptions" filterable
            :loading="loading" :disabled="!form.orgId" placeholder="选择通知中心已配置的短信模板" /></NFormItemGi>
          <NFormItemGi label="设备范围"><NSelect v-model:value="form.deviceId" :options="options.devices" filterable clearable
            :loading="loading" :disabled="!form.orgId" placeholder="本场馆全部设备" /></NFormItemGi>
          <NFormItemGi label="位置范围"><NSelect v-model:value="form.locationId" :options="options.locations" filterable clearable
            :loading="loading" :disabled="!form.orgId" placeholder="本场馆全部位置；选中时仅匹配该位置" /></NFormItemGi>
          <NFormItemGi label="预警规则" :span="2"><NSelect v-model:value="form.ruleId" :options="options.rules" filterable clearable
            :loading="loading" :disabled="!form.orgId" placeholder="全部预警规则，含设备主动告警" /></NFormItemGi>
          <NFormItemGi label="接收用户" :span="2"><NSelect v-model:value="form.userIds" :options="options.users" multiple filterable clearable
            :loading="loading" :disabled="!form.orgId" placeholder="选择接收用户" /></NFormItemGi>
          <NFormItemGi label="接收角色" :span="2"><NSelect v-model:value="form.roleIds" :options="options.roles" multiple filterable clearable
            :loading="loading" :disabled="!form.orgId" placeholder="角色中的有效场馆用户，与指定用户合并" /></NFormItemGi>
          <NFormItemGi label="重复抑制（秒）"><NInputNumber v-model:value="form.cooldownSeconds" :min="0" :max="86400" /></NFormItemGi>
          <NFormItemGi label="短信有效期（秒）"><NInputNumber v-model:value="form.expirySeconds" :min="60" :max="3600" /></NFormItemGi>
          <NFormItemGi label="最多尝试次数"><NInputNumber v-model:value="form.maxAttempts" :min="1" :max="5" /></NFormItemGi>
          <NFormItemGi label="绕过个人订阅"><NSwitch v-model:value="form.bypassSubscription" :disabled="!isAdmin" /></NFormItemGi>
        </NGrid>
      </NForm>
      <NAlert v-if="selectedTemplateError" type="warning" :bordered="false" class="sms-tip">{{ selectedTemplateError }}。请在通知中心修正模板后重新打开策略，或选择其他模板。</NAlert>
      <NAlert type="info" :bordered="false" class="sms-tip">模板参数 name 为接收人姓名，location 为位置，event 为预警标题。移动云信需使用云信审核通过的模板编号，并将变量映射为 p1 至 p30。</NAlert>
      <NAlert v-if="form.bypassSubscription" type="warning" :bordered="false" class="sms-tip">紧急短信将忽略个人退订设置，仍遵守短信总开关、演练模式、有效期和发送频率限制。</NAlert>
      <NAlert v-if="form.orgId && !loading && !options.available" type="warning" :bordered="false">当前平台未接入短信模块，不能启用策略。</NAlert>
      <NText depth="3">重复抑制针对同设备同预警的相同或较低等级；升级不受此限制。限流时任务保留等待，超过有效期停止投递。</NText>
      <template #footer><NSpace justify="end"><NButton @click="show = false">取消</NButton><NButton type="primary" :loading="saving" :disabled="loading" @click="save">保存策略</NButton></NSpace></template>
    </NModal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref } from 'vue';
import { NAlert, NButton, NForm, NFormItem, NFormItemGi, NGrid, NInput, NInputNumber, NModal, NSelect, NSpace,
  NSwitch, NTabPane, NTabs, NTag, NText, useMessage, type DataTableColumns, type SelectOption } from 'naive-ui';
import { useAuthStore } from '@/store';
import { formatDateTime } from '@/utils';
import { alarmSmsApi, type AlarmSmsPolicy, type AlarmSmsTask } from '../api/alarmSms';
import VenueSelect from './VenueSelect.vue';
import VenueText from './VenueText.vue';

const message = useMessage();
const auth = useAuthStore();
const isAdmin = computed(() => auth.hasPermission('ADMIN,MUSEUM_DEVICE_ADMIN'));
const policyTable = ref<any>();
const policyQuery = reactive({ orgId: null as string | null });
const taskQuery = reactive({ orgId: null as string | null, status: null as string | null });
const severityOptions = [{ label: '严重及紧急', value: 'MAJOR' }, { label: '仅紧急', value: 'CRITICAL' }];
const statuses: Record<string, string> = { PENDING: '等待发送', SENDING: '发送中', ACCEPTED: '已受理', REJECTED: '失败',
  UNKNOWN: '待核实', EXPIRED: '已过期', CANCELED: '已取消', SUPPRESSED: '已抑制', DRY_RUN: '演练完成' };
const statusOptions = Object.entries(statuses).map(([value, label]) => ({ value, label }));
const show = ref(false), loading = ref(false), saving = ref(false);
function defaults(): AlarmSmsPolicy { return { orgId: '', name: '', enabled: false, minSeverity: 'MAJOR', userIds: [], roleIds: [],
  templateCode: '', bypassSubscription: false, cooldownSeconds: 300, expirySeconds: 600, maxAttempts: 3 }; }
const form = ref<AlarmSmsPolicy>(defaults());
interface TemplateOption extends SelectOption { value: string; label: string; disabledReason?: string | null }
interface Options { available: boolean; users: SelectOption[]; roles: SelectOption[]; templates: TemplateOption[];
  devices: SelectOption[]; locations: SelectOption[]; rules: SelectOption[] }
function emptyOptions(): Options { return { available: false, users: [], roles: [], templates: [], devices: [], locations: [], rules: [] }; }
const options = ref<Options>(emptyOptions());
const templateOptions = computed(() => options.value.templates.map(template => ({ ...template,
  label: template.disabledReason ? `${template.label}（不可用：${template.disabledReason}）` : template.label,
})));
const selectedTemplateError = computed(() => {
  if (!form.value.orgId || loading.value || !options.value.available || !form.value.templateCode) return '';
  const template = options.value.templates.find(item => item.value === form.value.templateCode);
  if (!template) return '所选短信模板不存在、已停用或未配置短信内容';
  return template.disabled ? template.disabledReason || '所选短信模板不适用于当前预警通知' : '';
});
let optionsRequest = 0;
async function loadOptions(orgId: string) {
  const current = ++optionsRequest; loading.value = true; options.value = emptyOptions();
  try { const response: any = await alarmSmsApi.options(orgId);
    if (current === optionsRequest && orgId === form.value.orgId) options.value = { ...emptyOptions(), ...response.data };
  } catch { if (current === optionsRequest) message.error('通知选项加载失败，请重试'); }
  finally { if (current === optionsRequest) loading.value = false; }
}
function edit(row?: AlarmSmsPolicy) {
  form.value = row ? { ...defaults(), ...row, userIds: [...(row.userIds || [])], roleIds: [...(row.roleIds || [])] } : defaults();
  options.value = emptyOptions(); show.value = true;
  ++optionsRequest; loading.value = false;
  if (form.value.orgId) void loadOptions(form.value.orgId);
}
function changeVenue(orgId: string | null) {
  form.value = { ...form.value, orgId: orgId || '', deviceId: null, locationId: null, ruleId: null, userIds: [], roleIds: [] };
  ++optionsRequest; options.value = emptyOptions(); loading.value = false;
  if (orgId) void loadOptions(orgId);
}
async function save() {
  if (!form.value.orgId || !form.value.name.trim()) return message.warning('请填写所属场馆和策略名称');
  if (form.value.enabled && (!form.value.templateCode || (!form.value.userIds.length && !form.value.roleIds.length)))
    return message.warning('启用前请选择短信模板和接收用户或角色');
  if (form.value.enabled && !options.value.available) return message.warning('当前平台未接入短信模块');
  if (form.value.enabled && selectedTemplateError.value) return message.warning(selectedTemplateError.value);
  saving.value = true;
  try { await alarmSmsApi.save(form.value); show.value = false; message.success('策略已保存，对新产生的预警生效'); policyTable.value?.handleRefresh?.(); }
  finally { saving.value = false; }
}
const policyColumns: DataTableColumns<AlarmSmsPolicy> = [
  { title: '策略名称', key: 'name', width: 200 },
  { title: '场馆', key: 'orgId', width: 180, render: row => h(VenueText, { value: row.orgId }) },
  { title: '状态', key: 'enabled', width: 90, render: row => h(NTag, { type: row.enabled ? 'success' : 'default', bordered: false }, { default: () => row.enabled ? '启用' : '停用' }) },
  { title: '通知等级', key: 'minSeverity', width: 120, render: row => row.minSeverity === 'CRITICAL' ? '仅紧急' : '严重及紧急' },
  { title: '接收范围', key: 'userIds', width: 180, render: row => `${row.userIds?.length || 0} 个用户 / ${row.roleIds?.length || 0} 个角色` },
  { title: '有效期', key: 'expirySeconds', width: 100, render: row => `${row.expirySeconds} 秒` },
  { title: '操作', key: 'actions', width: 100, fixed: 'right', render: row => h(NButton, { size: 'small', onClick: () => edit(row) }, { default: () => '编辑' }) },
];
const taskColumns: DataTableColumns<AlarmSmsTask> = [
  { title: '预警', key: 'title', width: 240, ellipsis: { tooltip: true } },
  { title: '场馆', key: 'orgId', width: 170, render: row => h(VenueText, { value: row.orgId }) },
  { title: '等级', key: 'severity', width: 80, render: row => row.severity === 'CRITICAL' ? '紧急' : '严重' },
  { title: '接收人', key: 'userName', width: 120 }, { title: '手机号', key: 'phone', width: 150 },
  { title: '状态', key: 'status', width: 110, render: row => h(NTag, { bordered: false,
    type: row.status === 'ACCEPTED' ? 'success' : ['UNKNOWN', 'REJECTED'].includes(row.status) ? 'warning' : 'default' }, { default: () => statuses[row.status] || row.status }) },
  { title: '尝试次数', key: 'attempts', width: 90 },
  { title: '说明', key: 'message', width: 300, ellipsis: { tooltip: true } },
  { title: '返回码', key: 'code', width: 150, ellipsis: { tooltip: true } },
  { title: '平台受理编号', key: 'requestId', width: 180, ellipsis: { tooltip: true } },
  { title: '创建时间', key: 'createdAt', width: 170, render: row => formatDateTime(row.createdAt) },
  { title: '最后更新', key: 'updatedAt', width: 170, render: row => formatDateTime(row.updatedAt) },
];
</script>

<style scoped>
.sms-panel, .sms-tabs { display:flex; flex:1; width:100%; height:100%; min-height:0; flex-direction:column; overflow:hidden; }
.sms-tip { margin-bottom:12px; flex:none; }
</style>

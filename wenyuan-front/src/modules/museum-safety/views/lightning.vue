<template>
  <SafetyPageGuard>
    <div class="lightning-page">
      <div class="page-heading">
        <div>
          <h2>防雷系统</h2>
          <p>按整馆、馆区或建筑查看防雷整体状态；设备安装位置仅作为监测点证据。</p>
        </div>
        <div class="heading-actions">
          <VenueSelect v-model:value="orgId" placeholder="全部场馆" @update:value="handleVenueChange" />
          <NInput v-model:value="keyword" clearable placeholder="系统名称 / 编码 / 范围" />
          <NSelect v-model:value="stateFilter" :options="stateOptions" clearable placeholder="全部状态" />
          <NButton :loading="loading" @click="loadOverview">刷新</NButton>
          <NButton v-if="canManage" type="primary" @click="openCreate">新增防雷系统</NButton>
        </div>
      </div>

      <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>

      <NSpin :show="loading">
        <NGrid :cols="'1 s:2 m:4 l:7'" responsive="screen" :x-gap="12" :y-gap="12" class="summary-grid">
          <NGridItem v-for="card in summaryCards" :key="card.label">
            <NCard size="small" :class="`summary-card summary-card--${card.tone}`">
              <NStatistic :label="card.label" :value="card.value" />
              <div class="summary-card__hint">{{ card.hint }}</div>
            </NCard>
          </NGridItem>
        </NGrid>

        <div class="list-toolbar">
          <div>
            <strong>防雷系统</strong>
            <span>共 {{ filteredSystems.length }} 套；整体状态由系统、分项、设备及完整性校验共同汇总</span>
          </div>
        </div>

        <NEmpty
          v-if="!filteredSystems.length && !loading"
          :description="orgId ? '当前场馆尚未配置防雷系统' : '当前可见范围尚未配置防雷系统'"
          class="page-empty"
        >
          <template v-if="canManage" #extra
            ><NButton type="primary" @click="openCreate">新增防雷系统</NButton></template
          >
        </NEmpty>

        <NGrid v-else :cols="'1 xl:2'" responsive="screen" :x-gap="14" :y-gap="14">
          <NGridItem v-for="system in pagedSystems" :key="system.id">
            <NCard class="system-card" :bordered="false">
              <template #header>
                <div class="system-title">
                  <div>
                    <strong>{{ system.name }}</strong>
                    <span>{{ system.code }}</span>
                  </div>
                  <NTag :type="stateTagType(system.state)" :bordered="false">{{ stateLabel(system.state) }}</NTag>
                </div>
              </template>
              <template #header-extra>
                <NSpace v-if="canManage || canReport" :size="8">
                  <NButton v-if="canReport" text type="primary" @click="openReport(system)">生成报告</NButton>
                  <NButton v-if="canManage" text type="primary" @click="openEdit(system)">管理</NButton>
                  <NButton v-if="canManage" text type="error" @click="removeSystem(system)">停用</NButton>
                </NSpace>
              </template>

              <div class="system-scope">
                <div>
                  <span>覆盖范围</span>
                  <strong>{{ scopeLabel(system) }}</strong>
                </div>
                <div>
                  <span>最近数据</span>
                  <strong>{{ system.latestDataAt ? formatDateTime(system.latestDataAt) : '--' }}</strong>
                </div>
              </div>
              <p v-if="system.description" class="system-description">{{ system.description }}</p>

              <NGrid :cols="4" responsive="screen" :x-gap="8" :y-gap="8" class="system-stat-grid">
                <NGridItem
                  ><div class="system-stat">
                    <strong>{{ system.onlineDevices }} / {{ system.configuredDevices }}</strong
                    ><span>在线 / 已配置</span>
                  </div></NGridItem
                >
                <NGridItem
                  ><div class="system-stat">
                    <strong>{{ system.abnormalDevices }}</strong
                    ><span>异常设备</span>
                  </div></NGridItem
                >
                <NGridItem
                  ><button
                    class="system-stat system-stat--action"
                    type="button"
                    title="查看该系统风险"
                    @click="openRisks(system)"
                  >
                    <strong>{{ system.activeRisks }}</strong
                    ><span>活动风险</span>
                  </button></NGridItem
                >
                <NGridItem
                  ><div class="system-stat">
                    <strong>{{ system.activeAlarms }}</strong
                    ><span>活动告警</span>
                  </div></NGridItem
                >
              </NGrid>

              <NAlert v-if="system.integrityWarningCount" type="warning" :bordered="false" class="integrity-alert">
                发现 {{ system.integrityWarningCount }} 项配置或安装完整性问题，请展开分项查看证据。
              </NAlert>

              <NCollapse class="component-collapse" display-directive="show">
                <NCollapseItem
                  v-for="component in system.components || []"
                  :key="component.componentType"
                  :name="component.componentType"
                >
                  <template #header>
                    <div class="component-heading">
                      <strong>{{ component.label || componentTypeLabel(component.componentType) }}</strong>
                      <NTag size="small" :type="stateTagType(component.state)" :bordered="false">{{
                        stateLabel(component.state)
                      }}</NTag>
                    </div>
                  </template>
                  <template #header-extra>
                    <span class="component-summary">
                      {{ component.onlineDevices }}/{{ component.configuredDevices }} 在线
                      <template v-if="component.activeRisks"> · {{ component.activeRisks }} 项风险</template>
                      <template v-if="component.integrityWarningCount">
                        · {{ component.integrityWarningCount }} 项完整性问题</template
                      >
                    </span>
                  </template>
                  <NDataTable
                    v-if="component.devices?.length"
                    :columns="deviceColumns"
                    :data="component.devices"
                    :row-key="(row) => row.bindingId"
                    :scroll-x="1180"
                    :max-height="360"
                    virtual-scroll
                    size="small"
                  />
                  <NEmpty v-else description="该分项尚未绑定监测设备" class="component-empty" />
                </NCollapseItem>
              </NCollapse>
              <NEmpty v-if="!system.components?.length" description="尚未配置防雷分项与设备" class="component-empty" />
            </NCard>
          </NGridItem>
        </NGrid>

        <NPagination
          v-if="filteredSystems.length > pageSize"
          v-model:page="page"
          v-model:page-size="pageSize"
          :item-count="filteredSystems.length"
          :page-sizes="[12, 24, 48]"
          show-size-picker
          class="pagination"
        />
      </NSpin>

      <HPageForm
        v-model:show="editorVisible"
        :title="systemForm.id ? '管理防雷系统' : '新增防雷系统'"
        :model="systemForm"
        :rules="systemRules"
        :auto-close="false"
        :handle-submit="saveSystemInfo"
        :closable="!systemSaving && !bindingSaving"
        :disabled="systemSaving || bindingSaving"
        :submit-text="systemForm.id ? '保存系统信息' : '创建并继续配置'"
        label-placement="left"
        label-width="100"
      >
        <NSpin :show="editorLoading">
          <NAlert v-if="editorError" type="error" closable class="state-alert" @close="editorError = ''">{{
            editorError
          }}</NAlert>
          <NGrid :cols="2" responsive="screen" :x-gap="16">
            <NFormItemGi label="所属场馆" path="orgId">
              <VenueSelect
                v-model:value="systemForm.orgId"
                :disabled="Boolean(systemForm.id)"
                placeholder="请选择场馆"
                @update:value="handleEditorVenueChange"
              />
            </NFormItemGi>
            <NFormItemGi label="系统编码" path="code"
              ><NInput v-model:value="systemForm.code" maxlength="64"
            /></NFormItemGi>
            <NFormItemGi label="系统名称" path="name"
              ><NInput v-model:value="systemForm.name" maxlength="100"
            /></NFormItemGi>
            <NFormItemGi label="显示顺序" path="sort"
              ><NInputNumber v-model:value="systemForm.sort" :min="0" :max="9999"
            /></NFormItemGi>
            <NFormItemGi label="覆盖类型" path="scopeType">
              <NSelect
                v-model:value="systemForm.scopeType"
                :options="scopeTypeOptions"
                @update:value="handleScopeTypeChange"
              />
            </NFormItemGi>
            <NFormItemGi v-if="systemForm.scopeType !== 'VENUE'" label="覆盖范围" path="scopeLocationId">
              <NSelect
                v-model:value="systemForm.scopeLocationId"
                :options="scopeLocationOptions"
                :loading="catalogLoading"
                clearable
                filterable
                :placeholder="systemForm.scopeType === 'SITE' ? '请选择馆区' : '请选择建筑'"
              >
                <template #empty
                  ><NEmpty
                    size="small"
                    :description="`当前场馆暂无${systemForm.scopeType === 'SITE' ? '馆区' : '建筑'}位置`"
                /></template>
              </NSelect>
            </NFormItemGi>
            <NFormItemGi label="系统说明" path="description" :span="2">
              <NInput v-model:value="systemForm.description" type="textarea" :rows="3" maxlength="1000" show-count />
            </NFormItemGi>
          </NGrid>

          <NAlert v-if="!systemForm.id" type="info" :bordered="false">
            请先创建系统，再配置分项、监测点和设备。设备安装位置始终读取设备当前登记信息。
          </NAlert>

          <template v-else>
            <NDivider title-placement="left">分项与监测设备</NDivider>
            <NAlert v-if="candidateError" type="warning" closable class="state-alert" @close="candidateError = ''">{{
              candidateError
            }}</NAlert>
            <NAlert v-if="scopeChanged" type="warning" :bordered="false" class="state-alert">
              覆盖范围已经修改，请先保存系统信息；保存后会按新范围重新校验候选设备。
            </NAlert>
            <div class="binding-toolbar">
              <div>
                <strong>设备绑定</strong>
                <span>保存时原子替换整套绑定；安装位置和完整性结果不可在此编辑。</span>
              </div>
              <NSpace>
                <NButton :loading="candidateLoading" :disabled="scopeChanged" @click="loadCandidates"
                  >刷新候选设备</NButton
                >
                <NButton type="primary" secondary :disabled="scopeChanged || !candidates.length" @click="addBinding"
                  >新增监测点</NButton
                >
                <NButton type="primary" :loading="bindingSaving" :disabled="scopeChanged" @click="saveBindings"
                  >保存设备绑定</NButton
                >
              </NSpace>
            </div>
            <NDataTable
              :columns="bindingColumns"
              :data="bindingDrafts"
              :row-key="(row) => row.localKey"
              :scroll-x="1280"
              :max-height="500"
              virtual-scroll
              size="small"
            />
            <NEmpty
              v-if="!bindingDrafts.length"
              description="尚未绑定设备；可先保存空绑定，再逐步布点"
              class="binding-empty"
            />
          </template>
        </NSpin>
      </HPageForm>
    </div>
  </SafetyPageGuard>
</template>

<script setup lang="ts">
import { computed, h, onActivated, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  NAlert,
  NButton,
  NCard,
  NCollapse,
  NCollapseItem,
  NDataTable,
  NDivider,
  NEmpty,
  NFormItemGi,
  NGrid,
  NGridItem,
  NInput,
  NInputNumber,
  NPagination,
  NSelect,
  NSpace,
  NSpin,
  NStatistic,
  NTag,
  type DataTableColumns,
  type FormRules,
  useDialog,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { useAuthStore } from '@/store/modules/auth';
import { ingestApi } from '../../museum-device/api/ingest';
import VenueSelect from '../../museum-device/components/VenueSelect.vue';
import type { RecordFilterOptionsVo } from '../../museum-device/typings';
import { safetyApi, unwrapResponse } from '../api';
import type {
  LightningSystemOverviewVo,
  SafetySystemCommand,
  SafetySystemComponentType,
  SafetySystemDeviceCandidateVo,
  SafetySystemDevicesCommand,
  SafetySystemDeviceVo,
  SafetySystemIntegrityWarning,
  SafetySystemScopeType,
  SafetySystemVo,
  SafetyVenueDomainState,
} from '../typings';
import SafetyPageGuard from '../components/SafetyPageGuard.vue';

interface SystemFormModel {
  id: string | null;
  orgId: string | null;
  code: string;
  name: string;
  scopeType: SafetySystemScopeType;
  scopeLocationId: string | null;
  description: string;
  sort: number;
}

interface BindingDraft {
  localKey: string;
  bindingId?: string;
  deviceId: string | null;
  componentType: SafetySystemComponentType;
  pointCode: string;
  pointName: string;
  sort: number;
}

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const message = useMessage();
const dialog = useDialog();
const canSelect = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_SELECT'));
const canManage = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_MANAGE'));
const canReport = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_REPORT'));
const orgId = ref<string | null>((route.query.orgId as string) || null);
const keyword = ref('');
const stateFilter = ref<SafetyVenueDomainState | null>(null);
const page = ref(1);
const pageSize = ref(12);
const loading = ref(false);
const error = ref('');
let firstActivation = true;

const emptyOverview = (): LightningSystemOverviewVo => ({
  systemCount: 0,
  normalSystems: 0,
  riskSystems: 0,
  interruptedSystems: 0,
  unconfiguredSystems: 0,
  configuredDevices: 0,
  onlineDevices: 0,
  offlineDevices: 0,
  activeRisks: 0,
  activeAlarms: 0,
  integrityWarningCount: 0,
  systems: [],
});
const overview = ref<LightningSystemOverviewVo>(emptyOverview());
const stateOptions = [
  { label: '正常', value: 'NORMAL' },
  { label: '存在风险', value: 'RISK' },
  { label: '数据中断', value: 'DATA_INTERRUPTED' },
  { label: '未配置设备', value: 'UNCONFIGURED' },
];
const scopeTypeOptions = [
  { label: '整馆', value: 'VENUE' },
  { label: '馆区', value: 'SITE' },
  { label: '建筑', value: 'BUILDING' },
];
const componentTypeOptions = [
  { label: '大气电场', value: 'ATMOSPHERIC_ELECTRIC_FIELD' },
  { label: '雷电流', value: 'LIGHTNING_CURRENT' },
  { label: '接地系统', value: 'GROUNDING' },
  { label: '引下线', value: 'DOWN_CONDUCTOR' },
  { label: '浪涌保护器（SPD）', value: 'SPD' },
  { label: '其他', value: 'OTHER' },
];
const warningLabels: Record<SafetySystemIntegrityWarning, string> = {
  DEVICE_MISSING: '绑定设备不存在',
  DEVICE_DISABLED: '设备已停用',
  DEVICE_ORG_MISMATCH: '设备不属于当前场馆，设备信息已脱敏',
  LOCATION_MISSING: '未登记安装位置',
  LOCATION_DISABLED: '安装位置已停用',
  LOCATION_ORG_MISMATCH: '安装位置不属于当前场馆',
  COLLECTION_CONTAINER_LOCATION: '安装在藏品容器位置',
  INSTALLATION_OUT_OF_SCOPE: '安装位置超出系统范围',
  LIGHTNING_CAPABILITY_MISSING: '缺少防雷监测指标能力',
  BOUND_TO_OTHER_SYSTEM: '已绑定其他防雷系统',
};

const summaryCards = computed(() => [
  { label: '防雷系统', value: overview.value.systemCount, hint: '当前可见整体系统', tone: 'info' },
  { label: '正常系统', value: overview.value.normalSystems, hint: '运行与配置均正常', tone: 'success' },
  { label: '风险系统', value: overview.value.riskSystems, hint: '存在告警、风险或完整性问题', tone: 'danger' },
  { label: '数据中断', value: overview.value.interruptedSystems, hint: '已配置但存在离线', tone: 'warning' },
  { label: '待配置系统', value: overview.value.unconfiguredSystems, hint: '尚未绑定监测设备', tone: 'default' },
  {
    label: '设备在线',
    value: `${overview.value.onlineDevices} / ${overview.value.configuredDevices}`,
    hint: '在线 / 已配置设备',
    tone: 'info',
  },
  { label: '完整性问题', value: overview.value.integrityWarningCount, hint: '范围、位置或能力异常', tone: 'warning' },
]);
const filteredSystems = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase();
  return (overview.value.systems || []).filter((system) => {
    if (stateFilter.value && system.state !== stateFilter.value) return false;
    if (!normalizedKeyword) return true;
    return [system.name, system.code, system.scopeLocationPath, system.description].some((value) =>
      value?.toLowerCase().includes(normalizedKeyword),
    );
  });
});
const pagedSystems = computed(() => {
  const offset = (page.value - 1) * pageSize.value;
  return filteredSystems.value.slice(offset, offset + pageSize.value);
});

watch([keyword, stateFilter], () => {
  page.value = 1;
});
watch(pageSize, () => {
  page.value = 1;
});

const deviceColumns: DataTableColumns<SafetySystemDeviceVo> = [
  {
    title: '监测点',
    key: 'pointName',
    fixed: 'left',
    minWidth: 170,
    render: (row) => row.pointName || row.pointCode || '--',
  },
  { title: '点位编码', key: 'pointCode', width: 140, render: (row) => row.pointCode || '--' },
  {
    title: '设备',
    key: 'deviceName',
    minWidth: 190,
    render: (row) =>
      `${row.deviceName || row.deviceCode || row.deviceId}${row.deviceCode ? `（${row.deviceCode}）` : ''}`,
  },
  {
    title: '当前安装位置',
    key: 'installationLocationPath',
    minWidth: 250,
    render: (row) =>
      h('div', { class: 'installation-evidence' }, [
        h('span', null, row.installationLocationPath || row.installationLocationId || '未登记安装位置'),
        row.installationInScope
          ? null
          : h(NTag, { size: 'small', type: 'warning', bordered: false }, { default: () => '超出范围' }),
      ]),
  },
  {
    title: '设备状态',
    key: 'online',
    width: 105,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: row.online ? 'success' : 'warning', bordered: false },
        { default: () => (row.online ? '在线' : '离线') },
      ),
  },
  {
    title: '最后在线',
    key: 'lastSeenAt',
    width: 180,
    render: (row) => (row.lastSeenAt ? formatDateTime(row.lastSeenAt) : '--'),
  },
  {
    title: '风险 / 告警',
    key: 'riskAlarm',
    width: 115,
    render: (row) => `${row.activeRisks || 0} / ${row.activeAlarms || 0}`,
  },
  {
    title: '完整性',
    key: 'warnings',
    minWidth: 220,
    render: (row) =>
      row.warnings?.length
        ? h(
            NSpace,
            { size: 4, wrap: true },
            {
              default: () =>
                row.warnings.map((warning) =>
                  h(
                    NTag,
                    { size: 'small', type: 'warning', bordered: false },
                    { default: () => warningLabel(warning) },
                  ),
                ),
            },
          )
        : h(NTag, { size: 'small', type: 'success', bordered: false }, { default: () => '正常' }),
  },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 90,
    render: (row) =>
      h(
        NButton,
        {
          text: true,
          type: 'primary',
          onClick: () => router.push({ path: '/museum-device/records', query: { deviceId: row.deviceId } }),
        },
        { default: () => '查看数据' },
      ),
  },
];

const editorVisible = ref(false);
const editorLoading = ref(false);
const editorError = ref('');
const systemSaving = ref(false);
const bindingSaving = ref(false);
const catalogLoading = ref(false);
const candidateLoading = ref(false);
const candidateError = ref('');
const editorCatalog = ref<RecordFilterOptionsVo>({ devices: [], locations: [] });
const candidates = ref<SafetySystemDeviceCandidateVo[]>([]);
const bindingDrafts = ref<BindingDraft[]>([]);
const persistedScopeKey = ref('');
let bindingSequence = 0;
const systemForm = reactive<SystemFormModel>(emptySystemForm());
const scopeChanged = computed(() => Boolean(systemForm.id) && persistedScopeKey.value !== scopeKey());
const systemRules: FormRules = {
  orgId: { required: true, message: '请选择所属场馆', trigger: 'change' },
  code: { required: true, message: '请输入系统编码', trigger: ['blur', 'input'] },
  name: { required: true, message: '请输入系统名称', trigger: ['blur', 'input'] },
  scopeType: { required: true, message: '请选择覆盖类型', trigger: 'change' },
  scopeLocationId: {
    validator: () => systemForm.scopeType === 'VENUE' || Boolean(systemForm.scopeLocationId),
    message: '请选择系统覆盖范围',
    trigger: 'change',
  },
};
const scopeLocationOptions = computed(() =>
  editorCatalog.value.locations
    .filter((item) => item.status === 'NORMAL' && item.type?.toUpperCase() === systemForm.scopeType)
    .map((item) => ({ label: item.path || item.name || item.code, value: item.id })),
);

const bindingColumns = computed<DataTableColumns<BindingDraft>>(() => [
  {
    title: '分项',
    key: 'componentType',
    fixed: 'left',
    width: 190,
    render: (row) =>
      h(NSelect, {
        value: row.componentType,
        options: componentTypeOptions,
        filterable: true,
        'onUpdate:value': (value: SafetySystemComponentType) => {
          row.componentType = value;
        },
      }),
  },
  {
    title: '监测点编码',
    key: 'pointCode',
    width: 160,
    render: (row) =>
      h(NInput, {
        value: row.pointCode,
        maxlength: 64,
        'onUpdate:value': (value: string) => {
          row.pointCode = value;
        },
      }),
  },
  {
    title: '监测点名称',
    key: 'pointName',
    width: 180,
    render: (row) =>
      h(NInput, {
        value: row.pointName,
        maxlength: 100,
        'onUpdate:value': (value: string) => {
          row.pointName = value;
        },
      }),
  },
  {
    title: '设备',
    key: 'deviceId',
    minWidth: 290,
    render: (row) =>
      h(NSelect, {
        value: row.deviceId,
        options: candidateOptions(row),
        filterable: true,
        clearable: true,
        placeholder: '请选择具备防雷能力的设备',
        'onUpdate:value': (value: string | null) => {
          row.deviceId = value;
        },
      }),
  },
  {
    title: '当前安装位置（只读）',
    key: 'installationLocation',
    minWidth: 250,
    render: (row) => {
      const candidate = candidateOf(row.deviceId);
      return candidate?.installationLocationPath || candidate?.installationLocationId || '未选择设备';
    },
  },
  {
    title: '完整性',
    key: 'integrity',
    minWidth: 220,
    render: (row) => {
      const candidate = candidateOf(row.deviceId);
      if (!candidate) return '--';
      if (!candidate.warnings?.length)
        return h(NTag, { size: 'small', type: 'success', bordered: false }, { default: () => '可绑定' });
      return h(
        NSpace,
        { size: 4, wrap: true },
        {
          default: () =>
            candidate.warnings.map((warning) =>
              h(
                NTag,
                { size: 'small', type: candidate.eligible ? 'warning' : 'error', bordered: false },
                { default: () => warningLabel(warning) },
              ),
            ),
        },
      );
    },
  },
  {
    title: '排序',
    key: 'sort',
    width: 105,
    render: (row) =>
      h(NInputNumber, {
        value: row.sort,
        min: 0,
        max: 9999,
        'onUpdate:value': (value: number | null) => {
          row.sort = value || 0;
        },
      }),
  },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 75,
    render: (row) =>
      h(NButton, { text: true, type: 'error', onClick: () => removeBinding(row.localKey) }, { default: () => '移除' }),
  },
]);

function emptySystemForm(): SystemFormModel {
  return {
    id: null,
    orgId: orgId.value,
    code: '',
    name: '',
    scopeType: 'VENUE',
    scopeLocationId: null,
    description: '',
    sort: 0,
  };
}

function responseList<T>(response: unknown): T[] {
  const payload = unwrapResponse<any>(response);
  if (Array.isArray(payload)) return payload as T[];
  return Array.isArray(payload?.list) ? (payload.list as T[]) : [];
}

async function loadOverview() {
  if (!canSelect.value) return;
  loading.value = true;
  error.value = '';
  try {
    const value = unwrapResponse<LightningSystemOverviewVo>(
      await safetyApi.lightningOverview({ orgId: orgId.value || undefined }),
    );
    overview.value = { ...emptyOverview(), ...(value || {}), systems: value?.systems || [] };
    const maxPage = Math.max(1, Math.ceil(filteredSystems.value.length / pageSize.value));
    if (page.value > maxPage) page.value = maxPage;
  } catch (cause: any) {
    overview.value = emptyOverview();
    error.value = cause?.message || '防雷系统整体状态加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

async function handleVenueChange() {
  page.value = 1;
  await router.replace({ query: { ...route.query, orgId: orgId.value || undefined } });
  await loadOverview();
}

function openCreate() {
  Object.assign(systemForm, emptySystemForm());
  editorCatalog.value = { devices: [], locations: [] };
  candidates.value = [];
  bindingDrafts.value = [];
  persistedScopeKey.value = '';
  editorError.value = '';
  candidateError.value = '';
  editorVisible.value = true;
  if (systemForm.orgId) void loadEditorCatalog();
}

async function openEdit(system: SafetySystemVo) {
  editorVisible.value = true;
  editorLoading.value = true;
  editorError.value = '';
  candidateError.value = '';
  try {
    const detail = unwrapResponse<SafetySystemVo>(await safetyApi.system(system.id));
    Object.assign(systemForm, {
      id: detail.id,
      orgId: detail.orgId,
      code: detail.code,
      name: detail.name,
      scopeType: detail.scopeType,
      scopeLocationId: detail.scopeLocationId || null,
      description: detail.description || '',
      sort: detail.sort || 0,
    });
    persistedScopeKey.value = scopeKey();
    bindingDrafts.value = (detail.components || [])
      .flatMap((component) => component.devices || [])
      .map((device) => bindingDraftFromVo(device));
    await Promise.all([loadEditorCatalog(), loadCandidates()]);
  } catch (cause: any) {
    editorError.value = cause?.message || '防雷系统详情加载失败。';
  } finally {
    editorLoading.value = false;
  }
}

async function handleEditorVenueChange() {
  systemForm.scopeLocationId = null;
  await loadEditorCatalog();
}

function handleScopeTypeChange() {
  systemForm.scopeLocationId = null;
}

async function loadEditorCatalog() {
  editorCatalog.value = { devices: [], locations: [] };
  if (!systemForm.orgId) return;
  catalogLoading.value = true;
  try {
    const value = unwrapResponse<RecordFilterOptionsVo>(
      await ingestApi.recordFilterOptions({ orgId: systemForm.orgId }),
    );
    editorCatalog.value = value || { devices: [], locations: [] };
  } catch (cause: any) {
    editorError.value = cause?.message || '场馆空间目录加载失败。';
  } finally {
    catalogLoading.value = false;
  }
}

async function saveSystemInfo() {
  if (!canManage.value) return;
  systemSaving.value = true;
  editorError.value = '';
  const command: SafetySystemCommand = {
    orgId: systemForm.orgId,
    code: systemForm.code.trim(),
    name: systemForm.name.trim(),
    domain: 'LIGHTNING',
    scopeType: systemForm.scopeType,
    scopeLocationId: systemForm.scopeType === 'VENUE' ? null : systemForm.scopeLocationId,
    description: systemForm.description.trim() || null,
    sort: systemForm.sort || 0,
  };
  try {
    if (systemForm.id) {
      await safetyApi.updateSystem(systemForm.id, command);
      message.success('防雷系统信息已保存');
    } else {
      const result = unwrapResponse<any>(await safetyApi.createSystem(command));
      const id = typeof result === 'string' ? result : result?.id;
      if (!id) throw new Error('系统已创建，但接口未返回系统 ID');
      systemForm.id = String(id);
      message.success('防雷系统已创建，请继续配置监测设备');
    }
    persistedScopeKey.value = scopeKey();
    await Promise.all([loadOverview(), loadEditorCatalog(), loadCandidates()]);
  } catch (cause: any) {
    editorError.value = cause?.message || '防雷系统保存失败，当前表单内容已保留。';
  } finally {
    systemSaving.value = false;
  }
}

async function loadCandidates() {
  if (!systemForm.id) return;
  candidateLoading.value = true;
  candidateError.value = '';
  try {
    candidates.value = responseList<SafetySystemDeviceCandidateVo>(
      await safetyApi.systemDeviceCandidates(systemForm.id),
    );
  } catch (cause: any) {
    candidates.value = [];
    candidateError.value = cause?.message || '候选设备加载失败，现有绑定未被修改。';
  } finally {
    candidateLoading.value = false;
  }
}

function addBinding() {
  bindingSequence += 1;
  bindingDrafts.value.push({
    localKey: `new-${Date.now()}-${bindingSequence}`,
    deviceId: null,
    componentType: 'OTHER',
    pointCode: '',
    pointName: '',
    sort: bindingDrafts.value.length,
  });
}

function removeBinding(localKey: string) {
  bindingDrafts.value = bindingDrafts.value.filter((item) => item.localKey !== localKey);
}

async function saveBindings() {
  if (!systemForm.id || !canManage.value) return;
  const deviceIds = bindingDrafts.value.map((item) => item.deviceId).filter(Boolean) as string[];
  if (deviceIds.length !== bindingDrafts.value.length) {
    candidateError.value = '请为每个监测点选择设备。';
    return;
  }
  if (new Set(deviceIds).size !== deviceIds.length) {
    candidateError.value = '同一设备不能在当前系统中重复绑定。';
    return;
  }
  const incompletePoint = bindingDrafts.value.find((item) => !item.pointCode.trim() || !item.pointName.trim());
  if (incompletePoint) {
    candidateError.value = '请填写每个监测点的编码和名称。';
    return;
  }
  const ineligible = bindingDrafts.value
    .map((item) => candidateOf(item.deviceId))
    .find((candidate) => candidate && !candidate.eligible);
  if (ineligible) {
    candidateError.value = `设备“${ineligible.name || ineligible.code}”不符合绑定条件：${ineligible.warnings.map(warningLabel).join('、') || '已绑定其他系统'}`;
    return;
  }
  bindingSaving.value = true;
  candidateError.value = '';
  const command: SafetySystemDevicesCommand = {
    devices: bindingDrafts.value.map((item) => ({
      deviceId: item.deviceId,
      componentType: item.componentType,
      pointCode: item.pointCode.trim(),
      pointName: item.pointName.trim(),
      sort: item.sort || 0,
    })),
  };
  try {
    await safetyApi.replaceSystemDevices(systemForm.id, command);
    message.success('防雷系统设备绑定已保存');
    const refreshed = unwrapResponse<SafetySystemVo>(await safetyApi.system(systemForm.id));
    bindingDrafts.value = (refreshed.components || [])
      .flatMap((component) => component.devices || [])
      .map(bindingDraftFromVo);
    await Promise.all([loadCandidates(), loadOverview()]);
  } catch (cause: any) {
    candidateError.value = cause?.message || '设备绑定保存失败，当前编辑内容已保留。';
  } finally {
    bindingSaving.value = false;
  }
}

function removeSystem(system: SafetySystemVo) {
  dialog.warning({
    title: '停用防雷系统',
    content: `确认停用“${system.name}”？停用后不再进入整体态势，历史风险和报告继续保留。`,
    positiveText: '确认停用',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await safetyApi.deleteSystem(system.id);
        message.success('防雷系统已停用');
        await loadOverview();
      } catch (cause: any) {
        error.value = cause?.message || '防雷系统停用失败。';
      }
    },
  });
}

function bindingDraftFromVo(device: SafetySystemDeviceVo): BindingDraft {
  return {
    localKey: device.bindingId,
    bindingId: device.bindingId,
    deviceId: device.deviceId,
    componentType: device.componentType,
    pointCode: device.pointCode || '',
    pointName: device.pointName || '',
    sort: device.sort || 0,
  };
}

function candidateOf(deviceId?: string | null) {
  return candidates.value.find((item) => item.deviceId === deviceId);
}

function scopeKey() {
  return `${systemForm.orgId || ''}|${systemForm.scopeType}|${systemForm.scopeType === 'VENUE' ? '' : systemForm.scopeLocationId || ''}`;
}

function candidateOptions(row: BindingDraft) {
  const selectedByOthers = new Set(
    bindingDrafts.value
      .filter((item) => item.localKey !== row.localKey)
      .map((item) => item.deviceId)
      .filter(Boolean),
  );
  return candidates.value.map((candidate) => ({
    value: candidate.deviceId,
    label: `${candidate.name || candidate.code}（${candidate.code}）${candidate.installationLocationPath ? ` · ${candidate.installationLocationPath}` : ' · 未登记位置'}${candidate.boundSystemName && candidate.boundSystemId !== systemForm.id ? ` · 已绑定${candidate.boundSystemName}` : ''}`,
    disabled: (!candidate.eligible && candidate.deviceId !== row.deviceId) || selectedByOthers.has(candidate.deviceId),
  }));
}

function scopeLabel(system: SafetySystemVo) {
  if (system.scopeType === 'VENUE') return '整个场馆';
  return (
    system.scopeLocationPath || system.scopeLocationId || (system.scopeType === 'SITE' ? '馆区未设置' : '建筑未设置')
  );
}

function componentTypeLabel(value: SafetySystemComponentType) {
  return componentTypeOptions.find((item) => item.value === value)?.label || value;
}

function warningLabel(value: SafetySystemIntegrityWarning) {
  return warningLabels[value] || value;
}

function stateLabel(value: SafetyVenueDomainState) {
  return (
    (
      { NORMAL: '正常', RISK: '存在风险', DATA_INTERRUPTED: '数据中断', UNCONFIGURED: '未配置设备' } as Record<
        string,
        string
      >
    )[value] || value
  );
}

function stateTagType(value: SafetyVenueDomainState): 'default' | 'success' | 'warning' | 'error' {
  if (value === 'NORMAL') return 'success';
  if (value === 'RISK') return 'error';
  if (value === 'DATA_INTERRUPTED') return 'warning';
  return 'default';
}

function openRisks(system: SafetySystemVo) {
  router.push({
    path: '/museum-safety/risk',
    query: {
      orgId: system.orgId,
      systemId: system.id,
      systemName: system.name,
      domain: 'LIGHTNING',
      activeOnly: 'true',
    },
  });
}

function openReport(system: SafetySystemVo) {
  router.push({
    path: '/museum-safety/report',
    query: { orgId: system.orgId, type: 'LIGHTNING', systemId: system.id },
  });
}

onMounted(loadOverview);
onActivated(() => {
  if (firstActivation) {
    firstActivation = false;
    return;
  }
  orgId.value = (route.query.orgId as string) || null;
  void loadOverview();
});
</script>

<style scoped>
.lightning-page {
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 14px;
  overflow: auto;
  box-sizing: border-box;
}
.page-heading {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  margin-bottom: 14px;
}
.page-heading h2 {
  margin: 0;
  font-size: 20px;
}
.page-heading p {
  margin: 6px 0 0;
  color: #697386;
  font-size: 13px;
}
.heading-actions {
  display: grid;
  grid-template-columns: minmax(190px, 240px) minmax(190px, 240px) 140px auto auto;
  gap: 10px;
  align-items: center;
}
.state-alert {
  margin-bottom: 12px;
}
.summary-grid {
  margin-bottom: 16px;
}
.summary-card {
  min-height: 104px;
  border-top: 3px solid #8c9bab;
}
.summary-card--success {
  border-top-color: #18a058;
}
.summary-card--danger {
  border-top-color: #d03050;
}
.summary-card--warning {
  border-top-color: #f0a020;
}
.summary-card--info {
  border-top-color: #2080f0;
}
.summary-card__hint {
  margin-top: 6px;
  color: #697386;
  font-size: 12px;
  line-height: 1.45;
}
.list-toolbar,
.binding-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin: 12px 0;
}
.list-toolbar > div > *,
.binding-toolbar > div > * {
  display: block;
}
.list-toolbar span,
.binding-toolbar span {
  margin-top: 4px;
  color: #697386;
  font-size: 12px;
}
.system-card {
  height: 100%;
  box-shadow: 0 4px 18px rgb(23 42 69 / 7%);
}
.system-title {
  display: flex;
  gap: 10px;
  align-items: center;
}
.system-title > div > * {
  display: block;
}
.system-title > div > span {
  margin-top: 3px;
  color: #7a8494;
  font-size: 12px;
}
.system-scope {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  padding: 12px;
  border-radius: 6px;
  background: rgba(32, 128, 240, 0.06);
}
.system-scope div > * {
  display: block;
}
.system-scope span {
  margin-bottom: 4px;
  color: #697386;
  font-size: 12px;
}
.system-scope strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.system-description {
  margin: 10px 0 0;
  color: #566175;
  line-height: 1.6;
}
.system-stat-grid {
  margin-top: 12px;
}
.system-stat {
  display: flex;
  min-height: 72px;
  width: 100%;
  padding: 10px;
  border: 0;
  border-radius: 6px;
  background: rgba(128, 128, 128, 0.06);
  box-sizing: border-box;
  flex-direction: column;
  justify-content: center;
  text-align: left;
}
.system-stat strong {
  font-size: 18px;
}
.system-stat span {
  margin-top: 4px;
  color: #7a8494;
  font-size: 12px;
}
.system-stat--action {
  cursor: pointer;
}
.system-stat--action:hover {
  background: rgba(208, 48, 80, 0.08);
}
.integrity-alert {
  margin-top: 12px;
}
.component-collapse {
  margin-top: 12px;
}
.component-heading {
  display: flex;
  gap: 8px;
  align-items: center;
}
.component-summary {
  color: #697386;
  font-size: 12px;
  white-space: nowrap;
}
.component-empty,
.binding-empty {
  padding: 24px 0;
}
.installation-evidence {
  display: flex;
  gap: 6px;
  align-items: center;
}
.installation-evidence > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
.page-empty {
  padding: 80px 0;
}
@media (max-width: 1280px) {
  .page-heading {
    flex-direction: column;
  }
  .heading-actions {
    width: 100%;
    grid-template-columns: repeat(3, minmax(180px, 1fr));
  }
}
@media (max-width: 720px) {
  .heading-actions {
    grid-template-columns: 1fr;
  }
  .system-scope {
    grid-template-columns: 1fr;
  }
  .list-toolbar,
  .binding-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>

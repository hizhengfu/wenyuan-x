<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="restorationApi.page"
    :params="queryParams"
    :is-add="canWriteProject"
    :is-edit="false"
    :is-delete="false"
    :add-permission="RESTORATION_PROJECT_WRITE_PERMISSION"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="COLLECTION_RESTORATION" /></template>
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="登记号 / 名称 / 机构 / 方案 / 过程" style="width: 240px" />
      </NFormItem>
      <NFormItem v-if="canQueryCollection" label="藏品">
        <CollectionSelect v-model:value="queryParams.collectionId" clearable style="width: 180px" />
      </NFormItem>
      <NFormItem label="修复机构">
        <RestorationOrgPicker :only-enabled="false" v-model:value="queryParams.restoreOrgId" clearable style="width: 200px" />
      </NFormItem>
      <NFormItem label="经办人">
        <UserSelect v-model:value="queryParams.operator" clearable placeholder="经办人" style="width: 160px" />
      </NFormItem>
      <NFormItem label="复核人">
        <UserSelect v-model:value="queryParams.reviewer" clearable placeholder="复核人" style="width: 160px" />
      </NFormItem>
      <NFormItem label="审批状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable placeholder="审批状态" style="width: 130px" />
      </NFormItem>
      <NFormItem label="修复状态">
        <HDictSelect v-model:value="queryParams.state" type="col_process_state" clearable placeholder="修复状态" style="width: 130px" />
      </NFormItem>
      <NFormItem label="文物级别">
        <HDictSelect v-model:value="queryParams.collectionLevel" type="col_relic_level" clearable style="width: 120px" />
      </NFormItem>
      <NFormItem label="审批层级">
        <HDictSelect v-model:value="queryParams.approvalScope" type="col_approve_level" clearable style="width: 120px" />
      </NFormItem>
      <NFormItem label="项目来源">
        <HDictSelect v-model:value="queryParams.triggerSource" type="restoration_trigger_source" clearable style="width: 140px" />
      </NFormItem>
      <NFormItem label="紧急度">
        <HDictSelect v-model:value="queryParams.urgencyLevel" type="restoration_urgency" clearable style="width: 120px" />
      </NFormItem>
      <NFormItem label="修复前图">
        <HDictSelect v-model:value="queryParams.hasBeforeImages" type="col_presence" clearable placeholder="有无" style="width: 110px" />
      </NFormItem>
      <NFormItem label="修复后图">
        <HDictSelect v-model:value="queryParams.hasAfterImages" type="col_presence" clearable placeholder="有无" style="width: 110px" />
      </NFormItem>
      <NFormItem label="开始日期">
        <NDatePicker
          v-model:value="startDateRange"
          type="daterange"
          clearable
          style="width: 230px"
          @update:value="syncStartDateRange"
        />
      </NFormItem>
      <NFormItem label="竣工日期">
        <NDatePicker
          v-model:value="endDateRange"
          type="daterange"
          clearable
          style="width: 230px"
          @update:value="syncEndDateRange"
        />
      </NFormItem>
    </template>

    <FlowFormDrawer
      v-if="flowMounted"
      :key="flowWindowKey"
      ref="flowDrawerRef"
      :fetch-fn="restorationApi.flow"
      :create-api="restorationApi.create"
      :save-api="saveFlowDraft"
      :submit-api="restorationApi.submit"
      :cancel-api="restorationApi.cancel"
      @success="onDraftSuccess"
    />

    <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />
    <RestorationDetailView v-model:show="detailShow" :restoration-id="detailId" />

    <RestorationComplianceDrawer ref="complianceRef" @saved="refreshTable" @flow="openComplianceFlow" />
    <RestorationCloseoutDrawer ref="closeoutRef" @saved="refreshTable" />
    <RestorationAftercareDrawer ref="aftercareRef" @saved="refreshTable" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { computed, h, reactive, ref, nextTick, watch, onActivated, onDeactivated } from 'vue';
import { useRoute } from 'vue-router';
import { NButton, NTag, useMessage } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import { restorationApi } from '../api/restoration';
import CollectionSelect from '@/modules/collection/components/CollectionSelect.vue';
import CollectionPrintPreviewModal from '@/modules/collection/components/CollectionPrintPreviewModal.vue';
import FlowFormDrawer from '@/modules/flow/components/FlowFormDrawer.vue';
import { useFlowApproval } from '@/modules/flow/composables/useFlowApproval';
import { dictText, presenceText } from '@/modules/collection/utils/dictRender';
import type { CollectionPrintVo } from '@/modules/collection/typings/collection';
import type { RestorationVo } from '../typings';
import RestorationOrgPicker from '../components/RestorationOrgPicker.vue';
import RestorationDetailView from '../components/RestorationDetailView.vue';
import RestorationComplianceDrawer from '../components/RestorationComplianceDrawer.vue';
import RestorationCloseoutDrawer from '../components/RestorationCloseoutDrawer.vue';
import RestorationAftercareDrawer from '../components/RestorationAftercareDrawer.vue';
const aftercareRef = ref<any>();
const closeoutRef = ref<any>();
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import {
  COLLECTION_PRINT_RENDER_CODES,
  RESTORATION_PROJECT_WRITE_CODES,
  RESTORATION_PROJECT_WRITE_PERMISSION,
} from '../utils/restorationPermissions';
import { COLLECTION_SELECT_QUERY_CODES } from '@/modules/collection/utils/collectionSelectPermission';
import { useAuthStore } from '@/store';

const route = useRoute();
const message = useMessage();
const tableRef = ref<any>(null);
const auth = useAuthStore();
// 新增走 FlowFormDrawer：RestorationController.create / submit / cancel，后端同一组码；「开始 / 竣工」对应 start / complete 也是这组码。
// 未传 permission 时表格放行，所以另用 is-add 开关；本页没有表格自带的编辑（is-edit 保持 false），项目办理在各抽屉里由后端逐项校验。
const canWriteProject = computed(() => auth.hasPermission(RESTORATION_PROJECT_WRITE_CODES));
// 打印预览最终走 CollectionPrintController.render.pdf，仅 ADMIN / COLLECTION_SELECT。
const canPrintProject = computed(() => auth.hasPermission(COLLECTION_PRINT_RENDER_CODES));
// 列表藏品筛选项依赖 CollectionController.page；无 COLLECTION_SELECT 等码时不渲染，避免 403。
const canQueryCollection = computed(() => auth.hasPermission(COLLECTION_SELECT_QUERY_CODES));
const flowDrawerRef = ref<any>(null);
const flowWindowKey = ref(0);
const flowMounted = ref(true);
onDeactivated(() => { flowMounted.value = false; flowWindowKey.value++; });
onActivated(() => { flowMounted.value = true; });
function onDraftSuccess() {
  flowWindowKey.value++;
  refreshTable();
}
const projectLabel = (row: RestorationVo) => row.collectionName || row.collectionTotalRegNo || row.id || '修复详情';
const { flowStatusColumn } = useFlowApproval({
  refTable: tableRef,
  drawerRef: flowDrawerRef,
  getLabel: projectLabel,
});
/** F2：无项目写权限时以只读打开审批抽屉，隐藏「保存草稿 / 提交审批」。 */
function handleTitleClick(row: RestorationVo) {
  const title = projectLabel(row);
  if (!row.id) return;
  if (!canWriteProject.value) {
    flowDrawerRef.value?.openBusinessReadOnly(row.id, title, 'COLLECTION_RESTORATION');
  } else {
    flowDrawerRef.value?.open(row.id, title);
  }
}

const queryParams = reactive({
  keyword: null as string | null,
  collectionId: null as string | null,
  restoreOrgId: null as string | null,
  operator: null as string | null,
  reviewer: null as string | null,
  flowStatus: null as string | null,
  state: null as string | null,
  collectionLevel: null as string | null,
  approvalScope: null as string | null,
  triggerSource: null as string | null,
  urgencyLevel: null as string | null,
  hasBeforeImages: null as string | null,
  hasAfterImages: null as string | null,
  startDateStart: null as number | null,
  startDateEnd: null as number | null,
  endDateStart: null as number | null,
  endDateEnd: null as number | null,
});
const startDateRange = ref<[number, number] | null>(null);
const endDateRange = ref<[number, number] | null>(null);

const defaultForm: RestorationVo = {
  collectionId: undefined,
  restoreOrgId: undefined,
  operator: undefined,
  reviewer: undefined,
  plan: undefined,
  process: undefined,
  beforeImages: undefined,
  afterImages: undefined,
  startDate: undefined,
};
const printShow = ref(false);
const printData = ref<CollectionPrintVo | null>(null);
const complianceRef=ref<any>();


const detailShow = ref(false);
const detailId = ref<string | null>(null);


function syncStartDateRange(value: [number, number] | null) {
  queryParams.startDateStart = value?.[0] ?? null;
  queryParams.startDateEnd = value?.[1] ?? null;
}

function syncEndDateRange(value: [number, number] | null) {
  queryParams.endDateStart = value?.[0] ?? null;
  queryParams.endDateEnd = value?.[1] ?? null;
}

function splitFileIds(value?: string | null) {
  return (value || '')
    .split(/[,，;\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function hasFiles(value?: string | null) {
  return splitFileIds(value).length > 0;
}

const columns = [
  { title: '总登记号', key: 'collectionTotalRegNo', width: 150, resizable: true, ellipsis: { tooltip: true } },
  { title: '藏品名称', key: 'collectionName', minWidth: 160, resizable: true, width: 180, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  { title: '修复机构', key: 'restoreOrg', minWidth: 150, resizable: true, width: 200, ellipsis: { tooltip: true } },
  { title: '经办人', key: 'operatorName', width: 120, resizable: true, ellipsis: { tooltip: true }, render: (r: RestorationVo) => r.operatorName || r.operator || '—' },
  { title: '复核人', key: 'reviewerName', width: 110, resizable: true, ellipsis: { tooltip: true }, render: (r: RestorationVo) => r.reviewerName || r.reviewer || '—' },
  {
    title: '级别/审批', key: 'level', width: 150, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationVo) => h(NTag, { size: 'small', bordered: false, type: r.collectionLevel === 'FIRST' ? 'error' : 'info' }, {
      default: () => `${levelText(r.collectionLevel)} · ${approvalText(r.approvalScope)}`,
    }),
  },
  {
    title: '来源', key: 'triggerSource', width: 150, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationVo) => h(NTag, { size: 'small', bordered: false, type: r.triggerSource?.startsWith('PRESERVATION') ? 'warning' : 'default' }, {
      default: () => triggerText(r.triggerSource),
    }),
  },
  {
    title: '紧急度', key: 'urgencyLevel', width: 100, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationVo) => h(NTag, { size: 'small', bordered: false, type: r.urgencyLevel === 'EMERGENCY' ? 'error' : 'default' }, {
      default: () => r.urgencyLevel === 'EMERGENCY' ? '抢救性' : '一般',
    }),
  },
  { ...flowStatusColumn, width: 120, ellipsis: { tooltip: true }, resizable: true },
  {
    title: '项目状态', key: 'state', width: 150, ellipsis: { tooltip: true }, resizable: true,
    render: (row: RestorationVo) => {
      const stoppedText = row.actualStoppedAt != null ? '开工后已停止'
        : row.sentStoppedAt != null ? '未开工已停止' : null;
      return stoppedText
        ? h(NTag, { size: 'small', bordered: false, type: 'warning' }, () => stoppedText)
        : dictText('col_process_state', row.state);
    },
  },
  { title: '实际停止时间', key: 'actualStoppedAt', width: 190, ellipsis: { tooltip: true }, resizable: true, columnType: 'dateTime' },
  { title: '未开工停止时间', key: 'sentStoppedAt', width: 190, ellipsis: { tooltip: true }, resizable: true, columnType: 'dateTime' },
  {
    title: '修复前图',
    key: 'beforeImages',
    width: 90,
    render: (r: RestorationVo) => presenceText(hasFiles(r.beforeImages)),
  },
  {
    title: '修复后图',
    key: 'afterImages',
    width: 90,
    render: (r: RestorationVo) => presenceText(hasFiles(r.afterImages)),
  },
  { title: '开始', key: 'startDate', width: 120, ellipsis: { tooltip: true }, resizable: true, columnType: 'date' },
  { title: '竣工', key: 'endDate', width: 120, ellipsis: { tooltip: true }, resizable: true, columnType: 'date' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 250,
    fixed: 'right' as const,
    render: (row: RestorationVo) => {
      const btns: any[] = [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
      ];
      const isVoid = row.flowStatus === 'VOID';
      // F5：作废行只保留详情与（有权限时的）打印，隐藏法定原件办理 / 验收 / 养护 / 审批 / 开始 / 竣工。
      if (!isVoid) {
        btns.push(
          h(NButton,{text:true,type:'primary',size:'small',style:'margin-left:10px',onClick:()=>complianceRef.value?.open(row.id)},()=> '法定原件办理'),
          h(NButton,{text:true,type:'primary',size:'small',style:'margin-left:10px',onClick:()=>closeoutRef.value?.open(row.id)},()=> '验收与归档'),
          h(NButton,{text:true,size:'small',style:'margin-left:10px',onClick:()=>aftercareRef.value?.open(row.id)},()=> '后续养护'),
          h(NButton, { text: true, type: 'primary', size: 'small', style: 'margin-left:10px', onClick: () => handleTitleClick(row) }, () => '审批'),
        );
        // 「开始 / 竣工」是写操作（RestorationController.start / complete），无项目写权限时不显示。
        if (!canWriteProject.value) {
          /* 只读账号不追加写操作按钮 */
        } else if (!row.supersededById && !row.replacementErrors?.length && row.flowStatus === 'PASS' && row.state === 'DRAFT' && !row.sentStoppedAt) {
          btns.push(h(NButton, { text: true, type: 'primary', size: 'small', style: 'margin-left:10px', onClick: () => onStart(row) }, () => '开始'));
        } else if (!row.supersededById && !row.replacementErrors?.length && row.flowStatus === 'PASS' && row.state === 'ONGOING' && !row.actualStoppedAt) {
          btns.push(h(NButton, { text: true, type: 'primary', size: 'small', style: 'margin-left:10px', onClick: () => openComplete(row) }, () => '竣工'));
        }
      }
      // F3：无 render.pdf 权限时不显示打印，避免点了才 403。
      if (canPrintProject.value) {
        btns.push(h(NButton, { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onPrint(row) }, () => '打印'));
      }
      return btns;
    },
  },
];

async function onAdd() {
  const version = ++flowWindowKey.value;
  await nextTick();
  if (!flowMounted.value || version !== flowWindowKey.value) return;
  flowDrawerRef.value?.openCreate({
    title: '新建修复记录',
    businessType: 'COLLECTION_RESTORATION',
    formData: cloneDeep(defaultForm),
  });
}

function levelText(value?: string) {
  return ({ FIRST: '一级', SECOND: '二级', THIRD: '三级', GENERAL: '一般', UNRATED: '未定级' } as Record<string, string>)[value || ''] || '未定级';
}

function approvalText(value?: string) {
  return ({ NATIONAL: '国家局', PROVINCE: '省局', MUSEUM: '馆内' } as Record<string, string>)[value || ''] || '待核实';
}

function triggerText(value?: string) {
  return ({ MANUAL: '手工立项', PRESERVATION_AUTO: '预警自动', PRESERVATION_MANUAL: '预警派单', PATROL: '巡查发现' } as Record<string, string>)[value || ''] || '手工立项';
}

function onView(row: RestorationVo) {
  detailId.value = row.id || null;
  detailShow.value = true;
}

async function onPrint(row: RestorationVo) {
  const resp: any = await restorationApi.print(row.id!);
  printData.value = resp?.data ?? null;
  printShow.value = true;
}

function saveFlowDraft(id: string, data: Record<string, any>) {
  return restorationApi.create({ ...data, id });
}

function refreshTable() {
  tableRef.value?.handleRefresh();
  tableRef.value?.handleQuery?.();
}

async function openComplianceFlow(id: string) {
  const response: any = await restorationApi.get(id);
  if (response?.data) handleTitleClick(response.data);
}
watch(() => route.query.projectId, async (id) => { if (typeof id === 'string' && id.trim()) { await nextTick(); await complianceRef.value?.open(id); } }, { immediate: true });
function onStart(row:RestorationVo){void complianceRef.value?.open(row.id);}
function openComplete(row:RestorationVo){void complianceRef.value?.open(row.id);}
</script>

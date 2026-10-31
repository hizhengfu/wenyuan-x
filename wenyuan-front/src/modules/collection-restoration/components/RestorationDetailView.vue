<template>
  <HPageForm v-model:show="show" :title="project.collectionName || '修复项目详情'" content-only cancel-text="关闭" :footer-style="{ paddingRight: '36px' }">
    <NAlert v-if="loading" type="info">正在核对项目及相关资料…</NAlert>
    <NAlert v-if="error" type="error">{{ error }} <NButton size="small" @click="reload">重新读取</NButton></NAlert>
    <NTabs v-if="!loading && !error && project.id" :key="project.id" v-model:value="activeTab" type="line" animated>
      <NTabPane name="basic" tab="基本信息">
        <NDescriptions label-placement="left" :column="2" bordered size="small">
          <NDescriptionsItem label="藏品">{{ `${project.collectionTotalRegNo || ''} ${project.collectionName || ''}`.trim() || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="文物级别">{{ levelText(project.collectionLevel) }}</NDescriptionsItem>
          <NDescriptionsItem label="修复机构">{{ project.restoreOrg || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="机构资质快照">{{ qualificationSnapshotText(project.restoreOrgQualification) }}</NDescriptionsItem>
          <NDescriptionsItem label="经办人">{{ project.operatorName || project.operator || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="复核人">{{ project.reviewerName || project.reviewer || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="审批层级">{{ approvalText(project.approvalScope) }}</NDescriptionsItem>
          <NDescriptionsItem label="项目来源">{{ triggerText(project.triggerSource) }}</NDescriptionsItem>
          <NDescriptionsItem label="开始日期">{{ formatDateValue(project.startDate) }}</NDescriptionsItem>
          <NDescriptionsItem label="竣工日期">{{ formatDateValue(project.endDate) }}</NDescriptionsItem>
          <NDescriptionsItem label="修复方案" :span="2">{{ project.plan || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="修复过程" :span="2">{{ project.process || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="回库目标环境" :span="2">{{ project.targetEnvironment || '--' }}</NDescriptionsItem>
        </NDescriptions>
      </NTabPane>

      <NTabPane name="process" :tab="`工序 (${processes.length})`">
        <NDataTable :columns="processColumns" :data="processes" :bordered="false" size="small" />
      </NTabPane>

      <NTabPane name="log" tab="修复日志">
        <RestorationProcessLog :restoration-id="project.id" :processes="processes" @changed="reload" />
      </NTabPane>

      <NTabPane name="stage" tab="阶段分工">
        <RestorationStageTimeline :processes="processes" />
      </NTabPane>

      <NTabPane name="damage" :tab="`病害 (${damages.length})`">
        <NDataTable :columns="damageColumns" :data="damages" :bordered="false" size="small" />
      </NTabPane>

      <NTabPane name="contract" tab="合同">
        <NEmpty v-if="!contract" description="未维护委外修复合同" />
        <NDescriptions v-else label-placement="left" :column="2" bordered size="small">
          <NDescriptionsItem label="合同编号">{{ contract.contractNo }}</NDescriptionsItem>
          <NDescriptionsItem label="合同金额">{{ formatMoney(contract.contractAmount) }}</NDescriptionsItem>
          <NDescriptionsItem label="甲方">{{ contract.partyA || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="乙方">{{ contract.partyB || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="乙方证书">{{ contract.partyBCertNo || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="签订日期">{{ formatDateValue(contract.signDate) }}</NDescriptionsItem>
          <NDescriptionsItem label="计划工期" :span="2">{{ formatDateValue(contract.plannedStart) }} ~ {{ formatDateValue(contract.plannedEnd) }}</NDescriptionsItem>
          <NDescriptionsItem label="合同附件" :span="2"><HFileList :value="contract.attachmentFileIds" :revision="contract.id" /></NDescriptionsItem>
          <NDescriptionsItem label="保险条款" :span="2">{{ contract.insuranceInfo || '--' }}</NDescriptionsItem>
        </NDescriptions>
      </NTabPane>

      <NTabPane name="acceptance" tab="验收">
        <NEmpty v-if="!acceptance" description="未录入验收单" />
        <NDescriptions v-else label-placement="left" :column="2" bordered size="small">
          <NDescriptionsItem label="验收类型">{{ acceptance.acceptanceType || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="验收结论">{{ acceptance.conclusion ? conclusionText(acceptance.conclusion) : '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="验收时间" :span="2">{{ formatDateTime(acceptance.acceptedTime) }}</NDescriptionsItem>
          <NDescriptionsItem label="报告附件" :span="2"><HFileList :value="acceptance.reportFileId" :revision="acceptance.id" /></NDescriptionsItem>
          <NDescriptionsItem label="验收意见" :span="2">{{ acceptance.opinion || '--' }}</NDescriptionsItem>
        </NDescriptions>
      </NTabPane>

      <NTabPane name="archive" tab="档案">
        <NEmpty v-if="!archive" description="未建立修复档案" />
        <NDescriptions v-else label-placement="left" :column="2" bordered size="small">
          <NDescriptionsItem label="档案编号">{{ archive.archiveNo }}</NDescriptionsItem>
          <NDescriptionsItem label="归档状态">{{ archive.approvedTime ? `已归档 · ${formatDateTime(archive.approvedTime)}` : '未归档' }}</NDescriptionsItem>
          <NDescriptionsItem label="修复前现状" :span="2">{{ archive.beforeDescription || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="修复后现状" :span="2">{{ archive.afterDescription || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="材料清单" :span="2">{{ materialsText(archive.materialsUsed) || archive.materialsUsed || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="人员签名" :span="2"><template v-if="readSignatures(archive.restorerSignatures).length">
            <div v-for="(signature, index) in readSignatures(archive.restorerSignatures)" :key="index">
              <div>{{ signature.name || '姓名未留存' }} · {{ signature.certNo || '资质证书号未留存' }}</div>
              <HFileList :value="signature.signImageId" :revision="archive.id" />
            </div>
          </template>
          <template v-else>{{ archive.restorerSignatures ? '原记录未留存结构化签名影像，请核查原件' : '未留存' }}</template></NDescriptionsItem>
        </NDescriptions>
      </NTabPane>

      <NTabPane name="evaluation" tab="结果评估">
        <RestorationResultEvaluation :processes="processes" :archive="archive" :acceptance="acceptance" :contract="contract" />
      </NTabPane>

      <NTabPane name="report" tab="总结报告">
        <RestorationSummaryReport :project="project" :processes="processes" :archive="archive" :acceptance="acceptance" :contract="contract" />
      </NTabPane>
    </NTabs>
  </HPageForm>
</template>

<script setup lang="ts">
import { errorText } from '../utils/restorationError';
import { h, ref, resolveComponent, watch, onActivated, onDeactivated, onBeforeUnmount } from 'vue';
import { restorationDetailResponse } from '../utils/restorationDetailResponse';
import { NAlert, NButton, NDataTable, NDescriptions, NDescriptionsItem, NEmpty, NTabPane, NTabs } from 'naive-ui';
import { restorationApi } from '../api/restoration';
import { restorationProcessApi } from '../api/process';
import { restorationArchiveApi } from '../api/archive';
import { restorationContractApi } from '../api/contract';
import type {
  RestorationAcceptanceVo, RestorationArchiveVo, RestorationContractVo, RestorationDamageVo,
  RestorationProcessVo, RestorationVo,
} from '../typings';
import { formatDateTime, formatDateValue } from '@/utils/datetime';
import { materialsText } from '../utils/restorationReport';
import { qualificationLevelLabel } from '../utils/qualificationLevels';
import RestorationProcessLog from './RestorationProcessLog.vue';
import RestorationStageTimeline from './RestorationStageTimeline.vue';
import RestorationResultEvaluation from './RestorationResultEvaluation.vue';
import RestorationSummaryReport from './RestorationSummaryReport.vue';

const props = defineProps<{ restorationId?: string | null }>();
const show = defineModel<boolean>('show', { default: false });
const project = ref<RestorationVo>({});
const processes = ref<RestorationProcessVo[]>([]);
const damages = ref<RestorationDamageVo[]>([]);
const contract = ref<RestorationContractVo | null>(null);
const acceptance = ref<RestorationAcceptanceVo | null>(null);
const archive = ref<RestorationArchiveVo | null>(null);
const activeTab = ref('basic');
watch(() => props.restorationId, () => { activeTab.value = 'basic'; });

function readSignatures(raw?: string): { name?: string; certNo?: string; signImageId?: string }[] {
  if (!raw) return [];
  try {
    const rows: unknown = JSON.parse(raw);
    return Array.isArray(rows) ? rows.filter((row) => row && typeof row === 'object' && !Array.isArray(row)) : [];
  } catch { return []; }
}

const processColumns = [
  { title: '序号', key: 'sort', width: 60 },
  { title: '工序', key: 'stageName', ellipsis: { tooltip: true }, resizable: true, width: 180 },
  { title: '方法', key: 'methodName', ellipsis: { tooltip: true }, resizable: true, width: 160 },
  { title: '主操作人', key: 'operatorName', ellipsis: { tooltip: true }, resizable: true, width: 120 },
  { title: '协助人员', key: 'assistantNames', ellipsis: { tooltip: true }, resizable: true, width: 180 },
  { title: '开始', key: 'startTime', ellipsis: { tooltip: true }, resizable: true, width: 190, render: (row: RestorationProcessVo) => formatDateTime(row.startTime) },
  { title: '结束', key: 'endTime', ellipsis: { tooltip: true }, resizable: true, width: 190, render: (row: RestorationProcessVo) => formatDateTime(row.endTime) },
];
const HDictText = resolveComponent('HDictText');
const damageColumns = [
  { title: '病害类型', key: 'damageType', ellipsis: { tooltip: true }, resizable: true, width: 130,
    render: (row: RestorationDamageVo) => h(HDictText, { type: 'restoration_damage_type', value: row.damageType }) },
  { title: '严重程度', key: 'severity', ellipsis: { tooltip: true }, resizable: true, width: 100,
    render: (row: RestorationDamageVo) => h(HDictText, { type: 'restoration_damage_severity', value: row.severity }) },
  { title: '部位', key: 'location', ellipsis: { tooltip: true }, resizable: true, width: 160 },
  { title: '描述', key: 'description', ellipsis: { tooltip: true }, resizable: true, width: 240 },
];

/** 资质快照可能是「UNGRADED/证书号」之类，枚举段转中文。 */
function qualificationSnapshotText(raw?: string | null) {
  const text = String(raw || '').trim();
  if (!text) return '--';
  return text.split(/[\/|,，]/).map((part) => {
    const p = part.trim();
    if (!p) return p;
    const labeled = qualificationLevelLabel(p);
    return labeled && labeled !== '未登记' ? labeled : p;
  }).filter(Boolean).join(' / ');
}

const loading=ref(false),error=ref('');
let active=true,generation=0;
function clear() {project.value={};processes.value=[];damages.value=[];contract.value=null;acceptance.value=null;archive.value=null;}
function invalidate() {generation++;loading.value=false;error.value='';clear();}
async function reload() {
  invalidate();if(!active || !show.value)return;
  const id=props.restorationId;
  if(typeof id!=='string' || !id.trim()){error.value='请选择有效修复项目';return;}
  const token=generation;loading.value=true;
  const current=()=>active && show.value && token===generation && props.restorationId===id;
  try {
    const responses=await Promise.all([
      restorationApi.get(id),restorationProcessApi.list(id),restorationArchiveApi.damageList(id),
      restorationContractApi.getByRestoration(id),restorationArchiveApi.acceptanceGet(id),restorationArchiveApi.getByRestoration(id),
    ]);
    if(!current())return;
    const next=restorationDetailResponse(id,responses);
    project.value=next.project;processes.value=next.processes;damages.value=next.damages;
    contract.value=next.contract;acceptance.value=next.acceptance;archive.value=next.archive;
  } catch(e) {if(current()){clear();error.value=errorText(e, '修复资料读取失败，请重新读取');}}
  finally {if(current())loading.value=false;}
}
watch(()=>[show.value,props.restorationId] as const,()=>{void reload();},{immediate:true,flush:'sync'});
onActivated(()=>{active=true;});
onDeactivated(()=>{active=false;invalidate();show.value=false;});
onBeforeUnmount(()=>{active=false;invalidate();});

function levelText(value?: string) {
  return ({ FIRST: '一级', SECOND: '二级', THIRD: '三级', GENERAL: '一般', UNRATED: '未定级' } as Record<string, string>)[value || ''] || '--';
}
function approvalText(value?: string) {
  return ({ NATIONAL: '国家文物主管部门', PROVINCE: '省级文物主管部门', MUSEUM: '馆内' } as Record<string, string>)[value || ''] || '--';
}
function triggerText(value?: string) {
  return ({ MANUAL: '手工立项', PRESERVATION_AUTO: '预警自动派单', PRESERVATION_MANUAL: '预警人工派单', PATROL: '巡查发现' } as Record<string, string>)[value || ''] || '--';
}
function conclusionText(value?: string) {
  return ({ PASS: '通过', REVISION: '需整改复验', REJECT: '不通过' } as Record<string, string>)[value || ''] || '--';
}
function formatMoney(value?: number | null) {
  return value == null ? '--' : `¥${(value / 100).toFixed(2)}`;
}
</script>

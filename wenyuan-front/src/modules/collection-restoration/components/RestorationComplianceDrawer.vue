<template>
  <HPageView v-model:show="show" title="修复方案与实际实施原件" width="1120px">
    <NAlert v-if="readError" type="error">{{ readError }}</NAlert>
    <NSpin v-else :show="loading"
      ><NSpace v-if="!loading && data.restorationId" vertical :size="16">
        <CollectionArchiveIssues :data="data" />
        <NAlert type="info"
          >先保存修复项目基本方案，再登记对应方案、资质和场所安全原件。一级文物须国务院文物行政部门批准，二、三级须省级批准；馆内确认不能代替实际行政批文。实际送修通过提用办理，竣工不代表已经归库验收。</NAlert
        >
        <RestorationResponsibilityReplacementPanel v-if="show && data.restorationId" :key="data.restorationId" :project-id="data.restorationId" @open="open" @created="replacementCreated" />
        <NAlert v-if="data.replacementErrors?.length" type="error">{{ data.replacementErrors.join('；') }}</NAlert>
        <NAlert v-if="data.historicalTransferred" type="info">藏品已调出原馆；此处仅按原馆留存账查阅历史修复原件，旧项目不能继续办理。</NAlert>
        <NAlert v-if="data.supersededById" type="warning">原修复项目后续执行已终止。原批准仅保留历史事实，请在承接的新项目重新办理。</NAlert>
        <NButton v-if="canOperate && data.replacesId && !executionClosed" @click="emit('flow', projectId)">编辑并重新提交本项目审批</NButton>
        <NCard v-if="data.continuation" title="本项目续作来源">
          <NSpace vertical>
            <NText
              >原项目保留实际停止和未完成工序。本项目须登记新方案、适用批文并由指定负责人本人审批；实际开始后才承接原作业占用。</NText
            >
            <CollectionEvidenceReadView :original-url="originalUrl" :fields="restorationContinuationFields()" :data="data.continuation.request" :archive-files="stageFiles(data.continuationArchives, `CONTINUATION_${data.continuation.version}`)" />
            <NAlert v-if="data.continuation.request.responsibilityChange" type="info">新方案须使用此次清点移交指定的负责人，并重新办理适用批文及本人审批。原项目批准与未完成工序保留。</NAlert>
            <CollectionEvidenceReadView :original-url="originalUrl" v-if="data.continuation.request.responsibilityChange" :fields="restorationResponsibilityFields" :data="restorationResponsibilityForm(data.continuation.request.responsibilityChange)" :archive-files="stageFiles(data.continuationArchives, `CONTINUATION_${data.continuation.version}`)" />
            <NButton @click="open(data.continuation.previousProjectId)">查阅原停止项目</NButton>
            <NButton v-if="canOperate"
              v-permission="'COLLECTION_ADMIN,RESTORATION_ADMIN,RESTORATION_PROJECT_MANAGE,COLLECTION_STORAGE'"
              @click="emit('flow', projectId)"
              >办理本项目馆内审批</NButton
            >
            <NCollapse
              ><NCollapseItem
                v-for="a in data.continuationArchives || []"
                :key="a.id"
                :name="a.id"
                :title="`续作核查原件 · ${a.phase}`"
              >
                <CollectionCaseOriginalFiles v-for="f in a.files" :key="f.fileId" case-type="restoration" :case-id="projectId" :value="f.fileId" :archive-files="a.files" /> </NCollapseItem
            ></NCollapse>
          </NSpace>
        </NCard>
        <NAlert v-if="data.superseded" type="info"
          >本项目已由续作实际承接。后续修复和安全检查请进入续作项目办理。</NAlert
        >
        <NButton v-if="data.successorProjectId" @click="open(data.successorProjectId)">查阅续作项目</NButton>
        <NAlert v-if="data.continuationIssue" type="warning">{{ data.continuationIssue }}</NAlert>
        <NAlert v-if="data.workIssue" type="warning">{{ data.workIssue }}</NAlert>
        <NAlert v-if="latest?.sentStop && data.sentReturn?.received !== true" type="warning"
          >已实际送修，但本次修复未开工并已停止启动。原方案不能继续开工；请核实现场防护、事故处理和实际归还。安全复查不代表重新批准实施。</NAlert
        >
        <NAlert v-if="data.sentReturn?.issue" type="error">{{ data.sentReturn.issue }}</NAlert>
        <NAlert v-if="latest?.sentStop && data.sentReturn?.received === true" type="info">本次送修已形成实际归还接收记录。原方案不能重新开工；此处仅可补录归还前真实发生的检查。异常处理和后续保管请查阅归还案卷，原停止和送修原件继续保留。</NAlert>
        <NButton v-if="latest?.sentStop && data.sentReturn?.received === true && data.sentReturn?.caseId" v-permission="'COLLECTION_ADMIN,COLLECTION_SELECT,COLLECTION_STORAGE'" @click="openReceivedReturn">查阅实际归还案卷</NButton>
        <NAlert v-if="latest?.stopped" type="warning"
          >本次修复已实际停止，原工序与病害原件保留。请核实事故报告、现场防护及后续方案；安全复查不表示恢复实施，停止也不代表竣工或已经归还。</NAlert
        >
        <NAlert v-if="data.authorityIssue" type="warning">{{ data.authorityIssue }}</NAlert>
        <NSpace v-if="canOperate && !executionClosed" v-permission="'COLLECTION_ADMIN,RESTORATION_ADMIN,RESTORATION_PROJECT_MANAGE,COLLECTION_STORAGE'">
          <NButton
            v-if="data.flowStatus === 'DRAFT' && !data.flowInstanceId && data.state === 'DRAFT' && !latest?.sentStop"
            @click="edit('plan')"
            >登记本次方案及资质核查</NButton
          >
          <NButton v-if="latest && !latest.sentStop && !data.flowInstanceId && data.requiredAuthority" @click="edit('administrative')"
            >登记本方案实际行政批文</NButton
          >
          <NButton
            v-if="latest && data.flowStatus === 'PASS' && !latest.started && !latest.sentStop"
            @click="edit('start')"
            >登记实际开始修复</NButton
          >
          <NButton
            v-if="(latest?.started || latest?.sentStop) && !latest.completed && !data.superseded"
            @click="edit('safety')"
            >{{ data.sentReturn?.received === true ? '补录归还前安全检查原件' : '登记实际安全检查及处置' }}</NButton
          >
          <NButton
            v-if="latest && !latest.started && !latest.sentStop && data.sentRequisitionIds?.length"
            @click="edit('sentStop')"
            >登记送修后停止启动及保管</NButton
          >
          <NButton v-if="latest?.sentStop && data.sentReturn?.received !== true && !data.sentReturn?.issue" :loading="returnLoading" @click="openSentReturn">办理原送修归还</NButton>
          <NButton v-if="(latest?.stopped || latest?.sentStop) && !data.superseded" @click="edit('custody')"
            >{{ data.sentReturn?.received === true ? '补录归还前保管复核原件' : '登记停止后实际保管复核' }}</NButton
          >
          <NButton v-if="latest?.started && !latest.completed && !latest.stopped" @click="edit('stop')"
            >登记实际停止及保管</NButton
          >
          <NButton
            v-if="latest?.stopped && !data.superseded && data.continuationRevision"
            :loading="continuationLoading"
            @click="edit('continuation')"
            >建立或订正续作项目</NButton>
          <NButton v-if="latest?.stopped && !data.superseded && data.continuationRevision" :loading="continuationLoading" @click="edit('continuation-change')">负责人变更后建立续作</NButton>
          <NButton
            v-if="latest?.started && !latest.completed && !latest.stopped && !data.workIssue"
            @click="edit('complete')"
            >登记实际竣工原件</NButton
          >
        </NSpace>
        <NButton
          v-if="latest && !data.historicalTransferred && !executionClosed"
          v-permission="'COLLECTION_ADMIN,COLLECTION_INCIDENT_MANAGE'"
          :disabled="!restrictedRef"
          @click="restrictedRef?.open(projectId)"
          >办理受限送修及修复核查</NButton
        >
        <NSpace
          v-if="(latest?.started || latest?.sentStop) && !loading"
          v-permission="'COLLECTION_ADMIN,COLLECTION_INCIDENT_MANAGE'"
        >
          <NButton v-if="canOperate" @click="createIncident">登记事故报告与处置</NButton>
          <NButton v-for="caseId in data.incidentIds || []" :key="caseId" @click="incidentRef?.open(caseId)"
            >查阅事故案卷 {{ caseId }}</NButton
          >
        </NSpace>
        <NAlert v-if="latest?.completed" type="info"
          >已留存实际修复完成原件，请继续办理修复验收、归还点交与归库核查；环境预警需核实实际处理结果。</NAlert
        >
        <NAlert v-if="data.safetyOverdue && !data.historicalTransferred" type="warning"
          >已超过本方案的安全检查间隔，请核实当前场所和藏品状态，登记真实检查及处置原件。</NAlert
        >
        <NText v-if="data.nextSafetyCheckAt && !latest?.completed && !data.historicalTransferred"
          >按本方案下次安全检查应不晚于：{{
            formatLocalDateValue(data.nextSafetyCheckAt, 'YYYY-MM-DD HH:mm:ss')
          }}</NText
        >
        <NCollapse
          ><NCollapseItem
            v-for="p in [...(data.plans || [])].reverse()"
            :key="p.id"
            :name="p.id"
            :title="`方案原件第 ${p.version} 版`"
          >
            <NSpace vertical :size="14">
              <NAlert v-if="!p.matchesSource" type="warning"
                >项目方案、藏品或机构资质资料已变化，本版原件仍保留，不能继续作为新的实施依据。</NAlert
              >
              <RestorationPlanEvidenceView
                :project-id="projectId" :plan-evidence="p.content" :archive-files="stageFiles(p.archives, 'PLAN')" :archives="p.archives"
                :source-snapshot="p.sourceSnapshot"
                :administrative-evidence="p.administrative"
              />
              <NCard v-if="p.sentStop" title="实际送修后未开工，已停止启动">
                <CollectionEvidenceReadView :original-url="originalUrl"
                  :archive-files="stageFiles(p.archives, 'SENT_STOPPED')"
                  :fields="restorationSentStopFields"
                  :data="{ ...p.sentStop.record, requisitionId: p.sentStop.requisitionId }"
                />
                <NText>原送修点交和停止时资料永久保留；此记录不代表开工、竣工或归还。</NText>
              </NCard>
              <NCard v-if="p.started" title="实际开始"
                ><CollectionEvidenceReadView :original-url="originalUrl" :fields="restorationStartFields" :data="p.started" :archive-files="stageFiles(p.archives, 'STARTED')"
              /></NCard>
              <NCard v-for="record in p.safety || []" :key="record.id" :title="`实际安全检查 · ${record.id}`"
                ><CollectionEvidenceReadView :original-url="originalUrl"
                  :archive-files="stageFiles(p.archives, `SAFETY_${record.id}`)"
                  :fields="record.content.custody ? restorationCustodyFields : restorationSafetyFields"
                  :data="
                    record.content.custody
                      ? {
                          ...record.content,
                          ...record.content.custody,
                          custodyFileIds: record.content.custody.signedFileIds,
                        }
                      : record.content
                  "
              /></NCard>
              <NCard v-if="p.stopped" title="实际停止与保管">
                <CollectionEvidenceReadView :original-url="originalUrl" :fields="restorationStopFields" :data="p.stopped.record" :archive-files="stageFiles(p.archives, 'STOPPED')" />
                <NText
                  >停止时已有工序 {{ p.stopped.processRecords?.length || 0 }} 项、病害记录
                  {{ p.stopped.damageRecords?.length || 0 }} 项，原件已封存；未完成工序不自动填写结束时间。</NText
                >
              </NCard>
              <NCard v-if="p.completed" title="实际竣工"
                ><CollectionEvidenceReadView :original-url="originalUrl" :fields="restorationCompletionFields" :data="p.completed" :archive-files="stageFiles(p.archives, 'COMPLETED')"
              /></NCard>
              <NCollapse
                ><NCollapseItem v-for="a in p.archives || []" :key="a.id" :name="a.id" :title="`永久原件 · ${a.phase}`"
                  ><CollectionCaseOriginalFiles v-for="f in a.files" :key="f.fileId" case-type="restoration" :case-id="projectId" :value="f.fileId" :archive-files="a.files" /></NCollapseItem
              ></NCollapse>
            </NSpace> </NCollapseItem
        ></NCollapse> </NSpace
    ></NSpin>
  </HPageView>
  <template v-if="show && active && !loading && !readError">
  <CollectionReturnCaseDrawer ref="returnRef" @saved="afterSave" />
  <RestorationRestrictedDrawer ref="restrictedRef" @saved="afterSave" @flow="emit('flow', $event)" />
  <IncidentDrawer ref="incidentRef" @saved="afterSave" />
  <CollectionEvidenceForm ref="evidenceRef" :title="title" :fields="fields" :save="save" @saved="evidenceSaved" />
  </template>
</template>
<script setup lang="ts">
import { errorText } from '../utils/restorationError';
import CollectionArchiveIssues from '@/modules/collection/components/CollectionArchiveIssues.vue';
import { useRestorationAccountWindow } from '../composables/useRestorationAccountWindow';
import { usesRelicRules } from '@/modules/collection/utils/collectionNature';
import { ref, shallowRef, computed, nextTick, defineAsyncComponent, watch, onBeforeUnmount, onActivated, onDeactivated } from 'vue';
const IncidentDrawer = defineAsyncComponent(() => import('@/modules/collection/components/IncidentDrawer.vue'));
const incidentRef = ref<any>();
const RestorationRestrictedDrawer = defineAsyncComponent(() => import('./RestorationRestrictedDrawer.vue'));
const restrictedRef = ref<any>();
import { formatLocalDateValue } from '@/utils';
import { restorationApi } from '../api/restoration';
import { requisitionApi } from '@/modules/collection/api/requisition';
const CollectionReturnCaseDrawer = defineAsyncComponent(
  () => import('@/modules/collection/components/CollectionReturnCaseDrawer.vue'),
);
const returnRef = ref<any>(),
  returnLoading = ref(false);
let returnRequest = 0;
import { restorationOrgApi } from '../api/org';
import CollectionEvidenceForm from '@/modules/collection/components/CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from '@/modules/collection/components/CollectionEvidenceReadView.vue';
import CollectionCaseOriginalFiles from '@/modules/collection/components/CollectionCaseOriginalFiles.vue';
import RestorationPlanEvidenceView from './RestorationPlanEvidenceView.vue';
import {
  restorationResponsibilityFields,
  restorationResponsibilityForm,
  restorationPlanFields,
  restorationAdministrativeFields,
  restorationStartFields,
  restorationSafetyFields,
  restorationCompletionFields,
  restorationStopFields,
  restorationSentStopFields,
  restorationCustodyFields,
  restorationContinuationFields,
  restorationEvidencePayload,
} from '../utils/restorationComplianceFields';
import RestorationResponsibilityReplacementPanel from './RestorationResponsibilityReplacementPanel.vue';
const emit = defineEmits<{ (e: 'saved'): void; (e: 'flow', id: string): void }>();
const show = ref(false),
  loading = ref(false),
  data = ref<any>({}),
  evidenceRef = ref<any>(),
  stage = ref<
    'plan' | 'administrative' | 'start' | 'safety' | 'complete' | 'stop' | 'sentStop' | 'custody' | 'continuation' | 'continuation-change'
  >('plan');
let projectId = '', request = 0, editRequest = 0;
function stageFiles(archives: any[] | undefined, phase: string): any[] {
  const matching = Array.isArray(archives) ? archives.filter((archive) => archive.phase === phase) : [];
  return matching.length === 1 && Array.isArray(matching[0].files) ? matching[0].files : [];
}
function originalUrl(fileId: string) { return '/collection/restoration/' + encodeURIComponent(projectId) + '/original/' + encodeURIComponent(fileId); }
const readError = ref(''), active = ref(true);
const canOperate = computed(() => show.value && active.value && !loading.value && !readError.value && data.value.canOperate === true);
const save = shallowRef<(value:Record<string,any>)=>Promise<any>>(async()=>{throw new Error('请重新打开本次办理表单');});
const evidenceSaved = shallowRef<()=>void>(()=>{});
function owned() {return {id:projectId,orgId:data.value.orgId,epoch:request};}
function valid(o:{id:string,orgId:string,epoch:number},formToken?:number) {return active.value && show.value && !loading.value && !readError.value && request===o.epoch && projectId===o.id && data.value.orgId===o.orgId && (formToken===undefined || formToken===editRequest);}
function invalidate() {++request;++editRequest;++returnRequest;evidenceRef.value?.close?.();}
watch(show,v=>{if(!v)invalidate();},{flush:'sync'});
useRestorationAccountWindow(() => {
  show.value = false; invalidate(); projectId = ''; data.value = {}; readError.value = ''; loading.value = false;
  continuationLoading.value = false; returnLoading.value = false; continuationOrganizations.value = [];
  save.value = async () => { throw new Error('原修复办理窗口已关闭或切换，请重新核对'); };
  evidenceSaved.value = () => {};
});
onBeforeUnmount(()=>{active.value=false;invalidate();});
onDeactivated(()=>{active.value=false;invalidate();show.value=false;});
onActivated(()=>{active.value=true;});
const continuationLoading = ref(false);
const continuationOrganizations = ref<{ label: string; value: string }[]>([]);
const editingRelic = ref(false),
  editingReplacement = ref(false);
const executionClosed = computed(() => !!data.value.supersededById || !!data.value.replacementErrors?.length);
const latest = computed(() => data.value.plans?.at(-1));
const fields = computed(() =>
  stage.value === 'continuation' || stage.value === 'continuation-change'
    ? [...restorationContinuationFields(continuationOrganizations.value),...(stage.value === 'continuation-change' ? restorationResponsibilityFields : [])]
    : stage.value === 'custody'
      ? restorationCustodyFields
      : stage.value === 'sentStop'
        ? restorationSentStopFields
        : stage.value === 'plan'
          ? restorationPlanFields(editingRelic.value, editingReplacement.value)
          : stage.value === 'administrative'
            ? restorationAdministrativeFields
            : stage.value === 'start'
              ? restorationStartFields
              : stage.value === 'safety'
                ? restorationSafetyFields
                : stage.value === 'stop'
                  ? restorationStopFields
                  : restorationCompletionFields,
);
const title = computed(
  () =>
    ({
      plan: '登记方案及资质安全核查',
      administrative: '登记本方案实际行政批准',
      start: '登记实际开始及送修原件',
      safety: '登记实际安全检查及复查',
      complete: '登记实际竣工原件',
      stop: '登记实际停止与保管原件',
      sentStop: '登记送修后停止启动及实际保管',
      custody: '登记停止后实际保管复核',
      continuation: '依据实际停止建立或订正续作项目',
      'continuation-change': '依据真实责任变更和清点移交建立续作',
    })[stage.value],
);
async function replacementCreated(source: string, child: string) {
  if (!active.value || !show.value || projectId !== source || !child || child === source) return;
  if (await open(child)) emit('saved');
}
async function open(id: string) {
  if (!active.value || !id) return false;
  invalidate();projectId=id;data.value={};readError.value='';show.value=true;loading.value=true;
  continuationLoading.value=false;returnLoading.value=false;continuationOrganizations.value=[];
  const token=request;
  try {
    const response:any=await restorationApi.compliance(id);
    if(token!==request || !show.value || !active.value)return false;
    const value=response?.data;
    if(!value || value.restorationId!==id || !value.orgId || !Array.isArray(value.plans))throw new Error('修复原件编号或所属馆不一致，请重新读取');
    data.value=value;return true;
  } catch(error:any) {
    if(token===request && show.value)readError.value=errorText(error, '无法读取当前修复原件，请核对当前权限和来源后重试');
    return false;
  } finally {if(token===request)loading.value=false;}
}
async function reload() {return open(projectId);}
async function edit(kind: typeof stage.value) {
  if(!canOperate.value || executionClosed.value)return;
  const target=owned(), token=++editRequest;
  evidenceRef.value?.close?.();stage.value=kind;
  const planId=latest.value?.id || '', revision=data.value.sourceRevision, continuationRevision=data.value.continuationRevision;
  const change=data.value.continuation?.request?.responsibilityChange;
  editingRelic.value=usesRelicRules(data.value.nature);editingReplacement.value=!!latest.value;
  const isContinuation=kind==='continuation' || kind==='continuation-change';
  if(isContinuation) {
    if(!continuationRevision)return;
    continuationLoading.value=true;continuationOrganizations.value=[];
    try {
      const response:any=await restorationOrgApi.page({pageSize:0,enabled:'true',orgId:target.orgId});
      if(!valid(target,token))return;
      const result=response?.data?.data ?? response?.data ?? response;
      const rows=Array.isArray(result?.list)?result.list:Array.isArray(result)?result:null;
      if(!rows)throw new Error('未取得本馆有效修复机构清单');
      continuationOrganizations.value=rows.filter((r:any)=>r.orgId===target.orgId && r.enabled!==false).map((r:any)=>({label:r.name||r.id,value:r.id}));
    } catch(error:any) {
      if(valid(target,token))readError.value=errorText(error, '修复机构读取失败，请重新核对');
      return;
    } finally {if(valid(target,token) || (request===target.epoch && editRequest===token))continuationLoading.value=false;}
  }
  let created='';
  save.value=async(input:Record<string,any>)=>{
    if(!valid(target,token) || !canOperate.value)throw new Error('原修复办理窗口已关闭或切换，请重新核对');
    const v=restorationEvidencePayload(input);
    if(isContinuation) {
      const {newKeeperUserId,newLeaderUserId,handedAt,actualInventory,outstandingMatters,appointmentFileIds,handoverFileIds,...body}=v;
      const response:any=await restorationApi.continuation(target.id,{...body,previousPlanId:planId,responsibilityChange:kind==='continuation-change'?{keeperUserId:newKeeperUserId,leaderUserId:newLeaderUserId,handedAt,actualInventory,outstandingMatters,appointmentFileIds,handoverFileIds}:null},target.orgId,continuationRevision);
      if(typeof response?.data!=='string' || !response.data || response.data===target.id)throw new Error('未取得有效续作项目编号，请回原项目核对实际结果');
      created=response.data;return response;
    }
    if(kind==='plan')return restorationApi.planEvidence(target.id,{...v,sourceRevision:revision},target.orgId);
    if(kind==='administrative')return restorationApi.administrative(target.id,{planId,evidence:{...v,action:'APPROVAL'}},target.orgId);
    if(kind==='sentStop') {const {requisitionId,...record}=v;return restorationApi.sentStop(target.id,{requisitionId,record:{...record,planId}},target.orgId);}
    if(kind==='start')return restorationApi.start(target.id,{...v,planId},target.orgId);
    if(kind==='custody') {const {physicallyPresent,actualLocation,custodianUserId,custodyFileIds,...check}=v;return restorationApi.safetyCheck(target.id,{...check,custody:{physicallyPresent,actualLocation,custodianUserId,signedFileIds:custodyFileIds}},target.orgId,planId);}
    if(kind==='safety')return restorationApi.safetyCheck(target.id,v,target.orgId,planId);
    if(kind==='stop')return restorationApi.stop(target.id,{...v,planId},target.orgId);
    return restorationApi.complete(target.id,v,target.orgId,planId);
  };
  evidenceSaved.value=async()=>{if(valid(target,token) && await open(created || target.id))emit('saved');};
  await nextTick();
  if(valid(target,token))evidenceRef.value?.open(kind==='sentStop' && data.value.sentRequisitionIds?.length===1?{requisitionId:data.value.sentRequisitionIds[0]}:kind==='plan' && change?{keeperUserId:change.keeperUserId,leaderUserId:change.leaderUserId}:{});
}
async function openReceivedReturn() {
  const target=owned(),caseId=data.value.sentReturn?.caseId;
  if(!valid(target) || data.value.sentReturn?.received!==true || typeof caseId!=='string' || !caseId)return;
  await nextTick();
  if(valid(target) && data.value.sentReturn?.received===true && data.value.sentReturn?.caseId===caseId)returnRef.value?.open(caseId);
}
async function openSentReturn() {
  if(!canOperate.value || data.value.sentReturn?.received===true || data.value.sentReturn?.issue)return;
  const target=owned(),token=++returnRequest,requisitionId=latest.value?.sentStop?.requisitionId;
  if(!requisitionId)return;returnLoading.value=true;
  try {
    const response:any=await requisitionApi.returnBack(requisitionId);
    if(valid(target) && token===returnRequest && typeof response?.data==='string' && response.data)returnRef.value?.open(response.data);
  } finally {if(valid(target) && token===returnRequest)returnLoading.value=false;}
}
function createIncident() {
  const p = latest.value;
  const stopped = p?.stopped || p?.sentStop;
  const abnormal = !!stopped || p?.safety?.some((r: any) => !r.content.safe || r.content.unexpectedDamage);
  const damaged = stopped?.record?.unexpectedDamage || p?.safety?.some((r: any) => r.content.unexpectedDamage);
  incidentRef.value?.create({
    ...(abnormal ? { source: { bizType: 'COLLECTION_RESTORATION', bizId: projectId, recordId: p.id } } : {}),
    ...(stopped ? { relatedRestorationPlanIds: [p.id] } : {}),
    items: [{ collectionId: data.value.collectionId, ...(damaged ? { damaged: true } : {}) }],
  });
}
async function afterSave() {
  const target=owned();
  if(valid(target) && await reload())emit('saved');
}
defineExpose({ open });
</script>

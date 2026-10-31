<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="藏品归还办理"
    ><NAlert v-if="readError" type="error">{{ readError }}</NAlert>
    <NText v-else-if="readLoading">正在读取办理资料…</NText>
    <NSpace v-else vertical :size="18">
      <CollectionApprovalProgress v-if="show && form.id" :business-id="form.id" business-type="COLLECTION_RETURN" :fetch-flow="collectionReturnApi.flow" :revision="form" />
      <CollectionArchiveIssues :data="form" />
      <NAlert type="info"
        >先核实本次实际接收清单及保管位置，再逐件对照来源原件验收并由双方签署。缺件继续保留为未归还，不能勾选为已接收。</NAlert
      >
      <NForm v-if="editable" :model="form" label-width="170"
        ><NFormItem label="本次实际接收藏品" required
          ><NSelect v-model:value="ids" :options="options" multiple /></NFormItem
        ><NFormItem label="实际接收保管位置" required
          ><WarehouseTreeSelect v-model:value="form.toWarehouseId" /></NFormItem
        ><NFormItem label="实际经办人员" required><UserSelect v-model:value="form.operator" /></NFormItem
        ><NFormItem label="保管部门负责人" required><UserSelect v-model:value="form.keeperUserId" /></NFormItem
      ></NForm>
      <NSpace size="small" align="center">
        <NButton v-if="editable" type="primary" :loading="busy" @click="saveDraft">保存本次接收方案</NButton>
        <NButton
          v-if="canManage && form.executionState === 'PENDING' && ['DRAFT', 'AUDIT'].includes(form.flowStatus)"
          @click="inspect"
          >{{ form.inspection ? '补充或更正点交原件' : '登记逐件验收及签署' }}</NButton
        >
        <NButton
          v-if="canManage && form.flowStatus === 'DRAFT' && !form.flowInstanceId && form.inspection"
          type="primary"
          @click="submit"
          >提交保管负责人确认</NButton
        >
        <NButton v-if="canReceive" type="warning" @click="execute">登记实际接收保管</NButton>
        <CollectionActionMenu
          label="异常处理"
          v-if="
            canResolve ||
            (anomalies.length && (canManage || auth.hasPermission('COLLECTION_ADMIN,COLLECTION_INCIDENT_MANAGE')))
          "
        >
          <NButton
            quaternary
            v-if="anomalies.length"
            v-permission="'COLLECTION_ADMIN,COLLECTION_INCIDENT_MANAGE'"
            @click="createIncident"
            >登记事故报告与处置</NButton
          >
          <NButton quaternary v-if="canManage && anomalies.length" @click="edit('report')">补录原归还报告</NButton>
          <NButton quaternary v-if="canResolve" @click="edit('resolution')">登记异常处理结果</NButton>
        </CollectionActionMenu>
        <CollectionActionMenu label="更多">
          <NButton quaternary @click="flowRef?.open(form.id, '归还验收确认')">查看确认流程</NButton>
          <NButton quaternary v-if="canReceive" @click="rfid">RFID 核验归还</NButton>
          <NButton
            quaternary
            v-if="
              canManage && form.executionState === 'PENDING' && ['DRAFT', 'REJECT', 'CANCEL'].includes(form.flowStatus)
            "
            @click="voidDraft"
            >作废本次申请</NButton
          >
        </CollectionActionMenu>
      </NSpace>
      <NAlert v-if="unresolved.length" type="warning"
        >异常件可先实际接收并保管。解除待核查须关联同一来源、同一件藏品的事故案卷，由本件已生效的保管负责人和主要负责人本人完成确认。负责人离任时先登记实际清点移交并完成承接确认，原点交、审批及归还报告继续留存。</NAlert
      >
      <NSpace
        ><NButton v-for="incident in resolvedIncidentIds" :key="incident" @click="incidentRef?.open(incident)"
          >查看处理案卷 {{ incident }}</NButton
        ></NSpace
      >
      <NAlert v-if="responsibilityError" type="error">{{ responsibilityError }}</NAlert>
      <NSpace v-if="responsibility" vertical>
        <NButton v-if="canResolve" @click="edit('handover')">登记异常处理责任承接</NButton>
        <NText v-if="responsibility.pendingCollectionIds?.length">本次承接须覆盖 {{ responsibility.pendingCollectionIds.length }} 件未结异常。登记清点移交不代表异常已解除。</NText>
        <NCard v-for="record in responsibility.records" :key="record.id" :title="record.complete ? '已确认生效的责任承接' : '尚待本人确认的责任承接'">
          <CollectionEvidenceReadView :fields="returnResponsibilityFields" :data="record.content" :archive-files="(form.archives || []).filter((archive: any) => archive.phase === `RESPONSIBILITY_P_${record.id}`).flatMap((archive: any) => archive.files || [])" :original-url="responsibilityOriginalUrl(record.id, 'P')" />
          <NText>实际涉及藏品：{{ record.content.collectionIds.join('、') }}</NText>
          <NSpace v-for="(signature, role) in record.signatures" :key="role" vertical>
            <NText>{{ role === 'KEEPER' ? '保管负责人本人确认' : '主要负责人本人确认' }} · {{ signature.actorUserId }} · {{ formatLocalDateValue(signature.confirmedAt, 'YYYY-MM-DD HH:mm:ss') }}</NText>
            <CollectionEvidenceReadView :fields="returnResponsibilitySignatureFields" :data="signature.record" :archive-files="(form.archives || []).filter((archive: any) => archive.phase === `RESPONSIBILITY_${String(role)}_${record.id}`).flatMap((archive: any) => archive.files || [])" :original-url="responsibilityOriginalUrl(record.id, String(role))" />
          </NSpace>
          <NSpace v-if="canResolve && record.id === responsibility.previousId && !record.complete">
            <NButton v-if="canSignResponsibility(record, 'KEEPER')" @click="edit('signature', record.id, 'KEEPER')">保管负责人本人确认承接</NButton>
            <NButton v-if="canSignResponsibility(record, 'DIRECTOR')" @click="edit('signature', record.id, 'DIRECTOR')">主要负责人本人确认承接</NButton>
          </NSpace>
        </NCard>
      </NSpace>
      <ReturnEvidenceView v-if="form.id" :data="form" @open-source="openSource" />
      <NCollapse v-if="form.archives?.length"
        ><NCollapseItem
          v-for="a in form.archives"
          :key="a.id"
          :name="a.id"
          :title="`原件归档 · ${formatLocalDateValue(a.archivedAt, 'YYYY-MM-DD HH:mm:ss')}`"
          ><NSpace vertical
            ><HFileList :files="a.files" :original-url="archiveOriginalUrl(a)" :revision="[a.id, a.digest]" show-download-btn /></NSpace
          ><CollectionEvidenceReadView
            v-if="a.phase?.startsWith('REPORT_')"
            :fields="returnReportFields(form.sourceSnapshot?.items || [])"
            :data="archiveData(a)"
            :archive-files="a.files" :original-url="archiveOriginalUrl(a)"
          /><CollectionEvidenceReadView
            v-else-if="a.phase?.startsWith('RESOLUTION_')"
            :fields="returnResolutionFields(form.sourceSnapshot?.items || [])"
            :data="archiveData(a)"
            :archive-files="a.files" :original-url="archiveOriginalUrl(a)"
          /><ReturnEvidenceView
            v-else-if="!a.phase?.startsWith('INCIDENT_CLEARANCE_') && !a.phase?.startsWith('RESPONSIBILITY_')"
            :data="archiveData(a).proposal || archiveData(a)" :archive-files="a.files" :original-url="archiveOriginalUrl(a)" @open-source="openSource"
          /><NText v-if="a.phase?.startsWith('INCIDENT_CLEARANCE_')"
            >本次解限案卷：{{ archiveData(a).incidentProof?.incidentId }}；对应处理结果：{{
              archiveData(a).incidentProof?.handlingId
            }}</NText
          ></NCollapseItem
        ></NCollapse
      >
    </NSpace></HPageForm
  >
  <template v-if="show && active && !readLoading && !readError">
    <ReturnInspectionForm ref="inspectionRef" @saved="reload" /><CollectionEvidenceForm
      ref="evidenceRef"
      :title="evidenceTitle"
      :fields="fields"
      :save="saveEvidence"
      @saved="evidenceSaved"
    /><IncidentDrawer ref="incidentRef" @saved="reload" /><FlowFormDrawer
      ref="flowRef"
      :fetch-fn="collectionReturnApi.flow"
      :cancel-api="collectionReturnApi.cancel"
      @success="reload"
    />
    <CollectionLoanCaseDrawer ref="loanSourceRef" />
    <CollectionRequisitionCaseDrawer ref="requisitionSourceRef" />
  </template>
</template>
<script setup lang="ts">
import CollectionApprovalProgress from './CollectionApprovalProgress.vue';
import CollectionActionMenu from './CollectionActionMenu.vue';
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { computed, defineAsyncComponent, ref, shallowRef, nextTick, watch, onBeforeUnmount, onActivated, onDeactivated } from 'vue';
import { useMessage, useDialog } from 'naive-ui';
import { useRouter } from 'vue-router';
import { formatLocalDateValue } from '@/utils';
import { useAuthStore } from '@/store/modules/auth';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import WarehouseTreeSelect from './WarehouseTreeSelect.vue';
import ReturnInspectionForm from './ReturnInspectionForm.vue';
import ReturnEvidenceView from './ReturnEvidenceView.vue';
const CollectionLoanCaseDrawer = defineAsyncComponent(() => import('./CollectionLoanCaseDrawer.vue'));
const CollectionRequisitionCaseDrawer = defineAsyncComponent(() => import('./CollectionRequisitionCaseDrawer.vue'));
import IncidentDrawer from './IncidentDrawer.vue';
import type { EvidenceField } from '../utils/evidenceFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import FlowFormDrawer from '@/modules/flow/components/FlowFormDrawer.vue';
import { collectionReturnApi } from '../api/return';
import {
  returnResponsibilityFields,
  returnResponsibilitySignatureFields,
  returnItemAbnormal,
  returnReportFields,
  returnResolutionFields,
  latestReturnInspectionArchiveId,
  returnEvidencePayload,
} from '../utils/returnEvidenceFields';
const emit = defineEmits<{ (e: 'saved'): void }>(),
  message = useMessage(),
  dialog = useDialog(),
  router = useRouter(),
  auth = useAuthStore();
const show = ref(false),
  busy = ref(false),
  readLoading = ref(false),
  readError = ref(''),
  form = ref<any>({}),
  ids = ref<string[]>([]),
  inspectionRef = ref<any>(),
  flowRef = ref<any>(),
  evidenceRef = ref<any>(),
  incidentRef = ref<any>(),
  loanSourceRef = ref<any>(),
  requisitionSourceRef = ref<any>(),
  kind = ref('report');
function openSource(source: { bizType: string; bizId: string }) {
  if (!show.value || !active.value || readLoading.value || readError.value ||
      source.bizType !== form.value.sourceBizType || source.bizId !== form.value.sourceBizId) {
    message.warning('来源案卷与当前归还单不一致，请重新核对');
    return;
  }
  if (source.bizType === 'COLLECTION_LOAN') void loanSourceRef.value?.open(source.bizId);
  else if (source.bizType === 'COLLECTION_REQUISITION') void requisitionSourceRef.value?.open(source.bizId);
}
const active = ref(true), responsibility = ref<any>(), responsibilityError = ref('');
const canManage = computed(() => !form.value.evidenceErrors?.length && auth.hasPermission('COLLECTION_ADMIN,COLLECTION_STORAGE'));
const canResolve = computed(() => canManage.value && show.value && active.value && !readLoading.value && !readError.value &&
  form.value.executionState === 'EXECUTED' && unresolved.value.length > 0 && responsibility.value?.canOperate === true && !responsibilityError.value);
function canSignResponsibility(record: any, role: string) {
  if (!canResolve.value || !record || record.complete || record.id !== responsibility.value?.previousId || record.signatures?.[role]) return false;
  const actor = role === 'KEEPER' ? record.content?.keeperUserId : role === 'DIRECTOR' ? record.content?.directorUserId : '';
  return Boolean(auth.userInfo?.id && actor === auth.userInfo.id && (role !== 'DIRECTOR' || record.signatures?.KEEPER));
}
const evidenceTitle = computed(() => ({ report: '登记实际报案或报送', resolution: '登记双方说明、调查及处理完成原件', handover: '登记归还异常实际清点移交', signature: '本人确认异常处理责任承接' }[kind.value] || '核对归还原件'));
const editable = computed(
  () =>
    canManage.value &&
    !readLoading.value &&
    !readError.value &&
    form.value.flowStatus === 'DRAFT' &&
    !form.value.flowInstanceId &&
    !form.value.inspection &&
    form.value.executionState === 'PENDING',
);
const canReceive = computed(
  () => canManage.value && form.value.flowStatus === 'PASS' && form.value.executionState === 'PENDING' && !!form.value.inspection,
);
const options = computed(() =>
  (form.value.sourceSnapshot?.items || []).map((i: any) => ({
    label: i.content?.formalName || i.collectionId,
    value: i.collectionId,
  })),
);
const anomalies = computed(() =>
  (form.value.sourceSnapshot?.items || []).filter((i: any) =>
    form.value.inspection?.items?.some((v: any) => v.collectionId === i.collectionId && returnItemAbnormal(v)),
  ),
);
const unresolved = computed(() =>
  anomalies.value.filter(
    (i: any) =>
      !String(form.value.acceptedCollectionIds || '')
        .split(',')
        .includes(i.collectionId),
  ),
);
const fields = ref<EvidenceField[]>([]);
const resolvedIncidentIds = computed<string[]>(() => [
  ...new Set<string>(
    (form.value.complianceRecords || [])
      .filter((r: any) => r.kind === 'RESOLUTION' && r.content?.incidentId)
      .map((r: any) => r.content.incidentId),
  ),
]);
let generation = 0;
const saveEvidence = shallowRef<(value:any) => Promise<any>>(async () => { throw new Error('请先打开本次办理表单'); });
const evidenceSaved = shallowRef<() => void>(() => {});
function valid(target:{id:string,orgId:string,epoch:number}) {
  return active.value && show.value && generation === target.epoch && form.value.id === target.id && form.value.orgId === target.orgId && !readLoading.value && !readError.value;
}
function target() { return {id:form.value.id,orgId:form.value.orgId,epoch:generation}; }
function invalidate() { ++generation; evidenceRef.value?.close?.(); }
watch(() => auth.userInfo?.id, () => {
  invalidate();
  show.value = false;
  form.value = {};
  ids.value = [];
  responsibility.value = undefined;
  responsibilityError.value = '';
  readLoading.value = false;
  readError.value = '';
  busy.value = false;
}, { flush: 'sync' });
onActivated(() => { active.value = true; });
onDeactivated(() => { active.value = false; invalidate(); show.value = false; });
watch(
  show,
  (value) => {
    if (!value) invalidate();
  },
  { flush: 'sync' },
);
onBeforeUnmount(() => { active.value = false; invalidate(); });
async function open(id: string) {
  if (!active.value) return false;
  invalidate();
  const current = generation;
  busy.value = false;
  responsibility.value = undefined;
  responsibilityError.value = '';
  form.value = {};
  ids.value = [];
  readError.value = '';
  readLoading.value = true;
  show.value = true;
  try {
    const data = ((await collectionReturnApi.get(id)) as any).data;
    if (current !== generation || !show.value) return false;
    if (!data || data.id !== id || !data.orgId) throw new Error('办理记录编号或所属馆不一致，请重新读取');
    form.value = data;
    ids.value = String(data.collectionIds || '')
      .split(',')
      .filter(Boolean);
    if (data.executionState === 'EXECUTED' && data.inspection) {
      try {
        const context = ((await collectionReturnApi.responsibility(id, data.orgId)) as any).data;
        if (current !== generation || !show.value || !active.value) return false;
        if (!context || context.id !== id || context.orgId !== data.orgId || !context.receivedDigest || !Array.isArray(context.pendingCollectionIds) || !Array.isArray(context.records) || !Array.isArray(context.responsibleItems)) throw new Error('责任承接依据与当前归还单不一致');
        responsibility.value = context;
      } catch (error:any) {
        if (current === generation && show.value) responsibilityError.value = error?.message || '无法核对当前异常责任，请核实原接收及处理档案后重试';
      }
    }
    return current === generation && show.value && active.value;
  } catch (error: any) {
    if (current === generation && show.value)
      readError.value = error?.message || '读取失败，请核对当前授权和原件归属后重新打开';
    return false;
  } finally {
    if (current === generation) readLoading.value = false;
  }
}
async function reload() {
  if (!(await open(form.value.id))) return false;
  emit('saved');
  return true;
}
async function persist() {
  const owned = target();
  if (!valid(owned)) return false;
  if (!ids.value.length || !form.value.toWarehouseId || !form.value.operator || !form.value.keeperUserId) {
    message.warning('请明确实际接收清单、库位、经办人和保管负责人');
    return false;
  }
  await collectionReturnApi.save({ ...form.value, collectionIds: ids.value.join(',') });
  return valid(owned) ? await reload() : false;
}
async function saveDraft() {
  const owned = target();
  busy.value = true;
  try {
    await persist();
  } finally {
    if (generation === owned.epoch) busy.value = false;
  }
}
async function inspect() {
  const owned = target();
  if (!valid(owned)) return;
  if (editable.value && !(await persist())) return;
  if (show.value && active.value && form.value.id === owned.id && form.value.orgId === owned.orgId) inspectionRef.value?.open(form.value);
}
async function submit() {
  const owned = target();
  if (!valid(owned)) return;
  busy.value = true;
  try {
    await collectionReturnApi.submit(owned.id);
    if (valid(owned)) await reload();
  } finally {
    if (generation === owned.epoch) busy.value = false;
  }
}
async function execute() {
  const owned = target();
  const ok = await new Promise<boolean>((resolve) =>
    dialog.warning({
      title: '确认登记实际接收保管',
      content:
        '请核对本批实物已经到馆并由双方实际签署。正常件完成归库，存在差异的实物进入待核查并继续锁定，需另行调查及报告。',
      positiveText: '确认实际接收',
      negativeText: '返回核对',
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false),
      onMaskClick: () => resolve(false),
    }),
  );
  if (!ok || !valid(owned)) return;
  busy.value = true;
  try {
    await collectionReturnApi.execute(owned.id);
    if (valid(owned)) await reload();
  } finally {
    if (generation === owned.epoch) busy.value = false;
  }
}
async function edit(value: string, recordId = '', role = '') {
  if (!canManage.value || (value !== 'report' && !canResolve.value)) return;
  if (value === 'signature' && !canSignResponsibility(responsibility.value?.records.find((record: any) => record.id === recordId), role)) return;
  const owned = target();
  const context = responsibility.value ? JSON.parse(JSON.stringify(responsibility.value)) : undefined;
  const reportInspectionArchiveId = value === 'report' ? latestReturnInspectionArchiveId(form.value) || '' : '';
  if (value === 'report' && !reportInspectionArchiveId) {
    message.warning('请重新打开案卷核对当前点交原件版本后登记报告');
    return;
  }
  kind.value = value;
  fields.value = value === 'report' ? returnReportFields(anomalies.value) : value === 'resolution' ? returnResolutionFields(unresolved.value) : value === 'handover' ? returnResponsibilityFields : returnResponsibilitySignatureFields;
  const first = context?.responsibleItems?.[0];
  const initial = value === 'resolution' ? { collectionId: first?.collectionId, keeperUserId: first?.keeperUserId, leaderUserId: first?.directorUserId } : value === 'report' ? { kind: 'INCIDENT' } : {};
  saveEvidence.value = async (input:any) => {
    if (!valid(owned)) throw new Error('原办理窗口已关闭或切换，请重新核对');
    const payload = returnEvidencePayload(input);
    if (value === 'report') return collectionReturnApi.report(owned.id, payload, reportInspectionArchiveId);
    if (value === 'resolution') {
      const item = context.responsibleItems.find((i:any) => i.collectionId === payload.collectionId);
      if (!item) throw new Error('本件不在打开表单时的未结异常清单中');
      return collectionReturnApi.resolve(owned.id, {...payload, expectedResponsibilityId:item.responsibilityId ?? null}, owned.orgId);
    }
    if (value === 'handover') return collectionReturnApi.registerResponsibility(owned.id, {...payload, previousId:context.previousId ?? null, expectedEffectiveId:context.effectiveId ?? null, receivedDigest:context.receivedDigest, collectionIds:[...context.pendingCollectionIds]}, owned.orgId);
    return collectionReturnApi.signResponsibility(owned.id, recordId, role, payload, owned.orgId);
  };
  evidenceSaved.value = () => { if (valid(owned)) void reload(); };
  await nextTick();
  if (valid(owned)) evidenceRef.value?.open(initial);
}
function createIncident() {
  incidentRef.value?.create({
    source: { bizType: 'COLLECTION_RETURN', bizId: form.value.id },
    items: (form.value.inspection?.items || []).filter(returnItemAbnormal).map((i: any) => ({
      collectionId: i.collectionId,
      damaged: i.damaged,
      security: i.securityIncident,
      findings: i.anomalyDescription,
      imageFileIds: i.imageFileIds,
    })),
  });
}
async function voidDraft() {
  const owned = target();
  const confirmed = await new Promise<boolean>((resolve) =>
    dialog.warning({
      title: '确认作废未接收申请',
      content: '本申请将停止办理，已登记的点交、报告及档案继续保留。需要重新核实清单时，可从来源单另建归还申请。',
      positiveText: '确认作废申请',
      negativeText: '继续核对',
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false),
      onMaskClick: () => resolve(false),
    }),
  );
  if (!confirmed || !valid(owned)) return;
  await collectionReturnApi.voidRecord(owned.id);
  if (valid(owned)) await reload();
}
function rfid() {
  void router.push({ path: '/collection/rfid-desk', query: { type: 'RETURN_IN', businessId: form.value.id } });
}
function archiveData(a: any) {
  try {
    return JSON.parse(a.snapshot);
  } catch {
    return {};
  }
}
const archiveOriginalUrl = (archive: any) => (fileId: string) =>
  archive.files?.some((file: any) => file.fileId === fileId)
    ? `/collection/return/${encodeURIComponent(form.value.id)}/original/${encodeURIComponent(archive.id)}/${encodeURIComponent(fileId)}`
    : '';
const responsibilityOriginalUrl = (recordId: string, stage: string) => (fileId: string) => {
  const archive = form.value.archives?.find((item: any) => item.phase === `RESPONSIBILITY_${stage}_${recordId}`);
  return archive ? archiveOriginalUrl(archive)(fileId) : '';
};
defineExpose({ open });
</script>

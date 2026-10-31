<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="藏品借用办理"
    ><NAlert v-if="readError" type="error">{{ readError }}</NAlert>
    <NText v-else-if="readLoading">正在读取办理资料…</NText>
    <NSpace v-else vertical :size="18">
      <CollectionApprovalProgress v-if="show && form.id" :business-id="form.id" business-type="COLLECTION_LOAN" :fetch-flow="loanApi.flow" :revision="form" />
      <CollectionArchiveIssues :issues="form.evidenceErrors" />
      <NAlert type="warning">借用须由实际借用单位依法办理。不得以借用名义赠与、出租、出售、抵押或质押藏品；工作人员不得个人借用或非法侵占藏品。请核实实际目的、单位身份及协议原件。</NAlert>
      <NAlert type="info"
        >先核实完整借用方案及每件保存状况，再登记行政原件。实际出库后，请在本单持续办理备案、双方检查、催还及报告。</NAlert
      >
      <NSpace v-if="form.id" v-permission="'COLLECTION_ADMIN,COLLECTION_CARE_MANAGE'"
        ><NButton
          v-for="piece in selectedIds"
          :key="piece"
          @click="fragilityRef?.open(piece, 'COLLECTION_LOAN', form.id)"
          >{{ piece }}：使用保护评估</NButton
        ></NSpace
      >
      <NForm v-if="editable" :model="form" label-width="150" label-placement="left">
        <NFormItem label="借用藏品清单" required
          ><CollectionSelect v-model:value="selectedIds" multiple life-status="IN_STORE" /></NFormItem
        ><NFormItem label="展览或借用事由" required><NInput v-model:value="form.title" /></NFormItem
        ><NFormItem label="借用单位全称" required><NInput v-model:value="form.borrower" /></NFormItem
        ><NFormItem label="协议编号"><NInput v-model:value="form.protocolNo" /></NFormItem
        ><NFormItem label="预计借出日期" required><NDatePicker v-model:value="form.loanDate" type="date" /></NFormItem
        ><NFormItem label="经办人" required><UserSelect v-model:value="form.operator" /></NFormItem
        ><NFormItem label="一级文物复核人"><UserSelect v-model:value="form.reviewer" /></NFormItem
        ><NFormItem label="协议及其他附件"
          ><HUploadInput v-model:value="form.attachmentIds" @upload-finish="form.attachmentIds = $event.id"
        /></NFormItem>
      </NForm>
      <NSpace size="small" align="center">
        <NButton v-if="editable" type="primary" :loading="busy" @click="saveDraft">保存借用草稿</NButton>
        <NButton
          v-if="form.id && editable && form.flowStatus === 'DRAFT' && !form.flowInstanceId"
          :loading="busy"
          @click="submit"
          >提交责任人确认</NButton
        >
        <NButton v-if="canOut && form.handover" type="warning" @click="execute">登记实际出库</NButton>
        <CollectionActionMenu label="借用材料" v-if="(form.id && (editable || !form.outExecuted)) || canOut">
          <NButton quaternary v-if="form.id && editable" @click="edit('dossier')">完整借用及监管方案</NButton>
          <NButton quaternary v-if="form.id && !form.outExecuted" @click="edit('administrative')"
            >登记实际行政原件</NButton
          >
          <NButton quaternary v-if="canOut" @click="handoverRef?.open(form)">{{
            form.handover ? '补充或更正点交' : '登记双方逐件点交'
          }}</NButton>
        </CollectionActionMenu>
        <CollectionActionMenu
          label="跟踪与备案"
          v-if="
            form.outExecuted ||
            form.extensionAvailable === true ||
            form.portAvailable === true ||
            form.overseasFilingAvailable === true ||
            form.exhibitionEndAvailable === true
          "
        >
          <NButton quaternary v-if="form.outExecuted" @click="edit('administrative')">登记出借后备案</NButton>
          <NButton quaternary v-if="form.outExecuted" @click="edit('monitoring')">检查、催还与报告</NButton>
          <NButton quaternary v-if="form.extensionAvailable === true" @click="edit('extension')"
            >登记出境展览延期</NButton
          >
          <NButton quaternary v-if="form.portAvailable === true" @click="edit('port')">登记出入境口岸手续</NButton>
          <NButton quaternary v-if="form.overseasFilingAvailable === true" @click="edit('overseasFiling')"
            >协议备案与展览结项</NButton
          >
          <NButton quaternary v-if="form.exhibitionEndAvailable === true" @click="edit('exhibitionEnd')"
            >登记展览实际结束</NButton
          >
        </CollectionActionMenu>
        <CollectionActionMenu label="更多" v-if="form.id || (canOut && form.handover)">
          <NButton quaternary v-if="form.id" @click="flowRef?.open(form.id, '藏品借用确认')">查看审批</NButton>
          <NButton quaternary v-if="canOut && form.handover" @click="openRfid">RFID 核验出库</NButton>
        </CollectionActionMenu>
      </NSpace>
      <NCard v-if="form.dossier && editable" title="逐件借用评估" size="small"
        ><NSpace
          ><NButton
            v-for="item in form.baseline?.items || []"
            :key="item.collectionId"
            @click="editAssessment(item.collectionId)"
            >{{ item.content?.formalName || item.collectionId }}：{{
              form.dossier.assessments?.some((a: any) => a.collectionId === item.collectionId)
                ? '核对已登记评估'
                : '登记评估'
            }}</NButton
          ></NSpace
        ></NCard
      >
      <LoanEvidenceView v-if="form.id" :data="form" :archive-files="currentOriginalFiles" :original-url="currentOriginalUrl" />
      <NCollapse v-if="form.archives?.length"
        ><NCollapseItem
          v-for="archive in form.archives"
          :key="archive.id"
          :name="archive.id"
          :title="`归档材料 · ${formatLocalDateValue(archive.archivedAt, 'YYYY-MM-DD HH:mm:ss')}`"
          ><NSpace vertical
            ><HFileList :files="archive.files" :original-url="archiveOriginalUrl(archive)" :revision="[archive.id, archive.digest]" show-download-btn /></NSpace
          ><CollectionEvidenceReadView
            v-if="archive.phase?.startsWith('ADMINISTRATIVE_')"
            :fields="loanAdministrativeFields"
            :data="rawArchive(archive)" :archive-files="archive.files" :original-url="archiveOriginalUrl(archive)" /><CollectionEvidenceReadView
            v-else-if="archive.phase?.startsWith('MONITORING_')"
            :fields="loanMonitoringFields(form.monitoringRecords, form.baseline?.items)"
            :data="rawArchive(archive)" :archive-files="archive.files" :original-url="archiveOriginalUrl(archive)" /><CollectionEvidenceReadView
            v-else-if="archive.phase?.startsWith('OVERSEAS_EXTENSION_')" :fields="loanExtensionFields(form.administrativeEvidence)"
            :data="rawArchive(archive).request || {}" :archive-files="archive.files" :original-url="archiveOriginalUrl(archive)" /><CollectionEvidenceReadView
            v-else-if="archive.phase?.startsWith('OVERSEAS_PORT_')" :fields="loanPortFields(form.administrativeEvidence,form.overseasPorts,form.baseline?.items,rawArchive(archive).request?.stage)"
            :data="loanEvidenceInitial(rawArchive(archive).request || {})" :archive-files="archive.files" :original-url="archiveOriginalUrl(archive)" /><CollectionEvidenceReadView
            v-else-if="archive.phase?.startsWith('OVERSEAS_FILING_')" :fields="loanOverseasFilingFields([],rawArchive(archive).request?.kind)"
            :data="rawArchive(archive).request || {}" :archive-files="archive.files" :original-url="archiveOriginalUrl(archive)" /><CollectionEvidenceReadView
            v-else-if="archive.phase?.startsWith('EXHIBITION_END_')" :fields="loanExhibitionEndFields()"
            :data="rawArchive(archive).request || {}" :archive-files="archive.files" :original-url="archiveOriginalUrl(archive)" /><LoanEvidenceView
            v-else :data="archiveData(archive)" :archive-files="archive.files" :original-url="archiveOriginalUrl(archive)" /></NCollapseItem
      ></NCollapse> </NSpace
  ></HPageForm>
  <template v-if="!readLoading && !readError">
    <CollectionEvidenceForm
      ref="evidenceRef"
      :title="evidenceTitle"
      :fields="fields"
      :save="saveEvidence"
      @saved="reload"
      @change="evidenceChanged"
    /><LoanHandoverForm ref="handoverRef" @saved="reload" /><FlowFormDrawer
      ref="flowRef"
      :fetch-fn="loanApi.flow"
      :cancel-api="loanApi.cancel"
      @success="reload"
    />
    <CollectionFragilityDrawer ref="fragilityRef" />
  </template>
</template>
<script setup lang="ts">
import CollectionApprovalProgress from './CollectionApprovalProgress.vue';
import CollectionActionMenu from './CollectionActionMenu.vue';
const CollectionFragilityDrawer = defineAsyncComponent(() => import('./CollectionFragilityDrawer.vue'));
const fragilityRef = ref<any>();
import { computed, defineAsyncComponent, ref, nextTick, watch, onBeforeUnmount, onDeactivated } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage, useDialog } from 'naive-ui';
import { formatLocalDateValue } from '@/utils';
import CollectionSelect from './CollectionSelect.vue';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import LoanHandoverForm from './LoanHandoverForm.vue';
import LoanEvidenceView from './LoanEvidenceView.vue';
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import FlowFormDrawer from '@/modules/flow/components/FlowFormDrawer.vue';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import { loanApi } from '../api/loan';
import {
  loanDossierFields,
  loanExtensionFields,
  loanPortFields,
  loanOverseasFilingFields,
  loanExhibitionEndFields,
  loanPortPayload,
  loanAssessmentFields,
  loanAdministrativeFields,
  loanMonitoringFields,
  loanEvidenceInitial,
  loanEvidencePayload,
} from '../utils/loanEvidenceFields';
const emit = defineEmits<{ (e: 'saved'): void }>(),
  message = useMessage(),
  dialog = useDialog(),
  router = useRouter();
const show = ref(false),
  busy = ref(false),
  readLoading = ref(false),
  readError = ref(''),
  form = ref<any>({}),
  selectedIds = ref<string[]>([]),
  kind = ref('dossier'),
  assessmentId = ref(''),
  evidenceRef = ref<any>(),
  handoverRef = ref<any>(),
  flowRef = ref<any>();
const extensionContext = ref<{id:string;orgId:string;sourceRevision:string;previousId:string|null;generation:number;mode:string;filingPreviousIds:Record<string,string>;endBasis:any}|null>(null);
const dossierPreview = ref<Record<string, any>>({});
const editable = computed(
  () =>
    !readLoading.value &&
    !readError.value &&
    (!form.value.id ||
      (form.value.proposalEditable !== false && form.value.flowStatus === 'DRAFT' && !form.value.flowInstanceId)),
);
const canOut = computed(() => !form.value.evidenceErrors?.length && form.value.flowStatus === 'PASS' && !form.value.outExecuted && !form.value.returned);
const currentOriginalFiles = computed(() => (form.value.archives || []).flatMap((archive: any) => archive.files || []));
const currentOriginalUrl = (fileId: string): string => {
  const archive = [...(form.value.archives || [])].reverse().find((row: any) => row.files?.some((file: any) => file.fileId === fileId));
  return archive ? archiveOriginalUrl(archive)(fileId) : '';
};
const evidenceTitle = computed(
  () =>
    ({
      dossier: '完整借用方案与运输监管',
      assessment: '逐件保存状况及必要性风险评估',
      administrative: '依据实际文书登记报批或备案',
      monitoring: '登记实际检查、催还和报告',
      extension: '登记原机关延期批准及双方补充协议',
      port: '登记实际出入境审核及海关手续',
      overseasFiling:'登记正式协议备案或展览结项报送',
      exhibitionEnd:'核实展览实际结束事实',
    })[kind.value] || '借用原件',
);
const fields = computed(() =>
  kind.value === 'exhibitionEnd' ? loanExhibitionEndFields(form.value.exhibitionEnds) : kind.value === 'overseasFiling' ? loanOverseasFilingFields(form.value.overseasFilings,dossierPreview.value.kind,true,form.value.exhibitionEnds?.at(-1)) : kind.value === 'port' ? loanPortFields(form.value.administrativeEvidence,form.value.overseasPorts,form.value.baseline?.items,dossierPreview.value.stage) : kind.value === 'extension' ? loanExtensionFields(form.value.administrativeEvidence) : kind.value === 'dossier'
    ? loanDossierFields(dossierPreview.value)
    : kind.value === 'assessment'
      ? loanAssessmentFields(form.value.dossier?.exhibitionOverseas)
      : kind.value === 'administrative'
        ? loanAdministrativeFields
        : loanMonitoringFields(form.value.monitoringRecords, form.value.baseline?.items),
);
let generation = 0;
watch(
  show,
  (value) => {
    if (!value) { ++generation; extensionContext.value=null; evidenceRef.value?.close?.(); }
  },
  { flush: 'sync' },
);
onDeactivated(() => { extensionContext.value=null; evidenceRef.value?.close?.(); });
onBeforeUnmount(() => {
  ++generation;
});
async function open(id?: string, initial: Record<string, any> = {}) {
  extensionContext.value=null; evidenceRef.value?.close?.();
  const current = ++generation;
  form.value = {};
  selectedIds.value = [];
  readError.value = '';
  readLoading.value = true;
  show.value = true;
  try {
    const data = id ? ((await loanApi.get(id)) as any).data : { ...initial, flowStatus: 'DRAFT' };
    if (current !== generation || !show.value) return false;
    if (!data) throw new Error('办理记录不存在或当前不可读取');
    form.value = data;
    selectedIds.value = String(data.collectionIds || '')
      .split(',')
      .filter(Boolean);
    return true;
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
const payload = () => ({ ...form.value, collectionIds: selectedIds.value.join(',') });
async function persist() {
  if (
    !selectedIds.value.length ||
    !form.value.title?.trim() ||
    !form.value.borrower?.trim() ||
    !form.value.operator ||
    form.value.loanDate == null
  ) {
    message.warning('请填写完整清单、借用事由、单位、经办人和预计借出日期');
    return false;
  }
  const response: any = await loanApi.save(payload());
  if (!(await open(response.data))) return false;
  emit('saved');
  return true;
}
async function saveDraft() {
  busy.value = true;
  try {
    if (await persist()) message.success('借用草稿已保存');
  } finally {
    busy.value = false;
  }
}
async function edit(value: string) {
  if(value==='extension' || value==='port' || value==='overseasFiling' || value==='exhibitionEnd') {
    const id=form.value.id; if(!id || !(await open(id)))return;
    if(!form.value.extensionSourceRevision || (value==='extension' ? form.value.extensionAvailable!==true : value==='port' ? form.value.portAvailable!==true : value==='exhibitionEnd' ? form.value.exhibitionEndAvailable!==true : form.value.overseasFilingAvailable!==true))return;
    extensionContext.value={id,orgId:form.value.orgId,sourceRevision:form.value.extensionSourceRevision,previousId:(value==='exhibitionEnd'?form.value.exhibitionEnds?.at(-1)?.id:form.value.overseasExtensions?.at(-1)?.id)||null,generation,mode:value,filingPreviousIds:Object.fromEntries((form.value.overseasFilings||[]).map((r:any)=>[r.content.request.kind,r.id])),endBasis:form.value.exhibitionEnds?.at(-1)||null};
  }
  if (value === 'dossier' && editable.value && !(await persist())) return;
  kind.value = value;
  dossierPreview.value = value==='overseasFiling' ? {kind:'AGREEMENT'} : value==='port' ? {stage:'EXIT'} : form.value.dossier || {};
  await nextTick();
  evidenceRef.value?.open(
    value === 'exhibitionEnd' ? {} : value === 'overseasFiling' ? {kind:'AGREEMENT'} : value === 'port' ? {stage:'EXIT',collectionIds:[...selectedIds.value]} : value === 'extension' ? {} : value === 'dossier'
      ? loanEvidenceInitial(form.value.dossier || {})
      : value === 'administrative'
        ? { action: form.value.outExecuted ? 'FILING' : 'APPROVAL' }
        : { majorSituation: 'NO', collectionIds: [...selectedIds.value] },
  );
}
function evidenceChanged(value: Record<string, any>) {
  if (kind.value === 'dossier' || kind.value==='port' || kind.value==='overseasFiling') dossierPreview.value = loanEvidencePayload(value);
}
async function editAssessment(id: string) {
  assessmentId.value = id;
  kind.value = 'assessment';
  await nextTick();
  const a = form.value.dossier.assessments?.find((i: any) => i.collectionId === id) || {};
  const extra = form.value.dossier.overseasExhibition?.objects?.find((i: any) => i.collectionId === id) || {};
  evidenceRef.value?.open(loanEvidenceInitial({ ...a, ...extra }));
}
async function saveEvidence(value: Record<string, any>) {
  if(kind.value==='extension' || kind.value==='port' || kind.value==='overseasFiling' || kind.value==='exhibitionEnd') {
    const context=extensionContext.value;
    if(!context || context.mode!==kind.value || context.generation!==generation || !show.value || readError.value || readLoading.value)return false;
    if(context.mode==='exhibitionEnd')await loanApi.exhibitionEnd(context.id,context.orgId,{...value,sourceRevision:context.sourceRevision,previousId:context.previousId});
    else if(context.mode==='overseasFiling') {
      if(!['AGREEMENT','CONCLUSION'].includes(value.kind))return false;
      const request:Record<string,any>={...value,authorityLevel:'NATIONAL',sourceRevision:context.sourceRevision,previousId:context.filingPreviousIds[value.kind]||null};
      if(value.kind==='AGREEMENT')for(const key of ['endingRecordId','actualExhibitionEndedOn','endingEvidenceFileIds','conclusionFormFileIds','conclusionReportFileIds','audiovisualFileIds'])delete request[key];
      if(value.kind==='CONCLUSION') {
        if(!context.endBasis){message.warning('请先登记展览实际结束事实');return false;}
        Object.assign(request,{endingRecordId:context.endBasis.id,actualExhibitionEndedOn:context.endBasis.content.request.actualEndedOn,endingEvidenceFileIds:context.endBasis.content.request.evidenceFileIds});
      }
      await loanApi.overseasFiling(context.id,context.orgId,request);
    }
    else if(context.mode==='port')await loanApi.port(context.id,context.orgId,{...loanPortPayload(value),sourceRevision:context.sourceRevision});
    else await loanApi.extension(context.id,context.orgId,{...value,sourceRevision:context.sourceRevision,previousId:context.previousId});
    if(context!==extensionContext.value || context.generation!==generation || !show.value)return false;
    message.success(context.mode==='exhibitionEnd'?'结束事实已留存，结项报送期限已更新':context.mode==='overseasFiling'?'报送原件与历次日期已留存':context.mode==='port'?'口岸审核及海关原件已留存':'延期批准及原期限已留存');return;
  }
  const data = loanEvidencePayload(value),
    id = form.value.id;
  if (kind.value === 'dossier') {
    const dossier = { ...form.value.dossier, ...data };
    if (data.overseasExhibition)
      dossier.overseasExhibition = { ...form.value.dossier?.overseasExhibition, ...data.overseasExhibition };
    if (dossier.purpose !== 'EXHIBITION') dossier.exhibitionOverseas = false;
    if (!dossier.exhibitionOverseas) delete dossier.overseasExhibition;
    await loanApi.save({ ...payload(), dossier });
  } else if (kind.value === 'assessment') {
    const dossier = {
      ...form.value.dossier,
      assessments: [
        ...(form.value.dossier.assessments || []).filter((i: any) => i.collectionId !== assessmentId.value),
        { ...data, collectionId: assessmentId.value },
      ],
    };
    if (dossier.exhibitionOverseas)
      dossier.overseasExhibition = {
        ...dossier.overseasExhibition,
        objects: [
          ...(dossier.overseasExhibition?.objects || []).filter((i: any) => i.collectionId !== assessmentId.value),
          {
            collectionId: assessmentId.value,
            uniqueOrFragileFirstLevel: data.uniqueOrFragileFirstLevel,
            prohibitedFromExit: data.prohibitedFromExit,
            domesticExhibitionEvidenceFileIds: data.domesticExhibitionEvidenceFileIds,
          },
        ],
      };
    await loanApi.save({ ...payload(), dossier });
  } else if (kind.value === 'administrative')
    await loanApi.administrative(id, { ...data, collectionIds: selectedIds.value });
  else await loanApi.monitoring(id, data);
  message.success('实际材料已保存');
}
async function submit() {
  if (form.value.evidenceErrors?.length) return;
  busy.value = true;
  try {
    if (editable.value && !(await persist())) return;
    await loanApi.submit(form.value.id);
    message.success('已提交本人确认');
    await reload();
  } finally {
    busy.value = false;
  }
}
async function execute() {
  if (!canOut.value) return;
  const confirmed = await new Promise<boolean>((resolve) =>
    dialog.warning({
      title: '确认登记实际借用出库',
      content:
        '请核对双方各不少于两人已逐件点交并签署。确认后生成逐件出库流水及归还草稿，出借后备案和在借监管期限同时开始。',
      positiveText: '确认实物已签署点交出库',
      negativeText: '返回核对',
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false),
      onMaskClick: () => resolve(false),
    }),
  );
  if (!confirmed) return;
  busy.value = true;
  try {
    await loanApi.executeOut(form.value.id, {});
    message.success('出库已登记，请按期备案和监督归还');
    await reload();
  } finally {
    busy.value = false;
  }
}
function openRfid() {
  if (!canOut.value) return;
  void router.push({ path: '/collection/rfid-desk', query: { type: 'LOAN_OUT', businessId: form.value.id } });
}
function rawArchive(a: any) {
  try {
    return JSON.parse(a.snapshot);
  } catch {
    return {};
  }
}
function archiveData(a: any) {
  try {
    const r = JSON.parse(a.snapshot);
    return r.proposal || r.request || (r.content ? { ...form.value, handover: r } : r);
  } catch {
    return {};
  }
}
const archiveOriginalUrl = (archive: any) => (fileId: string) =>
  archive.files?.some((file: any) => file.fileId === fileId)
    ? `/collection/loan/${encodeURIComponent(form.value.id)}/original/${encodeURIComponent(archive.id)}/${encodeURIComponent(fileId)}`
    : '';
defineExpose({ open });
</script>

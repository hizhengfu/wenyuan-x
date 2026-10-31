<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="馆内提用办理">
    <NAlert v-if="readError" type="error">{{ readError }}</NAlert>
    <NText v-else-if="readLoading">正在读取办理资料…</NText>
    <NSpace v-else vertical :size="18">
      <CollectionApprovalProgress v-if="show && form.id" :business-id="form.id" business-type="COLLECTION_REQUISITION" :fetch-flow="requisitionApi.flow" :revision="form" />
      <NAlert type="warning"
        >本流程用于馆内部门依法开展业务的提用。工作人员不得个人借用或非法侵占藏品，也不得以提用名义办理赠与、出租、出售、抵押或质押。</NAlert
      >
      <NAlert v-if="form.replacementErrors?.length" type="error">{{ form.replacementErrors.join('；') }}</NAlert>
      <NAlert type="info">{{
        form.sourceRetirement
          ? '本单因修复来源重建已终止执行，原批准和签署原件保留；新项目须另行办理送修。'
          : form.supersededById
            ? '本单已由新申请承接，原审批及档案保留，不能继续点交或出库。'
            : form.returned
              ? '本单已归还，不能再次点交或出库；历史状态不一致时请核查原记录。'
              : form.outExecuted
                ? '实际出库已登记，归还草稿已生成，请按期办理逐件验收。'
                : form.flowStatus === 'PASS'
                  ? '本轮提用已批准，请先登记保管负责人审查指派、逐件状况及双方签署点交原件。'
                  : '先保存提用清单并核实完整使用方案，再提交三方责任人确认批准。'
      }}</NAlert>
      <NSpace v-if="form.id" v-permission="'COLLECTION_ADMIN,COLLECTION_CARE_MANAGE'"
        ><NButton
          v-for="piece in selectedIds"
          :key="piece"
          @click="fragilityRef?.open(piece, 'COLLECTION_REQUISITION', form.id)"
          >{{ piece }}：使用保护评估</NButton
        ></NSpace
      >
      <NButton
        v-if="form.id"
        v-permission="'COLLECTION_ADMIN,COLLECTION_CARE_MANAGE'"
        @click="jointSafetyRef?.open(form.id)"
        >多部门定期安全检查</NButton
      >
      <NForm v-if="editable" :model="form" label-placement="left" label-width="150">
        <NFormItem label="提用藏品清单" required
          ><CollectionSelect
            v-model:value="selectedIds"
            multiple
            life-status="IN_STORE"
            :disabled="form.sourceBizType === 'COLLECTION_RESTORATION' || !!form.replacesId"
        /></NFormItem>
        <NFormItem label="提用单号"><NInput v-model:value="form.documentNo" placeholder="留空由系统编制" /></NFormItem>
        <NAlert type="info"
          >常规巡库和账物核对在库内登记；仅确需提取实物时办理提用。搬迁及其他事项须在使用方案内说明具体用途。</NAlert
        >
        <NFormItem label="提用事由" required
          ><NSelect
            v-model:value="form.purpose"
            :options="requisitionPurposeOptions"
            :disabled="form.sourceBizType === 'COLLECTION_RESTORATION' || !!form.replacesId"
        /></NFormItem>
        <NFormItem label="提用依据" required><NInput v-model:value="form.basis" type="textarea" /></NFormItem>
        <NFormItem label="提用部门名称" required
          ><NInput v-model:value="form.department" placeholder="与使用方案内实际机构一致"
        /></NFormItem>
        <NFormItem label="经办人" required><UserSelect v-model:value="form.operator" /></NFormItem>
        <NFormItem label="一级文物复核人"><UserSelect v-model:value="form.reviewer" /></NFormItem>
        <NFormItem label="归库保管位置" required><WarehouseTreeSelect v-model:value="form.toWarehouseId" /></NFormItem>
        <NFormItem label="提用依据附件"
          ><HUploadInput v-model:value="form.attachmentIds" @upload-finish="form.attachmentIds = $event.id" clearable
        /></NFormItem>
      </NForm>
      <NSpace size="small" align="center">
        <NButton v-if="editable" type="primary" :loading="busy" @click="saveDraft">保存提用草稿</NButton>
        <NButton v-if="form.id && editable && form.dossier" :loading="busy" @click="submit">提交三方确认</NButton>
        <NTooltip v-if="canRecordHandover" :disabled="!handoverTooEarly">
          <template #trigger>
            <NButton :disabled="handoverTooEarly" @click="openHandover">{{
              form.handover ? '补充或更正点交原件' : '登记实际逐件点交'
            }}</NButton>
          </template>
          {{ REQUISITION_HANDOVER_TOO_EARLY }}
        </NTooltip>
        <NButton
          v-if="canExecuteOut"
          type="warning"
          :loading="busy || confirmingOut"
          :disabled="confirmingOut"
          @click="execute"
          >登记实际出库</NButton
        >
        <CollectionActionMenu
          label="方案与记录"
          v-if="
            form.id ||
            form.supersededById ||
            form.replacesId ||
            form.sourceRetirement?.context?.sourceBizType === 'COLLECTION_RESTORATION' ||
            canReplace ||
            canExecuteOut
          "
        >
          <NButton quaternary v-if="form.id && editable" @click="editDossier">填写完整使用及安全方案</NButton>
          <NButton quaternary v-if="form.supersededById" @click="open(form.supersededById)">查看承接的新提用单</NButton>
          <NButton
            quaternary
            v-if="form.sourceRetirement?.context?.sourceBizType === 'COLLECTION_RESTORATION'"
            @click="openReplacementSource"
            >查看承接的新修复项目</NButton
          >
          <NButton quaternary v-if="form.replacesId" @click="open(form.replacesId)">查看所依据的原提用单</NButton>
          <NButton quaternary v-if="canReplace" :loading="busy" @click="replacementRef?.open(form)"
            >岗位变动：重建提用责任方案</NButton
          >
          <NButton
            quaternary
            v-if="form.id"
            @click="flowRef?.openBusinessReadOnly(form.id, '馆内提用审批', 'COLLECTION_REQUISITION')"
            >查看审批</NButton
          >
          <NButton quaternary v-if="canExecuteOut" @click="openRfid">进入 RFID 核验出库</NButton>
        </CollectionActionMenu>
        <OriginalHistoryButton
          v-if="historyContextReadable"
          :key="form.id"
          business-type="COLLECTION_REQUISITION"
          :business-id="form.id"
          label="本单历史材料"
        />
      </NSpace>
      <NText v-if="canRecordHandover && form.handover" depth="3">{{ executionNotice }}</NText>
      <RequisitionEvidenceView v-if="form.id" :data="form" />
      <NCollapse v-if="form.archives?.length">
        <NCollapseItem
          v-for="archive in form.archives"
          :key="archive.id"
          :title="archiveTitle(archive)"
          :name="archive.id"
        >
          <NText depth="3">此阶段材料已归档，后续补充不覆盖原件。</NText>
          <NSpace vertical
            ><HFileList
              :files="archive.files"
              :original-url="archiveOriginalUrl(archive)"
              :revision="[archive.id, archive.digest]"
              show-download-btn
          /></NSpace>
          <template v-if="archive.phase?.startsWith('RESPONSIBILITY_')">
            <NText>责任变更原因：{{ archiveRecord(archive).request?.reason }}</NText>
            <NText
              >原单 {{ archiveRecord(archive).sourceId }} → 新单 {{ archiveRecord(archive).replacementId }}；登记人
              {{ archiveRecord(archive).authorizedBy }}</NText
            >
          </template>
          <RequisitionEvidenceView
            :data="archiveData(archive)"
            :archive-files="archive.files"
            :original-url="archiveOriginalUrl(archive)"
          />
        </NCollapseItem>
      </NCollapse>
    </NSpace>
  </HPageForm>
  <template v-if="!readLoading && !readError">
    <CollectionEvidenceForm
      ref="dossierRef"
      title="完整使用、安全方案及三方责任人"
      :fields="requisitionDossierFields"
      :save="saveDossier"
      @saved="reload"
    />
    <RequisitionResponsibilityReplacementForm
      v-if="form.id"
      :key="form.id"
      ref="replacementRef"
      @created="replacementCreated"
    />
    <RequisitionHandoverForm ref="handoverRef" @saved="reload" />
    <FlowFormDrawer
      v-if="show"
      :key="form.id"
      ref="flowRef"
      :fetch-fn="requisitionApi.flow"
      :cancel-api="requisitionApi.cancel"
      @success="reload"
    />
    <CollectionFragilityDrawer ref="fragilityRef" />
    <CollectionJointSafetyDrawer ref="jointSafetyRef" />
  </template>
</template>
<script setup lang="ts">
import CollectionApprovalProgress from './CollectionApprovalProgress.vue';
import CollectionActionMenu from './CollectionActionMenu.vue';
const CollectionFragilityDrawer = defineAsyncComponent(() => import('./CollectionFragilityDrawer.vue'));
const fragilityRef = ref<any>();
const CollectionJointSafetyDrawer = defineAsyncComponent(() => import('./CollectionJointSafetyDrawer.vue'));
const jointSafetyRef = ref<any>();
import { cloneDeep } from 'lodash-es';
import { computed, defineAsyncComponent, onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
import { formatLocalDateValue } from '@/utils';
import { useRouter } from 'vue-router';
import { useMessage, useDialog } from 'naive-ui';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import FlowFormDrawer from '@/modules/flow/components/FlowFormDrawer.vue';
import CollectionSelect from './CollectionSelect.vue';
import WarehouseTreeSelect from './WarehouseTreeSelect.vue';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import RequisitionEvidenceView from './RequisitionEvidenceView.vue';
import OriginalHistoryButton from './OriginalHistoryButton.vue';
import RequisitionHandoverForm from './RequisitionHandoverForm.vue';
import RequisitionResponsibilityReplacementForm from './RequisitionResponsibilityReplacementForm.vue';
import { requisitionApi } from '../api/requisition';
import {
  requisitionDossierFields,
  requisitionPurposeOptions,
  requisitionHandoverError,
} from '../utils/requisitionEvidenceFields';
import {
  REQUISITION_HANDOVER_TOO_EARLY,
  REQUISITION_PREREQUISITE_CONFIRM,
  REQUISITION_PREREQUISITE_DRAFT_HINT,
  isBeforeExpectedOutAt,
  summarizeRequisitionPrerequisites,
} from '../utils/requisitionPrerequisite';
import { fragilityApi } from '../api/fragility';
import { jointSafetyApi } from '../api/jointSafety';
import { useAuthStore } from '@/store/modules/auth';
const emit = defineEmits<{ (e: 'saved'): void }>(),
  message = useMessage(),
  dialog = useDialog(),
  router = useRouter(),
  auth = useAuthStore();
const show = ref(false),
  busy = ref(false),
  confirmingOut = ref(false),
  readActor = ref<string>(),
  readLoading = ref(false),
  readError = ref(''),
  form = ref<any>({}),
  selectedIds = ref<string[]>([]),
  dossierRef = ref<any>(),
  handoverRef = ref<any>(),
  flowRef = ref<any>(),
  replacementRef = ref<any>();
const editable = computed(
  () =>
    !readLoading.value &&
    !readError.value &&
    !busy.value &&
    !form.value.supersededById &&
    !form.value.sourceRetirement &&
    !form.value.returned &&
    !form.value.replacementErrors?.length &&
    (!form.value.id ||
      (form.value.flowStatus === 'DRAFT' &&
        !form.value.flowInstanceId &&
        !form.value.outExecuted &&
        !form.value.handover)),
);
const canRecordHandover = computed(
  () =>
    show.value &&
    !readLoading.value &&
    !readError.value &&
    !!readActor.value &&
    readActor.value === auth.userInfo?.id &&
    auth.hasPermission('COLLECTION_ADMIN,COLLECTION_STORAGE') &&
    !!form.value.id &&
    !busy.value &&
    !form.value.supersededById &&
    !form.value.sourceRetirement &&
    !form.value.returned &&
    !form.value.replacementErrors?.length &&
    form.value.flowStatus === 'PASS' &&
    !form.value.outExecuted,
);
const handoverTooEarly = computed(() => isBeforeExpectedOutAt(form.value.dossier));
function openHandover() {
  if (handoverTooEarly.value) {
    message.warning(REQUISITION_HANDOVER_TOO_EARLY);
    return;
  }
  handoverRef.value?.open(form.value);
}
const assignedCustodian = computed(() => {
  const ids = form.value.handover?.content?.custodianUserIds;
  return !!auth.userInfo?.id && Array.isArray(ids) && ids.includes(auth.userInfo.id);
});
const validHandover = computed(() => {
  const content = form.value.handover?.content;
  if (
    !content ||
    !Array.isArray(content.custodianUserIds) ||
    !Array.isArray(content.receivingUserIds) ||
    !Array.isArray(content.items)
  )
    return false;
  return !requisitionHandoverError(content, selectedIds.value, false, form.value.dossier?.transportRequired === true);
});
const canExecuteOut = computed(() => canRecordHandover.value && assignedCustodian.value && validHandover.value);
const executionNotice = computed(() =>
  !assignedCustodian.value
    ? '实际出库须由本单已指派的保管人员本人办理；当前账号可核对点交资料，不能执行出库。'
    : !validHandover.value
      ? '当前点交资料或安全条件尚需核对，请补齐后由已指派的保管人员本人执行出库。'
      : '当前账号是本单已指派的保管人员，请核对点交资料后由本人登记实际出库。',
);
const canReplace = computed(
  () =>
    !busy.value &&
    form.value.id &&
    ['AUDIT', 'PASS', 'CANCEL', 'REJECT'].includes(form.value.flowStatus) &&
    !form.value.supersededById &&
    !form.value.sourceRetirement &&
    !form.value.returned &&
    !form.value.replacementErrors?.length &&
    !form.value.outExecuted &&
    !form.value.handover &&
    form.value.sourceBizType !== 'COLLECTION_RESTORATION',
);
let generation = 0;
let historyReadGeneration = 0;
const historyReadIdentity = ref<string>();
function currentHistoryIdentity() {
  const user = auth.userInfo;
  return JSON.stringify([
    auth.token,
    auth.authorizationName,
    user?.id,
    user?.groupId,
    user?.enabled,
    [...(user?.roles || [])].sort(),
    [...(user?.roleCodes || [])].sort(),
    [...(auth.roles || [])].sort(),
    [...(auth.roleCodes || [])].sort(),
    [...(auth.permissions || [])].sort(),
  ]);
}
function invalidateHistoryContext() {
  ++historyReadGeneration;
  historyReadIdentity.value = undefined;
}
const historyContextReadable = computed(
  () =>
    show.value &&
    !readLoading.value &&
    !readError.value &&
    !!form.value.id &&
    !!auth.userInfo?.id &&
    auth.userInfo?.enabled !== false &&
    historyReadIdentity.value === currentHistoryIdentity(),
);
watch(currentHistoryIdentity, invalidateHistoryContext, { flush: 'sync' });
onDeactivated(invalidateHistoryContext);
watch(
  show,
  (value) => {
    if (!value) {
      ++generation;
      invalidateHistoryContext();
    }
  },
  { flush: 'sync' },
);
onBeforeUnmount(() => {
  ++generation;
});
function openReplacementSource() {
  const id = form.value.sourceRetirement?.context?.replacementSourceId;
  if (id) router.push({ path: '/collection-restoration/project', query: { projectId: id } });
}
async function replacementCreated(source: string, child: string) {
  emit('saved');
  if (show.value && form.value.id === source) await open(child);
}
async function open(id?: string, initial: Record<string, any> = {}) {
  const current = ++generation;
  const actor = auth.userInfo?.id;
  invalidateHistoryContext();
  const historyTicket = historyReadGeneration,
    historyIdentity = currentHistoryIdentity();
  readActor.value = undefined;
  form.value = {};
  selectedIds.value = [];
  readError.value = '';
  readLoading.value = true;
  show.value = true;
  try {
    const data = id
      ? ((await requisitionApi.get(id)) as any).data
      : { flowStatus: 'DRAFT', purpose: 'RESEARCH', ...cloneDeep(initial) };
    if (current !== generation || !show.value || actor !== auth.userInfo?.id) return false;
    if (!data) throw new Error('办理记录不存在或当前不可读取');
    readActor.value = actor;
    if (historyTicket === historyReadGeneration && historyIdentity === currentHistoryIdentity())
      historyReadIdentity.value = historyIdentity;
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
function payload() {
  return { ...form.value, collectionIds: selectedIds.value.join(',') };
}
async function persist() {
  if (
    !selectedIds.value.length ||
    !form.value.purpose ||
    !form.value.basis?.trim() ||
    !form.value.department?.trim() ||
    !form.value.operator ||
    !form.value.toWarehouseId
  ) {
    message.warning('请填写完整藏品清单、事由、依据、部门、经办人和归库位置');
    return false;
  }
  const response: any = await requisitionApi.save(payload());
  if (!(await open(response.data))) return false;
  emit('saved');
  return true;
}
async function probePrerequisites() {
  const ids = selectedIds.value.filter(Boolean);
  const reqId = form.value.id;
  const canReadCare = auth.hasPermission('COLLECTION_ADMIN,COLLECTION_CARE_MANAGE');
  if (!canReadCare || !ids.length) {
    return summarizeRequisitionPrerequisites(null, undefined, ids.length);
  }
  const fragilityDetails = await Promise.all(
    ids.map(async (id) => {
      try {
        const r: any = await fragilityApi.detail(id, { silentError: true });
        return r?.data ?? null;
      } catch {
        return null;
      }
    }),
  );
  let joint: Record<string, any> | null | undefined = undefined;
  if (reqId) {
    try {
      const r: any = await jointSafetyApi.detail(reqId, { silentError: true });
      joint = r?.data ?? null;
    } catch {
      joint = null;
    }
  }
  return summarizeRequisitionPrerequisites(fragilityDetails, joint, ids.length);
}

/** 保存/提交前确认；能读到状态且未齐则阻止并引导入口，否则弹确认。 */
async function confirmPrerequisites(mode: 'draft' | 'submit') {
  const status = await probePrerequisites();
  if (mode === 'submit' && status.missing.length) {
    const firstPiece = selectedIds.value[0];
    message.warning(`提交前尚缺：${status.missing.join('；')}。请先到本抽屉对应入口办妥后再提交。`);
    if (status.fragilityOk === false && firstPiece) fragilityRef.value?.open(firstPiece, 'COLLECTION_REQUISITION', form.value.id);
    else if (status.jointSafetyOk === false && form.value.id) jointSafetyRef.value?.open(form.value.id);
    return false;
  }
  const content =
    mode === 'draft'
      ? REQUISITION_PREREQUISITE_DRAFT_HINT
      : status.fragilityOk === true && status.jointSafetyOk === true
        ? '已检测到材质脆弱性评估双签与联合安全方案三签均完成。确认提交指定责任人依次确认批准？'
        : REQUISITION_PREREQUISITE_CONFIRM;
  return await new Promise<boolean>((resolve) =>
    dialog.warning({
      title: mode === 'draft' ? '保存提用草稿' : '提交前请确认前置条件',
      content,
      positiveText: mode === 'draft' ? '知晓并保存' : '已办妥，继续提交',
      negativeText: mode === 'draft' ? '取消' : '返回办理',
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => {
        if (mode === 'submit' && form.value.id) {
          const firstPiece = selectedIds.value[0];
          if (firstPiece) fragilityRef.value?.open(firstPiece, 'COLLECTION_REQUISITION', form.value.id);
        }
        resolve(false);
      },
      onClose: () => resolve(false),
      onMaskClick: () => resolve(false),
    }),
  );
}

async function saveDraft() {
  busy.value = true;
  try {
    if (!(await confirmPrerequisites('draft'))) return;
    if (await persist()) message.success('提用草稿已保存');
  } finally {
    busy.value = false;
  }
}
async function editDossier() {
  busy.value = true;
  try {
    if (await persist())
      dossierRef.value?.open({
        ...form.value.dossier,
        transportRequired:
          typeof form.value.dossier?.transportRequired === 'boolean'
            ? form.value.dossier.transportRequired
              ? 'YES'
              : 'NO'
            : form.value.dossier?.transportRequired,
      });
  } finally {
    busy.value = false;
  }
}
async function saveDossier(value: Record<string, any>) {
  if (
    value.transportRequired === 'YES' &&
    [
      'transportStandardReference',
      'transportStandardApplicability',
      'transportCarrierName',
      'transportCarrierCapabilityFileIds',
      'transportPlanFileIds',
    ].some((key) => !String(value[key] || '').trim())
  ) {
    message.warning('需要包装运输时，请补齐标准版本、适用依据、承运能力和签署方案');
    return false;
  }
  await requisitionApi.save({
    ...payload(),
    dossier: { ...value, transportRequired: value.transportRequired === 'YES' },
  });
  message.success('完整使用及安全方案已保存');
}
async function submit() {
  busy.value = true;
  try {
    if (!(await confirmPrerequisites('submit'))) return;
    if (!(await persist())) return;
    await requisitionApi.submit(form.value.id);
    message.success('已提交指定责任人依次确认批准');
    await reload();
  } finally {
    busy.value = false;
  }
}
async function execute() {
  if (busy.value || confirmingOut.value) return;
  if (!canExecuteOut.value) {
    message.warning('请由本单已指派的保管人员本人，重新核对当前权限和点交资料后执行');
    return;
  }
  const target = form.value,
    id = target.id,
    actor = auth.userInfo?.id,
    ticket = generation;
  const handover = JSON.stringify(target.handover);
  const current = () =>
    show.value &&
    ticket === generation &&
    target === form.value &&
    id === form.value.id &&
    actor === auth.userInfo?.id &&
    canExecuteOut.value &&
    handover === JSON.stringify(form.value.handover);
  confirmingOut.value = true;
  let confirmed: boolean;
  try {
    confirmed = await new Promise<boolean>((resolve) =>
      dialog.warning({
        title: '确认登记实际出库',
        content:
          '须由本单已指派的保管人员本人核对逐件点交及双方签署确已完成。确认后将按点交记录生成逐件出库流水，藏品状态变为使用中，并生成待办理归还单。',
        positiveText: '本人确认实物已点交出库',
        negativeText: '返回核对',
        onPositiveClick: () => resolve(true),
        onNegativeClick: () => resolve(false),
        onClose: () => resolve(false),
        onMaskClick: () => resolve(false),
      }),
    );
  } finally {
    confirmingOut.value = false;
  }
  if (!confirmed) return;
  if (!current()) {
    message.warning('办理案卷、当前账号、权限或点交指派资料已变化，请重新打开本单核对');
    return;
  }
  busy.value = true;
  try {
    await requisitionApi.executeOut(id, {});
    if (ticket === generation && show.value && target === form.value && actor === auth.userInfo?.id) {
      message.success('实际出库已登记，请按期办理归还');
      await reload();
    }
  } finally {
    busy.value = false;
  }
}
function openRfid() {
  if (!canExecuteOut.value || confirmingOut.value) return;
  void router.push({ path: '/collection/rfid-desk', query: { type: 'REQUISITION_OUT', businessId: form.value.id } });
}
function archiveTitle(archive: any) {
  const p = archive.phase || '';
  const title =
    p === 'RESPONSIBILITY_REPLACED'
      ? '旧单终止执行及责任变更原件'
      : p === 'RESPONSIBILITY_CREATED'
        ? '承接草稿建立依据'
        : p === 'SUBMITTED'
          ? '提交的提用方案'
          : p.startsWith('APPROVED_')
            ? '本轮责任人批准'
            : p.startsWith('HANDOVER_')
              ? '已签署点交版本'
              : p === 'OUT_EXECUTED'
                ? '实际出库记录'
                : '业务档案';
  return `${title} · ${formatLocalDateValue(archive.archivedAt, 'YYYY-MM-DD HH:mm:ss')}`;
}
function archiveRecord(archive: any) {
  try {
    return JSON.parse(archive.snapshot);
  } catch {
    return {};
  }
}
function archiveData(archive: any) {
  try {
    const record = JSON.parse(archive.snapshot);
    if (archive.phase?.startsWith('RESPONSIBILITY_')) return JSON.parse(record.initialProposal);
    return record.proposal || record.request || (record.content ? { ...form.value, handover: record } : record);
  } catch {
    return {};
  }
}
const archiveOriginalUrl = (archive: any) => (fileId: string) =>
  archive.files?.some((file: any) => file.fileId === fileId)
    ? `/collection/requisition/${encodeURIComponent(form.value.id)}/original/${encodeURIComponent(archive.id)}/${encodeURIComponent(fileId)}`
    : '';
defineExpose({ open });
</script>

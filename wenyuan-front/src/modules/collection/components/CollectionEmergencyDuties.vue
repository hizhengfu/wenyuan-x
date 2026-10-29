<template>
  <NSpace vertical :size="16">
    <NAlert type="info"
      >职责承接只用于原件核对和后续办理。主要负责人本人登记实际指定，原任本人交接，接任人本人确认后按实际生效时间承接；不能改写参加记录或代替旧签署。</NAlert
    >
    <NSelect v-model:value="selected" :options="options" filterable placeholder="选择需承接的预案、演练或整改职责" />
    <template v-if="target">
      <CollectionEvidenceReadView :fields="peopleFields" :data="target" />
      <NButton @click="edit('appoint')">主要负责人登记实际责任承接</NButton>
      <NEmpty v-if="!target.records.length" description="暂无责任承接，按原指定人员办理" />
      <NCard v-for="r in [...target.records].reverse()" :key="r.id" :title="`第 ${r.version} 次责任指定`">
        <NText>{{
          r.active
            ? '当前已生效承接'
            : !r.latest
              ? '历史指定，待接受的旧指定不能再签署'
              : r.acceptance
                ? '接任已确认，按指定时间生效'
                : '尚待交接或本人接受职责'
        }}</NText>
        <CollectionEvidenceReadView :fields="historyFields" :data="appointmentHistory(r)" :archive-files="archiveFiles?.[r.id]?.['DUTY_APPOINTED']" :original-url="originalUrl(r.id, 'DUTY_APPOINTED')" />
        <NText>原任实际交接</NText>
        <CollectionEvidenceReadView v-if="r.handover" :fields="signatureFields" :data="signature(r.handover)" :archive-files="archiveFiles?.[r.id]?.['DUTY_HANDOVER']" :original-url="originalUrl(r.id, 'DUTY_HANDOVER')" />
        <NText v-else>{{
          r.appointment.request.predecessorUnavailable ? '按主要负责人所登记的无法交接说明及原件办理' : '待原任本人交接'
        }}</NText>
        <NText>接任人员本人确认</NText>
        <CollectionEvidenceReadView v-if="r.acceptance" :fields="signatureFields" :data="signature(r.acceptance)" :archive-files="archiveFiles?.[r.id]?.['DUTY_ACCEPTED']" :original-url="originalUrl(r.id, 'DUTY_ACCEPTED')" />
        <NText v-else>待接任人员核对原件、未完成事项及职责后本人确认</NText>
        <NAlert v-if="r.latest && !r.acceptance && r.basisMatches === false" type="warning"
          >报告已有订正，须由主要负责人核对当前报告后重新指定，不沿用旧页面承接。</NAlert
        >
        <NSpace v-if="r.latest && r.basisMatches !== false">
          <NButton v-if="!r.handover && !r.appointment.request.predecessorUnavailable" @click="edit('handover', r)"
            >原任本人交接</NButton
          >
          <NButton
            v-if="!r.acceptance && (r.handover || r.appointment.request.predecessorUnavailable)"
            @click="edit('accept', r)"
            >接任人本人确认承接</NButton
          >
        </NSpace>
      </NCard>
    </template>
    <CollectionEvidenceForm ref="formRef" :title="formTitle" :fields="fields" :save="save" @saved="$emit('saved')">
      <template #context
        ><NAlert type="info"
          >{{ editingLabel }}。请核对实际指定和未完成事项；系统记录本次本人确认时间，不冒充过去的履责时间。</NAlert
        ></template
      >
    </CollectionEvidenceForm>
  </NSpace>
</template>
<script setup lang="ts">
import type { CollectionReadonlyFile } from '../utils/readonlyFiles';
import { computed, nextTick, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { emergencyApi } from '../api/emergency';
import {
  emergencyDutyFields,
  emergencyDutyRoles,
  emergencyDutyQualifications,
  emergencyDutyValues,
} from '../utils/emergencyDutyFields';
import { emergencyFields } from '../utils/emergencyFields';
import type { EvidenceField } from '../utils/evidenceFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
const props = defineProps<{ orgId: string; targets: any[]; archiveFiles?:Record<string,Record<string,CollectionReadonlyFile[]>> }>();
defineEmits<{ (e: 'saved'): void }>();
const selected = ref<string | null>(null),
  formRef = ref<any>(),
  fields = ref<EvidenceField[]>([]),
  formTitle = ref(''),
  editingLabel = ref('');
const options = computed(() =>
  props.targets.map((t) => ({ value: t.key, label: `${t.label} · ${emergencyDutyRoles[t.target.role]}` })),
);
const target = computed(() => props.targets.find((t) => t.key === selected.value));
watch(
  () => props.orgId,
  () => {
    selected.value = null;
  },
);
const peopleFields: EvidenceField[] = [
  { key: 'originalUserId', label: '原件记载的实际人员', type: 'user' },
  { key: 'currentUserId', label: '当前后续办理人员', type: 'user' },
];
const signatureFields: EvidenceField[] = [
  { key: 'actorUserId', label: '本人核对人员', type: 'user' },
  { key: 'confirmedAt', label: '系统本人确认时间', type: 'datetime' },
  ...emergencyFields.sign,
];
const historyFields: EvidenceField[] = [
  { key: 'issuerUserId', label: '实际指定的主要负责人', type: 'user' },
  { key: 'submittedAt', label: '系统登记时间', type: 'datetime' },
  { key: 'fromUserId', label: '原任人员', type: 'user' },
  ...emergencyDutyFields,
];
const signature = (v: any) => ({ ...v.record, actorUserId: v.actorUserId, confirmedAt: v.confirmedAt });
const appointmentHistory = (r: any) => ({
  ...r.appointment.request,
  fromUserId: r.appointment.fromUserId,
  issuerUserId: r.appointment.issuerUserId,
  submittedAt: r.appointment.submittedAt,
});
const originalUrl = (recordId: string, phase: string) => (fileId: string) =>
  emergencyApi.originalUrl(props.orgId, recordId, phase, fileId);
let editing: any = {};
async function edit(kind: string, r?: any) {
  if (!target.value) return;
  editing = {
    kind,
    orgId: props.orgId,
    target: cloneDeep(target.value.target),
    expectedReportId: target.value.expectedReportId,
    previousId: target.value.previousId,
    recordId: r?.id,
  };
  editingLabel.value = `${target.value.label} · ${emergencyDutyRoles[target.value.target.role]}`;
  formTitle.value =
    kind === 'appoint'
      ? '主要负责人本人登记实际责任指定'
      : kind === 'handover'
        ? '原任本人交接实际原件与未完事项'
        : '接任人本人核对并接受后续职责';
  fields.value =
    kind === 'appoint'
      ? emergencyDutyFields.map((f) =>
          f.key === 'qualification'
            ? {
                ...f,
                options: target.value.qualifications.map((q: string) => ({
                  value: q,
                  label: emergencyDutyQualifications[q],
                })),
              }
            : f,
        )
      : emergencyFields.sign;
  await nextTick();
  formRef.value?.open({});
}
async function save(value: Record<string, any>) {
  const e = { ...editing };
  if (e.kind === 'appoint')
    return emergencyApi.record(e.orgId, 'duty', {
      ...emergencyDutyValues(value),
      target: e.target,
      expectedReportId: e.expectedReportId || null,
      previousId: e.previousId || null,
    });
  return emergencyApi.record(e.orgId, `duty/${e.recordId}/${e.kind === 'handover' ? 'HANDOVER' : 'ACCEPT'}`, value);
}
</script>

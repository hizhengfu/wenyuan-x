<template>
  <HPageForm content-only :enable-cancel="false"
    :show="show"
    title="岗位教育需求、参加与原件"
    @update:show="
      (v: boolean) => {
        if (!v) close();
      }
    "
  >
    <NSpace vertical>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NButton :loading="loading" @click="load">重新核对记录</NButton>
      <template v-if="data.id && !loading">
        <NText>{{ data.personName || data.userId }} · {{ educationStates[data.state] || data.state }}</NText>
        <NAlert v-if="data.historicalPerson" type="info"
          >历史人员依据任职原件补录，不表示其目前仍在岗或账号权限已恢复。</NAlert
        >
        <NAlert v-if="data.evidenceErrors?.length" type="error">{{ data.evidenceErrors.join('；') }}</NAlert>
        <NText>核实结论只覆盖本记录明确的岗位和技能，不自动取得任职或入库权限。</NText>
        <NSpace>
          <NButton v-if="data.canEdit" @click="edit('plan')">补充或更正岗位需求</NButton>
          <NButton v-if="data.canEdit && !data.content?.withdrawal" @click="edit('participation')"
            >登记或更正实际参加</NButton
          >
          <NButton
            v-if="data.canConfirm && data.content?.participation && !data.content?.withdrawal"
            @click="edit('review')"
            >本人核实本项需求</NButton
          >
          <NButton v-if="data.canConfirm && !data.content?.withdrawal" @click="edit('withdraw')"
            >依据原件终止本项需求</NButton
          >
        </NSpace>
        <NCollapse>
          <NCollapseItem
            v-for="v in data.versions"
            :key="v.record.version"
            :name="v.record.version"
            :title="`版本 ${v.record.version} · 登记人 ${v.record.registeredBy}`"
          >
            <CollectionEvidenceReadView
              v-if="v.record.version > 1"
              :fields="educationCorrectionFields"
              :data="v.record"
              :archive-files="(data.archives || []).filter((archive: any) => archive.phase === `V_${v.record.version}`).flatMap((archive: any) => archive.files || [])" :original-url="versionOriginalUrl(v.record.version)"
            />
            <NText v-else>首次登记岗位教育需求</NText>
            <CollectionEvidenceReadView :fields="educationPlanFields" :data="v.record.content.plan" :archive-files="(data.archives || []).filter((archive: any) => archive.phase === `V_${v.record.version}`).flatMap((archive: any) => archive.files || [])" :original-url="versionOriginalUrl(v.record.version)" />
            <CollectionEvidenceReadView
              v-if="v.record.content.participation"
              :fields="educationParticipationFields"
              :data="v.record.content.participation"
              :archive-files="(data.archives || []).filter((archive: any) => archive.phase === `V_${v.record.version}`).flatMap((archive: any) => archive.files || [])" :original-url="versionOriginalUrl(v.record.version)"
            />
            <template v-if="v.record.content.withdrawal">
              <NAlert type="warning">本项需求已终止，不计为培训完成。</NAlert>
              <CollectionEvidenceReadView
                :fields="educationCorrectionFields"
                :data="v.record.content.withdrawal"
                :archive-files="(data.archives || []).filter((archive: any) => archive.phase === `V_${v.record.version}`).flatMap((archive: any) => archive.files || [])" :original-url="versionOriginalUrl(v.record.version)"
              />
            </template>
            <template v-if="v.confirmation">
              <NText
                >核实人 {{ v.confirmation.actorUserId }}；{{
                  v.confirmation.content.meetsNeed ? '满足本项需求' : '仍需补充'
                }}</NText
              >
              <CollectionEvidenceReadView
                :fields="educationReviewFields"
                :data="{ ...v.confirmation.content, meetsNeed: v.confirmation.content.meetsNeed ? 'YES' : 'NO' }"
                :archive-files="(data.archives || []).filter((archive: any) => archive.phase === `REVIEW_${v.record.version}`).flatMap((archive: any) => archive.files || [])" :original-url="reviewOriginalUrl(v.record.version)"
              />
            </template>
          </NCollapseItem>
          <NCollapseItem v-for="a in data.archives" :key="a.id" :name="a.id" :title="`永久原件 · ${a.phase}`">
            <HFileList :files="a.files" :original-url="(fileId: string) => originalUrl(a, fileId)" :revision="[a?.id, a?.digest]" show-download-btn />
          </NCollapseItem>
        </NCollapse>
      </template>
    </NSpace>
    <CollectionEvidenceForm ref="formRef" :title="title" :fields="fields" :save="save" @saved="changed">
      <template #context
        ><NAlert type="info"
          >补充或更正会追加版本并保留旧原件，新版本须重新核实。课程已参加、课程已完成和满足本项岗位需求分别记录。</NAlert
        ></template
      >
    </CollectionEvidenceForm>
  </HPageForm>
</template>
<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { cloneDeep } from 'lodash-es';
import { staffEducationApi } from '../api/staffEducation';
import {
  educationStates,
  educationPlanFields,
  educationParticipationFields,
  educationCorrectionFields,
  educationReviewFields,
  educationPick,
} from '../utils/staffEducationFields';
import type { EvidenceField } from '../utils/evidenceFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
const props = defineProps<{ id: string }>();
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>();
const data = ref<any>({}),
  loading = ref(false),
  error = ref(''),
  formRef = ref<any>(),
  fields = ref<EvidenceField[]>([]),
  title = ref('');
const auth = useAuthStore();
const show = ref(true);
let alive = true;
const save = ref<(v: any) => Promise<any>>(async () => false);
const originalUrl = (archive: any, fileId: string) =>
  `/collection/staff-education/${props.id}/original/${archive.id}/${fileId}`;
const phaseOriginalUrl = (phase: string) => (fileId: string) => {
  const archive = (data.value.archives || []).find((a: any) => a.phase === phase && a.files?.some((f: any) => f.fileId === fileId));
  return archive ? originalUrl(archive, fileId) : '';
};
const versionOriginalUrl = (version: number) => phaseOriginalUrl(`V_${version}`);
const reviewOriginalUrl = (version: number) => phaseOriginalUrl(`REVIEW_${version}`);
let generation = 0;
function invalidate() {
  ++generation;
  formRef.value?.close();
  save.value = async () => false;
  data.value = {};
  fields.value = [];
  loading.value = false;
  error.value = '';
}
function close() {
  if (!show.value) return;
  show.value = false;
  invalidate();
  emit('close');
}
watch(() => auth.userInfo?.id, close, { flush: 'sync' });
watch(() => props.id, () => { void load(); }, { flush: 'sync' });
onBeforeUnmount(() => { alive = false; show.value = false; invalidate(); });
async function load() {
  if (!alive || !show.value) return false;
  invalidate();
  const token = generation, id = props.id, actor = auth.userInfo?.id;
  if (!id || !actor) return false;
  const current = () => alive && show.value && token === generation && id === props.id && actor === auth.userInfo?.id;
  loading.value = true;
  try {
    const r: any = await staffEducationApi.detail(id);
    if (!current()) return false;
    data.value = r.data;
    return true;
  } catch (e: any) {
    const businessMessage = e?.response?.data?.message;
    const localMessage = typeof e?.message === 'string' && /[\u3400-\u9fff]/.test(e.message) ? e.message : '';
    if (current()) error.value = typeof businessMessage === 'string' && businessMessage.trim()
      ? businessMessage
      : localMessage || '岗位教育记录读取失败，请核对当前权限和所属馆后重新读取';
    return false;
  } finally {
    if (current()) loading.value = false;
  }
}
async function edit(kind: 'plan' | 'participation' | 'review' | 'withdraw') {
  if (!alive || !show.value || loading.value) return;
  if (!await load()) return;
  const token = generation,
    basis = cloneDeep(data.value),
    id = props.id, actor = auth.userInfo?.id;
  if (!basis.id || error.value || basis.evidenceErrors?.length ||
    (kind !== 'plan' && basis.content?.withdrawal) || (kind === 'review' && !basis.content?.participation) || (kind === 'review' || kind === 'withdraw' ? !basis.canConfirm : !basis.canEdit))
    return;
  title.value = {
    plan: '补充或更正岗位需求',
    participation: '登记实际参加情况',
    review: '本人核实岗位技能需求',
    withdraw: '据实终止岗位需求',
  }[kind];
  fields.value =
    kind === 'plan'
      ? [...educationPlanFields, ...educationCorrectionFields]
      : kind === 'participation'
        ? [...educationParticipationFields, ...educationCorrectionFields]
        : kind === 'review'
          ? educationReviewFields
          : educationCorrectionFields;
  const initial =
    kind === 'plan'
      ? cloneDeep(basis.content.plan)
      : kind === 'participation'
        ? cloneDeep(basis.content.participation || {})
        : {};
  save.value = async (value: any) => {
    if (!alive || !show.value || token !== generation || id !== props.id || actor !== auth.userInfo?.id) return false;
    let result: any;
    if (kind === 'review')
      result = await staffEducationApi.confirm(id, basis.version, {
        ...educationPick(value, educationReviewFields),
        meetsNeed: value.meetsNeed === 'YES',
      });
    else {
      const content = cloneDeep(basis.content);
      if (kind === 'plan') {
        content.plan = educationPick(value, educationPlanFields);
        content.participation = null;
        content.withdrawal = null;
      } else if (kind === 'participation') content.participation = educationPick(value, educationParticipationFields);
      else content.withdrawal = { reason: value.reason, fileIds: value.fileIds };
      result = await staffEducationApi.revise(id, {
        expectedVersion: basis.version,
        content,
        reason: value.reason,
        fileIds: value.fileIds,
      });
    }
    return alive && show.value && token === generation && id === props.id && actor === auth.userInfo?.id ? result : false;
  };
  await nextTick();
  if (alive && show.value && token === generation && actor === auth.userInfo?.id) formRef.value?.open(initial);
}
async function changed() {
  const actor = auth.userInfo?.id, id = props.id;
  if (await load() && alive && show.value && actor === auth.userInfo?.id && id === props.id) emit('saved');
}
onMounted(load);
</script>

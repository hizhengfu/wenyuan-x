<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="事故应急搬移与真实记录"
    ><NAlert v-if="listError || readError" type="error">{{ listError || readError }}</NAlert
    ><NAlert v-if="contextError" type="warning">当前实物位置无法读取，原档案仍可查阅：{{ contextError }}</NAlert
    ><NSpin :show="loading"
      ><NSpace vertical :size="16">
      <CollectionArchiveIssues :data="data" />
        <NAlert type="info"
          >搬移不解除事故或来源业务限制。可安排的搬移先办理具体方案；已经紧急发生的事项如实补记实际搬出，再登记实际接收，不补造事前批准。</NAlert
        >
        <NCard v-for="item in items" :key="item.value" :title="item.label"
          ><NSpace>
            <NButton @click="create(item.value, 'plan')">登记专项搬移方案</NButton
            ><NButton @click="create(item.value, 'emergency')">补记已发生的紧急搬移</NButton>
          </NSpace></NCard
        >
        <NCard v-for="row in works" :key="row.id" :title="`搬移记录 ${row.id}`"
          ><NSpace
            ><NText>{{ states[row.state] }} · {{ row.mode === 'PLANNED' ? '事前方案' : '实际紧急补记' }}</NText
            ><NButton @click="openRelocation(row.id)">查看及办理</NButton></NSpace
          ></NCard
        >
        <template v-if="data.id">
          <NAlert v-if="actualLocation" type="info"
            >本件实物位置：{{
              actualLocation.inTransit
                ? '应急搬移在途，待实际接收'
                : actualLocation.locationName || actualLocation.locationId
            }}。原业务状态及回库依据另行保留。</NAlert
          >
          <NText>当前搬移：{{ data.id }}；{{ states[data.state] }}</NText
          ><NText>关联案卷：{{ data.incidentIds?.join('、') }}</NText>
          <NAlert v-if="data.mode === 'EMERGENCY_RECORD'" type="warning"
            >本项为已经发生事实的补记，没有本流程的事前批准记录。未知当时人员或不安全情况应如实保留，后续由责任人员核查。</NAlert
          >
          <NAlert v-if="data.factsChanged" type="warning"
            >当前事故事实或限制案卷已变化，旧方案不能授权新的搬出；已经发生的运输观察和接收仍可如实登记。</NAlert
          >
          <NSpace v-if="!contextError"
            ><NButton v-for="action in actions" :key="action.key" @click="edit(action.key, action.stage)">{{
              action.label
            }}</NButton></NSpace
          >
          <NAlert type="info"
            >方案依次由保护修复人员、该部门负责人、保管负责人、主要负责人本人核对。实际接收后由保护修复人员、保管负责人本人共同核对，并把本次搬移编号关联各事故的新事实版本，再完成报告处理及安全确认。历史补记保留更晚的实物位置。</NAlert
          >
          <CollectionEvidenceReadView v-if="data.plan" :fields="fieldsFor('plan')" :data="data.plan.plan" :archive-files="phaseFiles('PLAN')" :original-url="phaseOriginalUrl('PLAN')" />
          <CollectionEvidenceReadView
            v-if="data.emergency"
            :fields="fieldsFor('emergency')"
            :data="{ ...data.emergency.event, ...data.emergency.event.departure }"
            :archive-files="phaseFiles('EMERGENCY')" :original-url="phaseOriginalUrl('EMERGENCY')"
          />
          <NCard
            v-for="r in (data.records || []).filter((r: any) => !['PLAN', 'EMERGENCY'].includes(r.kind))"
            :key="r.id"
            :title="recordName(r)"
          >
            <NText v-if="r.kind === 'CONFIRMATION'"
              >本人签署：{{ r.content.actorUserId }}；{{ roleNames[r.content.role] }}；{{
                r.content.content.stage === 'PLAN' ? '方案确认' : '实际接收确认'
              }}</NText
            >
            <NText v-if="r.kind === 'RECEIPT'">{{
              r.content.updatesCurrentLocation ? '本次实际接收更新了当前位置' : '本次接收作为历史保留，未覆盖更晚位置'
            }}</NText>
            <CollectionEvidenceReadView
              :fields="fieldsFor(recordAction[r.kind] || 'confirm')"
              :data="
                r.kind === 'CONFIRMATION' ? r.content.content : r.kind === 'RECEIPT' ? r.content.receipt : r.content
              "
              :archive-files="phaseFiles(r.kind, r.id)" :original-url="phaseOriginalUrl(r.kind, r.id)"
            />
          </NCard>
          <NCard v-for="a in data.archives || []" :key="a.id" title="永久原件"
            ><HFileList :files="a.files || []" :original-url="(fileId: string) => originalUrl(a, fileId)" :revision="[a?.id, a?.digest]" show-download-btn /></NCard>
        </template> </NSpace></NSpin
  ></HPageForm>
  <CollectionEvidenceForm
    :key="formEpoch"
    ref="formRef"
    :title="formTitle"
    :fields="fields"
    :save="save"
    @saved="saved"
  />
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
const phaseFiles = (kind: string, recordId?: string) => {
  const id = recordId || (data.value.records || []).find((row: any) => row.kind === kind)?.id;
  return (data.value.archives || []).filter((archive: any) => archive.phase === `${kind}_${id}`).flatMap((archive: any) => archive.files || []);
};
import { onBeforeUnmount, ref, computed, watch, onMounted, nextTick } from 'vue';
import { cloneDeep } from 'lodash-es';
import { incidentRelocationApi as api } from '../api/incidentRelocation';
import {
  incidentRelocationFields,
  incidentRelocationValues,
  incidentRelocationStates as states,
} from '../utils/incidentRelocationFields';
import type { EvidenceField } from '../utils/evidenceFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
const props = defineProps<{ incidentId: string; items: { value: string; label: string }[] }>();
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>();
const actualLocation = ref<any>();
const show = ref(true),
  loading = ref(false),
  readError = ref(''),
  listError = ref(''),
  contextError = ref(''),
  formEpoch = ref(0),
  works = ref<any[]>([]),
  data = ref<any>({}),
  formRef = ref<any>(),
  fields = ref<EvidenceField[]>([]),
  formTitle = ref(''),
  locations = ref<{ label: string; value: string }[]>([]);
const originalUrl = (archive: any, fileId: string) =>
  `/collection/incident-relocation/${data.value.id}/original/${archive.id}/${fileId}`;
const phaseOriginalUrl = (kind: string, recordId?: string) => (fileId: string) => {
  const id = recordId || (data.value.records || []).find((r: any) => r.kind === kind)?.id;
  const archive = (data.value.archives || []).find((a: any) =>
    a.phase === `${kind}_${id}` && a.files?.some((f: any) => f.fileId === fileId));
  return archive ? originalUrl(archive, fileId) : '';
};
const titles: Record<string, string> = {
  plan: '登记专项搬移方案',
  emergency: '补记已发生的实际搬出',
  confirm: '本人核对完整原件',
  depart: '登记实际点交搬出',
  observation: '登记实际运输观察',
  receive: '登记实际接收及现状',
  cancel: '取消未实施搬移方案',
};
const recordAction: Record<string, string> = {
  DEPARTURE: 'depart',
  OBSERVATION: 'observation',
  RECEIPT: 'receive',
  CANCELED: 'cancel',
};
const roleNames: Record<string, string> = {
  CONSERVATION: '保护修复人员',
  DEPARTMENT: '部门负责人',
  KEEPER: '保管负责人',
  DIRECTOR: '主要负责人',
};
const recordName = (r: any) => titles[recordAction[r.kind]] || '本人签署原件';
const actions = computed(() => {
  const a: { key: string; label: string; stage?: string }[] = [];
  if (data.value.state === 'PLAN' && !data.value.factsChanged)
    a.push({ key: 'confirm', stage: 'PLAN', label: '本人核对搬移方案' });
  if (data.value.state === 'APPROVED' && !data.value.factsChanged) a.push({ key: 'depart', label: titles.depart });
  if (data.value.state === 'DEPARTED')
    a.push({ key: 'observation', label: titles.observation }, { key: 'receive', label: titles.receive });
  if (data.value.state === 'RECEIVED') a.push({ key: 'confirm', stage: 'END', label: '本人核对实际接收' });
  if (data.value.mode === 'PLANNED' && ['PLAN', 'APPROVED'].includes(data.value.state))
    a.push({ key: 'cancel', label: titles.cancel });
  return a;
});
let request = 0,
  creation = 0,
  listRequest = 0,
  revision = 0,
  target: any = {};
function fieldsFor(action: string, choices = locations.value) {
  return cloneDeep(incidentRelocationFields[action] || []).map<EvidenceField>((f) =>
    f.key.endsWith('LocationId')
      ? { ...f, options: choices }
      : action === 'receive' && data.value.mode === 'EMERGENCY_RECORD' && f.key === 'receivingUserIds'
        ? { ...f, type: 'textarea', multiple: false, label: '当时实际接收人员标识或姓名（逐行；未知可留空）' }
        : action === 'receive' && data.value.mode === 'EMERGENCY_RECORD' && f.key === 'custodianUserId'
          ? { ...f, type: 'text' }
          : f,
  );
}
async function load() {
  const token = ++listRequest,
    id = props.incidentId;
  works.value = [];
  listError.value = '';
  try {
    const r: any = await api.list(id);
    if (token !== listRequest || id !== props.incidentId || !show.value) return false;
    works.value = r.data || [];
    return true;
  } catch (error: any) {
    if (token === listRequest && id === props.incidentId && show.value) {
      listError.value = error?.message || '关联列表读取失败';
      data.value = {};
      target = {};
      formEpoch.value++;
      request++;
      creation++;
      loading.value = false;
    }
    return false;
  }
}
async function openRelocation(id: string) {
  const token = ++request;
  creation++;
  loading.value = true;
  data.value = {};
  readError.value = '';
  contextError.value = '';
  locations.value = [];
  actualLocation.value = undefined;
  try {
    const r: any = await api.detail(id);
    if (token !== request || !show.value) return false;
    if (!r?.data) throw new Error('办理记录不存在或当前不可读取');
    data.value = r.data;

    try {
      const context: any = await api.context(r.data.collectionId);
      if (token !== request || !show.value) return false;
      locations.value = context.data.locations || [];
      actualLocation.value = context.data.actualLocation;
    } catch (error: any) {
      if (token !== request || !show.value) return false;
      contextError.value = error?.message || '当前实物位置未能核实';
      target = {};
      formEpoch.value++;
    }
    return true;
  } catch (error: any) {
    if (token === request && show.value) {
      readError.value = error?.message || '办理资料读取失败';
      target = {};
      formEpoch.value++;
    }
    return false;
  } finally {
    if (token === request) loading.value = false;
  }
}
async function create(collectionId: string, action: 'plan' | 'emergency') {
  request++;
  formEpoch.value++;
  target = {};
  data.value = {};
  readError.value = '';
  contextError.value = '';
  const token = ++creation,
    id = props.incidentId;
  try {
    const r: any = await api.context(collectionId);
    if (token !== creation || id !== props.incidentId || !show.value) return false;
    target = { action, collectionId, incidentIds: [...(r.data.incidentIds || [])], parent: id, revision };
    fields.value = fieldsFor(action, r.data.locations || []);
    formTitle.value = titles[action];
    await nextTick();
    if (token !== creation || id !== props.incidentId || !show.value) return false;
    formRef.value?.open({});
    return true;
  } catch (error: any) {
    if (token === creation && id === props.incidentId && show.value)
      readError.value = error?.message || '无法读取当前实物和案卷范围';
    return false;
  }
}
async function edit(action: string, stage?: string) {
  if (loading.value || readError.value || listError.value || contextError.value || !data.value.id) return;
  target = {
    action,
    stage,
    id: data.value.id,
    parent: props.incidentId,
    revision,
    basisRevision: stage === 'PLAN' ? data.value.planBasis : data.value.endBasis,
  };
  fields.value = fieldsFor(action);
  formTitle.value = titles[action];
  await nextTick();
  formRef.value?.open({});
}
async function save(value: Record<string, any>) {
  if (!target.action || readError.value || listError.value || contextError.value) return false;
  const frozen = cloneDeep(target),
    selected = request,
    v = incidentRelocationValues(frozen.action, value);
  const here = () => frozen.revision === revision && frozen.parent === props.incidentId && selected === request;
  if (['plan', 'emergency'].includes(frozen.action)) {
    const body = { ...v, collectionId: frozen.collectionId, incidentIds: frozen.incidentIds };
    const r: any = await (frozen.action === 'plan' ? api.create(body) : api.emergency(body));
    if (!here() || !(await openRelocation(r.data))) return false;
    return r;
  }
  const r = await api.record(frozen.id, frozen.action, {
    ...v,
    ...(frozen.action === 'confirm' ? { stage: frozen.stage, basisRevision: frozen.basisRevision } : {}),
  });
  if (!here()) return false;
  if (data.value.id === frozen.id && !(await openRelocation(frozen.id))) return false;
  return r;
}
async function saved() {
  if (readError.value || !(await load())) return;
  emit('saved');
}
watch(
  show,
  (value) => {
    if (!value) {
      request++;
      creation++;
      listRequest++;
      formEpoch.value++;
      target = {};
      emit('close');
    }
  },
  { flush: 'sync' },
);
onBeforeUnmount(() => {
  request++;
  creation++;
  listRequest++;
});
watch(
  () => props.incidentId,
  () => {
    request++;
    creation++;
    revision++;
    loading.value = false;
    readError.value = '';
    contextError.value = '';
    formEpoch.value++;
    target = {};
    works.value = [];
    data.value = {};
    locations.value = [];
    actualLocation.value = undefined;
    void load();
  },
);
onMounted(load);
defineExpose({ openRelocation });
</script>

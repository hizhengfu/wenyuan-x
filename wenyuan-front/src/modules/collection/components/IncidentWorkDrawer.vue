<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="事故现场专项防护"
    ><NAlert v-if="listError || readError" type="error">{{ listError || readError }}</NAlert
    ><NAlert v-if="contextError" type="warning">当前实物位置无法读取，原档案仍可查阅：{{ contextError }}</NAlert
    ><NSpin :show="loading"
      ><NSpace vertical :size="16">
      <CollectionArchiveIssues :data="data" />
        <NAlert type="info"
          >本入口用于原地支撑、包护、固定、隔离和环境遮护。方案批准不解除事故限制，也不代办搬移、送修、复制或取样。已开始的作业应如实记录停止和结束。</NAlert
        >
        <NSpace v-if="!readError && !listError && !loading"
          ><NButton v-for="item in items" :key="item.value" @click="create(item.value)"
            >为 {{ item.label }} 登记方案</NButton
          ></NSpace
        >
        <NCard v-for="work in works" :key="work.id" :title="`防护作业 ${work.id}`"
          ><NSpace align="center"
            ><NText>{{ incidentWorkStates[work.state] }}</NText
            ><NButton @click="openWork(work.id)">查看及办理</NButton></NSpace
          ></NCard
        >
        <template v-if="data.id">
          <NText>当前作业：{{ data.id }}；{{ incidentWorkStates[data.state] }}</NText>
          <NText>本件全部限制案卷：{{ data.plan?.plan?.incidentIds?.join('、') }}</NText>
          <NAlert v-if="data.factsChanged" type="warning"
            >事故事实或限制案卷已变化，原方案不能授权新的操作。已经实际开始的，仍可如实登记观察、停止和结束；后续作业应依据当前事实另立方案。</NAlert
          >
          <NSpace>
            <NButton v-if="data.state === 'PLAN' && !data.factsChanged" @click="edit('confirm', 'PLAN')"
              >本人核对防护方案</NButton
            >
            <NButton v-if="data.state === 'APPROVED' && !data.factsChanged" @click="edit('start')"
              >登记实际开始</NButton
            >
            <NButton v-if="data.state === 'STARTED'" @click="edit('observation')">登记真实现场观察</NButton>
            <NButton v-if="data.state === 'STARTED'" @click="edit('end')">登记实际停止或结束</NButton>
            <NButton v-if="data.state === 'ENDED'" @click="edit('confirm', 'END')">本人核对实际结束</NButton>
            <NButton v-if="['PLAN', 'APPROVED'].includes(data.state)" @click="edit('cancel')">取消未实施方案</NButton>
          </NSpace>
          <NAlert type="info"
            >方案依次由保护修复人员、该部门负责人、保管负责人、主要负责人本人确认。实际结束由保护修复人员、保管负责人依次本人核对。实际防护结束结果须用作业编号关联各事故的新事实版本，再完成核查处理与安全确认；额外损伤、越界及中止情况均应如实保留。</NAlert
          >
          <CollectionEvidenceReadView :fields="incidentWorkFields.plan" :data="data.plan.plan" :archive-files="phaseFiles('PLAN')" :original-url="phaseOriginalUrl('PLAN')" />
          <NCard
            v-for="r in (data.records || []).filter((r: any) => r.kind !== 'PLAN')"
            :key="r.id"
            :title="recordTitle(r)"
          >
            <NText v-if="r.kind === 'CONFIRMATION'"
              >签署人：{{ r.content.actorUserId }}；{{ roleNames[r.content.role] }}；{{
                r.content.content.stage === 'PLAN' ? '方案确认' : '结束确认'
              }}</NText
            >
            <CollectionEvidenceReadView
              :fields="incidentWorkFields[r.kind === 'CONFIRMATION' ? 'confirm' : recordActions[r.kind]] || []"
              :data="r.kind === 'CONFIRMATION' ? r.content.content : r.content"
              :archive-files="phaseFiles(r.kind, r.id)" :original-url="phaseOriginalUrl(r.kind, r.id)"
            />
          </NCard>
          <NCollapse
            ><NCollapseItem v-for="a in data.archives || []" :key="a.id" :name="a.id" title="永久原件"
              ><HFileList :files="a.files || []" :original-url="(fileId: string) => originalUrl(a, fileId)" :revision="[a?.id, a?.digest]" show-download-btn /></NCollapseItem
          ></NCollapse>
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
import { ref, onMounted, watch, nextTick, onBeforeUnmount } from 'vue';
import { cloneDeep } from 'lodash-es';
import { incidentWorkApi as api } from '../api/incidentWork';
import { incidentWorkFields, incidentWorkValues, incidentWorkStates } from '../utils/incidentWorkFields';
import type { EvidenceField } from '../utils/evidenceFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
const props = defineProps<{ incidentId: string; items: { value: string; label: string }[] }>();
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>();
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
  formTitle = ref('');
const originalUrl = (archive: any, fileId: string) =>
  `/collection/incident-work/${data.value.id}/original/${archive.id}/${fileId}`;
const phaseOriginalUrl = (kind: string, recordId?: string) => (fileId: string) => {
  const id = recordId || (data.value.records || []).find((r: any) => r.kind === kind)?.id;
  const archive = (data.value.archives || []).find((a: any) =>
    a.phase === `${kind}_${id}` && a.files?.some((f: any) => f.fileId === fileId));
  return archive ? originalUrl(archive, fileId) : '';
};
const titles: Record<string, string> = {
  plan: '登记原地非损伤性防护方案',
  confirm: '本人核对原件并确认',
  start: '登记现场实际开始',
  observation: '登记真实现场观察',
  end: '登记实际停止或结束',
  cancel: '登记未实施取消',
};
const roleNames: Record<string, string> = {
  CONSERVATION: '保护修复人员',
  DEPARTMENT: '保护修复部门负责人',
  KEEPER: '保管部门负责人',
  DIRECTOR: '主要负责人',
};
const recordActions: Record<string, string> = {
  STARTED: 'start',
  OBSERVATION: 'observation',
  ENDED: 'end',
  CANCELED: 'cancel',
};
const recordTitle = (r: any) => (r.kind === 'CONFIRMATION' ? '本人签署原件' : titles[recordActions[r.kind]] || r.kind);
let request = 0,
  creation = 0,
  listRequest = 0,
  scopeRevision = 0;
let target: any = {};
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
async function openWork(id: string) {
  const token = ++request;
  creation++;
  loading.value = true;
  data.value = {};
  readError.value = '';
  contextError.value = '';
  try {
    const r: any = await api.detail(id);
    if (token !== request || !show.value) return false;
    if (!r?.data) throw new Error('办理记录不存在或当前不可读取');
    data.value = r.data;
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
async function create(collectionId: string) {
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
    target = {
      action: 'plan',
      parentCaseId: id,
      scopeRevision,
      collectionId,
      incidentIds: [...(r.data.incidentIds || [])],
    };
    fields.value = cloneDeep(incidentWorkFields.plan);
    formTitle.value = titles.plan;
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
    id: data.value.id,
    parentCaseId: props.incidentId,
    scopeRevision,
    action,
    stage,
    basisRevision: stage === 'PLAN' ? data.value.planBasis : data.value.endBasis,
  };
  fields.value = cloneDeep(incidentWorkFields[action]);
  formTitle.value = titles[action];
  await nextTick();
  formRef.value?.open({});
}
async function save(value: Record<string, any>) {
  if (!target.action || readError.value || listError.value || contextError.value) return false;
  const frozen = cloneDeep(target),
    selection = request,
    v = incidentWorkValues(value);
  const stillHere = () =>
    frozen.scopeRevision === scopeRevision && frozen.parentCaseId === props.incidentId && selection === request;
  if (frozen.action === 'plan') {
    const r: any = await api.create({ ...v, collectionId: frozen.collectionId, incidentIds: frozen.incidentIds });
    if (!stillHere() || !(await openWork(r.data))) return false;
    return r;
  }
  const r = await api.record(frozen.id, frozen.action, {
    ...v,
    ...(frozen.action === 'confirm' ? { stage: frozen.stage, basisRevision: frozen.basisRevision } : {}),
  });
  if (!stillHere()) return false;
  if (data.value.id === frozen.id && !(await openWork(frozen.id))) return false;
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
    scopeRevision++;
    loading.value = false;
    readError.value = '';
    contextError.value = '';
    formEpoch.value++;
    target = {};
    works.value = [];
    data.value = {};
    void load();
  },
);
onMounted(load);
defineExpose({ openWork });
</script>

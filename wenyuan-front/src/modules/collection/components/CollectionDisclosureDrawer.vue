<template>
  <HPageForm
    v-model:show="show"
    :title="`${data.name || '业务对象'} · 公开核查与实际公开`"
    content-only
    :enable-cancel="false"
  >
    <NSpin :show="loading"
      ><NSpace vertical :size="16">
      <CollectionArchiveIssues :data="data" />
        <NAlert type="info"
          >核查公开稿、向CMS供稿和实际公开分别记录。CMS应按本馆、对象类型及编号更新内容，并执行停止供稿指令；停止供稿不会证明官网或现场已经撤下。真实公开资料仍须按实际渠道核对。</NAlert
        >
        <NAlert v-if="readError" type="error">{{ readError }}</NAlert>
        <NButton :disabled="loading" @click="reload">重新读取核查资料</NButton>
        <NAlert v-if="!loading && !readError && !data.sourceReady" type="warning">{{
          data.statutorySubject
            ? '当前公示或公告尚缺完整的业务依据，请先核实专家评估或实际退出、交接原件。'
            : data.subject === 'RESEARCH_ACHIEVEMENT'
              ? '研究成果尚未通过审批或未开启内部展示，当前不能启用公开稿供稿；既有公开记录仍须按实际情况核对更新或撤下。'
              : '当前对象尚无可核实的正式登记或征集履行资料，或已转出、退出。可查阅原公开记录及办理实际撤下；不以旧发布标志继续输出内容。'
        }}</NAlert>
        <NButton v-if="data.sourceRevision" type="primary" @click="editReview">登记本次公开稿核查或不公开依据</NButton>
        <NAlert v-if="data.activePublications?.some((e: any) => e.needsUpdate)" type="warning"
          >已有公开记录与当前资料或核查版本不一致，请核实官网、现场等渠道的更新或撤下，保留实际凭据。</NAlert
        >
        <NText
          >参考公开渠道：{{
            (data.recommendedChannels || []).map(channelLabel).join('、')
          }}。逐个记录真实情况，不能把推荐渠道当作已经公开。</NText
        >
        <NAlert v-if="data.statutorySubject" type="info"
          >本页核查法定公开稿；实际公示或公告从对应退出、交换调拨单登记，三渠道和期限仍由原业务核对。</NAlert
        >
        <DeregistrationEvidenceList v-if="data.statutorySubject" :notices="data.statutoryNotices || []" />
        <NCollapse
          ><NCollapseItem
            v-for="row in [...records].reverse()"
            :key="row.id"
            :name="row.id"
            :title="`公开核查第 ${row.version} 版`"
            ><NSpace vertical :size="12">
              <NAlert :type="row.matchesCurrentSource ? 'info' : 'warning'">{{ disclosureStatus(row) }}</NAlert>
              <CollectionEvidenceReadView
                :fields="disclosureReviewFields()"
                :data="row.content"
                :archive-files="reviewArchiveFiles(row)" :original-url="reviewOriginalUrl(row)"
              />
              <NDescriptions :column="1" bordered
                ><NDescriptionsItem v-for="[key, label] in disclosureRestrictions" :key="key" :label="label"
                  >{{ handlingLabel(row.content.restrictions?.[key]?.handling) }}：{{
                    row.content.restrictions?.[key]?.explanation
                  }}</NDescriptionsItem
                ></NDescriptions
              >
              <NText v-if="row.content.withholdingReason">不公开依据：{{ row.content.withholdingReason }}</NText>
              <NCard v-if="row.content.content" title="本版实际核查的公开内容"
                ><NSpace vertical
                  ><NText strong>{{ row.content.content.title }}</NText
                  ><NText style="white-space: pre-wrap">{{ row.content.content.body }}</NText
                  ><NText>{{ row.content.content.feedbackInstructions }}</NText
                  ><NDescriptions :column="1"
                    ><NDescriptionsItem
                      v-for="(value, key) in row.content.content.facts"
                      :key="key"
                      :label="factLabel(String(key))"
                      >{{ value }}</NDescriptionsItem
                    ></NDescriptions
                  ><NCard
                    v-for="opinion in row.content.content.expertOpinions || []"
                    :key="opinion.sourceOpinionId"
                    :title="opinion.publicExpertLabel"
                    ><NText>{{ opinion.opinion }}</NText
                    ><NText>结论：{{ opinion.agree ? '同意退出' : '不同意退出' }}</NText></NCard
                  >
                  <NText v-for="url in row.content.content.imageUrls" :key="url"
                    >已核查公开图片地址：{{ url }}</NText
                  ></NSpace
                ></NCard
              >
              <HFileList :files="readonlyFiles(row.content.publicContentFileIds, reviewArchiveFiles(row))"
                    :original-url="reviewOriginalUrl(row)" :revision="[row.id, row.archives]" show-download-btn />
              <NSpace>
                <NPopconfirm
                  v-if="
                    row.id === records.at(-1)?.id &&
                    row.matchesCurrentSource &&
                    row.content.decision === 'PUBLIC' &&
                    !row.feedEnabledAt &&
                    !row.feedStoppedAt
                  "
                  :onPositiveClick="enableHandler(row)"
                  ><template #trigger><NButton>向CMS提供本版公开稿</NButton></template
                  >确认向CMS提供此处显示的已核查文字和图片地址？此操作不表示已经在官网公开。</NPopconfirm
                >
                <NButton v-if="row.feedEnabledAt && !row.feedStoppedAt" @click="editStop(row)">停止本版CMS供稿</NButton>
                <NButton
                  v-if="row.content.decision === 'PUBLIC' && !data.statutorySubject"
                  @click="editPublication(row)"
                  >登记本版实际公开凭据</NButton
                >
              </NSpace>
              <NCard
                v-for="e in row.events || []"
                :key="e.id"
                :title="`${e.content.action === 'PUBLISHED' ? '实际公开' : '实际撤下'} · ${channelLabel(e.content.channel)}`"
                ><CollectionEvidenceReadView
                  :fields="disclosureEventFields(e.content.action === 'WITHDRAWN')"
                  :data="e.content"
                  :archive-files="(e.archives || []).filter((archive: any) => archive.phase === e.content.action).flatMap((archive: any) => archive.files || [])" :original-url="eventOriginalUrl(row, e)"
                /><template v-for="a in e.archives || []" :key="a.id">
                  <HFileList :files="a.files" :original-url="(fileId: string) => originalUrl(row, a, fileId)" :revision="[a?.id, a?.digest]" show-download-btn /> </template
                ><NButton
                  v-if="(data.activePublications || []).some((a: any) => a.id === e.id)"
                  @click="editWithdrawal(row, e)"
                  >登记该次公开实际撤下</NButton
                ></NCard
              >
              <NCollapse
                ><NCollapseItem
                  v-for="a in row.archives || []"
                  :key="a.id"
                  :name="a.id"
                  :title="`内部留档 · ${a.phase}`"
                  ><HFileList :files="a.files" :original-url="(fileId: string) => originalUrl(row, a, fileId)" :revision="[a?.id, a?.digest]" show-download-btn /></NCollapseItem
                ></NCollapse
              >
            </NSpace></NCollapseItem
          ></NCollapse
        >
      </NSpace></NSpin
    >
  </HPageForm>
  <DisclosureReviewForm ref="reviewRef" :save="saveReview" @saved="reviewSaved" />
  <CollectionEvidenceForm
    ref="eventRef"
    :title="stageTitle"
    :fields="stage === 'stop' ? disclosureStopFields : disclosureEventFields(stage === 'withdraw')"
    :save="saveStage"
    @saved="stageSaved"
  />
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { readonlyFiles } from '../utils/readonlyFiles';
import { computed, ref, shallowRef, watch, nextTick, onActivated, onDeactivated, onBeforeUnmount } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import DeregistrationEvidenceList from './DeregistrationEvidenceList.vue';
const emit = defineEmits<{ (e: 'updated'): void }>();
import { disclosureApi } from '../api/disclosure';
import DisclosureReviewForm from './DisclosureReviewForm.vue';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import {
  disclosureReviewFields,
  disclosureEventFields,
  disclosureStopFields,
  disclosureRestrictions,
  disclosureHandlings,
  disclosureChannels,
  publicFacts,
  disclosureStatus,
} from '../utils/disclosureFields';
const auth = useAuthStore();
const show = ref(false),
  loading = ref(false),
  readError = ref(''),
  context = ref<any>({}),
  data = ref<any>({}),
  reviewRef = ref<any>(),
  eventRef = ref<any>();
const stage = ref<'publish' | 'withdraw' | 'stop'>('publish'),
  stageTitle = ref('登记实际公开原件'),
  enablePending = ref(false);
const saveStage = shallowRef<(value: any) => Promise<any>>(async () => false),
  saveReview = shallowRef<(org: string, subject: string, id: string, value: any) => Promise<any>>(async () => false);
const stageSaved = shallowRef<() => unknown>(afterSave),
  reviewSaved = shallowRef<() => unknown>(afterSave);
let request = 0,
  formRevision = 0,
  active = true;
const records = computed<any[]>(() => data.value.records || []);
const originalUrl = (row: any, archive: any, fileId: string) =>
  `/collection/disclosure/${data.value.orgId}/${data.value.subject}/${data.value.subjectId}/record/${row.id}/original/${archive.id}/${fileId}`;
const phaseOriginalUrl = (row: any, list: any[], phase: string) => (fileId: string) => {
  const archive = list.find((a: any) => a.phase === phase && a.files?.some((f: any) => f.fileId === fileId));
  return archive ? originalUrl(row, archive, fileId) : '';
};
const reviewArchiveFiles = (row: any) =>
  (row.archives || []).filter((archive: any) => archive.phase === 'REVIEWED').flatMap((archive: any) => archive.files || []);
const reviewOriginalUrl = (row: any) => phaseOriginalUrl(row, row.archives || [], 'REVIEWED');
const eventOriginalUrl = (row: any, event: any) => phaseOriginalUrl(row, event.archives || [], event.content.action);
const fileIds = (value: string | null | undefined): string[] => [
  ...new Set(
    (value || '')
      .split(/[,，;；\r\n]/)
      .map((p) => p.trim())
      .filter(Boolean),
  ),
];
const channelLabel = (key: string) => disclosureChannels.find((c) => c.value === key)?.label || key;
const handlingLabel = (key: string) => disclosureHandlings.find((c) => c.value === key)?.label || '未核实';
const factLabel = (key: string) => publicFacts.find(([k]) => k === key)?.[1] || key;
function closeForms() {
  ++formRevision;
  reviewRef.value?.close();
  eventRef.value?.close();
  saveStage.value = async () => false;
  saveReview.value = async () => false;
}
function clear() {
  ++request;
  closeForms();
  data.value = {};
  context.value = {};
  loading.value = false;
  readError.value = '';
  enablePending.value = false;
}
watch(
  show,
  (value) => {
    if (!value) clear();
  },
  { flush: 'sync' },
);
watch(
  () => auth.userInfo?.id,
  () => {
    show.value = false;
    clear();
  },
  { flush: 'sync' },
);
onActivated(() => {
  active = true;
});
onDeactivated(() => {
  active = false;
  show.value = false;
  clear();
});
onBeforeUnmount(() => {
  active = false;
  clear();
});
const currentForm = (token: number, actor: string | undefined, form: any) =>
  active && token === formRevision && actor === auth.userInfo?.id && form?.isOpen?.() !== false;
function receipt(result: any, expected?: string) {
  if (
    (result?.status !== undefined && result.status !== 200) ||
    typeof result?.data !== 'string' ||
    !result.data.trim() ||
    (expected && result.data !== expected)
  )
    throw Error('登记回执无法确认，请回原公开记录核对实际结果');
  return result;
}
async function open(value: { orgId: string; subject: string; subjectId: string }) {
  if (!active) return;
  context.value = { ...value };
  data.value = {};
  show.value = true;
  await reload();
}
async function reload() {
  if (!active || !show.value) return false;
  const token = ++request,
    c = { ...context.value };
  enablePending.value = false;
  loading.value = true;
  data.value = {};
  readError.value = '';
  try {
    const result: any = await disclosureApi.detail(c.orgId, c.subject, c.subjectId);
    if (!active || !show.value || token !== request) return false;
    const value = result?.data;
    if (
      (result?.status !== undefined && result.status !== 200) ||
      !value ||
      value.orgId !== c.orgId ||
      value.subject !== c.subject ||
      value.subjectId !== c.subjectId ||
      typeof value.sourceReady !== 'boolean' ||
      !Array.isArray(value.records) ||
      !Array.isArray(value.activePublications) ||
      value.records.some((r: any) => !r || typeof r.id !== 'string' || !r.id || !r.content) ||
      new Set(value.records.map((r: any) => r.id)).size !== value.records.length
    )
      throw Error('公开核查资料不完整或与本次业务对象不一致');
    data.value = value;
    return true;
  } catch {
    if (active && show.value && token === request) {
      closeForms();
      readError.value = '公开核查原件读取失败，请核对当前公开管理权限及原件后重试。';
    }
  } finally {
    if (token === request) loading.value = false;
  }
  return false;
}
async function afterSave() {
  if (await reload()) emit('updated');
}
function savedFor(c: any) {
  return () => {
    if (active && show.value && Object.keys(c).every((k) => context.value[k] === c[k])) return afterSave();
  };
}
function editReview() {
  if (!active || loading.value || readError.value || !data.value.sourceRevision) return;
  closeForms();
  const token = formRevision,
    actor = auth.userInfo?.id,
    c = { ...context.value };
  let pending = false;
  saveReview.value = async (org, subject, id, value) => {
    if (!currentForm(token, actor, reviewRef.value) || pending) return false;
    if (org !== c.orgId || subject !== c.subject || id !== c.subjectId)
      throw Error('公开核查对象已变化，请重新打开原表单');
    pending = true;
    try {
      const result = await disclosureApi.review(org, subject, id, value);
      if (!currentForm(token, actor, reviewRef.value)) return false;
      return receipt(result);
    } finally {
      pending = false;
    }
  };
  reviewSaved.value = savedFor(c);
  reviewRef.value?.open({
    ...c,
    sourceRevision: data.value.sourceRevision,
    revision: records.value.length > 0,
    ...(data.value.statutorySubject ? { expertOpinionIds: data.value.expertOpinionIds || [] } : {}),
  });
}
function enableHandler(row: any) {
  const token = request,
    actor = auth.userInfo?.id,
    c = { ...context.value },
    id = row.id;
  const current = () => active && show.value && token === request && actor === auth.userInfo?.id;
  return async () => {
    if (!current() || enablePending.value || !records.value.some((r) => r.id === id)) return false;
    enablePending.value = true;
    try {
      const result = await disclosureApi.enable(c.orgId, id);
      if (!current()) return false;
      receipt(result, id);
      await reload();
      return result;
    } catch (e) {
      if (current()) {
        closeForms();
        data.value = {};
        readError.value = e instanceof Error ? e.message : '供稿办理失败，请重新核查原记录';
      }
      return false;
    } finally {
      if (current()) enablePending.value = false;
    }
  };
}
async function editEvent(row: any, type: 'publish' | 'withdraw' | 'stop', event?: any) {
  if (!active || loading.value || readError.value || !records.value.some((r) => r.id === row.id)) return;
  closeForms();
  const token = formRevision,
    actor = auth.userInfo?.id,
    c = { ...context.value },
    id = row.id,
    publicationId = event?.id;
  let pending = false;
  stage.value = type;
  stageTitle.value = `${data.value.name || '业务对象'} · 第 ${row.version} 版 · ${type === 'stop' ? '停止向CMS供稿的原因' : type === 'withdraw' ? '登记实际撤下原件' : '登记实际公开原件'}`;
  saveStage.value = async (value) => {
    if (!currentForm(token, actor, eventRef.value) || pending) return false;
    pending = true;
    try {
      const result =
        type === 'stop'
          ? await disclosureApi.stop(c.orgId, id, value)
          : await disclosureApi.event(c.orgId, id, {
              ...value,
              action: type === 'publish' ? 'PUBLISHED' : 'WITHDRAWN',
              ...(type === 'withdraw' ? { revokesPublicationId: publicationId, publicContentFileIds: null } : {}),
            });
      if (!currentForm(token, actor, eventRef.value)) return false;
      return receipt(result, type === 'stop' ? id : undefined);
    } finally {
      pending = false;
    }
  };
  stageSaved.value = savedFor(c);
  await nextTick();
  if (active && token === formRevision)
    eventRef.value?.open(
      type === 'publish'
        ? { publicContentFileIds: row.content.publicContentFileIds }
        : type === 'withdraw'
          ? { channel: event.content.channel, location: event.content.location }
          : {},
    );
}
const editPublication = (row: any) => editEvent(row, 'publish');
const editWithdrawal = (row: any, event: any) => editEvent(row, 'withdraw', event);
const editStop = (row: any) => editEvent(row, 'stop');
defineExpose({ open });
</script>

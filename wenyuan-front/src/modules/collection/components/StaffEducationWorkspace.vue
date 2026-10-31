<template>
  <div class="flex h-full min-h-0 flex-col gap-3 overflow-auto p-3">
    <NAlert type="info"
      >按真实岗位需求办理培训与业务技能继续教育。期限、课程和时长依据馆方实际材料填写；没有全国统一学时或年度次数的预设值。</NAlert
    >
    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <NSpace align="center">
      <NSelect
        :value="orgId ?? null"
        :options="orgOptions"
        placeholder="选择收藏单位"
        style="width: 220px"
        @update:value="selectOrg"
      />
      <NSelect
        :value="userId"
        :options="personOptions"
        clearable
        filterable
        :placeholder="canViewAll === false ? '仅本人记录' : '筛选人员'"
        :disabled="!ready || canViewAll !== true"
        style="width: 220px"
        @update:value="selectPerson"
      />
      <NButton :loading="loading" @click="refresh">重新核对</NButton>
      <NButton v-if="page.canCreate && !error && !loading" type="primary" @click="create">登记岗位教育需求</NButton>
    </NSpace>
    <NAlert v-if="ready && canViewAll === false" type="info" class="education-read-scope">
      当前仅可查阅本人记录；不能据此判断本馆其他人员的培训或继续教育情况。
    </NAlert>
    <NDivider v-if="ready">{{ canViewAll ? '本馆可见岗位教育名册' : '本人岗位教育记录' }}</NDivider>
    <NText v-if="notice">{{ notice }}</NText>
    <NDataTable :columns="peopleColumns" :data="people" :loading="loading" :row-key="(r) => r.id" size="small" :scroll-x="870" />
    <NDivider>岗位教育办理记录</NDivider>
    <NDataTable :columns="columns" :data="page.list || []" :loading="loading" :row-key="(r) => r.id" size="small" :scroll-x="1090" />
    <NPagination :page="pageNum" :page-size="20" :item-count="page.total || 0" @update:page="selectPage" />
    <CollectionEvidenceForm
      :key="formEpoch"
      ref="formRef"
      title="登记岗位教育需求"
      :fields="createFields"
      :save="save"
      @change="formChanged"
      @saved="refresh"
    >
      <template #context
        ><NAlert type="info"
          >登记计划不会记作已参加或已完成。历史人员须由现任保管负责人或主要负责人依据任职原件补录；原账号不需要恢复权限。</NAlert
        ></template
      >
    </CollectionEvidenceForm>
    <StaffEducationDetailDrawer
      v-if="selectedId"
      :key="selectedId"
      :id="selectedId"
      @close="selectedId = undefined"
      @saved="refresh"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, h, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useAuthStore } from '@/store/modules/auth';
import { NButton } from 'naive-ui';
import { staffEducationApi } from '../api/staffEducation';
import {
  educationKinds,
  educationStates,
  educationPlanFields,
  educationPick,
  yesNo,
} from '../utils/staffEducationFields';
import type { EvidenceField } from '../utils/evidenceFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import StaffEducationDetailDrawer from './StaffEducationDetailDrawer.vue';
const orgId = ref<string>(),
  userId = ref<string | null>(),
  orgOptions = ref<any[]>([]),
  people = ref<any[]>([]),
  canViewAll = ref<boolean>(),
  page = ref<any>({}),
  pageNum = ref(1),
  notice = ref(''),
  error = ref(''),
  loading = ref(false),
  selectedId = ref<string>(),
  formRef = ref<any>(),
  historical = ref(false);
const auth = useAuthStore(), readActor = ref<string>(), formEpoch = ref(0);
let generation = 0;
const lifecycle = reactive({ alive: true, active: true });
const ready = computed(() => lifecycle.alive && lifecycle.active && !!orgId.value && !!readActor.value &&
  readActor.value === auth.userInfo?.id && !loading.value && !error.value);
const save = ref<(v: any) => Promise<any>>(async () => false);
function invalidateEditor() {
  ++formEpoch.value;
  formRef.value?.close();
  save.value = async () => false;
  historical.value = false;
}
function clearRecords() {
  readActor.value = undefined;
  page.value = {};
  people.value = [];
  canViewAll.value = undefined;
  notice.value = '';
}
function clearScope() {
  ++generation;
  invalidateEditor();
  clearRecords();
  orgOptions.value = [];
  orgId.value = undefined;
  userId.value = null;
  pageNum.value = 1;
  selectedId.value = undefined;
  loading.value = false;
  error.value = '';
}
watch(() => auth.userInfo?.id, clearScope, { flush: 'sync' });
onBeforeUnmount(() => { lifecycle.alive = false; clearScope(); });
onDeactivated(() => { lifecycle.active = false; clearScope(); });
onActivated(() => { if (lifecycle.active || !lifecycle.alive) return; lifecycle.active = true; void refresh(); });
function rowAction(rows: () => any[], row: any, action: () => void) {
  const token = generation, actor = readActor.value;
  return () => {
    if (ready.value && token === generation && actor === auth.userInfo?.id && rows().includes(row)) action();
  };
}
const personOptions = computed(() => people.value.map((p) => ({ label: p.name, value: p.id })));
const createFields = computed<EvidenceField[]>(() => [
  { key: 'historicalPerson', label: '是否为历史任职人员补录', type: 'select', options: yesNo, required: true },
  {
    key: 'userId',
    label: historical.value ? '原人员账号编号（无需启用账号）' : '实际人员',
    type: historical.value ? 'text' : 'user',
    required: true,
  },
  { key: 'employmentFileIds', label: '本馆实际任职与岗位依据', type: 'file', required: true },
  ...educationPlanFields,
]);
const peopleColumns = computed(() => [
  { title: '保管人员或已登记人员', key: 'name', width: 200, minWidth: 160, resizable: true, ellipsis: { tooltip: true } },
  {
    title: '已核实岗位培训',
    key: 'hasVerifiedPostTraining',
    width: 220,
    minWidth: 160,
    resizable: true,
    ellipsis: { tooltip: true },
    render: (r: any) => (r.hasVerifiedPostTraining ? '有记录，须核对适用岗位' : '尚无已核实证明'),
  },
  { title: '继续教育需求记录', key: 'continuingEducationNeeds', width: 180 },
  { title: '原件异常', key: 'evidenceIssueCount', width: 110 },
  {
    title: '操作',
    key: 'operate',
    width: 160,
    render: (r: any) => h(NButton, { size: 'small', onClick: rowAction(() => people.value, r, () => selectPerson(r.id)) }, () => '查看该人员记录'),
  },
].filter(column => column.key !== 'operate' || canViewAll.value !== false));
const columns = [
  { title: '人员', key: 'personName', width: 140, minWidth: 100, resizable: true, ellipsis: { tooltip: true } },
  {
    title: '类型',
    key: 'kind',
    width: 160,
    minWidth: 100,
    resizable: true,
    ellipsis: { tooltip: true },
    render: (r: any) => educationKinds.find((k) => k.value === r.content?.plan?.kind)?.label || '待核对',
  },
  { title: '岗位教育需求', key: 'title', width: 240, minWidth: 160, resizable: true, ellipsis: { tooltip: true }, render: (r: any) => r.content?.plan?.title || '原件待核对' },
  { title: '状态', key: 'state', width: 180, minWidth: 120, resizable: true, ellipsis: { tooltip: true }, render: (r: any) => educationStates[r.state] || r.state },
  {
    title: '期限提示',
    key: 'overdue',
    width: 240,
    minWidth: 180,
    resizable: true,
    ellipsis: { tooltip: true },
    render: (r: any) =>
      r.completedLate
        ? '实际完成晚于馆方期限'
        : r.completionInTimePendingReview
          ? '完成时间在期限内，待核实'
          : r.overdue
            ? '已超过馆方期限，须核实处理'
            : '--',
  },
  {
    title: '操作',
    key: 'operate',
    width: 130,
    render: (r: any) =>
      h(
        NButton,
        {
          size: 'small',
          onClick: rowAction(() => page.value.list || [], r, () => { selectedId.value = r.id; }),
        },
        () => '查看与办理',
      ),
  },
];
const formChanged = computed(() => {
  const epoch = formEpoch.value, actor = readActor.value;
  return (v: any) => {
    if (ready.value && epoch === formEpoch.value && actor === auth.userInfo?.id) historical.value = v.historicalPerson === 'YES';
  };
});
async function refresh() {
  if (!lifecycle.alive || !lifecycle.active || !auth.userInfo?.id) return false;
  const token = ++generation, actor = auth.userInfo.id;
  const current = () => lifecycle.alive && lifecycle.active && token === generation && actor === auth.userInfo?.id;
  invalidateEditor();
  clearRecords();
  orgOptions.value = [];
  loading.value = true;
  error.value = '';
  try {
    const context: any = await staffEducationApi.context();
    if (!current()) return false;
    if (!Array.isArray(context.data?.orgs)) throw new Error('收藏单位范围格式异常');
    orgOptions.value = context.data.orgs.map((o: any) => ({ label: o.name, value: o.id }));
    if (!orgOptions.value.some(o => o.value === orgId.value)) {
      orgId.value = undefined;
      userId.value = null;
      pageNum.value = 1;
      selectedId.value = undefined;
    }
    if (!orgId.value && orgOptions.value.length === 1) orgId.value = orgOptions.value[0].value;
    if (!orgId.value) return false;
    const org = orgId.value;
    const roster: any = await staffEducationApi.people(org);
    if (!current() || org !== orgId.value) return false;
    if (!Array.isArray(roster.data?.people)) throw new Error('岗位教育记录格式异常');
    if (typeof roster.data.canViewAll !== 'boolean') throw new Error('岗位教育查看范围未确认，请重新核对');
    if (!roster.data.canViewAll) {
      userId.value = null;
      pageNum.value = 1;
    }
    const response: any = await staffEducationApi.list({ orgId: org, userId: userId.value || undefined, pageNum: pageNum.value, pageSize: 20 });
    if (!current() || org !== orgId.value) return false;
    if (!Array.isArray(response.data?.list)) throw new Error('岗位教育记录格式异常');
    if (typeof response.data.canViewAll !== 'boolean') throw new Error('岗位教育查看范围未确认，请重新核对');
    if (roster.data.canViewAll !== response.data.canViewAll) throw new Error('读取期间岗位教育查看范围已变化，请重新核对');
    people.value = roster.data.people;
    canViewAll.value = response.data.canViewAll;
    notice.value = roster.data.notice;
    page.value = response.data;
    readActor.value = actor;
    return true;
  } catch (e: any) {
    if (current()) {
      error.value = `${e?.message || '岗位教育记录读取失败'}；不能据空表认定没有缺项`;
      selectedId.value = undefined;
    }
    return false;
  } finally {
    if (current()) loading.value = false;
  }
}
function selectOrg(id: string) {
  if (!lifecycle.alive || !lifecycle.active) return;
  orgId.value = id;
  userId.value = null;
  pageNum.value = 1;
  selectedId.value = undefined;
  void refresh();
}
function selectPerson(id: string | null) {
  if (!ready.value || canViewAll.value !== true) return;
  userId.value = id;
  pageNum.value = 1;
  selectedId.value = undefined;
  void refresh();
}
function selectPage(n: number) {
  if (!ready.value) return;
  pageNum.value = n;
  selectedId.value = undefined;
  void refresh();
}
async function create() {
  if (!ready.value || !page.value.canCreate) return false;
  const actor = auth.userInfo?.id, org = orgId.value;
  if (!await refresh() || actor !== auth.userInfo?.id || org !== orgId.value || !ready.value || !page.value.canCreate) return false;
  const token = generation, epoch = formEpoch.value, key = crypto.randomUUID();
  const current = () => ready.value && token === generation && epoch === formEpoch.value &&
    actor === auth.userInfo?.id && org === orgId.value;
  save.value = async (v: any) => {
    if (!current() || !formRef.value?.isOpen()) return false;
    const input = cloneDeep(v);
    try {
      const result: any = await staffEducationApi.create(org!, {
        requestKey: key,
        userId: input.userId,
        historicalPerson: input.historicalPerson === 'YES',
        employmentFileIds: input.employmentFileIds,
        plan: educationPick(input, educationPlanFields),
      });
      if (!current() || !formRef.value?.isOpen()) return false;
      selectedId.value = result.data;
      return result;
    } catch (e) { if (current() && formRef.value?.isOpen()) throw e; return false; }
  };
  await nextTick();
  if (current()) formRef.value?.open({});
  return current();
}
onMounted(refresh);
</script>

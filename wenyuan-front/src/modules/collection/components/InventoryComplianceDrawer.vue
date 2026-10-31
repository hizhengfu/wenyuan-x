<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="清点、离任移交与年度报送"
    ><NSpin :show="loadingConfigs"
      ><NAlert v-if="configsError" type="error">{{ configsError }}</NAlert>
      <NText v-else-if="loadingConfigs" depth="3">正在读取清点批次与方案，请稍候…</NText>
      <NSpace v-else-if="!configsError" vertical :size="18">
      <NAlert type="info"
        >清点须核验总登记账、实物、编目卡和照片影像。RFID
        观察记录用于辅助查找；请如实登记签署原件。年度报告须由主要负责人签字，每年12月底前报主管文物行政部门。</NAlert
      >
      <NButton @click="openKeeperHistory(false)">库管离任历史任职核对</NButton>
      <NButton @click="leadershipRef?.open(batch.orgId)">负责人离任交接案与历史任职核对</NButton>
      <NTabs type="line" v-model:value="tab">
        <NTabPane name="batch" tab="批次与离任移交">
          <NSpace vertical :size="16"
            ><NSelect
              :value="batchId"
              :options="batchOptions"
              placeholder="选择已配置批次"
              @update:value="selectBatch"
            />
            <NAlert v-if="!configs.length" type="warning">请先在盘点页面配置一个批次，再登记实际清点方案。</NAlert>
            <NAlert v-if="batchError" type="error">{{ batchError }}</NAlert>
            <template v-if="batch.id"
              ><NSpace>
                <NTag
                  :type="
                    batch.evidenceErrors?.length || batch.departureScopeErrors?.length
                      ? 'error'
                      : batch.completion && !inventoryDepartureLate(batch, batch.completion)
                        ? 'success'
                        : 'warning'
                  "
                  >{{ inventoryBatchStatus(batch) }}</NTag
                >
                <NButton
                  v-if="!batch.plan"
                  :disabled="!!batch.evidenceErrors?.length || !!batch.supersededByBatchId"
                  type="primary"
                  @click="edit('begin')"
                  >核实方案并开始清点</NButton
                >
                <NButton
                  v-if="batch.plan && !batch.completion"
                  :disabled="
                    !!batch.evidenceErrors?.length ||
                    !!batch.supersededByBatchId ||
                    !!batch.departureScopeErrors?.length
                  "
                  type="primary"
                  @click="edit('complete')"
                  >登记清点及移交签署结果</NButton
                >
              </NSpace>
              <NAlert v-if="batch.evidenceErrors?.length" type="error"
                >{{ batch.evidenceErrors.join('；') }}。请核对原档案，不要覆盖或删除旧记录。</NAlert
              >
              <NAlert v-if="batch.departureScopeErrors?.length" type="warning"
                >{{
                  batch.departureScopeErrors.join('；')
                }}。请保留原记录，核实完整责任后另建清点批次；原问题继续如实跟进。</NAlert
              >
              <NAlert v-if="batch.plan?.purpose === 'KEEPER_DEPARTURE' && !batch.baseline?.scopeSelection" type="info"
                >旧方案未留存范围选择方式，须结合原责任清单及签署材料核查完整范围，不能从当前位置推定。</NAlert
              >
              <NAlert v-if="batch.plan?.purpose && batch.plan.purpose !== 'ROUTINE'" type="warning"
                >清点、实际移交和结果签署应在离任前完成。迟办须如实填写实际日期、原因及处理原件；补录不会消除逾期事实。</NAlert
              >
              <CollectionEvidenceReadView
                v-if="batch.plan"
                :fields="inventoryPlanFields(batch.plan)"
                :data="batch.plan"
                :archive-files="currentBatchArchive('STARTED')?.files" :original-url="currentBatchOriginalUrl('STARTED')"
              />
              <NCard
                v-for="item in batch.baseline?.items || []"
                :key="item.collectionId"
                :title="item.content.formalName?.trim() || `藏品 ${item.collectionId}`"
                size="small"
              >
                <NSpace vertical
                  ><NText
                    >账册记录位置：{{ item.locationName }}；{{ item.precious ? '珍贵文物，重点核查；' : ''
                    }}{{ item.usedThisYear ? '当年提借用，重点核查；' : ''
                    }}{{ overdue(item) ? '来源已超过归库期限' : '' }}</NText
                  >
                  <NSpace
                    ><NButton
                      v-if="!batch.completion"
                      :disabled="!!batch.evidenceErrors?.length || !!batch.supersededByBatchId"
                      size="small"
                      @click="edit('check', item.collectionId)"
                      >{{ check(item.collectionId) ? '补充或更正实际核验' : '登记逐件账物卡照核验' }}</NButton
                    >
                    <NButton
                      v-if="check(item.collectionId) && (inventoryAbnormal(check(item.collectionId)) || overdue(item))"
                      size="small"
                      :disabled="!!batch.evidenceErrors?.length"
                      @click="edit('action', item.collectionId)"
                      >登记调查、处置及报告</NButton
                    >
                    <NButton
                      v-if="check(item.collectionId) && inventoryAbnormal(check(item.collectionId))"
                      v-permission="'COLLECTION_ADMIN,COLLECTION_INCIDENT_MANAGE'"
                      size="small"
                      @click="createIncident(item.collectionId)"
                      >登记事故报告与处置</NButton
                    >
                    <NButton
                      v-if="action(item.collectionId)?.incidentId"
                      size="small"
                      @click="incidentRef?.open(action(item.collectionId).incidentId)"
                      >查看关联事故案卷</NButton
                    >
                  </NSpace>
                  <NAlert v-if="check(item.collectionId) && inventoryAbnormal(check(item.collectionId))" type="warning"
                    >原核验异常继续留存。恢复可用须先完成本批次本件事故案卷的处理和方案责任人本人确认；尚未处理完毕可继续登记实际进展。</NAlert
                  >
                  <NAlert v-if="!check(item.collectionId)" type="warning">本件尚未完成实际核验。</NAlert>
                  <CollectionEvidenceReadView
                    v-else
                    :fields="inventoryCheckFields(check(item.collectionId))"
                    :data="check(item.collectionId)"
                    :archive-files="currentBatchArchive('CHECK_', item.collectionId)?.files" :original-url="currentBatchOriginalUrl('CHECK_', item.collectionId)"
                  />
                  <CollectionEvidenceReadView
                    v-if="action(item.collectionId)"
                    :fields="inventoryActionFields(action(item.collectionId))"
                    :data="action(item.collectionId)"
                    :archive-files="currentBatchArchive('ACTION_', item.collectionId)?.files" :original-url="currentBatchOriginalUrl('ACTION_', item.collectionId)"
                  />
                </NSpace>
              </NCard>
              <CollectionEvidenceReadView
                v-if="batch.completion"
                :fields="inventoryCompletionFields(batch)"
                :data="batch.completion"
                :archive-files="currentBatchArchive('COMPLETED')?.files" :original-url="currentBatchOriginalUrl('COMPLETED')"
              />
              <NCollapse
                ><NCollapseItem
                  v-for="archive in batch.archives || []"
                  :key="archive.id"
                  :name="archive.id"
                  :title="`原件留存 · ${formatLocalDateValue(archive.archivedAt, 'YYYY-MM-DD HH:mm:ss')}`"
                >
                  <NAlert v-if="!inventoryArchiveSnapshot(archive)" type="error"
                    >该档案正文无法解析，请核对原始记录；现有原件仍予保留。</NAlert
                  >
                  <HFileList :files="archive.files" :original-url="(fileId: string) => batchOriginalUrl(archive, fileId)" :revision="[archive?.id, archive?.digest]" show-download-btn />
                  <CollectionEvidenceReadView
                    v-if="archive.phase.startsWith('CHECK_')"
                    :fields="inventoryCheckFields(snapshot(archive))"
                    :data="snapshot(archive)"
                    :archive-files="archive.files" :original-url="archiveBatchOriginalUrl(archive)"
                  />
                  <CollectionEvidenceReadView
                    v-else-if="archive.phase.startsWith('ACTION_')"
                    :fields="inventoryActionFields(snapshot(archive))"
                    :data="snapshot(archive)"
                    :archive-files="archive.files" :original-url="archiveBatchOriginalUrl(archive)"
                  />
                  <CollectionEvidenceReadView
                    v-else-if="archive.phase === 'COMPLETED'"
                    :fields="inventoryCompletionFields(batch)"
                    :data="snapshot(archive).completion"
                    :archive-files="archive.files" :original-url="archiveBatchOriginalUrl(archive)"
                  />
                  <CollectionEvidenceReadView
                    v-else-if="!archive.phase.startsWith('INCIDENT_CLEARANCE_')"
                    :fields="inventoryPlanFields(snapshot(archive).plan)"
                    :data="snapshot(archive).plan"
                    :archive-files="archive.files" :original-url="archiveBatchOriginalUrl(archive)"
                  /><NText v-if="archive.phase.startsWith('INCIDENT_CLEARANCE_')"
                    >本次解限案卷：{{ snapshot(archive).incidentProof?.incidentId }}；对应处理结果：{{
                      snapshot(archive).incidentProof?.handlingId
                    }}</NText
                  >
                </NCollapseItem></NCollapse
              >
            </template>
          </NSpace>
        </NTabPane>
        <NTabPane name="annual" tab="年度覆盖与报送"
          ><NSpace vertical :size="16">
            <NSpace align="center"
              ><NInputNumber v-model:value="year" :min="1900" :max="new Date().getFullYear()" :precision="0" /><NButton
                :loading="busy"
                @click="loadAnnual"
                >核对年度覆盖</NButton
              ></NSpace
            >
            <NAlert v-if="annualError" type="error">{{ annualError }}</NAlert>
            <template v-if="coverage"
              ><NAlert type="info"
                >{{
                  inventoryAnnualStatus(coverage.year, reports)
                }}。覆盖依据为已签署批次和当前账册；本年度后续新增、移库、提借用仍须继续核查。</NAlert
              >
              <NDescriptions bordered :column="2"
                ><NDescriptionsItem label="正式藏品">{{ coverage.population.length }}</NDescriptionsItem
                ><NDescriptionsItem label="原件待核对批次">{{
                  batchNames(coverage.unverifiedBatchIds)
                }}</NDescriptionsItem
                ><NDescriptionsItem label="迟办离任移交批次">{{
                  batchNames(coverage.departureLateBatchIds)
                }}</NDescriptionsItem
                ><NDescriptionsItem label="离任范围待补核批次">{{
                  batchNames(coverage.departureScopeReviewBatchIds)
                }}</NDescriptionsItem
                ><NDescriptionsItem label="已核验藏品">{{ coverage.checkedCollectionIds.length }}</NDescriptionsItem
                ><NDescriptionsItem label="未覆盖类别">{{
                  coverage.missingCategories.join('、') || '无'
                }}</NDescriptionsItem
                ><NDescriptionsItem label="未覆盖地点">{{
                  Object.values(coverage.missingLocations).join('、') || '无'
                }}</NDescriptionsItem
                ><NDescriptionsItem label="待核查重点藏品">{{ names(coverage.uncheckedPriorityIds) }}</NDescriptionsItem
                ><NDescriptionsItem label="待核实类别或位置">{{ names(coverage.unclassifiedIds) }}</NDescriptionsItem
                ><NDescriptionsItem label="尚未登记差异处置">{{ names(coverage.unhandledIssueIds) }}</NDescriptionsItem
                ><NDescriptionsItem label="已处置但须继续跟进">{{
                  names(coverage.pendingIssueIds)
                }}</NDescriptionsItem></NDescriptions
              >
              <NAlert v-if="coverage.departureScopeReviewBatchIds.length" type="warning"
                >上述批次的真实逐件核验仍予留存和统计，其范围不足不能作为完整离任交接凭据；年度报告须如实说明核查及补办情况。</NAlert
              >
              <NButton type="primary" :disabled="!inventoryCoverageReady(coverage)" @click="edit('report')"
                >登记已签署年度报告与实际报送凭据</NButton
              >
              <NAlert v-if="!inventoryCoverageReady(coverage)" type="warning"
                >请先补齐上述覆盖和处置缺口，再登记年度报告。</NAlert
              >
              <NCard
                v-for="report in reports"
                :key="report.id"
                :title="`年度报告 · 第${report.version}次报送`"
                size="small"
                ><NAlert v-if="report.evidenceErrors?.length" type="error"
                  >{{ report.evidenceErrors.join('；') }}。原报送档案继续保留，不能认定有效。</NAlert
                >
                <template v-else
                  ><CollectionEvidenceReadView :fields="inventoryAnnualFields" :data="report.content || {}" :archive-files="(report.archives || []).filter((archive: any) => archive.phase === 'REPORTED').flatMap((archive: any) => archive.files || [])" :original-url="annualOriginalUrl(report)" /><NText
                    >当时纳入{{ report.coverage.completedBatchIds.length }}个已签署批次，待继续跟进{{
                      report.coverage.pendingIssueIds.length
                    }}件。</NText
                  ><NText v-if="report.coverage.departureScopeReviewBatchIds.length"
                    >当时报送所载离任范围待补核批次：{{
                      batchNames(report.coverage.departureScopeReviewBatchIds)
                    }}。</NText
                  ></template
                  ><template v-for="archive in report.archives || []" :key="archive.id">
                    <HFileList :files="archive.files || []" :original-url="(fileId: string) => annualArchiveUrl(report, archive, fileId)" :revision="[archive?.id, archive?.digest]" show-download-btn />
                  </template></NCard
              >
            </template>
          </NSpace></NTabPane
        >
      </NTabs>
    </NSpace></NSpin></HPageForm
  >
  <WarehouseStaffingDrawer ref="staffingRef" allow-prepare-inventory @prepare-inventory="prepareKeeperInventory" />
  <LeadershipDepartureDrawer ref="leadershipRef" @prepare-inventory="prepareLeadershipInventory" />
  <IncidentDrawer ref="incidentRef" @saved="reload" />
  <CollectionEvidenceForm v-if="!loadingConfigs && !configsError && !batchError && !annualError"
    ref="formRef"
    :title="titles[kind]"
    :fields="fields"
    :save="save"
    @change="draft = $event"
    @saved="reload"
    ><template #context
      ><NAlert v-if="kind === 'begin' && draft.purpose === 'KEEPER_DEPARTURE'" type="info"
        >离任须核对完整保管责任。专项清点请事先明确全部责任藏品编号，包含异地藏品；全面清点覆盖全馆。抽检和单个当前库位不能代替完整责任清点。</NAlert
      ><NAlert v-if="editingLeadership" type="info"
        >本方案为全馆负责人离任清点，关联 {{ editingLeadership.leavingUserName }} 离任交接案，接任人为
        {{
          editingLeadership.successorUserName
        }}。离任时间和责任人取自已核对原件，实际核验、签署及监交事实仍须另行登记。{{
          editingLeadership.replacesInventoryBatchId
            ? `本次重办保留原批次 ${editingLeadership.replacesInventoryBatchId}，请填写真实重办原因及依据。`
            : ''
        }}</NAlert
      >
      <NSpace v-if="editingKeeper.length" vertical>
        <NAlert type="info"
          >本次离任人员：{{ editingKeeper[0].leavingUserId }}；已选
          {{ editingKeeper.length }}
          个库房的历史任职依据。请核实完整责任清单，涵盖展出、借出等异地藏品。实际接任人、离任日期、交接范围和签署事实须另行填写。</NAlert
        >
        <NButton @click="openKeeperHistory(true)">补充其他库房的任职依据</NButton>
      </NSpace></template
    ></CollectionEvidenceForm
  >
</template>
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import WarehouseStaffingDrawer from './WarehouseStaffingDrawer.vue';
import type { KeeperInventorySource } from '../utils/inventoryEvidenceFields';
import LeadershipDepartureDrawer from './LeadershipDepartureDrawer.vue';
import { formatLocalDateValue } from '@/utils';
import { inventoryApi } from '../api/inventory';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import IncidentDrawer from './IncidentDrawer.vue';
import { cloneDeep } from 'lodash-es';
import {
  inventoryPlanFields,
  inventoryCheckFields,
  inventoryActionFields,
  inventoryCompletionFields,
  inventoryBatchStatus,
  inventoryArchiveSnapshot,
  inventoryDepartureLate,
  inventoryAnnualFields,
  inventoryInitial,
  inventoryPayload,
  inventoryAbnormal,
  inventoryCoverageReady,
  inventoryAnnualStatus,
  inventoryCoverageInitial,
} from '../utils/inventoryEvidenceFields';
const message = useMessage();
const staffingRef = ref<any>(),
  editingKeeper = ref<KeeperInventorySource[]>([]);
let appendKeeper = false;
const leadershipRef = ref<any>(),
  editingLeadership = ref<any>();
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  tab = ref('batch'),
  configs = ref<any[]>([]),
  batchId = ref<string>(),
  batch = ref<any>({}),
  year = ref<number | null>(new Date().getFullYear()),
  coverage = ref<any>(),
  reports = ref<any[]>([]),
  busy = ref(false),
  formRef = ref<any>(),
  incidentRef = ref<any>(),
  editingBatch = ref<any>({}),
  requiresIncident = ref(false),
  kind = ref('begin'),
  draft = ref<any>({}),
  collectionId = ref<string>();
const loadingConfigs = ref(false), configsError = ref(''), batchError = ref(''), annualError = ref('');
let openRequest = 0;
const titles: Record<string, string> = {
  begin: '核实清点范围与离任责任',
  check: '登记实际账物卡照核验',
  action: '登记实际调查、处置和报告',
  complete: '登记实际签署的清点与移交结果',
  report: '登记年度报告原件与实际报送凭据',
};
const batchOptions = computed(() => configs.value.map((b) => ({ label: b.batchName, value: b.id })));
const batchOriginalUrl = (archive: any, fileId: string) =>
  `/collection/inventory/batch/${batch.value.id}/original/${archive.id}/${fileId}`;
const archiveBatchOriginalUrl = (archive: any) => (fileId: string) =>
  archive.files?.some((f: any) => f.fileId === fileId) ? batchOriginalUrl(archive, fileId) : '';
const currentBatchArchive = (phase: string, itemId?: string) =>
  [...(batch.value.archives || [])].reverse().find((a: any) =>
    (phase.endsWith('_') ? a.phase?.startsWith(phase) : a.phase === phase) &&
    (!itemId || inventoryArchiveSnapshot(a)?.collectionId === itemId));
const currentBatchOriginalUrl = (phase: string, itemId?: string) => (fileId: string) => {
  const archive = currentBatchArchive(phase, itemId);
  return archive ? archiveBatchOriginalUrl(archive)(fileId) : '';
};
const annualArchiveUrl = (report: any, archive: any, fileId: string) =>
  `/collection/inventory/annual/${report.year}/report/${report.id}/original/${archive.id}/${fileId}`;
const annualOriginalUrl = (report: any) => (fileId: string) => {
  const archive = (report.archives || []).find((a: any) => a.phase === 'REPORTED' && a.files?.some((f: any) => f.fileId === fileId));
  return archive ? annualArchiveUrl(report, archive, fileId) : '';
};
const fields = computed(() =>
  kind.value === 'begin'
    ? inventoryPlanFields(draft.value)
        .filter(
          (f) =>
            !editingLeadership.value ||
            ![
              'leavingUserId',
              'successorUserId',
              'leavingAt',
              'keeperUserId',
              'leaderUserId',
              'leadershipDepartureId',
              'replacesInventoryBatchId',
              'method',
              'purpose',
            ].includes(f.key),
        )
        .filter(
          (f) =>
            !editingKeeper.value.length || !['leavingUserId', 'keeperStaffingChangeIds', 'purpose'].includes(f.key),
        )
        .map((f) =>
          editingKeeper.value.length && f.key === 'method'
            ? { ...f, options: f.options?.filter((o: any) => o.value !== 'SAMPLE') }
            : f,
        )
    : kind.value === 'check'
      ? inventoryCheckFields(draft.value)
      : kind.value === 'action'
        ? inventoryActionFields(draft.value, requiresIncident.value)
        : kind.value === 'complete'
          ? inventoryCompletionFields(editingBatch.value, draft.value)
          : inventoryAnnualFields,
);
const check = (id: string) => batch.value.checks?.items?.find((i: any) => i.collectionId === id);
const overdue = (item: any) => item.overdue || batch.value.overdueCollectionIds?.includes(item.collectionId);
const action = (id: string) => batch.value.issueActions?.items?.find((i: any) => i.collectionId === id);
const snapshot = (a: any) => inventoryArchiveSnapshot(a) || {};
function batchNames(ids: string[] = []) {
  return ids.map((id) => configs.value.find((b) => b.id === id)?.batchName || id).join('、') || '无';
}
function names(ids: string[]) {
  return (
    ids
      .map((id) => {
        const i = coverage.value.population.find((p: any) => p.collectionId === id);
        const name = i?.content?.formalName || '藏品名称未随本次结果留存';
        const totalRegNo = i?.content?.totalRegNo;
        return totalRegNo ? `${totalRegNo} ${name}` : `${name}（登记号未随本次结果留存）`;
      })
      .join('、') || '无'
  );
}
async function open(selected?: string, initialTab: 'batch' | 'annual' = 'batch', exactId?: string) {
  tab.value = initialTab;
  const token = ++openRequest;
  batchRequest++;
  annualRequest++;
  configs.value = [];
  batch.value = {};
  batchId.value = undefined;
  coverage.value = undefined;
  reports.value = [];
  configsError.value = batchError.value = annualError.value = '';
  busy.value = false;
  loadingConfigs.value = true;
  show.value = true;
  try {
    const result = ((await inventoryApi.batchConfigs()) as any)?.data || [];
    if (token !== openRequest) return;
    configs.value = result;
    const target = exactId ? configs.value.find((b) => b.id === exactId) : configs.value.find((b) => b.batchName === selected) || configs.value[0];
    if (exactId && !target) throw new Error('指定清点批次已不存在或当前不可读取');
    if (target) await selectBatch(target.id);
    if (token !== openRequest) return;
    await loadAnnual();
  } catch (error) {
    if (token === openRequest) configsError.value = error instanceof Error ? error.message : '清点批次读取失败，请核对当前权限';
  } finally {
    if (token === openRequest) loadingConfigs.value = false;
  }
}
let batchRequest = 0;
let editing = { batchId: '', kind: '', collectionId: '' };
async function selectBatch(id: string) {
  const token = ++batchRequest;
  batchId.value = id;
  batch.value = {};
  batchError.value = '';
  try {
    const result = (await inventoryApi.batch(id)) as any;
    if (token !== batchRequest) return false;
    batch.value = result?.data || {};
    return true;
  } catch (error) {
    if (token === batchRequest) batchError.value = error instanceof Error ? error.message : '清点详情读取失败，请核对原件及当前权限';
    return false;
  }
}
let annualRequest = 0;
async function loadAnnual() {
  if (!year.value) return false;
  coverage.value = undefined;
  reports.value = [];
  annualError.value = '';
  const token = ++annualRequest,
    requestedYear = year.value;
  busy.value = true;
  try {
    const [c, r] = await Promise.all([inventoryApi.coverage(requestedYear), inventoryApi.annualReports(requestedYear)]);
    if (token !== annualRequest || year.value !== requestedYear) return false;
    coverage.value = inventoryCoverageInitial((c as any).data);
    reports.value = ((r as any).data || []).map((report: any) => ({
      ...report,
      coverage: inventoryCoverageInitial(report.coverage),
    }));
    return true;
  } catch (error) {
    if (token === annualRequest) annualError.value = error instanceof Error ? error.message : '年度清点资料读取失败，请核对原件及当前权限';
    return false;
  } finally {
    if (token === annualRequest) busy.value = false;
  }
}
watch(year, () => {
  annualError.value = '';
  annualRequest++;
  busy.value = false;
  coverage.value = undefined;
  reports.value = [];
});
async function edit(k: string, id?: string, leadership?: any, keeper?: KeeperInventorySource) {
  editingKeeper.value = keeper ? [cloneDeep(keeper)] : [];
  editingLeadership.value = leadership ? cloneDeep(leadership) : undefined;
  editing = { batchId: batchId.value || '', kind: k, collectionId: id || '' };
  editingBatch.value = cloneDeep(batch.value);
  requiresIncident.value = !!id && !!check(id) && inventoryAbnormal(check(id));
  kind.value = k;
  collectionId.value = id;
  draft.value = inventoryInitial(
    k === 'begin'
      ? {
          year: year.value,
          method: keeper ? 'SPECIAL' : 'FULL',
          purpose: keeper ? 'KEEPER_DEPARTURE' : leadership ? 'DIRECTOR_DEPARTURE' : 'ROUTINE',
          ...(keeper
            ? {
                keeperStaffingChangeIds: [keeper.assignmentArchiveId],
                leavingUserId: keeper.leavingUserId,
                keeperUserId: keeper.keeperUserId,
                leaderUserId: keeper.leaderUserId,
              }
            : {}),
          scopeDescription: leadership?.request.responsibilityStatement || batch.value.scopeDesc,
          ...(leadership
            ? {
                leadershipDepartureId: leadership.id,
                replacesInventoryBatchId: leadership.replacesInventoryBatchId,
                leavingUserId: leadership.request.leavingUserId,
                successorUserId: leadership.request.successorUserId,
                leavingAt: leadership.request.leavingAt,
                keeperUserId: leadership.request.keeperUserId,
                leaderUserId: leadership.request.successorUserId,
                responsibilityFileIds: leadership.request.responsibilityFileIds,
              }
            : {}),
        }
      : k === 'check'
        ? { ...check(id!), collectionId: id, replacementReason: undefined }
        : k === 'action'
          ? { ...action(id!), collectionId: id }
          : k === 'complete'
            ? { keeperUserId: batch.value.plan.keeperUserId, leaderUserId: batch.value.plan.leaderUserId }
            : { year: coverage.value.year },
  );
  await nextTick();
  formRef.value?.open(draft.value);
}
async function prepareLeadershipInventory(value: { orgId: string; record: any; replacesInventoryBatchId?: string }) {
  if (!batch.value.id || batch.value.plan || batch.value.evidenceErrors?.length || batch.value.orgId !== value.orgId) {
    message.warning('请先选择同馆尚未开始且原件正常的清点批次，再从离任交接案填写方案');
    return;
  }
  await edit('begin', undefined, { ...value.record, replacesInventoryBatchId: value.replacesInventoryBatchId });
  leadershipRef.value?.close();
}
function openKeeperHistory(append: boolean) {
  appendKeeper = append;
  staffingRef.value?.open();
}
async function prepareKeeperInventory(value: KeeperInventorySource) {
  const selected = appendKeeper ? editingBatch.value : batch.value;
  if (
    !selected.id ||
    selected.plan ||
    selected.evidenceErrors?.length ||
    selected.orgId !== value.orgId ||
    selected.warehouseId
  ) {
    message.warning('请先配置并选择同馆未开始的完整责任清点批次；不得限定单个当前库位');
    return;
  }
  if (!value.assignmentArchiveId || !value.leavingUserId || !value.warehouseId) {
    message.warning('历史任职原件信息不完整，请重新核对');
    return;
  }
  if (appendKeeper && editingKeeper.value.length) {
    if (value.leavingUserId !== editingKeeper.value[0].leavingUserId) {
      message.warning('同次清点只能合并同一离任人员的任职依据');
      return;
    }
    const previous = editingKeeper.value.find((v) => v.warehouseId === value.warehouseId);
    if (previous && previous.assignmentArchiveId !== value.assignmentArchiveId) {
      message.warning('不能混用同一库房不同次离任的任职依据');
      return;
    }
    if (!previous) editingKeeper.value.push(cloneDeep(value));
    draft.value = { ...draft.value, keeperStaffingChangeIds: editingKeeper.value.map((v) => v.assignmentArchiveId) };
    await nextTick();
    formRef.value?.open(draft.value);
  } else await edit('begin', undefined, undefined, value);
  staffingRef.value?.close();
}
async function save(value: Record<string, any>) {
  const target = { ...editing };
  const payload = inventoryPayload(value);
  if (target.kind === 'report') return inventoryApi.annualReport(payload);
  if (target.collectionId) payload.collectionId = target.collectionId;
  return inventoryApi.compliance(target.batchId, target.kind, payload);
}
function createIncident(id: string) {
  const checked = check(id);
  incidentRef.value?.create({
    source: { bizType: 'COLLECTION_INVENTORY_BATCH', bizId: batchId.value },
    items: [{ collectionId: id, damaged: checked.damaged, major: action(id)?.major, findings: checked.findings }],
  });
}
async function reload() {
  const batchReady = batchId.value ? await selectBatch(batchId.value) : true;
  const annualReady = await loadAnnual();
  if (batchReady && annualReady) emit('saved');
}
defineExpose({ open });
</script>

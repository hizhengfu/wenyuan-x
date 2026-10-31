<template>
  <HPageForm v-model:show="show" :title="`${org.name || '收藏单位'} · 藏品建档与行政备案`" content-only :enable-cancel="false">
      <NSpace vertical :size="16">
      <CollectionArchiveIssues :data="[data, jurisdictions]" />
        <NAlert type="info"
          >实际建档与文物档案行政备案分别留档。已审核编目不能单独代替建档；一级文物及国家文物行政部门主管馆的文物档案，还须核对国家档案收存范围。各项报送和回执按真实原件登记。</NAlert
        >
        <NCollapse
          ><NCollapseItem title="本馆主管归属及文物档案实际接收部门">
            <NSpace vertical :size="12"
              ><NButton v-if="jurisdictionReady" @click="editJurisdiction">登记实际核实的主管归属依据</NButton>
              <NAlert v-if="jurisdictionError" type="error">{{ jurisdictionError }}</NAlert>
              <NAlert v-if="jurisdictionReady && !jurisdictions.some((j) => j.current)" type="warning"
                >尚无当前有效的档案备案归属依据，不能默认免除国家备案范围。</NAlert
              >
              <NCollapse
                ><NCollapseItem
                  v-for="j in [...jurisdictions].reverse()"
                  :key="j.id"
                  :name="j.id"
                  :title="`第 ${j.version} 版 · ${j.current ? '当前有效依据' : '其他版本（核对生效日期）'}`"
                  ><CollectionEvidenceReadView :fields="jurisdictionFields()" :data="j.content" :archive-files="stageFiles(j.archives, 'VERIFIED')" :original-url="fileId => originalUrl(j.archives, 'VERIFIED', fileId)" /></NCollapseItem
              ></NCollapse>
            </NSpace> </NCollapseItem
        ></NCollapse>
        <CollectionLedgerReferenceSelect :value="collectionId" :org-id="org.id" @update:value="(value) => select(value ?? undefined)" />
        <NAlert v-if="detailError" type="error">{{ detailError }}</NAlert>
        <NSpin :show="loading"
          ><template v-if="data.collectionId">
            <NText
              >{{ data.totalRegNo }} · {{ data.name
              }}{{ data.source === 'RETAINED_SOURCE_MUSEUM' ? ' · 原馆留存档案' : '' }}</NText
            >
            <NButton :disabled="!data.catalogVersions?.length" type="primary" @click="editPacket"
              >登记实际档案目录及编制核验原件</NButton
            >
            <NAlert v-if="!data.catalogVersions?.length" type="warning"
              >尚无本馆可对应的已审核编目版本，请先核实正式登记和历史建档依据。</NAlert
            >
            <NCollapse
              ><NCollapseItem
                v-for="row in [...records].reverse()"
                :key="row.id"
                :name="row.id"
                :title="`档案第 ${row.version} 版 · ${row.content.archiveNo}`"
              >
                <NSpace vertical :size="12">
                  <NAlert :type="row.coverageIssues?.length || !row.matchesCurrentCatalog ? 'warning' : 'info'">{{
                    archivePacketStatus(row)
                  }}</NAlert>
                  <NText>对应编目第 {{ row.source.catalogVersion.version }} 版 · {{ row.source.name }}</NText>
                  <CollectionEvidenceReadView :fields="archivePacketFields()" :data="row.content" :archive-files="stageFiles(row.archives, 'COMPILED')" :original-url="fileId => originalUrl(row.archives, 'COMPILED', fileId)" />
                  <NText v-if="row.coverageIssues?.length"
                    >待核实：{{ row.coverageIssues.map(archiveSectionLabel).join('、') }}</NText
                  >
                  <NDescriptions bordered :column="1"
                    ><NDescriptionsItem v-for="[key, label] in archiveSections" :key="key" :label="label"
                      ><span
                        >{{ sectionStatus(row.content.sections?.[key]?.status) }}：{{
                          row.content.sections?.[key]?.reference || '尚未登记目录依据'
                        }}</span
                      ><NSpace v-if="row.content.sections?.[key]?.fileIds"><HFileList :files="readonlyFiles(row.content.sections[key].fileIds, stageFiles(row.archives, 'COMPILED'))"
                  :original-url="(fileId: string) => originalUrl(row.archives, 'COMPILED', fileId)"
                  :revision="[row.id, row.archives]" show-download-btn /></NSpace></NDescriptionsItem
                  ></NDescriptions>
                  <NCollapse v-if="row.jurisdiction"
                    ><NCollapseItem title="该版实际报送时采用的主管归属依据"
                      ><CollectionEvidenceReadView
                        :fields="jurisdictionFields()"
                        :data="row.jurisdiction.content"
                        :archive-files="stageFiles(row.archives, 'JURISDICTION')" :original-url="fileId => originalUrl(row.archives, 'JURISDICTION', fileId)" /></NCollapseItem
                  ></NCollapse>
                  <NSpace
                    ><NButton v-for="target in pendingTargets(row)" :key="target" @click="editSubmission(row, target)"
                      >登记实际报送：{{ archiveTargetLabel(target) }}</NButton
                    ></NSpace
                  >
                  <NCard
                    v-for="submission in row.submissions || []"
                    :key="submission.id"
                    :title="archiveTargetLabel(submission.content.target)"
                  >
                    <CollectionEvidenceReadView :fields="archiveSubmissionFields" :data="submission.content" :archive-files="stageFiles(submission.archives, 'SUBMITTED')" :original-url="fileId => originalUrl(submission.archives, 'SUBMITTED', fileId)" />
                    <CollectionEvidenceReadView
                      v-if="submission.receipt"
                      :fields="archiveReceiptFields"
                      :data="submission.receipt"
                      :archive-files="stageFiles(submission.archives, 'FILED')" :original-url="fileId => originalUrl(submission.archives, 'FILED', fileId)"
                    />
                    <NButton v-else @click="editReceipt(submission)">登记该次实际备案回执</NButton>
                  </NCard>
                  <NCollapse
                    ><NCollapseItem
                      v-for="a in row.archives || []"
                      :key="a.id"
                      :name="a.id"
                      :title="`永久原件 · ${date(a.archivedAt)}`"
                      ><HFileList :files="a.files" :original-url="(fileId: string) => archiveOriginalUrl(a.id, fileId)" :revision="[a?.id, a?.digest]" show-download-btn /></NCollapseItem
                  ></NCollapse>
                </NSpace> </NCollapseItem
            ></NCollapse> </template
        ></NSpin>
      </NSpace>
  </HPageForm>
  <ArchivePacketForm ref="packetRef" :save="savePacket" @saved="reload" />
  <CollectionEvidenceForm ref="stageRef" :title="stageTitle" :fields="stageFields" :save="saveStage" @saved="refresh" />
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { readonlyFiles } from '../utils/readonlyFiles';
import { computed, ref, nextTick } from 'vue';
import { formatLocalDateValue } from '@/utils';
import CollectionLedgerReferenceSelect from './CollectionLedgerReferenceSelect.vue';
import ArchivePacketForm from './ArchivePacketForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import { archivePacketsApi } from '../api/archivePackets';
import {
  archiveSections,
  archiveSectionStatuses,
  archiveSectionLabel,
  archiveTargetLabel,
  jurisdictionFields,
  archivePacketFields,
  archiveSubmissionFields,
  archiveReceiptFields,
  archivePacketStatus,
} from '../utils/archivePacketFields';
const show = ref(false),
  loading = ref(false),
  detailError = ref(''),
  jurisdictionError = ref(''),
  jurisdictionReady = ref(false),
  org = ref<{ id: string; name?: string }>({ id: '' }),
  collectionId = ref<string>(),
  data = ref<any>({}),
  jurisdictions = ref<any[]>([]),
  packetRef = ref<any>(),
  stageRef = ref<any>(),
  stage = ref<'jurisdiction' | 'submission' | 'receipt'>('jurisdiction'),
  revision = ref(false);
let request = 0,
  jurisdictionRequest = 0,
  editingOrg = '',
  editingId = '';
const records = computed<any[]>(() => data.value.records || []);
const stageFields = computed(() =>
  stage.value === 'jurisdiction'
    ? jurisdictionFields(revision.value)
    : stage.value === 'submission'
      ? archiveSubmissionFields
      : archiveReceiptFields,
);
const stageTitle = computed(() =>
  stage.value === 'jurisdiction'
    ? '实际主管归属和档案受理依据'
    : stage.value === 'submission'
      ? '实际文物档案报送凭据'
      : '实际文物档案备案回执',
);
const date = (v: any) => (v == null ? '未登记' : formatLocalDateValue(v, 'YYYY-MM-DD HH:mm:ss'));
const files = (value: unknown): string[] => [...new Set((Array.isArray(value) ? value : [value]).flatMap(item =>
  typeof item === 'string' ? item.split(/[,，;；\r\n]/).map(v => v.trim()).filter(Boolean) : []))];
function archiveOriginalUrl(archiveId: string, fileId: string) {
  return `/collection/org/${encodeURIComponent(org.value.id)}/archives/original/${encodeURIComponent(archiveId)}/${encodeURIComponent(fileId)}`;
}
function originalUrl(archives: any[], phase: string, fileId: string) {
  const archive = (archives || []).find(item => item.phase === phase && item.files?.some((file: any) => file.fileId === fileId));
  return archive ? archiveOriginalUrl(archive.id, fileId) : '';
}
const stageFiles = (archives: any[] | undefined, phase: string) =>
  (archives || []).filter(archive => archive.phase === phase).flatMap(archive => archive.files || []);
const sectionStatus = (v: string) =>
  v === 'NOT_APPLICABLE' ? '不适用文物定级' : archiveSectionStatuses.find((s) => s.value === v)?.label || '未核实';
async function open(value: { orgId: string; orgName?: string; collectionId?: string }) {
  org.value = { id: value.orgId, name: value.orgName };
  collectionId.value = value.collectionId;
  data.value = {};
  jurisdictions.value = [];
  show.value = true;
  await Promise.all([reload(), reloadJurisdictions()]);
}
async function select(id?: string) {
  collectionId.value = id;
  data.value = {};
  await reload();
}
async function reload() {
  const token = ++request;
  data.value = {};
  detailError.value = '';
  if (!collectionId.value) {
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    const result: any = await archivePacketsApi.detail(org.value.id, collectionId.value);
    if (token === request) data.value = result.data;
  } catch (error: any) {
    if (token === request) detailError.value = error?.message || '档案读取失败，请核对当前权限及原记录';
  } finally {
    if (token === request) loading.value = false;
  }
}
async function reloadJurisdictions() {
  const token = ++jurisdictionRequest;
  jurisdictions.value = [];
  jurisdictionReady.value = false;
  jurisdictionError.value = '';
  try {
    const result: any = await archivePacketsApi.jurisdictions(org.value.id);
    if (token === jurisdictionRequest) {
      jurisdictions.value = result.data || [];
      jurisdictionReady.value = true;
    }
  } catch (error: any) {
    if (token === jurisdictionRequest)
      jurisdictionError.value = error?.message || '主管归属读取失败，请核对当前权限及原记录';
  }
}
async function refresh() {
  await Promise.all([reload(), reloadJurisdictions()]);
}
function editPacket() {
  packetRef.value?.open({
    orgId: org.value.id,
    collectionId: collectionId.value,
    versions: data.value.catalogVersions || [],
    currentVersionId: data.value.currentCatalogVersionId,
    revision: records.value.length > 0,
  });
}
async function editJurisdiction() {
  stage.value = 'jurisdiction';
  revision.value = jurisdictions.value.length > 0;
  editingOrg = org.value.id;
  editingId = '';
  await nextTick();
  stageRef.value?.open({});
}
const pendingTargets = (row: any) =>
  (row.requiredTargets || []).filter(
    (target: string) => !(row.submissions || []).some((s: any) => s.content.target === target),
  );
async function editSubmission(row: any, target: string) {
  stage.value = 'submission';
  editingOrg = org.value.id;
  editingId = row.id;
  await nextTick();
  const j = row.jurisdiction?.content;
  stageRef.value?.open(
    j
      ? { target, authorityName: target === 'NATIONAL' ? j.nationalAuthorityName : j.localArchiveAuthorityName }
      : { target },
  );
}
async function editReceipt(submission: any) {
  stage.value = 'receipt';
  editingOrg = org.value.id;
  editingId = submission.id;
  await nextTick();
  stageRef.value?.open({ authorityName: submission.content.authorityName });
}
const savePacket = (orgId: string, id: string, value: any) => archivePacketsApi.record(orgId, id, value);
const saveStage = (value: any) =>
  stage.value === 'jurisdiction'
    ? archivePacketsApi.jurisdiction(editingOrg, value)
    : stage.value === 'submission'
      ? archivePacketsApi.submission(editingOrg, editingId, value)
      : archivePacketsApi.receipt(editingOrg, editingId, value);
defineExpose({ open });
</script>

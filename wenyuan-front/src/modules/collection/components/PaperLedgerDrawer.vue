<template>
  <HPageForm v-model:show="show" :title="`${org.name || '收藏单位'} · 纸账保管与财务核对`" content-only :enable-cancel="false">
      <NSpace vertical :size="16">
      <CollectionArchiveIssues :data="[books, finance]" />
        <NAlert type="info"
          >纸质总登记账须实际永久保存并由专人保管。这里留存纸账保管和账账核对原件；扫描件、系统记录不能代替实物纸账。总账专管人员不得兼管藏品库房。</NAlert
        >
        <NSpace
          ><NButton secondary :loading="loading" @click="reloadBooks">重新读取纸账原件</NButton><NButton
            v-permission="'COLLECTION_ADMIN,COLLECTION_ORG_MANAGE,COLLECTION_LEDGER_CREATE,COLLECTION_LEDGER_CORRECT'"
            :disabled="loading || !!bookError"
            @click="editBook"
            >登记实际纸质总账册</NButton
          ><NButton v-permission="'COLLECTION_ADMIN,COLLECTION_LEDGER_FINANCE'" :disabled="loading || !!bookError" @click="loadFinance"
            >核对财务账</NButton
          ></NSpace
        >
        <NSpin :show="loading"
          ><NAlert v-if="bookError" type="error">{{ bookError }}</NAlert>
          <NEmpty v-if="!loading && !bookError && !books.length" description="尚无纸质总账原件保管记录" />
          <NCollapse
            ><NCollapseItem
              v-for="book in books"
              :key="book.id"
              :name="book.id"
              :title="`${book.bookNo} · ${book.book.title} · 永久保存`"
            >
              <NSpace vertical :size="12">
                <CollectionEvidenceReadView :fields="paperBookFields" :data="book.book" v-bind="currentBookReadProps(book)" />
                <PaperLedgerOriginalFiles :archives="book.archives" :original-url="bookOriginalUrl(book.id)" /><NCollapse
                  ><NCollapseItem title="初次登记原件资料（永久保留）"
                    ><CollectionEvidenceReadView :fields="paperBookFields" :data="book.originalBook" v-bind="bookReadProps(book.id, book.archives, 'REGISTERED')" /></NCollapseItem
                ></NCollapse>
                <NSpace
                  v-permission="
                    'COLLECTION_ADMIN,COLLECTION_ORG_MANAGE,COLLECTION_LEDGER_CREATE,COLLECTION_LEDGER_CORRECT'
                  "
                  ><NButton @click="editCustody(book, false)">记录实际保管核查</NButton
                  ><NButton @click="editCustody(book, true)">记录专管人员实际移交</NButton
                  ><NButton @click="editMetadata(book)">订正账册资料并留存依据</NButton></NSpace
                >
                <NCollapse
                  ><NCollapseItem
                    v-for="receipt in [...book.custodies].reverse()"
                    :key="receipt.id"
                    :name="receipt.id"
                    :title="`保管记录第 ${receipt.version} 版 · ${date(receipt.content.checkedAt)}`"
                  >
                    <NAlert v-if="receipt.content.condition === 'ISSUE'" type="warning"
                      >原件记载保存异常{{
                        receipt.overdue ? '，原定后续复核日期已过' : ''
                      }}，该历史事实继续保留。</NAlert
                    >
                    <CollectionEvidenceReadView
                      :fields="paperCustodyFields()"
                      v-bind="bookReadProps(book.id, receipt.archives, 'RECORDED')"
                      :data="receipt.content"
                    /><CollectionEvidenceReadView
                      v-if="receipt.content.correctedMetadata"
                      :fields="paperMetadataCorrectionFields"
                      v-bind="bookReadProps(book.id, receipt.archives, 'RECORDED')"
                      :data="{
                        ...receipt.content.correctedMetadata,
                        metadataCorrectionFileIds: receipt.content.metadataCorrectionFileIds,
                      }"
                    /><PaperLedgerOriginalFiles :archives="receipt.archives" :original-url="bookOriginalUrl(book.id)" /> </NCollapseItem
                ></NCollapse>
              </NSpace> </NCollapseItem></NCollapse
        ></NSpin>
        <NCard v-if="financeVisible" title="纸质总账与文物资源资产财务账实际核对">
          <NSpace vertical :size="14">
            <NAlert type="info"
              >只比较本次签署明细的登记号、名称、数量和单位，按实际范围核对。系统参照另列为留档时快照，不冒充核对日的历史总账；差异须依法处理后另留复核原件。</NAlert
            >
            <NButton secondary :loading="financeLoading" @click="reloadFinance">重新读取财务核对原件</NButton>
            <NButton :disabled="financeLoading || !!financeError || !books.length" @click="editFinance()">登记实际核对原件</NButton>
            <NAlert v-if="financeError" type="error">{{ financeError }}</NAlert>
            <NSpin :show="financeLoading"
              ><NCollapse
                ><NCollapseItem
                  v-for="record in [...finance].reverse()"
                  :key="record.id"
                  :name="record.id"
                  :title="`${record.content.referenceNo} · 第 ${record.version} 版 · ${reconciliationStatus(record)}`"
                >
                  <NSpace vertical :size="12">
                    <NAlert v-if="record.overdue" type="warning"
                      >此版本原定下次核对日期已过，请核实是否已有后续真实核对记录。</NAlert
                    >
                    <CollectionEvidenceReadView :fields="reconciliationFields()" :data="record.content" v-bind="financeReadProps(record)" />
                    <PaperLedgerOriginalFiles :archives="record.archives" :original-url="financeOriginalUrl(record.id)" />
                    <NText
                      >本次纸账册：{{
                        record.content.bookIds.map((id: string) => bookRefLabel(record, id)).join('、')
                      }}</NText
                    >
                    <NDataTable :columns="columns(record)" :data="record.content.rows" :pagination="{ pageSize: 15 }" :scroll-x="2160" />
                    <NText v-for="issue in record.differences" :key="issue.lineNo"
                      >第 {{ issue.lineNo }} 行：{{ issue.items.join('、') }}</NText
                    >
                    <NText v-for="(reason, line) in record.content.omittedLineReasons" :key="line"
                      >本次排除原误录行 {{ line }}：{{ reason }}</NText
                    >
                    <NAlert v-if="record.unlinkedLines?.length" type="warning"
                      >第
                      {{
                        record.unlinkedLines.join('、')
                      }}
                      行尚未关联系统记录。两账一致不代表已完成系统与纸账信息核对。</NAlert
                    >
                    <NCollapse
                      ><NCollapseItem title="留档时系统参照（不是核对日历史数据）"
                        ><NDataTable
                          :data="record.systemReferences"
                          :scroll-x="970"
                          :columns="referenceColumns"
                          :pagination="{ pageSize: 15 }" /></NCollapseItem
                    ></NCollapse>
                    <NButton v-if="isLatest(record)" @click="editFinance(record)">登记实际处理复核或订正原件</NButton>
                  </NSpace>
                </NCollapseItem></NCollapse
              ></NSpin
            >
          </NSpace>
        </NCard>
      </NSpace>
  </HPageForm>
  <CollectionEvidenceForm
    ref="paperRef"
    :title="paperTitle"
    :fields="paperFields"
    :save="savePaper"
    @saved="reloadBooks"
  />
  <LedgerReconciliationForm ref="financeRef" :save="saveFinance" @saved="reloadFinance" />
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { computed, ref, nextTick } from 'vue';
import { formatLocalDateValue } from '@/utils';
import { orgApi } from '../api/org';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import LedgerReconciliationForm from './LedgerReconciliationForm.vue';
import PaperLedgerOriginalFiles from './PaperLedgerOriginalFiles.vue';
import type { CollectionReadonlyFile } from '../utils/readonlyFiles';
import {
  paperBookFields,
  paperCustodyFields,
  paperBookPayload,
  paperMetadataCorrectionFields,
  paperMetadataCorrectionPayload,
  reconciliationFields,
  reconciliationStatus,
} from '../utils/paperLedgerFields';
const show = ref(false),
  org = ref<{ id: string; name?: string }>({ id: '' }),
  books = ref<any[]>([]),
  finance = ref<any[]>([]),
  loading = ref(false),
  bookError = ref(''),
  financeError = ref(''),
  financeLoading = ref(false),
  financeVisible = ref(false),
  paperRef = ref<any>(),
  financeRef = ref<any>();
const paperStage = ref<'book' | 'custody'>('book'),
  transfer = ref(false),
  metadataCorrection = ref(false);
let editingOrg = '',
  editingBook = '',
  bookRequest = 0,
  financeRequest = 0;
const paperFields = computed(() =>
  paperStage.value === 'book'
    ? [...paperBookFields, ...paperCustodyFields()]
    : [...paperCustodyFields(true, transfer.value), ...(metadataCorrection.value ? paperMetadataCorrectionFields : [])],
);
const paperTitle = computed(() =>
  paperStage.value === 'book'
    ? '实际纸质总登记账册原件'
    : metadataCorrection.value
      ? '实际账册资料订正'
      : transfer.value
        ? '实际纸账专管人员移交'
        : '实际纸账保管核查',
);
const date = (v: any) => (v == null ? '未登记' : formatLocalDateValue(v, 'YYYY-MM-DD HH:mm:ss'));
const bookOriginalUrl = (bookId: string) => (archiveId: string, fileId: string) =>
  `/collection/org/${encodeURIComponent(org.value.id)}/paper-ledger/${encodeURIComponent(bookId)}/original/${encodeURIComponent(archiveId)}/${encodeURIComponent(fileId)}`;
const financeOriginalUrl = (recordId: string) => (archiveId: string, fileId: string) =>
  `/collection/org/${encodeURIComponent(org.value.id)}/paper-ledger/reconciliations/${encodeURIComponent(recordId)}/original/${encodeURIComponent(archiveId)}/${encodeURIComponent(fileId)}`;
interface ReadonlyArchive {
  id: string;
  phase: string;
  files?: CollectionReadonlyFile[];
}
// A description field reads only its own frozen phase, even when file IDs recur in later versions.
function archivedReadProps(
  archives: ReadonlyArchive[] | undefined,
  phase: string,
  originalUrl: (archiveId: string, fileId: string) => string,
) {
  const matching = (archives || []).filter((archive) => archive.phase === phase);
  const archive = matching.length === 1 ? matching[0] : undefined;
  const archiveFiles = archive?.files || [];
  return {
    archiveFiles,
    originalUrl: (fileId: string) =>
      archive && archiveFiles.some((file) => (file.fileId || file.id) === fileId)
        ? originalUrl(archive.id, fileId)
        : undefined,
  };
}
const bookReadProps = (bookId: string, archives: ReadonlyArchive[] | undefined, phase: string) =>
  archivedReadProps(archives, phase, bookOriginalUrl(bookId));
function currentBookReadProps(book: any) {
  const correction = [...(book.custodies || [])]
    .sort((a, b) => b.version - a.version)
    .find((receipt) => receipt.content?.correctedMetadata);
  return bookReadProps(book.id, correction ? correction.archives : book.archives, correction ? 'RECORDED' : 'REGISTERED');
}
const financeReadProps = (record: any) =>
  archivedReadProps(record.archives, 'CHECKED', financeOriginalUrl(record.id));
const bookLabel = (id: string) => books.value.find((b) => b.id === id)?.bookNo || id;
const bookRefLabel = (record: any, id: string) => record.bookReferences?.[id]?.bookNo || '当时册号待核实';
const columns = (record: any) => [
  { title: '原行', key: 'lineNo', width: 80 },
  { title: '纸账册', key: 'bookId', width: 170, minWidth: 120, resizable: true, ellipsis: { tooltip: true }, render: (r: any) => bookRefLabel(record, r.bookId) },
  { title: '纸账页码', key: 'paperPageRef', width: 130, minWidth: 100, resizable: true, ellipsis: { tooltip: true } },
  { title: '财务记录位置', key: 'financialRecordRef', width: 220, minWidth: 140, resizable: true, ellipsis: { tooltip: true } },
  ...['paper', 'financial'].flatMap((side) => [
    { title: `${side === 'paper' ? '纸账' : '财务'}登记号`, key: `${side}.totalRegNo`, width: 160, minWidth: 120, resizable: true, ellipsis: { tooltip: true } },
    { title: `${side === 'paper' ? '纸账' : '财务'}名称`, key: `${side}.name`, width: 180, minWidth: 140, resizable: true, ellipsis: { tooltip: true } },
    { title: `${side === 'paper' ? '纸账' : '财务'}数量`, key: `${side}.quantity`, width: 90 },
    { title: `${side === 'paper' ? '纸账' : '财务'}单位`, key: `${side}.unit`, width: 90, minWidth: 65, resizable: true, ellipsis: { tooltip: true } },
  ]),
  { title: '原因核查', key: 'issueExplanation', width: 260, minWidth: 180, resizable: true, ellipsis: { tooltip: true } },
  { title: '处理安排', key: 'plannedAction', width: 260, minWidth: 180, resizable: true, ellipsis: { tooltip: true } },
];
const referenceColumns = [
  { title: '行号', key: 'lineNo', width: 80 },
  { title: '登记号', key: 'item.totalRegNo', width: 160, minWidth: 120, resizable: true, ellipsis: { tooltip: true } },
  { title: '名称', key: 'item.name', width: 180, minWidth: 140, resizable: true, ellipsis: { tooltip: true } },
  { title: '数量', key: 'item.quantity', width: 90 },
  { title: '单位', key: 'item.unit', width: 90, minWidth: 65, resizable: true, ellipsis: { tooltip: true } },
  {
    title: '参照来源',
    key: 'source',
    width: 180,
    minWidth: 140,
    resizable: true,
    ellipsis: { tooltip: true },
    render: (r: any) => (r.source === 'RETAINED_SOURCE_LEDGER' ? '原馆留存账' : '留档时当前主档'),
  },
  { title: '参照留档时间', key: 'capturedAt', width: 190, minWidth: 160, resizable: true, ellipsis: { tooltip: true }, render: (r: any) => date(r.capturedAt) },
];
async function open(value: { id: string; name?: string }) {
  org.value = { ...value };
  financeVisible.value = false;
  finance.value = [];
  books.value = [];
  financeRequest++;
  show.value = true;
  await reloadBooks();
}
async function reloadBooks() {
  const token = ++bookRequest;
  loading.value = true;
  books.value = [];
  bookError.value = '';
  try {
    const result: any = await orgApi.paperBooks(org.value.id);
    if (token === bookRequest) books.value = result.data || [];
  } catch {
    if (token === bookRequest) bookError.value = '纸账原件读取失败，请核对当前权限后重试。';
  } finally {
    if (token === bookRequest) loading.value = false;
  }
}
async function reloadFinance() {
  const token = ++financeRequest;
  financeLoading.value = true;
  finance.value = [];
  financeError.value = '';
  try {
    const result: any = await orgApi.ledgerReconciliations(org.value.id);
    if (token === financeRequest) finance.value = result.data || [];
  } catch {
    if (token === financeRequest) financeError.value = '财务核对原件读取失败，请核对当前财务权限后重试。';
  } finally {
    if (token === financeRequest) financeLoading.value = false;
  }
}
async function loadFinance() {
  financeVisible.value = true;
  await reloadFinance();
}
async function editBook() {
  editingOrg = org.value.id;
  editingBook = '';
  paperStage.value = 'book';
  transfer.value = false;
  metadataCorrection.value = false;
  await nextTick();
  paperRef.value?.open({});
}
async function editCustody(book: any, isTransfer: boolean) {
  editingOrg = org.value.id;
  editingBook = book.id;
  paperStage.value = 'custody';
  transfer.value = isTransfer;
  metadataCorrection.value = false;
  await nextTick();
  const latest = book.custodies.at(-1)?.content;
  paperRef.value?.open(isTransfer ? { fromKeeperUserId: latest?.keeperUserId } : {});
}
async function editMetadata(book: any) {
  editingOrg = org.value.id;
  editingBook = book.id;
  paperStage.value = 'custody';
  transfer.value = false;
  metadataCorrection.value = true;
  await nextTick();
  paperRef.value?.open({ ...book.book });
}
const savePaper = (value: any) =>
  paperStage.value === 'book'
    ? orgApi.registerPaperBook(editingOrg, paperBookPayload(value))
    : orgApi.recordPaperCustody(
        editingOrg,
        editingBook,
        metadataCorrection.value ? paperMetadataCorrectionPayload(value) : value,
      );
function editFinance(previous?: any) {
  financeRef.value?.open({ orgId: org.value.id, books: books.value, previous });
}
const saveFinance = (orgId: string, value: any) => orgApi.recordLedgerReconciliation(orgId, value);
const isLatest = (row: any) =>
  !finance.value.some((r) => r.content.referenceNo === row.content.referenceNo && r.version > row.version);
defineExpose({ open });
</script>

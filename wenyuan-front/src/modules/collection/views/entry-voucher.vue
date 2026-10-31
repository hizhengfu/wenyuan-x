<template>
  <div class="flex h-full min-h-0 flex-col gap-3">
    <nav class="collection-process" aria-label="入馆核收与登账办理顺序">
      <ol class="collection-process-steps">
        <li>凭证草稿</li>
        <li>实际逐件核收</li>
        <li>定级依据核对</li>
        <li>签署登账</li>
      </ol>
      <CollectionFieldLabel label="登账条件" help="征集审批通过和保存凭证草稿均不代表已正式登账。实际逐件核收、定级依据核对和签署完成后才可正式登账。" />
    </nav>
  <HPageDataTable export-label="导出明细（Excel）" auto-scroll-x
    class="collection-data-page min-h-0 flex-1"
    title="入馆核收与登账"
    ref="tableRef"
    :columns="columns"
    :fetch-data="entryVoucherApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="onReceipts"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="COLLECTION_ACCESSION_RECEIPT" /></template>
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="凭证号 / 依据 / 备注" />
      </NFormItem>
      <NFormItem label="凭证状态">
        <HDictSelect
          v-model:value="queryParams.voucherStatus"
          type="col_voucher_status"
          clearable
          placeholder="待核收 / 已核收待登记 / 已正式登账"
        />
      </NFormItem>
    </template>

    <NModal
      v-model:show="editShow"
      preset="card"
      :title="form.id ? '编辑入馆凭证' : '新建入馆凭证'"
      style="width: 720px"
    >
      <NForm ref="formRef" :model="form" label-placement="left" label-width="96">
        <NFormItem label="凭证号">
          <NInput :value="form.voucherNo || '保存后自动生成（RJ-日期-序号）'" disabled />
        </NFormItem>
        <NFormItem label="征集单" required>
          <NSelect
            v-model:value="selectedAcquisitionIds"
            multiple
            filterable
            :options="registrableOptions"
            placeholder="选择审批通过、待入馆的征集单"
          />
        </NFormItem>
        <NFormItem label="入藏依据" required>
          <NInput v-model:value="form.basis" placeholder="如：馆藏发展规划 / 征集批复文号" />
        </NFormItem>
        <NFormItem label="凭证草稿附件">
          <HUploadInput
            v-model:value="form.attachmentIds"
            @upload-finish="form.attachmentIds = $event.id"
            clearable
            placeholder="取得依据及凭证草稿附件；实际签署材料请从核收入口登记"
          />
        </NFormItem>
        <NAlert type="info" :bordered="false">
          先保存凭证草稿，再从“实际核收”逐件记录实收、核对及签署原件；凭证草稿中的计划信息不作为实收完成事实。
        </NAlert>
        <NFormItem label="备注">
          <NInput v-model:value="form.remark" type="textarea" :rows="2" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="editShow = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="onSave">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <AccessionReceiptDrawer ref="receiptRef" @saved="refreshTable" />
    <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />
  </HPageDataTable>
  </div>
</template>

<script setup lang="ts">
import CollectionFieldLabel from '../components/CollectionFieldLabel.vue';
import '../styles/collection-page.css';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { h, reactive, ref } from 'vue';
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui';
import { formatLocalDateValue } from '@/utils';
import { entryVoucherApi } from '../api/entryVoucher';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import AccessionReceiptDrawer from '../components/AccessionReceiptDrawer.vue';
import { receiptStatus } from '../utils/accessionReceiptFields';
import type { AcquisitionVo, CollectionPrintVo, EntryVoucherVo } from '../typings/collection';

const message = useMessage();
const receiptRef = ref<any>();
const tableRef = ref<any>(null);

const queryParams = reactive({
  keyword: null as string | null,
  voucherStatus: null as string | null,
});

const editShow = ref(false);
const saving = ref(false);
const form = reactive<EntryVoucherVo>({});
const selectedAcquisitionIds = ref<string[]>([]);
const registrableOptions = ref<{ label: string; value: string }[]>([]);
const printShow = ref(false);
const printData = ref<CollectionPrintVo | null>(null);

const columns = [
  { resizable: true, title: '凭证号', key: 'voucherNo', width: 170, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  { resizable: true, width: 220,
    title: '入藏藏品',
    key: 'acquisitionSummaries',
    minWidth: 220,
    ellipsis: { tooltip: true },
    render: (r: EntryVoucherVo) =>
      (r.acquisitionSummaries || [])
        .map((a: AcquisitionVo) => a.name)
        .filter(Boolean)
        .join('、') || '--',
  },
  {
    title: '征集条目数',
    key: 'count',
    width: 100,
    render: (r: EntryVoucherVo) => (r.acquisitionIds || '').split(',').filter(Boolean).length,
  },
  { resizable: true, title: '入藏依据', key: 'basis', width: 160, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '核收完成日',
    key: 'entryDate',
    width: 120,
    render: (r: EntryVoucherVo) => (r.voucherStatus === 'DRAFT' ? '待核收' : formatLocalDateValue(r.entryDate)),
  },
  {
    title: '状态',
    key: 'voucherStatus',
    width: 100,
    render: (r: EntryVoucherVo) =>
      h(NTag, { type: r.voucherStatus === 'REGISTERED' ? 'success' : 'warning', size: 'small' }, () =>
        receiptStatus(r.voucherStatus),
      ),
  },
  { title: '创建时间', key: 'createTime', width: 180, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 280,
    fixed: 'right' as const,
    render: (row: EntryVoucherVo) => {
      const btns: any[] = [];
      btns.push(h(NButton, { text: true, size: 'small', onClick: () => onPrint(row) }, () => '打印凭证'));
      btns.push(
        h(
          NButton,
          { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onReceipts(row) },
          () => '实际核收',
        ),
      );
      if (row.voucherStatus === 'DRAFT') {
        btns.push(
          h(
            NButton,
            { text: true, type: 'primary', size: 'small', style: 'margin-left:10px', onClick: () => onEdit(row) },
            () => '编辑',
          ),
        );
        btns.push(
          h(
            NPopconfirm,
            { onPositiveClick: () => onDelete(row) },
            {
              trigger: () =>
                h(NButton, { text: true, type: 'error', size: 'small', style: 'margin-left:10px' }, () => '删除'),
              default: () => '仅没有实际核收记录的凭证可以删除，确认删除？',
            },
          ),
        );
      }
      return btns;
    },
  },
];

async function loadRegistrable(extra?: AcquisitionVo[]) {
  const resp: any = await entryVoucherApi.registrable();
  const list: AcquisitionVo[] = resp?.data ?? [];
  const all = [...list];
  (extra || []).forEach((a) => {
    if (a.id && !all.some((x) => x.id === a.id)) all.push(a);
  });
  registrableOptions.value = all.map((a) => ({
    label: `${a.name || '--'}（${a.holder || '未知持有人'} / ${a.quantity ?? 1}${a.unit || '件'}）`,
    value: a.id!,
  }));
}

async function onAdd() {
  Object.assign(form, {
    id: undefined,
    voucherNo: undefined,
    basis: undefined,
    entryDate: null,
    handoverUser: undefined,
    receiverUser: undefined,
    custodian: undefined,
    warehouseId: undefined,
    attachmentIds: undefined,
    remark: undefined,
  });
  selectedAcquisitionIds.value = [];
  await loadRegistrable();
  editShow.value = true;
}

async function onEdit(row: EntryVoucherVo) {
  const receipt: any = await entryVoucherApi.receipts(row.id!);
  if (receipt.data?.items?.some((item: any) => item.history?.length)) {
    message.warning('已有实际核收原件，请通过实际核收入口补充或订正清册');
    return;
  }
  const resp: any = await entryVoucherApi.get(row.id!);
  const data: EntryVoucherVo = resp?.data ?? row;
  Object.assign(form, data);
  selectedAcquisitionIds.value = (data.acquisitionIds || '').split(',').filter(Boolean);
  await loadRegistrable(data.acquisitionSummaries);
  editShow.value = true;
}

async function onSave() {
  if (!selectedAcquisitionIds.value.length) {
    message.warning('请选择入凭证的征集单');
    return;
  }
  saving.value = true;
  try {
    await entryVoucherApi.save({
      ...form,
      acquisitionIds: selectedAcquisitionIds.value.join(','),
    });
    message.success('保存成功');
    editShow.value = false;
    refreshTable();
  } finally {
    saving.value = false;
  }
}

function onReceipts(row: EntryVoucherVo) {
  receiptRef.value?.open(row.id!);
}

async function onDelete(row: EntryVoucherVo) {
  await entryVoucherApi.delete(row.id!);
  message.success('已删除');
  refreshTable();
}

async function onPrint(row: EntryVoucherVo) {
  const resp: any = await entryVoucherApi.print(row.id!);
  printData.value = resp?.data ?? null;
  printShow.value = true;
}

function refreshTable() {
  tableRef.value?.handleRefresh();
  tableRef.value?.handleQuery?.();
}
useCollectionSearchRecord('/collection/entry-voucher', 'ENTRY_VOUCHER', id => receiptRef.value?.open(id));
</script>

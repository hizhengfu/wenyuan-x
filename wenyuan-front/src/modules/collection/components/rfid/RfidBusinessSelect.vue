<template>
  <NSelect
    :value="value"
    :options="options"
    :loading="loading"
    :disabled="disabled"
    :placeholder="placeholder || defaultPlaceholder"
    :filter="filterOption"
    filterable
    clearable
    :fallback-option="false"
    @update:value="updateValue"
  />
</template>

<script setup lang="ts">
import type { SelectOption } from 'naive-ui';
import { computed, ref, watch } from 'vue';
import { inventoryApi } from '../../api/inventory';
import { loanApi } from '../../api/loan';
import { requisitionApi } from '../../api/requisition';
import { collectionReturnApi } from '../../api/return';
import type {
  CollectionReturnVo,
  InventoryBatchVo,
  LoanVo,
  RequisitionVo,
} from '../../typings/collection';
import type { RfidSessionType } from '../../typings/rfid';

type RfidBusinessKind =
  | RfidSessionType
  | 'COLLECTION_LOAN'
  | 'COLLECTION_REQUISITION'
  | 'COLLECTION_RETURN';
type RfidBusinessRecord = LoanVo | RequisitionVo | CollectionReturnVo | InventoryBatchVo;

type BusinessOption = SelectOption & {
  value: string;
  searchText: string;
  record: RfidBusinessRecord;
};

const props = withDefaults(defineProps<{
  value?: string | null;
  kind: RfidBusinessKind;
  purpose?: 'SESSION' | 'GRANT';
  collectionId?: string | null;
  placeholder?: string;
  disabled?: boolean;
}>(), {
  value: null,
  purpose: 'SESSION',
  collectionId: null,
  placeholder: '',
  disabled: false,
});

const emit = defineEmits<{
  (e: 'update:value', value: string | null): void;
  (e: 'selected', value: RfidBusinessRecord | null): void;
}>();

const loading = ref(false);
const records = ref<RfidBusinessRecord[]>([]);
let requestSequence = 0;

const defaultPlaceholder = computed(() => props.kind === 'INVENTORY' ? '选择已配置盘点批次' : '选择已审批业务单据');
const options = computed<BusinessOption[]>(() => records.value
  .filter((row) => includesCollection(row, props.collectionId))
  .map((row) => buildOption(row, props.kind))
  .filter((option): option is BusinessOption => Boolean(option)));

watch(() => [props.kind, props.purpose] as const, () => void load(), { immediate: true });

async function load() {
  const sequence = ++requestSequence;
  loading.value = true;
  try {
    const response: any = await queryByKind(props.kind);
    if (sequence !== requestSequence) return;
    const list = normalizeList<RfidBusinessRecord>(response);
    records.value = list.filter((row) => isEligible(row, props.kind, props.purpose));
    emitSelected(props.value);
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

function queryByKind(kind: RfidBusinessKind) {
  switch (kind) {
    case 'LOAN_OUT':
    case 'COLLECTION_LOAN':
      return loanApi.page({ pageSize: 0, flowStatus: 'PASS' });
    case 'REQUISITION_OUT':
    case 'COLLECTION_REQUISITION':
      return requisitionApi.page({ pageSize: 0, flowStatus: 'PASS' });
    case 'RETURN_IN':
    case 'COLLECTION_RETURN':
      return collectionReturnApi.page({ pageSize: 0, flowStatus: 'PASS' });
    case 'INVENTORY':
      return inventoryApi.batchConfigs();
    default:
      return Promise.resolve({ data: [] });
  }
}

function normalizeList<T>(response: any): T[] {
  const value = response?.data?.list ?? response?.data ?? [];
  return Array.isArray(value) ? value : [];
}

function isEligible(row: RfidBusinessRecord, kind: RfidBusinessKind, purpose: 'SESSION' | 'GRANT') {
  if (kind === 'INVENTORY') return !(row as InventoryBatchVo).finishedAt;
  if (kind === 'LOAN_OUT' || kind === 'COLLECTION_LOAN') {
    const item = row as LoanVo;
    return purpose === 'GRANT' ? item.outExecuted === true && item.returned !== true : item.outExecuted !== true;
  }
  if (kind === 'REQUISITION_OUT' || kind === 'COLLECTION_REQUISITION') {
    const item = row as RequisitionVo;
    return purpose === 'GRANT' ? item.outExecuted === true && item.returned !== true : item.outExecuted !== true;
  }
  if (kind === 'RETURN_IN' || kind === 'COLLECTION_RETURN') {
    return (row as CollectionReturnVo).executionState === 'PENDING';
  }
  return true;
}

function includesCollection(row: RfidBusinessRecord, collectionId?: string | null) {
  if (!collectionId) return true;
  const item = row as LoanVo | RequisitionVo | CollectionReturnVo;
  if (item.collectionSummaries?.some((collection) => collection.id === collectionId)) return true;
  return item.collectionIds?.split(',').map((id) => id.trim()).includes(collectionId) === true;
}

function buildOption(row: RfidBusinessRecord, kind: RfidBusinessKind): BusinessOption | null {
  if (kind === 'INVENTORY') {
    const item = row as InventoryBatchVo;
    const value = item.batchName?.trim();
    if (!value) return null;
    const scope = item.scopeDesc || (item.warehouseId ? '限定库位' : '全馆在库藏品');
    return { label: `${value} · ${scope}`, value, searchText: `${value} ${scope}`.toLocaleLowerCase(), record: row };
  }
  const item = row as LoanVo | RequisitionVo | CollectionReturnVo;
  const value = item.id?.trim();
  if (!value) return null;
  const collectionText = item.collectionSummaries?.map((collection) => [collection.totalRegNo, collection.name].filter(Boolean).join(' ')).join('、') || '';
  let primary = '';
  let secondary = '';
  if (kind === 'LOAN_OUT' || kind === 'COLLECTION_LOAN') {
    const loan = item as LoanVo;
    primary = loan.title || loan.protocolNo || '借展单';
    secondary = [loan.borrower, loan.protocolNo].filter(Boolean).join(' · ');
  } else if (kind === 'REQUISITION_OUT' || kind === 'COLLECTION_REQUISITION') {
    const requisition = item as RequisitionVo;
    primary = requisition.documentNo || '提用单';
    secondary = requisition.department || '';
  } else {
    const returnItem = item as CollectionReturnVo;
    primary = returnItem.sourceDocumentNo || '归还单';
    secondary = returnItem.department || '';
  }
  const label = [primary, secondary, collectionText].filter(Boolean).join(' · ');
  return { label, value, searchText: `${label} ${value}`.toLocaleLowerCase(), record: row };
}

function filterOption(pattern: string, option: SelectOption) {
  const keyword = pattern.trim().toLocaleLowerCase();
  return !keyword || String((option as BusinessOption).searchText || '').includes(keyword);
}

function updateValue(value: string | null) {
  emit('update:value', value);
  emitSelected(value);
}

function emitSelected(value?: string | null) {
  const selected = value ? options.value.find((option) => option.value === value)?.record || null : null;
  emit('selected', selected);
}
</script>

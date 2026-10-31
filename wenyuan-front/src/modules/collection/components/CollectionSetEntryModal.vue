<template>
  <NModal
    v-model:show="visible"
    preset="card"
    title="成套藏品分件录入"
    style="width: 1060px; max-width: calc(100vw - 32px)"
  >
    <NAlert type="info" :bordered="false" style="margin-bottom: 14px">
      主件和每个分件会分别生成藏品台账记录；分件默认继承主件的分类、级别、来源和入藏信息。
    </NAlert>
    <NForm ref="formRef" :model="entry.main" :rules="rules" label-placement="left" label-width="90">
      <NGrid :cols="3" :x-gap="14">
        <NFormItemGi label="主件登记号" path="totalRegNo">
          <NInput v-model:value="entry.main!.totalRegNo" placeholder="留空自动编号" />
        </NFormItemGi>
        <NFormItemGi label="主件名称" path="name">
          <NInput v-model:value="entry.main!.name" placeholder="整套藏品名称" />
        </NFormItemGi>
        <NFormItemGi label="文物分类" path="categoryId">
          <NSelect v-model:value="entry.main!.categoryId" :options="categoryOptions" filterable clearable />
        </NFormItemGi>
        <NFormItemGi label="藏品级别" path="level">
          <HDictSelect v-model:value="entry.main!.level" type="col_relic_level" clearable />
        </NFormItemGi>
        <NFormItemGi label="来源方式" path="source">
          <HDictSelect v-model:value="entry.main!.source" type="col_source" clearable />
        </NFormItemGi>
        <NFormItemGi label="整套数量" path="quantity">
          <NInputNumber v-model:value="entry.main!.quantity" :min="1" style="width: 100%" />
        </NFormItemGi>
        <NFormItemGi label="整套单位" path="unit">
          <NInput v-model:value="entry.main!.unit" placeholder="套" />
        </NFormItemGi>
        <NFormItemGi label="入藏时间" path="acquireDateRange">
          <NInput v-model:value="entry.main!.acquireDateRange" placeholder="如 2026 或 1949.10.1-1965" />
        </NFormItemGi>
        <NFormItemGi label="精确日期" path="acquireDate">
          <NDatePicker v-model:value="entry.main!.acquireDate" type="date" clearable style="width: 100%" />
        </NFormItemGi>
        <NFormItemGi label="存放库位" path="warehouseId" :span="2">
          <WarehouseTreeSelect v-model:value="entry.main!.warehouseId" placeholder="选择主件及分件默认库位" />
        </NFormItemGi>
        <NFormItemGi label="备注" path="remark">
          <NInput v-model:value="entry.main!.remark" />
        </NFormItemGi>
      </NGrid>
    </NForm>

    <div class="part-header">
      <div>
        <strong>分件清单</strong>
        <NText depth="3">（至少一件，分件号在本套内唯一）</NText>
      </div>
      <NButton type="primary" secondary size="small" @click="addPart">添加分件</NButton>
    </div>
    <NDataTable :columns="partColumns" :data="entry.parts" :bordered="false" size="small" :max-height="360" :scroll-x="870" />

    <template #footer>
      <NSpace justify="end">
        <NButton @click="visible = false">取消</NButton>
        <NButton type="primary" :loading="saving" @click="submit">一次完成主件及分件录入</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<script setup lang="ts">
import { h, reactive, ref } from 'vue';
import { NButton, NInput, NInputNumber, NSelect, NSpace, useMessage, type FormInst, type FormRules } from 'naive-ui';
import { categoryApi } from '../api/category';
import { REGISTRATION_RECEIPT_HINT } from '../utils/accessionReceiptHints';
import { collectionApi } from '../api/collection';
import WarehouseTreeSelect from './WarehouseTreeSelect.vue';
import type { CollectionSetEntryPartVo, CollectionSetEntryVo, CollectionVo } from '../typings/collection';
import { hasLocalDateValue } from '@/utils';

const emit = defineEmits<{ (event: 'saved'): void }>();
const message = useMessage();
const visible = ref(false);
const saving = ref(false);
const formRef = ref<FormInst | null>(null);
const categoryOptions = ref<{ label: string; value: string }[]>([]);

function defaultMain(): CollectionVo {
  return { quantity: 1, unit: '套' };
}

function defaultPart(index: number): CollectionSetEntryPartVo {
  return {
    partNo: String(index + 1).padStart(2, '0'),
    partName: undefined,
    relation: 'PART',
    quantity: 1,
    unit: '件',
    sort: index,
    collection: {},
  };
}

const entry = reactive<CollectionSetEntryVo>({ main: defaultMain(), parts: [defaultPart(0)] });
const rules: FormRules = {
  name: { required: true, message: '请输入主件名称', trigger: ['blur', 'input'] },
  categoryId: { required: true, message: '请选择文物分类', trigger: ['change', 'blur'] },
  level: { required: true, message: '请选择藏品级别', trigger: ['change', 'blur'] },
  source: { required: true, message: '请选择来源方式', trigger: ['change', 'blur'] },
  quantity: { required: true, type: 'number', message: '请输入整套数量', trigger: ['change', 'blur'] },
  unit: { required: true, message: '请输入整套单位', trigger: ['input', 'blur'] },
  acquireDateRange: {
    validator: () => Boolean(entry.main?.acquireDateRange?.trim() || hasLocalDateValue(entry.main?.acquireDate)),
    message: '请输入入藏时间或选择精确日期',
    trigger: ['input', 'change', 'blur'],
  },
};

const partColumns = [
  {
    title: '分件号',
    key: 'partNo',
    width: 120,
    render: (row: CollectionSetEntryPartVo) =>
      h(NInput, { value: row.partNo, placeholder: '必填', onUpdateValue: (value) => (row.partNo = value) }),
  },
  {
    title: '分件名称',
    key: 'partName',
    minWidth: 190,
    render: (row: CollectionSetEntryPartVo) =>
      h(NInput, { value: row.partName, placeholder: '必填', onUpdateValue: (value) => (row.partName = value) }),
  },
  {
    title: '独立登记号',
    key: 'totalRegNo',
    minWidth: 150,
    render: (row: CollectionSetEntryPartVo) =>
      h(NInput, {
        value: row.collection?.totalRegNo,
        placeholder: '留空自动编号',
        onUpdateValue: (value) => {
          row.collection = { ...(row.collection || {}), totalRegNo: value };
        },
      }),
  },
  {
    title: '关系',
    key: 'relation',
    width: 120,
    render: (row: CollectionSetEntryPartVo) =>
      h(NSelect, {
        value: row.relation,
        options: [
          { label: '分件', value: 'PART' },
          { label: '附件', value: 'ATTACH' },
        ],
        onUpdateValue: (value) => (row.relation = value),
      }),
  },
  {
    title: '数量',
    key: 'quantity',
    width: 110,
    render: (row: CollectionSetEntryPartVo) =>
      h(NInputNumber, { value: row.quantity, min: 1, onUpdateValue: (value) => (row.quantity = value || 1) }),
  },
  {
    title: '单位',
    key: 'unit',
    width: 100,
    render: (row: CollectionSetEntryPartVo) =>
      h(NInput, { value: row.unit, onUpdateValue: (value) => (row.unit = value) }),
  },
  {
    title: '操作',
    key: 'operate',
    width: 80,
    render: (row: CollectionSetEntryPartVo) =>
      h(NButton, { text: true, type: 'error', onClick: () => removePart(row) }, () => '删除'),
  },
];

async function loadCategories() {
  if (categoryOptions.value.length) return;
  const response: any = await categoryApi.list();
  const rows = response?.data?.list ?? response?.data ?? [];
  categoryOptions.value = rows.map((item: any) => ({ label: item.name, value: item.id }));
}

function open() {
  entry.main = defaultMain();
  entry.parts = [defaultPart(0)];
  formRef.value?.restoreValidation?.();
  loadCategories();
  visible.value = true;
}

function addPart() {
  entry.parts.push(defaultPart(entry.parts.length));
}

function removePart(row: CollectionSetEntryPartVo) {
  entry.parts = entry.parts.filter((item) => item !== row).map((item, index) => ({ ...item, sort: index }));
}

function validateParts() {
  if (!entry.parts.length) throw new Error('请至少添加一个分件');
  const partNos = new Set<string>();
  entry.parts.forEach((part, index) => {
    const no = part.partNo?.trim();
    if (!no) throw new Error(`第 ${index + 1} 个分件缺少分件号`);
    if (partNos.has(no)) throw new Error(`分件号重复：${no}`);
    partNos.add(no);
    if (!part.partName?.trim()) throw new Error(`第 ${index + 1} 个分件缺少名称`);
    if (!part.quantity || part.quantity <= 0) throw new Error(`第 ${index + 1} 个分件数量必须大于 0`);
    if (!part.unit?.trim()) throw new Error(`第 ${index + 1} 个分件缺少单位`);
  });
}

async function submit() {
  try {
    await formRef.value?.validate();
    validateParts();
  } catch (error: any) {
    if (error instanceof Error) message.warning(error.message);
    return;
  }
  saving.value = true;
  try {
    await collectionApi.createSetEntry({
      main: { ...entry.main },
      parts: entry.parts.map((part, index) => ({
        ...part,
        partNo: part.partNo?.trim(),
        partName: part.partName?.trim(),
        unit: part.unit?.trim(),
        sort: index,
        collection: { ...(part.collection || {}), name: part.partName?.trim(), warehouseId: entry.main?.warehouseId },
      })),
    });
    message.success(`已完成 1 套主件及 ${entry.parts.length} 个分件录入`);
    message.info(REGISTRATION_RECEIPT_HINT, { duration: 6000 });
    visible.value = false;
    emit('saved');
  } finally {
    saving.value = false;
  }
}

defineExpose({ open });
</script>

<style scoped>
.part-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 18px 0 10px;
}
</style>

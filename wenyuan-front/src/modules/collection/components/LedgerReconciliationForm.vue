<template>
  <NModal v-model:show="show" preset="card" title="登记纸账与财务账实际核对原件" style="width: 1160px; max-width: 96vw">
    <NSpace vertical :size="14">
      <NAlert type="info"
        >按双方已签署的实际核对明细填写。纸账和财务账分别录入，记录缺项时明确选择缺失。系统不会据此修改藏品主档；复核或订正另留版本及原件。</NAlert
      >
      <NText v-if="review"
        >复核编号 {{ model.referenceNo }}；原期间及纸账范围固定。请核实原明细，填写本次实际时间、人员和原件。</NText
      >
      <NForm label-placement="top"
        ><WarehouseRecordFields v-model="model" :fields="fields" />
        <NFormItem label="本次实际核对纸账册" required
          ><NSelect v-model:value="model.bookIds" :options="bookOptions" multiple :disabled="review"
        /></NFormItem>
      </NForm>
      <NCard v-for="(row, index) in rows" :key="row.lineNo" :title="`原明细行 ${row.lineNo}`">
        <NForm label-placement="top">
          <NGrid :cols="2" :x-gap="12"
            ><NGi
              ><NFormItem label="本行纸质账册" required
                ><NSelect v-model:value="row.bookId" :options="selectedBookOptions" /></NFormItem></NGi
            ><NGi
              ><NFormItem label="纸账实际页码或查找范围" required
                ><NInput v-model:value="row.paperPageRef" /></NFormItem></NGi
          ></NGrid>
          <NFormItem label="关联本馆系统记录（历史未建系统记录可留空）"
            ><CollectionLedgerReferenceSelect v-model:value="row.collectionId" :org-id="editingOrg" purpose="finance"
          /></NFormItem>
          <NFormItem label="财务账实际记录位置"><NInput v-model:value="row.financialRecordRef" /></NFormItem>
          <section v-for="side in sides" :key="side.key" style="margin-bottom: 12px">
            <NSpace align="center"
              ><NText>{{ side.label }}</NText
              ><NButton size="small" @click="row[side.key] = {}">录入原账记录</NButton
              ><NButton size="small" @click="row[side.key] = null">实际缺对应记录</NButton
              ><NText>{{
                row[side.key] === undefined ? '尚未核实' : row[side.key] === null ? '原件记载缺项' : '录入原件数据'
              }}</NText></NSpace
            >
            <NGrid v-if="row[side.key]" :cols="4" :x-gap="12"
              ><NGi v-for="f in accountFields" :key="f.key"
                ><NFormItem :label="f.label" required
                  ><NInputNumber
                    v-if="f.key === 'quantity'"
                    v-model:value="row[side.key][f.key]"
                    :precision="0"
                    :max="Number.MAX_SAFE_INTEGER"
                    :min="-Number.MAX_SAFE_INTEGER" /><NInput
                    v-else
                    v-model:value="row[side.key][f.key]" /></NFormItem></NGi
            ></NGrid>
          </section>
          <NFormItem label="有差异时的原因核查说明"
            ><NInput v-model:value="row.issueExplanation" type="textarea" /></NFormItem
          ><NFormItem label="有差异时的后续处理安排"
            ><NInput v-model:value="row.plannedAction" type="textarea"
          /></NFormItem>
          <NButton @click="removeLine(Number(index))">{{
            previousLines.has(row.lineNo) ? '排除原误录行（须另留原因）' : '移除尚未保存行'
          }}</NButton>
        </NForm>
      </NCard>
      <NButton @click="addLine">添加逐项明细</NButton>
      <NFormItem v-for="(_, lineNo) in omissions" :key="lineNo" :label="`排除原行 ${lineNo} 的实际订正依据`" required
        ><NInput v-model:value="omissions[lineNo]" type="textarea"
      /></NFormItem>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
    </NSpace>
    <template #footer
      ><NSpace justify="end"
        ><NButton @click="show = false">取消</NButton
        ><NButton type="primary" :loading="saving" @click="submit">保留实际核对及签署原件</NButton></NSpace
      ></template
    >
  </NModal>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import CollectionLedgerReferenceSelect from './CollectionLedgerReferenceSelect.vue';
import { accountFields, reconciliationFields } from '../utils/paperLedgerFields';
const props = defineProps<{ save: (orgId: string, value: any) => Promise<any> }>();
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  saving = ref(false),
  review = ref(false),
  model = ref<any>({}),
  rows = ref<any[]>([]),
  omissions = ref<Record<number, string>>({}),
  error = ref(''),
  books = ref<any[]>([]);
const editingOrg = ref('');
let nextLine = 1;
const previousLines = ref(new Set<number>());
const fields = computed(() => reconciliationFields(review.value));
const sides = [
  { key: 'paper', label: '纸质总登记账' },
  { key: 'financial', label: '文物资源资产财务账' },
];
const bookOptions = computed(() => books.value.map((b) => ({ label: b.bookNo, value: b.id })));
const selectedBookOptions = computed(() => bookOptions.value.filter((b) => model.value.bookIds?.includes(b.value)));
function open(ctx: { orgId: string; books: any[]; previous?: any }) {
  editingOrg.value = ctx.orgId;
  books.value = cloneDeep(ctx.books);
  review.value = !!ctx.previous;
  const p = ctx.previous?.content;
  model.value = p
    ? { referenceNo: p.referenceNo, periodFrom: p.periodFrom, periodTo: p.periodTo, bookIds: cloneDeep(p.bookIds) }
    : {};
  rows.value = p ? cloneDeep(p.rows || []) : [];
  previousLines.value = new Set(rows.value.map((r) => r.lineNo));
  nextLine = Math.max(0, ...rows.value.map((r) => r.lineNo)) + 1;
  omissions.value = {};
  error.value = '';
  show.value = true;
}
function addLine() {
  rows.value.push({ lineNo: nextLine++ });
}
function removeLine(index: number) {
  const [line] = rows.value.splice(index, 1);
  if (previousLines.value.has(line.lineNo)) omissions.value[line.lineNo] = '';
}
async function submit() {
  const missing = fields.value.find(
    (f) => f.required && (model.value[f.key] === undefined || model.value[f.key] === null || model.value[f.key] === ''),
  );
  if (missing || !model.value.bookIds?.length) {
    error.value = `请填写${missing?.label || '本次实际核对纸账册'}`;
    return;
  }
  if (!rows.value.length && !model.value.emptyScopeReason?.trim()) {
    error.value = '空范围核对须填写实际依据并保留双方空表原件';
    return;
  }
  for (const row of rows.value) {
    if (
      !row.bookId ||
      !row.paperPageRef ||
      row.paper === undefined ||
      row.financial === undefined ||
      (!row.paper && !row.financial)
    ) {
      error.value = `请核实第 ${row.lineNo} 行的纸账册、页码及两边实际记录或缺项`;
      return;
    }
    for (const side of sides) {
      const value = row[side.key];
      if (
        value &&
        accountFields.some((f) => (f.key === 'quantity' ? !Number.isSafeInteger(value[f.key]) : !value[f.key]?.trim()))
      ) {
        error.value = `请完整填写第 ${row.lineNo} 行${side.label}的原件数据`;
        return;
      }
    }
  }
  if (Object.values(omissions.value).some((v) => !v.trim())) {
    error.value = '请逐行说明排除原误录行的真实依据';
    return;
  }
  error.value = '';
  saving.value = true;
  try {
    const payload = cloneDeep({
      ...model.value,
      rows: rows.value.map((r) => ({ ...r, collectionId: r.collectionId || null })),
      omittedLineReasons: omissions.value,
    });
    await props.save(editingOrg.value, payload);
    show.value = false;
    emit('saved');
  } finally {
    saving.value = false;
  }
}
defineExpose({ open });
</script>

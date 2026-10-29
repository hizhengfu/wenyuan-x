<template>
  <NModal v-model:show="show" preset="card" title="登记已经实际签署的年度报表" style="width: 1050px; max-width: 96vw">
    <NSpace vertical :size="16">
      <NAlert type="info"
        >{{
          year
        }}
        年度。请按已签署原报表填写实际数值，条目数与件、套等数量分开登记；系统实时预览不会自动填为签署事实。</NAlert
      >
      <NAlert v-if="hasIssues" type="warning">系统历史仍有缺口，必须保留纸质总账、逐件明细及核对说明原件。</NAlert>
      <NForm label-placement="top">
        <WarehouseRecordFields v-model="model" :fields="annualSignedFields" />
        <NGrid :cols="3" :x-gap="12"
          ><NGi v-for="f in annualCountFields" :key="f.key"
            ><NFormItem :label="f.label" required
              ><NInputNumber v-model:value="figures[f.key]" :min="0" :precision="0" /></NFormItem></NGi
        ></NGrid>
        <NCard
          v-for="(q, index) in figures.quantities"
          :key="index"
          title="按计量单位填写数量"
          style="margin-bottom: 12px"
        >
          <NFormItem label="计量单位" required
            ><NInput v-model:value="q.unit" placeholder="例如件或套，每种单位一行"
          /></NFormItem>
          <NGrid :cols="3" :x-gap="12"
            ><NGi v-for="f in annualQuantityFields" :key="f.key"
              ><NFormItem :label="f.label" required
                ><NInputNumber
                  v-model:value="q[f.key]"
                  :min="f.key === 'adjustment' ? undefined : 0"
                  :precision="0" /></NFormItem></NGi
          ></NGrid>
          <NButton @click="figures.quantities.splice(Number(index), 1)">移除该单位</NButton>
        </NCard>
        <NButton @click="figures.quantities.push({})">添加一种计量单位</NButton>
        <NFormItem label="原报表所载的本年增减及信息变更情况" required
          ><NInput v-model:value="figures.changesDescription" type="textarea"
        /></NFormItem>
      </NForm>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
    </NSpace>
    <template #footer
      ><NSpace justify="end"
        ><NButton @click="show = false">取消</NButton
        ><NButton type="primary" :loading="saving" @click="submit">保存实际签署原件</NButton></NSpace
      ></template
    >
  </NModal>
</template>
<script setup lang="ts">
import { ref, watch, onActivated, onDeactivated, onBeforeUnmount } from 'vue';
import { cloneDeep } from 'lodash-es';
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import { annualSignedFields, annualCountFields, annualQuantityFields } from '../utils/annualReportFields';
const props = defineProps<{ save: (value: any) => Promise<any> }>();
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  year = ref(0),
  model = ref<any>({}),
  figures = ref<any>({ quantities: [] }),
  error = ref(''),
  saving = ref(false),
  hasIssues = ref(false),
  replacement = ref(false);
let generation = 0,
  active = true;
function invalidate() {
  ++generation;
  saving.value = false;
  error.value = '';
  model.value = {};
  figures.value = { quantities: [] };
  year.value = 0;
  hasIssues.value = false;
  replacement.value = false;
}
function close() {
  show.value = false;
  invalidate();
}
watch(
  show,
  (value) => {
    if (!value) invalidate();
  },
  { flush: 'sync' },
);
onActivated(() => {
  active = true;
});
onDeactivated(() => {
  active = false;
  close();
});
onBeforeUnmount(() => {
  active = false;
  close();
});
function open(value: { year: number; hasIssues?: boolean; replacement?: boolean }) {
  if (!active) return;
  ++generation;
  saving.value = false;
  year.value = value.year;
  hasIssues.value = !!value.hasIssues;
  replacement.value = !!value.replacement;
  model.value = {};
  figures.value = { quantities: [] };
  error.value = '';
  show.value = true;
}
async function submit() {
  if (!active || !show.value || saving.value) return;
  const missing = annualSignedFields.find((f) => f.required && !model.value[f.key]);
  const invalidCount = annualCountFields.find(
    (f) => !Number.isSafeInteger(figures.value[f.key]) || figures.value[f.key] < 0,
  );
  if (missing || invalidCount || !figures.value.changesDescription?.trim()) {
    error.value = `请填写${missing?.label || invalidCount?.label || '本年增减及变更情况'}`;
    return;
  }
  for (const q of figures.value.quantities) {
    if (
      !q.unit?.trim() ||
      annualQuantityFields.some((f) => !Number.isSafeInteger(q[f.key]) || (f.key !== 'adjustment' && q[f.key] < 0))
    ) {
      error.value = '请完整填写各计量单位的实际数量（整数）';
      return;
    }
  }
  if (hasIssues.value && (!model.value.reconciliationNote || !model.value.reconciliationFileIds)) {
    error.value = '请保留历史缺口的核对说明及原件';
    return;
  }
  if (replacement.value && (!model.value.replacementReason || !model.value.replacementFileIds)) {
    error.value = '补报或订正须另留原因和本次签署原件';
    return;
  }
  const token = generation,
    save = props.save;
  error.value = '';
  saving.value = true;
  try {
    const result: any = await save(cloneDeep({ ...model.value, year: year.value, figures: figures.value }));
    if (!active || token !== generation || !show.value || result === false) return;
    if (
      (result?.status !== undefined && result.status !== 200) ||
      typeof result?.data !== 'string' ||
      !result.data.trim()
    )
      throw Error('登记回执无法确认，请回原记录核对实际结果');
    show.value = false;
    emit('saved');
  } catch (e) {
    if (active && token === generation && show.value)
      error.value = e instanceof Error ? e.message : '登记失败，请核对原记录';
  } finally {
    if (token === generation) saving.value = false;
  }
}
defineExpose({ open, close, isOpen: () => show.value });
</script>

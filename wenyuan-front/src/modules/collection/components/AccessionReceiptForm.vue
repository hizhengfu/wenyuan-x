<template>
  <NModal
    v-model:show="show"
    preset="card"
    :title="signatureOnly ? '追加核收照片及签署原件订正' : '登记实际逐件核收清册'"
    style="width: 960px; max-width: 96vw"
  >
    <NSpace vertical :size="18">
      <NAlert v-if="signatureOnly" type="info"
        >{{ subject }}。可为每件补充独立照片，并上传新的双方签署原件、说明订正原因；原缺失的实际数量可凭本次签署原件补录；已有数量、原核收时间、实物状况、人员、地点和旧版本保持不变。</NAlert
      >
      <NAlert v-else type="info"
        >{{
          subject
        }}。每次保存累计清册并保留全部历史版本；只填实际收到的物品。完成情况须依据本次签署原件重新确认。</NAlert
      >
      <NForm label-placement="top">
        <template v-if="signatureOnly">
          <NCard v-for="(piece, index) in model.pieces" :key="index" :title="`清册第 ${Number(index) + 1} 件（套）· ${piece.pieceNo}`" style="margin-bottom: 12px">
            <WarehouseRecordFields v-model="model.pieces[index]" :fields="receiptPhotoCorrectionFields" />
            <NFormItem label="实际数量"><NInputNumber v-model:value="piece.actualQuantity" :min="1" :precision="0" :disabled="originalCounts[Number(index)]?.actualQuantity != null" /></NFormItem>
            <NFormItem label="实际数量单位"><NInput v-model:value="piece.actualQuantityUnit" :disabled="originalCounts[Number(index)]?.actualQuantityUnit != null" placeholder="如件、册；不能填写套" /></NFormItem>
          </NCard>
          <WarehouseRecordFields v-model="model" :fields="[receiptFields[3]]" />
          <WarehouseRecordFields v-model="model" :fields="correctionReceiptFields" />
        </template>
        <template v-else>
          <WarehouseRecordFields v-model="model" :fields="receiptFields" />
          <NFormItem label="实际接收保管库位" required
            ><WarehouseTreeSelect v-model:value="model.warehouseId"
          /></NFormItem>
          <NFormItem label="实际移交人员姓名、单位（每行一人）" required
            ><NInput v-model:value="handoverText" type="textarea"
          /></NFormItem>
          <NCard
            v-for="(piece, index) in model.pieces"
            :key="index"
            :title="`清册第 ${Number(index) + 1} 件（套）`"
            style="margin-bottom: 12px"
          >
            <WarehouseRecordFields v-model="model.pieces[index]" :fields="pieceFields" />
            <NButton type="error" @click="model.pieces.splice(index, 1)">从本次清册移除</NButton>
          </NCard>
          <NButton @click="addPiece">添加实际收到的一件（套）</NButton>
          <WarehouseRecordFields v-if="model.accepted === 'NO'" v-model="model" :fields="pendingReceiptFields" />
          <WarehouseRecordFields
            v-if="model.accepted === 'YES' && isLate"
            v-model="model"
            :fields="lateReceiptFields"
          />
          <WarehouseRecordFields v-if="isCorrection" v-model="model" :fields="correctionReceiptFields" />
        </template>
      </NForm>
      <NAlert v-if="isLate && !signatureOnly" type="warning"
        >已超过实际取得后三个月。请按真实时间登记并保留迟办处理原件。</NAlert
      >
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
    </NSpace>
    <template #footer
      ><NSpace justify="end"
        ><NButton :disabled="saving" @click="show = false">取消</NButton
        ><NButton type="primary" :loading="saving" @click="submit">{{
          signatureOnly ? '追加订正原件' : '保存实际核收原件'
        }}</NButton></NSpace
      ></template
    >
  </NModal>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { formatLocalDateValue } from '@/utils';
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import WarehouseTreeSelect from './WarehouseTreeSelect.vue';
import {
  receiptFields,
  pieceFields,
  receiptPhotoCorrectionFields,
  pendingReceiptFields,
  lateReceiptFields,
  correctionReceiptFields,
  receiptSeed,
  receiptSignatureCorrectionSeed,
  receiptPayload,
  receiptError,
} from '../utils/accessionReceiptFields';
const props = defineProps<{ save: (value: any) => Promise<any> }>();
const emit = defineEmits<{ (e: 'saved'): void }>();
const originalCounts = ref<any[]>([]);
const show = ref(false),
  model = ref<any>({ pieces: [] }),
  saving = ref(false),
  error = ref(''),
  handoverText = ref(''),
  isCorrection = ref(false),
  signatureOnly = ref(false),
  subject = ref(''),
  deadline = ref<any>();
const isLate = computed(
  () =>
    !!model.value.checkedAt &&
    !!deadline.value &&
    formatLocalDateValue(model.value.checkedAt, 'YYYY-MM-DD') > formatLocalDateValue(deadline.value, 'YYYY-MM-DD'),
);
function open(item: any, correctSignature = false) {
  signatureOnly.value = correctSignature;
  originalCounts.value = cloneDeep(item.current?.content?.pieces || []);
  model.value = cloneDeep(correctSignature ? receiptSignatureCorrectionSeed(item) : receiptSeed(item));
  handoverText.value = correctSignature ? (model.value.handoverNames || []).join('\n') : '';
  error.value = '';
  isCorrection.value = !!item.current;
  deadline.value = item.deadline;
  subject.value = `${item.name}，取得清册 ${item.quantity}${item.unit || ''}`;
  show.value = true;
}
function addPiece() {
  model.value.pieces.push({});
}
async function submit() {
  const payload = receiptPayload({
    ...model.value,
    handoverNames: signatureOnly.value
      ? model.value.handoverNames
      : handoverText.value
          .split('\n')
          .map((s) => s.trim())
          .filter(Boolean),
  });
  error.value = receiptError(payload, isCorrection.value, isLate.value) || '';
  if (error.value) return;
  saving.value = true;
  try {
    await props.save(payload);
    show.value = false;
    emit('saved');
  } finally {
    saving.value = false;
  }
}
defineExpose({ open });
</script>

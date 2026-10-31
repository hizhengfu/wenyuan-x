<template>
  <HPageForm v-model:show="show" title="登记实际逐件点交原件" :model="form" label-width="190">
    <NAlert type="info"
      >依据保管负责人审查指派记录及双方签署原件如实登记。安全条件不足时不得出库。更正已登记材料时，原版本继续保存。</NAlert
    >
    <NFormItem
      v-for="field in visibleHandoverFields"
      :key="field.key"
      :label="field.label"
      :required="field.required"
    >
      <NDatePicker v-if="field.type === 'datetime'" v-model:value="form[field.key]" type="datetime" clearable />
      <UserSelect v-else-if="field.type === 'user'" v-model:value="form[field.key]" :multiple="field.multiple" />
      <HUploadInput
        v-else-if="field.type === 'file'"
        v-model:value="form[field.key]"
        @upload-finish="form[field.key] = $event.id"
        clearable
      />
      <NSelect v-else-if="field.type === 'select'" v-model:value="form[field.key]" :options="field.options" />
      <NInput v-else v-model:value="form[field.key]" type="textarea" />
    </NFormItem>
    <NCard
      v-for="item in form.items"
      :key="item.collectionId"
      size="small"
      :title="itemName(item.collectionId)"
      style="margin: 14px 0"
    >
      <NFormItem label="具体保存现状" required
        ><NInput v-model:value="item.condition" type="textarea" placeholder="请描述本件各部位的保存现状，并与档案核对"
      /></NFormItem>
      <NFormItem label="文字是否需要影像补充" required
        ><NSelect v-model:value="item.textNeedsImages" :options="requisitionYesNo"
      /></NFormItem>
      <NFormItem label="本件状况影像" :required="item.textNeedsImages === 'YES'"
        ><HUploadInput v-model:value="item.imageFileIds" @upload-finish="item.imageFileIds = $event.id" clearable
      /></NFormItem>
    </NCard>
    <template #form-action
      ><NButton type="primary" :loading="busy" @click="submit">核对原件并登记点交</NButton></template
    >
  </HPageForm>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { useMessage } from 'naive-ui';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import { requisitionApi } from '../api/requisition';
import {
  requisitionHandoverFields,
  requisitionHandoverInitial,
  requisitionHandoverPayload,
  requisitionHandoverError,
  requisitionYesNo,
} from '../utils/requisitionEvidenceFields';
import { REQUISITION_HANDOVER_TOO_EARLY, isBeforeExpectedOutAt } from '../utils/requisitionPrerequisite';
const emit = defineEmits<{ (e: 'saved'): void }>(),
  message = useMessage();
const show = ref(false),
  busy = ref(false),
  source = ref<Record<string, any>>({}),
  form = ref<Record<string, any>>({});
const visibleHandoverFields = computed(() => requisitionHandoverFields
  .filter((field) => source.value.dossier?.transportRequired === true ||
    !['appliedTransportStandardReference', 'packingStandardConfirmed', 'packingCheckFileIds'].includes(field.key))
  .map((field) => source.value.dossier?.transportRequired === true &&
    ['appliedTransportStandardReference', 'packingStandardConfirmed', 'packingCheckFileIds'].includes(field.key)
      ? { ...field, required: true } : field));
function open(data: Record<string, any>) {
  if (isBeforeExpectedOutAt(data?.dossier)) {
    message.warning(REQUISITION_HANDOVER_TOO_EARLY);
    return;
  }
  source.value = JSON.parse(JSON.stringify(data));
  form.value = requisitionHandoverInitial(data);
  show.value = true;
}
function itemName(id: string) {
  const entry = source.value.baseline?.items?.find((item: any) => item.collectionId === id);
  return `${entry?.content?.formalName || '藏品'}（${source.value.collectionSummaries?.find((item: any) => item.id === id)?.totalRegNo || id}）`;
}
async function submit() {
  if (isBeforeExpectedOutAt(source.value?.dossier)) {
    message.warning(REQUISITION_HANDOVER_TOO_EARLY);
    return;
  }
  const payload = requisitionHandoverPayload(form.value);
  const error = requisitionHandoverError(
    payload,
    (source.value.baseline?.items || []).map((item: any) => item.collectionId),
    !!source.value.handover,
    source.value.dossier?.transportRequired === true,
  );
  if (error) {
    message.warning(error);
    return;
  }
  busy.value = true;
  try {
    await requisitionApi.handover(source.value.id, payload);
    message.success('点交原件已登记，请按实际交接情况执行出库');
    show.value = false;
    emit('saved');
  } finally {
    busy.value = false;
  }
}
defineExpose({ open });
</script>

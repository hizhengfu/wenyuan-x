<template>
  <HPageForm v-model:show="show" title="逐件归还验收及双方签署" :model="form" label-width="205">
    <NAlert type="info"
      >仅登记本次实际收到的实物。逐件对照原出库状况和影像；发现差异时如实记录，接收后进入待核查并保留防护措施。</NAlert
    >
    <NAlert v-if="sourceOriginals.error.value" type="warning">{{ sourceOriginals.error.value }}</NAlert>
    <NFormItem
      v-for="field in returnHeaderFields(loan)"
      :key="field.key"
      :label="field.label"
      :required="field.required"
    >
      <NDatePicker v-if="field.type === 'datetime'" v-model:value="form[field.key]" type="datetime" /><UserSelect
        v-else-if="field.type === 'user'"
        v-model:value="form[field.key]"
        :multiple="field.multiple"
      /><HUploadInput
        v-else-if="field.type === 'file'"
        v-model:value="form[field.key]"
        @upload-finish="form[field.key] = $event.id"
      /><NSelect v-else-if="field.type === 'select'" v-model:value="form[field.key]" :options="field.options" /><NInput
        v-else
        v-model:value="form[field.key]"
        type="textarea"
      />
    </NFormItem>
    <NCard
      v-for="item in form.items"
      :key="item.collectionId"
      :title="sourceItem(item.collectionId)?.content?.formalName || item.collectionId"
      size="small"
      style="margin: 14px 0"
    >
      <NAlert type="info">出库保存现状：{{ sourceItem(item.collectionId)?.outboundCondition?.condition }}</NAlert
      ><HFileList
        v-if="sourceItem(item.collectionId)?.outboundCondition?.imageFileIds"
        :files="sourceOriginals.files(sourceItem(item.collectionId)?.outboundCondition?.imageFileIds)"
        :original-url="sourceOriginals.originalUrl"
        :revision="sourceOriginals.archive.value"
        show-download-btn
      />
      <NFormItem
        v-for="field in returnItemFields(item)"
        :key="field.key"
        :label="field.label"
        :required="field.required"
        ><NSelect
          v-if="field.type === 'select'"
          v-model:value="item[field.key]"
          :options="field.options" /><HUploadInput
          v-else-if="field.type === 'file'"
          v-model:value="item[field.key]"
          @upload-finish="item[field.key] = $event.id" /><NInput v-else v-model:value="item[field.key]" type="textarea"
      /></NFormItem> </NCard
    ><template #form-action
      ><NButton type="primary" :loading="busy" @click="submit">核对签署原件并登记</NButton></template
    ></HPageForm
  >
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { useReturnSourceOriginals } from '../utils/returnSourceOriginals';
import { useMessage } from 'naive-ui';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import { collectionReturnApi } from '../api/return';
import {
  returnHeaderFields,
  returnItemFields,
  returnInspectionInitial,
  returnEvidencePayload,
  returnInspectionError,
} from '../utils/returnEvidenceFields';
const emit = defineEmits<{ (e: 'saved'): void }>(),
  message = useMessage(),
  show = ref(false),
  busy = ref(false),
  source = ref<any>({}),
  form = ref<any>({});
const sourceOriginals = useReturnSourceOriginals(() => source.value, () => show.value);
const loan = computed(() => source.value.sourceBizType === 'COLLECTION_LOAN');
function open(data: any) {
  source.value = JSON.parse(JSON.stringify(data));
  form.value = returnInspectionInitial(data);
  show.value = true;
}
function sourceItem(id: string) {
  return source.value.sourceSnapshot?.items?.find((i: any) => i.collectionId === id);
}
async function submit() {
  const value = returnEvidencePayload(form.value),
    error = returnInspectionError(
      value,
      source.value.sourceSnapshot.items.map((i: any) => i.collectionId),
      loan.value,
      !!source.value.inspection,
    );
  if (error) {
    message.warning(error);
    return;
  }
  busy.value = true;
  try {
    await collectionReturnApi.inspection(source.value.id, value);
    show.value = false;
    emit('saved');
  } finally {
    busy.value = false;
  }
}
defineExpose({ open });
</script>

<template>
  <NModal
    v-model:show="show"
    preset="card"
    title="登记已实际编制的藏品档案目录和原件"
    style="width: 1050px; max-width: 96vw"
  >
    <NSpace vertical :size="14">
      <NAlert type="info"
        >选择原件对应的正式编目版本，按实际目录登记原件位置。尚未发生的事项须说明截至本版编制日的情况；有原件缺项可以如实留档，但不能据此认定建档完成。</NAlert
      >
      <NForm label-placement="top">
        <NFormItem label="原件对应的已审核编目版本" required
          ><NSelect
            v-model:value="model.catalogVersionId"
            :options="
              versions.map((v) => ({
                value: v.id,
                label: `第 ${v.version} 版 · ${v.content?.formalName || v.content?.totalName || '历史名称待核实'}`,
              }))
            "
        /></NFormItem>
        <WarehouseRecordFields v-model="model" :fields="fields" />
        <NCard v-for="[key, label] in archiveSections" :key="key" :title="label" style="margin-bottom: 12px">
          <NFormItem label="本版编制时的实际目录状态"
            ><NSelect v-model:value="sections[key].status" :options="statusOptions(key)" clearable
          /></NFormItem>
          <NFormItem label="实际原件位置、未发生依据或缺项说明"
            ><NInput v-model:value="sections[key].reference" type="textarea"
          /></NFormItem>
          <NFormItem label="本项原件"
            ><HUploadInput v-model:value="sections[key].fileIds" @upload-finish="sections[key].fileIds = $event.id"
          /></NFormItem>
        </NCard>
      </NForm>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
    </NSpace>
    <template #footer
      ><NSpace justify="end"
        ><NButton @click="show = false">取消</NButton
        ><NButton type="primary" :loading="saving" @click="submit">保存实际档案目录及核验原件</NButton></NSpace
      ></template
    >
  </NModal>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import { archivePacketFields, archiveSections, archiveSectionStatuses } from '../utils/archivePacketFields';
const props = defineProps<{ save: (orgId: string, collectionId: string, value: any) => Promise<any> }>();
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  saving = ref(false),
  model = ref<any>({}),
  sections = ref<Record<string, any>>({}),
  versions = ref<any[]>([]),
  revision = ref(false),
  error = ref('');
let editingOrg = '',
  editingCollection = '';
const fields = computed(() => archivePacketFields(revision.value));
function open(value: {
  orgId: string;
  collectionId: string;
  versions: any[];
  currentVersionId?: string;
  revision?: boolean;
}) {
  editingOrg = value.orgId;
  editingCollection = value.collectionId;
  versions.value = cloneDeep(value.versions);
  revision.value = !!value.revision;
  model.value = value.currentVersionId ? { catalogVersionId: value.currentVersionId } : {};
  sections.value = Object.fromEntries(archiveSections.map(([key]) => [key, {}]));
  error.value = '';
  show.value = true;
}
function statusOptions(key: string) {
  const nature = versions.value.find((v) => v.id === model.value.catalogVersionId)?.content?.nature;
  return [
    ...archiveSectionStatuses,
    ...(key === 'GRADING' && nature === 'OTHER' ? [{ value: 'NOT_APPLICABLE', label: '其他藏品不适用文物定级' }] : []),
  ];
}
async function submit() {
  const missing = fields.value.find(
    (f) => f.required && (model.value[f.key] === undefined || model.value[f.key] === null || model.value[f.key] === ''),
  );
  if (missing || !model.value.catalogVersionId) {
    error.value = `请填写${missing?.label || '原件对应的已审核编目版本'}`;
    return;
  }
  if (['PAPER', 'BOTH'].includes(model.value.format) && !model.value.physicalLocation?.trim()) {
    error.value = '请填写纸质档案实际保存位置';
    return;
  }
  if (['ELECTRONIC', 'BOTH'].includes(model.value.format) && !model.value.electronicLocation?.trim()) {
    error.value = '请填写电子档案实际卷宗或存储位置';
    return;
  }
  const entries = Object.entries(sections.value).filter(([, v]) => v.status);
  for (const [key, entry] of entries) {
    if (!entry.reference?.trim() || (entry.status === 'INCLUDED' && !entry.fileIds)) {
      error.value = `请填写${archiveSections.find(([k]) => k === key)?.[1]}的实际目录说明及已归入原件`;
      return;
    }
  }
  error.value = '';
  saving.value = true;
  try {
    await props.save(
      editingOrg,
      editingCollection,
      cloneDeep({ ...model.value, sections: Object.fromEntries(entries) }),
    );
    show.value = false;
    emit('saved');
  } finally {
    saving.value = false;
  }
}
defineExpose({ open });
</script>

<template>
  <div
    ref="rootRef"
    class="warehouse-entry-evidence"
    :class="{ 'warehouse-entry-evidence--wide': wide, 'warehouse-entry-evidence--paired': paired }"
  >
    <template v-if="section === 'all' || section === 'application'">
      <NAlert v-if="!data.dossier || !data.baseline" type="warning">
        旧申请尚未核实实际库管和完整陪同方案，不能用旧审批结果代替新核验。
      </NAlert>
      <NCard title="入库安排" size="small">
        <div class="warehouse-entry-evidence__warehouse">
          <NText depth="3">拟进入库房</NText>
          <NText>{{ warehouseNames }}</NText>
        </div>
        <CollectionEvidenceReadView
          :fields="applicationFields"
          :data="applicationData"
          :archive-files="archiveFiles" :original-url="originalUrl"
          label-placement="left"
          size="small"
        />
        <div class="warehouse-entry-evidence__planned">
          <CollectionEvidenceReadView
            v-for="field in plannedFields"
            :key="field.key"
            :fields="[field]"
            :data="data"
            label-placement="left"
            size="small"
          />
        </div>
      </NCard>
      <NCard v-if="data.dossier" title="人员与陪同方案" size="small">
        <CollectionEvidenceReadView
          :fields="warehouseEntryFields(data.dossier)"
          :data="readable(data.dossier)"
          :archive-files="archiveFiles" :original-url="originalUrl"
          label-placement="left"
          size="small"
        />
      </NCard>
    </template>
    <NCollapse
      v-if="(section === 'all' || section === 'application' || section === 'staffing') && data.baseline?.items?.length"
    >
      <NCollapseItem name="staffing" :title="`库管任职依据（${data.baseline.items.length}个库房）`">
        <div class="warehouse-entry-evidence__staffing">
          <NCard v-for="item in data.baseline.items" :key="item.warehouseId" :title="item.warehouseName" size="small">
            <CollectionEvidenceReadView
              :fields="warehouseStaffingFields"
              :data="item.staffing"
              label-placement="left"
              size="small"
            />
            <NButton
              v-if="openStaffing && item.staffing?.signedAssignmentFileIds"
              v-permission="
                'ADMIN,COLLECTION_ADMIN,COLLECTION_WAREHOUSE_MANAGE,COLLECTION_SELECT,COLLECTION_WAREHOUSE_ENTRY_APPLY'
              "
              class="warehouse-entry-evidence__staffing-action"
              size="small"
              secondary
              @click="openStaffing(item.warehouseId)"
            >
              查看该库房的任职原件
            </NButton>
          </NCard>
        </div>
      </NCollapseItem>
    </NCollapse>
    <template v-if="section === 'all' || section === 'attendance'">
      <div v-if="data.entryRecord || data.exitRecord" class="warehouse-entry-evidence__attendance">
        <NCard v-if="data.entryRecord" title="实际入库登记" size="small">
          <CollectionEvidenceReadView
            :fields="warehouseAttendanceFields(data.entryRecord)"
            :data="readable(data.entryRecord)"
            :archive-files="archiveFiles" :original-url="originalUrl"
            label-placement="left"
            size="small"
          />
        </NCard>
        <NCard v-if="data.exitRecord" title="实际离库登记" size="small">
          <CollectionEvidenceReadView
            :fields="warehouseAttendanceFields(data.exitRecord, true)"
            :data="readable(data.exitRecord)"
            :archive-files="archiveFiles" :original-url="originalUrl"
            label-placement="left"
            size="small"
          />
        </NCard>
      </div>
      <NEmpty v-else description="尚未登记实际出入库记录" />
    </template>
  </div>
</template>
<script setup lang="ts">
import { type CollectionReadonlyFile } from '../utils/readonlyFiles';
import { computed, ref } from 'vue';
import { useElementSize } from '@vueuse/core';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import {
  warehouseEntryBaseFields,
  warehouseEntryFields,
  warehouseStaffingFields,
  warehouseAttendanceFields,
} from '../utils/warehouseEvidenceFields';
const props = withDefaults(
  defineProps<{
    data: Record<string, any>;
    originalUrl?: (fileId: string) => string;
    archiveFiles?: CollectionReadonlyFile[];
    openStaffing?: (id: string) => void;
    section?: 'all' | 'application' | 'attendance' | 'staffing';
  }>(),
  { section: 'all' },
);
const rootRef = ref<HTMLElement>();
const { width } = useElementSize(rootRef);
const wide = computed(() => width.value >= 900);
const paired = computed(() => width.value >= 760);
const warehouseNames = computed(
  () =>
    props.data.baseline?.items?.map((item: any) => item.warehouseName).join('、') ||
    props.data.warehouseNames?.join('、') ||
    '待核实',
);
const applicationFields = computed(() =>
  warehouseEntryBaseFields.filter(
    (field) =>
      !['plannedStart', 'plannedEnd'].includes(field.key) &&
      (field.key !== 'externalEntrants' || props.data.externalEntrants),
  ),
);
const plannedFields = warehouseEntryBaseFields.filter((field) => ['plannedStart', 'plannedEnd'].includes(field.key));
const applicationData = computed(() => ({
  ...props.data,
  entrantIds: Array.isArray(props.data.entrantIds)
    ? props.data.entrantIds
    : String(props.data.entrantIds || '')
        .split(',')
        .filter(Boolean),
}));
function readable(value: Record<string, any>) {
  return {
    ...value,
    externalNames: Array.isArray(value.externalNames) ? value.externalNames.join('\n') || '无' : value.externalNames,
  };
}
</script>
<style scoped>
.warehouse-entry-evidence,
.warehouse-entry-evidence__staffing {
  display: grid;
  gap: 16px;
  min-width: 0;
}
.warehouse-entry-evidence__warehouse {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 12px;
  margin-bottom: 12px;
  overflow-wrap: anywhere;
}
.warehouse-entry-evidence__planned {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}
.warehouse-entry-evidence--paired .warehouse-entry-evidence__planned {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.warehouse-entry-evidence__attendance {
  display: grid;
  gap: 16px;
  align-items: start;
  min-width: 0;
}
.warehouse-entry-evidence--wide .warehouse-entry-evidence__attendance {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.warehouse-entry-evidence__staffing-action {
  margin-top: 12px;
}
.warehouse-entry-evidence :deep(.n-descriptions-table-content) {
  white-space: pre-line;
  overflow-wrap: anywhere;
}
.warehouse-entry-evidence :deep(.n-button__content) {
  white-space: normal;
  overflow-wrap: anywhere;
}
.warehouse-entry-evidence :deep(.n-button) {
  max-width: 100%;
  height: auto;
  min-height: 28px;
}
</style>

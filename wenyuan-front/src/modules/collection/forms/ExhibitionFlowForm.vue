<template>
  <NForm ref="formRef" :model="inner" label-width="95px">
    <NGrid :cols="2" :x-gap="16">
      <NGi :span="2">
        <FlowFormItem field="title" label="展览名称" :rule="{ required: true, message: '请输入展览名称' }" v-slot="{ disabled }">
          <NInput v-model:value="inner.title" placeholder="如 瓷器精品展" :disabled="disabled" />
        </FlowFormItem>
      </NGi>

      <NGi>
        <FlowFormItem field="exhibitionNo" label="展览编号" v-slot="{ disabled }">
          <NInput v-model:value="inner.exhibitionNo" placeholder="展览编号 / 项目编号" :disabled="disabled" />
        </FlowFormItem>
      </NGi>

      <NGi>
        <FlowFormItem field="venueId" label="展馆" v-slot="{ disabled }">
          <WarehouseTreeSelect
            :value="inner.venueId"
            scene-type="EXHIBITION"
            :allowed-types="['EXHIBITION_HALL']"
            placeholder="请选择展馆/展厅"
            :disabled="disabled"
            @loaded="onVenueLocationsLoaded"
            @update:value="setVenue"
          />
        </FlowFormItem>
      </NGi>

      <NGi :span="2">
        <FlowFormItem field="organizer" label="主办/承办" v-slot="{ disabled }">
          <NInput v-model:value="inner.organizer" placeholder="主办、承办或执行部门" :disabled="disabled" />
        </FlowFormItem>
      </NGi>

      <NGi :span="2">
        <FlowFormItem field="collectionIds" label="参展藏品" v-slot="{ disabled }">
          <CollectionSelect
            v-model:value="selectedCollectionIds"
            :multiple="true"
            life-status="IN_STORE"
            placeholder="选择参展藏品"
            :disabled="disabled"
            @loaded="onCollectionsLoaded"
          />
        </FlowFormItem>
      </NGi>

      <NGi v-if="selectedCollectionIds.length" :span="2">
        <FlowFormItem field="placements" label="展柜分配" v-slot="{ disabled }">
          <div class="exhibition-placement-list">
            <NAlert type="info" :bordered="false">
              每件藏品分配一个展柜；多件藏品可以选择同一个展柜。
            </NAlert>
            <div v-for="collectionId in selectedCollectionIds" :key="collectionId" class="exhibition-placement-row">
              <div class="exhibition-placement-row__collection" :title="collectionLabel(collectionId)">
                {{ collectionLabel(collectionId) }}
              </div>
              <WarehouseTreeSelect
                :value="placementLocation(collectionId)"
                scene-type="EXHIBITION"
                :allowed-types="['SHOWCASE']"
                placeholder="选择展柜"
                :disabled="disabled"
                @update:value="setPlacementLocation(collectionId, $event)"
              />
            </div>
          </div>
        </FlowFormItem>
      </NGi>

      <NGi>
        <FlowFormItem field="state" label="状态" v-slot="{ disabled }">
          <HDictSelect v-model:value="inner.state" type="col_process_state" placeholder="展览状态" :disabled="disabled" />
        </FlowFormItem>
      </NGi>

      <NGi>
        <FlowFormItem field="startDate" label="开展日期" v-slot="{ disabled }">
          <NDatePicker
            v-model:value="inner.startDate"
            type="date"
            clearable
            style="width: 100%"
            :disabled="disabled"
          />
        </FlowFormItem>
      </NGi>

      <NGi>
        <FlowFormItem field="endDate" label="结束日期" v-slot="{ disabled }">
          <NDatePicker
            v-model:value="inner.endDate"
            type="date"
            clearable
            style="width: 100%"
            :disabled="disabled"
          />
        </FlowFormItem>
      </NGi>

      <NGi>
        <FlowFormItem field="coverImage" label="封面图" v-slot="{ disabled }">
          <HFileList v-if="disabled" :value="inner.coverImage" show-download-btn />
          <HUploadInput v-else v-model:value="inner.coverImage" @upload-finish="inner.coverImage = $event.id" accept="image/*" placeholder="上传展览封面或现场图" clearable :disabled="disabled" />
        </FlowFormItem>
      </NGi>

      <NGi :span="2">
        <FlowFormItem field="attachmentIds" label="附件" v-slot="{ disabled }">
          <HFileList v-if="disabled" :value="inner.attachmentIds" show-download-btn />
          <HUploadInput v-else v-model:value="inner.attachmentIds" @upload-finish="inner.attachmentIds = $event.id" placeholder="上传展陈方案、批复、参展清单等" clearable :disabled="disabled" />
        </FlowFormItem>
      </NGi>

      <NGi :span="2">
        <FlowFormItem field="remark" label="备注" v-slot="{ disabled }">
          <NInput v-model:value="inner.remark" type="textarea" :rows="3" placeholder="展陈说明、布撤展要求、特殊保护条件" :disabled="disabled" />
        </FlowFormItem>
      </NGi>
    </NGrid>
  </NForm>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useFlowForm } from '../../flow/composables/useFlowForm';
import FlowFormItem from '../../flow/components/FlowFormItem.vue';
import type { FlowFormExpose, FlowFormProps } from '../../flow/typings/flow';
import CollectionSelect from '../components/CollectionSelect.vue';
import WarehouseTreeSelect from '../components/WarehouseTreeSelect.vue';
import type { CollectionVo, ExhibitionPlacementVo, WarehouseVo } from '../typings/collection';

const props = defineProps<FlowFormProps>();
const emit = defineEmits<{ (e: 'update:formData', val: Record<string, any>): void }>();

const { inner, formRef, validate } = useFlowForm(props, emit);
const collectionLabels = ref<Record<string, string>>({});
const venueLocations = ref<WarehouseVo[]>([]);

const selectedCollectionIds = computed({
  get: () => splitIds(inner.value.collectionIds),
  set: (value: string[] | null) => {
    const ids = value || [];
    inner.value.collectionIds = ids.length ? ids.join(',') : undefined;
    const existing = placementMap();
    inner.value.placements = ids.map((collectionId) => ({
      collectionId,
      locationId: existing.get(collectionId),
    }));
  },
});

function onCollectionsLoaded(rows: CollectionVo[]) {
  const labels = Object.fromEntries(
    rows.filter((row) => row.id).map((row) => [
      String(row.id),
      [row.totalRegNo, row.name].filter(Boolean).join(' · ') || String(row.id),
    ]),
  );
  collectionLabels.value = { ...collectionLabels.value, ...labels };
}

function onVenueLocationsLoaded(rows: WarehouseVo[]) {
  venueLocations.value = rows;
  if (inner.value.venueId || !inner.value.venue) return;
  const matches = rows.filter((row) =>
    row.sceneType === 'EXHIBITION'
    && row.type === 'EXHIBITION_HALL'
    && row.name?.trim() === inner.value.venue?.trim(),
  );
  if (matches.length === 1) inner.value.venueId = matches[0].id;
}

function setVenue(venueId: string | null) {
  inner.value.venueId = venueId || undefined;
  inner.value.venue = venueId
    ? venueLocations.value.find((row) => row.id === venueId)?.name
    : undefined;
}

function collectionLabel(collectionId: string) {
  const summary = inner.value.collectionSummaries?.find((item: any) => item.id === collectionId);
  return collectionLabels.value[collectionId]
    || [summary?.totalRegNo, summary?.name].filter(Boolean).join(' · ')
    || collectionId;
}

function placementMap() {
  return new Map<string, string | undefined>(
    ((inner.value.placements || []) as ExhibitionPlacementVo[])
      .filter((item) => item.collectionId)
      .map((item) => [String(item.collectionId), item.locationId]),
  );
}

function placementLocation(collectionId: string) {
  return placementMap().get(collectionId) || null;
}

function setPlacementLocation(collectionId: string, locationId: string | null) {
  const existing = placementMap();
  existing.set(collectionId, locationId || undefined);
  inner.value.placements = selectedCollectionIds.value.map((id) => ({
    collectionId: id,
    locationId: existing.get(id),
  }));
}

function splitIds(value?: string | null) {
  return (value || '')
    .split(/[,，;\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

defineExpose<FlowFormExpose>({ validate });
</script>

<style scoped>
.exhibition-placement-list {
  display: grid;
  width: 100%;
  gap: 10px;
}

.exhibition-placement-row {
  display: grid;
  grid-template-columns: minmax(180px, 0.9fr) minmax(240px, 1.1fr);
  align-items: center;
  gap: 12px;
}

.exhibition-placement-row__collection {
  overflow: hidden;
  color: var(--text-color-2);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .exhibition-placement-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }
}
</style>

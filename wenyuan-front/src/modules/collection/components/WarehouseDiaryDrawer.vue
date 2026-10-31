<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="每日库房日记"
    ><NAlert v-if="readError" type="error">{{ readError }}</NAlert
    ><NSpace v-if="!reading && !readError" vertical :size="18"
      ><NAlert type="info"
        >如实记录当日实际进出时间、人员、工作、温湿度和库内安全情况。更正须保留原件；异常应报告保管负责人，重大异常报告主要负责人。</NAlert
      ><NAlert v-if="data.evidenceIssue" type="warning"><CollectionFieldLabel label="原件暂不可用，可查阅记录；恢复后可保存更正。" :help="data.evidenceIssue" /></NAlert
      ><NAlert v-if="data.entriesChanged" type="warning"
        >本日进出记录已有补充，请核对下方最新情况并补记日记；原签署快照继续保留。</NAlert
      ><NCard title="每日巡库检查要点" size="small"
        ><ul class="patrol-checklist">
          <li>核对实际人员进出、时间和工作内容。</li>
          <li>测量并记录温湿度，检查环境设备运行情况。</li>
          <li>检查门窗、门禁、消防、安防及水电设施。</li>
          <li>检查藏品、包装、柜架及虫霉、渗漏等异常。</li>
          <li>如实记录发现、处置和报告对象；上传当日签署原件。</li>
        </ul>
        <NText depth="3">检查要点供逐项核对，完成情况以实际填写、测量和签署记录为准。</NText></NCard
      ><NForm label-width="210" :disabled="!!data.evidenceIssue"
        ><NFormItem label="实际库房" required
          ><WarehouseTreeSelect
            v-model:value="form.warehouseId"
            :disabled="!!form.id"
            scene-type="STORAGE"
            :allowed-types="['WAREHOUSE', 'AREA']" /></NFormItem
        ><NFormItem label="日记实际日期" required
          ><NDatePicker v-model:value="form.logDate" :disabled="!!form.id" type="date" /></NFormItem
        ><NFormItem label="入库工作及巡查内容" required
          ><NInput v-model:value="form.content" type="textarea" /></NFormItem
        ><WarehouseRecordFields v-model="form.diary" :fields="warehouseDiaryFields(form.diary)" /><NCard
          v-for="(reading, index) in form.diary.readings || []"
          :key="index"
          :title="`实测环境记录 ${Number(index) + 1}`"
          size="small"
          ><WarehouseRecordFields v-model="form.diary.readings[index]" :fields="warehouseReadingFields" /><NButton
            v-if="form.diary.readings.length > 1"
            @click="form.diary.readings.splice(index, 1)"
            >移除本次编辑中的测量项</NButton
          ></NCard
        ></NForm
      ><NSpace
        ><NButton :disabled="!!data.evidenceIssue" @click="form.diary.readings.push({})">补充实际环境测量</NButton
        ><NButton :loading="loading" @click="loadEntries">核对当日实际人员进出</NButton
        ><NButton type="primary" :disabled="!!data.evidenceIssue" :loading="busy" @click="save">核实完整记录及原件后保存</NButton></NSpace
      ><NAlert v-if="entriesError" type="error">{{ entriesError }}</NAlert
      ><NCard title="最新实际人员进出" size="small"
        ><WarehouseDiaryVisits :entries="currentEntries" @open-entry="openEntry" /></NCard
      ><NCard v-if="data.entries" title="本版日记签署时的进出快照" size="small"
        ><WarehouseDiaryVisits :entries="data.entries" @open-entry="openEntry" /></NCard
      ><NCollapse
        ><NCollapseItem
          v-for="a in data.archives || []"
          :key="a.id"
          :name="a.id"
          :title="`日记原件 · ${formatLocalDateValue(a.archivedAt, 'YYYY-MM-DD HH:mm:ss')}`"
          ><NAlert v-if="a.evidenceIssue" type="warning"><CollectionFieldLabel label="该版原件暂不可用，以下为已保存的记录。" :help="a.evidenceIssue" /></NAlert
          ><HFileList
            :files="a.files"
            :original-url="(fileId: string) => originalUrl(a)(fileId)"
            :revision="[a?.id, a?.digest]"
            show-download-btn /><NText>{{ snapshot(a).workContent || snapshot(a).content }}</NText
          ><CollectionEvidenceReadView
            v-if="snapshot(a).diary"
            :fields="warehouseDiaryFields(snapshot(a).diary)"
            :data="snapshot(a).diary"
            :archive-files="a.files"
            :original-url="originalUrl(a)" /><CollectionEvidenceReadView
            v-for="(reading, i) in snapshot(a).diary?.readings || []"
            :key="i"
            :fields="warehouseReadingFields"
            :data="reading"
            :archive-files="a.files"
            :original-url="originalUrl(a)" /><WarehouseDiaryVisits
            v-if="snapshot(a).entries"
            :entries="snapshot(a).entries"
            @open-entry="openEntry" /></NCollapseItem></NCollapse
    ></NSpace>
    <WarehouseEntryCaseDrawer v-if="show" ref="entryRef" @saved="loadEntries" />
  </HPageForm>
</template>
<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';
import { useMessage } from 'naive-ui';
import { formatLocalDateValue } from '@/utils';
import { warehouseLogApi } from '../api/warehouseLog';
import WarehouseTreeSelect from './WarehouseTreeSelect.vue';
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import WarehouseDiaryVisits from './WarehouseDiaryVisits.vue';
import WarehouseEntryCaseDrawer from './WarehouseEntryCaseDrawer.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import CollectionFieldLabel from './CollectionFieldLabel.vue';
import {
  warehouseDiaryFields,
  warehouseReadingFields,
  warehouseEvidenceInitial,
  warehouseEvidencePayload,
  warehouseFieldError,
} from '../utils/warehouseEvidenceFields';
const emit = defineEmits<{ (e: 'saved'): void }>(),
  message = useMessage();
const show = ref(false),
  busy = ref(false),
  loading = ref(false),
  form = ref<any>({ diary: { readings: [{}] } }),
  data = ref<any>({}),
  currentEntries = ref<any>(),
  entryRef = ref<{ open: (id: string) => Promise<boolean> }>();
const reading = ref(false),
  readError = ref(''),
  entriesError = ref('');
let readRequest = 0;
let opening = false;
let entriesRequest = 0;
const snapshot = (a: any) => JSON.parse(a.snapshot);
async function openEntry(id: string) {
  if (show.value && id) await entryRef.value?.open(id);
}
const originalUrl = (archive: any) => (fileId: string) =>
  archive.files?.some((file: any) => file.fileId === fileId)
    ? `/collection/warehouse-log/${data.value.id}/original/${archive.id}/${fileId}`
    : '';
async function open(id?: string, initial: { warehouseId?: string; logDate?: number } = {}) {
  const request = ++readRequest;
  entriesRequest++;
  loading.value = false;
  reading.value = true;
  readError.value = '';
  entriesError.value = '';
  show.value = true;
  opening = true;
  data.value = {};
  form.value = { diary: { readings: [{}] } };
  currentEntries.value = undefined;
  try {
    const loaded = id ? ((await warehouseLogApi.get(id)) as any).data : {};
    if (request !== readRequest) return false;
    data.value = loaded;
    form.value = warehouseEvidenceInitial({
      ...(!id ? { logDate: Date.now(), ...initial } : {}),
      ...data.value,
      diary: {
        ...data.value.diary,
        readings: data.value.diary?.readings?.map((v: any) => ({ ...v })) || [{}],
        replacementReason: undefined,
      },
    });
    await nextTick();
    if (request !== readRequest) return false;
    currentEntries.value = data.value.currentEntries;
    form.value.entries = currentEntries.value;
    if (data.value.entriesChanged) form.value.diary.entryRecordsComplete = undefined;
    return true;
  } catch (error) {
    if (request === readRequest)
      readError.value = error instanceof Error ? error.message : '库房日记读取失败，请核对当前权限及原记录';
    return false;
  } finally {
    if (request === readRequest) {
      reading.value = false;
      opening = false;
    }
  }
}
watch(
  () => [form.value.warehouseId, form.value.logDate],
  () => {
    entriesRequest++;
    loading.value = false;
    entriesError.value = '';
    currentEntries.value = undefined;
    form.value.entries = undefined;
    if (!opening) form.value.diary.entryRecordsComplete = undefined;
  },
);
async function loadEntries() {
  if (!form.value.warehouseId || form.value.logDate == null) {
    message.warning('请先选择实际库房和日期');
    return;
  }
  const request = ++entriesRequest;
  loading.value = true;
  entriesError.value = '';
  currentEntries.value = undefined;
  form.value.entries = undefined;
  form.value.diary.entryRecordsComplete = undefined;
  try {
    const result = ((await warehouseLogApi.entries(form.value.warehouseId, form.value.logDate)) as any).data;
    if (request !== entriesRequest) return;
    currentEntries.value = result;
    if (JSON.stringify(form.value.entries) !== JSON.stringify(currentEntries.value))
      form.value.diary.entryRecordsComplete = undefined;
    form.value.entries = currentEntries.value;
  } catch (error) {
    if (request === entriesRequest)
      entriesError.value = error instanceof Error ? error.message : '人员进出读取失败，请重新核对';
  } finally {
    if (request === entriesRequest) loading.value = false;
  }
}
async function save() {
  if (data.value.evidenceIssue) {
    message.warning('请先恢复并核验归档原件，再保存更正');
    return;
  }
  const error =
    warehouseFieldError(warehouseDiaryFields(form.value.diary), form.value.diary) ||
    form.value.diary.readings.map((v: any) => warehouseFieldError(warehouseReadingFields, v)).find(Boolean);
  if (error || !form.value.warehouseId || form.value.logDate == null || !form.value.content?.trim()) {
    message.warning(error || '请填写实际库房、日期和工作内容');
    return;
  }
  if (!form.value.entries) {
    message.warning('请先核对当日实际人员进出');
    return;
  }
  busy.value = true;
  try {
    const id = ((await warehouseLogApi.save(warehouseEvidencePayload(form.value))) as any).data;
    if (await open(id)) emit('saved');
  } finally {
    busy.value = false;
  }
}
defineExpose({ open });
</script>

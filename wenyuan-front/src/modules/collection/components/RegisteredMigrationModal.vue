<template>
  <NModal v-model:show="visible" :mask-closable="session.operation.value !== 'commit'" :closable="session.operation.value !== 'commit'" preset="card" title="已登账藏品迁移（本馆旧系统）" style="width: 1100px; max-width: calc(100vw - 32px)">
    <div class="migration-body">
      <NAlert type="info">保留原总登记号，迁入后统一待独立复核。复核通过即确认原有登记事实，编目可另行完善，不重新登账。原账缺失字段允许保留空值，复核时须逐项说明并提供登记依据。外馆调入请使用调拨接收。</NAlert>
      <NForm label-placement="left" label-width="110" :disabled="busy">
        <NFormItem label="来源系统" required><NInput v-model:value="sourceSystem" placeholder="例如：本馆原藏品管理系统" maxlength="100" /></NFormItem>
        <NFormItem label="迁移核验说明" required><NInput v-model:value="reason" type="textarea" :rows="2" maxlength="500" placeholder="说明原账、登记依据及本次核验范围" /></NFormItem>
        <NFormItem label="本馆归属确认"><NCheckbox v-model:checked="belongs" :disabled="busy">已核对为本馆旧系统数据，非外馆调入</NCheckbox></NFormItem>
      </NForm>
      <p class="migration-note">模板中的原登记馆代码须与当前馆代码一致。登记依据填写已上传文件的唯一文件名；历史增减核对为“是”仅适用于已核对原账并确认原登记以来未发生馆藏归属、数量及单位变化的记录，否则填“否”。借出、提用等状态保留，原业务单据须另外核对迁移。</p>
      <NSpace>
        <NButton :disabled="busy || !columns.length" @click="fileInput?.click()">选择 Excel/CSV</NButton>
        <NButton :disabled="busy || !columns.length" @click="download">下载迁移模板</NButton>
        <NText>{{ fileName || '未选择文件' }} · {{ rows.length }} 条</NText>
      </NSpace>
      <input ref="fileInput" type="file" accept=".xlsx,.xls,.csv" hidden @change="readFile" />
      <NCollapse><NCollapseItem title="模板列说明" name="template"><NDataTable :columns="templateColumns" :data="columns" :max-height="240" size="small" /></NCollapseItem></NCollapse>
      <NDataTable v-if="errors.length" :columns="errorColumns" :data="errors" :max-height="240" size="small" />
      <NAlert v-if="confirmed" type="success">
        目标馆：{{ confirmed.preview.confirmation.orgName }}（{{ confirmed.preview.confirmation.orgId }}）；
        迁入 {{ confirmed.preview.createCount }} 条，均待独立复核，无覆盖更新。表中“已核实”仅保留为原系统声明。
      </NAlert>
      <NCheckbox v-if="confirmed" v-model:checked="approve">已核对目标馆、原登记资料及待复核范围，确认迁移</NCheckbox>
    </div>
    <template #footer><NSpace justify="end">
      <NButton :disabled="session.operation.value === 'commit'" @click="visible = false">取消</NButton>
      <NButton :loading="session.operation.value === 'validate'" :disabled="busy || !rows.length || !belongs || !sourceSystem.trim() || !reason.trim()" @click="validate">预校验</NButton>
      <NButton type="primary" :loading="session.operation.value === 'commit'" :disabled="busy || !confirmed || !approve" @click="commit">确认迁移</NButton>
    </NSpace></template>
  </NModal>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import * as XLSX from 'xlsx';
import { collectionApi } from '../api/collection';
import { useCollectionImportSession } from '../composables/useCollectionImportSession';
import { yuanImportColumn, parseImportAmount } from '../utils/moneyYuan';
import { migrationPayload, migrationPreview, migrationCommit } from '../utils/registeredMigration';
import type { CollectionImportTemplateColumnVo, CollectionImportErrorVo } from '../typings/collection';
const emit = defineEmits<{ saved: [] }>();
const message = useMessage();
const visible = ref(false), sourceSystem = ref(''), reason = ref(''), belongs = ref(false), approve = ref(false);
const rows = ref<Record<string, unknown>[]>([]), columns = ref<CollectionImportTemplateColumnVo[]>([]), errors = ref<CollectionImportErrorVo[]>([]);
const fileName = ref(''), fileInput = ref<HTMLInputElement | null>(null);
const confirmed = ref<{ request: ReturnType<typeof migrationPayload>; preview: ReturnType<typeof migrationPreview> } | null>(null);
const session = useCollectionImportSession(() => { visible.value = false; clear(); });
const busy = session.busy;
const templateColumns = [{ title: '字段', key: 'title' }, { title: '必填', key: 'required', render: (r: CollectionImportTemplateColumnVo) => r.required ? '是' : '原账未载可留空' }, { title: '示例', key: 'sample' }];
const errorColumns = [{ title: '行号', key: 'rowNo', width: 65 }, { title: '字段', key: 'field', width: 170 }, { title: '问题', key: 'message' }];
function clear() { rows.value = []; errors.value = []; confirmed.value = null; approve.value = false; fileName.value = ''; if(fileInput.value) fileInput.value.value = ''; }
watch([rows, sourceSystem, reason, belongs], () => { session.invalidate(); confirmed.value = null; approve.value = false; }, { deep: true, flush: 'sync' });
watch(visible, show => { if(!show) { session.invalidate(); clear(); } });
async function open() {
  session.invalidate(); clear(); sourceSystem.value = ''; reason.value = ''; belongs.value = false; columns.value = []; visible.value = true;
  const ticket = session.begin('load'); if(ticket === null) return;
  try {
    const response: any = await collectionApi.migrationTemplate(); if(!session.current(ticket) || !visible.value) return;
    if(!Array.isArray(response?.data) || !response.data.length || response.data.some((c: any) => !c || typeof c.field !== 'string' || typeof c.title !== 'string')) throw Error('迁移模板未完整加载');
    columns.value = response.data.map((c: CollectionImportTemplateColumnVo) => yuanImportColumn(c,new Set(['valuation'])));
  } catch(e: any) { if(session.current(ticket)) message.error(e?.message || '迁移模板加载失败'); }
  finally { session.finish(ticket); }
}
function download() {
  const workbook = XLSX.utils.book_new(); XLSX.utils.book_append_sheet(workbook,XLSX.utils.aoa_to_sheet([columns.value.map(c => c.title), columns.value.map(c => c.sample || '')]),'已登账迁移');
  XLSX.writeFile(workbook,'已登账藏品迁移模板.xlsx');
}
async function readFile(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; if(!file || !visible.value || busy.value) return;
  session.invalidate(); confirmed.value = null; approve.value = false; errors.value = [];
  const ticket = session.begin('read'); if(ticket === null) return;
  try {
    const workbook = XLSX.read(await file.arrayBuffer(),{ type: 'array' }); if(!session.current(ticket) || !visible.value) return;
    const sheet = workbook.Sheets[workbook.SheetNames[0]]; if(!sheet) throw Error('文件没有可读取的工作表');
    const headers = new Map(columns.value.flatMap(c => [[c.title,c.field],[c.field,c.field]]));
    headers.set('登记依据文件ID','registrationEvidence'); headers.set('registrationEvidence','registrationEvidence');
    const parsed = XLSX.utils.sheet_to_json<Record<string, unknown>>(sheet,{ defval: '', raw: false }).map((row,index) => {
      const result: Record<string, unknown> = { rowNo: index + 2 };
      for(const [key,raw] of Object.entries(row)) {
        const field = headers.get(key.trim()); const value = String(raw ?? '').trim(); if(!field || !value) continue;
        result[field] = field === 'quantity' ? Number(value) : field === 'valuation' ? parseImportAmount(value,key) : value;
      }
      return result;
    }).filter(row => Object.keys(row).length > 1);
    rows.value = parsed; fileName.value = file.name; input.value = ''; message.success(`已解析 ${parsed.length} 条，请预校验`);
  } catch(e: any) { if(session.current(ticket)) { clear(); message.error(e?.message || '文件解析失败'); } }
  finally { session.finish(ticket); }
}
async function validate() {
  if(busy.value || !visible.value) return;
  const request = migrationPayload(rows.value,sourceSystem.value,reason.value,belongs.value); confirmed.value = null; approve.value = false;
  const ticket = session.begin('validate'); if(ticket === null) return;
  try {
    const response = await collectionApi.validateMigration(request); if(!session.current(ticket) || !visible.value) return;
    const preview = migrationPreview(response,rows.value.length); errors.value = preview.errors;
    if(preview.valid) confirmed.value = {request,preview}; else message.warning('存在校验问题，未迁移');
  } catch(e: any) { if(session.current(ticket)) message.error(e?.message || '迁移预校验失败'); }
  finally { session.finish(ticket); }
}
async function commit() {
  if(busy.value || !confirmed.value || !approve.value || !visible.value) return;
  const frozen = confirmed.value; const ticket = session.begin('commit'); if(ticket === null) return;
  try {
    const response = await collectionApi.commitMigration(frozen.request,frozen.preview.confirmation); if(!session.current(ticket) || !visible.value) return;
    const result = migrationCommit(response,frozen.preview.createCount); errors.value = result.errors;
    if(!result.valid) { confirmed.value = null; approve.value = false; message.warning('提交时核验未通过，未迁移'); return; }
    message.success(`已迁入 ${result.createdIds.length} 条，请到“迁移批次复核”办理核验`); visible.value = false; emit('saved');
  } catch(e: any) { if(session.current(ticket)) { confirmed.value = null; approve.value = false; message.error(e?.message || '迁移提交失败，请核对结果后重新预校验'); } }
  finally { session.finish(ticket); }
}
defineExpose({open});
</script>
<style scoped>
.migration-body { display: grid; gap: 16px; max-height: calc(100vh - 210px); overflow-y: auto; padding-right: 8px; }
.migration-note { margin: 0; color: #64748b; line-height: 1.6; }
</style>

<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :scroll-x="1390"
    :fetch-data="restorationArchiveApi.page"
    :is-delete="false"
    :is-add="canWriteArchive"
    :is-edit="canWriteArchive"
    :add-permission="RESTORATION_ARCHIVE_WRITE_PERMISSION"
    :edit-permission="RESTORATION_ARCHIVE_WRITE_PERMISSION"
    :params="queryParams"
    row-key-field="id"
    @add="onAdd"
    @row-edit="onEdit"
    @row-view="onView"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="RESTORATION_ACCEPTANCE_2026" /></template>
    <template #queryForm>
      <NFormItem label="关键字" style="flex: 0 0 300px; max-width: 100%; min-width: 0">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="档案编号 / 备注" style="width: 100%; min-width: 0" />
      </NFormItem>
      <NFormItem label="档案编号" style="flex: 0 0 280px; max-width: 100%; min-width: 0">
        <NInput v-model:value="queryParams.archiveNo" clearable style="width: 100%; min-width: 0" />
      </NFormItem>
      <NFormItem label="归档状态" style="flex: 0 0 240px; max-width: 100%; min-width: 0">
        <NSelect
          v-model:value="queryParams.approved"
          :options="approvedOptions"
          clearable
          style="width: 100%; min-width: 0"
          placeholder="全部"
        />
      </NFormItem>
    </template>

    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <HPageForm
      v-if="formShow" :key="`archive-main-${formKey}`"
      v-model:show="formShow"
      :title="title"
      :model="archive"
      :rules="mode === 'delete' ? {} : rules"
      :auto-reload="false" :auto-close="false"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="140"
      width="860px"
    >
      <template #form-action>
        <NButton v-if="archive.id && mode !== 'delete' && canPrintArchive" style="margin-right: 8px" @click="() => onPrint()">归档打印</NButton>
        <NButton
          v-if="canWriteArchive && archive.id && !archive.approvedTime && mode !== 'delete'"
          type="success"
          style="margin-right: 8px"
          @click="onApprove"
        >
          验收原件与实际归档
        </NButton>
      </template>

      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NAlert v-if="loading" type="info">正在读取当前原馆及档案病害依据…</NAlert>
      <NAlert v-if="mode === 'delete'" type="warning">确认删除档案 {{ archive.archiveNo }}（{{ archive.id }}），原项目 {{ archive.restorationId }}。原件历史继续保留。</NAlert>
      <template v-else>
      <NTag
        v-if="archive.approvedTime"
        type="success"
        :bordered="false"
        size="small"
        style="margin-bottom: 12px"
      >
        已归档 · {{ formatDateTime(archive.approvedTime) }}{{ archive.approvedByName ? ` · ${archive.approvedByName}` : '' }}
      </NTag>

      <NTabs v-model:value="activeTab" type="line" animated>
        <NTabPane name="main" tab="档案主件">
          <NAlert type="info" style="margin-bottom: 12px" :show-icon="false">
            WW/T 0032-2010 十二项档案条目中的 <b>第 1/3/7/10/11 项</b> 在此录入；病害记录（第 4 项）在下一 tab；检测报告（第 5 项）与验收（第 12 项）分别由修复项目附件与验收单承载。
          </NAlert>

          <NFormItem label="修复项目" path="restorationId">
            <RestorationProjectPicker
              v-model:value="archive.restorationId"
              :disabled="mode !== 'add'"
              style="width: 360px"
            />
          </NFormItem>
          <NFormItem label="档案编号" path="archiveNo">
            <NInput v-model:value="archive.archiveNo" placeholder="馆代号-年份-序号" style="width: 320px" />
          </NFormItem>

          <NFormItem label="修复前现状描述" path="beforeDescription">
            <NInput
              v-model:value="archive.beforeDescription"
              type="textarea"
              :autosize="{ minRows: 4, maxRows: 20 }"
              placeholder="记录修复前的现状：外观、破损、稳定性、颜色、附着物等"
            />
          </NFormItem>

          <NFormItem label="修复后现状描述" path="afterDescription">
            <NInput
              v-model:value="archive.afterDescription"
              type="textarea"
              :autosize="{ minRows: 4, maxRows: 20 }"
              placeholder="记录修复后的现状：处理结果、颜色恢复、结构稳定性等"
            />
          </NFormItem>

          <NFormItem
            label="材料清单"
            path="materialsUsed"
            :feedback="!materials.length ? '暂无材料。点击「新增材料」录入本次修复用到的材料。' : undefined"
          >
            <div class="rows-block">
              <div v-if="materials.length" class="rows-header">
                <div class="col-name">材料名称</div>
                <div class="col-qty">用量</div>
                <div class="col-unit">单位</div>
                <div class="col-batch">批号</div>
                <div class="col-supplier">供应商</div>
                <div class="col-ops" />
              </div>
              <div v-for="(item, idx) in materials" :key="idx" class="rows-row">
                <NInput v-model:value="item.name" placeholder="如：环氧树脂" class="col-name" />
                <NInputNumber v-model:value="item.qty" :precision="2" :min="0" class="col-qty" />
                <NInput v-model:value="item.unit" placeholder="g / ml / 支" class="col-unit" />
                <NInput v-model:value="item.batch" placeholder="20260601" class="col-batch" />
                <NInput v-model:value="item.supplier" placeholder="供应商" class="col-supplier" />
                <NButton class="col-ops" size="small" quaternary type="error" @click="removeMaterial(idx)">移除</NButton>
              </div>
              <div style="margin-top: 8px">
                <NButton size="small" dashed @click="addMaterial">+ 新增材料</NButton>
              </div>
            </div>
          </NFormItem>

          <NFormItem
            label="修复人员签名"
            path="restorerSignatures"
            :feedback="!signatures.length ? '暂无签名。每位修复师上传签名影像并填写资质证书号。' : undefined"
          >
            <div class="rows-block">
              <div v-if="signatures.length" class="rows-header">
                <div class="col-name">姓名</div>
                <div class="col-cert">资质证书号</div>
                <div class="col-sign">签名影像</div>
                <div class="col-ops" />
              </div>
              <div v-for="(item, idx) in signatures" :key="idx" class="rows-row">
                <NInput v-model:value="item.name" placeholder="姓名" class="col-name" />
                <NInput v-model:value="item.certNo" placeholder="职业资格证书号" class="col-cert" />
                <HUploadInput
                  v-model:value="item.signImageId"
                  @upload-finish="item.signImageId = $event.id"
                  accept="image/*"
                  placeholder="签名 fileId"
                  clearable
                  class="col-sign"
                >
                  上传
                </HUploadInput>
                <NButton class="col-ops" size="small" quaternary type="error" @click="removeSignature(idx)">移除</NButton>
              </div>
              <div style="margin-top: 8px">
                <NButton size="small" dashed @click="addSignature">+ 新增签名</NButton>
              </div>
            </div>
          </NFormItem>

          <NFormItem label="备注" path="remarks">
            <NInput v-model:value="archive.remarks" type="textarea" :autosize="{ minRows: 3, maxRows: 12 }" />
          </NFormItem>
        </NTabPane>

        <NTabPane name="damage" :tab="`病害记录${damages.length ? ` (${damages.length})` : ''}`">
          <NAlert type="info" style="margin-bottom: 12px" :show-icon="false">
            对应 WW/T 0032-2010 档案第 4 项「病害描述与图示」。每条病害独立记录一种类型与部位，图示可选。
          </NAlert>
          <div style="margin-bottom: 8px">
            <NButton v-if="canWriteArchive" type="primary" size="small" :disabled="!archive.restorationId" @click="onAddDamage">+ 新增病害</NButton>
            <span v-if="!archive.restorationId" style="margin-left: 12px; color: #999; font-size: 12px">
              先保存档案主件（选定修复项目）后再新增病害
            </span>
          </div>
          <NDataTable :columns="damageColumns" :scroll-x="730" :data="damages" size="small" :bordered="false" />
        </NTabPane>
      </NTabs>
      </template>
    </HPageForm>

    <HPageForm
      v-if="damageShow" :key="`archive-damage-${damageFormKey}`"
      v-model:show="damageShow"
      :title="damageTitle"
      :model="damageVo"
      :rules="damageMode === 'delete' ? {} : damageRules"
      :handle-submit="handleDamageSubmit"
      :auto-close="false" :auto-reload="false"
      label-placement="left"
      label-width="100"
      width="640px"
    >
      <NAlert v-if="damageError" type="error">{{ damageError }}</NAlert>
      <NAlert v-if="damageLoading" type="info">正在读取病害原件…</NAlert>
      <NAlert v-if="damageMode === 'delete'" type="warning">确认删除病害 {{ damageVo.id }}，原项目 {{ damageVo.restorationId }}。</NAlert>
      <template v-else>
      <NFormItem label="病害类型" path="damageType">
        <HDictSelect v-model:value="damageVo.damageType" type="restoration_damage_type" clearable style="width: 220px" />
      </NFormItem>
      <NFormItem label="严重程度" path="severity">
        <HDictSelect v-model:value="damageVo.severity" type="restoration_damage_severity" clearable style="width: 220px" />
      </NFormItem>
      <NFormItem label="部位" path="location">
        <NInput v-model:value="damageVo.location" placeholder="口沿 / 圈足 / 中腹部…" />
      </NFormItem>
      <NFormItem label="描述" path="description">
        <NInput
          v-model:value="damageVo.description"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 12 }"
          placeholder="详细描述病害形态、成因推测、是否稳定等"
        />
      </NFormItem>
      <NFormItem label="图示" path="imageFileIds">
        <HUploadInput
          :value="damageVo.imageFileIds" :disabled="!handleDamageSubmit"
          @update:value="damageImageUpdate" @upload-finish="damageUploadFinished"
          accept="image/*"
          placeholder="病害图示 fileId（多张请分次上传后手工拼接逗号分隔）"
          clearable
        >
          上传图示
        </HUploadInput>
      </NFormItem>
      <NFormItem label="排序" path="sort">
        <NInputNumber v-model:value="damageVo.sort" :min="0" />
      </NFormItem>
      </template>
    </HPageForm>

    <HPageView v-model:show="viewShow" title="修复档案详情" width="860px">
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="档案编号">{{ viewRow.archiveNo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="藏品">{{ `${viewRow.collectionTotalRegNo || ''} ${viewRow.collectionName || ''}`.trim() || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="修复机构">{{ viewRow.restoreOrg || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="修复前现状">{{ viewRow.beforeDescription || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="修复后现状">{{ viewRow.afterDescription || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="材料清单">{{ viewRow.materialsUsed || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="人员签名"><template v-if="parseSignatures(viewRow.restorerSignatures).length">
            <div v-for="(signature, index) in parseSignatures(viewRow.restorerSignatures)" :key="index">
              <div>{{ signature.name || '姓名未留存' }} · {{ signature.certNo || '资质证书号未留存' }}</div>
              <HFileList :value="signature.signImageId" :revision="viewRow.id" />
            </div>
          </template>
          <template v-else>{{ viewRow.restorerSignatures ? '原记录未留存结构化签名影像，请核查原件' : '未留存' }}</template></NDescriptionsItem>
        <NDescriptionsItem label="病害记录">
          <template v-if="viewDamages.length">
            <div v-for="d in viewDamages" :key="d.id" style="margin-bottom: 4px">
              <HDictText type="restoration_damage_type" :value="d.damageType" />
              · <HDictText type="restoration_damage_severity" :value="d.severity" />
              · {{ d.location || '—' }}
              <span v-if="d.description" style="color:#888;font-size:12px;margin-left:8px">{{ d.description }}</span>
            </div>
          </template>
          <template v-else>无</template>
        </NDescriptionsItem>
        <NDescriptionsItem label="归档状态">
          <NTag :type="viewRow.approvedTime ? 'success' : 'default'" size="small" :bordered="false">
            {{ viewRow.approvedTime ? `已归档 · ${formatDateTime(viewRow.approvedTime)}` : '未归档' }}
          </NTag>
        </NDescriptionsItem>
        <NDescriptionsItem label="备注">{{ viewRow.remarks || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>
    <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />
  <RestorationCloseoutDrawer v-if="formShow" :key="`archive-closeout-${formKey}`" ref="closeoutRef" @saved="afterCloseout" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { h, reactive, ref, resolveComponent, watch, computed, resolveDirective, withDirectives } from 'vue';
import { useArchiveRecordWindow } from '../composables/useArchiveRecordWindow';
import {
  NAlert, NButton, NDataTable, NDescriptions, NDescriptionsItem, NFormItem, NInput, NInputNumber,
  NSelect, NTabPane, NTabs, NTag,
} from 'naive-ui';
import { restorationArchiveApi } from '../api/archive';
import RestorationCloseoutDrawer from '../components/RestorationCloseoutDrawer.vue';
const closeoutRef=ref<any>();
import RestorationProjectPicker from '../components/RestorationProjectPicker.vue';
import type { RestorationArchiveVo, RestorationDamageVo } from '../typings';
import { formatDateTime } from '@/utils/datetime';
import CollectionPrintPreviewModal from '@/modules/collection/components/CollectionPrintPreviewModal.vue';
import {
  COLLECTION_PRINT_RENDER_CODES,
  RESTORATION_ARCHIVE_WRITE_CODES,
  RESTORATION_ARCHIVE_WRITE_PERMISSION,
} from '../utils/restorationPermissions';
import { useAuthStore } from '@/store';

const HDictText = resolveComponent('HDictText');

interface MaterialRow {
  [key: string]: any;
  name?: string; qty?: number | null; unit?: string; batch?: string; supplier?: string;
}
interface SignatureRow { [key:string]:any; name?: string; certNo?: string; signImageId?: string; }

const tableRef = ref<any>(null);
const activeTab = ref<'main' | 'damage'>('main');
const materials = ref<MaterialRow[]>([]);
const signatures = ref<SignatureRow[]>([]);
const aw=useArchiveRecordWindow<RestorationArchiveVo>('archive',()=>tableRef.value?.handleRefresh(),row=>({...row,materialsUsed:serializeMaterials(materials.value),restorerSignatures:serializeSignatures(signatures.value)}));
const {formShow,viewShow,loading,error,title,editVo:archive,viewRow,mode,formKey,handleSubmit,onEdit,onDelete,onView,onPrint,printShow,printData}=aw;
const dw=useArchiveRecordWindow<RestorationDamageVo>('damage',()=>{tableRef.value?.handleRefresh();void aw.reloadRelated();});
const {formShow:damageShow,loading:damageLoading,error:damageError,title:damageTitle,editVo:damageVo,mode:damageMode,formKey:damageFormKey,handleSubmit:handleDamageSubmit,updateImageFiles:damageImageUpdate,uploadFinished:damageUploadFinished,onEdit:onEditDamage,onDelete:onDeleteDamage}=dw;
const damages=computed(()=>aw.context.value?.damages||[]),viewDamages=damages;
const permission=resolveDirective('permission');
const auth=useAuthStore();
// 档案及其病害的写接口（RestorationArchiveController.save / delete / approve / saveDamage / deleteDamage）同一组码；
// 未传 permission 时表格放行，另用 is-add / is-edit 开关。
const canWriteArchive=computed(()=>auth.hasPermission(RESTORATION_ARCHIVE_WRITE_CODES));
const canPrintArchive=computed(()=>auth.hasPermission(COLLECTION_PRINT_RENDER_CODES));
watch([formShow,formKey],()=>{dw.close();activeTab.value='main';},{flush:'sync'});

const rules = {
  restorationId: { required: true, message: '请选择修复项目', trigger: ['change', 'blur'] },
  archiveNo: { required: true, message: '请输入档案编号', trigger: ['blur', 'input'] },
};
const damageRules = {
  damageType: { required: true, message: '请选择病害类型', trigger: ['change', 'blur'] },
  severity: { required: true, message: '请选择严重程度', trigger: ['change', 'blur'] },
};

const queryParams = reactive({
  keyword: null as string | null,
  archiveNo: null as string | null,
  approved: null as string | null,
});
const approvedOptions = [
  { label: '已归档', value: 'true' },
  { label: '未归档', value: 'false' },
];

// 操作列放最后：查看 / 打印（有 render.pdf 权限）/ 删除（有写权限）；编辑由 HPageDataTable 合并进 operate。
const operateColumn={title:'操作',key:'operate',type:'operate',width:220,align:'center',fixed:'right' as const,render:(r:RestorationArchiveVo)=>{
  const btns:any[]=[h(NButton,{text:true,type:'primary',size:'small',onClick:()=>onView(r)},{ default: () => '查看' })];
  if(canPrintArchive.value) btns.push(h(NButton,{text:true,size:'small',style:'margin-left:8px',onClick:()=>onPrint(r)},{ default: () => '打印' }));
  if(canWriteArchive.value){
    const del=h(NButton,{size:'small',type:'error',text:true,style:'margin-left:8px',onClick:()=>onDelete(r)},{ default: () => '删除' });
    btns.push(permission?withDirectives(del,[[permission,[...RESTORATION_ARCHIVE_WRITE_CODES]]]):del);
  }
  return btns;
}};
const columns = computed<any[]>(() => [
  { title: '档案编号', key: 'archiveNo', columnType: 'title', width: 180, ellipsis: { tooltip: true }, resizable: true },
  {
    title: '藏品', key: 'collection', width: 240, resizable: true, ellipsis: { tooltip: true },
    render: (r: RestorationArchiveVo) => `${r.collectionTotalRegNo || ''} ${r.collectionName || ''}`.trim() || '—',
  },
  { title: '修复机构', key: 'restoreOrg', width: 200, resizable: true, ellipsis: { tooltip: true } },
  {
    title: '病害数', key: 'damageCount', width: 90,
    render: (r: RestorationArchiveVo) => r.damageCount ?? 0,
  },
  {
    title: '归档状态', key: 'approved', width: 120, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationArchiveVo) => h(
      NTag,
      { size: 'small', type: r.approvedTime ? 'success' : 'default', bordered: false },
      { default: () => (r.approvedTime ? '已归档' : '未归档') },
    ),
  },
  { title: '归档时间', key: 'approvedTime', width: 190, ellipsis: { tooltip: true }, resizable: true, render: (r: RestorationArchiveVo) => formatDateTime(r.approvedTime) },
  { title: '创建时间', key: 'createTime', width: 190, ellipsis: { tooltip: true }, resizable: true, render: (r: RestorationArchiveVo) => formatDateTime(r.createTime) },
  operateColumn,
]);

// ── 材料 / 签名 序列化 ──
function parseMaterials(raw?: string | null): MaterialRow[] {
  if (!raw) return [];
  try {
    const arr = JSON.parse(raw);
    if (Array.isArray(arr)) {
      return arr.map((m: any) => ({
        ...m,
        name: m?.name ?? '',
        qty: m?.quantity ?? m?.qty ?? null,
        unit: m?.unit ?? '', batch: m?.batch ?? '', supplier: m?.supplier ?? '',
      }));
    }
  } catch { /* 忽略 */ }
  return [];
}
function serializeMaterials(list: MaterialRow[]): string | undefined {
  const cleaned = list
    .map((m) => ({
      ...m,
      name: (m.name || '').trim(),
      ...('quantity' in m ? {quantity:m.qty,qty:undefined} : {qty:m.qty}),
      unit: (m.unit || '').trim(), batch: (m.batch || '').trim(), supplier: (m.supplier || '').trim(),
    }))
    .filter((m) => m.name || m.qty != null || ('quantity' in m && m.quantity != null) || m.unit || m.batch || m.supplier);
  return cleaned.length ? JSON.stringify(cleaned) : undefined;
}
function parseSignatures(raw?: string | null): SignatureRow[] {
  if (!raw) return [];
  try {
    const arr = JSON.parse(raw);
    if (Array.isArray(arr)) return arr.map((s: any) => ({ ...s, name: s?.name ?? '', certNo: s?.certNo ?? '', signImageId: s?.signImageId ?? '' }));
  } catch { /* 忽略 */ }
  return [];
}
function serializeSignatures(list: SignatureRow[]): string | undefined {
  const cleaned = list
    .map((s) => ({ ...s, name: (s.name || '').trim(), certNo: (s.certNo || '').trim(), signImageId: (s.signImageId || '').trim() }))
    .filter((s) => s.name || s.certNo || s.signImageId);
  return cleaned.length ? JSON.stringify(cleaned) : undefined;
}
watch(() => archive.value.materialsUsed, (v) => (materials.value = parseMaterials(v)));
watch(() => archive.value.restorerSignatures, (v) => (signatures.value = parseSignatures(v)));
const addMaterial = () => materials.value.push({});
const removeMaterial = (i: number) => materials.value.splice(i, 1);
const addSignature = () => signatures.value.push({});
const removeSignature = (i: number) => signatures.value.splice(i, 1);

const onAdd = () => {
  materials.value = [];
  signatures.value = [];
  activeTab.value = 'main';
  aw.onAdd();
};

const onApprove = async () => {
  if(archive.value.restorationId) await closeoutRef.value?.open(archive.value.restorationId);
};
async function afterCloseout() {
  tableRef.value?.handleRefresh();
  if(formShow.value && archive.value.id) await aw.onEdit({...archive.value});
}

// ── 病害记录 ──
const damageColumns: any[] = [
  {
    title: '病害类型', key: 'damageType', width: 130, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationDamageVo) => h(HDictText, { type: 'restoration_damage_type', value: r.damageType }),
  },
  {
    title: '严重度', key: 'severity', width: 100, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationDamageVo) => h(HDictText, { type: 'restoration_damage_severity', value: r.severity }),
  },
  { title: '部位', key: 'location', width: 160, ellipsis: { tooltip: true }, resizable: true },
  { title: '描述', key: 'description', width: 240, resizable: true, ellipsis: { tooltip: true } },
  { title: '排序', key: 'sort', width: 60 },
  {
    title: '操作', key: 'ops', width: 130,
    // 病害编辑 / 删除与档案同一组写码（saveDamage / deleteDamage），无权限时不显示按钮。
    render: (r: RestorationDamageVo) => h('div', {}, canWriteArchive.value ? [
      h(NButton, { size: 'tiny', onClick: () => onEditDamage(r) }, () => '编辑'),
      h(NButton, { size: 'tiny', style: 'margin-left:6px', onClick: () => onDeleteDamage(r) }, () => '删除'),
    ] : []),
  },
];

const onAddDamage = () => {
  if (!archive.value.restorationId) return;
  dw.onAdd(archive.value.restorationId);
};
</script>

<style scoped>
.rows-block { width: 100%; }
.rows-header {
  display: flex;
  gap: 8px;
  align-items: center;
  color: #666;
  font-size: 12px;
  padding: 0 4px 6px;
  border-bottom: 1px dashed rgba(0, 0, 0, 0.08);
  margin-bottom: 6px;
}
.rows-row { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; }
.col-name { flex: 0 0 180px; }
.col-qty { flex: 0 0 110px; }
.col-unit { flex: 0 0 90px; }
.col-batch { flex: 0 0 150px; }
.col-supplier { flex: 1 1 auto; min-width: 140px; }
.col-cert { flex: 0 0 220px; }
.col-sign { flex: 1 1 auto; min-width: 220px; }
.col-ops { flex: 0 0 60px; text-align: right; }
</style>

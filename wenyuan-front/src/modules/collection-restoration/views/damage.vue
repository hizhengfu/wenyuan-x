<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :scroll-x="1420"
    :fetch-data="restorationArchiveApi.damagePage"
    :is-delete="false"
    :is-add="canWriteDamage"
    :is-edit="canWriteDamage"
    :add-permission="RESTORATION_ARCHIVE_WRITE_PERMISSION"
    :edit-permission="RESTORATION_ARCHIVE_WRITE_PERMISSION"
    :params="queryParams"
    row-key-field="id"
    @add="onAdd(queryParams.restorationId)"
    @row-edit="onEdit"
    @row-view="onView"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="COLLECTION_RESTORATION" /></template>
    <template #queryForm>
      <NFormItem label="关键字" style="flex: 0 0 270px; max-width: 100%; min-width: 0">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="部位 / 描述" style="width: 100%; min-width: 0" />
      </NFormItem>
      <NFormItem label="病害类型" style="flex: 0 0 270px; max-width: 100%; min-width: 0">
        <HDictSelect v-model:value="queryParams.damageType" type="restoration_damage_type" clearable style="width: 100%; min-width: 0" />
      </NFormItem>
      <NFormItem label="严重程度" style="flex: 0 0 270px; max-width: 100%; min-width: 0">
        <HDictSelect v-model:value="queryParams.severity" type="restoration_damage_severity" clearable style="width: 100%; min-width: 0" />
      </NFormItem>
      <NFormItem label="修复项目" style="flex: 0 0 350px; max-width: 100%; min-width: 0">
        <RestorationProjectPicker v-model:value="queryParams.restorationId" style="width: 100%; min-width: 0" />
      </NFormItem>
      <NFormItem label="部位" style="flex: 0 0 270px; max-width: 100%; min-width: 0">
        <NInput v-model:value="queryParams.location" clearable style="width: 100%; min-width: 0" />
      </NFormItem>
    </template>

    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <HPageForm
      v-if="formShow" :key="formKey"
      v-model:show="formShow"
      :title="title"
      :model="editVo"
      :rules="mode === 'delete' ? {} : rules"
      :auto-reload="false" :auto-close="false"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="110"
      width="640px"
    >
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NAlert v-if="loading" type="info">正在读取当前原馆与病害依据…</NAlert>
      <NAlert v-if="mode === 'delete'" type="warning">确认删除病害 {{ editVo.id }}，原项目 {{ editVo.restorationId }}。原件历史继续保留。</NAlert>
      <template v-else>
      <NFormItem label="修复项目" path="restorationId">
        <RestorationProjectPicker
          v-model:value="editVo.restorationId"
          :disabled="mode !== 'add'"
          style="width: 360px"
        />
      </NFormItem>
      <NFormItem label="病害类型" path="damageType">
        <HDictSelect v-model:value="editVo.damageType" type="restoration_damage_type" clearable style="width: 220px" />
      </NFormItem>
      <NFormItem label="严重程度" path="severity">
        <HDictSelect v-model:value="editVo.severity" type="restoration_damage_severity" clearable style="width: 220px" />
      </NFormItem>
      <NFormItem label="部位" path="location">
        <NInput v-model:value="editVo.location" placeholder="口沿 / 圈足 / 中腹部…" />
      </NFormItem>
      <NFormItem label="描述" path="description">
        <NInput
          v-model:value="editVo.description"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 12 }"
          placeholder="详细描述病害形态、成因推测、是否稳定等"
        />
      </NFormItem>
      <NFormItem label="图示" path="imageFileIds">
        <HUploadInput
          :value="editVo.imageFileIds" :disabled="!handleSubmit"
          @update:value="updateImageFiles" @upload-finish="uploadFinished"
          accept="image/*"
          placeholder="病害图示 fileId（多张请分次上传后手工拼接逗号分隔）"
          clearable
        >
          上传图示
        </HUploadInput>
      </NFormItem>
      <NFormItem label="排序" path="sort">
        <NInputNumber v-model:value="editVo.sort" :min="0" />
      </NFormItem>
      </template>
    </HPageForm>

    <HPageView v-model:show="viewShow" title="病害记录详情" width="640px">
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="病害类型"><HDictText type="restoration_damage_type" :value="viewRow.damageType" /></NDescriptionsItem>
        <NDescriptionsItem label="严重度"><HDictText type="restoration_damage_severity" :value="viewRow.severity" /></NDescriptionsItem>
        <NDescriptionsItem label="部位">{{ viewRow.location || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="藏品">{{ `${viewRow.collectionTotalRegNo || ''} ${viewRow.collectionName || ''}`.trim() || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="所在档案">{{ viewRow.restorationArchiveNo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="描述">{{ viewRow.description || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="图示"><HFileList :value="viewRow.imageFileIds" :revision="viewRow.id" /></NDescriptionsItem>
        <NDescriptionsItem label="排序">{{ viewRow.sort ?? '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="创建时间">{{ formatDateTime(viewRow.createTime) }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { computed, h, reactive, ref, resolveComponent, resolveDirective, withDirectives } from 'vue';
import { NAlert, NButton, NDescriptions, NDescriptionsItem, NFormItem, NInput, NInputNumber } from 'naive-ui';
import { useArchiveRecordWindow } from '../composables/useArchiveRecordWindow';
import { restorationArchiveApi } from '../api/archive';
import RestorationProjectPicker from '../components/RestorationProjectPicker.vue';
import type { RestorationDamageVo } from '../typings';
import { formatDateTime } from '@/utils/datetime';
import { RESTORATION_ARCHIVE_WRITE_CODES, RESTORATION_ARCHIVE_WRITE_PERMISSION } from '../utils/restorationPermissions';
import { useAuthStore } from '@/store';

const HDictText = resolveComponent('HDictText');

const tableRef = ref<any>(null);

const queryParams = reactive({
  keyword: null as string | null,
  damageType: null as string | null,
  severity: null as string | null,
  restorationId: null as string | null,
  location: null as string | null,
});

const {formShow,viewShow,loading,error,title,editVo,viewRow,mode,formKey,handleSubmit,updateImageFiles,uploadFinished,onAdd,onEdit,onDelete,onView}=useArchiveRecordWindow<RestorationDamageVo>('damage',()=>tableRef.value?.handleRefresh());
const permission=resolveDirective('permission');
const auth=useAuthStore();
// 病害记录写接口（RestorationArchiveController.saveDamage / deleteDamage）与档案同一组码；未传 permission 时表格放行，另用 is-add / is-edit 开关。
const canWriteDamage=computed(()=>auth.hasPermission(RESTORATION_ARCHIVE_WRITE_CODES));

const rules = {
  restorationId: { required: true, message: '请选择修复项目', trigger: ['change', 'blur'] },
  damageType: { required: true, message: '请选择病害类型', trigger: ['change', 'blur'] },
  severity: { required: true, message: '请选择严重程度', trigger: ['change', 'blur'] },
};

// 无写权限时整列不渲染。
const deleteColumn={title:'删除',key:'damageDelete',width:70,render:(r:RestorationDamageVo)=>{const button=h(NButton,{size:'small',type:'error',text:true,onClick:()=>onDelete(r)},()=> '删除');return permission?withDirectives(button,[[permission,[...RESTORATION_ARCHIVE_WRITE_CODES]]]):button;}};
const columns = computed<any[]>(() => [
  ...(canWriteDamage.value ? [deleteColumn] : []),
  {
    title: '病害类型', key: 'damageType', width: 130, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationDamageVo) =>
      h(
        'a',
        { class: 'data-table-link', style: { cursor: 'pointer' }, onClick: () => onView(r) },
        [h(HDictText, { type: 'restoration_damage_type', value: r.damageType })],
      ),
  },
  {
    title: '严重度', key: 'severity', width: 100, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationDamageVo) => h(HDictText, { type: 'restoration_damage_severity', value: r.severity }),
  },
  { title: '部位', key: 'location', width: 160, ellipsis: { tooltip: true }, resizable: true },
  {
    title: '藏品', key: 'collection', width: 240, resizable: true, ellipsis: { tooltip: true },
    render: (r: RestorationDamageVo) => `${r.collectionTotalRegNo || ''} ${r.collectionName || ''}`.trim() || '—',
  },
  { title: '所在档案', key: 'restorationArchiveNo', width: 180, ellipsis: { tooltip: true }, resizable: true },
  { title: '描述', key: 'description', width: 240, resizable: true, ellipsis: { tooltip: true } },
  { title: '排序', key: 'sort', width: 60 },
  { title: '创建时间', key: 'createTime', width: 190, ellipsis: { tooltip: true }, resizable: true, render: (r: RestorationDamageVo) => formatDateTime(r.createTime) },
]);

</script>

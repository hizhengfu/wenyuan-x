<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :scroll-x="1460"
    :fetch-data="restorationProcessApi.page"
    :is-delete="false"
    :is-add="canWriteProcess"
    :is-edit="canWriteProcess"
    :add-permission="RESTORATION_PROCESS_WRITE_PERMISSION"
    :edit-permission="RESTORATION_PROCESS_WRITE_PERMISSION"
    :params="queryParams"
    row-key-field="id"
    @add="onAdd(queryParams.restorationId)"
    @row-edit="onEdit"
    @row-view="onView"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="RESTORATION_PROCESS_ORIGINAL" /></template>
    <template #queryForm>
      <NFormItem label="关键字" style="flex: 0 0 300px; max-width: 100%; min-width: 0">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="工序名 / 描述" style="width: 100%; min-width: 0" />
      </NFormItem>
      <NFormItem label="修复项目" style="flex: 0 0 350px; max-width: 100%; min-width: 0">
        <RestorationProjectPicker v-model:value="queryParams.restorationId" style="width: 100%; min-width: 0" />
      </NFormItem>
      <NFormItem label="工序名称" style="flex: 0 0 270px; max-width: 100%; min-width: 0">
        <NInput v-model:value="queryParams.stageName" clearable style="width: 100%; min-width: 0" />
      </NFormItem>
    </template>

    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <HPageForm
      v-if="formShow"
      :key="formKey"
      v-model:show="formShow"
      :title="title"
      :model="editVo"
      :rules="rules"
      :auto-reload="false"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="110"
      width="720px"
    >
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NAlert v-if="loading" type="info">正在核对原馆、工序及实施依据…</NAlert>
      <NAlert v-if="mode === 'delete'" type="warning">确认删除工序 {{ editVo.stageName }}（{{ editVo.id }}），所属项目 {{ editVo.restorationId }}。原件读取身份继续保留。</NAlert>
      <template v-else>
      <NAlert v-if="!editVo.id" type="info" :show-icon="false" style="margin-bottom: 12px">
        请据实登记已发生的工序。工序号决定显示顺序，材料汇总在项目详情和档案中查看。
      </NAlert>
      <NFormItem label="修复项目" path="restorationId">
        <RestorationProjectPicker
          v-model:value="editVo.restorationId"
          :disabled="mode !== 'add'"
          style="width: 360px"
        />
      </NFormItem>
      <NFormItem label="工序号" path="sort">
        <NInputNumber v-model:value="editVo.sort" :min="0" />
      </NFormItem>
      <NFormItem label="工序名称" path="stageName">
        <NInput v-model:value="editVo.stageName" placeholder="清洗 / 软化 / 加固 / 补配 / 做旧 …" />
      </NFormItem>
      <NFormItem label="修复方法" path="methodId">
        <RestorationMethodPicker :org-id="editVo.orgId" :disabled="!editVo.orgId || !handleSubmit" v-model:value="editVo.methodId" style="width: 360px" />
      </NFormItem>
      <NFormItem label="主操作人" path="operatorId">
        <RestorerPicker :org-id="editVo.orgId" :disabled="!editVo.orgId || !handleSubmit" v-model:value="editVo.operatorId" style="width: 360px" />
      </NFormItem>
      <NFormItem label="协助人员" path="assistantIds">
        <RestorerPicker :org-id="editVo.orgId" :disabled="!editVo.orgId || !handleSubmit" v-model:value="assistantIdList" multiple style="width: 520px" />
      </NFormItem>
      <NFormItem label="用料（JSON）" path="materials">
        <NInput
          v-model:value="editVo.materials"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 6 }"
          placeholder='[{"name":"棉球","qty":10,"unit":"包"}]'
        />
      </NFormItem>
      <NFormItem label="工具" path="tools">
        <NInput v-model:value="editVo.tools" />
      </NFormItem>
      <NFormItem label="开始时间" path="startTime">
        <NDatePicker v-model:value="editVo.startTime" type="datetime" clearable />
      </NFormItem>
      <NFormItem label="结束时间" path="endTime">
        <NDatePicker v-model:value="editVo.endTime" type="datetime" clearable />
      </NFormItem>
      <NFormItem label="用时（小时）" path="duration">
        <NInputNumber v-model:value="editVo.duration" :precision="1" />
      </NFormItem>
      <NFormItem label="操作要点" path="description">
        <NInput
          v-model:value="editVo.description"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 10 }"
          placeholder="记录本工序的操作要点、临时决策、注意事项等"
        />
      </NFormItem>
      <NFormItem label="过程影像" path="imageFileIds">
        <HUploadInput
          :value="editVo.imageFileIds"
          :disabled="!handleSubmit"
          @update:value="updateImageFiles"
          @upload-finish="uploadFinished"
          accept="image/*"
          placeholder="过程影像 fileId（多张分次上传后逗号拼接）"
          clearable
        >
          上传影像
        </HUploadInput>
      </NFormItem>
      </template>
    </HPageForm>

    <HPageView v-model:show="viewShow" title="工序详情" width="720px">
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NAlert v-if="loading" type="info">正在读取工序…</NAlert>
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="工序号">{{ viewRow.sort ?? '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="工序名称">{{ viewRow.stageName || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="藏品">{{ `${viewRow.collectionTotalRegNo || ''} ${viewRow.collectionName || ''}`.trim() || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="方法">{{ viewRow.methodName || viewRow.methodId || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="主操作">{{ viewRow.operatorName || (viewRow.operatorId ? UNKNOWN_PERSON : '--') }}</NDescriptionsItem>
        <NDescriptionsItem label="协助人员">{{ viewRow.assistantNames || viewRow.assistantIds || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="用料">{{ viewRow.materials || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="工具">{{ viewRow.tools || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="开始时间">{{ formatDateTime(viewRow.startTime) }}</NDescriptionsItem>
        <NDescriptionsItem label="结束时间">{{ formatDateTime(viewRow.endTime) }}</NDescriptionsItem>
        <NDescriptionsItem label="用时(小时)">{{ formatDuration(viewRow.duration) }}</NDescriptionsItem>
        <NDescriptionsItem label="操作要点">{{ viewRow.description || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="过程影像"><HFileList :value="viewRow.imageFileIds" :revision="viewRow.id" /></NDescriptionsItem>
      </NDescriptions>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { computed, reactive, ref, h, withDirectives, resolveDirective } from 'vue';
import { useProcessWindow } from '../composables/useProcessWindow';
import { NButton, NAlert, NDatePicker, NDescriptions, NDescriptionsItem, NFormItem, NInput, NInputNumber } from 'naive-ui';
import { restorationProcessApi } from '../api/process';
import RestorationProjectPicker from '../components/RestorationProjectPicker.vue';
import RestorerPicker from '../components/RestorerPicker.vue';
import RestorationMethodPicker from '../components/RestorationMethodPicker.vue';
import type { RestorationProcessVo } from '../typings';
import { formatDateTime } from '@/utils/datetime';
import { UNKNOWN_PERSON } from '../utils/restorationReport';
import { RESTORATION_PROCESS_WRITE_CODES, RESTORATION_PROCESS_WRITE_PERMISSION } from '../utils/restorationPermissions';
import { useAuthStore } from '@/store';

const tableRef = ref<any>(null);
const {formShow,viewShow,loading,error,title,editVo,viewRow,mode,formKey,handleSubmit,updateImageFiles,uploadFinished,onAdd,onEdit,onDelete,onView}=useProcessWindow(()=>tableRef.value?.handleRefresh());
const permission=resolveDirective('permission');
const auth=useAuthStore();
// HPageDataTable 在未传 permission 编码时 judgePermissionByCode 恒为 true，单传 add/edit-permission 拦不住，
// 所以同时用 is-add / is-edit 按同一组码开关表格自带的「新增 / 编辑」。
const canWriteProcess=computed(()=>auth.hasPermission(RESTORATION_PROCESS_WRITE_CODES));
const assistantIdList = computed<string[]>({
  get: () => (editVo.value.assistantIds || '').split(',').map((id) => id.trim()).filter(Boolean),
  set: (ids) => { editVo.value.assistantIds = ids.length ? ids.join(',') : undefined; },
});

const queryParams = reactive({
  keyword: null as string | null,
  restorationId: null as string | null,
  stageName: null as string | null,
});

const rules = {
  restorationId: { required: true, message: '请选择修复项目', trigger: ['change', 'blur'] },
  stageName: { required: true, message: '请输入工序名称', trigger: ['blur', 'input'] },
};

function formatDuration(value?: number | null): string {
  return value == null || !Number.isFinite(value) ? '--' : value.toFixed(2);
}

// 只读账号不渲染「删除」列（整列不出现，避免表头剩一个空的「删除」列）；有权限时按钮仍挂 v-permission 兜底。
const deleteColumn = {title: '删除',key: 'processDelete',width: 70,render:(row:RestorationProcessVo)=>{const button=h(NButton,{size:'small',type:'error',text:true,onClick:()=>onDelete(row)},{default:()=>'删除'});return permission?withDirectives(button,[[permission,[...RESTORATION_PROCESS_WRITE_CODES]]]):button;}};
const columns = computed<any[]>(() => [
  ...(canWriteProcess.value ? [deleteColumn] : []),
  { title: '序号', key: 'sort', width: 60 },
  { title: '工序名称', key: 'stageName', width: 180, ellipsis: { tooltip: true }, resizable: true, columnType: 'title' },
  {
    title: '藏品', key: 'collection', width: 240, resizable: true, ellipsis: { tooltip: true },
    render: (r: RestorationProcessVo) => `${r.collectionTotalRegNo || ''} ${r.collectionName || ''}`.trim() || '—',
  },
  { title: '方法', key: 'methodName', width: 160, ellipsis: { tooltip: true }, resizable: true },
  { title: '主操作', key: 'operatorName', width: 120, ellipsis: { tooltip: true }, resizable: true },
  { title: '用时(h)', key: 'duration', width: 90, align: 'right', render: (r: RestorationProcessVo) => formatDuration(r.duration) },
  {
    title: '起止', key: 'time',
    render: (r: RestorationProcessVo) => `${formatDateTime(r.startTime)} ~ ${formatDateTime(r.endTime)}`,
    width: 360, ellipsis: { tooltip: true }, resizable: true,
  },
  { title: '描述', key: 'description', width: 240, resizable: true, ellipsis: { tooltip: true } },
]);

</script>

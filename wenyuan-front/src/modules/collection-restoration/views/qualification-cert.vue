<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="qualificationCertApi.page"
    :is-delete="false"
    :is-add="canWriteCert"
    :is-edit="canWriteCert"
    :add-permission="QUALIFICATION_CERT_WRITE_PERMISSION"
    :edit-permission="QUALIFICATION_CERT_WRITE_PERMISSION"
    :params="queryParams"
    row-key-field="id"
    @add="onAdd"
    @row-edit="onEdit"
    @row-view="onView"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="RESTORATION_QUALIFICATION_ORIGINAL" /></template>
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="证书号 / 发证机关" style="width: 220px" />
      </NFormItem>
      <NFormItem label="持有方类型">
        <HDictSelect v-model:value="queryParams.holderType" type="restoration_cert_holder_type" clearable style="width: 120px" />
      </NFormItem>
      <NFormItem label="启用">
        <HDictSelect v-model:value="queryParams.enabled" type="col_presence" clearable style="width: 100px" />
      </NFormItem>
    </template>

    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <HPageForm
      v-if="formShow"
      :key="formKey"
      v-model:show="formShow"
      :title="title"
      :model="editVo"
      :rules="mode === 'delete' ? {} : rules"
      :auto-reload="false"
      :auto-close="false"
      :disabled="loading || submitting || !!error || holderLoading || !!holderError"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="110"
      width="640px"
    >
      <NAlert v-if="loading || holderLoading" type="info">正在核对当前证书和持有方…</NAlert>
      <NAlert v-if="error || holderError" type="error">{{ error || holderError }}</NAlert>
      <NAlert v-if="!loading && !error && mode === 'delete'" type="warning">
        确认删除证书 {{ editVo.certNo }}（{{ editVo.id }}）。已被机构或合同引用的证书不能删除；停用请从编辑窗口核对后办理。
      </NAlert>
      <template v-if="mode === 'add' || mode === 'edit'">
      <NFormItem label="持有方类型" path="holderType">
        <HDictSelect
          :value="editVo.holderType"
          :disabled="!handleSubmit || submitting"
          type="restoration_cert_holder_type"
          clearable
          style="width: 220px"
          @update:value="changeHolderType"
        />
      </NFormItem>
      <NFormItem
        v-if="editVo.holderType === 'PERSON'"
        label="持有方（人员）"
        path="holderId"
      >
        <RestorerPicker :only-enabled="false" :org-id="editVo.orgId" :key="`person-${formKey}`" :value="editVo.holderId" :disabled="!handleSubmit || submitting" @update:value="changeHolder" style="width: 360px" />
      </NFormItem>
      <NFormItem
        v-else-if="editVo.holderType === 'ORG'"
        label="持有方（机构）"
        path="holderId"
      >
        <RestorationOrgPicker :only-enabled="false" :org-id="editVo.orgId" :key="`institution-${formKey}`" :value="editVo.holderId" :disabled="!handleSubmit || submitting" @update:value="changeHolder" style="width: 360px" />
      </NFormItem>
      <NFormItem v-else label="持有方" feedback="请先选择持有方类型">
        <NSelect disabled placeholder="请选择持有方" style="width: 360px" />
      </NFormItem>
      <NText v-if="holder" depth="3">已核对持有方：{{ holder.holderName }}（{{ holder.holderId }}）</NText>
      <NFormItem label="证书号" path="certNo">
        <NInput v-model:value="editVo.certNo" />
      </NFormItem>
      <NFormItem label="资质/技能等级" path="certLevel">
        <NSelect v-model:value="editVo.certLevel" :options="levelOptions" :disabled="!editVo.holderType" clearable />
      </NFormItem>
      <NAlert v-if="editVo.holderType === 'ORG'" type="info">可移动文物修复单位资质不分等级，须核对证书原件和核定业务范围。旧等级可保留或停用；用于新业务前须据当前原件核对订正。</NAlert>
      <NFormItem label="业务范围" path="scope">
        <NInput v-model:value="editVo.scope" placeholder="金属/陶瓷/纸张…" />
      </NFormItem>
      <NFormItem label="发证机关" path="issuer">
        <NInput v-model:value="editVo.issuer" />
      </NFormItem>
      <NFormItem label="发证日期" path="issueDate">
        <NDatePicker v-model:value="editVo.issueDate" type="date" clearable />
      </NFormItem>
      <NFormItem label="到期日期" path="expireDate">
        <NDatePicker v-model:value="editVo.expireDate" type="date" clearable />
      </NFormItem>
      <NFormItem label="证书原件" path="attachmentFileId">
        <HUploadInput :key="`attachment-${formKey}-${holderKey}`" :value="editVo.attachmentFileId" :disabled="!canUpload"
          @update:value="updateAttachment" @upload-finish="uploadFinished" accept=".pdf,.jpg,.jpeg,.png" clearable>上传证书原件</HUploadInput>
      </NFormItem>
      <NFormItem label="启用" path="enabled">
        <NSwitch v-model:value="editVo.enabled" />
      </NFormItem>
      <NFormItem label="备注" path="remarks">
        <NInput v-model:value="editVo.remarks" type="textarea" />
      </NFormItem>
      </template>
    </HPageForm>

    <HPageView v-if="viewShow" v-model:show="viewShow" :title="title" width="640px">
      <NAlert v-if="loading" type="info">正在读取当前证书…</NAlert>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="证书号">{{ viewRow.certNo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="持有方类型"><HDictText type="restoration_cert_holder_type" :value="viewRow.holderType" /></NDescriptionsItem>
        <NDescriptionsItem label="持有方">{{ viewRow.holderName || viewRow.holderId || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="资质/技能等级">{{ qualificationLevelLabel(viewRow.certLevel) }}</NDescriptionsItem>
        <NDescriptionsItem label="业务范围">{{ viewRow.scope || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="发证机关">{{ viewRow.issuer || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="发证日期">{{ formatDateValue(viewRow.issueDate) }}</NDescriptionsItem>
        <NDescriptionsItem label="到期日期">{{ formatDateValue(viewRow.expireDate) }}</NDescriptionsItem>
        <NDescriptionsItem label="证书原件"><HFileList :value="viewRow.attachmentFileId" :revision="viewRow.id" /></NDescriptionsItem>
        <NDescriptionsItem label="启用">{{ viewRow.enabled === false ? '停用' : '启用' }}</NDescriptionsItem>
        <NDescriptionsItem label="备注">{{ viewRow.remarks || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { computed, h, reactive, ref, resolveComponent, resolveDirective, withDirectives } from 'vue';
import { NAlert, NButton, NDatePicker, NDescriptions, NDescriptionsItem, NFormItem, NInput, NSelect, NSwitch, NText } from 'naive-ui';
import { useQualificationWindow } from '../composables/useQualificationWindow';
import { qualificationCertApi } from '../api/qualificationCert';
import RestorationOrgPicker from '../components/RestorationOrgPicker.vue';
import RestorerPicker from '../components/RestorerPicker.vue';
import type { QualificationCertVo } from '../typings';
import { formatDateValue } from '@/utils/datetime';
import { QUALIFICATION_CERT_WRITE_CODES, QUALIFICATION_CERT_WRITE_PERMISSION } from '../utils/restorationPermissions';
import { useAuthStore } from '@/store';

import { qualificationLevelLabel, qualificationLevelOptions } from '../utils/qualificationLevels';
const HDictText = resolveComponent('HDictText');

const tableRef = ref<any>(null);
const auth = useAuthStore();
// 新增 / 编辑 / 删除：QualificationCertController.save / delete 同一组码。
// 未传 permission 时表格放行，所以另用 is-add / is-edit 按同一组码开关。
const canWriteCert = computed(() => auth.hasPermission(QUALIFICATION_CERT_WRITE_CODES));

const queryParams = reactive({
  keyword: null as string | null,
  holderType: null as string | null,
  enabled: null as string | null,
});

const { formShow, viewShow, loading, submitting, error, title, vo: editVo, viewRow, mode, formKey, handleSubmit,
  holder, holderLoading, holderError, holderKey, changeHolderType, changeHolder, updateAttachment, uploadFinished, canUpload,
  onAdd, onEdit, onDelete, onView } = useQualificationWindow(() => tableRef.value?.handleRefresh?.());

const levelOptions = computed(() => qualificationLevelOptions(editVo.value.holderType, mode.value === 'edit' ? editVo.value.certLevel : undefined));

const rules = {
  holderType: { required: true, message: '请选择持有方类型', trigger: ['change', 'blur'] },
  holderId: { required: true, message: '请选择持有方', trigger: ['change', 'blur'] },
  certNo: { required: true, message: '请输入证书号', trigger: ['blur', 'input'] },
  certLevel: { required: true, message: '请选择证书等级', trigger: ['change', 'blur'] },
};

// 无写权限时整列不渲染。
const operateColumn = { title: '操作', key: 'operate', type: 'operate', width: 100, align: 'center', fixed: 'right' as const, render: (row: QualificationCertVo) => {
    const button = h(NButton, { size: 'small', type: 'error', onClick: () => onDelete(row) }, { default: () => '删除' });
    const permission = resolveDirective('permission');
    return permission ? withDirectives(button, [[permission, [...QUALIFICATION_CERT_WRITE_CODES]]]) : button;
  } };
const columns = computed<any[]>(() => [
  { title: '证书号', key: 'certNo', columnType: 'title', minWidth: 160, ellipsis: { tooltip: true }, resizable: true, width: 180 },
  { title: '持有方', key: 'holderName', ellipsis: { tooltip: true }, resizable: true, width: 200 },
  {
    title: '类型', key: 'holderType', width: 100, ellipsis: { tooltip: true }, resizable: true,
    render: (r: QualificationCertVo) => h(HDictText, { type: 'restoration_cert_holder_type', value: r.holderType }),
  },
  {
    title: '资质/技能等级', key: 'certLevel', width: 140, ellipsis: { tooltip: true }, resizable: true,
    render: (r: QualificationCertVo) => qualificationLevelLabel(r.certLevel),
  },
  { title: '业务范围', key: 'scope', ellipsis: { tooltip: true }, resizable: true, width: 240 },
  { title: '发证机关', key: 'issuer', ellipsis: { tooltip: true }, resizable: true, width: 200 },
  { title: '到期日', key: 'expireDate', width: 120, ellipsis: { tooltip: true }, resizable: true, render: (r: QualificationCertVo) => formatDateValue(r.expireDate) },
  { title: '启用', key: 'enabled', render: (r: QualificationCertVo) => (r.enabled ? '启用' : '停用'), width: 90, ellipsis: { tooltip: true }, resizable: true },
  ...(canWriteCert.value ? [operateColumn] : []),
]);

</script>

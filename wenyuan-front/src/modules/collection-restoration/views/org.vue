<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="restorationOrgApi.page"
    :is-delete="false"
    :is-add="canWriteOrg"
    :is-edit="canWriteOrg"
    :add-permission="RESTORATION_ORG_WRITE_PERMISSION"
    :edit-permission="RESTORATION_ORG_WRITE_PERMISSION"
    :params="queryParams"
    row-key-field="id"
    @add="onAdd"
    @row-edit="onEdit"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="名称 / 编号 / 联系人" style="width: 220px" />
      </NFormItem>
      <NFormItem label="资质登记">
        <NSelect
          v-model:value="queryParams.qualificationLevel"
          :options="qualOptions"
          clearable
          placeholder="全部"
          style="width: 140px"
        />
      </NFormItem>
      <NFormItem label="外聘">
        <HDictSelect v-model:value="queryParams.external" type="col_presence" clearable style="width: 100px" />
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
      :rules="mode === 'delete' || mode === 'disable' ? {} : rules"
      :auto-reload="false"
      :auto-close="false"
      :disabled="loading || !!error"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="120"
      width="720px"
    >
      <NAlert v-if="loading" type="info">正在读取当前机构和资质资料…</NAlert>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NAlert v-if="!loading && !error && (mode === 'delete' || mode === 'disable')" type="warning">
        确认{{ mode === 'disable' ? '停用' : '删除' }}机构 {{ editVo.name }}（{{ editVo.id }}）。已被项目、合同或证书引用的机构应停用保留，不能删除。
      </NAlert>
      <template v-if="mode === 'add' || mode === 'edit'">
      <NFormItem label="机构名称" path="name">
        <NInput v-model:value="editVo.name" style="width: 360px" />
      </NFormItem>
      <NFormItem label="内部编号" path="code">
        <NInput v-model:value="editVo.code" style="width: 220px" />
      </NFormItem>
      <NFormItem label="资质登记" path="qualificationLevel">
        <NSelect v-model:value="editVo.qualificationLevel" :options="qualEditOptions" clearable style="width: 220px" placeholder="核对当前单位资质" />
      </NFormItem>
      <NFormItem
        label="有效资质证书"
        path="qualificationCertId"
        :feedback="!editVo.id ? '请先保存机构，再到证书库新增并关联证书' : undefined"
      >
        <QualificationCertPicker
          :value="editVo.qualificationCertId"
          :disabled="!editVo.id || loading || !!error"
          @update:value="updateCertificate"
          holder-type="ORG"
          :holder-id="editVo.id"
          style="width: 420px"
        />
      </NFormItem>
      <NFormItem label="业务范围" path="scope">
        <NInput v-model:value="editVo.scope" placeholder="金属/陶瓷/纸张…逗号分隔" />
      </NFormItem>
      <NFormItem label="地址" path="address">
        <NInput v-model:value="editVo.address" type="textarea" :autosize="{ minRows: 1, maxRows: 3 }" />
      </NFormItem>
      <NFormItem label="联系人" path="contactName">
        <NInput v-model:value="editVo.contactName" style="width: 220px" />
      </NFormItem>
      <NFormItem label="联系电话" path="contactPhone">
        <NInput v-model:value="editVo.contactPhone" style="width: 220px" />
      </NFormItem>
      <NFormItem label="邮箱" path="contactEmail">
        <NInput v-model:value="editVo.contactEmail" style="width: 320px" />
      </NFormItem>
      <NFormItem label="法人代表" path="legalPerson">
        <NInput v-model:value="editVo.legalPerson" style="width: 220px" />
      </NFormItem>
      <NFormItem label="税号" path="taxNo">
        <NInput v-model:value="editVo.taxNo" style="width: 320px" />
      </NFormItem>
      <NFormItem label="银行账户" path="bankAccount">
        <NInput v-model:value="editVo.bankAccount" style="width: 360px" />
      </NFormItem>
      <NFormItem label="外聘" path="external">
        <NSwitch v-model:value="editVo.external" />
      </NFormItem>
      <NFormItem label="启用" path="enabled">
        <NSwitch v-model:value="editVo.enabled" />
      </NFormItem>
      <NFormItem label="备注" path="remarks">
        <NInput v-model:value="editVo.remarks" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" />
      </NFormItem>
      </template>
    </HPageForm>

    <HPageView v-if="viewShow" v-model:show="viewShow" :title="title" width="720px">
      <NAlert v-if="loading" type="info">正在读取当前机构和资质资料…</NAlert>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="机构名称">{{ viewRow.name || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="内部编号">{{ viewRow.code || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="资质登记">{{ qualificationLevelLabel(viewRow.qualificationLevel) }}</NDescriptionsItem>
        <NDescriptionsItem label="资质证书号">{{ viewRow.qualificationCertNo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="证书到期日">{{ formatDateValue(viewRow.qualificationExpireDate) }}</NDescriptionsItem>
        <NDescriptionsItem label="业务范围">{{ viewRow.scope || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="地址">{{ viewRow.address || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="联系人">{{ viewRow.contactName || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="联系电话">{{ viewRow.contactPhone || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="邮箱">{{ viewRow.contactEmail || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="法人代表">{{ viewRow.legalPerson || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="税号">{{ viewRow.taxNo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="银行账户">{{ viewRow.bankAccount || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="外聘">{{ viewRow.external === false ? '本馆' : '外聘' }}</NDescriptionsItem>
        <NDescriptionsItem label="启用">{{ viewRow.enabled === false ? '停用' : '启用' }}</NDescriptionsItem>
        <NDescriptionsItem label="备注">{{ viewRow.remarks || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { qualificationLevelLabel, qualificationLevelOptions, unitQualificationOptions } from '../utils/qualificationLevels';
import { computed, h, reactive, ref, resolveDirective, withDirectives } from 'vue';
import { NAlert, NButton, NDescriptions, NDescriptionsItem, NFormItem, NInput, NSelect, NSwitch, NTag } from 'naive-ui';
import { useMasterDataWindow } from '../composables/useMasterDataWindow';
import { restorationOrgApi } from '../api/org';
import type { RestorationOrgVo } from '../typings';
import QualificationCertPicker from '../components/QualificationCertPicker.vue';
import { formatDateValue } from '@/utils/datetime';
import { RESTORATION_ORG_WRITE_CODES, RESTORATION_ORG_WRITE_PERMISSION } from '../utils/restorationPermissions';
import { useAuthStore } from '@/store';

const tableRef = ref<any>(null);
const auth = useAuthStore();
// 新增 / 编辑 / 停用 / 删除：RestorationOrgController.save / disable / delete 同一组码。
// 未传 permission 时表格放行，所以另用 is-add / is-edit 按同一组码开关。
const canWriteOrg = computed(() => auth.hasPermission(RESTORATION_ORG_WRITE_CODES));

const qualOptions = [...unitQualificationOptions, ...['FIRST', 'SECOND', 'THIRD'].map(value => ({ value, label: qualificationLevelLabel(value) }))];

const queryParams = reactive({
  keyword: null as string | null,
  qualificationLevel: null as string | null,
  external: null as string | null,
  enabled: null as string | null,
});

const { formShow, viewShow, loading, error, title, vo: editVo, viewRow, mode, formKey, handleSubmit, updateField, onAdd, onEdit, onDelete, onDisable, onView } = useMasterDataWindow<RestorationOrgVo>({
  label: '修复机构', empty: { external: true, enabled: true }, context: restorationOrgApi.context,
  save: restorationOrgApi.save, remove: restorationOrgApi.delete, disable: restorationOrgApi.disable,
  refresh: () => tableRef.value?.handleRefresh?.(),
});
const qualEditOptions = computed(() => qualificationLevelOptions('ORG', mode.value === 'edit' ? editVo.value.qualificationLevel : undefined));
const updateCertificate = computed(() => {
  const owned = updateField.value;
  return (value: unknown) => owned?.('qualificationCertId', typeof value === 'string' ? value : undefined);
});

const rules = {
  name: { required: true, message: '请输入机构名称', trigger: ['blur', 'input'] },
};

// 无写权限时整列不渲染。
const operateColumn = { title: '操作', key: 'operate', type: 'operate', width: 160, align: 'center', fixed: 'right' as const, render: (row: RestorationOrgVo) => {
    const buttons = h('div', { class: 'flex gap-2' }, [
      h(NButton, { size: 'small', disabled: row.enabled === false, onClick: () => onDisable(row) }, { default: () => '停用' }),
      h(NButton, { size: 'small', type: 'error', onClick: () => onDelete(row) }, { default: () => '删除' }),
    ]);
    const permission = resolveDirective('permission');
    return permission ? withDirectives(buttons, [[permission, [...RESTORATION_ORG_WRITE_CODES]]]) : buttons;
  } };
const columns = computed<any[]>(() => [
  { title: '机构名称', key: 'name', columnType: 'title', minWidth: 180, ellipsis: { tooltip: true }, resizable: true, width: 180 },
  { title: '编号', key: 'code', width: 180, ellipsis: { tooltip: true }, resizable: true },
  {
    title: '资质登记', key: 'qualificationLevel', width: 140, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationOrgVo) => r.qualificationLevel
      ? h(
        NTag,
        { size: 'small', bordered: false, type: r.qualificationLevel === 'FIRST' ? 'success' : r.qualificationLevel === 'SECOND' ? 'warning' : 'default' },
        { default: () => qualificationLevelLabel(r.qualificationLevel) },
      )
      : '—',
  },
  { title: '证书号', key: 'qualificationCertNo', width: 180, ellipsis: { tooltip: true }, resizable: true },
  { title: '到期日', key: 'qualificationExpireDate', width: 120, ellipsis: { tooltip: true }, resizable: true, render: (r: RestorationOrgVo) => formatDateValue(r.qualificationExpireDate) },
  { title: '业务范围', key: 'scope', resizable: true, width: 240, ellipsis: { tooltip: true } },
  { title: '联系人', key: 'contactName', width: 120, ellipsis: { tooltip: true }, resizable: true },
  { title: '电话', key: 'contactPhone', width: 150, ellipsis: { tooltip: true }, resizable: true },
  {
    title: '外聘', key: 'external', width: 90, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationOrgVo) => (r.external === false ? '本馆' : '外聘'),
  },
  { title: '启用', key: 'enabled', width: 90, ellipsis: { tooltip: true }, resizable: true, render: (r: RestorationOrgVo) => (r.enabled === false ? '停用' : '启用') },
  ...(canWriteOrg.value ? [operateColumn] : []),
]);

</script>

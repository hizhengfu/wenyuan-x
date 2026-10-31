<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="restorerApi.page"
    :is-delete="false"
    :is-add="canWriteRestorer"
    :is-edit="canWriteRestorer"
    :add-permission="RESTORER_WRITE_PERMISSION"
    :edit-permission="RESTORER_WRITE_PERMISSION"
    :params="queryParams"
    row-key-field="id"
    @add="onAdd"
    @row-edit="onEdit"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="姓名 / 证件号 / 电话" style="width: 220px" />
      </NFormItem>
      <NFormItem label="擅长领域">
        <NInput v-model:value="queryParams.speciality" clearable placeholder="金属/陶瓷/纸张…" style="width: 160px" />
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
      label-width="100"
      width="640px"
    >
      <NAlert v-if="loading" type="info">正在读取当前人员和资质资料…</NAlert>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NAlert v-if="!loading && !error && (mode === 'delete' || mode === 'disable')" type="warning">
        确认{{ mode === 'disable' ? '停用' : '删除' }}人员 {{ editVo.name }}（{{ editVo.id }}）。已被证书或工序引用的人员应停用保留，不能删除。
      </NAlert>
      <template v-if="mode === 'add' || mode === 'edit'">
      <NFormItem label="姓名" path="name">
        <NInput v-model:value="editVo.name" />
      </NFormItem>
      <NFormItem label="性别" path="sex">
        <HDictSelect v-model:value="editVo.sex" type="sex_type" clearable style="width: 200px" />
      </NFormItem>
      <NFormItem label="出生日期" path="birthDate">
        <NDatePicker
          v-model:value="editVo.birthDate"
          type="date"
          clearable
          style="width: 220px"
        />
      </NFormItem>
      <NFormItem label="技能等级" path="title">
        <HDictSelect v-model:value="editVo.title" type="restoration_restorer_title" clearable style="width: 220px" />
      </NFormItem>
      <NFormItem label="证件号" path="certNo">
        <NInput v-model:value="editVo.certNo" placeholder="身份证 / 工号" />
      </NFormItem>
      <NFormItem label="擅长领域" path="speciality">
        <HDictSelect
          v-model:value="editVo.speciality"
          type="restoration_method_category"
          multiple
          clearable
          placeholder="金属 / 陶瓷 / 纸张 / 漆木 / 纺织品 / 石质 / 综合 …"
        />
      </NFormItem>
      <NFormItem label="从业年限" path="specialityYears">
        <NInputNumber v-model:value="editVo.specialityYears" :min="0" />
      </NFormItem>
      <NFormItem label="电话" path="telephone">
        <NInput v-model:value="editVo.telephone" />
      </NFormItem>
      <NFormItem label="邮箱" path="email">
        <NInput v-model:value="editVo.email" />
      </NFormItem>
      <NFormItem label="外聘" path="external">
        <NSwitch v-model:value="editVo.external" />
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
      <NAlert v-if="loading" type="info">正在读取当前人员和资质资料…</NAlert>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="姓名">{{ viewRow.name || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="性别"><HDictText type="sex_type" :value="viewRow.sex" /></NDescriptionsItem>
        <NDescriptionsItem label="出生日期">{{ formatDateValue(viewRow.birthDate) }}</NDescriptionsItem>
        <NDescriptionsItem label="技能等级"><HDictText type="restoration_restorer_title" :value="viewRow.title" /></NDescriptionsItem>
        <NDescriptionsItem label="证件号">{{ viewRow.certNo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="擅长领域">
          <template v-if="viewRow.speciality">
            <HDictText
              v-for="v in viewRow.speciality.split(',').map((s) => s.trim()).filter(Boolean)"
              :key="v"
              type="restoration_method_category"
              :value="v"
              style="margin-right: 8px"
            />
          </template>
          <template v-else>--</template>
        </NDescriptionsItem>
        <NDescriptionsItem label="从业年限">{{ viewRow.specialityYears ?? '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="电话">{{ viewRow.telephone || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="邮箱">{{ viewRow.email || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="外聘">{{ viewRow.external ? '是' : '否' }}</NDescriptionsItem>
        <NDescriptionsItem label="启用">{{ viewRow.enabled === false ? '停用' : '启用' }}</NDescriptionsItem>
        <NDescriptionsItem label="备注">{{ viewRow.remarks || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref, resolveComponent, resolveDirective, withDirectives } from 'vue';
import { NAlert, NButton, NDatePicker, NDescriptions, NDescriptionsItem, NFormItem, NInput, NInputNumber, NSwitch } from 'naive-ui';
import { useMasterDataWindow } from '../composables/useMasterDataWindow';
import { restorerApi } from '../api/restorer';
import type { RestorerVo } from '../typings';
import { formatDateValue } from '@/utils/datetime';
import { RESTORER_DELETE_CODES, RESTORER_WRITE_CODES, RESTORER_WRITE_PERMISSION } from '../utils/restorationPermissions';
import { useAuthStore } from '@/store';

const HDictText = resolveComponent('HDictText');

const tableRef = ref<any>(null);
const auth = useAuthStore();
// 新增 / 编辑 / 停用：RestorerController.save / disable 同一组码；删除（RestorerController.delete）少 COLLECTION_RESTORER_MANAGE，单独用 RESTORER_DELETE_CODES。
// 未传 permission 时表格放行，所以另用 is-add / is-edit 按同一组码开关。
const canWriteRestorer = computed(() => auth.hasPermission(RESTORER_WRITE_CODES));

const queryParams = reactive({
  keyword: null as string | null,
  speciality: null as string | null,
  external: null as string | null,
  enabled: null as string | null,
});

const { formShow, viewShow, loading, error, title, vo: editVo, viewRow, mode, formKey, handleSubmit, onAdd, onEdit, onDelete, onDisable, onView } = useMasterDataWindow<RestorerVo>({
  label: '修复人员', empty: { external: false, enabled: true, specialityYears: 0 }, context: restorerApi.context,
  save: restorerApi.save, remove: restorerApi.delete, disable: restorerApi.disable,
  refresh: () => tableRef.value?.handleRefresh?.(),
});

const rules = {
  name: { required: true, message: '请输入姓名', trigger: ['blur', 'input'] },
};

// 无写权限时整列不渲染；列内「删除」再按更严的删除码控制。
const operateColumn = { title: '操作', key: 'operate', type: 'operate', width: 160, align: 'center', fixed: 'right' as const, render: (row: RestorerVo) => {
    const permission = resolveDirective('permission');
    const action = (remove: boolean) => {
      const button = h(NButton, { size: 'small', type: remove ? 'error' : 'default', disabled: !remove && row.enabled === false,
        onClick: () => remove ? onDelete(row) : onDisable(row) }, { default: () => remove ? '删除' : '停用' });
      const allowed = remove ? [...RESTORER_DELETE_CODES] : [...RESTORER_WRITE_CODES];
      return permission ? withDirectives(button, [[permission, allowed]]) : button;
    };
    return h('div', { class: 'flex gap-2' }, [action(false), action(true)]);
  } };
const columns = computed<any[]>(() => [
  { title: '姓名', key: 'name', columnType: 'title', minWidth: 100, ellipsis: { tooltip: true }, resizable: true, width: 180 },
  {
    title: '性别', key: 'sex', width: 90, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorerVo) => h(HDictText, { type: 'sex_type', value: r.sex }),
  },
  {
    title: '技能等级', key: 'title', width: 140, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorerVo) => h(HDictText, { type: 'restoration_restorer_title', value: r.title }),
  },
  {
    title: '擅长领域', key: 'speciality', ellipsis: { tooltip: true }, resizable: true, width: 240,
    render: (r: RestorerVo) => {
      const list = r.speciality?.split(',').map((s) => s.trim()).filter(Boolean) || [];
      if (!list.length) return '—';
      return h('span', {}, list.map((v) => h(HDictText, { type: 'restoration_method_category', value: v, style: 'margin-right:6px' })));
    },
  },
  { title: '从业年限', key: 'specialityYears', width: 90 },
  { title: '电话', key: 'telephone', ellipsis: { tooltip: true }, resizable: true, width: 150 },
  { title: '外聘', key: 'external', render: (r: RestorerVo) => (r.external ? '是' : '否'), width: 90, ellipsis: { tooltip: true }, resizable: true },
  { title: '启用', key: 'enabled', render: (r: RestorerVo) => (r.enabled ? '启用' : '停用'), width: 90, ellipsis: { tooltip: true }, resizable: true },,
  ...(canWriteRestorer.value ? [operateColumn] : [])
]);

</script>

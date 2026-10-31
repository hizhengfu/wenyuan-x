<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="restorationMethodApi.page"
    :params="queryParams"
    :is-delete="false"
    :is-add="canWriteMethod"
    :is-edit="canWriteMethod"
    :add-permission="RESTORATION_METHOD_WRITE_PERMISSION"
    :edit-permission="RESTORATION_METHOD_WRITE_PERMISSION"
    row-key-field="id"
    @add="onAdd"
    @rowEdit="onEdit"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="名称 / 类别 / 质地 / 要点" style="width: 230px" />
      </NFormItem>
      <NFormItem label="类别">
        <HDictSelect
          v-model:value="queryParams.category"
          :type="DICT_RESTORATION_METHOD_CATEGORY"
          clearable
          placeholder="方法类别"
          style="width: 160px"
        />
      </NFormItem>
      <NFormItem label="适用质地">
        <HDictSelect v-model:value="queryParams.applicableTexture" type="col_texture" clearable placeholder="质地" style="width: 130px" />
      </NFormItem>
      <NFormItem label="启用">
        <HDictSelect v-model:value="queryParams.enabled" type="col_presence" clearable placeholder="启用状态" style="width: 120px" />
      </NFormItem>
      <NFormItem label="技术要点">
        <HDictSelect v-model:value="queryParams.hasSummary" type="col_presence" clearable placeholder="有无" style="width: 110px" />
      </NFormItem>
    </template>

    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <HPageForm
      v-if="formShow"
      :key="formKey"
      v-model:show="formShow"
      :title="title"
      :model="form"
      :rules="mode === 'delete' ? {} : rules"
      :auto-reload="false"
      :auto-close="false"
      :disabled="loading || !!error"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="95"
      width="720px"
    >
      <NAlert v-if="loading" type="info">正在读取当前方法资料…</NAlert>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NAlert v-if="mode === 'delete'" type="warning">确认删除方法 {{ form.name }}（{{ form.id }}）。已被工序引用的方法应停用保留，不能删除。</NAlert>
      <template v-else>
      <NFormItem label="方法名称" path="name"><NInput v-model:value="form.name" placeholder="如 纸本文物脱酸加固" /></NFormItem>
      <NFormItem label="类别" path="category">
        <HDictSelect v-model:value="form.category" :type="DICT_RESTORATION_METHOD_CATEGORY" clearable placeholder="方法类别" />
      </NFormItem>
      <NFormItem label="适用质地" path="applicableTexture"><HDictSelect v-model:value="form.applicableTexture" type="col_texture" clearable placeholder="适用质地" /></NFormItem>
      <NFormItem label="技术要点" path="summary"><NInput v-model:value="form.summary" type="textarea" :rows="3" placeholder="方法适用场景、目标和技术要点" /></NFormItem>
      <NFormItem label="操作步骤" path="process"><NInput v-model:value="form.process" type="textarea" :rows="4" placeholder="清理、加固、补配、封护等步骤" /></NFormItem>
      <NFormItem label="材料工具" path="materials"><NInput v-model:value="form.materials" type="textarea" :rows="3" placeholder="材料、试剂、工具、设备" /></NFormItem>
      <NFormItem label="注意事项" path="precautions"><NInput v-model:value="form.precautions" type="textarea" :rows="3" placeholder="环境、风险、禁忌和复核要求" /></NFormItem>
      <NFormItem label="启用" path="enabled"><NSwitch v-model:value="form.enabled" /></NFormItem>
      <NFormItem label="排序" path="sort"><NInputNumber v-model:value="form.sort" :min="0" style="width: 160px" /></NFormItem>
      </template>
    </HPageForm>

    <HPageView v-if="viewShow" v-model:show="viewShow" :title="title" width="720px">
      <NAlert v-if="loading" type="info">正在读取当前方法资料…</NAlert>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="方法名称">{{ viewRow.name || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="类别"><HDictText :type="DICT_RESTORATION_METHOD_CATEGORY" :value="viewRow.category" /></NDescriptionsItem>
        <NDescriptionsItem label="适用质地"><HDictText type="col_texture" :value="viewRow.applicableTexture" /></NDescriptionsItem>
        <NDescriptionsItem label="启用">{{ viewRow.enabled === false ? '否' : '是' }}</NDescriptionsItem>
        <NDescriptionsItem label="排序">{{ viewRow.sort ?? '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="技术要点">{{ viewRow.summary || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="操作步骤">{{ viewRow.process || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="材料工具">{{ viewRow.materials || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="注意事项">{{ viewRow.precautions || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref, resolveDirective, withDirectives } from 'vue';
import { NAlert, NButton } from 'naive-ui';
import { useMasterDataWindow } from '../composables/useMasterDataWindow';
import { restorationMethodApi } from '../api/method';
import { presenceText } from '@/modules/collection/utils/dictRender';
import type { RestorationMethodVo } from '../typings';
import { RESTORATION_METHOD_WRITE_CODES, RESTORATION_METHOD_WRITE_PERMISSION } from '../utils/restorationPermissions';
import { useAuthStore } from '@/store';

const DICT_RESTORATION_METHOD_CATEGORY = 'restoration_method_category';
const tableRef = ref<any>(null);
const auth = useAuthStore();
// 新增 / 编辑 / 删除：RestorationMethodController.create / update / delete 同一组码。
// 未传 permission 时表格放行，所以另用 is-add / is-edit 按同一组码开关。
const canWriteMethod = computed(() => auth.hasPermission(RESTORATION_METHOD_WRITE_CODES));
const queryParams = reactive({
  keyword: null as string | null,
  category: null as string | null,
  applicableTexture: null as string | null,
  enabled: null as string | null,
  hasSummary: null as string | null,
});

const { formShow, viewShow, loading, error, title, vo: form, viewRow, mode, formKey, handleSubmit, onAdd, onEdit, onDelete, onView } = useMasterDataWindow<RestorationMethodVo>({
  label: '修复方法', empty: { enabled: true, sort: 0 }, context: restorationMethodApi.context,
  save: (vo, org, revision) => vo.id ? restorationMethodApi.update(vo.id, vo, org, revision) : restorationMethodApi.create(vo, org, revision),
  remove: restorationMethodApi.delete, refresh: () => tableRef.value?.handleRefresh?.(),
});

const rules = {
  name: { required: true, message: '请输入方法名称', trigger: ['blur', 'input'] },
};

// 与合同页一致：单一「操作」列放最后，供 HPageDataTable 合并编辑；无写权限时整列不渲染。
const operateColumn = { title: '操作', key: 'operate', type: 'operate', width: 120, align: 'center', fixed: 'right' as const, render: (row: RestorationMethodVo) => {
    const button = h(NButton, { size: 'small', type: 'error', onClick: () => onDelete(row) }, { default: () => '删除' });
    const permission = resolveDirective('permission');
    return permission ? withDirectives(button, [[permission, [...RESTORATION_METHOD_WRITE_CODES]]]) : button;
  } };
const columns = computed<any[]>(() => [
  { title: '方法名称', key: 'name', minWidth: 180, resizable: true, width: 180, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  { title: '类别', key: 'category', width: 130, ellipsis: { tooltip: true }, resizable: true, dictType: DICT_RESTORATION_METHOD_CATEGORY },
  { title: '适用质地', key: 'applicableTexture', width: 130, ellipsis: { tooltip: true }, resizable: true, dictType: 'col_texture' },
  { title: '技术要点', key: 'summary', minWidth: 220, resizable: true, width: 220, ellipsis: { tooltip: true } },
  { title: '启用', key: 'enabled', width: 90, ellipsis: { tooltip: true }, resizable: true, render: (row: RestorationMethodVo) => presenceText(row.enabled !== false) },
  { title: '排序', key: 'sort', width: 80 },
  ...(canWriteMethod.value ? [operateColumn] : []),
]);

</script>

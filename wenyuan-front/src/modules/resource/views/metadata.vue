<template>
  <ResourcePage fill title="元数据模板">
    <div class="resource-records">
      <n-alert v-if="error" type="error">{{ error }}</n-alert
      ><h-page-data-table ref="tableRef" class="resource-data-table" title="元数据模板" :bordered="false"
      :is-add="false" :is-export="false" :is-edit="false" :is-copy="false" :is-delete="false"
      :show-operate="false" :enable-drag="false" :initial-page-size="20" reset-page-on-size-change :fetch-data="fetchRows" :columns="columns" :scroll-x="1000" :pagination="false" column-config-key="resource-metadata" >    <template #toolbar-extra>
      <n-popover trigger="click"><template #trigger><n-button quaternary circle aria-label="字段规则"><template #icon><n-icon><IconMdiInformationOutline /></n-icon></template></n-button></template><div style="max-width: 280px">分类或类型字段覆盖同名全局字段。提交审核时校验必填项；删除模板不影响已登记数据。</div></n-popover>
      <n-button :loading="loading" @click="load">刷新</n-button>
      <n-button type="primary" @click="edit()"><template #icon><n-icon><IconMdiPlus /></n-icon></template>新增字段</n-button>
    </template>
</h-page-data-table>
      <n-modal class="resource-form-modal" v-model:show="show" preset="card" title="元数据字段" style="width: min(680px, calc(100vw - 32px))"
        ><n-form label-placement="top">
          <n-form-item label="分类范围"
            ><CategoryTreeSelect v-model:value="form.categoryId" placeholder="留空为全部分类"
          /></n-form-item>
          <n-form-item label="资源类型"
            ><n-select
              v-model:value="form.resourceType"
              :options="RESOURCE_TYPE_OPTIONS"
              clearable
              placeholder="留空为全部类型"
          /></n-form-item>
          <n-form-item label="字段键名" required
            ><n-input
              v-model:value="form.fieldName"
              placeholder="如 author，仅字母数字下划线，以字母开头"
              maxlength="100"
          /></n-form-item>
          <n-form-item label="显示名称" required
            ><n-input v-model:value="form.fieldLabel" maxlength="100"
          /></n-form-item>
          <n-form-item label="字段类型" required
            ><n-select v-model:value="form.fieldType" :options="types"
          /></n-form-item>
          <n-form-item label="必填"><n-switch v-model:value="form.required" /></n-form-item>
          <n-form-item v-if="['SELECT', 'MULTI_SELECT'].includes(form.fieldType)" label="选项（每行一个）" required
            ><n-input v-model:value="optionsText" type="textarea"
          /></n-form-item>
          <n-form-item label="默认值"
            ><n-input
              v-model:value="form.defaultValue"
              placeholder="布尔填 true/false，多选填 JSON 数组，其余填字段值"
              maxlength="500"
          /></n-form-item>
          <n-form-item label="排序"><n-input-number v-model:value="form.sort" :precision="0" /></n-form-item
          ><n-form-item label="填写说明"
            ><n-input v-model:value="form.description" type="textarea" maxlength="500"
          /></n-form-item> </n-form
        ><n-alert v-if="editError" type="error">{{ editError }}</n-alert
        ><n-space justify="end"
          ><n-button @click="show = false">取消</n-button
          ><n-button :loading="busy" type="primary" @click="save">保存字段</n-button></n-space
        ></n-modal
      >
    </div></ResourcePage>
</template>
<script setup lang="ts">
import HPageDataTable from '@/components/data/table/PageDataTable.vue';
import ResourcePage from '../components/ResourcePage.vue';
import { h, ref } from 'vue';
import { NButton, NSpace, useDialog, useMessage } from 'naive-ui';
import CategoryTreeSelect from '../components/CategoryTreeSelect.vue';
import { RESOURCE_TYPE_OPTIONS } from '../api/resource';
import { templates, saveTemplate, deleteTemplate } from '../api/business';
const loading = ref(false),
  error = ref(''),
  show = ref(false),
  busy = ref(false),
  form = ref<any>({}),
  editError = ref(''),
  optionsText = ref('');
const dialog = useDialog(),
  message = useMessage();
const types = [
  { label: '文本', value: 'TEXT' },
  { label: '数字', value: 'NUMBER' },
  { label: '日期', value: 'DATE' },
  { label: '单选', value: 'SELECT' },
  { label: '多选', value: 'MULTI_SELECT' },
  { label: '是/否', value: 'BOOLEAN' },
];
const tableRef = ref<InstanceType<typeof HPageDataTable>>();
const columns = [
  { title: '名称', key: 'fieldLabel' },
  { title: '键名', key: 'fieldName' },
  { title: '分类范围', key: 'categoryId', render: (r: any) => r.categoryId || '全部分类' },
  {
    title: '资源类型',
    key: 'resourceType',
    render: (r: any) => RESOURCE_TYPE_OPTIONS.find((v) => v.value === r.resourceType)?.label || '全部类型',
  },
  { title: '字段类型', key: 'fieldType', render: (r: any) => types.find((v) => v.value === r.fieldType)?.label },
  { title: '必填', key: 'required', render: (r: any) => (r.required ? '是' : '否') },
  {
    title: '操作',
    key: 'actions',
    render: (r: any) =>
      h(NSpace, {}, () => [
        h(NButton, { text: true, onClick: () => edit(r) }, () => '编辑'),
        h(NButton, { text: true, type: 'error', onClick: () => remove(r) }, () => '删除'),
      ]),
  },
];
async function fetchRows() {
  loading.value = true;
  error.value = '';
  try {
    const r: any = await templates();
    return r;
  } catch {
    error.value = '模板读取失败或当前账号无管理权限';
    return { data: [] };
  } finally {
    loading.value = false;
  }
}
function load() { tableRef.value?.handleRefresh(); }
function edit(row?: any) {
  form.value = row ? { ...row } : { fieldType: 'TEXT', required: false, sort: 0 };
  optionsText.value = row?.options ? JSON.parse(row.options).join('\n') : '';
  editError.value = '';
  show.value = true;
}
async function save() {
  busy.value = true;
  editError.value = '';
  try {
    await saveTemplate({
      ...form.value,
      options: JSON.stringify(
        optionsText.value
          .split('\n')
          .map((v) => v.trim())
          .filter(Boolean),
      ),
    });
    show.value = false;
    message.success('字段已保存');
    await load();
  } catch (e: any) {
    editError.value = e.message || '保存失败';
  } finally {
    busy.value = false;
  }
}
function remove(row: any) {
  dialog.warning({
    title: '删除模板字段',
    content: `删除「${row.fieldLabel}」？已登记元数据会保留。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await deleteTemplate(row.id);
      await load();
    },
  });
}

</script>

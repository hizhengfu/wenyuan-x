<template>
  <n-tabs
    type="card"
    v-model:value="activeTab"
    class="flex-1 flex wh-full"
    pane-style="display: flex;flex: 1;overflow: hidden;"
    @update:value="onTabChange"
  >
    <n-tab-pane name="all" tab="所有配置">
      <h-page-data-table
        ref="refPageTable"
        :fetch-data="list"
        :scroll-x="1200"
        :params="paramsForm"
        @add="handleAdd"
        @rowEdit="handleEdit"
        @rowView="handleView"
        :funApiDelete="del"
        :columns="columns"
      >
        <template #queryForm>
          <n-form-item label="配置名称" path="name">
            <n-input v-model:value="paramsForm.name" placeholder="配置名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="配置描述" path="remarks">
            <n-input v-model:value="paramsForm.remarks" placeholder="配置描述" clearable></n-input>
          </n-form-item>
        </template>
        <h-page-form
          :rules="rules"
          :model="form"
          label-width="auto"
          :title="formTitle"
          :disabled="formType === 'view'"
          :refTable="refPageTable"
          :handleSubmit="handleSubmit"
          v-model:show="formModel"
        >
          <n-grid :cols="2" :x-gap="16" responsive="screen" item-responsive>
            <n-form-item-gi label="名称" path="name">
              <n-input v-model:value="form.name" :disabled="formType !== 'add'" placeholder="请输入名称"></n-input>
            </n-form-item-gi>
            <n-form-item-gi label="描述" path="remarks">
              <n-input v-model:value="form.remarks" placeholder="请输入描述"></n-input>
            </n-form-item-gi>
            <n-form-item-gi :span="2" label="内容" path="content">
              <n-input type="textarea" v-model:value="form.content" placeholder="请输入内容"></n-input>
            </n-form-item-gi>
            <n-form-item-gi label="启用" path="enable">
              <n-switch v-model:value="form.enable"></n-switch>
            </n-form-item-gi>

            <n-gi :span="2">
              <n-divider title-placement="left" style="margin: 4px 0 12px">
                <span class="text-sm text-gray-400">字段元数据（选填，填了才会进对应模块的结构化表单 tab）</span>
              </n-divider>
            </n-gi>
            <n-form-item-gi label="字段类型" path="fieldType">
              <n-select
                v-model:value="form.fieldType"
                :options="fieldTypeOptions"
                clearable
                placeholder="留空表示无 schema（按自由文本处理）"
              ></n-select>
            </n-form-item-gi>
            <n-form-item-gi label="归属模块" path="module">
              <n-input
                v-model:value="form.module"
                placeholder="如 notify / sys；留空则不出现在模块 tab"
                clearable
              ></n-input>
            </n-form-item-gi>
            <n-form-item-gi label="分组" path="fieldGroup">
              <n-input v-model:value="form.fieldGroup" placeholder="模块内分组名，如 邮件 / 通用" clearable></n-input>
            </n-form-item-gi>
            <n-form-item-gi label="标签" path="label">
              <n-input v-model:value="form.label" placeholder="管理台展示名称" clearable></n-input>
            </n-form-item-gi>
            <n-form-item-gi label="私密" path="isPrivate">
              <n-switch v-model:value="form.isPrivate"></n-switch>
            </n-form-item-gi>
            <n-form-item-gi label="必填" path="required">
              <n-switch v-model:value="form.required"></n-switch>
            </n-form-item-gi>
            <n-form-item-gi v-if="form.fieldType === 'INT' || form.fieldType === 'LONG'" label="最小值" path="minVal">
              <n-input-number v-model:value="form.minVal" clearable placeholder="数值下限（闭区间）"></n-input-number>
            </n-form-item-gi>
            <n-form-item-gi v-if="form.fieldType === 'INT' || form.fieldType === 'LONG'" label="最大值" path="maxVal">
              <n-input-number v-model:value="form.maxVal" clearable placeholder="数值上限（闭区间）"></n-input-number>
            </n-form-item-gi>
            <n-form-item-gi v-if="form.fieldType === 'ENUM'" :span="2" label="枚举值" path="enumValues">
              <n-input v-model:value="form.enumValues" placeholder="ENUM 取值，多个用英文逗号分隔" clearable></n-input>
            </n-form-item-gi>
            <n-form-item-gi v-if="form.fieldType === 'STRING'" :span="2" label="正则约束" path="pattern">
              <n-input v-model:value="form.pattern" placeholder="STRING 完整匹配正则，选填" clearable></n-input>
            </n-form-item-gi>
            <n-form-item-gi label="显示条件：配置名称" path="visibleWhenField">
              <n-input v-model:value="form.visibleWhenField" placeholder="留空表示始终显示；填写同模块的配置名称" clearable></n-input>
            </n-form-item-gi>
            <n-form-item-gi label="显示条件：等于" path="visibleWhenEquals">
              <n-input v-model:value="form.visibleWhenEquals" :disabled="!form.visibleWhenField.trim()" placeholder="满足此值时显示；布尔值使用 TRUE / FALSE" clearable></n-input>
            </n-form-item-gi>
            <n-form-item-gi label="排序" path="sort">
              <n-input-number v-model:value="form.sort" placeholder="同分组内越小越靠前"></n-input-number>
            </n-form-item-gi>
          </n-grid>
        </h-page-form>
      </h-page-data-table>
    </n-tab-pane>
    <n-tab-pane v-for="m in moduleTabs" :key="m.id" :name="m.id" :tab="m.label" display-directive="show:lazy">
      <n-card class="module-config-form" content-style="overflow:auto;" size="small">
        <n-spin :show="loadingModule[m.id] && !moduleGroups[m.id]">
          <ModuleConfigForm
            :id="m.id"
            :groups="moduleGroups[m.id] || []"
            :loading="saving[m.id] || false"
            @save="(payload) => handleSaveModule(m.id, payload)"
          />
        </n-spin>
        <template #action>
          <div :id="'config-action-' + m.id"></div>
        </template>
      </n-card>
    </n-tab-pane>
  </n-tabs>
</template>

<script lang="ts" setup>
import { add, del, edit, list, modules, moduleRows, save } from '../api/config';
import { h, onMounted, reactive, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NSwitch, useMessage } from 'naive-ui';
import ModuleConfigForm from '../components/config/ModuleConfigForm.vue';
import { type ConfigModule, type ConfigRow, type FieldGroup, groupRows } from '../components/config/moduleKeys';

const message = useMessage();

const columns = [
  { key: 'name', title: '名称', width: '300px', columnType: 'title', resizable: true, ellipsis: true },
  { key: 'content', title: '内容', resizable: true, ellipsis: true },
  {
    key: 'remarks',
    title: '描述',
    width: '320px',
    fixed: 'left',
    resizable: true,
    ellipsis: true,
    render: (row: any) => {
      return row.remarks || row.label;
    },
  },
  {
    key: 'enable',
    title: '启用',
    width: '80px',
    align: 'center',
    render: (row: any) => {
      return h(NSwitch, {
        round: false,
        value: row.enable,
        size: 'small',
        onUpdateValue(value: boolean) {
          row.enable = value;
          edit(row);
        },
      });
    },
  },
];

const activeTab = ref('all');

const formModel = ref(false);
const refPageTable = ref(null);
const formTitle = ref();

const defaultForm = {
  id: '',
  name: '',
  remarks: '',
  enable: true,
  content: '',
  // schema 元数据（选填）
  fieldType: null as string | null,
  module: '',
  fieldGroup: '',
  label: '',
  isPrivate: false,
  required: false,
  minVal: null as number | null,
  maxVal: null as number | null,
  enumValues: '', // 表单内为逗号分隔字符串，提交时转数组
  pattern: '',
  visibleWhenField: '',
  visibleWhenEquals: '',
  enumLabels: null as Record<string, string> | null,
  sort: 0,
};

const fieldTypeOptions = [
  { label: 'STRING', value: 'STRING' },
  { label: 'INT', value: 'INT' },
  { label: 'LONG', value: 'LONG' },
  { label: 'BOOLEAN', value: 'BOOLEAN' },
  { label: 'ENUM', value: 'ENUM' },
];

const form = ref(cloneDeep(defaultForm));

const rules = {
  name: { required: true, message: '请输入名称' },
  remarks: { required: true, message: '请输入备注' },
  content: {
    validator(_rule: unknown, value: string | null) {
      return !form.value.fieldType || !form.value.required || !!value?.trim()
        ? true
        : new Error('请输入配置内容');
    },
    trigger: ['blur', 'change'],
  },
};

const paramsForm = ref({ name: '', remarks: '' });
const formType = ref('add');

function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  formTitle.value = '添加';
  formModel.value = true;
}

/** 后端 enumValues 为 string[]，表单内用逗号分隔字符串编辑 */
function enumArrToStr(v: any): string {
  return Array.isArray(v) ? v.join(',') : (v ?? '');
}

function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  Object.assign(form.value, row);
  form.value.enumValues = enumArrToStr(row.enumValues);
  form.value.visibleWhenField = row.visibleWhen?.field ?? '';
  form.value.visibleWhenEquals = row.visibleWhen?.equals ?? '';
  formTitle.value = `编辑「${row.name}」`;
  formModel.value = true;
}

function handleView(row: any) {
  formType.value = 'view';
  form.value = cloneDeep(defaultForm);
  Object.assign(form.value, row);
  form.value.enumValues = enumArrToStr(row.enumValues);
  form.value.visibleWhenField = row.visibleWhen?.field ?? '';
  form.value.visibleWhenEquals = row.visibleWhen?.equals ?? '';
  formTitle.value = `查看「${row.name}」`;
  formModel.value = true;
}

function handleSubmit(data: any) {
  // enumValues：逗号分隔字符串 → string[]（与后端 ConfigVo 对齐）
  const { visibleWhenField, visibleWhenEquals, ...values } = data;
  const payload = {
    ...values,
    visibleWhen: visibleWhenField?.trim()
      ? { field: visibleWhenField.trim(), equals: visibleWhenEquals ?? '' }
      : null,
    enumValues:
      typeof data.enumValues === 'string' && data.enumValues.trim()
        ? data.enumValues
            .split(',')
            .map((s: string) => s.trim())
            .filter(Boolean)
        : null,
  };
  return formType.value === 'add' ? add(payload) : edit(payload);
}

/**
 * 模块配置：
 *  - 挂载时只拉一次 `/config/modules`，后端返回 [{id,label}]（label 由 config_module 字典提供），据此渲染 tab
 *  - 点击某 tab 时按需 `/config/modules/{id}` 拉该模块的 schema 行，结果缓存在 moduleGroups
 *  - 保存成功后仅刷新当前模块，不波及其他 tab
 */
const moduleTabs = ref<ConfigModule[]>([]);
const moduleGroups = reactive<Record<string, FieldGroup[]>>({});
const loadingModule = reactive<Record<string, boolean>>({});
const saving = reactive<Record<string, boolean>>({});

async function loadModuleIds() {
  const res: any = await modules();
  // 兼容旧返回（string[]）：无 label 时回退用 id 作展示名
  const data = (res?.data || res || []) as Array<ConfigModule | string>;
  moduleTabs.value = data.map((m) => (typeof m === 'string' ? { id: m, label: m } : m));
}

async function loadModule(id: string, force = false) {
  if (!force && moduleGroups[id]) return;
  loadingModule[id] = true;
  try {
    const res: any = await moduleRows(id);
    const rows = (res?.data || res || []) as ConfigRow[];
    moduleGroups[id] = groupRows(rows);
  } finally {
    loadingModule[id] = false;
  }
}

function onTabChange(name: string) {
  if (name === 'all') return;
  loadModule(name);
}

async function handleSaveModule(moduleId: string, payload: Record<string, any>) {
  saving[moduleId] = true;
  try {
    await save(payload);
    await loadModule(moduleId, true);
  } finally {
    saving[moduleId] = false;
  }
}

onMounted(loadModuleIds);
</script>

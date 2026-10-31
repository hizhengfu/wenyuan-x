<template>
  <n-card class="module-config-form" content-style="overflow:auto;" size="small">
    <n-form ref="formRef" :model="model" :rules="formRules" label-placement="left" label-width="320px">
      <div v-for="group in visibleGroups" :key="group.title" class="mb-4">
        <n-divider title-placement="left">
          <span class="text-base font-medium">{{ group.title }}</span>
        </n-divider>
        <n-form-item
          v-for="row in group.fields"
          :key="row.name"
          :label="row.label || row.name"
          :path="row.name"
          class="mb-3"
        >
          <div class="flex-1">
            <component
              :is="controlOf(row)"
              v-bind="bindingsOf(row)"
              :value="model[row.name]"
              @update:value="(v: any) => (model[row.name] = v)"
            />
            <div v-if="row.remarks" class="text-xs text-gray-400 mt-1">{{ row.remarks }}</div>
          </div>
        </n-form-item>
      </div>
    </n-form>
    <Teleport defer :to="'#config-action-' + id">
      <n-space style="margin-left: 320px">
        <n-button type="primary" :loading="loading" @click="onSave">
          <template #icon>
            <n-icon size="20px">
              <IconMdiCheck />
            </n-icon>
          </template>
          保存
        </n-button>
        <n-button @click="reset" type="warning">
          <template #icon>
            <n-icon size="20px">
              <IconMdiRestore />
            </n-icon>
          </template>
          重置
        </n-button>
      </n-space>
    </Teleport>
  </n-card>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
import { NInput, NInputNumber, NSwitch, NSelect, NIcon, type FormInst, type FormItemRule } from 'naive-ui';
import type { Component } from 'vue';
import { visibleConfigNames, type ConfigRow, type FieldGroup } from './moduleKeys';

const props = defineProps<{
  id: string;
  groups: FieldGroup[];
  loading?: boolean;
}>();

const emit = defineEmits<{
  (e: 'save', payload: Record<string, any>): void;
}>();

const formRef = ref<FormInst | null>(null);
const allRows = computed<ConfigRow[]>(() => props.groups.flatMap((g) => g.fields));

const model = ref<Record<string, any>>({});

const visibleGroups = computed<FieldGroup[]>(() => {
  const visibleNames = visibleConfigNames(allRows.value, model.value);
  return props.groups
    .map((group) => ({ ...group, fields: group.fields.filter((row) => visibleNames.has(row.name)) }))
    .filter((group) => group.fields.length > 0);
});
const visibleRows = computed(() => visibleGroups.value.flatMap((group) => group.fields));

/** 把 `Configs.content` 字符串解析为表单模型值 */
function parseValue(row: ConfigRow, content: any): any {
  if (content == null || content === '') {
    if (row.fieldType === 'BOOLEAN') return false;
    if (row.fieldType === 'INT' || row.fieldType === 'LONG') return null;
    return '';
  }
  if (row.fieldType === 'BOOLEAN') {
    const s = String(content).toLowerCase();
    return s === 'true' || s === '1';
  }
  if (row.fieldType === 'INT' || row.fieldType === 'LONG') {
    const n = Number(content);
    return Number.isFinite(n) ? n : null;
  }
  return String(content);
}

function rebuildModel() {
  const next: Record<string, any> = {};
  for (const row of allRows.value) {
    next[row.name] = parseValue(row, row.content);
  }
  model.value = next;
}

watch(() => props.groups, rebuildModel, { immediate: true, deep: true });

function controlOf(row: ConfigRow): Component {
  if (row.fieldType === 'BOOLEAN') return NSwitch;
  if (row.fieldType === 'INT' || row.fieldType === 'LONG') return NInputNumber;
  if (row.fieldType === 'ENUM') return NSelect;
  return NInput;
}

function bindingsOf(row: ConfigRow): Record<string, any> {
  if (row.fieldType === 'ENUM') {
    return {
      options: (row.enumValues || []).map((v) => ({
        label: row.enumLabels?.[v] ?? v,
        value: v,
      })),
      style: 'width: 240px',
    };
  }
  if (row.fieldType === 'INT' || row.fieldType === 'LONG') {
    return {
      min: row.minVal ?? undefined,
      max: row.maxVal ?? undefined,
      style: 'width: 240px',
    };
  }
  // STRING / 其他：私密字段（如 SMTP 密码/授权码）用密码框遮挡明文
  if (row.isPrivate) {
    return {
      type: 'password',
      showPasswordOn: 'click',
      placeholder: '',
      clearable: true,
    };
  }
  return { placeholder: '', clearable: true };
}

/** 由 schema 列生成 naive 的 form rules；与后端 `validateAgainstSchema` 规则同构。 */
function rulesOf(row: ConfigRow): FormItemRule[] {
  const rules: FormItemRule[] = [];
  const label = row.label || row.name;
  const required = row.required !== false && row.fieldType !== 'BOOLEAN';

  if (required) {
    rules.push({
      required: true,
      validator(_rule, value: any) {
        if (value == null || value === '') return new Error(`${label}不能为空`);
        return true;
      },
      trigger: ['blur', 'change'],
    });
  }

  if (row.fieldType === 'INT' || row.fieldType === 'LONG') {
    rules.push({
      validator(_rule, value: any) {
        if (value == null || value === '') return true; // required 由上条负责
        if (typeof value !== 'number' || !Number.isFinite(value)) return new Error(`${label}必须是整数`);
        if (row.minVal != null && value < row.minVal) return new Error(`${label}不能小于 ${row.minVal}`);
        if (row.maxVal != null && value > row.maxVal) return new Error(`${label}不能大于 ${row.maxVal}`);
        return true;
      },
      trigger: ['blur', 'change'],
    });
  }

  if (row.fieldType === 'ENUM' && row.enumValues?.length) {
    const allowed = row.enumValues;
    rules.push({
      validator(_rule, value: any) {
        if (value == null || value === '') return true;
        if (!allowed.includes(String(value))) {
          return new Error(`${label}必须是 ${allowed.join('|')} 之一`);
        }
        return true;
      },
      trigger: 'change',
    });
  }

  if (row.fieldType === 'STRING' && row.pattern) {
    const re = new RegExp(row.pattern);
    rules.push({
      validator(_rule, value: any) {
        if (value == null || value === '') return true;
        if (!re.test(String(value))) return new Error(`${label}格式不正确`);
        return true;
      },
      trigger: 'blur',
    });
  }

  return rules;
}

const formRules = computed<Record<string, FormItemRule[]>>(() => {
  const out: Record<string, FormItemRule[]> = {};
  for (const row of visibleRows.value) out[row.name] = rulesOf(row);
  return out;
});

/** 序列化回 Configs.content 期望的字符串 */
function serialize(): Record<string, any> {
  const out: Record<string, any> = {};
  // 隐藏字段保留在 model 中供切换回来继续编辑，但不校验、不提交，避免覆盖其他服务商或存储方式的配置。
  for (const row of visibleRows.value) {
    const v = model.value[row.name];
    if (row.fieldType === 'BOOLEAN') out[row.name] = v ? 'TRUE' : 'FALSE';
    else if (row.fieldType === 'INT' || row.fieldType === 'LONG') out[row.name] = v == null ? '' : String(v);
    else out[row.name] = v == null ? '' : String(v);
  }
  return out;
}

async function onSave() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  emit('save', serialize());
}

function reset() {
  rebuildModel();
}
</script>

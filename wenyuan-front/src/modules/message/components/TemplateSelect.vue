<template>
  <n-select
    :value="modelValue"
    :options="options"
    :loading="loading"
    filterable
    clearable
    placeholder="选择已配置的通知模板"
    @update:value="onSelect"
  />
</template>

<script lang="ts" setup>
import { onBeforeMount, ref } from 'vue';
import { findPage } from '../api/template';
import type { MessageTemplateVo } from '../typings/message';

const props = defineProps<{
  /** 选中模板 id */
  modelValue?: string | null;
  /** 仅保留具备该通道模板字段的模板（如 'SMS' → 含平台模板编号或正文模板）。不传则全部。 */
  requireChannelField?: 'SMS' | 'WX_MP' | 'WX_MA' | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', id: string | null): void;
  (e: 'update:template', tpl: MessageTemplateVo | null): void;
}>();

interface Option {
  label: string;
  value: string;
  raw: MessageTemplateVo;
}

const options = ref<Option[]>([]);
const loading = ref(false);
let templates: MessageTemplateVo[] = [];

/** 按需要的通道字段过滤：保证选中的模板确实能覆盖该模板强制通道。 */
function hasChannelField(tpl: MessageTemplateVo): boolean {
  switch (props.requireChannelField) {
    case 'SMS':
      return !!(tpl.smsTemplateCode?.trim() || tpl.smsContentTpl?.trim());
    case 'WX_MP':
      return !!tpl.wxMpTemplateId;
    case 'WX_MA':
      return !!tpl.wxMaTemplateId;
    default:
      return true;
  }
}

async function load() {
  loading.value = true;
  try {
    // pageSize=0 → 后端返回全部（见 CLAUDE.md 约定）
    const resp: any = await findPage({ pageSize: 0 }).catch(() => ({ data: { list: [] } }));
    const list: MessageTemplateVo[] = resp?.data?.list || resp?.data || [];
    templates = list.filter(hasChannelField);
    options.value = templates.map((t) => ({
      label: `${t.name}（${t.code}）`,
      value: t.id!,
      raw: t,
    }));
  } finally {
    loading.value = false;
  }
}

function onSelect(id: string | null) {
  emit('update:modelValue', id);
  emit('update:template', templates.find((t) => t.id === id) ?? null);
}

onBeforeMount(load);
</script>

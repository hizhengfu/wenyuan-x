<template>
  <n-modal v-model:show="visible" preset="card" style="width: 520px" title="按模板导出 Word">
    <n-form label-placement="left" label-width="100px">
      <n-form-item label="待导出文物">
        <n-tag :bordered="false" type="info">已选 {{ rows.length }} 件</n-tag>
      </n-form-item>
      <n-form-item label="模板">
        <n-radio-group v-model:value="template" @update:value="applyPreset">
          <n-space vertical>
            <n-radio value="STANDARD">标准卡片（文字 + 封面图）</n-radio>
            <n-radio value="WITH_LINE">含线图卡片（文字 + 封面 + 线图）</n-radio>
            <n-radio value="TEXT_ONLY">纯文字卡片</n-radio>
            <n-radio value="FULL">全字段卡片（文字 + 多角度照片 + 线图 + 平剖面图）</n-radio>
          </n-space>
        </n-radio-group>
      </n-form-item>
      <n-form-item label="导出内容">
        <n-space vertical>
          <n-checkbox v-model:checked="opt.includeShape">形制（主要研究数据）</n-checkbox>
          <n-checkbox v-model:checked="opt.includeCover">封面图</n-checkbox>
          <n-checkbox v-model:checked="opt.includeAngles">多角度照片</n-checkbox>
          <n-checkbox v-model:checked="opt.includeLine">线图</n-checkbox>
          <n-checkbox v-model:checked="opt.includeProfile">平剖面图</n-checkbox>
          <span class="opt-tip">勾选项会覆盖模板预设；切换模板会重置为该模板默认内容。</span>
        </n-space>
      </n-form-item>
      <n-form-item label="文件名">
        <span style="color: #666">
          {{ rows.length === 1 ? `文物卡片_${rows[0].name}.docx` : `文物卡片_批量_${rows.length}件.docx` }}
        </span>
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="visible = false">取消</n-button>
        <n-button type="primary" :loading="loading" @click="handleExport">导出</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useMessage } from 'naive-ui';
import { exportCard } from '../api/artifact';

const props = defineProps<{
  show: boolean;
  rows: any[];
}>();
const emit = defineEmits<{ (e: 'update:show', v: boolean): void }>();

const message = useMessage();
const template = ref('STANDARD');
const loading = ref(false);

const opt = ref({
  includeShape: true,
  includeCover: true,
  includeAngles: false,
  includeLine: false,
  includeProfile: false,
});

/** 切换模板时把勾选项重置为该模板的默认内容 */
function applyPreset(t: string) {
  const presets: Record<string, typeof opt.value> = {
    STANDARD: { includeShape: true, includeCover: true, includeAngles: false, includeLine: false, includeProfile: false },
    WITH_LINE: { includeShape: true, includeCover: true, includeAngles: false, includeLine: true, includeProfile: false },
    TEXT_ONLY: { includeShape: true, includeCover: false, includeAngles: false, includeLine: false, includeProfile: false },
    FULL: { includeShape: true, includeCover: true, includeAngles: true, includeLine: true, includeProfile: true },
  };
  opt.value = { ...(presets[t] || presets.STANDARD) };
}

const visible = computed({
  get: () => props.show,
  set: (v) => emit('update:show', v),
});

async function handleExport() {
  if (!props.rows.length) {
    message.warning('请先勾选文物');
    return;
  }
  loading.value = true;
  try {
    const ids = props.rows.map((r) => r.id);
    const resp: any = await exportCard(ids, template.value, opt.value);
    // resp 可能直接是 Blob，也可能是 AxiosResponse —— 兼容两种封装
    const blob: Blob = resp instanceof Blob ? resp : resp.data || resp;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download =
      ids.length === 1
        ? `文物卡片_${props.rows[0].name || ids[0]}.docx`
        : `文物卡片_批量_${ids.length}件.docx`;
    a.click();
    URL.revokeObjectURL(url);
    message.success('导出成功');
    visible.value = false;
  } catch (e: any) {
    message.error(e?.message || '导出失败');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.opt-tip {
  font-size: 12px;
  color: #999;
}
</style>

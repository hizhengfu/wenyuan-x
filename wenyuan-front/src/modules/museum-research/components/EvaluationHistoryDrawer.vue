<template>
  <NDrawer :show="show" :width="720" @update:show="setShow">
    <NDrawerContent :title="`${title || '成果'} · 评价历史`" closable>
      <p>以下为已留存的历史记录。删除记录保留当时的评价，不代表该评价当前仍有效。</p>
      <NButton :loading="loading" :disabled="!target" @click="reload">重新核查</NButton>
      <NSpin :show="loading">
        <NAlert v-if="error" type="error" style="margin-top: 12px">{{ error }}</NAlert>
        <NEmpty v-else-if="!loading && !records.length" description="暂无已留存的评价历史" style="margin-top: 20px" />
        <article v-for="record in records" :key="record.id" class="evaluation-history-record">
          <h3>{{ record.action }} · {{ formatDateTime(record.archivedAt) }}</h3>
          <dl>
            <dt>评价人</dt><dd>{{ record.evaluation.evaluatorName || '署名未核实' }}</dd>
            <dt>评分</dt><dd>{{ record.evaluation.score }}</dd>
            <dt>评价意见</dt><dd>{{ record.evaluation.opinion || '未填写' }}</dd>
            <dt>改进建议</dt><dd>{{ record.evaluation.suggestion || '未填写' }}</dd>
            <dt>评价编号</dt><dd>{{ record.evaluation.id }}</dd>
          </dl>
        </article>
      </NSpin>
    </NDrawerContent>
  </NDrawer>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { NAlert, NButton, NDrawer, NDrawerContent, NEmpty, NSpin } from 'naive-ui';
import { formatDateTime } from '@/utils/datetime';
import { evaluationApi } from '../api/evaluation';
import type { ResAchievementVo, ResAchievementEvalVo } from '../typings';

type RecordView = { id: string; action: string; archivedAt: any; evaluation: ResAchievementEvalVo };
const actions: Record<string, string> = { CREATED: '创建评价', BEFORE_UPDATE: '修改前', UPDATED: '修改后', DELETED: '删除评价' };
const show = ref(false), loading = ref(false), error = ref(''), title = ref('');
const target = ref<{ id: string; orgId: string } | null>(null);
const records = ref<RecordView[]>([]);
let revision = 0;

function setShow(value: boolean) {
  show.value = value;
  if (!value) {
    revision++; records.value = []; error.value = ''; loading.value = false; target.value = null;
  }
}

async function reload() {
  const version = ++revision;
  const requested = target.value;
  records.value = []; error.value = '';
  if (!requested) { loading.value = false; return; }
  loading.value = true;
  try {
    const response: any = await evaluationApi.history(requested.id, requested.orgId);
    if (version !== revision || !show.value) return;
    if (!Array.isArray(response.data)) throw new Error('invalid history');
    const parsed: RecordView[] = response.data.map((item: any) => {
      const snapshot = typeof item.snapshot === 'string' ? JSON.parse(item.snapshot) : item.snapshot;
      const evaluation = snapshot?.evaluation;
      const action = /^EVAL_(BEFORE_UPDATE|CREATED|UPDATED|DELETED)_/.exec(item.phase || '')?.[1];
      if (!item.id || !action || !evaluation?.id || evaluation.achievementId !== requested.id ||
          typeof evaluation.score !== 'number' || !Number.isFinite(evaluation.score) || evaluation.score < 0 || evaluation.score > 100) {
        throw new Error('invalid history');
      }
      return { id: String(item.id), action: actions[action], archivedAt: item.archivedAt, evaluation };
    });
    records.value = parsed.reverse();
  } catch {
    if (version === revision && show.value) {
      records.value = [];
      error.value = '评价历史读取失败，请核对当前权限或档案记录后重试。';
    }
  } finally {
    if (version === revision) loading.value = false;
  }
}

async function open(row: Pick<ResAchievementVo, 'id' | 'orgId' | 'title'>) {
  revision++; records.value = []; error.value = ''; loading.value = false;
  title.value = row.title || '成果'; show.value = true;
  target.value = row.id && row.orgId ? { id: row.id, orgId: row.orgId } : null;
  if (!target.value) { error.value = '成果编号或所属馆未核实，无法读取评价历史。'; return; }
  await reload();
}
onBeforeUnmount(() => { revision++; });
defineExpose({ open });
</script>

<style scoped>
.evaluation-history-record { margin-top: 18px; padding-top: 12px; border-top: 1px solid #ddd; }
dl { display: grid; grid-template-columns: 80px 1fr; gap: 8px; }
dd { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>

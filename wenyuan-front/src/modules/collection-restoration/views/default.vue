<template>
  <div class="restoration-dashboard">
    <NCard title="文物修复看板" size="small" :bordered="false">
      <NAlert type="info">
        修复办理顺序：方案与实际许可 → 送修点交 → 实施记录 → 实际验收及适用的结果备案 → 资料归档。归库和后续养护分别核实办理。
      </NAlert>
    </NCard>

    <div style="margin-top: 12px">
      <NButton size="small" :loading="loading" @click="refresh">重新核对</NButton>
      <NAlert v-if="loading" type="info" style="margin-top: 8px">正在读取当前可查阅范围内的修复资料…</NAlert>
      <NAlert v-for="error in errors" :key="error" type="error" style="margin-top: 8px">{{ error }}</NAlert>
    </div>

    <NGrid :cols="3" :x-gap="12" style="margin-top: 12px">
      <NGi><NCard size="small"><div class="stat-value">{{ completedThisMonth ?? '—' }}</div><div class="tile-label">本月修复完成</div></NCard></NGi>
      <NGi><NCard size="small"><div class="stat-value emergency">{{ emergencyPending ?? '—' }}</div><div class="tile-label">抢救性修复待办</div></NCard></NGi>
      <NGi><NCard size="small"><div class="stat-value warning">{{ certificatesReady ? expiring.length : '—' }}</div><div class="tile-label">90 天内资质到期</div></NCard></NGi>
    </NGrid>

    <NGrid :cols="4" :x-gap="12" :y-gap="12" style="margin-top: 12px">
      <NGi>
        <NCard size="small" :bordered="false">
          <div class="tile-label">修复项目</div>
          <NButton text tag="a" @click="go('/collection-restoration/project')">进入 →</NButton>
        </NCard>
      </NGi>
      <NGi>
        <NCard size="small" :bordered="false">
          <div class="tile-label">修复档案</div>
          <NButton text tag="a" @click="go('/collection-restoration/archive')">进入 →</NButton>
        </NCard>
      </NGi>
      <NGi>
        <NCard size="small" :bordered="false">
          <div class="tile-label">修复验收</div>
          <NButton text tag="a" @click="go('/collection-restoration/acceptance')">进入 →</NButton>
        </NCard>
      </NGi>
      <NGi>
        <NCard size="small" :bordered="false">
          <div class="tile-label">工序管理</div>
          <NButton text tag="a" @click="go('/collection-restoration/process')">进入 →</NButton>
        </NCard>
      </NGi>
    </NGrid>

    <NCard title="资质证书到期预警（90 天内）" size="small" :bordered="false" style="margin-top: 12px">
      <NDataTable v-if="certificatesReady" :columns="expiringColumns" :data="expiring" size="small" :bordered="false" />
    </NCard>
  </div>
</template>

<script setup lang="ts">
import { NAlert, NButton, NCard, NDataTable, NGi, NGrid } from 'naive-ui';
import { useRouter } from 'vue-router';
import { useRestorationDashboard } from '../composables/useRestorationDashboard';
import type { QualificationCertVo } from '../typings';
import { formatDateValue } from '@/utils/datetime';

const router = useRouter();
const { expiring, certificatesReady, completedThisMonth, emergencyPending, errors, loading, active, refresh } = useRestorationDashboard();

const expiringColumns = [
  { title: '证书号', key: 'certNo', ellipsis: { tooltip: true }, resizable: true, width: 180 },
  { title: '持有方', key: 'holderName', ellipsis: { tooltip: true }, resizable: true, width: 200 },
  { title: '等级', key: 'certLevel', ellipsis: { tooltip: true }, resizable: true, width: 140 },
  { title: '发证机关', key: 'issuer', ellipsis: { tooltip: true }, resizable: true, width: 200 },
  { title: '到期日', key: 'expireDate', ellipsis: { tooltip: true }, resizable: true, width: 120, render: (row: QualificationCertVo) => formatDateValue(row.expireDate) },
];

const go = (path: string) => { if (active.value) return router.push(path); };
</script>

<style scoped>
.tile-label { font-size: 14px; color: #666; margin-bottom: 8px; }
.stat-value { font-size: 28px; font-weight: 700; color: #18a058; }
.stat-value.emergency { color: #d03050; }
.stat-value.warning { color: #f0a020; }
.restoration-dashboard { padding: 12px; }
</style>

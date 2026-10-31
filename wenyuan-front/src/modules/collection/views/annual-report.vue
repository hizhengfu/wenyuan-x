<template>
  <NSpin :show="loading"
    ><NSpace vertical :size="18" style="padding: 20px">
      <NSpace align="center"
        ><NText>统计年度</NText><NInputNumber v-model:value="year" :min="1900" :max="currentYear" /><NButton
          type="primary"
          @click="loadSelectedYear"
          >核对年度统计</NButton
        ><NButton :disabled="!stat" @click="printReport">打印系统计算预览</NButton></NSpace
      >
      <NAlert type="info"
        >当前收藏单位的正式登记账统计。条目数与件、套等实际数量分开；其他馆的调拨使用当时交接记录。打印或保存预览不会产生签署、报送或备案事实。</NAlert
      >
      <NAlert v-if="readError" type="error">{{ readError }}</NAlert>
      <template v-if="stat">
        <section>
          <h2>{{ stat.year }} 年度正式总账计算预览{{ stat.completeYear ? '' : '（年度未结束）' }}</h2>
          <p>统计截至 {{ date(stat.cutoff) }}；范围：当前收藏单位；仅包含具有完整本馆登记历史的记录。</p>
          <p v-if="stat.issues?.length">
            历史核对缺口 {{ stat.issues.length }} 条，下列计算尚不能代表完整年度报送数。须核对原纸质总账及逐件明细。
          </p>
          <AnnualLedgerFigures :data="stat" />
          <NDataTable
            v-if="stat.issues?.length"
            :scroll-x="570"
            :columns="issueColumns"
            :data="stat.issues"
            :pagination="{ pageSize: 15 }"
          />
        </section>
        <NCollapse
          ><NCollapseItem title="本年实际登记、调拨、退出和信息变更记录"
            ><NDataTable :scroll-x="830"
              :columns="eventColumns"
              :data="stat.events || []"
              :pagination="{ pageSize: 15 }" /></NCollapseItem
        ></NCollapse>
        <AnnualReportEvidencePanel
          :year="stat.year"
          :complete-year="!!stat.completeYear"
          :has-issues="!!stat.issues?.length"
        />
      </template> </NSpace
  ></NSpin>
</template>
<script setup lang="ts">
import { ref, onMounted, watch, onActivated, onDeactivated, onBeforeUnmount } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { useRoute, useRouter } from 'vue-router';
import { formatLocalDateValue } from '@/utils';
import { htmlPrint } from '@/components/basic/print/PrintUtils';
import { buildAnnualReportPrintHtml, annualReportPrintStyles } from '../utils/annualReportPrint';
import { collectionApi } from '../api/collection';
import AnnualLedgerFigures from '../components/AnnualLedgerFigures.vue';
import AnnualReportEvidencePanel from '../components/AnnualReportEvidencePanel.vue';
const route = useRoute();
const router = useRouter();
const currentYear = new Date().getFullYear();
function queryYear(value: unknown): number | null {
  if (typeof value !== 'string' || !/^(?:19|20)\d{2}$/.test(value)) return null;
  const parsed = Number(value);
  return parsed >= 1900 && parsed <= currentYear ? parsed : null;
}
const year = ref(queryYear(route.query.year) ?? currentYear),
  stat = ref<any>(),
  loading = ref(false),
  readError = ref('');
const date = (value: any) => (value == null ? '未登记' : formatLocalDateValue(value, 'YYYY-MM-DD HH:mm:ss'));
const issueColumns = [
  { title: '登记号', key: 'totalRegNo', width: 150, ellipsis: { tooltip: true } },
  { title: '待核对事项', key: 'description', width: 420, ellipsis: { tooltip: true } },
];
const names: Record<string, string> = {
  REGISTER: '正式登记',
  TRANSFER_IN: '调入',
  TRANSFER_OUT: '调出',
  DEREGISTER: '退出',
  CHANGE: '信息变更',
};
const eventColumns = [
  { ellipsis: { tooltip: true }, resizable: true, width: 150, title: '登记号', key: 'item.totalRegNo' },
  { ellipsis: { tooltip: true }, resizable: true, width: 200, title: '名称', key: 'item.name' },
  { width: 180,
    title: '实际记录日期',
    key: 'occurredAt',
    render: (r: any) =>
      r.kind === 'TRANSFER_IN' || r.kind === 'TRANSFER_OUT'
        ? formatLocalDateValue(r.occurredAt, 'YYYY-MM-DD')
        : date(r.occurredAt),
  },
  { ellipsis: { tooltip: true }, resizable: true, width: 130, title: '事项', key: 'kind', render: (r: any) => names[r.kind] || r.kind },
  { width: 90, title: '数量', key: 'item.quantity' },
  { width: 80, title: '单位', key: 'item.unit' },
];
const auth = useAuthStore();
let request = 0,
  active = true,
  wasInactive = false;
function clear() {
  ++request;
  stat.value = undefined;
  readError.value = '';
  loading.value = false;
}
async function load() {
  if (!active) return;
  const version = ++request,
    selected = year.value;
  loading.value = true;
  stat.value = undefined;
  readError.value = '';
  try {
    const response: any = await collectionApi.annualStat(selected);
    if (!active || version !== request) return;
    const value = response?.data;
    if (
      (response?.status !== undefined && response.status !== 200) ||
      !value ||
      Array.isArray(value) ||
      value.year !== selected ||
      typeof value.completeYear !== 'boolean' ||
      !Array.isArray(value.issues) ||
      !Array.isArray(value.events)
    )
      throw Error('年度统计资料缺失或与本次年度不一致');
    stat.value = value;
  } catch {
    if (version === request) readError.value = '年度统计读取失败，请核对当前权限和收藏单位后重试。';
  } finally {
    if (version === request) loading.value = false;
  }
}
function loadSelectedYear() {
  void router.replace({ query: { ...route.query, year: String(year.value) } });
  void load();
}
function printReport() {
  if (!stat.value) return;
  htmlPrint(
    buildAnnualReportPrintHtml(stat.value, date(stat.value.cutoff)),
    `${stat.value.year}年度正式总账计算预览`,
    annualReportPrintStyles,
  );
}
watch(
  () => route.query.year,
  (value) => {
    const selected = queryYear(value);
    if (selected !== null && selected !== year.value) {
      year.value = selected;
      void load();
    }
  },
);
watch(
  () => auth.userInfo?.id,
  () => {
    clear();
    if (active) void load();
  },
  { flush: 'sync' },
);
onMounted(load);
onActivated(() => {
  active = true;
  if (wasInactive) {
    wasInactive = false;
    void load();
  }
});
onDeactivated(() => {
  active = false;
  wasInactive = true;
  clear();
});
onBeforeUnmount(() => {
  active = false;
  clear();
});
</script>

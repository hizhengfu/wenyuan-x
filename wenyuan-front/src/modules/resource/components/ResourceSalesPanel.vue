<template>
  <n-space vertical :size="16"
    ><n-space
      ><n-input-number
        v-model:value="year"
        :min="1900"
        :max="9998"
        :precision="0"
        placeholder="全部年度"
        clearable
        @update:value="load" /><n-button @click="load">查询</n-button
      ><n-button v-if="canManage" type="primary" @click="open">录入销售</n-button
      ><n-button @click="template">导入模板</n-button
      ><n-button v-if="canManage" :loading="busy" @click="input?.click()">导入销售</n-button
      ><n-button v-if="canExport" @click="exportSales">导出销售</n-button
      ><input ref="input" type="file" accept=".xlsx,.xls,.csv" hidden @change="importFile"
    /></n-space>
<n-alert v-if="error" type="error">{{ error }}</n-alert>
    <h-page-data-table ref="tableRef" :key="`${productId}:${auth.identityRevision}`" class="resource-data-table" title="销售记录" :bordered="false"
      :is-add="false" :is-export="false" :is-edit="false" :is-copy="false" :is-delete="false"
      :show-operate="false" :enable-drag="false" :initial-page-size="20" :pagination="{ showQuickJumper: false, pageSlot: 5 }" reset-page-on-size-change :fetch-data="fetchRows" :params="{ productId, year }" :columns="columns" :scroll-x="1000" column-config-key="resource-sales" />
    <n-modal class="resource-form-modal" v-model:show="show" preset="card" title="录入产品销售" style="width: min(600px, calc(100vw - 32px))"
      ><n-form label-placement="top"
        ><n-form-item label="销售日期" required
          ><n-date-picker v-model:formatted-value="form.soldOn" value-format="yyyy-MM-dd" /></n-form-item
        ><n-form-item label="业务单号" required><n-input v-model:value="form.reference" maxlength="100" /></n-form-item
        ><n-form-item label="销售数量" required
          ><n-input-number v-model:value="form.quantity" :min="1" :max="1000000" :precision="0" /></n-form-item
        ><n-form-item label="销售金额（元）" required
          ><n-input v-model:value="amount" placeholder="如 128.50" /></n-form-item
        ><n-form-item label="销售地区" required><n-input v-model:value="form.region" maxlength="100" /></n-form-item
        ><n-form-item label="销售渠道" required
          ><n-input v-model:value="form.channel" maxlength="100" /></n-form-item></n-form
      ><n-alert v-if="editError" type="error">{{ editError }}</n-alert
      ><n-space justify="end"
        ><n-button @click="show = false">取消</n-button
        ><n-button :loading="busy" type="primary" @click="save">保存销售</n-button></n-space
      ></n-modal
    >
    <n-modal class="resource-form-modal" v-model:show="voidShow" preset="card" title="作废销售记录" style="width: min(550px, calc(100vw - 32px))"
      ><n-input v-model:value="reason" type="textarea" placeholder="请填写作废原因" maxlength="500" /><n-space
        justify="end"
        class="mt-3"
        ><n-button @click="voidShow = false">取消</n-button
        ><n-button type="warning" :loading="busy" :disabled="!reason.trim()" @click="confirmVoid"
          >确认作废</n-button
        ></n-space
      ></n-modal
    >
  </n-space>
</template>
<script setup lang="ts">
import { formatLocalDateValue } from '@/utils/datetime';
import { resourceTablePage } from '../utils/tablePage';
import HPageDataTable from '@/components/data/table/PageDataTable.vue';
import { computed, h, ref, watch } from 'vue';
import { NButton, useDialog, useMessage } from 'naive-ui';
import { useAuthStore } from '@/store/modules/auth';
import { sales, importSales, voidSale } from '../api/business';
import { exportRows, readRows } from '../utils/files';
const props = defineProps<{ productId: string }>();
const emit = defineEmits<{ changed: [] }>();
const auth = useAuthStore(),
  dialog = useDialog(),
  message = useMessage();
const canManage = computed(() => auth.hasPermission('RESOURCE_ALL,RESOURCE_BUSINESS_MANAGE')),
  canExport = computed(() => auth.hasPermission('RESOURCE_ALL,RESOURCE_EXPORT'));
const rows = ref<any[]>([]),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  year = ref<number | null>(null),
  show = ref(false),
  form = ref<any>({}),
  amount = ref(''),
  editError = ref(''),
  input = ref<HTMLInputElement>(),
  voidShow = ref(false),
  voidId = ref(''),
  reason = ref('');
let generation = 0;
const tableRef = ref<InstanceType<typeof HPageDataTable>>();
const columns = [
  { title: '日期', key: 'soldOn', width: 120, render: (row: any) => formatLocalDateValue(row.soldOn) },
  { title: '业务单号', key: 'reference', width: 160 },
  { title: '数量', key: 'quantity', width: 80 },
  { title: '金额（元）', key: 'amountCents', width: 120, render: (r: any) => (r.amountCents / 100).toFixed(2) },
  { title: '地区', key: 'region', width: 140 },
  { title: '渠道', key: 'channel', width: 140 },
  {
    title: '状态/作废原因',
    key: 'voidReason',
    minWidth: 200,
    render: (r: any) => (r.voidReason ? `已作废：${r.voidReason}` : '有效'),
  },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    render: (r: any) =>
      canManage.value && !r.voidReason
        ? h(
            NButton,
            {
              text: true,
              type: 'warning',
              onClick: () => {
                voidId.value = r.id;
                reason.value = '';
                voidShow.value = true;
              },
            },
            () => '作废',
          )
        : null,
  },
];
function cents(value: any) {
  const text = String(value).trim();
  if (!/^\d+(\.\d{1,2})?$/.test(text)) throw new Error('金额须为非负数，最多两位小数');
  const [a, b = ''] = text.split('.');
  const v = Number(a) * 100 + Number(b.padEnd(2, '0'));
  if (!Number.isSafeInteger(v) || v > 100000000000) throw new Error('金额超出范围');
  return v;
}
async function fetchRows(params: any) {
  const own = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const r: any = await sales({ productId: params.productId, year: params.year || undefined });
    if (own === generation) rows.value = r.data || [];
    return resourceTablePage(r.data || [], params);
  } catch {
    if (own === generation) error.value = '销售记录读取失败';
    return resourceTablePage([], params);
  } finally {
    if (own === generation) loading.value = false;
  }
}
function load() { tableRef.value?.handleQuery(); }

function open() {
  form.value = { productId: props.productId, quantity: 1, region: '', channel: '', reference: '' };
  amount.value = '';
  editError.value = '';
  show.value = true;
}
async function save() {
  busy.value = true;
  editError.value = '';
  try {
    if (!form.value.soldOn) throw new Error('请选择销售日期');
    await importSales([{ ...form.value, amountCents: cents(amount.value) }]);
    show.value = false;
    await load();
    emit('changed');
    message.success('销售记录已保存');
  } catch (e: any) {
    editError.value = e.message || '保存失败';
  } finally {
    busy.value = false;
  }
}
async function template() {
  await exportRows(
    [
      {
        销售日期: '2026-01-01',
        业务单号: '请填写唯一业务单号',
        销售数量: 1,
        销售金额元: '0.00',
        销售地区: '请填写',
        销售渠道: '请填写',
      },
    ],
    '文创销售导入模板',
  );
}
async function importFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0];
  if (!file) return;
  try {
    const parsed = await readRows(file);
    const records = parsed.map((r) => ({
      productId: props.productId,
      soldOn: String(r['销售日期'] || '').trim(),
      reference: String(r['业务单号'] || '').trim(),
      quantity: Number(r['销售数量']),
      amountCents: cents(r['销售金额元']),
      region: String(r['销售地区'] || '').trim(),
      channel: String(r['销售渠道'] || '').trim(),
    }));
    if (!records.length) throw new Error('文件没有销售记录');
    dialog.warning({
      title: '导入销售记录',
      content: `共 ${records.length} 条记录，将归入当前产品。确认导入？`,
      positiveText: '确认导入',
      negativeText: '取消',
      onPositiveClick: async () => {
        busy.value = true;
        try {
          const r: any = await importSales(records);
          message.success(`新增 ${r.data.imported} 条，重复跳过 ${r.data.unchanged} 条`);
          await load();
          emit('changed');
        } finally {
          busy.value = false;
        }
      },
    });
  } catch (e: any) {
    error.value = e.message || '文件读取失败';
  } finally {
    if (input.value) input.value.value = '';
  }
}
async function exportSales() {
  await exportRows(
    rows.value.map((r) => ({
      销售日期: formatLocalDateValue(r.soldOn),
      业务单号: r.reference,
      销售数量: r.quantity,
      销售金额元: (r.amountCents / 100).toFixed(2),
      销售地区: r.region,
      销售渠道: r.channel,
      作废原因: r.voidReason || '',
    })),
    '文创销售记录',
  );
}
async function confirmVoid() {
  busy.value = true;
  try {
    await voidSale(voidId.value, reason.value);
    voidShow.value = false;
    await load();
    emit('changed');
  } finally {
    busy.value = false;
  }
}
watch(() => [props.productId, auth.identityRevision], () => {
  generation++;
  rows.value = [];
  error.value = '';
  show.value = false;
  voidShow.value = false;
}, { immediate: true });
</script>

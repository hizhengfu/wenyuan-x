<template>
  <div class="resource-records">
    <n-space v-if="planId" align="center"
      ><n-select
        v-model:value="selectedResources"
        multiple
        :options="resourceOptions"
        style="width: min(100%, 360px); min-width: 220px"
        placeholder="选择要领取或发证的收藏资源"
      /><n-button :loading="busy" :disabled="!selectedResources.length" @click="claim">领取所选资源</n-button></n-space
    >
    <n-space v-if="planId && canManage" align="center"
      ><UserSelect
        v-model:value="recipients"
        multiple
        style="width: min(100%, 360px); min-width: 220px"
        placeholder="选择领取人，可批量选择"
      /><n-button
        :loading="busy"
        type="primary"
        :disabled="!recipients.length || !selectedResources.length"
        @click="issue"
        >批量颁发证书</n-button
      ></n-space
    >
    <n-alert v-if="error" type="error">{{ error }}</n-alert
    ><h-page-data-table
      ref="tableRef"
      :key="`${planId || 'mine'}:${auth.identityRevision}`"
      class="resource-data-table"
      :title="planId ? '收藏证书' : '我的证书'"
      :bordered="false"
      :is-add="false"
      :is-export="false"
      :is-edit="false"
      :is-copy="false"
      :is-delete="false"
      :show-operate="false"
      :enable-drag="false"
      :initial-page-size="20"
      :pagination="{ showQuickJumper: false, pageSlot: 5 }"
      reset-page-on-size-change
      :fetch-data="fetchRows"
      :params="{ planId }"
      :columns="columns"
      :scroll-x="1000"
      column-config-key="resource-certificates"
    >
      <template #toolbar-extra>
        <n-button :loading="loading" @click="load">刷新</n-button
        ><n-button :disabled="!rows.length" @click="downloadAll">批量下载</n-button>
      </template>
    </h-page-data-table>

    <n-modal
      class="resource-form-modal"
      v-model:show="show"
      preset="card"
      title="数字收藏证书"
      style="width: min(840px, calc(100vw - 32px))"
      ><iframe
        v-if="selected"
        :srcdoc="html([selected])"
        title="数字收藏证书内容"
        style="border: 0; width: 100%; height: 550px"
        sandbox=""
      /><n-space justify="end"
        ><n-button @click="download(selected)">下载证书 HTML</n-button
        ><n-button @click="print(selected)">打印证书</n-button></n-space
      ></n-modal
    >
  </div>
</template>
<script setup lang="ts">
import { formatDateTime } from '@/utils/datetime';
import { resourceTablePage } from '../utils/tablePage';
import HPageDataTable from '@/components/data/table/PageDataTable.vue';
import { computed, h, ref, watch } from 'vue';
import { NButton, NSpace, useDialog, useMessage } from 'naive-ui';
import { useAuthStore } from '@/store/modules/auth';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import { businessAction, certificates } from '../api/business';
import { saveBlob } from '../utils/files';
import { getPreview } from '../api/resource';
const props = defineProps<{ planId?: string; resourceIds?: string[] }>();
const emit = defineEmits<{ issued: [] }>();
const auth = useAuthStore(),
  message = useMessage(),
  dialog = useDialog();
const canManage = computed(() => auth.hasPermission('RESOURCE_ALL,RESOURCE_BUSINESS_MANAGE'));
const recipients = ref<string[]>([]),
  rows = ref<any[]>([]),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  show = ref(false),
  selected = ref<any>();
let generation = 0;
const resourceOptions = ref<{ label: string; value: string }[]>([]),
  selectedResources = ref<string[]>([]);
let resourceGeneration = 0;
const tableRef = ref<InstanceType<typeof HPageDataTable>>();
const columns = [
  { title: '证书序列号', key: 'serial', minWidth: 210 },
  {
    title: '收藏资源',
    key: 'resourceId',
    width: 160,
    render: (r: any) => {
      try {
        return JSON.parse(r.snapshot).resources[0]?.name || '未登记';
      } catch {
        return '未登记';
      }
    },
  },
  { title: '领取人', key: 'recipientName', width: 130 },
  { title: '发证时间', key: 'issuedAt', columnType: 'dateTime', width: 185 },
  { title: '内容哈希', key: 'hash', minWidth: 200, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    render: (r: any) =>
      h(NSpace, {}, () => [
        h(
          NButton,
          {
            text: true,
            onClick: () => {
              selected.value = r;
              show.value = true;
            },
          },
          () => '查看证书',
        ),
        h(NButton, { text: true, onClick: () => download(r) }, () => '下载'),
      ]),
  },
];
async function fetchRows(params: any) {
  const own = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const r: any = await certificates(params.planId);
    if (own === generation) rows.value = r.data || [];
    return resourceTablePage(r.data || [], params);
  } catch {
    if (own === generation) {
      rows.value = [];
      error.value = '证书读取失败';
    }
    return resourceTablePage([], params);
  } finally {
    if (own === generation) loading.value = false;
  }
}
function load() {
  tableRef.value?.handleQuery();
}

function issue() {
  dialog.warning({
    title: '颁发数字收藏证书',
    content: `将向所选 ${recipients.value.length} 个账号，为 ${selectedResources.value.length} 件资源分别发证；已领取的资源不会重复发放。证书发出后计划内容不可改写。`,
    positiveText: '确认发证',
    negativeText: '取消',
    onPositiveClick: async () => {
      busy.value = true;
      try {
        await businessAction(props.planId!, 'certificates', {
          recipientIds: recipients.value,
          resourceIds: selectedResources.value,
        });
        message.success('发证完成');
        recipients.value = [];
        await load();
        emit('issued');
      } finally {
        busy.value = false;
      }
    },
  });
}
function escape(v: any) {
  return String(v ?? '').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!,
  );
}
function html(items: any[]) {
  return `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>数字收藏证书</title><style>body{font:16px system-ui;margin:0;color:#173d38}section{box-sizing:border-box;border:10px double #ad9360;padding:48px;margin:20px;page-break-after:always;overflow-wrap:anywhere}h1{text-align:center;font-size:30px;margin-bottom:40px}dt{margin-top:20px;color:#6d756b}dd{margin:6px 0}small{font-size:12px}@media print{section{margin:0;min-height:95vh}section:last-child{page-break-after:auto}}</style>${items
    .map((c) => {
      const s = JSON.parse(c.snapshot);
      return `<section><h1>数字收藏证书</h1><dl><dt>征集计划</dt><dd>${escape(s.planTitle)}</dd><dt>收藏者</dt><dd>${escape(c.recipientName)}</dd><dt>证书序列号</dt><dd>${escape(c.serial)}</dd><dt>收藏时间</dt><dd>${escape(formatDateTime(c.issuedAt))}</dd><dt>收藏资源</dt><dd>${s.resources.map((r: any) => `${escape(r.name)} · 版本 ${escape(r.version)}`).join('<br>')}</dd><dt>内容哈希（SHA-256）</dt><dd><small>${escape(c.hash)}</small></dd></dl></section>`;
    })
    .join('')}</html>`;
}
function download(row: any) {
  saveBlob(new Blob([html([row])], { type: 'text/html;charset=utf-8' }), `${row.serial}.html`);
}
function downloadAll() {
  saveBlob(new Blob([html(rows.value)], { type: 'text/html;charset=utf-8' }), '数字收藏证书集.html');
}
function print(row: any) {
  const frame = document.createElement('iframe');
  frame.style.cssText = 'position:fixed;width:0;height:0;border:0';
  frame.srcdoc = html([row]);
  frame.onload = () => {
    frame.contentWindow?.print();
    setTimeout(() => frame.remove(), 1000);
  };
  document.body.appendChild(frame);
}
async function claim() {
  busy.value = true;
  try {
    await businessAction(props.planId!, 'claim', { resourceIds: selectedResources.value });
    message.success('领取成功');
    await load();
    emit('issued');
  } finally {
    busy.value = false;
  }
}
watch(
  () => props.resourceIds,
  async (ids) => {
    const own = ++resourceGeneration;
    selectedResources.value = [...(ids || [])];
    resourceOptions.value = (ids || []).map((id, index) => ({ value: id, label: `收藏资源 ${index + 1}` }));
    const names = await Promise.allSettled(
      (ids || []).map(async (id) => {
        const r: any = await getPreview(id);
        return { value: id, label: r.data?.name || '收藏资源' };
      }),
    );
    if (own !== resourceGeneration) return;
    for (const result of names)
      if (result.status === 'fulfilled') {
        const index = resourceOptions.value.findIndex((v) => v.value === result.value.value);
        if (index >= 0) resourceOptions.value[index] = result.value;
      }
  },
  { immediate: true },
);
watch(
  () => [props.planId, auth.identityRevision],
  () => {
    generation++;
    error.value = '';
    recipients.value = [];
    rows.value = [];
    selected.value = undefined;
    show.value = false;
  },
  { immediate: true },
);
</script>

<template>
  <n-card class="h-full" content-style="padding:0">
    <h-page-data-table
      ref="refPageTable"
      :fetch-data="dispatchPage"
      :params="paramsForm"
      :columns="columns"
      @rowView="showDetail"
      :is-add="false"
      :is-edit="false"
      :is-delete="false"
    >
      <template #queryForm>
        <n-form-item label="通知ID" path="notifyId">
          <n-input v-model:value="paramsForm.notifyId" placeholder="精确匹配" clearable />
        </n-form-item>
        <n-form-item label="业务类型" path="bizType">
          <HDictSelect v-model:value="paramsForm.bizType" type="notify_dispatch_biz_type" clearable />
        </n-form-item>
        <n-form-item label="接收人" path="userName">
          <n-input v-model:value="paramsForm.userName" placeholder="用户名模糊" clearable />
        </n-form-item>
        <n-form-item label="通道" path="channel">
          <HDictSelect v-model:value="paramsForm.channel" type="notify_channel" clearable />
        </n-form-item>
        <n-form-item label="状态" path="dispatchStatus">
          <HDictSelect v-model:value="paramsForm.dispatchStatus" type="notify_dispatch_status" clearable />
        </n-form-item>
        <n-form-item label="时间范围" path="days">
          <n-select
            v-model:value="paramsForm.days"
            :options="[
              { label: '最近 1 天', value: 1 },
              { label: '最近 7 天', value: 7 },
              { label: '最近 30 天', value: 30 },
              { label: '最近 90 天', value: 90 },
              { label: '不限', value: 0 },
            ]"
          />
        </n-form-item>
      </template>
    </h-page-data-table>

    <h-page-form
      v-model:show="detailShow"
      :title="`投递详情：${detailTitle}`"
      :show-footer="false"
      label-placement="top"
    >
      <template v-if="detailData">
        <n-descriptions :column="2" size="small" bordered label-placement="left">
          <n-descriptions-item label="通知" :span="2">
            {{ detailData.notifyTitle || detailData.notifyId || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="通知ID" :span="2">
            {{ detailData.notifyId || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="业务类型">
            <HDictText type="notify_dispatch_biz_type" :value="detailData.bizType" />
          </n-descriptions-item>
          <n-descriptions-item label="业务ID">
            {{ detailData.bizId || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="通道">
            <HDictText type="notify_channel" :value="detailData.channel" />
          </n-descriptions-item>
          <n-descriptions-item label="状态">
            <HDictText type="notify_dispatch_status" :value="detailData.dispatchStatus" />
          </n-descriptions-item>
          <n-descriptions-item label="接收人">
            {{ detailData.userName || detailData.userId || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="投递地址">
            {{ detailData.target || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="模板编码">
            {{ detailData.templateCode || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="可重试">
            <n-tag :type="detailData.retryable ? 'info' : 'default'" size="small">
              {{ detailData.retryable ? '是' : '否' }}
            </n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="重试次数">
            {{ detailData.retryCount ?? 0 }}
          </n-descriptions-item>
          <n-descriptions-item label="下次重试">
            {{ detailData.nextRetryAt || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="创建时间">
             <HTime :time="detailData.createTime"></HTime>
          </n-descriptions-item>
          <n-descriptions-item label="更新时间">
            <HTime :time="detailData.updateTime"></HTime>
          </n-descriptions-item>
        </n-descriptions>

        <div v-if="detailData.message" class="dispatch-detail__section">
          <n-text depth="3">说明</n-text>
          <pre class="dispatch-detail__block">{{ detailData.message }}</pre>
        </div>
        <div v-if="detailTemplateParam" class="dispatch-detail__section">
          <n-text depth="3">模板参数</n-text>
          <pre class="dispatch-detail__block">{{ detailTemplateParam }}</pre>
        </div>
        <div v-if="detailData.content" class="dispatch-detail__section">
          <n-text depth="3">内容</n-text>
          <pre class="dispatch-detail__block">{{ detailData.content }}</pre>
        </div>
      </template>
      <n-empty v-else description="暂无详情" />
    </h-page-form>
  </n-card>
</template>

<script lang="ts" setup>
import { computed, h, ref } from 'vue';
import { NButton, NSpace } from 'naive-ui';
import { dispatchPage } from '../api/notify';
import { renderTimeCell } from '../components/timeCell';
import type { MessageNotifyDispatchRow } from '../typings/message';

/**
 * 全局投递记录页：跨通知的扁平 dispatch 查询。
 *
 * 与"通知详情里的 dispatches"不同点：
 *  - 默认按时间倒序，限制最近 30 天，避免一次扫全表
 *  - 行携带 notifyTitle / userName / bizType / bizId / 模板信息，运维直接定位投递来源
 *  - 不能编辑/删除，仅排错
 *
 * 复发失败行请到「通知列表」点对应通知行的"重发"按钮，逻辑收敛在 notify 维度。
 */

const refPageTable = ref();

const paramsForm = ref<{
  notifyId: string;
  bizType: string;
  userName: string;
  channel: string;
  dispatchStatus: string;
  days: number;
}>({
  notifyId: '',
  bizType: '',
  userName: '',
  channel: '',
  dispatchStatus: '',
  days: 30,
});

const detailShow = ref(false);
const detailData = ref<MessageNotifyDispatchRow | null>(null);
const detailTitle = computed(() => detailData.value?.notifyTitle || detailData.value?.notifyId || '-');
const detailTemplateParam = computed(() => formatMaybeJson(detailData.value?.templateParam));

function showDetail(row: MessageNotifyDispatchRow) {
  detailData.value = row;
  detailShow.value = true;
}

function formatMaybeJson(value?: string | null): string {
  if (!value) return '';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function renderRowActions(row: MessageNotifyDispatchRow) {
  return h(NSpace, { size: 8, justify: 'center' }, () => [
    h(NButton, { text: true, size: 'small', type: 'primary', onClick: () => showDetail(row) }, () => '详情'),
  ]);
}

const columns = [
  {
    key: 'notifyTitle',
    title: '通知',
    minWidth: 200,
    ellipsis: { tooltip: true },
    columnType: 'title',
    render(row: MessageNotifyDispatchRow) {
      return row.notifyTitle || row.notifyId || '-';
    },
  },
  {
    key: 'bizType',
    title: '业务类型',
    width: 150,
    dictType: 'notify_dispatch_biz_type',
    ellipsis: { tooltip: true },
  },
  {
    key: 'bizId',
    title: '业务ID',
    width: 160,
    ellipsis: { tooltip: true },
    render(row: MessageNotifyDispatchRow) {
      return row.bizId || '-';
    },
  },
  {
    key: 'userName',
    title: '接收人',
    width: 140,
    ellipsis: { tooltip: true },
    render(row: MessageNotifyDispatchRow) {
      return row.userName || row.userId || '-';
    },
  },
  { key: 'channel', title: '通道', width: 110, dictType: 'notify_channel' },
  { key: 'target', title: '投递地址', width: 200, ellipsis: { tooltip: true } },
  {
    key: 'templateCode',
    title: '模板',
    width: 150,
    ellipsis: { tooltip: true },
    render(row: MessageNotifyDispatchRow) {
      return row.templateCode || '-';
    },
  },
  {
    key: 'dispatchStatus',
    title: '状态',
    width: 90,
    align: 'center',
    dictType: 'notify_dispatch_status',
  },
  { key: 'retryCount', title: '重试', width: 70, align: 'right' },
  {
    key: 'message',
    title: '说明',
    minWidth: 220,
    ellipsis: { tooltip: { maxWidth: 300 } },
  },
  {
    key: 'updateTime',
    title: '时间',
    width: 200,
    render: (row: MessageNotifyDispatchRow) => renderTimeCell(row.updateTime || row.createTime),
  },
];
</script>

<style scoped>
.dispatch-detail__section {
  margin-top: 14px;
}

.dispatch-detail__block {
  box-sizing: border-box;
  width: 100%;
  max-height: 260px;
  margin: 6px 0 0;
  padding: 10px 12px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid var(--n-border-color);
  border-radius: 4px;
  background: var(--n-color);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
}
</style>

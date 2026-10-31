<template>
  <div class="resource-records">
    <n-alert v-if="error" type="error">{{ error }}</n-alert>
    <h-page-data-table ref="tableRef" :key="`${kind}:${parentId || ''}:${auth.identityRevision}`" class="resource-data-table" :title="KIND_NAMES[kind]" :bordered="false"
      :is-add="false" :is-export="false" :is-edit="false" :is-copy="false" :is-delete="false"
      :show-operate="false" :enable-drag="false" :initial-page-size="20" :pagination="{ showQuickJumper: false, pageSlot: 5 }" reset-page-on-size-change
      :fetch-data="fetchRows" :params="queryParams" :columns="columns" :scroll-x="900"
      :column-config-key="`resource-business-${kind}`" :always-visible-column-keys="['title', 'actions']"
     :query-configurable="false" :query-form-storage-key="`resource-business-${kind}`">
      <template #toolbar-extra>
        <n-button v-if="canExport" :loading="busy" @click="exportList"><template #icon><n-icon><IconMdiDownload /></n-icon></template>导出</n-button>
        <n-button v-if="canManage" type="primary" @click="edit()"><template #icon><n-icon><IconMdiPlus /></n-icon></template>新增{{ KIND_NAMES[kind] }}</n-button>
      </template>
      <template #queryForm>
        <n-form-item label="名称" path="keyword"><n-input v-model:value="keyword" placeholder="搜索名称" clearable @keydown.enter="load(1)" /></n-form-item>
        <n-form-item label="审核" path="flowStatus"><n-select v-model:value="flowStatus" :options="statuses" clearable placeholder="全部状态" style="width:140px" @update:value="load(1)" /></n-form-item>
        <n-form-item v-if="kind === 'CAPTURE'" label="来源" path="source"><n-input v-model:value="source" placeholder="采集来源" clearable @keydown.enter="load(1)" /></n-form-item>
        <n-form-item v-if="kind === 'CAPTURE'" label="类型" path="resourceType"><n-select v-model:value="resourceType" :options="mediaTypes" placeholder="全部类型" clearable style="width:140px" /></n-form-item>
        <n-form-item v-if="kind === 'CAPTURE'" label="时间" path="from"><n-date-picker v-model:formatted-value="dateRange" value-format="yyyy-MM-dd HH:mm:ss" type="datetimerange" clearable /></n-form-item>
      </template>
    </h-page-data-table>
  </div>
  <n-modal class="resource-form-modal"
    v-model:show="editing"
    preset="card"
    :title="`${form.id ? '编辑' : '新增'}${KIND_NAMES[kind]}`"
    style="width: min(900px, calc(100vw - 32px))"
  >
    <n-alert v-if="form.id && form.flowStatus === 'PASS'" type="warning" class="mb-3"
      >修改后将回到草稿并停止公开展示，需要重新审核。</n-alert
    >
    <n-form label-placement="top"
      ><n-grid cols="1 s:2" responsive="screen" item-responsive :x-gap="16">
        <n-form-item-gi label="名称" required><n-input v-model:value="form.title" maxlength="200" /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'PRODUCT'" label="产品类别" required
          ><n-select v-model:value="form.data.category" :options="PRODUCT_CATEGORIES"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'SCENE'" label="地点"
          ><n-input v-model:value="form.data.location"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'CAPTURE' || kind === 'PLAN'" label="资料来源" :required="kind === 'CAPTURE'"
          ><n-input v-model:value="form.data.source"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'CAPTURE'" label="采集时间" required
          ><n-date-picker
            v-model:formatted-value="form.data.capturedAt"
            value-format="yyyy-MM-dd HH:mm:ss"
            type="datetime"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'CAPTURE'" label="质量评分（1至5）"
          ><n-rate v-model:value="form.data.quality"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'PLAN'" label="领取开始时间" required
          ><n-date-picker
            v-model:formatted-value="form.data.startsAt"
            value-format="yyyy-MM-dd HH:mm:ss"
            type="datetime"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'PLAN'" label="领取结束时间" required
          ><n-date-picker v-model:formatted-value="form.data.endsAt" value-format="yyyy-MM-dd HH:mm:ss" type="datetime"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'PLAN'" label="发证限额" required
          ><n-input-number v-model:value="form.data.capacity" :min="1" :max="1000000" :precision="0"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'LICENSE' || kind === 'PRODUCT'" label="厂商名称" :required="kind === 'LICENSE'"
          ><n-input v-model:value="form.data.vendor"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'LICENSE'" label="授权/知识产权编号" required
          ><n-input v-model:value="form.data.licenseNo"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'LICENSE'" label="权利范围" required
          ><n-input v-model:value="form.data.rights" type="textarea"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'LICENSE'" label="有效期起" required
          ><n-date-picker v-model:formatted-value="form.data.validFrom" value-format="yyyy-MM-dd"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'LICENSE'" label="有效期止" required
          ><n-date-picker v-model:formatted-value="form.data.validTo" value-format="yyyy-MM-dd"
        /></n-form-item-gi>
        <n-form-item-gi span="1 s:2" label="关联数字资源" :required="['CAPTURE', 'PLAN', 'PRODUCT'].includes(kind)"
          ><ResourcePicker v-model="form.data.resourceIds"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'CAPTURE'" span="1 s:2" label="标记与处理说明"
          ><n-input
            v-model:value="form.data.annotation"
            type="textarea"
            placeholder="记录时间点、画面区域、天气、处理结果或质量问题"
        /></n-form-item-gi>
        <n-form-item-gi v-if="kind === 'PLAN'" span="1 s:2" label="创作背景与研究说明"
          ><n-input v-model:value="form.data.background" type="textarea"
        /></n-form-item-gi>
        <n-form-item-gi span="1 s:2" label="说明"
          ><n-input v-model:value="form.data.description" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }"
        /></n-form-item-gi> </n-grid
    ></n-form>
    <n-alert v-if="editError" type="error" class="mb-3">{{ editError }}</n-alert>
    <n-space justify="end"
      ><n-button @click="editing = false">取消</n-button
      ><n-button type="primary" :loading="busy" @click="save">保存草稿</n-button></n-space
    >
  </n-modal>
  <n-drawer class="resource-detail" v-model:show="detailShow" :width="'min(1000px,100vw)'"
    ><n-drawer-content :title="selected?.title" closable>
      <n-space v-if="selected" vertical :size="20">
        <n-descriptions :column="2" bordered
          ><n-descriptions-item label="业务类型">{{ KIND_NAMES[selected.kind] }}</n-descriptions-item
          ><n-descriptions-item label="发布状态">{{
            selected.published ? '已公开发布' : '未公开发布'
          }}</n-descriptions-item
          ><n-descriptions-item v-for="item in dataFields" :key="item.key" :label="item.label">{{
            displayDataField(item.key, selected.data[item.key])
          }}</n-descriptions-item></n-descriptions
        >
        <n-space
          ><n-button
            v-for="id in selected.data.resourceIds"
            :key="id"
            @click="
              previewId = id;
              previewShow = true;
            "
            >预览关联资源 {{ selected.data.resourceIds.indexOf(id) + 1 }}</n-button
          ></n-space
        >
        <ResourceApprovalProgress
          :business-id="selected.id"
          business-type="RESOURCE_BUSINESS_REVIEW"
          :fetch-flow="businessFlow"
          :revision="selected.revision"
        />
        <slot name="detail" :record="selected" :refresh="refreshSelected" />
        <ResourcePublicationPanel
          v-if="canManage && selected.flowStatus === 'PASS' && selected.published && kind !== 'LICENSE'"
          :subject-id="selected.id"
          subject-type="BUSINESS"
        />
        <n-collapse
          ><n-collapse-item title="业务操作记录"
            ><n-timeline
              ><n-timeline-item
                v-for="event in events"
                :key="event.id"
                :title="eventLabels[event.action] || event.action"
                :content="event.actor"
                :time="formatDateTime(event.createTime)" /></n-timeline></n-collapse-item
        ></n-collapse>
      </n-space> </n-drawer-content
  ></n-drawer>
  <ResourcePreview v-if="previewShow" v-model:show="previewShow" :resource-id="previewId" />
</template>
<script setup lang="ts">
import HPageDataTable from '@/components/data/table/PageDataTable.vue';
import { computed, defineAsyncComponent, h, ref, watch } from 'vue';
import { NButton, NSpace, NTag, useDialog, useMessage } from 'naive-ui';
import { useAuthStore } from '@/store/modules/auth';
import {
  businessList,
  businessGet,
  businessSave,
  businessAction,
  businessFlow,
  businessEvents,
  KIND_NAMES,
  PRODUCT_CATEGORIES,
} from '../api/business';
import { exportRows } from '../utils/files';
import { formatDateTime, formatLocalDateValue } from '@/utils/datetime';
import ResourcePicker from './ResourcePicker.vue';
import ResourceApprovalProgress from './ResourceApprovalProgress.vue';
import ResourcePublicationPanel from './ResourcePublicationPanel.vue';
const ResourcePreview = defineAsyncComponent(async () => (await import('./ResourcePreview.vue')).default);
const props = defineProps<{ kind: string; parentId?: string }>();
const emit = defineEmits<{ select: [any]; changed: [] }>();
const auth = useAuthStore();
const dialog = useDialog();
const message = useMessage();
const keyword = ref(''),
  flowStatus = ref<string | null>(null),
  source = ref(''),
  resourceType = ref<string | null>(null),
  dateRange = ref<[string, string] | null>(null),
  busy = ref(false),
  error = ref(''),
  editing = ref(false),
  form = ref<any>({ data: {} }),
  editError = ref(''),
  selected = ref<any>(),
  detailShow = ref(false),
  events = ref<any[]>([]),
  previewShow = ref(false),
  previewId = ref('');
let generation = 0;
const canManage = computed(() => auth.hasPermission('RESOURCE_ALL,RESOURCE_BUSINESS_MANAGE'));
const canExport = computed(() => auth.hasPermission('RESOURCE_ALL,RESOURCE_EXPORT'));
const statuses = [
  { label: '草稿', value: 'DRAFT' },
  { label: '审核中', value: 'AUDIT' },
  { label: '已通过', value: 'PASS' },
  { label: '已驳回', value: 'REJECT' },
  { label: '已撤销', value: 'CANCEL' },
];
const mediaTypes = [
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: 'VR全景', value: 'PANORAMA' },
];
const labels: Record<string, string> = {
  description: '说明',
  location: '地点',
  capturedAt: '采集时间',
  source: '来源',
  annotation: '标记与处理说明',
  quality: '质量评分',
  category: '类别',
  background: '创作背景',
  startsAt: '领取开始',
  endsAt: '领取结束',
  capacity: '发证限额',
  vendor: '厂商',
  licenseNo: '授权编号',
  rights: '权利范围',
  validFrom: '有效期起',
  validTo: '有效期止',
};
function displayDataField(key: string, value: any) {
  if (['capturedAt', 'startsAt', 'endsAt'].includes(key)) return formatDateTime(value);
  if (['validFrom', 'validTo'].includes(key)) return formatLocalDateValue(value);
  if (key === 'category') return PRODUCT_CATEGORIES.find((item) => item.value === value)?.label || value || '未登记';
  return value ?? '未登记';
}
const dataFields = computed(() =>
  Object.entries(labels)
    .filter(
      ([key]) =>
        selected.value?.data[key] !== undefined &&
        selected.value?.data[key] !== null &&
        selected.value?.data[key] !== '',
    )
    .map(([key, label]) => ({ key, label })),
);
const eventLabels: Record<string, string> = {
  CREATE: '创建',
  UPDATE: '修改',
  SUBMIT: '提交审核',
  PUBLISH: '公开发布',
  UNPUBLISH: '停止发布',
  ISSUE_CERTIFICATES: '颁发证书',
  IMPORT_SALES: '导入销售',
  VOID_SALE: '作废销售',
};
function filters() {
  return {
    kind: props.kind,
    parentId: props.parentId,
    keyword: keyword.value,
    flowStatus: flowStatus.value,
    source: source.value,
    resourceType: resourceType.value,
    from: dateRange.value?.[0],
    to: dateRange.value?.[1],
  };
}
const queryParams = computed(filters);
async function fetchRows(params: any) {
  const own = ++generation;
  error.value = '';
  try {
    return await businessList(params);
  } catch {
    if (own === generation) error.value = '业务记录读取失败，请重试';
    return { data: { list: [], total: 0 } };
  }
}
function load(next?: number) {
  if (next === 1) tableRef.value?.handleQuery();
  else tableRef.value?.handleRefresh();
}
watch(
  () => [props.kind, props.parentId, auth.identityRevision],
  () => {
    detailShow.value = false;
    selected.value = undefined;
    generation++;
    error.value = '';
  },
  { immediate: true },
);
function edit(row?: any) {
  editError.value = '';
  form.value = row
    ? JSON.parse(JSON.stringify(row))
    : {
        kind: props.kind,
        title: '',
        parentId: props.parentId,
        revision: 0,
        data: {
          resourceIds: [],
          description: '',
          ...(props.kind === 'PLAN' ? { capacity: 100 } : {}),
          ...(props.kind === 'CAPTURE' ? { quality: 3 } : {}),
        },
      };
  editing.value = true;
}
async function save() {
  busy.value = true;
  editError.value = '';
  try {
    if (!form.value.title.trim()) throw new Error('请填写名称');
    await businessSave(form.value);
    editing.value = false;
    message.success('草稿已保存');
    await load();
    emit('changed');
  } catch (e: any) {
    editError.value = e.message || '保存失败';
  } finally {
    busy.value = false;
  }
}
async function open(row: any) {
  selected.value = row;
  events.value = [];
  detailShow.value = true;
  emit('select', row);
  const id = row.id;
  try {
    const [r, e]: any[] = await Promise.all([businessGet(id), businessEvents(id)]);
    if (selected.value?.id !== id) return;
    selected.value = r.data;
    events.value = e.data || [];
  } catch {
    message.error('详情读取失败');
  }
}
async function refreshSelected() {
  if (selected.value) await open(selected.value);
  await load();
  emit('changed');
}
function action(row: any, type: string) {
  const name =
    type === 'submit' ? '提交审核' : type === 'cancel' ? '撤销审核' : row.published ? '停止公开发布' : '公开发布';
  dialog.warning({
    title: name,
    content:
      type === 'publish' && !row.published
        ? '公开发布后，关联资料可通过公开内容链接访问。请确认所有资源都允许公开。'
        : `确定${name}「${row.title}」？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      await businessAction(
        row.id,
        type,
        type === 'publish' ? { revision: row.revision, published: !row.published } : undefined,
      );
      await load();
      emit('changed');
    },
  });
}
const tableRef = ref<InstanceType<typeof HPageDataTable>>();
const columns = computed(() => [
  {
    title: '名称',
    key: 'title',
    minWidth: 200,
    render: (r: any) => h(NButton, { text: true, class: 'resource-table-heading', onClick: () => open(r) }, () => r.title),
  },
  {
    title: '审核状态',
    key: 'flowStatus',
    width: 110,
    render: (r: any) =>
      h(
        NTag,
        { size: 'small', bordered: false, round: true, type: r.flowStatus === 'PASS' ? 'success' : r.flowStatus === 'AUDIT' ? 'info' : 'default' },
        () => statuses.find((v) => v.value === r.flowStatus)?.label || r.flowStatus,
      ),
  },
  { title: '公开状态', key: 'published', width: 110, render: (r: any) => (r.published ? '已发布' : '未发布') },
  {
    title: props.kind === 'CAPTURE' ? '采集时间' : '更新时间',
    key: 'updateTime',
    width: 180,
    render: (r: any) => formatDateTime(props.kind === 'CAPTURE' ? r.data.capturedAt : r.updateTime || r.createTime),
  },
  {
    title: '操作',
    key: 'actions',
    width: 290,
    render: (r: any) =>
      h(NSpace, { size: 10 }, () => [
        h(NButton, { size: 'small', text: true, onClick: () => open(r) }, () => '审批进度'),
        canManage.value
          ? h(
              NButton,
              {
                size: 'small',
                text: true,
                disabled: r.flowStatus === 'AUDIT' || r.issuedCount > 0,
                title:
                  r.issuedCount > 0 ? '已发证计划不可改写' : r.flowStatus === 'AUDIT' ? '审核中不可修改' : undefined,
                onClick: () => edit(r),
              },
              () => '编辑',
            )
          : null,
        canManage.value && ['DRAFT', 'REJECT', 'CANCEL'].includes(r.flowStatus)
          ? h(
              NButton,
              { size: 'small', text: true, type: 'primary', onClick: () => action(r, 'submit') },
              () => '提交审核',
            )
          : null,
        canManage.value && r.flowStatus === 'AUDIT'
          ? h(NButton, { size: 'small', text: true, onClick: () => action(r, 'cancel') }, () => '撤销审核')
          : null,
        canManage.value && r.flowStatus === 'PASS'
          ? h(
              NButton,
              {
                size: 'small',
                text: true,
                type: r.published ? 'default' : 'primary',
                onClick: () => action(r, 'publish'),
              },
              () => (r.published ? '停止发布' : '公开发布'),
            )
          : null,
      ]),
  },
]);
async function exportList() {
  busy.value = true;
  try {
    const r: any = await businessList({ ...filters(), pageSize: 0 });
    await exportRows(
      (r.data?.list || []).map((v: any) => ({
        名称: v.title,
        类型: KIND_NAMES[v.kind],
        审核状态: statuses.find((item) => item.value === v.flowStatus)?.label || v.flowStatus,
        公开状态: v.published ? '已发布' : '未发布',
        采集时间: formatDateTime(v.data.capturedAt),
        来源: v.data.source,
        标记: v.data.annotation,
        质量评分: v.data.quality,
        说明: v.data.description,
      })),
      KIND_NAMES[props.kind],
    );
  } finally {
    busy.value = false;
  }
}
defineExpose({ load, refreshSelected });
</script>

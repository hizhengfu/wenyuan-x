<template>
  <n-modal
    v-model:show="visible"
    preset="card"
    title="资源权限申请"
    :style="{ width: '720px', maxWidth: 'calc(100vw - 32px)' }"
    :bordered="false"
    :mask-closable="!loading"
  >
    <div class="application-body">
      <n-alert type="info" :show-icon="false" class="mb-4">
        申请审批通过时，会按资源类型和文物编号匹配并固化当时的资源快照；后续新增资源不会自动继承。
      </n-alert>
      <n-form :model="form" label-placement="left" label-width="96" :disabled="loading">
        <n-form-item label="资源类型">
          <n-select v-model:value="form.resourceType" :options="RESOURCE_TYPE_OPTIONS" clearable placeholder="全部类型" />
        </n-form-item>
        <n-form-item label="文物编号">
          <n-input v-model:value="form.culturalRelicNo" clearable placeholder="精确匹配文物编号" />
        </n-form-item>
        <n-form-item v-if="!form.resourceType && !form.culturalRelicNo" label="全部资源">
          <n-checkbox v-model:checked="form.confirmAll">确认申请当前全部匹配资源</n-checkbox>
        </n-form-item>
        <n-form-item label="申请操作" required>
          <n-checkbox-group v-model:value="form.operationTypes">
            <n-space wrap item-style="display: flex;">
              <n-checkbox
                v-for="option in RESOURCE_GRANT_OPERATION_OPTIONS"
                :key="option.value"
                :value="option.value"
                :label="option.label"
              />
            </n-space>
          </n-checkbox-group>
        </n-form-item>
        <n-form-item label="权限等级" required>
          <n-select v-model:value="form.permissionLevel" :options="RESOURCE_PERMISSION_LEVEL_OPTIONS" />
        </n-form-item>
        <n-form-item label="使用场景" required>
          <n-select v-model:value="form.usageScene" :options="usageSceneOptions" />
        </n-form-item>
        <n-form-item label="有效期">
          <n-date-picker v-model:value="form.validRange" type="datetimerange" clearable style="width: 100%" />
        </n-form-item>
        <n-form-item label="申请原因" required>
          <n-input
            v-model:value="form.reason"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 6 }"
            placeholder="说明使用目的、范围和依据"
          />
        </n-form-item>
      </n-form>

      <n-divider title-placement="left">最近申请</n-divider>
      <n-data-table
        size="small"
        :columns="columns"
        :data="applications"
        :pagination="false"
        :max-height="230"
      />
    </div>
    <template #footer>
      <n-space justify="end">
        <n-button :disabled="loading" @click="visible = false">取消</n-button>
        <n-button type="primary" :loading="loading" @click="saveAndSubmit">保存并提交审批</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref, watch } from 'vue';
import { NTag, useMessage, type DataTableColumns } from 'naive-ui';
import {
  accessGrantList,
  saveAccessGrant,
  submitAccessGrant,
  RESOURCE_GRANT_OPERATION_OPTIONS,
  RESOURCE_PERMISSION_LEVEL_OPTIONS,
  RESOURCE_TYPE_OPTIONS,
} from '../api/resource';

const props = defineProps<{ show: boolean }>();
const emit = defineEmits<{ (e: 'update:show', value: boolean): void }>();
const message = useMessage();
const loading = ref(false);
const applications = ref<any[]>([]);

const visible = computed({
  get: () => props.show,
  set: (value: boolean) => emit('update:show', value),
});
const usageSceneOptions = [
  { label: '学术研究', value: 'ACADEMIC_RESEARCH' },
  { label: '展览展示', value: 'EXHIBITION' },
  { label: '公众教育', value: 'PUBLIC_EDUCATION' },
  { label: '保护修复', value: 'CONSERVATION_REPAIR' },
  { label: '其他', value: 'OTHER' },
];
const defaultForm = () => ({
  resourceType: null as string | null,
  culturalRelicNo: '',
  confirmAll: false,
  operationTypes: ['VIEW_METADATA', 'PREVIEW_LOW'] as string[],
  permissionLevel: 'LEVEL_1',
  usageScene: 'ACADEMIC_RESEARCH',
  validRange: null as [number, number] | null,
  reason: '',
});
const form = reactive(defaultForm());

watch(
  () => props.show,
  async (show) => {
    if (!show) return;
    Object.assign(form, defaultForm());
    await loadApplications();
  },
);

function validate() {
  if (!form.resourceType && !form.culturalRelicNo.trim() && !form.confirmAll) {
    message.warning('请选择资源类型、填写文物编号，或明确确认申请全部资源');
    return false;
  }
  if (!form.operationTypes.length) {
    message.warning('请至少选择一项申请操作');
    return false;
  }
  if (!form.reason.trim()) {
    message.warning('请填写申请原因');
    return false;
  }
  return true;
}

async function saveAndSubmit() {
  if (!validate()) return;
  loading.value = true;
  try {
    const resp: any = await saveAccessGrant({
      permissionLevel: form.permissionLevel,
      resourceType: form.resourceType || null,
      culturalRelicNo: form.culturalRelicNo.trim() || null,
      confirmAll: form.confirmAll,
      operationTypes: form.operationTypes.join(','),
      usageScene: form.usageScene,
      validFromMillis: form.validRange?.[0] || null,
      validUntilMillis: form.validRange?.[1] || null,
      reason: form.reason.trim(),
    });
    const id = resp?.data;
    if (!id) throw new Error('权限申请保存后未返回ID');
    await submitAccessGrant(id);
    message.success('权限申请已提交，等待审批');
    Object.assign(form, defaultForm());
    await loadApplications();
  } finally {
    loading.value = false;
  }
}

async function loadApplications() {
  const resp: any = await accessGrantList({ pageNum: 1, pageSize: 20 });
  const page = resp?.data || {};
  applications.value = page.list || page.data || [];
}

const flowLabels: Record<string, string> = {
  DRAFT: '草稿',
  AUDIT: '审批中',
  PASS: '已通过',
  REJECT: '已驳回',
  CANCEL: '已撤销',
};
const columns: DataTableColumns<any> = [
  {
    title: '申请时间',
    key: 'createTime',
    width: 165,
    render: (row) => row.createTime ? String(row.createTime).replace('T', ' ') : '-',
  },
  { title: '资源类型', key: 'resourceType', width: 100, render: (row) => row.resourceType || '全部' },
  { title: '文物编号', key: 'culturalRelicNo', ellipsis: { tooltip: true }, render: (row) => row.culturalRelicNo || '全部' },
  {
    title: '状态',
    key: 'flowStatus',
    width: 90,
    render: (row) => h(NTag, { size: 'small', type: row.flowStatus === 'PASS' ? 'success' : 'default' }, {
      default: () => flowLabels[row.flowStatus] || row.flowStatus || '-',
    }),
  },
];
</script>

<style scoped>
.application-body {
  max-height: min(68vh, 680px);
  overflow-y: auto;
  padding-right: 4px;
}
</style>

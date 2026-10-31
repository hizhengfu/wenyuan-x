<template>
  <HDialogForm
    v-model:show="dialogVisible"
    :title="title || '配置数据权限'"
    width="640px"
    size="small"
    :handle-submit="save"
    :disabled="!loaded || loading"
    submit-text="保存数据权限"
  >
    <n-spin :show="loading">
      <n-alert class="mb-16px" type="info" :bordered="false">
        数据类型决定该角色可以访问哪些业务数据，实际结果还会同时受到功能权限和角色数据范围限制。
      </n-alert>
      <n-form label-placement="left" label-width="90">
        <n-form-item label="所属应用" required>
          <h-application-select
            :value="applicationId"
            placeholder="请选择应用"
            @update:value="requestApplicationChange"
          />
        </n-form-item>
        <n-form-item label="数据类型">
          <h-dict-select
            v-model:value="selectedCodes"
            type="sys_role_data_permission"
            multiple
            return-array
            clearable
            filterable
            placeholder="请选择允许访问的数据类型"
          />
        </n-form-item>
      </n-form>
      <n-text depth="3">
        数据类型由各业务模块注册；未选择时，该角色不能访问带数据类型标记的数据，但仍可按数据范围访问未标记数据。
      </n-text>
    </n-spin>
  </HDialogForm>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
import { useDialog } from 'naive-ui';
import HApplicationSelect from '@/modules/sys/components/form/field/ApplicationSelect.vue';
import {
  roleDataPermissions,
  updateRoleDataPermissions,
  type RoleDataPermissionAssociation,
} from '@/modules/sys/api/role';

const props = defineProps({
  show: { type: Boolean, default: false },
  title: { type: String, default: '' },
  applicationId: { type: String, default: '1' },
  roleId: { type: String, required: true },
});

const emit = defineEmits(['update:show']);
const confirmDialog = useDialog();
const applicationId = ref('1');
const selectedCodes = ref<string[]>([]);
const originalCodes = ref<string[]>([]);
const version = ref('');
const loading = ref(false);
const loaded = ref(false);
let loadSequence = 0;

const normalizeCodes = (codes: string[] = []) => [...new Set(codes.map((code) => code.trim()).filter(Boolean))].sort();
const dirty = computed(
  () => normalizeCodes(selectedCodes.value).join('\u0000') !== normalizeCodes(originalCodes.value).join('\u0000'),
);

const dialogVisible = computed({
  get: () => props.show,
  set: (value: boolean) => {
    if (value) emit('update:show', true);
    else requestClose();
  },
});

async function load() {
  if (!props.roleId || !applicationId.value) return;
  const sequence = ++loadSequence;
  loading.value = true;
  loaded.value = false;
  selectedCodes.value = [];
  originalCodes.value = [];
  version.value = '';
  try {
    const response = await roleDataPermissions(props.roleId, applicationId.value);
    if (sequence !== loadSequence) return;
    const association = response.data as RoleDataPermissionAssociation;
    const codes = normalizeCodes(association.dataPermissionCodes);
    selectedCodes.value = codes;
    originalCodes.value = [...codes];
    version.value = association.version;
    loaded.value = true;
  } catch {
    // 请求层已展示错误；保持表单禁用，避免把旧应用的选择保存到当前应用。
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
}

async function save() {
  if (!loaded.value) throw new Error('数据权限尚未加载完成');
  const response = await updateRoleDataPermissions(props.roleId, {
    applicationId: applicationId.value,
    dataPermissionCodes: normalizeCodes(selectedCodes.value),
    expectedVersion: version.value,
  });
  const association = response.data as RoleDataPermissionAssociation;
  const codes = normalizeCodes(association.dataPermissionCodes);
  selectedCodes.value = codes;
  originalCodes.value = [...codes];
  version.value = association.version;
  return response;
}

function switchApplication(value: string) {
  applicationId.value = value;
  load();
}

function requestApplicationChange(value?: string | null) {
  if (!value || value === applicationId.value) return;
  if (!dirty.value) {
    switchApplication(value);
    return;
  }
  confirmDialog.warning({
    title: '切换应用？',
    content: '当前数据权限尚未保存，切换应用会丢弃这些修改。',
    positiveText: '放弃修改并切换',
    negativeText: '继续编辑',
    onPositiveClick: () => switchApplication(value),
  });
}

function requestClose() {
  if (!dirty.value) {
    emit('update:show', false);
    return;
  }
  confirmDialog.warning({
    title: '放弃未保存的数据权限修改？',
    content: '关闭后，本次数据类型选择不会保存。',
    positiveText: '放弃并关闭',
    negativeText: '继续编辑',
    onPositiveClick: () => emit('update:show', false),
  });
}

watch(
  () => [props.show, props.roleId, props.applicationId] as const,
  ([show]) => {
    if (!show) return;
    applicationId.value = props.applicationId || '1';
    load();
  },
  { immediate: true },
);
</script>

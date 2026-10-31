<template>
  <div>
    <div style="display: flex; justify-content: flex-end; margin-bottom: 8px">
      <NButton v-permission="manageRoles" size="small" type="primary" @click="onLogAdd">新增日志</NButton>
    </div>
    <NDataTable :columns="columns" :data="rows" :bordered="false" size="small" :row-key="(r: RestorationProcessVo) => r.id || ''" />
    <HPageForm
      v-if="formShow"
      :key="formKey"
      v-model:show="formShow"
      :title="title"
      :model="editVo"
      :rules="mode === 'delete' ? {} : rules"
      :auto-reload="false"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="100"
      width="640px"
    >
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NAlert v-if="loading" type="info">正在核对原馆、日志及实施依据…</NAlert>
      <template v-if="mode === 'delete'">
        <NAlert v-if="editVo.id" type="warning">确认删除日志 {{ editVo.stageName }}（{{ formatDateTime(editVo.startTime) }}）。</NAlert>
      </template>
      <template v-else>
        <NFormItem label="日志事项" path="stageName">
          <NInput v-model:value="editVo.stageName" />
        </NFormItem>
        <NFormItem label="开始时间" path="startTime">
          <NDatePicker v-model:value="editVo.startTime" type="datetime" clearable />
        </NFormItem>
        <NFormItem label="结束时间" path="endTime">
          <NDatePicker v-model:value="editVo.endTime" type="datetime" clearable />
        </NFormItem>
        <NFormItem label="负责人" path="operatorId">
          <RestorerPicker :org-id="editVo.orgId" :disabled="!editVo.orgId || !handleSubmit" v-model:value="editVo.operatorId" style="width: 360px" />
        </NFormItem>
        <NFormItem label="日志内容" path="description">
          <NInput v-model:value="editVo.description" type="textarea" :autosize="{ minRows: 4, maxRows: 12 }" />
        </NFormItem>
      </template>
    </HPageForm>
  </div>
</template>

<script setup lang="ts">
import { computed, h, resolveDirective, watch, withDirectives } from 'vue';
import { NAlert, NButton, NDataTable, NDatePicker, NFormItem, NInput } from 'naive-ui';
import { useProcessWindow } from '../composables/useProcessWindow';
import RestorerPicker from './RestorerPicker.vue';
import type { RestorationProcessVo } from '../typings';
import { RESTORATION_PROCESS_WRITE_CODES } from '../utils/restorationPermissions';
import { formatDateTime } from '@/utils/datetime';
import { hoursText, nextProcessSort, personNames, processHours, processPeople, sortProcessesByTime } from '../utils/restorationReport';

const props = defineProps<{ restorationId: string; processes: RestorationProcessVo[] }>();
const emit = defineEmits<{ (e: 'changed'): void }>();
// 与后端工序写接口一致（RestorationComplianceAccess.actor() / @PreAuthorize）：5 个码，含 ADMIN / COLLECTION_ADMIN / RESTORATION_ADMIN。
const manageRoles = [...RESTORATION_PROCESS_WRITE_CODES];
const permission = resolveDirective('permission');
const { formShow, loading, error, title, editVo, mode, formKey, handleSubmit, onAdd, onEdit, onDelete } = useProcessWindow(() => { setTimeout(() => emit('changed'), 0); }, '日志');
const rows = computed(() => sortProcessesByTime(props.processes));

const rules = {
  stageName: { required: true, message: '请输入日志事项', trigger: ['blur', 'input'] },
  startTime: { required: true, type: 'number', message: '请选择开始时间', trigger: ['change', 'blur'] },
};

// 起止时间改动后清空原用时，由服务端按新起止重新折算。
let lastModel: RestorationProcessVo | null = null;
watch(() => [editVo.value, editVo.value.startTime, editVo.value.endTime] as const, ([model], old) => {
  if (old && model === lastModel && mode.value === 'edit') model.duration = undefined;
  lastModel = model;
});

function onLogAdd() {
  onAdd(props.restorationId);
  editVo.value.sort = nextProcessSort(props.processes);
}

function guarded(node: any) {
  return permission ? withDirectives(node, [[permission, manageRoles]]) : node;
}

const columns = [
  {
    title: '日志时间', key: 'time', width: 300,
    render: (r: RestorationProcessVo) => `${formatDateTime(r.startTime, 'YYYY-MM-DD HH:mm')} ~ ${r.endTime ? formatDateTime(r.endTime, 'YYYY-MM-DD HH:mm') : '进行中'}`,
  },
  { title: '日志事项', key: 'stageName', width: 150, ellipsis: { tooltip: true } },
  { title: '内容', key: 'description', minWidth: 220, ellipsis: { tooltip: true }, render: (r: RestorationProcessVo) => r.description || '--' },
  {
    title: '负责人', key: 'operatorName', width: 160, ellipsis: { tooltip: true },
    render: (r: RestorationProcessVo) => {
      const people = processPeople(r);
      return [people.lead?.name, personNames(people.assistants)].filter(Boolean).join(' / ') || '--';
    },
  },
  { title: '用时', key: 'duration', width: 100, render: (r: RestorationProcessVo) => hoursText(processHours(r)) },
  {
    title: '操作', key: 'operate', width: 110,
    render: (r: RestorationProcessVo) => [
      guarded(h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onEdit(r) }, () => '编辑')),
      guarded(h(NButton, { text: true, type: 'error', size: 'small', style: 'margin-left:10px', onClick: () => onDelete(r) }, () => '删除')),
    ],
  },
];
</script>

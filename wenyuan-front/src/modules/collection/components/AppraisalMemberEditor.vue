<template>
  <div class="appraisal-members">
    <NAlert v-if="!rows.length" type="warning" :bordered="false">请添加鉴定组成员；关联系统用户的成员将收到会签任务。</NAlert>
    <div v-for="(row, index) in rows" :key="row.id || index" class="member-card">
      <NGrid :cols="12" :x-gap="10" :y-gap="8">
        <NGi :span="3">
          <UserSelect
            :value="row.userId"
            clearable
            placeholder="系统用户（审批人）"
            :disabled="disabled"
            @update:value="update(index, { userId: $event || undefined })"
          />
        </NGi>
        <NGi :span="2">
          <NInput
            :value="row.memberName"
            placeholder="姓名/外部专家"
            :disabled="disabled"
            @update:value="update(index, { memberName: $event })"
          />
        </NGi>
        <NGi :span="2">
          <NSelect
            :value="row.memberRole || 'MEMBER'"
            :options="roleOptions"
            :disabled="disabled"
            @update:value="update(index, { memberRole: $event })"
          />
        </NGi>
        <NGi :span="2">
          <NInput
            :value="row.organization"
            placeholder="单位"
            :disabled="disabled"
            @update:value="update(index, { organization: $event })"
          />
        </NGi>
        <NGi :span="2">
          <NInput
            :value="row.professionalTitle"
            placeholder="职称"
            :disabled="disabled"
            @update:value="update(index, { professionalTitle: $event })"
          />
        </NGi>
        <NGi :span="1" class="member-action">
          <NButton v-if="!disabled" text type="error" @click="remove(index)">移除</NButton>
        </NGi>
        <NGi :span="4">
          <NInput
            :value="row.specialty"
            placeholder="专业方向"
            :disabled="disabled"
            @update:value="update(index, { specialty: $event })"
          />
        </NGi>
        <NGi :span="4">
          <NInput
            :value="row.opinion"
            placeholder="成员意见（可在草稿中预录）"
            :disabled="disabled"
            @update:value="update(index, { opinion: $event })"
          />
        </NGi>
        <NGi :span="4">
          <HFileList v-if="disabled" :value="row.signatureFileId || ''" :revision="[row.id, row.userId]" show-download-btn />
          <HUploadInput v-else
            :value="row.signatureFileId"
            placeholder="成员签名文件"
            clearable
            :disabled="disabled"
            @update:value="update(index, { signatureFileId: $event || undefined })"
            @upload-finish="update(index, { signatureFileId: $event.id })"
          />
        </NGi>
      </NGrid>
    </div>
    <NButton v-if="!disabled" dashed block @click="add">添加鉴定组成员</NButton>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { AppraisalMemberVo } from '../typings/collection';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';

const props = defineProps<{ value?: AppraisalMemberVo[]; disabled?: boolean }>();
const emit = defineEmits<{ (event: 'update:value', value: AppraisalMemberVo[]): void }>();

const roleOptions = [
  { label: '组长', value: 'LEADER' },
  { label: '专家', value: 'EXPERT' },
  { label: '成员', value: 'MEMBER' },
];
const rows = computed(() => props.value || []);

function update(index: number, patch: Partial<AppraisalMemberVo>) {
  emit(
    'update:value',
    rows.value.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch, sort: row.sort ?? rowIndex } : row)),
  );
}

function add() {
  emit('update:value', [
    ...rows.value,
    { memberRole: rows.value.length ? 'MEMBER' : 'LEADER', sort: rows.value.length },
  ]);
}

function remove(index: number) {
  const next = rows.value.filter((_, rowIndex) => rowIndex !== index);
  if (next.length && !next.some((row) => row.memberRole === 'LEADER')) next[0] = { ...next[0], memberRole: 'LEADER' };
  emit('update:value', next.map((row, rowIndex) => ({ ...row, sort: rowIndex })));
}
</script>

<style scoped>
.appraisal-members {
  width: 100%;
  display: grid;
  gap: 10px;
}

.member-card {
  padding: 12px;
  border: 1px solid var(--n-border-color);
  border-radius: 6px;
  background: var(--n-color);
}

.member-action {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
</style>

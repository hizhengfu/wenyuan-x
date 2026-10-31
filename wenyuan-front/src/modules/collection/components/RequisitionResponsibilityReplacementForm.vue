<template>
  <CollectionEvidenceForm v-if="ready" ref="formRef" title="未点交提用责任变更" :fields="fields" :save="save">
    <template #context>
      <NAlert type="warning"
        >原提用单
        {{ sourceId }}
        的后续执行将终止，原方案、审批和档案永久保留。这里只创建新草稿，须重新提交三方审批；已发生实际点交或出库的业务不能使用此入口。</NAlert
      >
      <NText>登记本人（现任主要负责人）：{{ directorId }}。请核实新责任人、使用方案和实际签署依据。</NText>
    </template>
  </CollectionEvidenceForm>
</template>
<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useMessage } from 'naive-ui';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import { requisitionApi } from '../api/requisition';
import { requisitionDossierFields } from '../utils/requisitionEvidenceFields';
import type { EvidenceField } from '../utils/evidenceFields';
const emit = defineEmits<{ (e: 'created', sourceId: string, newId: string): void }>();
const message = useMessage(),
  formRef = ref<any>(),
  ready = ref(false),
  sourceId = ref(''),
  directorId = ref('');
const fields: EvidenceField[] = [
  { key: 'reason', label: '实际变动及旧单终止原因', type: 'textarea', required: true },
  { key: 'appointmentFileIds', label: '实际任职或岗位变动依据', type: 'file', required: true },
  { key: 'withdrawalFileIds', label: '旧申请终止执行签署原件', type: 'file', required: true },
  { key: 'operatorUserId', label: '新方案经办人', type: 'user', required: true },
  { key: 'reviewerUserId', label: '一级文物复核人', type: 'user' },
  ...requisitionDossierFields.filter((field) => field.key !== 'leaderUserId'),
];
const save = ref<(value: Record<string, any>) => Promise<unknown>>(async () => false);
let generation = 0;
onBeforeUnmount(() => {
  ++generation;
});
async function open(source: Record<string, any>) {
  const current = ++generation,
    original = cloneDeep(source),
    id = original.id;
  ready.value = false;
  sourceId.value = '';
  directorId.value = '';
  save.value = async () => false;
  try {
    const response: any = await requisitionApi.replacementInfo(id);
    if (current !== generation) return false;
    const info = response.data;
    if (!info?.canReplace) {
      message.warning(info?.notice || info?.evidenceErrors?.join('；') || '当前提用不能办理责任变更');
      return false;
    }
    sourceId.value = id;
    directorId.value = info.directorUserId;
    save.value = async (form) => {
      if (current !== generation) return false;
      const value = cloneDeep(form);
      const dossier = Object.fromEntries(
        requisitionDossierFields.map((field) => [
          field.key,
          field.key === 'leaderUserId'
            ? info.directorUserId
            : field.key === 'transportRequired'
              ? (value[field.key] === 'YES' ? true : value[field.key] === 'NO' ? false : value[field.key])
              : value[field.key],
        ]),
      );
      const result: any = await requisitionApi.replaceResponsibility(id, {
        expectedInstanceId: info.expectedInstanceId,
        expectedSubmittedDigest: info.expectedSubmittedDigest,
        reason: value.reason,
        appointmentFileIds: value.appointmentFileIds,
        withdrawalFileIds: value.withdrawalFileIds,
        operatorUserId: value.operatorUserId,
        reviewerUserId: value.reviewerUserId,
        dossier,
      });
      if (current !== generation) return false;
      message.success('新提用草稿已创建，请核实后重新提交三方审批');
      emit('created', id, result.data);
      return result;
    };
    ready.value = true;
    await nextTick();
    if (current !== generation) return false;
    formRef.value?.open({
      ...original.dossier,
      transportRequired: typeof original.dossier?.transportRequired === 'boolean'
        ? (original.dossier.transportRequired ? 'YES' : 'NO') : original.dossier?.transportRequired,
      departmentHeadUserId: undefined,
      keeperUserId: undefined,
      useResponsibleUserId: undefined,
      leaderUserId: undefined,
    });
    return true;
  } catch (error: any) {
    if (current === generation) {
      ready.value = false;
      sourceId.value = '';
      directorId.value = '';
      message.warning(error?.message || '无法读取当前责任变更资格，请核对授权及原件后重新打开');
    }
    return false;
  }
}
defineExpose({ open });
</script>

<template>
  <NSpace vertical :size="16">
    <NAlert type="info">审批前请核对变更依据和逐项差异。必须由指定责任人本人填写确认或批准意见。</NAlert>
    <NAlert v-if="archiveError" type="warning">{{ archiveError }}</NAlert>
    <NDescriptions bordered :column="1">
      <NDescriptionsItem label="变更事由" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ formData.reason }}</NDescriptionsItem>
      <NDescriptionsItem label="保管部门负责人"
        ><UserSelect :value="formData.keeperUserId" disabled
      /></NDescriptionsItem>
      <NDescriptionsItem label="法定代表人或主要负责人"
        ><UserSelect :value="formData.leaderUserId" disabled
      /></NDescriptionsItem>
      <NDescriptionsItem label="变更依据"
        ><CollectionCaseOriginalFiles v-if="stageArchive('SUBMITTED')" case-type="change" :archive-id="stageArchive('SUBMITTED')?.id" :case-id="formData.id" :archive-files="stageFiles('SUBMITTED')" :value="formData.evidenceFileIds"
      /><NText v-else depth="3">申请归档原件读取后显示</NText></NDescriptionsItem>
    </NDescriptions>
    <CollectionChangeCorrectionOrigin :archive-id="stageArchive('CORRECTION_ORIGIN')?.id" :archive-files="stageFiles('CORRECTION_ORIGIN')" :origin="formData.correction" :case-id="formData.id" />
    <CollectionChangeDiff :before="formData.before" :after="formData.content" />
    <NAlert v-if="setState.error" type="error">{{ setState.error }}。请停止确认或批准，返回原申请核对。</NAlert>
    <CollectionSetAmendmentPanel v-else :value="setState.value" />
  </NSpace>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { useCurrentImageLifecycle } from '@/utils/currentImage';
import { changeApi } from '../api/change';
import type { CollectionReadonlyFile } from '../utils/readonlyFiles';
import CollectionSetAmendmentPanel from '../components/CollectionSetAmendmentPanel.vue';
import { readSetAmendment } from '../utils/collectionSetAmendment';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import CollectionChangeDiff from '../components/CollectionChangeDiff.vue';
import CollectionChangeCorrectionOrigin from '../components/CollectionChangeCorrectionOrigin.vue';
import CollectionCaseOriginalFiles from '../components/CollectionCaseOriginalFiles.vue';
const props = defineProps<{ formData: Record<string, any> }>();
const archives = ref<{ id: string; phase: string; files?: CollectionReadonlyFile[] }[]>([]);
const archiveError = ref('');
const stageArchive = (phase: string) => { const selected = archives.value.filter(archive => archive.phase === phase); return selected.length === 1 ? selected[0] : undefined; };
const stageFiles = (phase: string) => stageArchive(phase)?.files || [];
let archiveEpoch = 0, archiveAbort: AbortController | undefined;
function clearArchives() { archiveEpoch++; archiveAbort?.abort(); archiveAbort = undefined; archives.value = []; archiveError.value = ''; }
const { eligible } = useCurrentImageLifecycle(() => props.formData, clearArchives, () => { void readArchives(); });
async function readArchives() {
  const id = props.formData.id, epoch = archiveEpoch;
  if (!id || !eligible.value) return;
  archiveAbort = new AbortController();
  try {
    const result: any = await changeApi.archive(id, archiveAbort.signal);
    if (epoch !== archiveEpoch || !eligible.value || props.formData.id !== id) return;
    if (!Array.isArray(result.data) || result.data.some((archive: any) => !archive || typeof archive.id !== 'string' || typeof archive.phase !== 'string' || (archive.bizId && archive.bizId !== id) || (archive.bizType && archive.bizType !== 'COLLECTION_CHANGE')))
      throw new Error('变更档案所属申请不一致');
    archives.value = result.data;
  } catch {
    if (epoch === archiveEpoch) archiveError.value = '变更原件读取失败，请核对当前授权并返回本单档案查看。';
  }
}
const setState = computed(() => {
  try {
    return { value: readSetAmendment(props.formData.setAmendment, props.formData.collectionId), error: '' };
  } catch (error) {
    return { value: undefined, error: error instanceof Error ? error.message : '套件订正资料不完整' };
  }
});
function validate() {
  if (setState.value.error) throw new Error(setState.value.error);
  return props.formData;
}
defineExpose({ validate });
</script>

<template>
  <NDescriptions
    bordered
    :column="1"
    :label-placement="labelPlacement"
    :class="{ 'collection-evidence--inline': labelPlacement === 'left' }"
  >
    <NDescriptionsItem v-for="field in fields" :key="field.key" v-bind="descriptionLabelProps(field)" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">
      <template v-if="field.labelHelp" #label><CollectionFieldLabel :label="field.label" :help="field.labelHelp" /></template>
      <template v-if="field.type === 'records'"
        ><CollectionEvidenceReadView
          v-for="(row, index) in value(field.key) || []"
          :key="index"
          :fields="field.children || []"
          :data="row"
          :original-url="originalUrl"
          :path-prefix="path(field.key)"
          :archive-files="archiveFiles"
          :label-placement="labelPlacement"
      /></template>
      <template v-else-if="field.type === 'file' && value(field.key)"
        ><slot name="file" :value="value(field.key)" :field="field"
          ><HFileList
              v-if="originalUrl"
              :files="readonlyFiles(value(field.key), archiveFiles)"
              :original-url="(fileId: string) => archivedOriginalUrl(fileId, path(field.key))"
              :revision="[data, path(field.key), archiveFiles]"
              show-download-btn
            />
            <HFileList v-else :value="files(value(field.key))" show-download-btn /></slot
      ></template>
      <UserText v-else-if="field.type === 'user'" :value="value(field.key)" placeholder="未登记" />
      <GroupSelect v-else-if="field.type === 'group' && value(field.key)" :value="value(field.key)" disabled />
      <span v-else>{{ display(field) }}</span>
    </NDescriptionsItem>
  </NDescriptions>
</template>
<script setup lang="ts">
import { formatLocalDateValue } from '@/utils';
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import UserText from '@/modules/sys/components/UserText.vue';
import CollectionFieldLabel from './CollectionFieldLabel.vue';
import { readonlyFiles, type CollectionReadonlyFile } from '../utils/readonlyFiles';
import type { EvidenceField } from '../utils/evidenceFields';
import { formatYuan } from '../utils/moneyYuan';
const props = withDefaults(
  defineProps<{
    fields: EvidenceField[];
    data: Record<string, any>;
    originalUrl?: (fileId: string, fieldKey: string) => string | undefined;
    archiveFiles?: CollectionReadonlyFile[];
    pathPrefix?: string;
    labelPlacement?: 'left' | 'top';
  }>(),
  { labelPlacement: 'top' },
);
const files = (input: unknown): string[] => [
  ...new Set(
    (Array.isArray(input) ? input : [input]).flatMap((item) =>
      typeof item === 'string'
        ? item
            .split(/[,，;；\r\n]/)
            .map((v) => v.trim())
            .filter(Boolean)
        : [],
    ),
  ),
];
function archivedOriginalUrl(fileId: string, field: string) {
  if (props.archiveFiles !== undefined && !props.archiveFiles.some(file => (file.fileId || file.id) === fileId)) return undefined;
  return props.originalUrl?.(fileId, field);
}
/** NDescriptionsItem 只要 props 里有 label 键（含 label: undefined）就忽略 #label 插槽；有 labelHelp 时整个去掉 label 键，交给插槽渲染问号提示。 */
const descriptionLabelProps = (field: EvidenceField): { label?: string } => (field.labelHelp ? {} : { label: field.label });
const path = (key: string) => [props.pathPrefix, key].filter(Boolean).join('.');
const value = (key: string) => key.split('.').reduce((data: any, part) => data?.[part], props.data);
function display(field: EvidenceField) {
  const result = value(field.key);
  if (result === undefined || result === null || result === '') return '未登记';
  if (field.unit === 'yuan') return formatYuan(result);
  if (field.type === 'datetime') return formatLocalDateValue(result, 'YYYY-MM-DD HH:mm:ss');
  if (field.type === 'date') return formatLocalDateValue(result);
  if (field.type === 'select' && Array.isArray(result))
    return result.map((v) => field.options?.find((o) => o.value === v)?.label || v).join('、');
  if (field.type === 'select')
    return (
      field.options?.find((o) => o.value === (typeof result === 'boolean' ? (result ? 'YES' : 'NO') : result))?.label ||
      result
    );
  return result;
}
</script>

<style scoped>
/* Each compact row has one label and one value; keep trailing library colspans from changing their widths. */
.collection-evidence--inline :deep(.n-descriptions-table),
.collection-evidence--inline :deep(.n-descriptions-table tbody) {
  display: block;
}
.collection-evidence--inline :deep(.n-descriptions-table-row) {
  display: flex;
}
.collection-evidence--inline :deep(.n-descriptions-table-header) {
  box-sizing: border-box;
  flex: 0 0 var(--collection-evidence-label-width, 180px);
}
.collection-evidence--inline :deep(.n-descriptions-table-content) {
  flex: 1;
  min-width: 0;
}
@media (max-width: 760px) {
  .collection-evidence--inline :deep(.n-descriptions-table-header) {
    flex-basis: 130px;
  }
}
</style>

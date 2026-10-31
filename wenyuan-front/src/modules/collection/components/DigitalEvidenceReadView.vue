<template>
  <CollectionEvidenceReadView :fields="fields" :data="data"
    ><template #file="{ value, field }"
      ><DigitalOriginalFiles
        :project-id="projectId"
        :files="digitalFileIds(value).map((fileId) => ({ ...readonlyFiles([fileId], archiveFiles)[0], fileId }))" /></template
  ></CollectionEvidenceReadView>
</template>
<script setup lang="ts">
import { readonlyFiles, type CollectionReadonlyFile } from '../utils/readonlyFiles';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import DigitalOriginalFiles from './DigitalOriginalFiles.vue';
import { digitalFileIds } from '../utils/digitalAcquisitionFields';
import type { EvidenceField } from '../utils/evidenceFields';
defineProps<{ projectId: string; fields: EvidenceField[]; data: Record<string, any>; archiveFiles?:CollectionReadonlyFile[] }>();
</script>

<style scoped>ul{padding-left:20px;overflow-wrap:anywhere}</style>

<template>
  <NSpace vertical
    ><NAlert v-if="!entries?.items?.length" type="info"
      >系统暂未发现当日实际进出登记，请核对是否完整；不能把未录入当成无人入库。</NAlert
    ><NCard
      v-for="v in entries?.items || []"
      :key="v.applicationId"
      :title="`${v.applicationNo} · ${warehouseVisitStatus(v)}`"
      size="small"
      ><NText
        >{{ v.purpose }}；实际进入 {{ formatLocalDateValue(v.enteredAt, 'YYYY-MM-DD HH:mm:ss') }}；实际离开
        {{ formatLocalDateValue(v.exitedAt, 'YYYY-MM-DD HH:mm:ss') || '待登记' }}</NText
      ><span
        v-if="v.applicationId"
        v-permission="'ADMIN,COLLECTION_ADMIN,COLLECTION_SELECT,COLLECTION_WAREHOUSE_ENTRY_APPLY,COLLECTION_WAREHOUSE_ENTRY_VERIFY'"
        ><NButton size="small" secondary @click="emit('openEntry', v.applicationId)"
          >查看来源人员入库案卷及原件</NButton></span
      ><CollectionEvidenceReadView
        v-if="v.entry"
        :fields="warehouseAttendanceFields(v.entry)"
        :data="v.entry" /><CollectionEvidenceReadView
        v-if="v.exit"
        :fields="warehouseAttendanceFields(v.exit, true)"
        :data="v.exit" /></NCard
  ></NSpace>
</template>
<script setup lang="ts">
import { formatLocalDateValue } from '@/utils';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import { warehouseVisitStatus, warehouseAttendanceFields } from '../utils/warehouseEvidenceFields';
defineProps<{ entries?: any }>();
const emit = defineEmits<{ (e: 'openEntry', id: string): void }>();
</script>

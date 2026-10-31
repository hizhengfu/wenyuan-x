<template>
  <HPageForm
    v-model:show="innerShow"
    :model="detail || {}"
    :title="title || `查看「${detail?.publishName || '基础档案'}」`"
    width="80%"
    :disabled="true"
    :enable-cancel="false"
  >
    <NSpin :show="loading" style="height: 100%" content-style="height: 100%">
      <AssociatedTab v-if="detail?.id" :detail="detail" />
      <NEmpty v-else-if="!loading" description="暂无基础档案信息" />
    </NSpin>
  </HPageForm>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { get } from '@/modules/archive/api/basic/culturalRecordBasic'
import AssociatedTab from './AssociatedTab.vue'

const props = defineProps<{ show: boolean; basicId?: string | null; title?: string }>()
const emit = defineEmits<{ 'update:show': [value: boolean] }>()
const message = useMessage()
const loading = ref(false)
const detail = ref<Record<string, any> | null>(null)
const innerShow = computed({ get: () => props.show, set: (value) => emit('update:show', value) })

watch([() => props.show, () => props.basicId], ([show, basicId]) => {
  if (show && basicId) void loadDetail(String(basicId))
})

async function loadDetail(basicId: string) {
  loading.value = true
  detail.value = null
  try {
    const resp: any = await get(basicId)
    detail.value = resp?.data || null
  } catch {
    message.error('基础档案信息读取失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <n-tag :type="cfg.type" :size="size">{{ cfg.label }}</n-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    status?: string | null
    size?: 'small' | 'medium' | 'large'
  }>(),
  { size: 'small' },
)

const STATUS_MAP: Record<string, { label: string; type: 'default' | 'info' | 'success' | 'warning' | 'error' }> = {
  DRAFT:  { label: '草稿',   type: 'default'  },
  AUDIT:  { label: '审批中', type: 'warning'  },
  PASS:   { label: '已通过', type: 'success'  },
  REJECT: { label: '已驳回', type: 'error'    },
  CANCEL: { label: '已撤销', type: 'default'  },
  VOID:   { label: '已作废', type: 'default'  },
}

const cfg = computed(() =>
  (props.status && STATUS_MAP[props.status]) || { label: props.status || '草稿', type: 'default' as const },
)
</script>

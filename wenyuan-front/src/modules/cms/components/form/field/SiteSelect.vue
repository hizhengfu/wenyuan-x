<template>
  <n-select v-bind="$attrs" v-model:value="siteId" :options="options" label-field="name" value-field="id" />
</template>
<script lang="ts" setup>
import { onBeforeMount, ref, computed } from "vue";
import { list } from '../../../api/site'

const emits = defineEmits(['update:value'])
const options = ref<any[]>([])

const props = defineProps({
  value: { type: String },
  autoSelectFirst: { type: Boolean, default: false },
  enabledOnly: { type: Boolean, default: false }
})

const siteId = computed<string | undefined>({
  get() {
    return props.value
  },
  set(value) {
    emits('update:value', value)
  }
});

onBeforeMount(() => {
  // 预览只查询启用站点；选择器请求全部匹配项，避免分页遗漏可选站点。
  list({ pageSize: 0, ...(props.enabledOnly ? { enabled: true } : {}) }).then((res) => {
    if (res.data) {
      options.value = res.data.list || []
      const unavailable = props.enabledOnly && props.value && !options.value.some(site => site.id === props.value)
      // 保留有效的手动选择；已停用的选择按默认选择规则替换，没有可用站点时清空。
      if ((props.autoSelectFirst && !props.value) || unavailable) {
        siteId.value = props.autoSelectFirst ? options.value[0]?.id : undefined
      }
    }
  })
})
</script>

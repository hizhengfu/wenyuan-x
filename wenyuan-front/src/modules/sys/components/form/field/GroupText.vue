<template>
  <span>{{ name }}</span>
</template>
<script lang="ts">
import { tree } from '../../../api/group'

type GroupNode = { id: string; name: string; children?: GroupNode[] }

const groupNameCache = new Map<string, string>()
let loadPromise: Promise<void> | null = null

function flatten(nodes: GroupNode[]): void {
  nodes.forEach((n) => {
    groupNameCache.set(n.id, n.name)
    if (n.children?.length) flatten(n.children)
  })
}

function ensureLoaded(): Promise<void> {
  if (!loadPromise) {
    loadPromise = tree({}).then((list: any) => {
      flatten(list || [])
    })
  }
  return loadPromise
}
</script>
<script lang="ts" setup>
import { computed, ref, watchEffect } from 'vue'

const props = defineProps<{ value?: string | null }>()
const ready = ref(false)
watchEffect(() => {
  ensureLoaded().then(() => (ready.value = true))
})

const name = computed(() => {
  if (!props.value) return '--'
  if (!ready.value) return props.value
  return groupNameCache.get(props.value) || props.value
})
</script>

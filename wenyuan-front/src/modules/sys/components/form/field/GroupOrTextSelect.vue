<template>
  <n-input-group>
    <n-select :value="mode" :options="modeOptions" style="width: 110px" @update:value="onModeChange" />
    <group-select v-if="mode === 'internal'" :value="groupId" :style="{ flex: 1 }"
      @update:value="onGroupChange" />
    <n-input v-else :value="name" placeholder="请输入外部单位名称" clearable :style="{ flex: 1 }"
      @update:value="onNameChange" />
  </n-input-group>
</template>
<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue'
import { NInputGroup, NSelect, NInput } from 'naive-ui'
import GroupSelect from './GroupSelect.vue'
import { tree } from '../../../api/group'

type GroupNode = { id: string; name: string; children?: GroupNode[] }

const props = defineProps<{
  groupId?: string | null
  name?: string | null
}>()
const emit = defineEmits<{
  (e: 'update:groupId', v: string | null): void
  (e: 'update:name', v: string): void
}>()

const modeOptions = [
  { label: '系统内机构', value: 'internal' },
  { label: '系统外单位', value: 'external' },
]
const mode = ref<'internal' | 'external'>(props.groupId ? 'internal' : 'external')

watch(
  () => props.groupId,
  (v) => {
    if (v) mode.value = 'internal'
  },
)

function onModeChange(v: 'internal' | 'external') {
  mode.value = v
  if (v === 'internal') {
    emit('update:name', '')
  } else {
    emit('update:groupId', null)
  }
}

const nameCache = new Map<string, string>()
let loaded = false

function flatten(nodes: GroupNode[]): void {
  nodes.forEach((n) => {
    nameCache.set(n.id, n.name)
    if (n.children?.length) flatten(n.children)
  })
}

onMounted(async () => {
  if (loaded) return
  const list = (await tree({})) as GroupNode[] | null
  flatten(list || [])
  loaded = true
  if (mode.value === 'internal' && props.groupId) {
    const n = nameCache.get(props.groupId)
    if (n && n !== props.name) emit('update:name', n)
  }
})

function onGroupChange(gid: string | null) {
  emit('update:groupId', gid)
  if (gid) emit('update:name', nameCache.get(gid) || '')
}

function onNameChange(v: string) {
  emit('update:name', v)
}
</script>

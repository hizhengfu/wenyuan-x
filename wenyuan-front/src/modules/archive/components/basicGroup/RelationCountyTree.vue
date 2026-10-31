<template>
  <section class="relation-filter-option-section relation-county-section">
    <div class="relation-filter-option-title">公布地域（区县）</div>
    <div v-if="cities.length" class="relation-county-tree">
      <NCollapse :default-expanded-names="cities.map((item) => item.value)">
        <NCollapseItem v-for="city in cities" :key="city.value" :name="city.value" :title="city.label">
          <NCheckboxGroup :value="value" class="relation-county-list" @update:value="updateValue">
            <NCheckbox v-for="county in city.children" :key="county.value" :value="county.value" :label="county.label" />
          </NCheckboxGroup>
        </NCollapseItem>
      </NCollapse>
    </div>
    <NEmpty v-else size="small" description="暂无区县选项" class="relation-county-empty" />
  </section>
</template>

<script lang="ts" setup>
export interface LocationTreeNode {
  label: string
  value: string
  children: Array<{ label: string; value: string }>
}

defineProps<{ value: string[]; cities: LocationTreeNode[] }>()
const emit = defineEmits<{ 'update:value': [value: string[]] }>()

function updateValue(value: Array<string | number>) {
  emit('update:value', value.map(String))
}
</script>

<style scoped>
.relation-filter-option-section { min-width: 0; overflow: hidden; border: 1px solid #edf1f5; background: #fff; }
.relation-filter-option-title { display: flex; height: 36px; align-items: center; padding: 0 12px; border-bottom: 1px solid #edf1f5; background: #fbfcfe; color: #344054; font-size: 13px; font-weight: 600; }
.relation-county-tree { max-height: 260px; overflow-y: auto; padding: 0 12px 8px; }
.relation-county-tree :deep(.n-collapse-item__header) { min-height: 34px; font-size: 13px; }
.relation-county-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 12px; padding: 2px 0 8px 24px; }
.relation-county-list :deep(.n-checkbox__label) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.relation-county-empty { padding: 24px 0; }
</style>

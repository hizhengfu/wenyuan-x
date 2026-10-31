<template>
  <div ref="refDetail" class="detail-table" :class="{ 'detail-table--with-print': showPrint }">
    <div v-if="showPrint" class="detail-table__toolbar">
      <Print :printable="refDetail" size="large"></Print>
    </div>

    <n-table v-if="hasRows" class="detail-table__table" :single-line="false">
      <tbody>
        <tr
          v-for="(row, rowIndex) in rows"
          :key="row.key ?? rowIndex"
          :class="row.class"
          :style="row.style"
        >
          <template v-for="(cell, cellIndex) in row.cells" :key="cell.key ?? `${rowIndex}-${cellIndex}`">
            <component
              :is="cell.tag || 'td'"
              :colspan="cell.colspan"
              :rowspan="cell.rowspan"
              :class="cell.class"
              :style="cell.style"
            >
              <slot
                v-if="cell.slot"
                :name="cell.slot"
                :cell="cell"
                :row="row"
                :row-index="rowIndex"
                :cell-index="cellIndex"
              >
                <span v-if="cell.html" v-html="normalizeText(cell.content)"></span>
                <template v-else>{{ normalizeText(cell.content) }}</template>
              </slot>
              <span v-else-if="cell.html" v-html="normalizeText(cell.content)"></span>
              <template v-else>{{ normalizeText(cell.content) }}</template>
            </component>
          </template>
        </tr>
      </tbody>
    </n-table>

    <n-empty v-else-if="showEmpty" :description="emptyDescription"></n-empty>
  </div>
</template>

<script lang="ts" setup>
import Print from '@/components/basic/print/Print.vue';
import { computed, PropType, ref } from 'vue';
import type { DetailRow } from './detail-table';

const props = defineProps({
  rows: {
    type: Array as PropType<DetailRow[]>,
    default: () => [],
  },
  showPrint: {
    type: Boolean,
    default: false,
  },
  showEmpty: {
    type: Boolean,
    default: true,
  },
  emptyDescription: {
    type: String,
    default: '暂无详情',
  },
  emptyText: {
    type: String,
    default: '--',
  },
});

const refDetail = ref();

const hasRows = computed(() => {
  return props.rows.some((row) => row?.cells?.length);
});

function normalizeText(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return props.emptyText;
  }

  return String(value);
}
</script>

<style lang="scss" scoped>
.detail-table {
  min-height: 180px;
}

.detail-table--with-print {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-table__toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex: none;
}

.detail-table__table {
  width: 100%;
  table-layout: fixed;
}
</style>

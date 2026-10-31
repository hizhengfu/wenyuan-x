<template>
  <div class="ledger-log-value" :class="{ 'ledger-log-value--compact': compact }">
    <dl v-if="rows.length">
      <div v-for="item in rows" :key="item.path" class="ledger-log-value__item">
        <dt v-if="item.label">{{ item.label }}</dt>
        <dd>
          <CollectionCatalogDictText v-if="ledgerValueDicts[item.field] && hasValue(item[side])"
            :type="ledgerValueDicts[item.field]" :value="item[side]" />
          <template v-else>{{ ledgerValueText(item.field, item[side]) }}</template>
        </dd>
      </div>
    </dl>
    <span v-else>未填写</span>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { LedgerLogVo } from '../typings/collection';
import { ledgerValueDicts, ledgerValueRows, ledgerValueText } from '../utils/ledgerLogDisplay';
import CollectionCatalogDictText from './CollectionCatalogDictText.vue';
const props = defineProps<{ log: LedgerLogVo; side: 'before' | 'after'; compact?: boolean }>();
const rows = computed(() => ledgerValueRows(props.log));
const hasValue = (value: unknown) => value != null && value !== '';
</script>
<style scoped>
.ledger-log-value { min-width: 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.6; }
.ledger-log-value--compact { max-height: 180px; overflow-y: auto; }
dl, dd { margin: 0; }
.ledger-log-value__item + .ledger-log-value__item { margin-top: 10px; }
dt { font-size: 12px; opacity: .65; margin-bottom: 2px; }
</style>

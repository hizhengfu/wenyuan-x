<template>
  <NGrid :cols="cols" :x-gap="16">
  <NGi v-if="historical" :span="cols"><NText depth="3">原账年代、质地及细分字段保留原值，缺项不阻止首次编目补录。</NText></NGi>
  <NFormItemGi v-if="model.nature !== 'OTHER'" label="年代类型" path="registrationDetails.eraMeaning">
    <NSelect :value="model.registrationDetails?.eraMeaning" :options="eraMeanings" clearable :disabled="historical" @update:value="setEra('eraMeaning', $event)" />
  </NFormItemGi>
  <NFormItemGi v-if="model.nature !== 'OTHER'" label="年代表示方式" path="registrationDetails.eraNotation">
    <NSelect :value="model.registrationDetails?.eraNotation" :options="eraNotations" clearable :disabled="historical" @update:value="setEra('eraNotation', $event)" />
  </NFormItemGi>
  <NFormItemGi label="具体年代" path="era" :span="cols">
    <NInput :disabled="historical" v-model:value="model.era" placeholder="如 清乾隆六十年（1795年）" />
  </NFormItemGi>
  <NFormItemGi v-if="model.nature !== 'OTHER'" label="质地类别" path="textureCategory1">
    <NSelect :value="model.textureCategory1" :options="categoryOptions" clearable :disabled="historical" @update:value="setCategory" />
  </NFormItemGi>
  <NFormItemGi v-if="model.nature !== 'OTHER'" label="质地细类" path="textureCategory2">
    <NSelect v-model:value="model.textureCategory2" :options="detailOptions" :disabled="historical || !model.textureCategory1" clearable />
  </NFormItemGi>
  <NGi :span="cols">
    <CollectionFeedbackItem label="具体质地" path="texture" hint="可选常用材质，也可输入具体材质并回车确认；复合或组合质地应写明主要材质，如木、漆、金。">
      <HDictSelect :disabled="historical" v-model:value="model.texture" type="col_texture" :fallback-options="materialOptions" tag clearable placeholder="选择或输入主体材质" />
    </CollectionFeedbackItem>
  </NGi>
  </NGrid>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { CollectionCatalogContentVo, CollectionRegistrationDetailsVo } from '../typings/collection';
import { eraMeanings, eraNotations, materialCategories, materialSuggestions } from '../utils/collectionDescription';
import CollectionFeedbackItem from './CollectionFeedbackItem.vue';
withDefaults(defineProps<{ cols?: number; historical?: boolean }>(), { cols: 2 });
const model = defineModel<CollectionCatalogContentVo>({ required: true });
const categoryOptions = Object.keys(materialCategories).map(value => ({ value, label: value }));
const detailOptions = computed(() => (materialCategories[model.value.textureCategory1 || ''] || []).map(value => ({ value, label: value.replace('/', ' / ') })));
const materialOptions = materialSuggestions.map(value => ({ value, label: value }));
function setCategory(value: string | null) {
  model.value.textureCategory1 = value || undefined;
  if (!value || !materialCategories[value]?.includes(model.value.textureCategory2 || '')) model.value.textureCategory2 = undefined;
}
function setEra(key: 'eraMeaning' | 'eraNotation', value: string | null) {
  model.value.registrationDetails = { ...model.value.registrationDetails, [key]: value || undefined } as CollectionRegistrationDetailsVo;
}
</script>

<template>
  <n-select v-model:value="i18n.locale.value" :options="options"></n-select>
</template>
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue';

const i18n = useI18n()

const options = computed(() => {
  return i18n.availableLocales.map((item: string) => {
    return {label: item, value: item}
  })
})

</script>

<template>
  <span>{{ label }}</span>
</template>
<script setup lang="ts">
import {PropType, ref, watchEffect} from 'vue'
import {cascadeName} from '../../../api/area'

const props = defineProps({
  value: {type: String as PropType<string | null>},
})

const label = ref()

watchEffect(() => {
  const {value} = props
  if (value) {
    cascadeName({id: value}).then((resp) => {
      label.value = resp
    })
  }
})

</script>

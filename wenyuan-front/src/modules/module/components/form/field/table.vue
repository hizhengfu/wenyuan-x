<template>
  <n-select :options="options" v-model:value="value" v-bind="$attrs" :multiple="multiple"/>
</template>
<script lang="ts">
export default {
  name: 'module-field-table'
}
</script>
<script lang="ts" setup>
import {computed, shallowRef, triggerRef, watchEffect} from "vue";
import {load} from '../../../api/moduleTable'
import {isEmpty} from "lodash-es";

const props = defineProps({
  value: {type: [String, Number]},
  multiple: {type: Boolean, default: false},
  isCreate: {type: Boolean, default: false}
})

const options = shallowRef<any>([])

const emit = defineEmits(['update:value'])
const value = computed({
  get() {
    let value = props.value?.toString()
    if (props.multiple) {
      if (isEmpty(value)) {
        return []
      } else {
        return value?.split(",")
      }
    } else {
      return value
    }
  },
  set(value) {
    if (value instanceof Array) {
      emit('update:value', value ? value.join(",") : value)
    } else {
      emit('update:value', value)
    }
  }
})

watchEffect(() => {
  const {isCreate} = props
  load({isCreate: isCreate}).then((resp) => {
    let data = resp.data
    if (data) {
      options.value = data.map((item: any) => {
        let title = item.comments && `(${item.comments})` || ''
        return {label: `${item.name}${title}`, value: item.name}
      })
      triggerRef(options)
    }
  })
})
</script>

<template>
  <n-select :options="options" v-model:value="value" v-bind="$attrs" :multiple="multiple"/>
</template>
<script lang="ts" setup>
import {computed, shallowRef, triggerRef, watchEffect} from 'vue';
import {columns} from '../../../api/moduleTable'
import {isEmpty} from 'lodash-es';

const props = defineProps({
  value: {type: [String, Number]},
  multiple: {type: Boolean, default: false},
  valueField: {type: String, default: 'value'},
  tableName: {type: String},
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
        return value?.split(',')
      }
    } else {
      return value
    }
  },
  set(value) {
    if (value instanceof Array) {
      emit('update:value', value ? value.join(',') : value)
    } else {
      emit('update:value', value)
    }
  }
})

watchEffect(() => {
  const {tableName} = props
  columns({tableName: tableName}).then((resp) => {
    let data = resp.data || []
    if (data) {
      options.value = data.map((item: any) => {
        return {
          label: item.label,
          value: item[props.valueField]
        }
      })
      triggerRef(options)
    }
  })
})
</script>

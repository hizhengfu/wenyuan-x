<template>
  <n-cascader v-bind="$attrs"
              :options="options"
              check-strategy="child"
              label-field="name"
              value-field="id"/>
</template>
<script lang="ts" setup>
import {findIndex} from 'lodash-es'
import {onBeforeMount, ref} from "vue";
import {list} from '../../../api/room'

const options = ref()

onBeforeMount(() => {
  list({pageSize: -1}).then((res) => {
    if (res.data) {
      let data = res.data.list || []
      let result: any[] = []
      data.forEach((item: any) => {
        let index = findIndex(result, (it) => {
          return it.id = item.place.id
        })
        if (index === -1) {
          result.push(Object.assign({children: [item]}, item.place))
        } else {
          result[index]['children'] = item
        }
      })
      options.value = result
    }
  })
})
</script>

<template>
    <n-card title="历史沿革" :bordered="false">
      <template v-if="form.evolutionList">

         <template v-if="form.evolutionList.length>1">

            <n-tabs type="bar" >
                <template v-for="item in form.evolutionList" :key="item.id">
                <n-tab-pane :name="item.id" :tab="item.name">
                    <div v-html="item.content"></div>
                </n-tab-pane>
                </template>
            </n-tabs>
            <n-divider></n-divider>
         </template>
         <template v-else>
           <div v-html="form.evolutionList[0].content"></div>
         </template>
      </template>
      <template v-else>
        <div>暂无数据</div>
      </template>

    </n-card>
</template>
<script lang="ts" setup >
import {ref} from 'vue'

const props = defineProps({
  form: {
    type: Object,
    default: () => ({})
  }
})

</script>

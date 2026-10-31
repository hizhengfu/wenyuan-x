<template>
  <div class="back">
    <div class="back-item">
      <img width="20" style="margin-right: 5px" src="../../assets/images/icon.png" alt="" />当前位置: &nbsp;
    </div>
    <n-breadcrumb>
      <template v-for="item in list">
        <n-breadcrumb-item v-if="item.name" @click="handleClick(item)">
          {{ item.name }}
        </n-breadcrumb-item>
      </template>
    </n-breadcrumb>
  </div>
</template>
<script lang="ts" setup>
import { watch, ref } from 'vue';

const emit = defineEmits(['click']);

const props = defineProps<{ list: any[] }>();

function handleClick(item: any) {
  emit('click', item);
}
watch(()=>props.list,(newVal:any)=>{
 console.log(newVal,'面包屑数据');
 
},{deep:true,immediate:true})
</script>
<style lang="scss" scoped>
.back {
  display: flex;
  padding: 8px 16px;
  flex-wrap: wrap;
  background-color: #fff;
  color: #333;
  border-radius: 4px;
  cursor: pointer;
  box-shadow: 0px 0px 3px 2px rgba(0, 0, 0, 0.16);
  display: flex;
  align-items: center;

  .back-item {
    white-space: nowrap;
    display: flex;
    align-items: center;
    cursor: pointer;

    div:hover {
      color: rgb(14, 131, 177);
    }
  }

  .under-line {
    text-decoration: underline;
  }
}
</style>

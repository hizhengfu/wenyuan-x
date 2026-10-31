<template>
    <n-flex justify="center" >
        <img :src="fileUrl(form.image)"  />
        <n-card title="遗产描述">
         <n-table class="info-table" :single-line="false">
            <tr>
                <td>所在省份:</td>
                <td>{{form.areaId}}</td>
                <td>中心坐标:</td>
                <td>E{{form.longitude}},N{{form.latitude}}</td>

            </tr>
            <tr>
                <td>列入时间:</td>
                <td>{{form.year}}</td>
                <td>扩展时间:</td>
                <td>{{form.extendYear}}</td>
            </tr>
            <tr>
                <td>遗产区面积:</td>
                <td>{{form.heritageArea || '--'}}ha</td>
                <td>缓冲区面积:</td>
                <td>{{form.bufferArea || '--'}}ha</td>
            </tr>
            <tr>
                <td>标准:</td>
                <td>{{form.criterion || '--'}}ha</td>
                <td></td>
                <td></td>
            </tr>
         </n-table>
            <div v-html="form.description" style="margin-top: 10px;"></div>
            <h3 style="margin-top: 50px;margin-bottom: 10px; text-align: center;">世界遗产保护区划图</h3>
             <heritageMap :form="form" style="margin-bottom: 20px;"></heritageMap>
        </n-card>
  </n-flex>

 </template>
 <script lang="ts" setup>
import { fileUrl } from '@/utils';
import heritageMap from './heritageMap.vue'
 import { ref } from 'vue'
 const props = defineProps({
    form:{
        type: Object,
        default: () => ({})
    }
 })
</script>

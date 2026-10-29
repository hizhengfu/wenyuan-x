<template>
   <n-card title="突出普遍价值" :bordered="false">
    <n-flex vertical>
        <div  v-html="form.outstandingUniversal"></div>
         <n-divider></n-divider>
        <n-card title="完整性" :bordered="false" content-style="padding: 0;" header-style="padding: 0;">
            <div v-html="form.integrity"></div>
        </n-card>
        <n-divider></n-divider>
        <n-card title="真实性" :bordered="false" content-style="padding: 0;" header-style="padding: 0;">
            <div v-html="form.authenticity"></div>
        </n-card>
        <n-divider></n-divider>
        <n-card title="保护和管理要求" :bordered="false" content-style="padding: 0;" header-style="padding: 0;">
            <div v-html="form.protectionManagement"></div>
        </n-card>

    </n-flex>
   </n-card>
</template>
<script lang="ts" setup>
import {ref} from 'vue'

 const props = defineProps({
    form:{
        type: Object,
        default: () => ({})
    }
 })
</script>

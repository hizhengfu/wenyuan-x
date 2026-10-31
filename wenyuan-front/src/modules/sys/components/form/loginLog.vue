<template>
  <h-dialog-form v-model:show="logModel" :title="formTitle" size='small' :enableCancel="false" width="1200px">
    <n-data-table :data="logData" scroll-x="100%" :columns="logColumns">
    </n-data-table>
  </h-dialog-form>
</template>
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { log } from '../../api/user.ts';
import dayjs from 'dayjs';

const props = defineProps({
  id: { type: String, },
});

const logColumns = [
  { key: 'userInfo', title: '用户信息', columnType: 'title', fixed: 'left', width: '150px', resizable: true },
  { key: 'loginIp', title: '登录IP', width: '120px', resizable: true, ellipsis: true },
  {
    key: 'loginTime', title: '登录时间', width: '120px',
    render(row: any, index: number) {
      return row.loginTime ? dayjs(row.loginTime).format('YYYY-MM-DD HH:mm:ss') : '';
    },
  },
] as any;
const logModel = ref(false)
const formTitle = ref()
const logData = ref()

watch(
  () => props.id,
  (newVal) => {
    fetchData()
  },
);

function fetchData() {
  log({ id: props.id }).then((res) => {
    logData.value = res.data || []
    logModel.value = true;
  });
}
</script>

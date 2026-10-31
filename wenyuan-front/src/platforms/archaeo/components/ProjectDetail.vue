<template>
  <n-table :single-line="false">
    <tr>
      <td style="width: 120px">遗址名称</td>
      <td>{{ form.name }}</td>
      <td style="width: 120px">遗址编号</td>
      <td>{{ form.code }}</td>
    </tr>
    <tr>
      <td>所属机构</td>
      <td>{{ form.group?.name }}</td>
      <td>负责人</td>
      <td>{{ form.leader }}</td>
    </tr>
    <tr>
      <td>开始日期</td>
      <td>
        <n-time :time="form.startDate" format="yyyy-MM-dd" />
      </td>
      <td>结束日期</td>
      <td>
        <n-time :time="form.endDate" format="yyyy-MM-dd" />
      </td>
    </tr>
    <tr>
      <td>位置</td>
      <td colspan="3">{{ form.location }}</td>
    </tr>
    <tr>
      <td colspan="4">
        备注:
        <div>{{ form.remarks }}</div>
      </td>
    </tr>
    <tr>
      <td colSpan="4">
        <h-file-list :contentId="form.id"></h-file-list>
      </td>
    </tr>
  </n-table>
</template>
<script lang="ts" setup>
import { get } from '../api/project';
import { cloneDeep } from 'lodash-es';
import { ref, watch } from 'vue';

const props = defineProps({ id: { type: String } });

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  name: '',
  image: '',
  code: '',
  groupId: '',
  group: { name: '' },
  leader: '',
  location: '',
  startDate: undefined,
  endDate: undefined,
  lat: '',
  lng: '',
  remarks: '',
  latlng: '',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

function fetchData(id: string) {
  get(id).then((resp: any) => {
    Object.assign(form.value, resp.data);
  });
}

watch(
  () => props.id,
  (value) => {
    if (value) {
      fetchData(value);
    }
  },
  {
    immediate: true,
  },
);
</script>

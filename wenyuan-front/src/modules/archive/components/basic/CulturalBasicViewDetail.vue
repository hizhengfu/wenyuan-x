<template>
  <div class="page-detail" ref="refDetail">
    <h2 class="text-center">文物保护单位登记表</h2>
    <div style="position: absolute; right: 10px; top: 10px">
      <Print :printable="refDetail" size="large"></Print>
    </div>
    <n-table class="info-table" :single-line="false">
      <tr>
        <td>公布名称</td>
        <td colSpan="3">{{ form.publishName || '--' }}</td>
        <td>其他名称</td>
        <td colSpan="4">{{ form.otherName || '--' }}</td>
      </tr>
      <tr>
        <td>公布批次</td>
        <td colSpan="3"><h-dict-text type="basic_public_batch" v-model:value="form.publishBatch"></h-dict-text></td>
        <td>公布分类号</td>
        <td colSpan="4">{{ form.publishClass || '--' }}</td>
      </tr>
      <tr>
        <td>公布编号</td>
        <td colSpan="3">{{ form.publishNo || '--' }}</td>
        <td>代码</td>
        <td colSpan="4">{{ form.publishCode || '--' }}</td>
      </tr>
      <tr>
        <td>公布地址</td>
        <td colSpan="8">
          {{ form.publishAddress }}
        </td>
      </tr>
      <tr>
        <td>现地址</td>
        <td colSpan="8">
          {{ form.currentAddress }}
        </td>
      </tr>
      <tr>
        <td rowSpan="3">面积</td>
        <td colSpan="2">保护单位面积</td>
        <td colSpan="6">{{ form.mjBhdw || '--' }}平方米</td>
      </tr>
      <tr>
        <td colSpan="2">保护范围面积</td>
        <td colSpan="6">{{ form.mjBhfw || '--' }}平方米</td>
      </tr>
      <tr>
        <td colSpan="2">建设控制地带面积</td>
        <td colSpan="6">{{ form.mjJskzdd || '--' }}平方米</td>
      </tr>
      <tr>
        <td>公布时代</td>
        <td colSpan="3">{{ form.publishYear || '--' }}</td>
        <td>时代研究信息</td>
        <td colSpan="4">{{ form.sdyjxx || '--' }}</td>
      </tr>
      <tr>
        <td>统计年代</td>
        <td colSpan="8">
          <h-dict-checkbox-text :value="form.tjYear" type="four_cultural_relic_tjnd" clearable></h-dict-checkbox-text>
        </td>
      </tr>
      <tr>
        <td>公布类别</td>
        <td colSpan="3"><h-dict-text v-model:value="form.publishCategory" type="four_cultural_relic_type"></h-dict-text>
        </td>
        <td>类别</td>
        <td colSpan="4"><h-dict-text v-model:value="form.category" type="four_cultural_relic_type"></h-dict-text></td>
      </tr>
      <tr>
        <td rowSpan="6">类别明细</td>
        <td>
          <n-radio :checked="form.mainCategory === '古遗址'" name="mainCategory">古遗址</n-radio>
        </td>
        <td colSpan="7">
          <h-dict-radio type="four_cultural_relic_type_gyz"
            :value="form.mainCategory === '古遗址' ? form.subCategory : ''"></h-dict-radio>
        </td>
      </tr>
      <tr>
        <td>
          <n-radio :checked="form.mainCategory === '古墓葬'" name="mainCategory">古墓葬</n-radio>
        </td>
        <td colSpan="7">
          <h-dict-radio type="four_cultural_relic_type_gmz"
            :value="form.mainCategory === '古墓葬' ? form.subCategory : ''"></h-dict-radio>
        </td>
      </tr>
      <tr>
        <td>
          <n-radio :checked="form.mainCategory === '古建筑'" name="mainCategory">古建筑</n-radio>
        </td>
        <td colSpan="7">
          <h-dict-radio type="four_cultural_relic_type_gjz"
            :value="form.mainCategory === '古建筑' ? form.subCategory : ''"></h-dict-radio>
        </td>
      </tr>
      <tr>
        <td>
          <n-radio :checked="form.mainCategory === '石窟寺及石刻'" name="mainCategory">石窟寺及石刻</n-radio>
        </td>
        <td colSpan="7">
          <h-dict-radio type="four_cultural_relic_type_sks"
            :value="form.mainCategory === '石窟寺及石刻' ? form.subCategory : ''"></h-dict-radio>
        </td>
      </tr>
      <tr>
        <td>
          <n-radio :checked="form.mainCategory === '近现代重要史迹及代表性建筑'" name="mainCategory">近现代重要史迹及代表性建筑</n-radio>
        </td>
        <td colSpan="7">
          <h-dict-radio type="four_cultural_relic_type_jxd"
            :value="form.mainCategory === '近现代重要史迹及代表性建筑' ? form.subCategory : ''"></h-dict-radio>
        </td>
      </tr>
      <tr>
        <td>
          <n-radio :checked="form.mainCategory === '其他'" name="mainCategory">其他</n-radio>
        </td>
        <td colSpan="7"></td>
      </tr>
      <tr>
        <td>公布机关</td>
        <td colSpan="4">{{ form.publishGroup || '--' }}</td>
        <td>公布日期</td>
        <td colSpan="3">{{ form.publishDate ? dayjs(form.publishDate).format('YYYY-MM-DD') : '--' }}</td>
        <!-- <td colSpan="3">{{ form.publishDate || '--' }}</td> -->
      </tr>
      <tr>
        <td>所有权</td>
        <td colSpan="8">
          <h-dict-checkbox-text type="four_cultural_relic_syq" :value="form.syq"></h-dict-checkbox-text>
        </td>
      </tr>
      <tr>
        <td rowSpan="2">使用情况</td>
        <td>使用人</td>
        <td colSpan="3" style="text-align: left">
          {{ form.syqkSyr || '--' }}
        </td>
        <td>隶属</td>
        <td colSpan="3" style="text-align: left">
          {{ form.syqkLs || '--' }}
        </td>
      </tr>
      <tr>
        <td>用途</td>
        <td colSpan="7">
          <h-dict-checkbox-text type="four_cultural_relic_syqk_yt" :value="form.syqkYt"></h-dict-checkbox-text>
        </td>
      </tr>
      <tr>
        <td colSpan="1">管理机构</td>
        <td colSpan="8">
          {{ form.gljg || '--' }}
        </td>
      </tr>
      <tr>
        <td colSpan="1">简要说明</td>
        <td colSpan="8">
          {{ form.jysm || '--' }}
        </td>
      </tr>
      <tr>
        <td>查看附件</td>
        <td colSpan="8">
          <h-file-list :contentId="form.id"></h-file-list>
        </td>
      </tr>
      <!-- <tr>
        <td>备注</td>
        <td colSpan="8">
          {{ form.remarks || '--' }}
        </td>
      </tr> -->
    </n-table>
  </div>
</template>

<script lang="ts" setup>
import { get } from '../../api/basic/culturalRecordBasic.ts';
import { ref, watchEffect } from 'vue';
import { cloneDeep } from 'lodash-es';
import Print from '@/components/basic/print/Print.vue';
import dayjs from 'dayjs';

const props = defineProps({
  id: {
    type: String,
  },
});

const refDetail = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  publishName: '',
  otherName: '',
  publishBatch: '',
  publishClass: '',
  publishNo: '',
  publishCode: '',
  publishAddress: '',
  currentAddress: '',
  dlzbJd: '',
  dlzbWd: '',
  dlzbGc: '',
  mjBhdw: '',
  mjBhfw: '',
  mjJskzdd: '',
  publishYear: '',
  sdyjxx: '',
  tjYear: '',
  publishCategory: '',
  category: '',
  mainCategory: '',
  subCategory: '',
  publishGroup: '',
  publishDate: null,
  syq: '',
  syqkSyr: '',
  syqkLs: '',
  syqkYt: '',
  gljg: '',
  jysm: '',
  bcxkXzpg: '',
  bcxkXzzk: '',
  csyyZrys: '',
  csyyRwys: '',
  csyyCsyyms: '',
  bhfwJskzdd: '',
  bhbzBzpCz: '',
  bhbzBzpSl: '',
  bhbzBzpLfjg: '',
  bhbzBzpGg: '',
  bhbzBzpLbrq: null,
  bhbzSmpCz: '',
  bhbzSmpSl: '',
  bhbzSmpLfjg: '',
  bhbzSmpGg: '',
  bhbzSmpLbrq: null,
  bhbzJzCz: '',
  bhbzJzSl: '',
  bhbzJzLfjg: '',
  bhbzJzGg: '',
  bhbzJzLbrq: null,
  bhjgxzMc: '',
  bhjgxzLb: '',
  bhjgxzFzr: '',
  bhjgxzRs: null,
  bhjgxzClsj: null,
  remarks: '',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 查看事件
 * */
function handleView() {
  if (props.id) {
    get(props.id).then((resp) => {
      if (resp.data) {
        Object.assign(form.value, resp.data);
      }
    });
  }
}

watchEffect(() => {
  handleView();
});
</script>

<style lang="scss" scoped>
.page-detail {
  table {
    table-layout: fixed;

    th {
      white-space: normal;
    }
  }
}
</style>

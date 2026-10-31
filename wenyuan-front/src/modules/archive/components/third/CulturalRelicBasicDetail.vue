<template>
  <div class="page-detail" ref="refDetail" >
    <h2 class="text-center">第三次全国文物普查不可移动文物登记表</h2>
    <div style="position: absolute; right: 10px; top: 10px">
      <Print :printable="refDetail" size="large"></Print>
    </div>
    <n-table class="info-table" :single-line="false" >
      <tr >
        <td >公布名称</td>
        <td colSpan="5">{{ form.name || '--' }}</td>
        <td>代码</td>
        <td colSpan="2">{{ form.code || '--' }}</td>
      </tr>
      <tr>
        <td>地址及位置</td>
        <td colSpan="8">{{ form.address || '--' }}</td>
      </tr>
      <tr>
        <td rowSpan="3">GPS坐标</td>
        <td colSpan="3">纬度</td>
        <td colSpan="3">经度</td>
        <td colSpan="2">海拔高程</td>
      </tr>
      <tr>
        <td colSpan="3">{{ form.latitude || '--' }}</td>
        <td colSpan="3">
          {{ form.longitude || '--' }}
        </td>
        <td colSpan="2">{{ form.altitude || '--' }}米</td>
      </tr>
      <tr>
        <td colSpan="1">测点说明</td>
        <td colSpan="7">
          {{ form.cdsm || '--' }}
        </td>
      </tr>
      <tr>
        <td>级别</td>
        <td colSpan="8">
          <h-dict-radio type="third_cultural_relic_level" :value="form.level"></h-dict-radio>
        </td>
      </tr>
      <tr>
        <td rowSpan="2">面 积</td>
        <td colSpan="2">分布面积(㎡)</td>
        <td colSpan="2">{{ form.mjFb || '--' }}平方米</td>
        <td colSpan="2">保护范围面积(㎡)</td>
        <td colSpan="2">{{ form.mjBh || '--' }}平方米</td>
      </tr>
      <tr>
        <td colSpan="2">建筑占地面积(㎡)</td>
        <td colSpan="2">{{ form.mjJzzd || '--' }}平方米</td>
        <td colSpan="2">建设控制地带面积(㎡)</td>
        <td colSpan="2">{{ form.mjJskzdz || '--' }}平方米</td>
      </tr>
      <tr>
        <td colSpan="1">年代</td>
        <td colSpan="8">
          {{ form.year || '--' }}
        </td>
      </tr>
      <tr>
        <td>统计年代</td>
        <td colSpan="8">
          <h-dict-checkbox-text :value="form.tjYear" type="third_cultural_relic_tjyear" clearable></h-dict-checkbox-text>
        </td>
      </tr>
      <tr>
        <td rowSpan="6">类别</td>
        <td>
          <n-radio :checked="form.type === '古遗址'" name="type">古遗址</n-radio>
        </td>
        <td colSpan="7">
          <h-dict-radio
            type="third_cultural_relic_type_gyz"
            :value="form.type === '古遗址' ? form.subType : ''"
          ></h-dict-radio>
        </td>
      </tr>
      <tr>
        <td>
          <n-radio :checked="form.type === '古墓葬'" name="type">古墓葬</n-radio>
        </td>
        <td colSpan="7">
          <h-dict-radio
            type="third_cultural_relic_type_gmz"
            :value="form.type === '古墓葬' ? form.subType : ''"
          ></h-dict-radio>
        </td>
      </tr>
      <tr>
        <td>
          <n-radio :checked="form.type === '古建筑'" name="type">古建筑</n-radio>
        </td>
        <td colSpan="7">
          <h-dict-radio
            type="third_cultural_relic_type_gjz"
            :value="form.type === '古建筑' ? form.subType : ''"
          ></h-dict-radio>
        </td>
      </tr>
      <tr>
        <td>
          <n-radio :checked="form.type === '石窟寺及石刻'" name="type">石窟寺及石刻</n-radio>
        </td>
        <td colSpan="7">
          <h-dict-radio
            type="third_cultural_relic_type_sks"
            :value="form.type === '石窟寺及石刻' ? form.subType : ''"
          ></h-dict-radio>
        </td>
      </tr>
      <tr>
        <td>
          <n-radio :checked="form.type === '近现代重要史迹及代表性建筑'" name="type">近现代重要史迹及代表性建筑</n-radio>
        </td>
        <td colSpan="7">
          <h-dict-radio
            type="third_cultural_relic_type_jxd"
            :value="form.type === '近现代重要史迹及代表性建筑' ? form.subType : ''"
          ></h-dict-radio>
        </td>
      </tr>
      <tr>
        <td>
          <n-radio :checked="form.type === '其他'" name="type">其他</n-radio>
        </td>
        <td colSpan="7"></td>
      </tr>
      <tr>
        <td>所有权</td>
        <td colSpan="8">
          <h-dict-radio type="third_cultural_relic_syq" :value="form.syq"></h-dict-radio>
        </td>
      </tr>
      <tr>
        <td rowSpan="2">使用情况</td>
        <td>使用单位（或人）</td>
        <td colSpan="3" style="text-align: left">
          {{ form.syqkSydw || '--' }}
        </td>
        <td>隶属</td>
        <td colSpan="3" style="text-align: left">
          {{ form.syqkLs || '--' }}
        </td>
      </tr>
      <tr>
        <td>用途</td>
        <td colSpan="7">
          <h-dict-checkbox-text type="third_cultural_relic_syqk_yt" :value="form.syqkYt"></h-dict-checkbox-text>
        </td>
      </tr>
      <tr>
        <td rowSpan="2">单体文物</td>
        <td>数量（个）</td>
        <td colSpan="7">
          {{ form.dtNum || '--' }}
        </td>
      </tr>
      <tr>
        <td>说明</td>
        <td colSpan="7">
          {{ form.dtRemark || '--' }}
        </td>
      </tr>
      <tr>
        <td>简介</td>
        <td colSpan="8">
          {{ form.description || '--' }}
        </td>
      </tr>
      <tr>
        <td rowSpan="2">保存状况</td>
        <td>现状评估</td>
        <td colSpan="7">
          <h-dict-radio type="third_cultural_relic_bzzk_xzpg" :value="form.bzzkXzpg"></h-dict-radio>
        </td>
      </tr>
      <tr>
        <td>现状描述</td>
        <td colSpan="7">
          {{ form.bzzkXzms || '--' }}
        </td>
      </tr>
      <tr>
        <td rowSpan="3">损毁原因</td>
        <td>自然因素</td>
        <td colSpan="7">
          <h-dict-checkbox-text type="third_cultural_relic_shyy_zrys" :value="form.shyyZrys"></h-dict-checkbox-text>
        </td>
      </tr>
      <tr>
        <td>人为因素</td>
        <td colSpan="7">
          <h-dict-checkbox-text type="third_cultural_relic_shyy_rwys" :value="form.shyyRwys"></h-dict-checkbox-text>
        </td>
      </tr>
      <tr>
        <td>损毁原因描述</td>
        <td colSpan="7">
          {{ form.shyyShyyms || '--' }}
        </td>
      </tr>
      <tr>
        <td rowSpan="2">环境状况</td>
        <td>自然环境</td>
        <td colSpan="7">
          {{ form.hjzkZrhj || '--' }}
        </td>
      </tr>
      <tr>
        <td>人文环境</td>
        <td colSpan="7">
          {{ form.hjzkRwhj || '--' }}
        </td>
      </tr>
      <tr>
        <td>普查组建议</td>
        <td colSpan="8">
          {{ form.pzzjy || '--' }}
        </td>
      </tr>
      <tr>
        <td>审核意见</td>
        <td colSpan="8">
          {{ form.rhyj || '--' }}
        </td>
      </tr>
      <tr>
        <td>抽查结论</td>
        <td colSpan="8">
          {{ form.ccjl || '--' }}
        </td>
      </tr>
      <tr>
        <td>备注</td>
        <td colSpan="8">
          {{ form.remark || '--' }}
        </td>
      </tr>
    </n-table>
  </div>
</template>

<script lang="ts" setup>
import { get } from '../../api/third/thirdCulturalRelicBasic';
import { ref ,onMounted} from 'vue';
import { cloneDeep } from 'lodash-es';
import { watchEffect } from 'vue';
import Print from '@/components/basic/print/Print.vue';

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
  parentId: '',
  parentIds: '',
  areaId: '',
  groupId: '',
  name: '',
  code: '',
  address: '',
  latitude: '',
  longitude: '',
  altitude: '',
  level: '',
  mjFb: '',
  mjBh: '',
  mjJzzd: '',
  mjJskzdz: '',
  year: '',
  tjYear: '',
  type: '',
  syq: '',
  syqkSydw: '',
  syqkLs: '',
  syqkYt: '',
  dtNum: '',
  dtRemark: '',
  description: '',
  bzzkXzpg: '',
  bzzkXzms: '',
  shyyZrys: '',
  shyyRwys: '',
  shyyShyyms: '',
  hjzkZrhj: '',
  hjzkRwhj: '',
  pzzjy: '',
  rhyj: '',
  remark: '',
  lng: '',
  lat: '',
  ccjl: '',
  subType: '',
  cdsm: '',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
  status: '',
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
onMounted(() => { handleView();})
// watchEffect(() => {
//   handleView();
// });
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

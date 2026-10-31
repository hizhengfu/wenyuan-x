<template>
  <div class="page-detail" ref="refDetail" v-if="culturalType !== undefined">
    <h2 class="text-center">
      第四次全国文物普查不可移动文物登记表(
      <h-dict-text v-model:value="culturalType" type="fourth_cultural_relics_type_one"></h-dict-text>
      )
    </h2>
    <div style="position: absolute; right: 10px; top: 10px">
      <Print :printable="refDetail" size="large"></Print>
    </div>
    <n-table class="info-table" :single-line="false">
      <tr>
        <td style="font-weight: bold">名称</td>
        <td colSpan="5">{{ form.name || '--' }}</td>
        <td style="font-weight: bold">代码</td>
        <td colSpan="2">{{ form.code || '--' }}</td>
      </tr>
      <tr v-if="culturalType == '古文化遗址' || culturalType == '其他'">
        <td style="font-weight: bold">地址及位置</td>
        <td colSpan="8">{{ form.address || '---' }}</td>
      </tr>
      <template v-else>
        <tr>
          <td rowSpan="3" style="font-weight: bold">地址及位置</td>
          <td colSpan="8">{{ form.address || '---' }}</td>
        </tr>
        <tr>
          <td colspan="3" style="font-weight: bold">迁移情况</td>
          <td colspan="5">
            <div style="display: flex; justify-content: space-between; flex-direction: column">
              <span>是否整体迁移并在新迁址占有独立地域范围</span>
              <h-dict-radio :value="form.qyqk" type="isMigration"></h-dict-radio>
            </div>
          </td>
        </tr>
        <tr>
          <td colspan="3" style="font-weight: bold">变更消失情况</td>
          <td colspan="5">
            <div style="display: flex; justify-content: space-between; flex-direction: column">
              <span>是否存在变更为可移动文物或消失的情况</span>
              <h-dict-radio :value="form.qyqk" type="fourth_change_disappear_status"></h-dict-radio>
            </div>
          </td>
        </tr>
      </template>
      <tr>
        <td>本体边界坐标</td>
        <td colSpan="8" style="padding: 0">
          <n-table :single-line="false" :bordered="false">
            <colgroup>
              <col style="width: 60px" />
            </colgroup>
      <tr v-if="gpsData?.length > gpsPageSize">
        <td colspan="8" style="text-align: center; padding: 8px">
          <n-pagination v-model:page="gpsPage" :page-count="Math.ceil(gpsData.length / gpsPageSize)" :page-slot="5" />
        </td>
      </tr>
      <tr>
        <td style="text-align: center">序号</td>
        <td>分组</td>
        <td>测点类型</td>
        <td>纬度</td>
        <td>经度</td>
        <td>海拔高程</td>
        <td colspan="2" style="font-weight: bold">测点(边界)说明</td>
      </tr>
      <template v-if="gpsData?.length > 0">
        <tr v-for="(item, index) in pagedGpsData" :key="item.id">
          <td style="text-align: center">{{ (gpsPage - 1) * gpsPageSize + index + 1 }}</td>
          <!-- <td><h-dict-text type="fourth_gps_group" v-model:value="item.culturalGroup"></h-dict-text></td> -->
          <td>{{ item.culturalGroup }}</td>
          <!-- <td><h-dict-text type="fourth_measuring_points_type" v-model:value="item.pointType"></h-dict-text></td> -->
          <td>{{ item.pointType }}</td>
          <td>{{ item.lat || '--' }}</td>
          <td>{{ item.lng || '--' }}</td>
          <td>{{ item.altitude ? `${item.altitude}` : '--' }}</td>
          <td colspan="2">{{ item.description || '--' }}</td>
        </tr>
      </template>
      <template v-else>
        <tr v-for="n in 2" :key="`empty-${n}`">
          <td>{{ n }}</td>
          <td></td>
          <td></td>
          <td></td>
          <td></td>
          <td></td>
          <td colspan="2"></td>
        </tr>
      </template>
      <tr v-if="gpsData?.length > gpsPageSize">
        <td colspan="8" style="text-align: center; padding: 8px">
          <n-pagination v-model:page="gpsPage" :page-count="Math.ceil(gpsData.length / gpsPageSize)" :page-slot="5" />
        </td>
      </tr>
    </n-table>
    </td>
    </tr>

    <tr>
      <td style="font-weight: bold">面积</td>
      <td colspan="8">{{ form.mjFb || '--' }} 平方米</td>
    </tr>

    <tr>
      <td rowSpan="2" style="font-weight: bold">文物级别</td>
      <td colSpan="8">
        <h-dict-radio :value="form.level" type="fourth_heritage_level"></h-dict-radio>
      </td>
    </tr>
    <tr>
      <td colspan="3" style="font-weight: bold">所属文物保护单位名称</td>
      <td colspan="5">
        {{ form.bhdwmc || '--' }}
      </td>
    </tr>
    <tr>
      <td rowspan="2" style="font-weight: bold">保护区划</td>
      <td colspan="3" style="font-weight: bold">是否已公布保护范围</td>
      <td colspan="5" style="font-weight: bold">
        <h-dict-radio :value="form.bhfwFlag" type="isMigration"></h-dict-radio>
      </td>
    </tr>
    <tr>
      <td colspan="3" style="font-weight: bold">是否已公布建设控制地带</td>
      <td colspan="5">
        <h-dict-radio :value="form.jskzddFlag" type="isMigration"></h-dict-radio>
      </td>
    </tr>

    <tr>
      <td colSpan="1" style="font-weight: bold">年代</td>
      <td colSpan="8">
        {{ form.year || '--' }}
      </td>
    </tr>
    <tr>
      <td style="font-weight: bold">统计年代</td>
      <td colSpan="8">
        <h-dict-checkbox-text :value="form.tjYear" :type="getTjYear(culturalType)"></h-dict-checkbox-text>
      </td>
    </tr>
    <tr v-if="culturalType != '近现代重要史迹及代表性建筑'">
      <td style="font-weight: bold">类别</td>
      <td colSpan="8">
        <h-dict-radio :value="form.type" :type="getType(culturalType)"></h-dict-radio>
      </td>
    </tr>
    <tr v-else>
      <td style="font-weight: bold">类别</td>
      <td colSpan="8">
        <span style="font-weight: bold">近现代重要史迹</span>
        <h-dict-radio :value="form.type" type="fourth_jxdzysj"></h-dict-radio>
        <span style="font-weight: bold">近现代代表性建筑</span>
        <h-dict-radio :value="form.type" type="fourth_jxddbxjz"></h-dict-radio>
      </td>
    </tr>
    <tr v-if="culturalType == '古文化遗址' || culturalType == '古墓葬'">
      <td style="font-weight: bold">所有权</td>
      <td colSpan="8">
          <h-dict-checkbox
            :value="form.syq"
            :type="
              culturalType == '古文化遗址' || culturalType == '古墓葬'
            ? 'fourth_ownership_countries'
            : 'fourth_ownership'
            "
          ></h-dict-checkbox>
      </td>
    </tr>
    <template v-else>
      <tr>
        <td rowSpan="2" style="font-weight: bold">所有权</td>
        <td colSpan="8">
          <h-dict-checkbox-text :value="form.syq" type="fourth_ownership"></h-dict-checkbox-text>
        </td>
      </tr>
      <tr>
        <td colspan="2" style="font-weight: bold">产权单位(人)</td>
        <td colspan="6">
          {{ form.syqCqdw || '--' }}
        </td>
      </tr>
    </template>
    <tr>
      <td rowspan="4" style="font-weight: bold">使用情况</td>
      <td colspan="2" style="font-weight: bold">使用单位(人)</td>
      <td colspan="2">{{ form.syqkSydw || '--' }}</td>
      <td colspan="2" style="font-weight: bold">上级管理机构</td>
      <td colspan="2">{{ form.syqkLs || '--' }}</td>
    </tr>
    <tr>
      <td colspan="2" style="font-weight: bold">所属行业、系统</td>
      <td colspan="6">
        <h-dict-checkbox-text :value="form.syqkSshy" type="fourth_industry"></h-dict-checkbox-text>
      </td>
    </tr>
    <tr>
      <td colspan="2" style="font-weight: bold">开放状况</td>
      <td colspan="6">
        <h-dict-radio :value="form.kfzt" type="fourth_open_state"></h-dict-radio>
      </td>
    </tr>
    <tr>
      <td colspan="2" style="font-weight: bold">使用用途</td>
      <td colspan="6">
        <h-dict-checkbox-text :value="form.syqkYt" type="fourth_use"></h-dict-checkbox-text>
      </td>
    </tr>

    <tr>
      <td rowspan="2" style="font-weight: bold">文物构成</td>
      <td>单体文物</td>
      <td colspan="7" style="padding: 0">
        <!-- 单体文物区块 -->
        <n-table :single-line="false" :bordered="false">
    <tr v-if="monomerData?.length > monomerPageSize">
      <td colspan="7" style="text-align: center; padding: 8px">
        <n-pagination v-model:page="monomerPage" :page-count="Math.ceil(monomerData.length / monomerPageSize)"
          :page-slot="5" />
      </td>
    </tr>
    <tr>
      <td style="width: 60px; text-align: center">序号</td>
      <td colspan="2" style="font-weight: bold">名称</td>
      <td colspan="2" style="font-weight: bold">类别</td>
      <td colspan="2" style="font-weight: bold">面积/数量</td>
    </tr>
    <template v-if="monomerData?.length > 0">
      <tr v-for="(item, index) in pagedMonomerData" :key="'single-' + item.id">
        <td style="text-align: center">{{ (monomerPage as any - 1) * monomerPageSize as any + index + 1 }}</td>
        <td colspan="2">{{ item.name || '--' }}</td>
        <td colspan="2">
          {{ item.type || '--' }}
        </td>
        <td colspan="2">{{ item.mjSl || '--' }}</td>
      </tr>
    </template>
    <template v-else>
      <tr v-for="n in 2" :key="`empty-${n}`">
        <td style="text-align: center">{{ n }}</td>
        <td colspan="2"></td>
        <td colspan="2"></td>
        <td colspan="2"></td>
      </tr>
    </template>
    <tr v-if="monomerData?.length > monomerPageSize">
      <td colspan="7" style="text-align: center; padding: 8px">
        <n-pagination v-model:page="monomerPage" :page-count="Math.ceil(monomerData.length / monomerPageSize)"
          :page-slot="5" />
      </td>
    </tr>
    </n-table>
    </td>
    </tr>

    <!-- 附属文物区块 -->
    <tr>
      <td style="font-weight: bold">附属文物</td>
      <td colspan="7" style="padding: 0">
        <n-table :single-line="false" :bordered="false">
    <tr>
      <td style="width: 60px; text-align: center">序号</td>
      <td colspan="4" style="font-weight: bold">名称/类别</td>
      <td colspan="2" style="font-weight: bold">面积/数量</td>
    </tr>
    <template v-if="subsidiaryData?.length > 0">
      <tr v-for="(item, index) in subsidiaryData" :key="'attach-' + item.id">
        <td style="text-align: center">{{ index as any + 1 }}</td>
        <td colspan="4">
          {{ item.name || '--' }} / {{ item.type || '--' }}
          <!-- <h-dict-text :type="`fourth_cultural_${culturalType}`" v-model:value="item.type"></h-dict-text> -->
        </td>
        <td colspan="2">{{ item.mjSl || '--' }}</td>
      </tr>
    </template>
    <template v-else>
      <tr v-for="n in 2" :key="`empty-${n}`">
        <td style="text-align: center">{{ n }}</td>
        <td colspan="4"></td>
        <td colspan="2"></td>
      </tr>
    </template>
    </n-table>
    </td>
    </tr>
    <tr>
      <td style="font-weight: bold">简介</td>
      <td colSpan="8">
        {{ form.description || '--' }}
      </td>
    </tr>
    <tr>
      <td rowspan="3" style="font-weight: bold">保存状况</td>
      <td colspan="2" style="font-weight: bold">现状评估</td>
      <td colspan="6">
        <h-dict-radio :value="form.bzzkXzpg" type="fourth_present_situation_appraisal"></h-dict-radio>
      </td>
    </tr>
    <tr>
      <td colspan="2">
        <div style="display: flex; flex-direction: column; justify-content: space-between">
          <span style="font-weight: bold">保护措施</span>
          <span>（三普以来已完成）</span>
        </div>
      </td>
      <td colspan="6">
        <h-dict-checkbox-text :value="form.bzzkBhcs" type="fourth_protection_measures"></h-dict-checkbox-text>
      </td>
    </tr>
    <tr>
      <td colspan="2" style="font-weight: bold">影响因素</td>
      <td colspan="6">
        <h-dict-checkbox-text :value="form.bzzjYxys" type="fourth_influencing_factors"></h-dict-checkbox-text>
      </td>
    </tr>
    <tr>
      <td style="font-weight: bold">专题类别</td>
      <td colSpan="8">
        <div style="display: flex; flex-direction: column; justify-content: space-between">
          <span style="font-weight: bold">是否已经列为以下名录、规划和数据库</span>
          <h-dict-checkbox-text :value="form.ztlb" type="fourth_thematic_type"></h-dict-checkbox-text>
        </div>
      </td>
    </tr>
    <tr>
      <td style="font-weight: bold">其他名录</td>
      <td colSpan="8">
        <div style="display: flex; flex-direction: column; justify-content: space-between">
          <span style="font-weight: bold">是否列入以下名录、称号</span>
          <h-dict-checkbox-text :value="form.qtml" type="fourth_other_list"></h-dict-checkbox-text>
        </div>
      </td>
    </tr>

    <tr>
      <td style="font-weight: bold">审核意见</td>
      <td colSpan="8">
        {{ form.shyj || '--' }}
      </td>
    </tr>
    <tr>
      <td style="font-weight: bold">抽查结论</td>
      <td colSpan="8">
        {{ form.ccjl || '--' }}
      </td>
    </tr>
    <tr>
      <td style="font-weight: bold">备注</td>
      <td colSpan="8">
        {{ form.remark || '--' }}
      </td>
    </tr>
    </n-table>
  </div>
</template>

<script lang="ts" setup>
import { get, list as basicList } from '../../api/fourth/fourthCulturalRelicBasic';
import { get as coverGet } from '../../api/fourth/fourthCulturalRelicCover';
import { list as gpsList } from '../../api/fourth/fourthCulturalRelicGps';
import { list as monomerList } from '../../api/fourth/fourthCulturalRelicMonomer';
import { list as subsidiarList } from '../../api/fourth/fourthCulturalRelicSubsidiary';
import { ref, computed, onUnmounted, onMounted } from 'vue';
import { cloneDeep } from 'lodash-es';
import { watchEffect } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
import Print from '@/components/basic/print/Print.vue';

const props = defineProps({
  fourthCulturalRecordId: {
    type: String,
    required: true,
  },
  title: {
    type: String,
  },
});
const culturalType = ref();
const refDetail = ref();
const gpsData = ref(); //gps数据
const monomerData = ref(); // 单体文物数据
const subsidiaryData = ref(); // 附属文物数据

// GPS 分页
const gpsPage = ref(1);
const gpsPageSize = 30;
const pagedGpsData = computed(() => {
  if (!gpsData.value?.length) return [];
  const start = (gpsPage.value - 1) * gpsPageSize;
  return gpsData.value.slice(start, start + gpsPageSize);
});

// 单体文物分页
const monomerPage = ref(1);
const monomerPageSize = 30;
const pagedMonomerData = computed(() => {
  if (!monomerData.value?.length) return [];
  const start = (monomerPage.value - 1) * monomerPageSize;
  return monomerData.value.slice(start, start + monomerPageSize);
});


/**
 * 表单默认值
 * */
const defaultForm = {
  isLeaf: true,
  culturalRelicId: '',
  areaId: '',
  groupId: '',
  name: '',
  code: '',
  address: '',
  qyqk: '',
  latitude: '',
  longitude: '',
  altitude: '',
  cdsm: '',
  mjFb: '',
  level: '',
  bhdwmc: '',
  mjBh: '',
  mjJzzd: '',
  mjJskzdz: '',
  bhfwFlag: '',
  jskzddFlag: '',
  year: '',
  tjYear: '',
  type: '',
  subType: '',
  syq: '',
  syqCqdw: '',
  syqkSydw: '',
  syqkLs: '',
  syqkSshy: '',
  kfzt: '',
  syqkYt: '',
  dtNum: '',
  dtRemark: '',
  description: '',
  bzzkXzpg: '',
  bzzkBhcs: '',
  bzzjYxys: '',
  ztlb: '',
  qtml: '',
  bzzkXzms: '',
  shyyZrys: '',
  shyyRwys: '',
  shyyShyyms: '',
  hjzkZrhj: '',
  hjzkRwhj: '',
  pzzjy: '',
  shyj: '',
  ccjl: '',
  remark: '',
  lng: '',
  lat: '',

  status: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 查看事件
 * */
const handleView = () => {
  return new Promise((resolve, reject) => {
    if (props.fourthCulturalRecordId) {
      basicList({ culturalRelicId: props.fourthCulturalRecordId || route.query.fourthCulturalRecordId }).then(
        (resp) => {
          if (resp?.data?.list) {
            const data = resp.data.list[0];
            if (data) {
              get(data.id).then((getResp) => {
                if (getResp.data) {
                  Object.assign(form.value, getResp.data);
                }
              });
            }
          }
        },
      );
    }
  });
};

const getTjYear = (type: any) => {
  switch (type) {
    case '古文化遗址':
      return 'fourth_gwh_gmz_tjyear';
    case '古墓葬':
      return 'fourth_gwh_gmz_tjyear';
    case '古建筑':
      return 'fourth_gjz_tjyear';
    case '石窟寺及石刻':
      return 'fourth_sksjsk_tjyear';
    case '近现代重要史迹及代表性建筑':
      return 'fourth_jxd_tjyear';
    case '其他':
      return 'fourth_qt_tjyear';
    default:
      break;
  }
};
const getType = (type: any) => {
  switch (type) {
    case '古文化遗址':
      return 'fourth_cultural_0';
    case '古墓葬':
      return 'fourth_cultural_1';
    case '古建筑':
      return 'fourth_cultural_2';
    case '石窟寺及石刻':
      return 'fourth_cultural_3';
    case '近现代重要史迹及代表性建筑':
      return 'fourth_cultural_4';
    case '其他':
      return 'fourth_cultural_5';
    default:
      break;
  }
};
const loadGpsData = async () => {
  if (!props.fourthCulturalRecordId) return;
  const res = await gpsList({ culturalRelicId: props.fourthCulturalRecordId || route.query.fourthCulturalRecordId, pageSize: -1 });
  gpsData.value = res.data?.list || [];
};

const loadCulturalData = async () => {
  // if (!props.fourthCulturalRecordId) return;
  let culturalRelicId = props.fourthCulturalRecordId || route.query.fourthCulturalRecordId;

  //加载单体数据
  const monomerRes = await monomerList({ culturalRelicId: culturalRelicId, pageSize: -1 });

  monomerData.value = monomerRes?.data.list || [];

  // 加载附属数据
  const subsidiaryRes = await subsidiarList({ culturalRelicId: culturalRelicId, pageSize: -1 });
  subsidiaryData.value = subsidiaryRes?.data.list || [];
};
const handleCoverData = async () => {
  // if (!props.fourthCulturalRecordId) return;
  let culturalRelicId = props.fourthCulturalRecordId || route.query.fourthCulturalRecordId;
  const coverRes = await coverGet(culturalRelicId);
  if (coverRes && coverRes.data && coverRes.data.type) {
    console.log('000000000077777', coverRes.data.type);

    culturalType.value = coverRes.data.type;
  }
};

const loadAllData = async () => {
  await handleCoverData();
  await Promise.allSettled([handleView(), loadGpsData(), loadCulturalData()]);
};
onMounted(async () => await loadAllData());
</script>

<style lang="scss" scoped>
.page-detail {
  table {
    // table-layout: fixed;

    th {
      white-space: normal;
    }
  }
}
</style>

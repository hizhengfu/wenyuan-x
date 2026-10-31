<template>
  <h-page-data-table v-if="props.showList" ref="refPageTable" :fetch-data="list" :scroll-x="1200" :params="paramsForm"
    @add="handleAdd" @rowEdit="handleEdit" @rowView="handleView" :funApiDelete="del" :columns="columns">
    <template #queryForm v-if="props.queryShow">
      <!-- <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item> -->
      <n-form-item label="类型" path="type">
        <h-dict-select v-model:value="paramsForm.type" type="third_cultural_relic_type"
          placeholder="请选择类型"></h-dict-select>
      </n-form-item>
      <n-form-item label="内容状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="content_status" clearable filterable
          placeholder="请选择内容状态"></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form :rules="rules" :model="form" label-width="auto" :title="formTitle" :refTable="refPageTable"
      :handleSubmit="handleSubmit" v-model:show="formModel">
      <n-grid x-gap="10">
        <n-gi span="16">
          <n-form-item label="名称" path="name" label-placement="left">
            <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="代码" path="code" label-placement="left">
            <n-input v-model:value="form.code" placeholder="请输入代码" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="地址及位置" path="address" label-placement="left">
        <n-input v-model:value="form.address" placeholder="请输入地址及位置" clearable></n-input>
      </n-form-item>
      <n-descriptions bordered label-placement="top" style="margin-top: 15px">
        <n-descriptions-item label="GPS坐标">
          <n-grid x-gap="10">
            <n-gi span="8">
              <n-form-item label="纬度" path="latitude">
                <n-input v-model:value="form.latitude" placeholder="请输入纬度（度分秒）" clearable></n-input>
              </n-form-item>
            </n-gi>
            <n-gi span="8">
              <n-form-item label="经度" path="longitude">
                <n-input v-model:value="form.longitude" placeholder="请输入经度（度分秒）" clearable></n-input>
              </n-form-item>
            </n-gi>
            <n-gi span="8">
              <n-form-item label="海拔高程(米)" path="altitude">
                <n-input-group>
                  <n-input v-model:value="form.altitude" placeholder="请输入海拔高程" clearable></n-input>
                  <n-input-group-label>米</n-input-group-label>
                </n-input-group>
              </n-form-item>
            </n-gi>
          </n-grid>
          <n-form-item label="测点说明" path="cdsm" label-placement="left">
            <n-input v-model:value="form.cdsm" placeholder="请输入测点说明" clearable></n-input>
          </n-form-item>
        </n-descriptions-item>
      </n-descriptions>
      <n-form-item label="级别" path="level" label-placement="left" style="margin-top: 30px">
        <h-dict-radio type="third_cultural_relic_level" v-model:value="form.level"></h-dict-radio>
      </n-form-item>
      <n-descriptions bordered label-placement="top">
        <n-descriptions-item label="面积">
          <n-grid x-gap="10">
            <n-gi span="12">
              <n-form-item label="分布面积(㎡)" path="mjFb">
                <n-input-group>
                  <n-input v-model:value="form.mjFb" placeholder="请输入分布面积(㎡)" clearable></n-input>
                  <n-input-group-label>平方米</n-input-group-label>
                </n-input-group>
              </n-form-item>
              <n-form-item label="建筑占地面积(㎡)" path="mjJzzd">
                <n-input-group>
                  <n-input v-model:value="form.mjJzzd" placeholder="请输入建筑占地面积(㎡)" clearable></n-input>
                  <n-input-group-label>平方米</n-input-group-label>
                </n-input-group>
              </n-form-item>
            </n-gi>
            <n-gi span="12">
              <n-form-item label="保护范围面积(㎡)" path="mjBh">
                <n-input-group>
                  <n-input v-model:value="form.mjBh" placeholder="请输入保护范围面积(㎡)" clearable></n-input>
                  <n-input-group-label>平方米</n-input-group-label>
                </n-input-group>
              </n-form-item>
              <n-form-item label="建设控制地带面积(㎡)" path="mjJskzdz">
                <n-input-group>
                  <n-input v-model:value="form.mjJskzdz" placeholder="请输入建设控制地带面积(㎡)" clearable></n-input>
                  <n-input-group-label>平方米</n-input-group-label>
                </n-input-group>
              </n-form-item>
            </n-gi>
          </n-grid>
        </n-descriptions-item>
      </n-descriptions>
      <n-form-item label="年代" path="year" label-placement="left" style="margin-top: 30px">
        <n-input v-model:value="form.year" placeholder="请输入年代" clearable></n-input>
      </n-form-item>
      <n-form-item label="统计年代" path="tjYear" label-placement="left">
        <h-dict-checkbox v-model:value="form.tjYear" type="third_cultural_relic_tjyear" clearable></h-dict-checkbox>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="类型" path="type" label-placement="left">
            <h-dict-select v-model:value="form.type" type="third_cultural_relic_type"></h-dict-select>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="子类型" path="subType"  label-placement="left">
            <h-dict-select v-model:value="form.subType" type="third_cultural_relic_type_detail"></h-dict-select>
          </n-form-item>
          <!-- <n-form-item label="子类型" path="subType" v-if="form.type == '1'" label-placement="left">
            <h-dict-select v-model:value="form.subType" type="third_cultural_relic_type_gmz"></h-dict-select>
          </n-form-item>
          <n-form-item label="子类型" path="subType" v-if="form.type == '2'" label-placement="left">
            <h-dict-select v-model:value="form.subType" type="third_cultural_relic_type_gjz"></h-dict-select>
          </n-form-item>
          <n-form-item label="子类型" path="subType" v-if="form.type == '3'" label-placement="left">
            <h-dict-select v-model:value="form.subType" type="third_cultural_relic_type_sks"></h-dict-select>
          </n-form-item>
          <n-form-item label="子类型" path="subType" v-if="form.type == '4'" label-placement="left">
            <h-dict-select v-model:value="form.subType" type="third_cultural_relic_type_jxd"></h-dict-select>
          </n-form-item>
          <n-form-item label="子类型" path="subType" v-if="form.type == '5' || form.type == ''" label-placement="left">
            <h-dict-select v-model:value="form.subType" type="third_cultural_relic_type" disabled></h-dict-select>
          </n-form-item> -->
        </n-gi>
      </n-grid>
      <n-form-item label="所有权" path="syq" label-placement="left">
        <h-dict-radio type="third_cultural_relic_syq" v-model:value="form.syq"></h-dict-radio>
      </n-form-item>
      <n-descriptions bordered label-placement="top">
        <n-descriptions-item label="使用情况">
          <n-grid x-gap="10">
            <n-gi span="16">
              <n-form-item label="使用单位（人）" path="syqkSydw">
                <n-input v-model:value="form.syqkSydw" placeholder="请输入使用单位（人）" clearable></n-input>
              </n-form-item>
            </n-gi>
            <n-gi span="8">
              <n-form-item label="隶属" path="syqkLs">
                <n-input v-model:value="form.syqkLs" placeholder="请输入隶属" clearable></n-input>
              </n-form-item>
            </n-gi>
          </n-grid>
          <n-form-item label="用途" path="syqkYt" label-placement="left">
            <h-dict-checkbox v-model:value="form.syqkYt" type="third_cultural_relic_syqk_yt"
              clearable></h-dict-checkbox>
          </n-form-item>
        </n-descriptions-item>
      </n-descriptions>
      <n-descriptions bordered label-placement="top" style="margin-top: 30px">
        <n-descriptions-item label="单体文物">
          <n-form-item label="数量(个)" path="dtNum">
            <n-input-number v-model:value="form.dtNum" placeholder="请输入单体文物数量(个)" clearable></n-input-number>
          </n-form-item>
          <n-form-item label="说明" path="dtRemark">
            <n-input v-model:value="form.dtRemark" placeholder="请输入单体文物说明" type="textarea"></n-input>
          </n-form-item>
        </n-descriptions-item>
      </n-descriptions>
      <n-form-item label="简介" path="description" style="margin-top: 30px">
        <n-input v-model:value="form.description" placeholder="请输入简介" type="textarea"></n-input>
      </n-form-item>
      <n-descriptions bordered label-placement="top">
        <n-descriptions-item label="使用情况">
          <n-form-item label="现状评估" path="bzzkXzpg">
            <h-dict-radio type="third_cultural_relic_bzzk_xzpg" v-model:value="form.bzzkXzpg"></h-dict-radio>
          </n-form-item>
          <n-form-item label="现状描述" path="bzzkXzms">
            <n-input v-model:value="form.bzzkXzms" placeholder="请输入现状描述" clearable type="textarea"></n-input>
          </n-form-item>
        </n-descriptions-item>
      </n-descriptions>
      <n-descriptions bordered label-placement="top" style="margin-top: 30px">
        <n-descriptions-item label="损毁原因">
          <n-form-item label="自然因素" path="shyyZrys">
            <h-dict-checkbox v-model:value="form.shyyZrys" type="third_cultural_relic_shyy_zrys"
              clearable></h-dict-checkbox>
          </n-form-item>
          <n-form-item label="人为因素" path="shyyRwys">
            <h-dict-checkbox v-model:value="form.shyyRwys" type="third_cultural_relic_shyy_rwys"
              clearable></h-dict-checkbox>
          </n-form-item>
          <n-form-item label="损毁原因描述" path="shyyShyyms">
            <n-input v-model:value="form.shyyShyyms" placeholder="请输入损毁原因描述" clearable type="textarea"></n-input>
          </n-form-item>
        </n-descriptions-item>
      </n-descriptions>
      <n-descriptions bordered label-placement="top" style="margin-top: 30px">
        <n-descriptions-item label="环境状况">
          <n-form-item label="自然环境" path="hjzkZrhj">
            <n-input v-model:value="form.hjzkZrhj" placeholder="请输入自然环境" clearable type="textarea"></n-input>
          </n-form-item>
          <n-form-item label="人文环境" path="hjzkRwhj">
            <n-input v-model:value="form.hjzkRwhj" placeholder="请输入人文环境" clearable type="textarea"></n-input>
          </n-form-item>
        </n-descriptions-item>
      </n-descriptions>
      <n-form-item label="普查组建议" path="pzzjy" style="margin-top: 30px">
        <n-input v-model:value="form.pzzjy" placeholder="请输入普查组建议" clearable type="textarea"></n-input>
      </n-form-item>
      <n-form-item label="审核意见" path="rhyj">
        <n-input v-model:value="form.rhyj" placeholder="请输入审核意见" clearable></n-input>
      </n-form-item>
      <n-form-item label="抽查结论" path="ccjl">
        <n-input v-model:value="form.ccjl" placeholder="请输入抽查结论" clearable></n-input>
      </n-form-item>
      <n-form-item label="备注" path="remark">
        <n-input v-model:value="form.remark" placeholder="请输入备注" clearable type="textarea"></n-input>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="8">
          <n-form-item label="机构选择" path="groupId">
            <group-select v-model:value="form.groupId"></group-select>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="区域选择" path="areaId">
            <CascadeArea v-model:value="form.areaId"></CascadeArea>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="真实经纬度" path="latlng">
            <HFieldLatlng v-model:value="form.latlng"></HFieldLatlng>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="内容状态" path="status">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>
    <h-page-form v-model:show="detailModel" class="project" :rules="rules" :model="form" label-width="auto"
      :title="formTitle" :ref-table="refPageTable" :disabled="formType === 'view'" :enable-cancel="false">
      <CulturalRelicBasicDetail :id="form.id"></CulturalRelicBasicDetail>
    </h-page-form>
  </h-page-data-table>
  <CulturalRelicBasicDetail :id="detailId" v-if="showDetail && !props.showList"></CulturalRelicBasicDetail>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/third/thirdCulturalRelicBasic';
import { onMounted, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRelicBasicDetail from '../CulturalRelicBasicDetail.vue';
import CascadeArea from '@/modules/sys/components/form/field/CascadeArea.vue';
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import { HFieldLatlng } from '@/components/form/field';

const props = defineProps({
  queryShow: {
    type: Boolean,
    default: true
  },
  showList: {
    type: Boolean,
    default: true
  },
  thirdCulturalRecordId: {
    type: String,
  },
  title: {
    type: String,
  },
  isAdd: { type: Boolean, default: true },
  showHeader: { type: Boolean, default: true },
  showToolbar: { type: Boolean, default: true },
  showOperate: { type: Boolean, default: true },
});
const showDetail = ref(false)

const columns = [
  {
    key: 'name',
    title: '名称',
    columnType: 'title',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    titleAlign: 'center',
    width: '200px',
  },
  { key: 'code', title: '代码', titleAlign: 'center', ellipsis: { tooltip: true }, width: '150px' },
  {
    key: 'address',
    title: '地址及位置',
    titleAlign: 'center',
    ellipsis: { tooltip: true },
    resizable: 'true',
    width: '280px',
  },
  {
    key: 'level',
    title: '级别',
    dictType: 'third_cultural_relic_level',
    align: 'center',
    ellipsis: { tooltip: true },
    width: '200px',
  },
  { key: 'year', title: '年代', titleAlign: 'center', ellipsis: { tooltip: true }, width: '150px' },
  {
    key: 'type',
    title: '类型',
    dictType: 'third_cultural_relic_type',
    align: 'center',
    ellipsis: { tooltip: true },
    width: '200px',
  },
  { key: 'status', title: '内容状态', dictType: 'content_status', align: 'center', width: '100px' },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref();
const detailModel = ref(false);

const formTitle = ref();

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
  dtNum: null,
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
  latlng: '',
  cdsm: '',
  ccjl: '',
  subType: '',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
  status: '',
  culturalRelicId: props.thirdCulturalRecordId
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  name: {
    required: true,
    message: '请输入名称',
  },
  code: {
    required: true,
    message: '请输入代码',
  },
  level: {
    required: true,
    message: '请输入级别',
  },
};


/**
 * 查询表单
 * */
const paramsForm = ref({
  culturalRelicId: props.thirdCulturalRecordId,
  type: '',
  status: '',
});

const formType = ref<'add' | 'edit' | 'view'>('add');

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  formTitle.value = '添加';
  formModel.value = true;
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  formTitle.value = `编辑「${row.name}」`;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    detailModel.value = true;
  });
  formTitle.value = `查看「${row.name}」`;
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === 'add') {
    return add(data);
  } else {
    return edit(data);
  }
}

const detailId = ref()

onMounted(() => {
  if (!props.showList) {
    list(paramsForm.value).then((resp) => {
      if (resp.data.list) {
        detailId.value = resp.data.list[0].id
      }
      showDetail.value = true
    });
  }
})
</script>

<style lang="scss" scoped>
:deep(.n-descriptions-table-content) {
  text-align: left;
}
</style>

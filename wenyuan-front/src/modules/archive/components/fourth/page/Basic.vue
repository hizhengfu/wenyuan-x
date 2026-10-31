<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
    v-if="props.showList"

  >
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="类别" path="type">
        <n-input v-model:value="paramsForm.type" placeholder="请输入类别" clearable></n-input>
      </n-form-item>
      <n-form-item label="内容状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="content_status" clearable filterable></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form
      :rules="rules"
      :model="form"
      label-width="100px"
      :title="formTitle"
      :refTable="refPageTable"
      :disabled="formType === 'view'"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
    >
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
      <n-descriptions bordered label-placement="top" style="margin-top: 10px; margin-bottom: 10px">
        <n-descriptions-item label="地址及位置">
          <n-grid x-gap="10">
            <n-gi span="12">
              <n-form-item label="地址及位置" path="address" label-placement="left">
                <n-input v-model:value="form.address" placeholder="请输入地址及位置" clearable></n-input>
              </n-form-item>
              <n-form-item label="变更消失情况" path="bgxsqk" label-placement="left" label-width="120px">
                <template #label>
                  变更消失情况
                  <n-popover trigger="hover">
                    <template #trigger>
                      <n-icon size="16px">
                        <icon-mdi-help-circle-outline></icon-mdi-help-circle-outline>
                      </n-icon>
                    </template>
                    <span>是否存在变更为可移动文物或消失的情况</span>
                  </n-popover>
                </template>

                <h-dict-radio v-model:value="form.bgxsqk" type="fourth_change_disappear_status"></h-dict-radio>
              </n-form-item>
            </n-gi>
            <n-gi span="12">
              <n-form-item label="迁移情况" path="qyqk" label-placement="left">
                <template #label>
                  迁移情况
                  <n-popover trigger="hover">
                    <template #trigger>
                      <n-icon size="16px">
                        <icon-mdi-help-circle-outline></icon-mdi-help-circle-outline>
                      </n-icon>
                    </template>
                    <span>是否整体迁移并在新迁址占有独立地域范围</span>
                  </n-popover>
                </template>

                <h-dict-radio v-model:value="form.qyqk" type="isMigration"></h-dict-radio>
              </n-form-item>
            </n-gi>
          </n-grid>
        </n-descriptions-item>
      </n-descriptions>

      <n-form-item label="面积" path="mjFb" label-placement="left">
        <n-input v-model:value="form.mjFb" placeholder="请输入面积" clearable>
          <template #suffix> 平方米</template>
        </n-input>
      </n-form-item>

      <n-descriptions bordered label-placement="top" style="margin-top: 10px; margin-bottom: 10px">
        <n-descriptions-item label="文物级别">
          <n-grid x-gap="10">
            <n-gi span="12">
              <n-form-item label="文物级别" path="level" label-placement="left">
                <h-dict-radio v-model:value="form.level" type="fourth_heritage_level"></h-dict-radio>
              </n-form-item>
            </n-gi>
            <n-gi span="12">
              <n-form-item label="所属文物保护单位名称" path="bhdwmc" label-placement="left">
                <n-input v-model:value="form.bhdwmc" placeholder="请输入所属文物保护单位名称" clearable></n-input>
              </n-form-item>
            </n-gi>
          </n-grid>
        </n-descriptions-item>
      </n-descriptions>

      <n-descriptions bordered label-placement="top" style="margin-top: 10px; margin-bottom: 10px">
        <n-descriptions-item label="保护区划">
          <n-grid x-gap="10">
            <n-gi span="12">
              <n-form-item label="是否已公布保护范围" path="bhfwFlag" label-placement="left">
                <h-dict-radio v-model:value="form.bhfwFlag" type="isMigration"></h-dict-radio>
              </n-form-item>
            </n-gi>
            <n-gi span="12">
              <n-form-item label="是否已公布建设控制地带" path="jskzddFlag" label-placement="left">
                <h-dict-radio v-model:value="form.jskzddFlag" type="isMigration"></h-dict-radio>
              </n-form-item>
            </n-gi>
          </n-grid>
        </n-descriptions-item>
      </n-descriptions>

      <n-form-item label="年代" path="year" label-placement="left">
        <n-input v-model:value="form.year" placeholder="请输入年代" clearable></n-input>
      </n-form-item>

      <n-form-item label="统计年代" path="tjYear" label-placement="left">
        <h-dict-checkbox v-model:value="form.tjYear" :type="getTjYear(culturalType)" clearable></h-dict-checkbox>
      </n-form-item>

      <n-form-item label="类别" path="type" label-placement="left">
        <template v-if="culturalType != '近现代重要史迹及代表性建筑'">
          <h-dict-radio

       v-model:value="form.type"
       :type="getType(culturalType)"
     ></h-dict-radio>
        </template>
        <template v-else>
          <div style="display: flex;flex-direction: column;">
            <div>
              <div style="font-weight: bold;">近现代重要史迹</div>
              <h-dict-radio v-model:value="form.type" type="fourth_jxdzysj"></h-dict-radio>
            </div>
            <div>
              <span style="font-weight: bold;">近现代代表性建筑</span>
              <h-dict-radio v-model:value="form.type" type="fourth_jxddbxjz"></h-dict-radio>
            </div>
          </div>



          <!-- <h-dict-radio

       v-model:value="form.type"
       :type="getType(culturalType)"
     ></h-dict-radio> -->
        </template>

      </n-form-item>

      <n-descriptions bordered label-placement="top" style="margin-top: 10px; margin-bottom: 10px">
        <n-descriptions-item label="所有权">
          <n-grid x-gap="10">
            <n-gi span="12">
              <n-form-item label="所有权" path="syq" label-placement="left">
                <h-dict-checkbox v-model:value="form.syq" type="fourth_ownership" clearable></h-dict-checkbox>
              </n-form-item>
            </n-gi>
            <n-gi span="12">
              <n-form-item label="产权单位(人)" path="syqCqdw" label-placement="left">
                <n-input v-model:value="form.syqCqdw" placeholder="请输入产权单位(人)" clearable></n-input>
              </n-form-item>
            </n-gi>
          </n-grid>
        </n-descriptions-item>
      </n-descriptions>
      <n-descriptions bordered label-placement="top" style="margin-top: 10px; margin-bottom: 10px">
        <n-descriptions-item label="使用情况">
          <n-grid x-gap="10">
            <n-gi span="12">
              <n-form-item label="使用单位（人）" path="syqkSydw" label-placement="left">
                <n-input v-model:value="form.syqkSydw" placeholder="使用单位（人）" clearable></n-input>
              </n-form-item>
            </n-gi>
            <n-gi span="12">
              <n-form-item label="上级管理机构" path="syqkLs" label-placement="left">
                <n-input v-model:value="form.syqkLs" placeholder="请输入上级管理机构" clearable></n-input>
              </n-form-item>
            </n-gi>
          </n-grid>
          <n-form-item label="所属行业、系统" path="syqkSshy" label-placement="left">
            <h-dict-checkbox v-model:value="form.syqkSshy" type="fourth_industry" clearable></h-dict-checkbox>
          </n-form-item>
          <n-form-item label="开放状况" path="kfzt" label-placement="left">
            <h-dict-radio v-model:value="form.kfzt" type="fourth_open_state"></h-dict-radio>
          </n-form-item>
          <n-form-item label="使用用途" path="syqkYt" label-placement="left">
            <h-dict-checkbox v-model:value="form.syqkYt" type="fourth_use" clearable></h-dict-checkbox>
          </n-form-item>
        </n-descriptions-item>
      </n-descriptions>

      <n-form-item label="简介" path="description" label-placement="left">
        <n-input v-model:value="form.description" placeholder="请输入简介" type="textarea"></n-input>
      </n-form-item>

      <n-descriptions bordered label-placement="top" style="margin-top: 10px; margin-bottom: 10px">
        <n-descriptions-item label="保存状况">
          <n-form-item label="现状评估" path="bzzkXzpg" label-placement="left">
            <h-dict-radio v-model:value="form.bzzkXzpg" type="fourth_present_situation_appraisal"></h-dict-radio>
          </n-form-item>
          <n-form-item label="保护措施" path="bzzkBhcs" label-placement="left">
            <template #label>
              保护措施
              <n-popover trigger="hover">
                <template #trigger>
                  <n-icon size="16px">
                    <icon-mdi-help-circle-outline></icon-mdi-help-circle-outline>
                  </n-icon>
                </template>
                <span>三普以来已完成</span>
              </n-popover>
            </template>
            <h-dict-checkbox v-model:value="form.bzzkBhcs" type="fourth_protection_measures"></h-dict-checkbox>
          </n-form-item>
          <n-form-item label="影响因素" path="bzzjYxys" label-placement="left">
            <h-dict-checkbox v-model:value="form.bzzjYxys" type="fourth_influencing_factors"></h-dict-checkbox>
          </n-form-item>
        </n-descriptions-item>
      </n-descriptions>

      <n-form-item label="专题类别" path="ztlb" label-placement="left">
        <template #label>
          专题类别
          <n-popover trigger="hover">
            <template #trigger>
              <n-icon size="16px">
                <icon-mdi-help-circle-outline></icon-mdi-help-circle-outline>
              </n-icon>
            </template>
            <span>是否已经列为以下名录、规划和数据库</span>
          </n-popover>
        </template>
        <h-dict-checkbox v-model:value="form.ztlb" type="fourth_thematic_type"></h-dict-checkbox>
      </n-form-item>
      <n-form-item label="其他名录" path="qtml" label-placement="left">
        <template #label>
          其他名录
          <n-popover trigger="hover">
            <template #trigger>
              <n-icon size="16px">
                <icon-mdi-help-circle-outline></icon-mdi-help-circle-outline>
              </n-icon>
            </template>
            <span>是否列入以下名号、称号</span>
          </n-popover>
        </template>
        <h-dict-checkbox v-model:value="form.qtml" type="fourth_other_list"></h-dict-checkbox>
      </n-form-item>
      <n-form-item label="审核意见" path="shyj" label-placement="left">
        <n-input type="textarea" v-model:value="form.shyj" placeholder="请输入审核意见"></n-input>
      </n-form-item>
      <n-form-item label="抽查结论" path="ccjl" label-placement="left">
        <n-input type="textarea" v-model:value="form.ccjl" placeholder="请输入抽查结论"></n-input>
      </n-form-item>
      <n-form-item label="备注" path="remark" label-placement="left">
        <n-input type="textarea" v-model:value="form.remark" placeholder="请输入备注"></n-input>
      </n-form-item>
      <n-form-item label="附件上传" label-placement="left">
        <h-upload :contentId="route.query.fourthCulturalRecordId"></h-upload>
      </n-form-item>
      <n-form-item label="内容状态" path="status" label-placement="left">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>
    <h-page-form
      v-model:show="detailModel"
      class="project"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :ref-table="refPageTable"
      :disabled="formType === 'view'"
      :enable-cancel="false"
    >
      <FourthBasicDetail
        :fourthCulturalRecordId="route?.query?.fourthCulturalRecordId || props.fourthCulturalRecordId"
      ></FourthBasicDetail>
    </h-page-form>

  </h-page-data-table>
   <FourthBasicDetail :fourthCulturalRecordId="route?.query?.fourthCulturalRecordId || props.fourthCulturalRecordId" v-if="showDetail && !props.showList"></FourthBasicDetail>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/fourth/fourthCulturalRelicBasic';
import { get as coverGet } from '../../../api/fourth/fourthCulturalRelicCover';
import { ref, onMounted } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useRoute } from 'vue-router';
import FourthBasicDetail from '../FourthBasicDetail.vue';

const route:any = useRoute();
const showDetail = ref(false)
const columns = [
  { key: 'name', title: '名称', columnType: 'title', width: '200px', resizable: true },

  { key: 'level', title: '级别',  },

  { key: 'year', title: '年代' },

  { key: 'type', title: '类别',  },
];
const getTjYear = (type: any) => {

  switch (type) {
    case "古文化遗址":
      return 'fourth_gwh_gmz_tjyear';
    case "古墓葬":
      return 'fourth_gwh_gmz_tjyear';
    case "古建筑":
      return 'fourth_gjz_tjyear';
    case "石窟寺及石刻":
      return 'fourth_sksjsk_tjyear';
    case "近现代重要史迹及代表性建筑":
      return 'fourth_jxd_tjyear';
    case "其他":
      return 'fourth_qt_tjyear';
    default:
      break;
  }
};
const getType =(type:any)=>{
  switch (type) {
    case "古文化遗址":
      return 'fourth_cultural_0';
    case "古墓葬":
      return 'fourth_cultural_1';
    case "古建筑":
      return 'fourth_cultural_2';
    case "石窟寺及石刻":
      return 'fourth_cultural_3';
    case "近现代重要史迹及代表性建筑":
      return 'fourth_cultural_4';
    case "其他":
      return 'fourth_cultural_5';
    default:
      break;
  }
}
const props = defineProps({
  queryShow: {
    type: Boolean,
    default: true
  },
  showList: {
    type: Boolean,
    default: true
  },
  fourthCulturalRecordId: {
    type: String,
  },
  title: {
    type: String,
  },
});
/**
 * 编辑表单相关
 * */
const formModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref();

const formTitle = ref();
const detailModel = ref(false);
/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  parentId: '',
  parentIds: '',
  isLeaf: true,
  culturalRelicId: route.query.fourthCulturalRecordId || props.fourthCulturalRecordId ,
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
  type: route.query.type,
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
  bgxsqk:'',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
  status: '',
};
const culturalType = ref();
const handleCoverData = async () => {
  console.log(route.query.fourthCulturalRecordId,'--------------------6666666666666');
  console.log(props.fourthCulturalRecordId,'--------------------6666666666666');

  // if ((!route.query.fourthCulturalRecordId  !props.fourthCulturalRecordId)) return;
  let culturalRelicId = route.query.fourthCulturalRecordId ||  props.fourthCulturalRecordId;
  const coverRes = await coverGet(culturalRelicId);
  if (coverRes && coverRes.data && coverRes.data.type) {
    console.log(coverRes.data.type,'------------5555555555555')
    culturalType.value = coverRes.data.type;
  }
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
  // code: {
  //   required: true,
  //   message: '请输入代码',
  // },
  level: {
    required: true,
    message: '请输入级别',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  type: '',
  status: '',
  culturalRelicId: route.query.fourthCulturalRecordId || props.fourthCulturalRecordId,

});

const formType = ref('add');

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

onMounted(async () => {
  await handleCoverData()
  showDetail.value = true

  });
</script>

<style lang="scss" scoped>
:deep(.n-descriptions-table-content) {
  text-align: left;
}
</style>

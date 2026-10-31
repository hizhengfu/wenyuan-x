<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    v-bind="$attrs"
    :scroll-x="1200"
    :params="paramsForm"
    @loadData="handleLoad"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="公布名称" path="publishName">
        <n-input v-model:value="paramsForm.publishName" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="公布级别" path="publishLevel">
        <n-input v-model:value="paramsForm.publishLevel" placeholder="请输入级别" clearable></n-input>
      </n-form-item>
      <n-form-item label="公布编号" path="publishNo">
        <n-input v-model:value="paramsForm.publishNo" placeholder="请输入公布编号" clearable></n-input>
      </n-form-item>
      <n-form-item label-width="68px" label="代码" path="publishCode">
        <n-input v-model:value="paramsForm.publishCode" placeholder="请输入代码" clearable></n-input>
      </n-form-item>
      <n-form-item label="公布批次" path="publishBatch">
        <h-dict-select
          v-model:value="paramsForm.publishBatch"
          placeholder="请选择公布批次"
          clearable
          type="basic_public_batch"
        ></h-dict-select>
      </n-form-item>
      <n-form-item label-width="68px" label="类别" path="mainCategory">
        <h-dict-select
          v-model:value="paramsForm.mainCategory"
          type="third_cultural_relic_type"
          placeholder="请选择类别"
          clearable
        ></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :refTable="refPageTable"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
    >
      <n-form-item label="四有档案" path="fourCulturalRecordId">
        <CulturalRecordSelect disabled v-model:value="form.fourCulturalRecordId" placeholder="请选择四有档案" clearable>
        </CulturalRecordSelect>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="公布名称" path="publishName">
            <n-input v-model:value="form.publishName" placeholder="请输入公布名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="公布级别" path="publishLevel">
            <h-dict-select
              v-model:value="form.publishLevel"
              placeholder="请选择公布级别"
              clearable
              type="four_cultural_relic_level"
            ></h-dict-select>
          </n-form-item>
          <n-form-item label="公布编号" path="publishNo">
            <n-input v-model:value="form.publishNo" placeholder="请输入公布编号" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="其它名称" path="otherName">
            <n-input v-model:value="form.otherName" placeholder="请输入其它名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="公布批次" path="publishBatch">
            <h-dict-select
              v-model:value="form.publishBatch"
              placeholder="请选择公布批次"
              clearable
              type="four_cultural_relic_public_batch"
            ></h-dict-select>
          </n-form-item>
          <n-form-item label="公布分类号" path="publishClass">
            <n-input v-model:value="form.publishClass" placeholder="请输入公布分类号" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="代码" path="publishCode">
        <n-input v-model:value="form.publishCode" placeholder="请输入代码" clearable></n-input>
      </n-form-item>
      <n-form-item label="公布地址" path="publishAddress">
        <n-input v-model:value="form.publishAddress" placeholder="请输入公布地址" clearable></n-input>
      </n-form-item>
      <n-form-item label="现地址" path="currentAddress">
        <n-input v-model:value="form.currentAddress" placeholder="请输入现地址" clearable></n-input>
      </n-form-item>
      <n-descriptions bordered label-placement="top" style="margin-top: 15px">
        <n-descriptions-item label="地理坐标">
          <n-grid x-gap="10">
            <n-gi span="8">
              <n-form-item label="经度" path="dlzbJd">
                <n-input-group>
                  <n-input-group-label>东经</n-input-group-label>
                  <n-input v-model:value="form.dlzbJd" placeholder="请输入经度（度分秒）" clearable></n-input>
                </n-input-group>
              </n-form-item>
            </n-gi>
            <n-gi span="8">
              <n-form-item label="纬度" path="dlzbWd">
                <n-input-group>
                  <n-input-group-label>北纬</n-input-group-label>
                  <n-input v-model:value="form.dlzbWd" placeholder="请输入纬度（度分秒）" clearable></n-input>
                </n-input-group>
              </n-form-item>
            </n-gi>
            <n-gi span="8">
              <n-form-item label="海拔高程(米)" path="dlzbGc">
                <n-input-group>
                  <n-input v-model:value="form.dlzbGc" placeholder="请输入海拔高程" clearable></n-input>
                  <n-input-group-label>米</n-input-group-label>
                </n-input-group>
              </n-form-item>
            </n-gi>
          </n-grid>
        </n-descriptions-item>
      </n-descriptions>
      <n-descriptions bordered label-placement="top" style="margin-top: 15px; margin-bottom: 20px">
        <n-descriptions-item label="面积">
          <n-form-item label="保护单位面积" path="mjBhdw">
            <n-input-group>
              <n-input v-model:value="form.mjBhdw" placeholder="请输入保护单位面积" clearable></n-input>
              <n-input-group-label>平方米</n-input-group-label>
            </n-input-group>
          </n-form-item>
          <n-form-item label="保护范围面积" path="mjBhfw">
            <n-input-group>
              <n-input v-model:value="form.mjBhfw" placeholder="请输入保护范围面积" clearable></n-input>
              <n-input-group-label>平方米</n-input-group-label>
            </n-input-group>
          </n-form-item>
          <n-form-item label="建设控制地带面积" path="mjJskzdd">
            <n-input-group>
              <n-input v-model:value="form.mjJskzdd" placeholder="请输入建设控制地带面积" clearable></n-input>
              <n-input-group-label>平方米</n-input-group-label>
            </n-input-group>
          </n-form-item>
        </n-descriptions-item>
      </n-descriptions>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="公布时代" path="publishYear">
            <n-input v-model:value="form.publishYear" placeholder="请输入公布时代" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="时代研究信息" path="sdyjxx">
            <n-input v-model:value="form.sdyjxx" placeholder="请输入时代研究信息" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="统计年代" path="tjYear" label-placement="left">
        <h-dict-checkbox v-model:value="form.tjYear" type="four_cultural_relic_tjnd" clearable></h-dict-checkbox>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="8">
          <n-form-item label="公布类别" path="publishCategory">
            <n-input v-model:value="form.publishCategory" placeholder="请输入公布类别" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="主类别" path="mainCategory">
            <h-dict-select v-model:value="form.mainCategory" type="four_cultural_relic_type"></h-dict-select>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="子类别" path="subCategory" v-if="form.mainCategory == '0'">
            <h-dict-select v-model:value="form.subCategory" type="four_cultural_relic_type_gyz"></h-dict-select>
          </n-form-item>
          <n-form-item label="子类别" path="subCategory" v-if="form.mainCategory == '1'">
            <h-dict-select v-model:value="form.subCategory" type="four_cultural_relic_type_gmz"></h-dict-select>
          </n-form-item>
          <n-form-item label="子类别" path="subCategory" v-if="form.mainCategory == '2'">
            <h-dict-select v-model:value="form.subCategory" type="four_cultural_relic_type_gjz"></h-dict-select>
          </n-form-item>
          <n-form-item label="子类别" path="subCategory" v-if="form.mainCategory == '3'">
            <h-dict-select v-model:value="form.subCategory" type="four_cultural_relic_type_sks"></h-dict-select>
          </n-form-item>
          <n-form-item label="子类别" path="subCategory" v-if="form.mainCategory == '4'">
            <h-dict-select v-model:value="form.subCategory" type="four_cultural_relic_type_jxd"></h-dict-select>
          </n-form-item>
          <n-form-item label="子类别" path="subCategory" v-if="form.mainCategory == '5' || form.mainCategory == ''">
            <h-dict-select v-model:value="form.subCategory" type="four_cultural_relic_type" disabled></h-dict-select>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="16">
          <n-form-item label="公布机关" path="publishGroup">
            <n-input v-model:value="form.publishGroup" placeholder="请输入公布机关" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="公布日期" path="publishDate">
            <n-date-picker clearable v-model:value="form.publishDate" placeholder="请选择公布日期" />
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="所有权" path="syq" label-placement="left">
        <h-dict-checkbox type="four_cultural_relic_syq" v-model:value="form.syq"></h-dict-checkbox>
      </n-form-item>
      <n-descriptions bordered label-placement="top" style="margin-bottom: 20px">
        <n-descriptions-item label="使用情况">
          <n-grid x-gap="10">
            <n-gi span="16">
              <n-form-item label="使用人" path="syqkSyr">
                <n-input v-model:value="form.syqkSyr" placeholder="请输入使用人" clearable></n-input>
              </n-form-item>
            </n-gi>
            <n-gi span="8">
              <n-form-item label="隶属" path="syqkLs">
                <n-input v-model:value="form.syqkLs" placeholder="请输入隶属" clearable></n-input>
              </n-form-item>
            </n-gi>
          </n-grid>
          <n-form-item label="用途" path="syqkYt" label-placement="left">
            <h-dict-checkbox v-model:value="form.syqkYt" type="four_cultural_relic_syqk_yt" clearable></h-dict-checkbox>
          </n-form-item>
        </n-descriptions-item>
      </n-descriptions>
      <n-form-item label="管理机构" path="gljg">
        <n-input v-model:value="form.gljg" placeholder="请输入管理机构" clearable></n-input>
      </n-form-item>
      <n-form-item label="简要说明" path="jysm">
        <n-input v-model:value="form.jysm" placeholder="请输入简要说明" clearable type="textarea"></n-input>
      </n-form-item>
      <n-descriptions bordered label-placement="top">
        <n-descriptions-item label="保存现况">
          <n-form-item label="现状评估" path="bcxkXzpg">
            <h-dict-radio type="four_cultural_relic_bzzk_xzpg" v-model:value="form.bcxkXzpg"></h-dict-radio>
          </n-form-item>
          <n-form-item label="现存状况" path="bcxkXzzk">
            <n-input v-model:value="form.bcxkXzzk" type="textarea"></n-input>
          </n-form-item>
        </n-descriptions-item>
      </n-descriptions>
      <n-descriptions bordered label-placement="top" style="margin-top: 20px; margin-bottom: 20px">
        <n-descriptions-item label="残损原因">
          <n-form-item label="自然因素" path="csyyZrys">
            <h-dict-checkbox
              v-model:value="form.csyyZrys"
              type="four_cultural_relic_csyy_zrys"
              clearable
            ></h-dict-checkbox>
          </n-form-item>
          <n-form-item label="人为因素" path="csyyRwys">
            <h-dict-checkbox
              v-model:value="form.csyyRwys"
              type="four_cultural_relic_csyy_rwys"
              clearable
            ></h-dict-checkbox>
          </n-form-item>
          <n-form-item label="残损原因描述" path="csyyCsyyms">
            <n-input v-model:value="form.csyyCsyyms" placeholder="残损原因描述" clearable type="textarea"></n-input>
          </n-form-item>
        </n-descriptions-item>
      </n-descriptions>
      <n-form-item label="保护范围和建设控制地带" path="bhfwJskzdd">
        <n-input v-model:value="form.bhfwJskzdd" type="textarea"></n-input>
      </n-form-item>
      <n-descriptions bordered label-placement="top" style="margin-bottom: 20px">
        <n-descriptions-item label="保护标志">
          <n-descriptions bordered label-placement="left" :column="1">
            <n-descriptions-item label="标志牌">
              <n-grid x-gap="10">
                <n-gi span="8">
                  <n-form-item label="材质" path="bhbzBzpCz">
                    <n-input v-model:value="form.bhbzBzpCz" placeholder="请输入材质" clearable></n-input>
                  </n-form-item>
                </n-gi>
                <n-gi span="8">
                  <n-form-item label="数量" path="bhbzBzpSl">
                    <n-input-number v-model:value="form.bhbzBzpSl" placeholder="请输入数量" clearable></n-input-number>
                  </n-form-item>
                </n-gi>
                <n-gi span="8">
                  <n-form-item label="立标机关" path="bhbzBzpLfjg">
                    <n-input v-model:value="form.bhbzBzpLfjg" placeholder="请输入立标机关" clearable></n-input>
                  </n-form-item>
                </n-gi>
              </n-grid>
              <n-grid x-gap="10">
                <n-gi span="16">
                  <n-form-item label="规格" path="bhbzBzpGg">
                    <n-input v-model:value="form.bhbzBzpGg" placeholder="请输入规格" clearable></n-input>
                  </n-form-item>
                </n-gi>
                <n-gi span="8">
                  <n-form-item label="立标日期" path="bhbzBzpLbrq">
                    <n-date-picker
                      clearable
                      v-model:value="form.bhbzBzpLbrq"
                      type="date"
                      placeholder="请输入立标日期"
                    />
                  </n-form-item>
                </n-gi>
              </n-grid>
            </n-descriptions-item>
            <n-descriptions-item label="说明牌">
              <n-grid x-gap="10">
                <n-gi span="8">
                  <n-form-item label="材质" path="bhbzSmpCz">
                    <n-input v-model:value="form.bhbzSmpCz" placeholder="请输入材质" clearable></n-input>
                  </n-form-item>
                </n-gi>
                <n-gi span="8">
                  <n-form-item label="数量" path="bhbzSmpSl">
                    <n-input-number v-model:value="form.bhbzSmpSl" placeholder="请输入数量" clearable></n-input-number>
                  </n-form-item>
                </n-gi>
                <n-gi span="8">
                  <n-form-item label="立标机关" path="bhbzSmpLfjg">
                    <n-input v-model:value="form.bhbzSmpLfjg" placeholder="请输入立标机关" clearable></n-input>
                  </n-form-item>
                </n-gi>
              </n-grid>
              <n-grid x-gap="10">
                <n-gi span="16">
                  <n-form-item label="规格" path="bhbzSmpGg">
                    <n-input v-model:value="form.bhbzSmpGg" placeholder="请输入规格" clearable></n-input>
                  </n-form-item>
                </n-gi>
                <n-gi span="8">
                  <n-form-item label="立标日期" path="bhbzSmpLbrq">
                    <n-date-picker
                      clearable
                      v-model:value="form.bhbzSmpLbrq"
                      type="date"
                      placeholder="请输入立标日期"
                    />
                  </n-form-item>
                </n-gi>
              </n-grid>
            </n-descriptions-item>
            <n-descriptions-item label="界桩">
              <n-grid x-gap="10">
                <n-gi span="8">
                  <n-form-item label="材质" path="bhbzJzCz">
                    <n-input v-model:value="form.bhbzJzCz" placeholder="请输入材质" clearable></n-input>
                  </n-form-item>
                </n-gi>
                <n-gi span="8">
                  <n-form-item label="数量" path="bhbzJzSl">
                    <n-input-number v-model:value="form.bhbzJzSl" placeholder="请输入数量" clearable></n-input-number>
                  </n-form-item>
                </n-gi>
                <n-gi span="8">
                  <n-form-item label="立标机关" path="bhbzJzLfjg">
                    <n-input v-model:value="form.bhbzJzLfjg" placeholder="请输入立标机关" clearable></n-input>
                  </n-form-item>
                </n-gi>
              </n-grid>
              <n-grid x-gap="10">
                <n-gi span="16">
                  <n-form-item label="规格" path="bhbzJzGg">
                    <n-input v-model:value="form.bhbzJzGg" placeholder="请输入规格" clearable></n-input>
                  </n-form-item>
                </n-gi>
                <n-gi span="8">
                  <n-form-item label="立标日期" path="bhbzJzLbrq">
                    <n-date-picker clearable v-model:value="form.bhbzJzLbrq" type="date" placeholder="请输入立标日期" />
                  </n-form-item>
                </n-gi>
              </n-grid>
            </n-descriptions-item>
          </n-descriptions>
        </n-descriptions-item>
      </n-descriptions>
      <n-descriptions bordered label-placement="top" style="margin-bottom: 20px">
        <n-descriptions-item label="保护机构现状">
          <n-form-item label="名称" path="bhjgxzMc">
            <n-input v-model:value="form.bhjgxzMc" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="类别" path="bhjgxzLb">
            <n-input v-model:value="form.bhjgxzLb" placeholder="请输入类别" clearable></n-input>
          </n-form-item>
          <n-form-item label="负责人" path="bhjgxzFzr">
            <n-input v-model:value="form.bhjgxzFzr" placeholder="请输入负责人" clearable></n-input>
          </n-form-item>
          <n-grid x-gap="10">
            <n-gi span="8">
              <n-form-item label="人数" path="bhjgxzRs">
                <n-input v-model:value="form.bhjgxzRs" placeholder="请输入人数" clearable></n-input>
              </n-form-item>
            </n-gi>
            <n-gi span="16">
              <n-form-item label="成立时间" path="bhjgxzClsj">
                <n-date-picker clearable v-model:value="form.bhjgxzClsj" type="date" placeholder="请选择成立时间" />
              </n-form-item>
            </n-gi>
          </n-grid>
        </n-descriptions-item>
      </n-descriptions>
      <n-form-item label="备注" path="remarks">
        <n-input v-model:value="form.remarks" type="textarea"></n-input>
      </n-form-item>
    </h-page-form>
    <h-page-form
      v-model:show="detailModel"
      :closable="closable"
      class="project"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :ref-table="refPageTable"
      :disabled="formType === 'view'"
      :enable-cancel="false"
    >
      <CulturalRecordMainDetail :id="form.id"></CulturalRecordMainDetail>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/four/fourCulturalRecordMainText1';
import { onMounted, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRecordMainDetail from '../CulturalRecordMainDetail.vue';
import CascadeArea from '@/modules/sys/components/form/field/CascadeArea.vue';
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import { HFieldLatlng } from '@/components/form/field';
import CulturalRecordSelect from '../CulturalRecordSelect.vue';

const columns = [
  {
    key: 'publishName',
    title: '公布名称',
    ellipsis: { tooltip: true },
    columnType: 'title',
    titleAlign: 'center',
    width: '150px',
  },
  { key: 'publishNo', title: '公布编号', ellipsis: { tooltip: true }, titleAlign: 'center', width: '150px' },
  { key: 'publishCode', title: '代码', ellipsis: { tooltip: true }, titleAlign: 'center', width: '150px' },
  { key: 'publishYear', title: '公布时代', ellipsis: { tooltip: true }, titleAlign: 'center', width: '150px' },
  {
    key: 'publishBatch',
    title: '公布批次',
    ellipsis: { tooltip: true },
    titleAlign: 'center',
    width: '150px',
    dictType: 'basic_public_batch',
  },
  {
    key: 'mainCategory',
    title: '类别',
    ellipsis: { tooltip: true },
    align: 'center',
    width: '150px',
    dictType: 'third_cultural_relic_type',
  },
  {
    key: 'publishDate',
    title: '公布日期',
    ellipsis: { tooltip: true },
    align: 'center',
    width: '150px',
    columnType: 'date',
  },
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
const props = defineProps({ fourCulturalRecordId: { type: String, required: true } });

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  fourCulturalRecordId: props.fourCulturalRecordId,
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
  bhbzBzpSl: null,
  bhbzBzpLfjg: '',
  bhbzBzpGg: '',
  bhbzBzpLbrq: null,
  bhbzSmpCz: '',
  bhbzSmpSl: null,
  bhbzSmpLfjg: '',
  bhbzSmpGg: '',
  bhbzSmpLbrq: null,
  bhbzJzCz: '',
  bhbzJzSl: null,
  bhbzJzLfjg: '',
  bhbzJzGg: '',
  bhbzJzLbrq: null,
  bhjgxzMc: '',
  bhjgxzLb: '',
  bhjgxzFzr: '',
  bhjgxzRs: '',
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
 * 验证规则
 * */
const rules = {
  publishName: {
    required: true,
    message: '请输入公布名称',
  },
  publishCode: {
    required: true,
    message: '请输入代码',
  },
  fourCulturalRecordId: {
    required: true,
    message: '请选择四有档案',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  fourCulturalRecordId: props.fourCulturalRecordId,
  publishName: '',
  mainCategory: '',
  publishNo: '',
  publishCode: '',
  publishBatch: null,
});

const formType = ref<'add' | 'edit' | 'view'>('add');
const dataList = ref<any[]>([]);
const dataListLength = ref();
const closable = ref(true);

function handleLoad(resp: any) {
  if (resp.data && resp.data.total === 1) {
    handleView(resp.data.list[0]);
  }
}

// onMounted(() => {
//   list(paramsForm).then((resp: any) => {
//     dataList.value = resp.data.list;
//     if (dataList.value) {
//       dataListLength.value = dataList.value.length;
//       Object.assign(form.value, dataList.value[0]);
//     } else {
//       dataListLength.value = 0;
//     }
//       formModel.value = false;
//       closable.value = false;
//       formTitle.value = '四有档案';
//       detailModel.value = true;
//   });
// });

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
  formTitle.value = `编辑「${row.publishName}」`;
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
  formTitle.value = `查看「${row.publishName}」`;
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
</script>

<style lang="scss" scoped>
:deep(.n-descriptions-table-content) {
  text-align: left;
}
</style>

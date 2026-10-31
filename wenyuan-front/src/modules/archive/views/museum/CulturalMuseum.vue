<template>
  <h-page-data-table ref="refPageTable" :fetch-data="list" :scroll-x="1700" :params="paramsForm" @add="handleAdd"
    @rowEdit="handleEdit" @rowView="handleView" :funApiDelete="del" :columns="columns">
    <template #queryForm>
      <n-form-item label="博物馆名称" path="wdmc">
        <n-input v-model:value="paramsForm.wdmc" placeholder="请输入博物馆名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="博物馆性质" path="type">
        <h-dict-select v-model:value="paramsForm.type" placeholder="请选择博物馆性质" clearable
          type="museum_type"></h-dict-select>
      </n-form-item>
      <n-form-item label="博物馆类别" path="category">
        <h-dict-select v-model:value="paramsForm.category" placeholder="请选择博物馆类别" clearable
          type="museum_category"></h-dict-select>
      </n-form-item>
      <n-form-item label="博物馆级别" path="level">
        <h-dict-select v-model:value="paramsForm.level" placeholder="请选择博物馆级别" clearable
          type="museum_level"></h-dict-select>
      </n-form-item>
      <n-form-item label="所在行政区" path="szxzq">
        <n-input v-model:value="paramsForm.szxzq" placeholder="请输入所在行政区" clearable></n-input>
      </n-form-item>
      <n-form-item label="遗产地名称" path="ycdmc">
        <n-input v-model:value="paramsForm.ycdmc" placeholder="请输入遗产地名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="机构名称" path="jgmc">
        <n-input v-model:value="paramsForm.jgmc" placeholder="请输入机构名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="机构成立时间" path="jgclsj">
        <n-date-picker clearable v-model:value="paramsForm.jgclsj" type="datetime" placeholder="请输入机构成立时间" />
      </n-form-item>
    </template>
    <h-page-form :rules="rules" :model="form" label-width="auto" :title="formTitle" :refTable="refPageTable"
      :disabled="formType === 'view'" :handleSubmit="handleSubmit" v-model:show="formModel">
      <n-grid x-gap="12" :cols="3">
        <n-gi>
          <n-form-item label="博物馆性质" path="type">
            <h-dict-select v-model:value="form.type" placeholder="请选择博物馆性质" clearable
              type="museum_type"></h-dict-select>
          </n-form-item>
          <n-form-item label="博物馆级别" path="level">
            <h-dict-select v-model:value="form.level" placeholder="请选择博物馆级别" clearable
              type="museum_level"></h-dict-select>
          </n-form-item>
        </n-gi>
        <n-gi>
          <n-form-item label="博物馆名称" path="wdmc">
            <n-input v-model:value="form.wdmc" placeholder="请输入博物馆名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="遗产地名称" path="ycdmc">
            <n-input v-model:value="form.ycdmc" placeholder="请输入遗产地名称" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi>
          <n-space justify="center">
            <n-form-item label="图片" path="image">
              <h-avatar v-model:value="form.image"></h-avatar>
            </n-form-item>
          </n-space>
        </n-gi>
      </n-grid>
      <n-grid x-gap="12" :cols="3">
        <n-gi>
          <n-form-item label="博物馆类别" path="category">
            <h-dict-select v-model:value="form.category" placeholder="请选择博物馆类别" clearable
              type="museum_category"></h-dict-select>
          </n-form-item>
          <n-form-item label="行政总负责人" path="xzzfzr">
            <n-input v-model:value="form.xzzfzr" placeholder="请输入行政总负责人" clearable></n-input>
          </n-form-item>
          <n-form-item label="联系人姓名" path="lxrxm">
            <n-input v-model:value="form.lxrxm" placeholder="请输入联系人姓名" clearable></n-input>
          </n-form-item>
          <n-form-item label="所在行政区" path="szxzq">
            <n-input v-model:value="form.szxzq" placeholder="请输入所在行政区" clearable></n-input>
          </n-form-item>
          <n-form-item label="机构名称" path="jgmc">
            <n-input v-model:value="form.jgmc" placeholder="请输入机构名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="数据保存状态" path="shzt">
            <n-input v-model:value="form.shzt" placeholder="请输入数据保存状态" clearable></n-input>
          </n-form-item>
          <n-form-item label="开放时间" path="openingHours">
            <n-input v-model:value="form.openingHours" placeholder="请输入开放时间" clearable></n-input>
          </n-form-item>
          <n-form-item label="在编人数" path="zbrs">
            <n-input v-model:value="form.zbrs" placeholder="请输入在编人数" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi>
          <n-form-item label="组织机构代码" path="zzjgdm">
            <n-input v-model:value="form.zzjgdm" placeholder="请输入组织机构代码" clearable></n-input>
          </n-form-item>
          <n-form-item label="安全负责人" path="aqfzr">
            <n-input v-model:value="form.aqfzr" placeholder="请输入安全负责人" clearable></n-input>
          </n-form-item>
          <n-form-item label="联系电话" path="lxdh">
            <n-input v-model:value="form.lxdh" placeholder="请输入联系电话" clearable></n-input>
          </n-form-item>
          <n-form-item label="所在省" path="prefecture">
            <n-input v-model:value="form.prefecture" placeholder="请输入所在省" clearable></n-input>
          </n-form-item>
          <n-form-item label="机构级别" path="jgjb">
            <n-input v-model:value="form.jgjb" placeholder="请输入机构级别" clearable></n-input>
          </n-form-item>
          <n-form-item label="基础数据类型" path="sjlx">
            <n-input v-model:value="form.sjlx" placeholder="请输入基础数据类型" clearable></n-input>
          </n-form-item>
          <n-form-item label="上传时间" path="scsj">
            <n-date-picker clearable v-model:value="form.scsj" type="datetime" placeholder="请输入上传时间" />
          </n-form-item>
          <n-form-item label="工作人员总数" path="gzryzs">
            <n-input v-model:value="form.gzryzs" placeholder="请输入工作人员总数" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi>
          <n-form-item label="一普机构编码" path="sourceId">
            <n-input v-model:value="form.sourceId" placeholder="请输入一普机构编码" clearable></n-input>
          </n-form-item>
          <n-form-item label="法定代表人" path="fddbr">
            <n-input v-model:value="form.fddbr" placeholder="请输入法定代表人" clearable></n-input>
          </n-form-item>
          <n-form-item label="电子邮箱" path="dzyx">
            <n-input v-model:value="form.dzyx" placeholder="请输入电子邮箱" clearable></n-input>
          </n-form-item>
          <n-form-item label="所在市" path="city">
            <n-input v-model:value="form.city" placeholder="请输入所在市" clearable></n-input>
          </n-form-item>
          <n-form-item label="机构成立时间" path="jgclsj">
            <n-date-picker clearable v-model:value="form.jgclsj" type="datetime" placeholder="请输入机构成立时间" />
          </n-form-item>
          <n-form-item label="上级管理单位" path="sjgldw">
            <n-input v-model:value="form.sjgldw" placeholder="请输入上级管理单位" clearable></n-input>
          </n-form-item>
          <n-form-item label="电话" path="telephone">
            <n-input v-model:value="form.telephone" placeholder="请输入电话" clearable></n-input>
          </n-form-item>
          <n-form-item label="文物数量" path="relicsNum">
            <n-input-number v-model:value="form.relicsNum" placeholder="请输入文物数量" clearable></n-input-number>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="12" :cols="2">
        <n-gi>
          <n-form-item label="官方网站" path="gfwz">
            <n-input v-model:value="form.gfwz" placeholder="请输入官方网站" clearable></n-input>
          </n-form-item>
          <n-form-item label="票价情况" path="ticketPrices">
            <n-input v-model:value="form.ticketPrices" placeholder="请输入票价情况" clearable></n-input>
          </n-form-item>
          <n-form-item label="主要经费来源" path="zyjfly">
            <h-dict-select v-model:value="form.zyjfly" placeholder="请输入主要经费来源" clearable
              type="cost_source"></h-dict-select>
          </n-form-item>
        </n-gi>
        <n-gi>
          <n-form-item label="经纬度" path="latlng">
            <HFieldLatlng v-model:value="form.latlng"></HFieldLatlng>
          </n-form-item>
          <n-form-item label="简要说明" path="remark">
            <n-input v-model:value="form.remark" placeholder="请输入简要说明" clearable></n-input>
          </n-form-item>
          <n-form-item label="其他经费来源" path="qt">
            <n-input v-model:value="form.qt" placeholder="请输入其他经费来源" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="管理区域说明" path="glqysm">
        <n-input v-model:value="form.glqysm" placeholder="请输入管理区域说明" clearable type="textarea"></n-input>
      </n-form-item>
      <n-form-item label="描述" path="description">
        <n-input v-model:value="form.description" placeholder="请输入描述" clearable type="textarea"></n-input>
      </n-form-item>
      <n-form-item label="地址" path="address">
        <n-input v-model:value="form.address" placeholder="请输入地址" clearable type="textarea"></n-input>
      </n-form-item>
      <n-form-item label="备注" path="bz">
        <n-input v-model:value="form.bz" placeholder="请输入备注" clearable type="textarea" rows="6"></n-input>
      </n-form-item>
      <n-form-item label="承担的责任" path="cddzr">
        <n-input v-model:value="form.cddzr" placeholder="请输入承担的责任" clearable type="textarea"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../api/museum/cultural'
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es'

const columns = [
  { 'key': 'wdmc', 'title': '博物馆名称', 'columnType': 'title', 'ellipsis': { tooltip: { style: { maxWidth: '600px' } } }, 'width': '120px' },
  { 'key': 'type', 'title': '博物馆性质', 'width': '120px', dictType: 'museum_type', align: 'center', 'ellipsis': { tooltip: { style: { maxWidth: '600px' } } } },
  { 'key': 'category', 'title': '博物馆类别', 'width': '100px', dictType: 'museum_category', align: 'center', 'ellipsis': { tooltip: { style: { maxWidth: '600px' } } } },
  { 'key': 'level', 'title': '博物馆级别', 'width': '100px', dictType: 'museum_level', align: 'center', 'ellipsis': { tooltip: { style: { maxWidth: '600px' } } } },
  { 'key': 'szxzq', 'title': '所在行政区', 'width': '120px', 'ellipsis': { tooltip: { style: { maxWidth: '600px' } } } },
  { 'key': 'ycdmc', 'title': '遗产地名称', 'width': '120px', 'ellipsis': { tooltip: { style: { maxWidth: '600px' } } } },
  { 'key': 'jgmc', 'title': '机构名称', 'width': '120px', 'ellipsis': { tooltip: { style: { maxWidth: '600px' } } } },
  { 'key': 'zzjgdm', 'title': '组织机构代码', 'width': '120px', 'ellipsis': { tooltip: { style: { maxWidth: '600px' } } } },
  { 'key': 'jgjb', 'title': '机构级别', 'width': '120px', 'ellipsis': { tooltip: { style: { maxWidth: '600px' } } } },
  { 'key': 'jgclsj', 'title': '机构成立时间', 'columnType': 'dateTime', 'width': '120px', align: 'center', 'ellipsis': { tooltip: { style: { maxWidth: '600px' } } } },
]

/**
 * 编辑表单相关
 * */
const formModel = ref(false)
/**
 * 表格ref
 * */
const refPageTable = ref()

const formTitle = ref()

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  wdmc: '',
  szxzq: '',
  shzt: '',
  scsj: null,
  sjlx: '',
  ycdmc: '',
  jgmc: '',
  zzjgdm: '',
  lxrxm: '',
  lxdh: '',
  dzyx: '',
  sjgldw: '',
  jgjb: '',
  gzryzs: '',
  fddbr: '',
  xzzfzr: '',
  aqfzr: '',
  gfwz: '',
  jgclsj: null,
  zbrs: '',
  zyjfly: '',
  qt: '',
  cddzr: '',
  glqysm: '',
  bz: '',
  name: '',
  image: '',
  lat: '',
  lng: '',
  telephone: '',
  openingHours: '',
  ticketPrices: '',
  city: '',
  type: '',
  category: '',
  level: '',
  address: '',
  remark: '',
  description: '',
  createBy: '',
  createDate: null,
  updateBy: '',
  updateDate: null,
  stationId: '',
  prefecture: '',
  relicsNum: null,
  sourceId: '',
  latlng: ''
}

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))

/**
 * 验证规则
 * */
const rules = {
  wdmc: {
    required: true,
    message: '请输入博物馆名称'
  }
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  type: '',
  category: '',
  level: '',
  wdmc: '',
  szxzq: '',
  ycdmc: '',
  jgmc: '',
  jgclsj: null,
})

const formType = ref('add')

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add'
  form.value = cloneDeep(defaultForm)
  formTitle.value = '添加'
  formModel.value = true
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit'
  form.value = cloneDeep(defaultForm)
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data)
    formModel.value = true
  })
  formTitle.value = `编辑「${row.wdmc}」`
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view'
  Object.assign(form.value, row)
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data)
    formModel.value = true
  })
  formTitle.value = `查看「${row.wdmc}」`
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === 'add') {
    return add(data)
  } else {
    return edit(data)
  }
}
</script>

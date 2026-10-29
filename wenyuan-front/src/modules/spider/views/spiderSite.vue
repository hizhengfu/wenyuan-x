<template>
  <h-page-data-table ref="refPageTable" :fetch-data="list" :scroll-x="1200" :params="paramsForm" @add="handleAdd"
                     @rowEdit="handleEdit" @rowView="handleView" :funApiDelete="del" :columns="columns"
                     :funApiCopy="copy">
    <template #queryForm>
      <n-form-item label="站点名称" path="title">
        <n-input v-model:value="paramsForm.title" clearable></n-input>
      </n-form-item>
    </template>
    <h-page-form :rules="rules" :model="form" label-width="auto" :title="formTitle" :refTable="refPageTable"
                 :disabled="formType === 'view'" :handleSubmit="handleSubmit" v-model:show="formModel">
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item path="title" label="站点名称">
            <n-input label="站点名称" v-model:value="form.title"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item path="module" label="模块代码">
            <n-input label="模块代码" v-model:value="form.module"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item path="domain" label="域名">
            <n-input label="域名" v-model:value="form.domain"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item path="charset" label="字符集">
            <n-input label="字符集" v-model:value="form.charset"></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item path="userAgent">
        <template #label>
          用户字符串
          <n-tooltip trigger="hover">
            <template #trigger>
              <IconMdiHelpCircle></IconMdiHelpCircle>
            </template>
            浏览器userAgent字符串
          </n-tooltip>
        </template>
        <n-input label="用户字符串" v-model:value="form.userAgent"></n-input>
      </n-form-item>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item path="timeOut" label="超时时间(s)">
            <n-input-number label="超时时间(s)" v-model:value="form.timeOut"></n-input-number>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item path="sleepTime" label="间隔时间(s)">
            <n-input-number label="间隔时间(s)" v-model:value="form.sleepTime"></n-input-number>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item path="retryTimes" label="重试次数">
            <n-input-number label="重试次数" v-model:value="form.retryTimes"></n-input-number>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item path="cycleRetryTimes" label="循环重试次数">
            <n-input-number label="循环重试次数" v-model:value="form.cycleRetryTimes"></n-input-number>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item path="acceptStatCode" label="有效状态码">
            <n-dynamic-tags :disabled="formType === 'view'" v-model:value="form.acceptStatCode" placeholder="请输入"
                            :min="1"/>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item path="userAgent" label="是否使用gzip">
            <n-switch v-model:value="form.useGzip"></n-switch>
          </n-form-item>
        </n-gi>
        <n-gi span="24">
          <n-form-item path="cookies" label="cookie">
            <n-dynamic-input :disabled="formType === 'view'" v-model:value="form.cookies" preset="pair"
                             key-placeholder="请输入参数名" value-placeholder="请输入参数值"/>
          </n-form-item>
        </n-gi>
        <n-gi span="24">
          <n-form-item path="headers" label="headers">
            <n-dynamic-input :disabled="formType === 'view'" v-model:value="form.headers" preset="pair"
                             key-placeholder="请输入参数名" value-placeholder="请输入参数值"/>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item path="status" label="状态">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable
                      placeholder="请选择"></h-dict-radio>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {list, edit, add, del, copy} from '../api/site'
import {ref} from "vue";
import {cloneDeep} from 'lodash-es'

/**
 * 编辑表单相关
 * */
const formModel = ref(false)
/**
 * 表格ref
 * */
const refPageTable = ref()

const formTitle = ref()

const columns = [
  {"key": "title", "title": "站点名称", "columnType": "title", "ellipsis": true},
  {"key": "module", "title": "模块代码"},
  {"key": "domain", "title": "域名"},
  {"key": "status", "title": "状态", dictType: 'content_status'},
  {"key": "useGzip", "title": "是否使用gzip", dictType: 'sys_enabled'},
]

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  title: '',//标题 必填
  module: '', //模块代码，底层数据库使用此区分存储对象 必填
  domain: '', // 域名 必填
  charset: null, //字符集
  userAgent: null, //用户字符串
  timeOut: 5000, //超时时间 单位s
  sleepTime: 1000, //间隔时间 单位s
  retryTimes: 0, // 重试次数
  cycleRetryTimes: 0, //循环重试次数
  acceptStatCode: ['200'], //有效状态码  默认状态码200
  cookies: [], //cookie
  headers: [], //header
  useGzip: false, // 是否使用gzip
  status: 0, //状态
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
}

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))

/**
 * 验证规则
 * */
const rules = {
  title: {
    required: true,
    message: '请输入标题'
  },
  domain: {
    required: true,
    message: '请输入域名'
  },
  module: {
    required: true,
    message: '请选择模块'
  },
  status: {
    required: true,
    message: '请选择状态'
  },
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  title: '',
})

const formType = ref("add")


/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add'
  form.value = cloneDeep(defaultForm)
  formTitle.value = "添加"
  formModel.value = true
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit'
  form.value = cloneDeep(defaultForm)
  Object.assign(form.value, row)
  form.value.cookies = handleObj(form.value.cookies)
  form.value.headers = handleObj(form.value.headers)
  formTitle.value = `编辑「${row.title}」站点`
  formModel.value = true
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view'
  Object.assign(form.value, row)
  form.value.cookies = handleObj(form.value.cookies)
  form.value.headers = handleObj(form.value.headers)
  formTitle.value = `查看「${row.title}」站点`
  formModel.value = true
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  let param = cloneDeep(data)
  param.cookies = handleArr(param.cookies)
  param.headers = handleArr(param.headers)
  if (formType.value === "add") {
    return add(param)
  } else {
    return edit(param)
  }
}

//提交时  将Naive UI中n-dynamic-input组件的数组格式转换成接口所需要的对象格式
function handleArr(data: any): any {
  let filteredArr = data.filter((item: any) => item.key !== '');
  if (filteredArr.length > 0) {
    const obj = filteredArr.reduce((acc: any, cur: any) => {
      acc[`${cur.key}`] = cur.value;
      return acc;
    }, {});
    return obj
  } else {
    return null
  }
}

// 查看详情以及编辑时，将接口返回的对象格式转换为组件所需要的数组格式
function handleObj(data: any): any {
  if (!Array.isArray(data) && data !== null) {
    const arr = Object.entries(data)
      .map(([key, value]) => {
        if (key === '' && value === null) {
          return null; // 忽略空对象
        } else {
          return {key, value};
        }
      })
      .filter(item => item !== null); // 过滤掉空对象
    return arr
  } else {
    return []
  }
}
</script>

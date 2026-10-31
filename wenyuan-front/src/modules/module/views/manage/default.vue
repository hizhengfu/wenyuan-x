<template>
  <h-page-data-table ref="refPageTable"
                     :fetch-data="list"
                     :scroll-x="1200"
                     :params="paramsForm"
                     @add="handleAdd"
                     @rowEdit="handleEdit"
                     :funApiDelete="del"
                     :columns="columns">
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="代码" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="代码" clearable></n-input>
      </n-form-item>
    </template>
    <h-page-form :rules="rules"
                 :model="form"
                 label-width="auto"
                 :refTable="refPageTable"
                 :handleSubmit="handleSubmit"
                 v-model:show="formModel">
      <n-form-item label="名称：" path="name">
        <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
      </n-form-item>
      <n-grid :x-gap="12">
        <n-form-item-gi label="代码：" path="code" :span="18">
          <n-input v-model:value="form.code" placeholder="请输入代码"></n-input>
        </n-form-item-gi>
        <n-form-item-gi label="启用状态：" path="enabled" :span="6">
          <n-switch v-model:value="form.enabled"></n-switch>
        </n-form-item-gi>
      </n-grid>
      <n-form-item label="包名：" path="packages">
        <n-input v-model:value="form.packages" placeholder="请输入包名"></n-input>
      </n-form-item>
      <n-form-item label="作者：" path="author">
        <n-input v-model:value="form.author" placeholder="请输入作者"></n-input>
      </n-form-item>
      <n-form-item label="备注：" path="remarks">
        <n-input type="textarea" v-model:value="form.remarks" placeholder="请输入备注"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {list, add, edit, del, install} from '../../api/module'
import {h, ref} from 'vue';
import {NButton, NIcon} from 'naive-ui';
import {cloneDeep} from 'lodash-es'
import {RouterLink} from 'vue-router';
import Code from '~icons/mdi/code'

const columns = [
  {
    key: 'name', title: '名称',
    render(row: any) {
      return h(RouterLink,
        {
          class: '',
          to: {path: '/module/manage/table', query: {id: row.id, title: row.name}}
        },
        {default: () => row.name}
      )
    }
  },
  {key: 'code', title: '代码'},
  {key: 'enabled', title: '状态', dictType: 'sys_enabled'},
  {key: 'author', title: '作者'},
  {
    key: 'install', title: '后端安装类', align: 'center',
    render(row: any) {
      return h(NButton,
        {
          type: 'warning', size: 'tiny',
          onClick: () => {
            install(row.id)
          }
        }, {
          icon: () => h(NIcon, {}, {default: () => h(Code)}),
          default: () => '点击生成'
        })
    }
  },
  {key: 'createTime', title: '创建时间', columnType: 'date', width: '220px', align: 'center'},
]

/**
 * 编辑表单相关
 * */
const formModel = ref(false)
/**
 * 表格ref
 * */
const refPageTable = ref(null)

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  name: '',
  code: '',
  packages: '',
  enabled: true,
  author: '',
  remarks: '',
}

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))

/**
 * 验证规则
 * */
const rules = {
  name: {
    required: true,
    message: '请输入名称'
  },
  code: {
    required: true,
    message: '请输入代码'
  },
  enabled: {
    required: true,
    message: '请选择状态'
  }
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  code: '',
})


const formType = ref("add")

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add'
  form.value = cloneDeep(defaultForm)
  formModel.value = true
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit'
  Object.assign(form.value, row)
  formModel.value = true
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === "add") {
    return add(data)
  } else {
    return edit(data)
  }
}
</script>

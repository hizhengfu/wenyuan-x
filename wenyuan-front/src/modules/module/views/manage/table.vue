<script lang="ts">
export default {
  name: 'RTable'
}
</script>

<script lang="ts" setup>
import {HModuleTableSelect} from '../../components/form/field'
import {list, add, edit, del, importTable, publish, gen} from '../../api/moduleTable'
import {computed, h, ref, watchEffect} from 'vue';
import {NButton, NDropdown, NIcon} from 'naive-ui';
import {cloneDeep} from 'lodash-es'
import {RouterLink, useRoute, useRouter} from 'vue-router';

const route = useRoute()
const router = useRouter()

const dropdownOptions = [
  {label: '字段管理', key: 'column'},
  {label: '索引管理', key: 'index'},
  {label: '预览列表', key: 'list'},
  {label: '预览表单', key: 'form'},
]

function handleDropdownSelect(key: string, row: any) {
  if (key === 'index') {
    router.push({path: '/module/manage/indexes', query: {tableId: row.id, title: `「${row.title}」索引管理`}})
  } else if (key === 'column') {
    router.push({path: '/module/manage/column', query: {tableId: row.id, title: `「${row.title}」字段管理`}})
  } else if (key === 'list') {
    router.push({path: '/module/view/list', query: {tableId: row.id, title: `「${row.title}」预览列表`}})
  } else if (key === 'form') {
    router.push({path: '/module/view/form', query: {tableId: row.id, title: `「${row.title}」预览表单`}})
  }
}

const columns = [
  {
    key: 'title', title: '标题', resizable: true,
    render(row: any, index: number) {
      return [
        h('span', {}, {default: () => row.title}),
        h(RouterLink,
            {to: {path: '/module/view/list', query: {id: row.id}}, class: 'mx-3'},
            {default: () => '列表'}),
        h(RouterLink,
            {to: {path: '/module/view/form', query: {id: row.id}}, class: 'mx-3'},
            {default: () => '表单'}),
      ]
    }
  },
  {key: 'name', title: '表名', width: '120px'},
  {key: 'module.name', title: '模块名称', width: '120px'},
  {key: 'code', title: '代码', width: '120px'},
  {key: 'enabled', title: '启用', dictType: 'sys_enabled', width: '80px', align: 'center'},
  {
    key: 'publish', title: '快捷操作', align: 'center', width: '280px',
    render(row: any, index: number) {
      return [
        h(NButton, {
              size: 'tiny',
              type: 'primary',
              class: 'mr-2',
              onClick: () => handlePublish(row)
            },
            {
              default: () => '发布'
            }
        ),
        h(NButton, {
              size: 'tiny',
              type: 'primary',
              class: 'mr-2',
              onClick: () => handleGen(row)
            },
            {
              default: () => '生成代码'
            }
        ),
        h(NButton, {
              size: 'tiny',
              type: 'primary',
              class: 'mr-2',
              onClick: () => {
                router.push({path: '/design', query: {id: row.id}})
              }
            },
            {
              default: () => '表单设计'
            }
        ),
        h(NButton, {
              size: 'tiny',
              type: 'primary',
              class: 'mr-2',
              onClick: () => {
                router.push({path: '/module/manage/layout', query: {id: row.id, title: `「${row.title}」布局管理`}})
              }
            },
            {
              default: () => '布局管理'
            }
        )
      ]
    }
  },
  {
    key: 'title', title: '管理数据', align: 'center', width: '120px',
    render(row: any, index: number) {
      return h(NDropdown, {
        options: dropdownOptions,
        onSelect: (key) => {
          handleDropdownSelect(key, row)
        },
      }, {
        default: () => h(NButton, {
          size: 'tiny',
        }, {
          default: () => '请选择'
        })
      })
    },
  },
]

/**
 * 编辑表单相关
 * */
const formModel = ref(false)

/**
 * 表格ref
 * */
const refPageTable = ref()

/**
 * 表单默认值
 * */
const defaultForm = {
  name: '',
  title: '',
  moduleId: '',
  code: '',
  enabledPermission: false,
  enabled: true,
  enableAuth: true,
  enableFlow: false,
  enableExcel: false,
  type: 0,
  author: '',
  remarks: ''
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
    message: '请输入名称'
  },
  name: {
    required: true,
    message: '请输入表名'
  },
  code: {
    required: true,
    message: '请输入代码'
  },
  type: {
    required: true,
    message: '请选择表类型'
  },
  enabled: {
    required: true,
    message: '请选择状态'
  },
  enableAuth: {
    required: true,
    message: '请选择是否登录'
  },
  enabledPermission: {
    required: true,
    message: '请选择权限状态'
  },
  enableExcel: {
    required: true,
    message: '请选择是否启用导入导出'
  },
  enableFlow: {
    required: true,
    message: '请选择流程状态'
  }
}

const moduleId = computed<string>(() => {
  return route.query.id as string
})

/**
 * 查询表单
 * */
const paramsForm = ref({
  moduleId: '',
  name: '',
  title: '',
  code: '',
})

watchEffect(() => {
  if (moduleId.value) {
    paramsForm.value.moduleId = moduleId.value
    refPageTable.value?.handleRefresh()
  }
})


const formType = ref('add')

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
  form.value = cloneDeep(defaultForm)
  Object.assign(form.value, row)
  form.value.moduleId = row.module.id
  formModel.value = true
}

/**
 * 模块发布
 * */
function handlePublish(row: any) {
  publish(row.id)
}

/**
 * 模块代码生成
 * */
function handleGen(row: any) {
  gen(row.id)
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

/**
 * 导入表单
 * */
const formImportModel = ref(false)
const importRules = {
  tableId: {
    required: true,
    message: '请选择需要导入的表'
  },
}
const defaultImportForm = {
  tableId: ''
}
const importForm = ref(cloneDeep(defaultImportForm))

function handleImportSubmit(data: any) {
  return importTable({name: data.tableId, moduleId: route.query.id})
}
</script>
<template>
  <h-page-data-table
      ref="refPageTable"
      :fetch-data="list"
      :scroll-x="1200"
      :params="paramsForm"
      :fun-api-delete="del"
      :columns="columns"
      @add="handleAdd"
      @rowEdit="handleEdit">
    <template #queryForm>
      <n-form-item label="名称" path="title">
        <n-input v-model:value="paramsForm.title" placeholder="名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="表名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="表名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="代码" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="代码" clearable></n-input>
      </n-form-item>
    </template>
    <template #toolbar-extra>
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button @click="formImportModel=true">
            <n-icon size="20px">
              <IconMdiDatabaseSearch></IconMdiDatabaseSearch>
            </n-icon>
          </n-button>
        </template>
        导入数据表
      </n-tooltip>
    </template>
    <h-dialog-form
        v-model:show="formImportModel"
        title="导入表"
        :rules="importRules"
        :model="importForm"
        label-placement="left"
        :ref-table="refPageTable"
        :handle-submit="handleImportSubmit">
      <n-form-item label="名称：" path="tableId">
        <h-module-table-select
            v-model:value="importForm.tableId"
            is-create
            clearable
            filterable placeholder="请输入名称"></h-module-table-select>
      </n-form-item>
    </h-dialog-form>
    <h-page-form
        v-model:show="formModel"
        :rules="rules"
        :model="form"
        label-width="auto"
        :ref-table="refPageTable"
        :handle-submit="handleSubmit">
      <n-form-item label="名称：" path="title">
        <n-input v-model:value="form.title" placeholder="请输入名称"></n-input>
      </n-form-item>
      <n-grid :x-gap="12">
        <n-form-item-gi label="表名：" path="name" :span="12">
          <n-input v-model:value="form.name" placeholder="请输入表名" :disabled="formType==='edit'"></n-input>
        </n-form-item-gi>
        <n-form-item-gi label="代码：" path="code" :span="12">
          <n-input v-model:value="form.code" placeholder="请输入代码"></n-input>
        </n-form-item-gi>
      </n-grid>
      <n-form-item label="类型：" path="type">
        <h-dict-select
            v-model:value="form.type"
            type="module_table_type"
            :disabled="formType==='edit'"
            placeholder="请选择类型"></h-dict-select>
      </n-form-item>
      <n-form-item label="作者：" path="author">
        <n-input v-model:value="form.author" placeholder="请输入作者"></n-input>
      </n-form-item>
      <div class="flex flex-column">
        <n-form-item label="启用用户" path="enableAuth" class="mr-2">
          <n-switch v-model:value="form.enableAuth"></n-switch>
        </n-form-item>
        <n-form-item label="启用权限" path="enabledPermission" class="mr-2">
          <n-switch v-model:value="form.enabledPermission"></n-switch>
        </n-form-item>
        <n-form-item label="启用流程" path="enableFlow" class="mr-2">
          <n-switch v-model:value="form.enableFlow"></n-switch>
        </n-form-item>
        <n-form-item label="启用导入导出" path="enableExcel" class="mr-2">
          <n-switch v-model:value="form.enableExcel"></n-switch>
        </n-form-item>
        <n-form-item label="启用表" path="enabled" class="mr-2">
          <n-switch v-model:value="form.enabled"></n-switch>
        </n-form-item>
      </div>
      <n-form-item label="备注：" path="remarks">
        <n-input v-model:value="form.remarks" type="textarea" placeholder="请输入备注"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>

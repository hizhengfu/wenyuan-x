<template>
  <h-dialog v-model:show="dialog" :title="props.title" style="width: 80%;">
    <h-page-data-table ref="refPageTableCmt" :fetch-data="fetchCommentData" :showOperate="true" :isAdd="false"
      :isEdit="false" :funApiDelete="del" :params="paramsForm" :columns="columns" title="留言管理">
      <template #queryForm>
        <n-form-item label="标题" path="title">
          <n-input v-model:value="paramsForm.title" placeholder="标题"></n-input>
        </n-form-item>
        <n-form-item label="留言状态" path="status">
          <h-dict-select v-model:value="paramsForm.status" type="content_status" placeholder="请选择留言状态"></h-dict-select>
        </n-form-item>
      </template>
    </h-page-data-table>
  </h-dialog>

  <h-dialog-form v-model:show="auditDialog" :model="form" :rules="rules" :handleSubmit="auditSubmit" :title="auditTitle">
    <n-form-item label="标题">
      {{ selectComment.title }}
    </n-form-item>
    <n-form-item label="内容">
      {{ selectComment.content }}
    </n-form-item>
    <n-form-item path="auditContent" label="审核内容">
      <n-input type="textarea" v-model:value="form.auditContent" placeholder="请输入审核内容"></n-input>
    </n-form-item>
    <n-form-item label="留言状态" path="status">
      <h-dict-radio v-model:value="form.status" type="content_status" placeholder="请选择留言状态"></h-dict-radio>
    </n-form-item>
  </h-dialog-form>
</template>

<script lang="ts" setup>
import {NButton} from 'naive-ui'
import {computed, h, ref} from "vue";
import {audit, del, listByContentId} from '../api/comment'
import {cloneDeep} from 'lodash-es'

const refPageTableCmt = ref()
const props = defineProps({
  title: { type: String },
  contentId: { type: String },
  show: {
    type: Boolean, default: false
  }
})

const emits = defineEmits(["update:show"])
const dialog = computed<boolean>({
  get() {
    return props.show as boolean
  },
  set(value) {
    emits('update:show', value)
  }
})

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  auditContent: '',
  status: 0,
}

/**
 * 表单
 * */
const form: any = ref(cloneDeep(defaultForm))

/**
 * 验证规则
 * */
const rules = {
  auditContent: {
    required: true,
    message: '请输入审核内容'
  },
}

/**
 * 留言管理
 * */
const columns = [
  { key: 'title', title: '留言标题', columnType: 'title' },
  {
    key: 'content', title: '留言内容', ellipsis: {
      width: '300px'
    }
  },
  { key: 'createTime', title: '发布时间', columnType: 'dateTime' },
  {
    key: 'auditContent', title: '审核内容', ellipsis: {
      width: '30%'
    }
  },
  { key: 'status', title: '留言状态', width: 100, dictType: 'content_status' },
  {
    key: 'operate', type: 'operate', title: '操作', width: 120, render(row: any, index: number) {
      return h(
        NButton,
        {
          size: 'tiny',
          type: 'warning',
          class: 'mr-3',
          onClick: () => {
            handleAudit(row, index)
          }
        },
        {
          default: () => '审核'
        }
      )
    }
  },
];

/**
 * 构建查询函数
 * */
async function fetchCommentData(params: any) {
  return listByContentId(props.contentId as string, params)
}

/**
 * 查询表单
 * */
const paramsForm = ref({
  title: '',
  categoryId: '',
  status: null,
})


/**
 * 如下代码留言审核相关
 * */
const auditDialog = ref(false)
const auditTitle = ref('审核留言')
const selectComment = ref()

/**
 * 提交审核
 * */
function auditSubmit(data: any) {
  let a = {
    auditContent: form.value.auditContent,
    status: form.value.status,
    id: form.value.id
  }
  audit(a).then(res => {
    refPageTableCmt.value?.handleQuery()
    auditDialog.value = false
  })
}

/**
 * 审核
 * */
function handleAudit(row: any, index: number) {
  form.value= row
  auditDialog.value = true
  selectComment.value = row
  auditTitle.value = '审核 「' + row.title + '」 '
}

</script>

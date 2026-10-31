<template>
  <h-page-data-table
    ref="refPageTable"
    title="答卷管理"
    permission="CMS_FEEDBACK"
    :fetch-data="listQuestionAnswer"
    :scroll-x="900"
    :params="paramsForm"
    :columns="columns"
    :is-add="false"
    :is-delete="false"
    :is-copy="false"
    :is-export="false"
    @rowView="handleView"
    @rowEdit="handleEdit"
  >
    <template #queryForm>
      <n-form-item label="答卷标题" path="title">
        <n-input v-model:value="paramsForm.title" placeholder="请输入答卷标题" clearable />
      </n-form-item>
      <n-form-item label="答卷状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="content_status" placeholder="请选择状态" clearable />
      </n-form-item>
    </template>
    <h-page-form
      v-model:show="formModel"
      :model="form"
      label-width="auto"
      :title="formTitle"
      width="80%"
      :disabled="formType === 'view'"
      :ref-table="refPageTable"
      :handle-submit="handleSubmit"
    >
      <FeedbackDetail :id="form.id" />
      <n-form-item label="答卷状态" class="mt-4">
        <h-dict-radio v-model:value="form.status" type="content_status" />
      </n-form-item>
      <n-collapse v-model:expanded-names="replyExpanded">
        <n-collapse-item title="管理员回复（可选）" name="reply">
          <n-form-item label="回复内容">
            <n-input v-model:value="form.answerReply" placeholder="需要回复时填写" clearable type="textarea" />
          </n-form-item>
        </n-collapse-item>
      </n-collapse>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { listQuestionAnswer, updateAnswerReply, updateAnswerStatus, loadQuestionAnswer } from '../../../api/feedback';
import FeedbackDetail from './feedbackDetail.vue';

const route = useRoute();
const paramsForm = ref({ title: '', status: null, feedbackId: route.query.id });
const formType = ref<'view' | 'edit'>('view');
const formModel = ref(false);
const form = ref({ id: '', title: '', status: 2, answerReply: '', sourceChannel: null });
const formTitle = computed(() => `${formType.value === 'view' ? '查看答卷' : '审核答卷'}「${form.value.title}」`);
const refPageTable = ref();
const replyExpanded = ref<string[]>([]);
const originalReply = ref('');
const columns = [
  {
    key: 'title',
    title: '答卷标题',
    columnType: 'title',
    width: '300px',
    resizable: true,
    ellipsis: { tooltip: true },
  },
  { key: 'createTime', title: '提交时间', columnType: 'dateTime', width: '170px' },
  { key: 'status', title: '答卷状态', width: '100px', columnType: 'dict', dictType: 'content_status' },
];

function handleView(row: any) {
  return openAnswer(row, 'view');
}
function handleEdit(row: any) {
  return openAnswer(row, 'edit');
}

let detailRequest = 0;
async function openAnswer(row: any, type: 'view' | 'edit') {
  const request = ++detailRequest;
  const response = await loadQuestionAnswer({ feedbackAnswerId: row.id });
  if (request !== detailRequest) return;
  formType.value = type;
  form.value = {
    ...row,
    ...response.data,
    id: row.id,
    status: row.status,
    answerReply: response.data.answerReply || '',
  };
  originalReply.value = form.value.answerReply;
  replyExpanded.value = [];
  formModel.value = true;
}

function handleSubmit(data: typeof form.value) {
  const status = { feedbackAnswerId: form.value.id, status: data.status };
  if (data.answerReply === originalReply.value) return updateAnswerStatus(status);
  return updateAnswerReply({ ...status, answerReply: data.answerReply, sourceChannel: data.sourceChannel });
}

watch(
  () => route.query.id,
  (id) => {
    ++detailRequest;
    formModel.value = false;
    paramsForm.value.feedbackId = id;
    refPageTable.value?.handleQuery();
  },
);
</script>

<template>
  <h-page-data-table ref="refPageTable" :fetch-data="codeList" :params="paramsForm" :showOperate="false"
    @rowView="handleView" :columns="columns" :is-add="false">
    <template #queryForm>
      <n-form-item label="发送方式" path="type">
        <h-dict-select v-model:value="paramsForm.type" clearable placeholder="请选择发送方式"
          type="sys_verification_type"></h-dict-select>
      </n-form-item>
      <n-form-item label="业务场景" path="scene">
        <h-dict-select v-model:value="paramsForm.scene" clearable placeholder="请选择业务场景"
          type="sys_verification_scene"></h-dict-select>
      </n-form-item>
      <n-form-item label="接收者信息" path="value">
        <n-input v-model:value="paramsForm.value" clearable placeholder="请输入接收者信息"></n-input>
      </n-form-item>
      <n-form-item label="发送时间">
        <n-date-picker v-model:value="paramsForm.dateTimeList" type="datetimerange" clearable></n-date-picker>
      </n-form-item>
    </template>
    <h-page-form :model="form" :title="formTitle" :disabled="formType === 'view'" :refTable="refPageTable"
      v-model:show="formModel" :enable-cancel="false">
      <div class="page-detail" ref="refDetail">
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>
              业务场景
            </td>
            <td colspan="2">
              {{ form.enabled || '' }}
            </td>
            <td>
              发送方式
            </td>
            <td colspan="2">
              <h-dict-text type="sys_verification_type" v-model:value="form.type"></h-dict-text>
            </td>
            <td>
              接收者信息
            </td>
            <td colspan="2">
              {{ form.value || '' }}
            </td>
          </tr>
          <tr>
            <td>
              发送状态
            </td>
            <td colspan="2">
              <h-dict-text type="sys_send_status" v-model:value="form.sendStatus"></h-dict-text>
            </td>
            <td>
              发送结果
            </td>
            <td colspan="5">
              {{ form.sendMessage || '' }}
            </td>
          </tr>
          <tr>
            <td>
              发送详细内容
            </td>
            <td colspan="8">
              <div v-html="form.content"></div>
            </td>
          </tr>
          <tr>
            <td>
              创建时间
            </td>
            <td colspan="2">
              {{ form.createTime ? dayjs(form.createTime).format('YYYY-MM-DD HH:mm:ss')
                : '--' }}
            </td>
            <td>
              更新时间
            </td>
            <td colspan="2">
              {{ form.updateTime ? dayjs(form.updateTime).format('YYYY-MM-DD HH:mm:ss') : '--' }}
            </td>
            <td>
              状态
            </td>
            <td colspan="2">
              {{ form.enabled || '' }}
            </td>
          </tr>
          <tr>
            <td>
              验证码
            </td>
            <td colspan="8">
              {{ form.code || '' }}
            </td>
          </tr>
        </n-table>
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { codeList } from '../api/log';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import dayjs from 'dayjs';

const columns = [
  { key: 'value', title: '接收者信息', minWidth: '220px', resizable: true, ellipsis: true, columnType: 'title', fixed: 'left' },
  { key: 'code', title: '验证码', width: '220px', resizable: true, ellipsis: true },
  { key: 'type', title: '发送方式', width: '210px', resizable: true, align: 'center', dictType: 'sys_verification_type', ellipsis: { tooltip: true } },
  { key: 'scene', title: '业务场景', width: '210px', resizable: true, align: 'center', dictType: 'sys_verification_scene', ellipsis: { tooltip: true } },
  { key: 'sendStatus', title: '发送状态', width: '210px', resizable: true, align: 'center', dictType: 'sys_send_status', ellipsis: { tooltip: true } },
  { key: 'createTime', title: '创建时间', columnType: 'dateTime', width: '210px', align: 'center', ellipsis: { tooltip: true } },
  { key: 'updateTime', title: '更新时间', columnType: 'dateTime', width: '210px', align: 'center', ellipsis: { tooltip: true } },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref(null);

const formTitle = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  createBy: '',
  createTime: null,
  updateTime: null,
  enabled: true,
  type: '',
  value: '',
  scene: '',
  code: '',
  content: '',
  sendStatus: '',
  sendMessage: '',
  bizId: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 查询表单
 * */
const paramsForm = ref({
  type: '',
  value: '',
  scene: '',
  dateTimeList: null
});

const formType = ref('view');

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  formTitle.value = '查看详情';
  formModel.value = true;
}
</script>

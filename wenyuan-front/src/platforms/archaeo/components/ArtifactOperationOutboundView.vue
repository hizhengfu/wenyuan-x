<template>
  <n-card size="small" :segmented="{ content: 'soft' }" bordered>
    <template #cover>
      <div style="padding: 15px 15px 0">
        <n-table :single-line="false">
          <colgroup>
            <col style="width: 100px" />
            <col />
            <col style="width: 100px" />
            <col />
          </colgroup>
          <tr>
            <td>出库原因</td>
            <td colspan="3">
              <n-flex justify="space-between" align="center">
                {{ formView.operationDetail.outPurpose }}
                <n-flex :wrap="false">
                  <n-tooltip trigger="hover">
                    <template #trigger>
                      <n-button @click="handlePrint">
                        <template #icon>
                          <n-icon>
                            <IconMdiPrinter></IconMdiPrinter>
                          </n-icon>
                        </template>
                      </n-button>
                    </template>
                    <slot>点击开始打印</slot>
                  </n-tooltip>
                  <n-tooltip trigger="hover">
                    <template #trigger>
                      <n-button :loading="downloading" @click="handleDownload">
                        <template #icon>
                          <n-icon>
                            <IconMdiFileWordBoxOutline></IconMdiFileWordBoxOutline>
                          </n-icon>
                        </template>
                      </n-button>
                    </template>
                    <slot>下载 {{ docTitle }}（.docx）</slot>
                  </n-tooltip>
                </n-flex>
              </n-flex>
            </td>
          </tr>
          <tr>
            <td>文物去向</td>
            <td colspan="3">{{ formView.operationDetail.artifactDest }}</td>
          </tr>
          <tr>
            <td>是否归还</td>
            <td>{{ formView.operationDetail.needReturn ? '是' : '否' }}</td>
            <td>预计时间</td>
            <td>
              <n-time :time="formView.operationDetail.expectedReturn" format="yyyy-MM-dd" />
            </td>
          </tr>
          <tr>
            <td>出库人</td>
            <td>{{ formView.operatorBy }}</td>
            <td>出库时间</td>
            <td>
              <n-time :time="formView.operationTime" format="yyyy-MM-dd" />
            </td>
          </tr>
          <tr>
            <td>接收人</td>
            <td colspan="3">{{ formView.operationDetail.receiver }}</td>
          </tr>
          <tr>
            <td>出库备注</td>
            <td colspan="3">{{ formView.remarks }}</td>
          </tr>
        </n-table>
      </div>
      <n-divider style="margin-bottom: 0"></n-divider>
    </template>
    <template #header> 关联文物列表</template>
    <HDataTable
      v-bind="$attrs"
      v-model:data="pageData"
      :columns="columns"
      :params="params"
      :pagination="false"
      minHeight="300px"
      :fetchData="artifactList"
      scroll-x="1200"
      :showOperate="false"
    ></HDataTable>
  </n-card>
</template>
<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { artifactList, get } from '../api/operation.ts';
import { downloadOperateDocx } from '../api/document.ts';
import { compiledTemplate, htmlPrint, tableColumnData } from '@/components/basic/print/PrintUtils.ts';
import outboundPrintTemplate from '../assets/templates/OutboundPrintTemplate.html?raw';
import dayjs from 'dayjs';

const props = defineProps({
  recordId: { type: String, required: true },
});

const params = {
  recordId: props.recordId,
  type: 1,
};

const pageData = ref();

const columns = [
  { key: 'index', title: '序号', width: '60px', align: 'center', columnType: 'index', fixed: 'left' },
  { key: 'code', title: '编号', width: '160px', resizable: true, ellipsis: true, fixed: 'left' },
  { key: 'image', title: '缩略图', columnType: 'image', width: '80px' },
  {
    key: 'name',
    title: '文物名称',
    width: '220px',
    resizable: true,
    ellipsis: true,
  },
  {
    key: 'material',
    title: '质地',
    width: '160px',
    dictType: 'wms_artifact_material_type',
    resizable: true,
    ellipsis: true,
  },
  { key: 'project.name', title: '遗址名称', width: '160px', resizable: true, ellipsis: true },
  { key: 'projectCellName', title: '遗迹单位', width: '160px', resizable: true, ellipsis: true },
  {
    key: 'quantity',
    title: '数量',
    width: '100px',
    align: 'right',
    render: (row: any) => {
      return `${row.quantity}${row.quantityUnit}`;
    },
  },
  {
    key: 'saveStatus',
    title: '保存状况',
    width: '120px',
    dictType: 'wms_artifact_save_status',
    resizable: true,
    ellipsis: true,
  },
  {
    key: 'flowStatus',
    title: '归还状态',
    width: '100px',
    dictType: 'sys_ok',
  },
];

/**
 * 表单默认值
 * */
const defaultFormView = {
  operationDetail: {
    outPurpose: undefined,
    receiver: '',
    artifactDest: '',
    needReturn: true,
    expectedReturn: undefined,
  },
  operatorBy: '',
  operationTime: undefined,
  quantity: 1,
  remarks: '',
};
const formView = ref(cloneDeep(defaultFormView));

const printData = computed(() => {
  let now = dayjs();
  return [
    {
      ...formView.value,
      operationTime: dayjs(formView.value.operationTime).format('YYYY-MM-DD'),
      printDate: now.format('YYYY-MM-DD'),
      list: pageData.value,
    },
  ];
});

function fetchData(id: string) {
  formView.value = cloneDeep(defaultFormView);
  get(id, '1').then((resp: any) => {
    formView.value = resp.data || {};
  });
}

/**
 * 单据标题：出库 + outPurpose=T6 借展 → 借调申请单，否则出库单
 * （后端会做同样判定，并以服务端为准）
 */
const docTitle = computed(() => {
  // outPurpose 在视图里被字典翻译为中文，但原始字典 key 是 T6
  const code = (formView.value as any)?.operationDetail?.outPurposeCode || (formView.value as any)?.operationDetail?.outPurpose;
  if (code === 'T6' || /借/.test(String(code))) return '借调申请单';
  return '出库单';
});

const downloading = ref(false);
async function handleDownload() {
  if (!props.recordId) return;
  downloading.value = true;
  try {
    const fileName = `${docTitle.value}_${(formView.value as any)?.code || props.recordId}.docx`;
    await downloadOperateDocx(props.recordId, fileName);
  } finally {
    downloading.value = false;
  }
}

function handlePrint() {
  let now = dayjs();
  let tableData = tableColumnData(columns, pageData.value);
  let html = compiledTemplate(outboundPrintTemplate, [
    {
      ...formView.value,
      operationTime: dayjs(formView.value.operationTime).format('YYYY-MM-DD'),
      printDate: now.format('YYYY-MM-DD'),
      list: tableData,
    },
  ]);
  htmlPrint(html, '打印出库单');
}

watch(
  () => props.recordId,
  (nValue: string) => {
    fetchData(nValue);
  },
  {
    immediate: true,
  },
);
</script>

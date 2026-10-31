<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :scroll-x="1800"
    :fetch-data="restorationContractApi.page"
    :is-delete="false"
    :is-add="canWriteContract"
    :is-edit="canWriteContract"
    :add-permission="RESTORATION_CONTRACT_WRITE_PERMISSION"
    :edit-permission="RESTORATION_CONTRACT_WRITE_PERMISSION"
    :params="queryParams"
    row-key-field="id"
    @add="onAdd(queryParams.restorationId)"
    @row-edit="onEdit"
    @row-view="onView"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="RESTORATION_CONTRACT_ORIGINAL" /></template>
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="合同号 / 乙方 / 证书号" />
      </NFormItem>
      <NFormItem label="合同编号">
        <NInput v-model:value="queryParams.contractNo" clearable />
      </NFormItem>
      <NFormItem label="乙方">
        <NInput v-model:value="queryParams.partyB" clearable />
      </NFormItem>
      <NFormItem label="修复项目">
        <RestorationProjectPicker v-model:value="queryParams.restorationId" />
      </NFormItem>
    </template>

    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <HPageForm
      v-if="formShow"
      :key="formKey"
      v-model:show="formShow"
      :title="title"
      :model="vo"
      :rules="mode === 'delete' ? {} : rules"
      :auto-reload="false"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="140"
      width="820px"
    >
      <template #form-action>
        <NButton v-if="vo.id && canPrintContract" style="margin-right: 8px" @click="() => onPrint()">打印预览</NButton>
      </template>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NAlert v-if="loading" type="info">正在核对原馆、合同和机构资质…</NAlert>
      <NAlert v-if="mode === 'delete'" type="warning">确认删除合同 {{ vo.contractNo }}（{{ vo.id }}），所属项目 {{ vo.restorationId }}。原件读取身份继续保留。</NAlert>
      <template v-else>
      <NAlert
        v-if="vo.collectionLevel === 'FIRST'"
        type="warning"
        style="margin-bottom: 12px"
      >
        一级文物委外修复合同<b>必须</b>填写保险条款（《文物保护法》要求）。乙方资质证书必须真实存在，且有效期覆盖计划竣工日期。
      </NAlert>

      <NFormItem label="修复项目" path="restorationId">
        <RestorationProjectPicker
          v-model:value="vo.restorationId"
          :disabled="mode !== 'add'"
          style="width: 360px"
        />
      </NFormItem>
      <NFormItem label="合同编号" path="contractNo">
        <NInput v-model:value="vo.contractNo" style="width: 320px" />
      </NFormItem>
      <NFormItem label="甲方（本馆）" path="partyA">
        <NInput v-model:value="vo.partyA" style="width: 360px" />
      </NFormItem>
      <NFormItem label="乙方（承担单位）" path="partyBOrgId">
        <RestorationOrgPicker
          v-model:value="vo.partyBOrgId"
          :org-id="vo.orgId"
          :disabled="!vo.orgId || !handleSubmit"
          style="width: 360px"
          @update:value="vo.partyBCertId = undefined"
        />
        <NText v-if="vo.partyB && !vo.partyBOrgId" depth="3" style="margin-left: 8px">
          历史值：{{ vo.partyB }}
        </NText>
      </NFormItem>
      <NFormItem label="乙方资质证书" path="partyBCertId" feedback="不适用可留空；适用证书须属于乙方并在计划期间有效。">
        <QualificationCertPicker
          v-model:value="vo.partyBCertId"
          holder-type="ORG"
          :holder-id="vo.partyBOrgId"
          placeholder="如适用请选择资质证书（选填）"
          style="width: 420px"
        />
        <NText v-if="vo.partyBCertNo && !vo.partyBCertId" depth="3" style="margin-left: 8px">
          历史值：{{ vo.partyBCertNo }}
        </NText>
      </NFormItem>
      <NFormItem label="合同金额（元）" path="contractAmount">
        <NInputNumber v-model:value="contractAmountYuan" :min="0" :precision="2" style="width: 240px" />
      </NFormItem>
      <NFormItem label="签订日期" path="signDate">
        <NDatePicker v-model:value="vo.signDate" type="date" clearable />
      </NFormItem>
      <NFormItem label="计划开工" path="plannedStart">
        <NDatePicker v-model:value="vo.plannedStart" type="date" clearable />
      </NFormItem>
      <NFormItem label="计划完工" path="plannedEnd">
        <NDatePicker v-model:value="vo.plannedEnd" type="date" clearable />
      </NFormItem>
      <NFormItem label="保险条款" path="insuranceInfo">
        <NInput
          v-model:value="vo.insuranceInfo"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 12 }"
          :placeholder="vo.collectionLevel === 'FIRST' ? '一级文物必填' : ''"
        />
      </NFormItem>
      <NFormItem label="违约条款" path="liquidatedDamages">
        <NInput v-model:value="vo.liquidatedDamages" type="textarea" :autosize="{ minRows: 2, maxRows: 10 }" />
      </NFormItem>
      <NFormItem label="知识产权条款" path="copyrightClause">
        <NInput v-model:value="vo.copyrightClause" type="textarea" :autosize="{ minRows: 2, maxRows: 10 }" />
      </NFormItem>
      <NFormItem label="合同附件" path="attachmentFileIds">
        <HUploadInput
          :value="vo.attachmentFileIds"
          :disabled="!handleSubmit"
          @update:value="updateFiles"
          @upload-finish="uploadFinished"
          accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
          placeholder="上传合同扫描件（多份分次上传后逗号拼接）"
          clearable
        >
          上传合同
        </HUploadInput>
      </NFormItem>
      <NFormItem label="备注" path="remarks">
        <NInput v-model:value="vo.remarks" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" />
      </NFormItem>
      </template>
    </HPageForm>

    <HPageView v-model:show="viewShow" title="合同详情" width="820px">
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NAlert v-if="loading" type="info">正在读取合同…</NAlert>
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="合同编号">{{ viewRow.contractNo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="藏品">{{ `${viewRow.collectionTotalRegNo || ''} ${viewRow.collectionName || ''}`.trim() || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="藏品级别">{{ ({ FIRST: '一级', SECOND: '二级', THIRD: '三级', GENERAL: '一般', UNRATED: '未定级' } as any)[viewRow.collectionLevel || ''] || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="甲方（本馆）">{{ viewRow.partyA || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="乙方（承担单位）">{{ viewRow.partyB || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="乙方资质证书号">{{ viewRow.partyBCertNo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="合同金额">{{ formatMoney(viewRow.contractAmount) }}</NDescriptionsItem>
        <NDescriptionsItem label="签订日期">{{ formatDateValue(viewRow.signDate) }}</NDescriptionsItem>
        <NDescriptionsItem label="计划开工">{{ formatDateValue(viewRow.plannedStart) }}</NDescriptionsItem>
        <NDescriptionsItem label="计划完工">{{ formatDateValue(viewRow.plannedEnd) }}</NDescriptionsItem>
        <NDescriptionsItem label="保险条款">{{ viewRow.insuranceInfo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="违约条款">{{ viewRow.liquidatedDamages || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="知识产权条款">{{ viewRow.copyrightClause || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="合同附件"><HFileList :value="viewRow.attachmentFileIds" :revision="viewRow.id" /></NDescriptionsItem>
        <NDescriptionsItem label="备注">{{ viewRow.remarks || '--' }}</NDescriptionsItem>
      </NDescriptions>
      <RestorationContractLinkage v-if="viewRow" :key="viewRow.id" :contract="viewRow" />
    </HPageView>
    <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { computed, h, reactive, ref, resolveDirective, withDirectives } from 'vue';
import { useContractWindow } from '../composables/useContractWindow';
import {
  NAlert, NButton, NDatePicker, NDescriptions, NDescriptionsItem, NFormItem, NInput, NInputNumber, NTag, NText,
} from 'naive-ui';
import { restorationContractApi } from '../api/contract';
import RestorationOrgPicker from '../components/RestorationOrgPicker.vue';
import RestorationProjectPicker from '../components/RestorationProjectPicker.vue';
import QualificationCertPicker from '../components/QualificationCertPicker.vue';
import RestorationContractLinkage from '../components/RestorationContractLinkage.vue';
import type { RestorationContractVo } from '../typings';
import { formatDateTime, formatDateValue } from '@/utils/datetime';
import CollectionPrintPreviewModal from '@/modules/collection/components/CollectionPrintPreviewModal.vue';
import {
  COLLECTION_PRINT_RENDER_CODES,
  RESTORATION_CONTRACT_WRITE_CODES,
  RESTORATION_CONTRACT_WRITE_PERMISSION,
} from '../utils/restorationPermissions';
import { useAuthStore } from '@/store';


const tableRef = ref<any>(null);
const auth = useAuthStore();
// HPageDataTable 未传 permission 时 judgePermissionByCode 恒为 true，单传 add/edit-permission 拦不住，另用 is-add / is-edit 按同一组码开关。
const canWriteContract = computed(() => auth.hasPermission(RESTORATION_CONTRACT_WRITE_CODES));
const canPrintContract = computed(() => auth.hasPermission(COLLECTION_PRINT_RENDER_CODES));
const {formShow,viewShow,printShow,loading,error,title,vo,viewRow,printData,mode,formKey,handleSubmit,updateFiles,uploadFinished,onAdd,onEdit,onDelete,onView,onPrint}=useContractWindow(()=>tableRef.value?.handleRefresh?.());
const contractAmountYuan = computed<number | null>({
  get: () => vo.value.contractAmount == null ? null : vo.value.contractAmount / 100,
  set: (value) => { vo.value.contractAmount = value == null ? null : Math.round(value * 100); },
});

const queryParams = reactive({
  keyword: null as string | null,
  contractNo: null as string | null,
  partyB: null as string | null,
  restorationId: null as string | null,
});

const rules = {
  restorationId: { required: true, message: '请选择修复项目', trigger: ['change', 'blur'] },
  contractNo: { required: true, message: '请输入合同编号', trigger: ['blur', 'input'] },
  partyBOrgId: { required: true, message: '请选择乙方（承担单位）', trigger: ['change', 'blur'] },
};

// 「操作」列（删除 + 表格自带的编辑）：
// HPageDataTable.initColumn 找到 key/type 均为 operate 的列时原地合并编辑按钮，不调整它在数组中的位置；
// naive-ui 按数组顺序排列，fixed:'right' 不会把首列挪到右侧，所以该列必须放在数组最后才显示在最右边。
// 没有合同写权限时整列不渲染（编辑与删除同一组码）。
const operateColumn = {title:'操作',key:'operate',type:'operate',width:220,align:'center',fixed:'right' as const,render:(row:RestorationContractVo)=>{
    const permission=resolveDirective('permission');
    const btns: any[] = [
      h(NButton,{text:true,type:'primary',size:'small',onClick:()=>onView(row)},{ default: () => '查看' }),
    ];
    if (canPrintContract.value) {
      btns.push(h(NButton,{text:true,size:'small',style:'margin-left:8px',onClick:()=>onPrint(row)},{ default: () => '打印' }));
    }
    if (canWriteContract.value) {
      const del=h(NButton,{size:'tiny',type:'error',class:'mr-3',style:'margin-left:8px',onClick:()=>onDelete(row)},{default:()=> '删除'});
      btns.push(permission?withDirectives(del,[[permission,[...RESTORATION_CONTRACT_WRITE_CODES]]]):del);
    }
    return btns;
  }};
const columns = computed<any[]>(() => [
  { title: '合同编号', key: 'contractNo', columnType: 'title', width: 220, resizable: true, ellipsis: { tooltip: true } },
  {
    title: '藏品', key: 'collection', width: 240, resizable: true, ellipsis: { tooltip: true },
    render: (r: RestorationContractVo) => `${r.collectionTotalRegNo || ''} ${r.collectionName || ''}`.trim() || '—',
  },
  {
    title: '级别', key: 'collectionLevel', width: 100, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationContractVo) => h(
      NTag,
      { size: 'small', bordered: false, type: r.collectionLevel === 'FIRST' ? 'error' : 'default' },
      { default: () => ({ FIRST: '一级', SECOND: '二级', THIRD: '三级', GENERAL: '一般', UNRATED: '未定级' } as any)[r.collectionLevel || ''] || r.collectionLevel || '—' },
    ),
  },
  { title: '乙方', key: 'partyB', width: 220, resizable: true, ellipsis: { tooltip: true } },
  { title: '乙方证书号', key: 'partyBCertNo', width: 180, resizable: true, ellipsis: { tooltip: true } },
  { title: '合同金额', key: 'contractAmount', width: 130, render: (r: RestorationContractVo) => formatMoney(r.contractAmount) },
  {
    title: '工期', key: 'period', width: 260, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationContractVo) => `${formatDateValue(r.plannedStart)} ~ ${formatDateValue(r.plannedEnd)}`,
  },
  {
    title: '保险条款', key: 'hasInsurance', width: 90, ellipsis: { tooltip: true }, resizable: true,
    render: (r: RestorationContractVo) => (r.insuranceInfo ? '有' : '无'),
  },
  { title: '创建时间', key: 'createTime', width: 190, ellipsis: { tooltip: true }, resizable: true, render: (r: RestorationContractVo) => formatDateTime(r.createTime) },
  operateColumn,
]);

function formatMoney(value?: number | null) {
  return value == null ? '--' : `¥${(value / 100).toFixed(2)}`;
}
</script>

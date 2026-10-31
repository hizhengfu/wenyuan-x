<template>
  <NForm ref="formRef" :model="inner" label-width="100px">
    <NAlert
      v-if="businessType === 'COLLECTION_ACQUISITION' && inner.unit === '套'"
      type="info"
      :bordered="false"
      style="margin-bottom: 12px"
    >
      数量按整套填写；入馆时逐套核收。套内分件关系需在正式藏品台账另行维护。
    </NAlert>
    <NAlert v-if="businessType === 'COLLECTION_ACQUISITION'" type="info" :bordered="false" style="margin-bottom: 12px">
      尚未确定具体对象时，可先用拟征集项目名称保存意向草稿，逐步补齐对象和材料。补录线下已办理手续应如实填写实际日期及依据；提交前须完成本次征集所需手续。
    </NAlert>
    <NAlert v-if="businessType === 'COLLECTION_APPRAISAL'" type="info" :bordered="false" style="margin-bottom: 12px">
      本页记录馆内初步鉴选，建议级别可不填写。征集前真伪、价值评估在征集申请材料中登记；正式级别以行政定级结果为准。
    </NAlert>
    <NGrid :cols="2" :x-gap="16">
      <template v-for="field in fields" :key="field.key">
        <NGi v-if="field.section" :span="2"
          ><NDivider title-placement="left">{{ field.section }}</NDivider></NGi
        >
        <NGi :span="field.type === 'textarea' ? 2 : field.span || 1">
          <FlowFormItem
            :field="field.key"
            :label="field.label"
            :rule="fieldRule(field)"
            :required="isSubmitRequired(field)"
            v-slot="{ disabled }"
          >
            <CollectionSelect
              v-if="field.type === 'collection'"
              :value="collectionValue(field)"
              :multiple="field.multiple"
              :life-status="field.lifeStatus"
              :life-statuses="field.lifeStatuses"
              :level="field.level"
              :placeholder="field.placeholder"
              :disabled="disabled || field.readonly"
              :fallback-label="collectionFallbackLabel(field)"
              @update:value="setCollectionValue(field, $event)"
              @selected="onCollectionSelected(field, $event)"
            />
            <UserSelect
              v-else-if="field.type === 'user'"
              :value="selectValue(field)"
              :multiple="field.multiple"
              :placeholder="field.placeholder"
              :disabled="disabled || field.readonly"
              @update:value="setSelectValue(field, $event)"
            />
            <AppraisalMemberEditor
              v-else-if="field.type === 'appraisalMembers'"
              :value="fieldValue(field.key)"
              :disabled="disabled || field.readonly"
              @update:value="setValue(field.key, $event)"
            />
            <WarehouseTreeSelect
              v-else-if="field.type === 'warehouse'"
              :value="selectValue(field)"
              :multiple="field.multiple"
              :allowed-types="field.allowedTypes"
              :placeholder="field.placeholder"
              :disabled="disabled || field.readonly"
              @update:value="setSelectValue(field, $event)"
            />
            <RestorationOrgPicker
              v-else-if="field.type === 'restorationOrg'"
              :value="fieldValue(field.key)"
              :placeholder="field.placeholder"
              :disabled="disabled || field.readonly"
              @update:value="setValue(field.key, $event)"
            />
            <NSelect
              v-else-if="field.type === 'plan'"
              :value="fieldValue(field.key)"
              :options="planOptions"
              filterable
              clearable
              :placeholder="field.placeholder || '选择征集计划'"
              :disabled="disabled || field.readonly"
              @update:value="setOptionValue(field.key, 'planTitle', $event, planOptions)"
            />
            <NSelect
              v-else-if="field.type === 'category'"
              :value="fieldValue(field.key)"
              :options="categoryOptions"
              filterable
              clearable
              :placeholder="field.placeholder || '选择文物分类'"
              :disabled="disabled || field.readonly"
              @update:value="setOptionValue(field.key, 'categoryName', $event, categoryOptions)"
            />
            <NSelect
              v-else-if="field.type === 'acquisitionSource'"
              :value="fieldValue(field.key)"
              :options="sourceOptions"
              filterable
              clearable
              placeholder="请选择征集方式"
              :disabled="disabled || field.readonly"
              @update:value="setAcquisitionSource($event)"
            />
            <NSelect
              v-else-if="field.type === 'acquisitionUnit'"
              :value="fieldValue(field.key)"
              :options="acquisitionUnitOptions"
              placeholder="选择件或套"
              :disabled="disabled || field.readonly"
              @update:value="setValue(field.key, $event)"
            />
            <NSelect
              v-else-if="field.key === 'purpose' && businessType === 'COLLECTION_REQUISITION'"
              :value="fieldValue(field.key)"
              :options="requisitionPurposeOptions"
              :disabled="disabled || field.readonly"
              @update:value="setValue(field.key, $event)"
            />
            <HDictSelect
              v-else-if="field.type === 'dict'"
              :value="dictValue(field)"
              :type="field.dictType"
              :tag="field.dictType === 'col_texture'"
              clearable
              :placeholder="field.placeholder"
              :disabled="disabled || field.readonly"
              @update:value="
                field.key === 'planType' && businessType === 'COLLECTION_ACQUISITION_PLAN'
                  ? setPlanPeriod($event)
                  : setValue(field.key, $event)
              "
            />
            <NSelect
              v-else-if="field.type === 'month'"
              :value="fieldValue(field.key)"
              :options="monthOptions"
              clearable
              :placeholder="field.placeholder || '请选择计划月份'"
              :disabled="disabled || field.readonly"
              @update:value="setValue(field.key, $event)"
            />
            <NDatePicker
              v-else-if="field.type === 'year'"
              :formatted-value="fieldValue(field.key) == null ? null : String(fieldValue(field.key))"
              type="year"
              format="yyyy"
              value-format="yyyy"
              clearable
              style="width: 100%"
              :placeholder="field.placeholder || '请选择计划年度'"
              :is-date-disabled="
                (timestamp: number) => field.min != null && new Date(timestamp).getFullYear() < field.min
              "
              :disabled="disabled || field.readonly"
              @update:formatted-value="setValue(field.key, $event == null ? null : Number($event))"
            />
            <NDatePicker
              v-else-if="field.type === 'date' || field.type === 'datetime'"
              :value="fieldValue(field.key)"
              :type="field.type === 'datetime' ? 'datetime' : 'date'"
              clearable
              style="width: 100%"
              :disabled="disabled || field.readonly"
              @update:value="setValue(field.key, $event)"
            />
            <NInputNumber
              v-else-if="field.type === 'number'"
              :value="field.unit === 'yuan' ? centsToYuan(fieldValue(field.key)) : fieldValue(field.key)"
              :min="field.min"
              :max="field.unit === 'yuan' ? Math.floor(Number.MAX_SAFE_INTEGER / 100) : undefined"
              :precision="
                field.unit === 'yuan'
                  ? yuanInputPrecision(fieldValue(field.key))
                  : field.key === 'quantity'
                    ? 0
                    : undefined
              "
              style="width: 100%"
              :placeholder="field.placeholder"
              :disabled="disabled || field.readonly"
              @update:value="
                setValue(field.key, field.unit === 'yuan' ? yuanFormToCents($event, fieldValue(field.key)) : $event)
              "
            />
            <HFileList
              v-else-if="field.type === 'upload' && (disabled || field.readonly)"
              :value="fieldValue(field.key)"
              show-download-btn
            />
            <HUploadInput
              v-else-if="field.type === 'upload'"
              :value="fieldValue(field.key)"
              clearable
              :placeholder="field.placeholder"
              :disabled="disabled || field.readonly"
              @update:value="setValue(field.key, $event)"
              @upload-finish="setValue(field.key, $event.id)"
            />
            <span
              v-else-if="field.type === 'textarea' && (disabled || field.readonly)"
              style="display: block; width: 100%; white-space: pre-wrap; overflow-wrap: anywhere"
              >{{ displayValue(field) || '未填写' }}</span
            >
            <NInput
              v-else
              :value="displayValue(field)"
              :type="field.type === 'textarea' ? 'textarea' : 'text'"
              :rows="field.type === 'textarea' ? field.rows || 3 : undefined"
              :placeholder="field.placeholder"
              :disabled="disabled || field.readonly"
              @update:value="setValue(field.key, $event)"
            />
          </FlowFormItem>
        </NGi>
      </template>
    </NGrid>
  </NForm>
  <AcquisitionEvidenceView
    v-if="businessType === 'COLLECTION_ACQUISITION' && formData.dossier"
    :source="formData.source"
    :dossier="formData.dossier"
    :experts="formData.experts"
    :completion="formData.completion"
    style="margin-top: 20px"
  />
  <RestorationPlanEvidenceView
    v-if="businessType === 'COLLECTION_RESTORATION' && formData.planEvidence"
    :plan-evidence="formData.planEvidence"
    :source-snapshot="formData.sourceSnapshot"
    :administrative-evidence="formData.administrativeEvidence"
    style="margin-top: 20px"
  />
</template>

<script setup lang="ts">
import { requisitionPurposeOptions } from '../utils/requisitionPurpose';
import { computed, nextTick, ref, watch } from 'vue';
import { useFlowForm } from '../../flow/composables/useFlowForm';
import FlowFormItem from '../../flow/components/FlowFormItem.vue';
import AcquisitionEvidenceView from '../components/AcquisitionEvidenceView.vue';
import RestorationPlanEvidenceView from '@/modules/collection-restoration/components/RestorationPlanEvidenceView.vue';
import type { FlowFormExpose, FlowFormProps } from '../../flow/typings/flow';
import CollectionSelect from '../components/CollectionSelect.vue';
import type { CollectionVo, RelicLevel } from '../typings/collection';
import WarehouseTreeSelect from '../components/WarehouseTreeSelect.vue';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import AppraisalMemberEditor from '../components/AppraisalMemberEditor.vue';
import { appraisalApi } from '../api/appraisal';
import { acquisitionApi } from '../api/acquisition';
import { acquisitionPlanApi } from '../api/acquisitionPlan';
import { categoryApi } from '../api/category';
import { type as fetchDictType } from '@/modules/sys/api/dict';
import { deregistrationApi } from '../api/deregistration';
import { exhibitionApi } from '../api/exhibition';
import { loanApi } from '../api/loan';
import { collectionReturnApi } from '../api/return';
import { requisitionApi } from '../api/requisition';
import { transferApi } from '../api/transfer';
import { warehouseEntryApi } from '../api/warehouseEntry';
import { restorationApi } from '../../collection-restoration/api/restoration';
import { collectionSnapshotLabel } from '../../collection-restoration/utils/restorationCollectionSnapshot';
import RestorationOrgPicker from '../../collection-restoration/components/RestorationOrgPicker.vue';
import { centsToYuan, yuanFormToCents, yuanInputPrecision } from '../utils/moneyYuan';

type FieldType =
  | 'text'
  | 'textarea'
  | 'number'
  | 'year'
  | 'month'
  | 'date'
  | 'datetime'
  | 'dict'
  | 'upload'
  | 'user'
  | 'collection'
  | 'warehouse'
  | 'restorationOrg'
  | 'plan'
  | 'category'
  | 'acquisitionSource'
  | 'acquisitionUnit'
  | 'appraisalMembers';

type CollectionFlowField = {
  key: string;
  label: string;
  type?: FieldType;
  dictType?: string;
  span?: number;
  rows?: number;
  min?: number;
  unit?: 'yuan';
  multiple?: boolean;
  lifeStatus?: string;
  lifeStatuses?: string[];
  level?: RelicLevel;
  placeholder?: string;
  required?: boolean;
  readonly?: boolean;
  section?: string;
  allowedTypes?: string[];
};

const props = withDefaults(defineProps<FlowFormProps & { autoLoadDetail?: boolean }>(), { autoLoadDetail: true });
const emit = defineEmits<{ (e: 'update:formData', val: Record<string, any>): void }>();

const { inner, formRef, validate: validateForm, perm } = useFlowForm(props, emit);
const planOptions = ref<{ label: string; value: string }[]>([]);
const categoryOptions = ref<{ label: string; value: string }[]>([]);
const sourceOptions = ref<{ label: string; value: string }[]>([]);
const monthOptions = Array.from({ length: 12 }, (_, index) => ({ label: `${index + 1}月`, value: index + 1 }));
const acquisitionUnitOptions = computed(() => {
  const options = [
    { label: '件', value: '件' },
    { label: '套', value: '套' },
  ];
  const current = String(inner.value.unit || '').trim();
  return current && !options.some((option) => option.value === current)
    ? [...options, { label: current, value: current }]
    : options;
});
const validateForSubmit = ref(false);

const fields = computed(() => {
  const type = props.businessType?.toUpperCase?.() || '';
  const configured = fieldRegistry[type] || fallbackFields(inner.value);
  const visible =
    type === 'COLLECTION_ACQUISITION'
      ? configured
          .filter(
            (field) =>
              (inner.value.source === 'PURCHASE' ||
                !['proposedPrice', 'contractAttachmentIds', 'invoiceAttachmentIds'].includes(field.key)) &&
              (inner.value.id || !['collectionTotalRegNo', 'collectionName'].includes(field.key)),
          )
          .map((field) => {
            if (field.key === 'holder') return { ...field, label: acquisitionHolderLabel(inner.value.source) };
            if (field.key === 'quantity')
              return {
                ...field,
                label: inner.value.unit === '套' ? '整套数量' : inner.value.unit === '件' ? '件数' : '数量',
              };
            if (field.key === 'reason')
              return {
                ...field,
                label:
                  inner.value.source === 'PURCHASE'
                    ? '收购理由'
                    : inner.value.source === 'DONATE'
                      ? '受赠理由'
                      : '征集理由',
              };
            return field;
          })
      : type === 'COLLECTION_ACQUISITION_PLAN'
        ? configured.filter((field) => field.key !== 'month' || inner.value.planType === 'MONTH')
        : configured;
  if (!props.task?.businessFormNotice) return visible;
  const submitted = props.task.formData ?? props.formData;
  return visible.filter(
    (field) =>
      Object.prototype.hasOwnProperty.call(submitted, field.key) || ['EDITABLE', 'REQUIRED'].includes(perm(field.key)),
  );
});

function setPlanPeriod(period: string | null) {
  setValue('planType', period);
  if (period !== 'MONTH') setValue('month', undefined);
}

function setAcquisitionSource(source: string | null) {
  setValue('source', source);
  if (source !== 'PURCHASE') {
    for (const key of ['proposedPrice', 'contractAttachmentIds', 'invoiceAttachmentIds']) setValue(key, undefined);
  }
}

function acquisitionHolderLabel(source?: string) {
  if (source === 'PURCHASE') return '卖方';
  if (source === 'DONATE') return '捐赠人';
  if (['ALLOCATE', 'TRANSFER', 'HANDOVER', 'EXCHANGE'].includes(source || '')) return '移交方';
  return '来源人/单位';
}

const detailFetchers: Record<string, ((id: string) => Promise<any>) | undefined> = {
  COLLECTION_ACQUISITION_PLAN: acquisitionPlanApi.get,
  COLLECTION_ACQUISITION: acquisitionApi.get,
  COLLECTION_APPRAISAL: appraisalApi.get,
  COLLECTION_DEREGISTER: deregistrationApi.get,
  COLLECTION_EXHIBITION: exhibitionApi.get,
  COLLECTION_LOAN: loanApi.get,
  COLLECTION_RETURN: collectionReturnApi.get,
  COLLECTION_REQUISITION: requisitionApi.get,
  COLLECTION_TRANSFER: transferApi.get,
  COLLECTION_WAREHOUSE_ENTRY: warehouseEntryApi.get,
  COLLECTION_RESTORATION: restorationApi.get,
};

let detailLoadVersion = 0;
watch(
  () =>
    [
      props.businessType,
      props.businessKey,
      props.task?.id,
      props.task?.businessFormNotice,
      props.autoLoadDetail,
    ] as const,
  async ([businessType, businessKey, , businessFormNotice]) => {
    const version = ++detailLoadVersion;
    validateForSubmit.value = false;
    formRef.value?.restoreValidation?.();
    const type = businessType?.toUpperCase?.() || '';
    const fetcher = detailFetchers[type];
    if (!props.autoLoadDetail || !fetcher || !businessKey || businessFormNotice) return;
    const resp = await fetcher(businessKey);
    if (version !== detailLoadVersion || props.task?.businessFormNotice) return;
    const data = resp?.data?.data ?? resp?.data;
    if (data) {
      inner.value = { ...inner.value, ...data };
    }
  },
  { immediate: true },
);

watch(
  () => props.businessType?.toUpperCase?.(),
  async (type) => {
    if (type !== 'COLLECTION_ACQUISITION') return;
    const [planResp, categoryResp, sourceResp]: any[] = await Promise.all([
      acquisitionPlanApi.page({ pageSize: 0, flowStatus: 'PASS' }),
      categoryApi.list(),
      fetchDictType('col_source', true),
    ]);
    const plans = planResp?.data?.list ?? planResp?.data ?? [];
    const categories = categoryResp?.data?.list ?? categoryResp?.data ?? [];
    planOptions.value = plans.map((item: any) => ({ label: item.title, value: item.id }));
    categoryOptions.value = categories.map((item: any) => ({ label: item.name, value: item.id }));
    sourceOptions.value = (sourceResp?.data ?? [])
      .filter((item: any) => item.value !== 'DESIGNATED')
      .map((item: any) => ({ label: String(item.label ?? item.name ?? item.value), value: String(item.value) }));
  },
  { immediate: true },
);

function fieldRule(field: CollectionFlowField) {
  if (props.task?.businessFormNotice && !['EDITABLE', 'REQUIRED'].includes(perm(field.key))) return undefined;
  if (field.type === 'month') {
    return {
      validator: () =>
        Number.isInteger(fieldValue(field.key)) && fieldValue(field.key) >= 1 && fieldValue(field.key) <= 12,
      message: '请选择 1—12 月的计划月份',
      trigger: ['change', 'blur'],
    };
  }
  if (props.businessType === 'COLLECTION_ACQUISITION' && !validateForSubmit.value && field.key !== 'name')
    return undefined;
  if (!field.required && !(validateForSubmit.value && isSubmitRequired(field))) return undefined;
  if (field.type === 'number' || field.type === 'year' || field.type === 'date' || field.type === 'datetime') {
    return {
      required: true,
      type: 'number',
      message: requiredMessage(field),
      trigger: ['change', 'blur'],
    };
  }
  if (field.multiple && ['collection', 'user', 'warehouse'].includes(field.type || '')) {
    return {
      validator: () => splitIds(fieldValue(field.key)).length > 0,
      message: requiredMessage(field),
      trigger: ['change', 'blur'],
    };
  }
  if (field.type === 'appraisalMembers') {
    return {
      validator: () => {
        const members = fieldValue(field.key);
        return (
          Array.isArray(members) && members.length > 0 && members.some((member) => member?.userId || member?.memberName)
        );
      },
      message: '请添加鉴定组成员',
      trigger: ['change', 'blur'],
    };
  }
  return { required: true, message: requiredMessage(field), trigger: ['blur', 'change'] };
}

function requiredMessage(field: CollectionFlowField) {
  if (field.placeholder) return field.placeholder;
  if (field.type === 'upload') return `请上传${field.label}`;
  const selectTypes: FieldType[] = [
    'collection',
    'user',
    'warehouse',
    'dict',
    'acquisitionSource',
    'acquisitionUnit',
    'year',
    'month',
    'date',
    'datetime',
    'restorationOrg',
    'plan',
    'category',
  ];
  return `${selectTypes.includes(field.type || 'text') ? '请选择' : '请输入'}${field.label}`;
}

function isSubmitRequired(field: CollectionFlowField) {
  if (props.task?.businessFormNotice && !['EDITABLE', 'REQUIRED'].includes(perm(field.key))) return false;
  const type = props.businessType?.toUpperCase?.();
  if (type === 'COLLECTION_ACQUISITION') {
    if (['expertOpinionAttachmentIds', 'approvalAttachmentIds'].includes(field.key)) return true;
    return inner.value.source === 'PURCHASE' && ['contractAttachmentIds', 'invoiceAttachmentIds'].includes(field.key);
  }
  if (type !== 'COLLECTION_RETURN') return false;
  if (
    [
      'operator',
      'reviewer',
      'custodian',
      'handoverUser',
      'returnDate',
      'toWarehouseId',
      'reviewOpinion',
      'signedVoucherAttachmentIds',
    ].includes(field.key)
  ) {
    return true;
  }
  return field.key === 'damagePhotoIds' && !!String(inner.value.damageNote || '').trim();
}

async function validate(options?: { submit?: boolean }) {
  validateForSubmit.value = !!options?.submit;
  await nextTick();
  if (!validateForSubmit.value) formRef.value?.restoreValidation?.();
  return validateForm();
}

function collectionValue(field: CollectionFlowField) {
  return field.multiple ? splitIds(fieldValue(field.key)) : fieldValue(field.key);
}

function setCollectionValue(field: CollectionFlowField, value: string | string[] | null) {
  if (field.multiple) {
    setValue(field.key, Array.isArray(value) && value.length ? value.join(',') : undefined);
    return;
  }
  setValue(field.key, typeof value === 'string' && value ? value : undefined);
}

/** 审批表单等场景：用项目 / 方案快照上的总账号+名称回退展示藏品，避免无查询权限时显示裸 id。 */
function collectionFallbackLabel(field: CollectionFlowField) {
  if (field.multiple) return undefined;
  if (field.key !== 'collectionId') return undefined;
  const snap = inner.value.sourceSnapshot;
  return (
    collectionSnapshotLabel(inner.value)
    || collectionSnapshotLabel(snap && typeof snap === 'object' ? snap : null)
    || undefined
  );
}

function onCollectionSelected(field: CollectionFlowField, row: CollectionVo | null) {
  if (props.businessType?.toUpperCase?.() !== 'COLLECTION_APPRAISAL' || field.key !== 'collectionId' || !row) return;
  setValue('resultName', row.name?.trim() || undefined);
  setValue('resultEra', row.era?.trim() || undefined);
  setValue('resultTexture', row.texture?.trim() || undefined);
}

function selectValue(field: CollectionFlowField) {
  return field.multiple ? splitIds(fieldValue(field.key)) : fieldValue(field.key);
}

function setSelectValue(field: CollectionFlowField, value: string | string[] | null) {
  if (field.multiple) {
    setValue(field.key, Array.isArray(value) && value.length ? value.join(',') : undefined);
    return;
  }
  setValue(field.key, typeof value === 'string' && value ? value : undefined);
}

function setValue(key: string, value: any) {
  const parts = key.split('.');
  const leaf = parts.pop()!;
  let target = inner.value;
  parts.forEach((part) => {
    if (!target[part] || typeof target[part] !== 'object') target[part] = {};
    target = target[part];
  });
  target[leaf] = value ?? undefined;
}

function setOptionValue(
  key: string,
  labelKey: string,
  value: string | null,
  options: { label: string; value: string }[],
) {
  setValue(key, value);
  setValue(labelKey, options.find((option) => option.value === value)?.label);
}

function fieldValue(key: string) {
  return key.split('.').reduce<any>((value, part) => value?.[part], inner.value);
}

function displayValue(field: CollectionFlowField) {
  const value = fieldValue(field.key);
  if (value === undefined || value === null) return '';
  return String(value);
}

function dictValue(field: CollectionFlowField) {
  const value = fieldValue(field.key);
  if (value === undefined || value === null) return undefined;
  return String(value);
}

function splitIds(value?: string | string[] | null) {
  if (Array.isArray(value)) return value;
  return (value || '')
    .split(/[,，;\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function fallbackFields(data: Record<string, any>): CollectionFlowField[] {
  return Object.keys(data || {}).map((key) => ({ key, label: key, type: 'text' }));
}

const fieldRegistry: Record<string, CollectionFlowField[]> = {
  COLLECTION_WAREHOUSE_ENTRY: [
    { key: 'applicationNo', label: '申请编号', readonly: true },
    { key: 'applicantName', label: '申请人', readonly: true },
    {
      key: 'entrantIds',
      label: '入库人员',
      type: 'user',
      multiple: true,
      required: true,
      placeholder: '选择一名或多名入库人员',
      span: 2,
    },
    { key: 'externalEntrants', label: '外部人员', type: 'textarea', placeholder: '姓名、单位及证件信息', span: 2 },
    {
      key: 'warehouseIds',
      label: '拟进入库房',
      type: 'warehouse',
      multiple: true,
      required: true,
      placeholder: '选择一个或多个库房',
      allowedTypes: ['WAREHOUSE', 'AREA'],
      span: 2,
    },
    { key: 'purpose', label: '入库事由', type: 'textarea', required: true, span: 2 },
    { key: 'plannedStart', label: '计划进入时间', type: 'datetime', required: true },
    { key: 'plannedEnd', label: '计划离开时间', type: 'datetime', required: true },
    { key: 'escortUserId', label: '陪同人员', type: 'user' },
    { key: 'attachmentIds', label: '申请材料', type: 'upload', span: 2 },
    { key: 'approvedAt', label: '审批通过时间', readonly: true },
    { key: 'actualEntryAt', label: '实际进入时间', readonly: true },
    { key: 'actualExitAt', label: '实际离开时间', readonly: true },
    { key: 'accessRemark', label: '门禁核验备注', type: 'textarea', readonly: true, span: 2 },
  ],
  COLLECTION_ACQUISITION: [
    { key: 'source', label: '征集方式', type: 'acquisitionSource', required: true, section: '征集方式与申请' },
    { key: 'planId', label: '所属征集计划', type: 'plan', required: true },
    { key: 'name', label: '拟征集名称', required: true, span: 2, section: '征集对象' },
    { key: 'unit', label: '藏品计量单位', type: 'acquisitionUnit', required: true },
    { key: 'quantity', label: '件数', type: 'number', min: 1, required: true },
    { key: 'categoryId', label: '文物分类', type: 'category' },
    { key: 'era', label: '年代' },
    { key: 'texture', label: '质地', type: 'dict', dictType: 'col_texture' },
    { key: 'proposedLevel', label: '参考级别（可选）', type: 'dict', dictType: 'col_relic_level' },
    { key: 'dimensions.height', label: '通高' },
    { key: 'dimensions.mouth', label: '口径' },
    { key: 'dimensions.bottom', label: '底径' },
    { key: 'dimensions.belly', label: '腹径' },
    { key: 'dimensions.length', label: '长度' },
    { key: 'dimensions.width', label: '宽度' },
    { key: 'dimensions.diameter', label: '直径' },
    { key: 'weight', label: '质量/重量' },
    { key: 'completeness', label: '完残程度', type: 'dict', dictType: 'col_completeness', required: true },
    { key: 'completeStatus', label: '完残状况', type: 'textarea', span: 2, required: true },
    { key: 'holder', label: '来源人/单位', required: true, section: '来源与取得' },
    { key: 'acquireDate', label: '实际取得日期', type: 'date', required: true },
    { key: 'reason', label: '征集理由', type: 'textarea', required: true, span: 2 },
    { key: 'proposedPrice', label: '拟收购价（元）', type: 'number', min: 0, unit: 'yuan' },
    { key: 'appraisedValue', label: '评估价（元）', type: 'number', min: 0, unit: 'yuan' },
    { key: 'images', label: '藏品影像', type: 'upload', span: 2, section: '影像与办理材料' },
    { key: 'expertOpinionAttachmentIds', label: '专家论证材料', type: 'upload', span: 2 },
    { key: 'approvalAttachmentIds', label: '审批材料', type: 'upload', span: 2 },
    { key: 'contractAttachmentIds', label: '收购合同', type: 'upload', span: 2 },
    { key: 'invoiceAttachmentIds', label: '发票/支付材料', type: 'upload', span: 2 },
    { key: 'collectionTotalRegNo', label: '总登记号', readonly: true, section: '入藏结果' },
    { key: 'collectionName', label: '登账藏品', readonly: true },
  ],
  COLLECTION_REQUISITION: [
    { key: 'documentNo', label: '提用单号', readonly: true },
    {
      key: 'collectionIds',
      label: '提用藏品',
      type: 'collection',
      multiple: true,
      lifeStatus: 'IN_STORE',
      required: true,
      span: 2,
    },
    { key: 'purpose', label: '提用事由', type: 'dict', dictType: 'col_move_reason', required: true },
    { key: 'basis', label: '依据', required: true },
    { key: 'department', label: '使用部门', required: true },
    { key: 'operator', label: '经办人', type: 'user', required: true },
    { key: 'reviewer', label: '复核人', type: 'user' },
    { key: 'custodian', label: '保管员', type: 'user' },
    { key: 'handoverUser', label: '交接人', type: 'user' },
    { key: 'expectReturnDate', label: '预计归还', type: 'date' },
    { key: 'toWarehouseId', label: '回库库位', type: 'warehouse' },
    { key: 'reviewOpinion', label: '复核意见', type: 'textarea', span: 2 },
    { key: 'attachmentIds', label: '附件', type: 'upload', span: 2 },
    { key: 'returnDate', label: '归还日期', type: 'date', readonly: true },
    { key: 'returned', label: '归还状态', type: 'dict', dictType: 'col_returned', readonly: true },
  ],
  COLLECTION_DEREGISTER: [
    { key: 'collectionId', label: '藏品', type: 'collection', lifeStatus: 'IN_STORE', required: true, span: 2 },
    { key: 'collectionTotalRegNo', label: '总登记号', readonly: true },
    { key: 'collectionName', label: '藏品名称', readonly: true },
    { key: 'reason', label: '退出馆藏原因', type: 'dict', dictType: 'col_dereg_reason', required: true },
    { key: 'approveLevel', label: '审批层级', type: 'dict', dictType: 'col_approve_level' },
    { key: 'approvalDocNo', label: '批文号' },
    { key: 'expertOpinion', label: '专家意见', type: 'textarea', required: true, span: 2 },
    { key: 'destination', label: '退出馆藏后去向', type: 'textarea', required: true, span: 2 },
    { key: 'approvedBy', label: '最终批准人', readonly: true },
    { key: 'detail', label: '退出馆藏说明', type: 'textarea', required: true, span: 2 },
    { key: 'attachmentIds', label: '附件', type: 'upload', span: 2 },
  ],
  COLLECTION_APPRAISAL: [
    {
      key: 'collectionId',
      label: '未定级藏品',
      type: 'collection',
      level: 'UNRATED',
      lifeStatuses: ['IN_STORE', 'PENDING'],
      placeholder: '按登记号或名称检索在库或已核收待登记的未定级藏品',
      required: true,
      span: 2,
    },
    { key: 'resultName', label: '定名' },
    { key: 'resultEra', label: '年代' },
    { key: 'resultTexture', label: '质地', type: 'dict', dictType: 'col_texture' },
    { key: 'resultLevel', label: '建议级别（可选）', type: 'dict', dictType: 'col_relic_level' },
    { key: 'members', label: '鉴定组成员', type: 'appraisalMembers', required: true, span: 2 },
    { key: 'appraisedAt', label: '鉴定日期', type: 'date' },
    { key: 'conclusion', label: '鉴定结论', type: 'textarea', required: true, span: 2 },
    { key: 'rejectReason', label: '驳回原因', type: 'textarea', span: 2 },
    { key: 'attachmentIds', label: '附件', type: 'upload', span: 2 },
  ],
  COLLECTION_RESTORATION: [
    { key: 'collectionId', label: '藏品', type: 'collection', lifeStatus: 'IN_STORE', required: true, span: 2 },
    { key: 'restoreOrgId', label: '修复机构', type: 'restorationOrg', required: true },
    { key: 'restoreOrg', label: '机构名称快照', readonly: true },
    { key: 'operator', label: '经办人', type: 'user', required: true },
    { key: 'reviewer', label: '复核人', type: 'user' },
    { key: 'startDate', label: '开始日期', type: 'date' },
    { key: 'endDate', label: '完成日期', type: 'date' },
    { key: 'state', label: '修复状态', type: 'dict', dictType: 'col_process_state' },
    { key: 'beforeImages', label: '修前影像', type: 'upload', span: 2 },
    { key: 'afterImages', label: '修后影像', type: 'upload', span: 2 },
    { key: 'plan', label: '修复方案', type: 'textarea', required: true, span: 2 },
    { key: 'process', label: '修复过程', type: 'textarea', span: 2 },
    { key: 'triggerSource', label: '项目来源', type: 'dict', dictType: 'restoration_trigger_source' },
    { key: 'urgencyLevel', label: '紧急度', type: 'dict', dictType: 'restoration_urgency' },
    { key: 'restoreOrgQualification', label: '机构资质快照', readonly: true, span: 2 },
    { key: 'contractFileIds', label: '合同附件', type: 'upload', span: 2 },
    { key: 'testReportFileIds', label: '检测报告', type: 'upload', span: 2 },
    { key: 'planFileIds', label: '方案附件', type: 'upload', span: 2 },
    { key: 'targetEnvironment', label: '回库目标环境', type: 'textarea', span: 2 },
  ],
  COLLECTION_LOAN: [
    { key: 'title', label: '展览/事由', required: true, span: 2 },
    {
      key: 'collectionIds',
      label: '借出藏品',
      type: 'collection',
      multiple: true,
      lifeStatus: 'IN_STORE',
      required: true,
      span: 2,
    },
    { key: 'borrower', label: '借入方', required: true },
    { key: 'protocolNo', label: '协议号' },
    { key: 'approveLevel', label: '审批层级', type: 'dict', dictType: 'col_approve_level' },
    { key: 'approvalDocNo', label: '出馆批文号' },
    { key: 'insurance', label: '保险' },
    { key: 'operator', label: '经办人', type: 'user' },
    { key: 'reviewer', label: '复核人', type: 'user' },
    { key: 'loanDate', label: '借出日期', type: 'date', required: true },
    { key: 'returnDate', label: '返还日期', type: 'date' },
    { key: 'attachmentIds', label: '附件', type: 'upload', span: 2 },
    { key: 'returned', label: '返还状态', type: 'dict', dictType: 'col_returned', readonly: true },
  ],
  COLLECTION_RETURN: [
    { key: 'sourceDocumentNo', label: '来源单号', readonly: true },
    // 部分归库：可在来源单未归明细内圈定本次归还子集（服务端校验子集合法性）
    {
      key: 'collectionIds',
      label: '本次归还藏品',
      type: 'collection',
      multiple: true,
      required: true,
      span: 2,
      placeholder: '默认全部未归藏品，可删减为本次归还子集',
    },
    { key: 'reason', label: '归还事由', type: 'dict', dictType: 'col_move_reason', readonly: true },
    { key: 'basis', label: '依据' },
    { key: 'department', label: '归还部门/单位' },
    { key: 'operator', label: '经办人', type: 'user' },
    { key: 'reviewer', label: '复核人', type: 'user' },
    { key: 'custodian', label: '保管员', type: 'user' },
    { key: 'handoverUser', label: '交接人', type: 'user' },
    { key: 'expectedReturnDate', label: '预计归还', type: 'date', readonly: true },
    { key: 'returnDate', label: '实际归还', type: 'date' },
    { key: 'toWarehouseId', label: '回库库位', type: 'warehouse' },
    {
      key: 'damageNote',
      label: '损伤确认记录',
      type: 'textarea',
      span: 2,
      placeholder: '提用/借展期间如有损伤，双方当面确认后录入；审批流程即报送领导',
    },
    { key: 'damagePhotoIds', label: '损伤照片', type: 'upload', span: 2, placeholder: '有损伤记录时必传' },
    { key: 'reviewOpinion', label: '验收意见', type: 'textarea', span: 2 },
    { key: 'attachmentIds', label: '验收附件', type: 'upload', span: 2 },
    { key: 'signedVoucherAttachmentIds', label: '双方签署归还凭证', type: 'upload', span: 2 },
  ],
  COLLECTION_REPRODUCTION: [
    { key: 'collectionId', label: '藏品', type: 'collection', lifeStatus: 'IN_STORE', required: true, span: 2 },
    { key: 'repType', label: '类型', type: 'dict', dictType: 'col_rep_type', required: true },
    { key: 'repDate', label: '日期', type: 'date', required: true },
    { key: 'purpose', label: '用途', type: 'textarea', required: true, span: 2 },
    { key: 'images', label: '成果影像/附件', type: 'upload', span: 2 },
  ],
  COLLECTION_INVENTORY: [
    { key: 'batchName', label: '盘点批次', required: true },
    { key: 'collectionId', label: '藏品', type: 'collection', span: 2 },
    { key: 'result', label: '盘点结果', type: 'dict', dictType: 'col_inventory_result', required: true },
    { key: 'checkedAt', label: '盘点时间', type: 'date' },
    { key: 'note', label: '说明', type: 'textarea', span: 2 },
    { key: 'attachmentIds', label: '附件', type: 'upload', span: 2 },
  ],
  COLLECTION_TRANSFER: [
    { key: 'collectionId', label: '藏品', type: 'collection', lifeStatus: 'IN_STORE', required: true, span: 2 },
    { key: 'fromOrgId', label: '调出单位', readonly: true },
    { key: 'toOrgId', label: '调入单位', required: true },
    { key: 'approvalDocNo', label: '批文号' },
    { key: 'reason', label: '调拨原因', type: 'textarea', required: true, span: 2 },
    { key: 'attachmentIds', label: '附件', type: 'upload', span: 2 },
  ],
  COLLECTION_ACQUISITION_PLAN: [
    { key: 'title', label: '计划名称', required: true, span: 2 },
    { key: 'planType', label: '计划周期', type: 'dict', dictType: 'col_plan_period', required: true },
    { key: 'year', label: '计划年度', type: 'year', min: 2000, required: true },
    { key: 'month', label: '计划月份', type: 'month', required: true },
    { key: 'budget', label: '计划预算（元）', type: 'number', min: 0, unit: 'yuan' },
    { key: 'content', label: '计划说明/征集方向', type: 'textarea', rows: 5, span: 2 },
    { key: 'attachmentIds', label: '附件', type: 'upload', span: 2 },
  ],
};

defineExpose<FlowFormExpose>({ validate });
</script>

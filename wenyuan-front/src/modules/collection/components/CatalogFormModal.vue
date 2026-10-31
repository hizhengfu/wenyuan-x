<template>
  <HPageForm
    ref="pageFormRef"
    v-model:show="visible"
    :title="proposalMode ? '编辑拟变更内容' : historical ? '迁入藏品编目补录' : '编目录入'"
    :model="form"
    :rules="rules"
    :label-placement="formColumns === 1 ? 'top' : 'left'"
    label-width="112"
    require-mark-placement="right-hanging"
  >
    <template #form-action>
      <NButton type="primary" secondary :loading="saving" :disabled="busy" style="margin-right: 8px" @click="saveDraft">
        {{ proposalMode ? '使用这些变更' : '保存草稿' }}
      </NButton>
      <NButton v-if="!proposalMode" type="primary" secondary :loading="submitting" :disabled="busy || catalog?.migrationVerificationRequired" style="margin-right: 8px" @click="saveAndSubmit">
        保存并提交
      </NButton>
    </template>
    <NAlert type="info" :show-icon="false" class="form-hint">
      <template v-if="catalog?.migrationVerificationRequired">历史迁入的登记依据待核实。可保存草稿；请先到总登记账详情的“旧系统迁移”补充材料并完成独立复核，再提交编目。</template>
      <template v-else-if="historical">原登账事实已核实，本次仅补充编目资料，不重新登账。</template>
      <template v-else>草稿可分次补充；提交时按藏品性质和是否成套核对必填项。</template>
    </NAlert>
    <fieldset ref="formBody" class="catalog-form-body" :disabled="busy" style="border: 0; margin: 0; padding: 0;">
      <NTabs type="line" animated size="small">
        <NTabPane name="identity" tab="标识定名">
          <NGrid :cols="formColumns" :x-gap="16">
            <NFormItemGi label="总登记号"><template #label><CollectionFieldLabel label="总登记号" :help="historical ? '保留原登记号、登记日期及原账事实。新增著录字段可补充；正式信息订正须通过信息变更办理。' : '编号来自藏品台账，更正须走留痕更正。'" /></template><NInput :value="catalog?.totalRegNo" disabled /></NFormItemGi>
            <NFormItemGi label="登记号分号"><NInput v-model:value="form.registrationSubNo" placeholder="登记号分号" /></NFormItemGi>
            <NFormItemGi label="分类号" path="classificationNo"><NInput v-model:value="form.classificationNo" placeholder="分类号" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="原藏号"><NInput v-model:value="form.originalNo" placeholder="原藏号 / 辅助号" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="总品名" path="totalName"><NInput v-model:value="form.totalName" placeholder="成套或整批藏品的总品名" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="分件名称"><NInput v-model:value="form.componentName" placeholder="分件名称" /></NFormItemGi>
            <NFormItemGi label="藏品性质" :span="formColumns" path="nature"><template #label><CollectionFieldLabel label="藏品性质" :help="collectionNatureHint" /></template><NSelect :value="form.nature" :options="collectionNatureOptions" :disabled="historical" placeholder="请核实藏品性质" @update:value="changeNature" /></NFormItemGi>
            <NFormItemGi label="正式定名" path="formalName"><NInput v-model:value="form.formalName" placeholder="填写正式定名" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="原名"><NInput v-model:value="form.originalName" placeholder="原有名称" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="藏品分类" path="categoryId">
              <NSelect v-model:value="form.categoryId" :options="categoryOptions" filterable clearable placeholder="选择分类" :disabled="historical" />
            </NFormItemGi>
            <NFormItemGi label="综合分类"><NInput v-model:value="form.comprehensiveCategory" placeholder="综合分类" /></NFormItemGi>
            <NFormItemGi label="专题分类" :span="formColumns">
              <NDynamicTags v-model:value="form.topicCategories" />
            </NFormItemGi>
          </NGrid>
        </NTabPane>

        <NTabPane name="era" tab="年代质地">
          <NGrid :cols="formColumns" :x-gap="16">
            <NGi :span="formColumns"><CollectionDescriptionFields v-model="form" :cols="formColumns" :historical="historical" /></NGi>
            <NFormItemGi label="地域"><NInput v-model:value="form.region" placeholder="出土 / 流传地域" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="国别"><NInput v-model:value="form.country" placeholder="国别" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="人文类型"><NInput v-model:value="form.culture" placeholder="民族 / 人文类型" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="文化类型"><NInput v-model:value="form.cultureType" placeholder="文化类型" /></NFormItemGi>
            <NFormItemGi label="色泽"><NInput v-model:value="form.color" placeholder="色泽" :disabled="historical" /></NFormItemGi>
          </NGrid>
        </NTabPane>

        <NTabPane name="quantity" tab="鉴定数量">
          <NGrid :cols="formColumns" :x-gap="16">
            <NFormItemGi label="文物等级" path="level"><NText v-if="form.nature === 'OTHER'" depth="3">不适用文物等级</NText><HDictSelect v-else v-model:value="form.level" type="col_relic_level" :disabled="historical || !form.nature" placeholder="先核实藏品性质" clearable /></NFormItemGi>
            <NFormItemGi label="登记件套数" path="quantity"><NInputNumber v-model:value="form.quantity" :min="1" :precision="0" :disabled="historical" /></NFormItemGi><NFormItemGi label="登记数量单位" path="unit"><NInput v-model:value="form.unit" :disabled="historical" placeholder="件 / 套" /></NFormItemGi>
            <NFormItemGi label="实际数量" path="actualQuantity"><template #label><CollectionFieldLabel label="实际数量" help="成套或多分件藏品提交编目时须填写实际件数和单位；单件可沿用登记数量，已核实历史迁入不强制补填未知数量。" /></template><NInputNumber v-model:value="form.actualQuantity" :min="1" :precision="0" /></NFormItemGi>
            <NFormItemGi label="实际数量单位" path="actualQuantityUnit"><NInput v-model:value="form.actualQuantityUnit" placeholder="如 件、只、册" /></NFormItemGi>
            <NFormItemGi label="完残程度" path="completeness"><HDictSelect v-model:value="form.completeness" type="col_completeness" clearable :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="保存状态" path="preserveState"><HDictSelect v-model:value="form.preserveState" type="col_preserve" clearable :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="鉴定意见" :span="formColumns"><NInput v-model:value="form.appraisalOpinion" type="textarea" :rows="2" /></NFormItemGi>
            <NFormItemGi label="尺寸" path="dimensions" :span="formColumns">
              <div class="dimension-grid" :style="formColumns === 1 ? { gridTemplateColumns: 'repeat(2, minmax(0, 1fr))' } : undefined">
                <NInput v-model:value="dimensions.height" placeholder="通高" :disabled="historical" />
                <NInput v-model:value="dimensions.mouth" placeholder="口径" :disabled="historical" />
                <NInput v-model:value="dimensions.bottom" placeholder="底径" :disabled="historical" />
                <NInput v-model:value="dimensions.belly" placeholder="腹径" :disabled="historical" />
                <NInput v-model:value="dimensions.length" placeholder="长" :disabled="historical" />
                <NInput v-model:value="dimensions.width" placeholder="宽" :disabled="historical" />
                <NInput v-model:value="dimensions.diameter" placeholder="直径" :disabled="historical" />
                <NInput v-model:value="form.dimensionUnit" placeholder="尺寸单位，如 cm" />
              </div>
            </NFormItemGi>
            <NFormItemGi label="质量范围"><NInput v-model:value="form.weightRange" placeholder="如 0.1-1 kg" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="具体质量"><NSpace :wrap="false"><NInput v-model:value="form.weight" placeholder="数值" :disabled="historical" /><NInput v-model:value="form.weightUnit" placeholder="单位" :disabled="historical" /></NSpace></NFormItemGi>
            <NFormItemGi label="容积"><NSpace :wrap="false"><NInput v-model:value="form.volume" placeholder="数值" :disabled="historical" /><NInput v-model:value="form.volumeUnit" placeholder="单位" :disabled="historical" /></NSpace></NFormItemGi>
            <NFormItemGi label="完残状况" path="completeStatus" :span="formColumns"><NInput v-model:value="form.completeStatus" type="textarea" :rows="2" :disabled="historical" /></NFormItemGi>
          </NGrid>
        </NTabPane>

        <NTabPane name="source" tab="来源入藏">
          <NGrid :cols="formColumns" :x-gap="16">
            <NFormItemGi label="来源方式" path="source"><HDictSelect v-model:value="form.source" type="col_source" clearable :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="来源号"><NInput v-model:value="form.sourceNo" placeholder="来源号" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="取得日期" path="acquireDate"><NDatePicker v-model:value="form.acquireDate" type="date" clearable style="width: 100%" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="取得时间范围"><NInput v-model:value="form.acquireDateRange" placeholder="不能确定日期时填写范围" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="取得年度"><NInput v-model:value="form.acquireYear" placeholder="如 2009" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="当前库位" path="warehouseId"><WarehouseTreeSelect :disabled="historical || proposalMode" v-model:value="form.warehouseId" /></NFormItemGi>
            <NFormItemGi label="实收入馆日期" path="entryDate"><template #label><CollectionFieldLabel label="实收入馆日期" help="新入藏记录从入藏核收单同步实收入馆日期与核收完成时间；请先完善核收记录。已核实历史迁入按原材料保留，不要求重新核收。" /></template><NDatePicker v-model:value="form.entryDate" type="date" clearable :disabled="historical || !proposalMode" /></NFormItemGi>
            <NFormItemGi label="核收完成时间"><NDatePicker v-model:value="form.receiptCompletedAt" type="datetime" clearable :disabled="historical || !proposalMode" /></NFormItemGi>
            <NFormItemGi label="来源说明" :span="formColumns"><NInput v-model:value="form.sourceDesc" type="textarea" :rows="3" :disabled="historical" /></NFormItemGi>
          </NGrid>
        </NTabPane>

        <NTabPane name="description" tab="描述研究">
          <NGrid :cols="formColumns" :x-gap="16">
            <NFormItemGi label="基本特征" path="feature" :span="formColumns"><NInput v-model:value="form.feature" type="textarea" :rows="3" placeholder="器形、纹饰、工艺和识别特征" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="铭文款识" :span="formColumns"><NInput v-model:value="form.inscription" type="textarea" :rows="2" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="题跋" :span="formColumns"><NInput v-model:value="form.colophon" type="textarea" :rows="2" /></NFormItemGi>
            <NFormItemGi label="著录文献" :span="formColumns"><NInput v-model:value="form.bibliography" type="textarea" :rows="2" /></NFormItemGi>
            <NFormItemGi label="研究说明" :span="formColumns"><NInput v-model:value="form.researchNote" type="textarea" :rows="3" /></NFormItemGi>
            <NFormItemGi label="著者"><NInput v-model:value="form.author" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="版本"><NInput v-model:value="form.edition" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="存卷"><NInput v-model:value="form.volumeNo" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="估价（元）"><NInputNumber :value="centsToYuan(form.valuation)" @update:value="form.valuation = yuanFormToCents($event, form.valuation)" :min="0" :max="Math.floor(Number.MAX_SAFE_INTEGER / 100)" :precision="yuanInputPrecision(form.valuation)" style="width: 100%" :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="著作权人"><NInput v-model:value="form.copyrightOwner" /></NFormItemGi>
            <NFormItemGi label="权利限制"><NInput v-model:value="form.rightsRestrictions" /></NFormItemGi>
            <NFormItemGi label="封面影像" path="coverImage" :span="formColumns"><HUploadInput v-model:value="form.coverImage" @upload-finish="coverUploadHandler" clearable :disabled="historical" /></NFormItemGi>
            <NFormItemGi label="附件说明" :span="formColumns"><NInput v-model:value="form.attachmentDescription" type="textarea" :rows="2" /></NFormItemGi>
            <NFormItemGi label="备注" :span="formColumns"><NInput v-model:value="form.remark" type="textarea" :rows="2" :disabled="historical" /></NFormItemGi>
          </NGrid>
        </NTabPane>

        <NTabPane v-if="!historical" name="registration" tab="测量与流传著录">
          <RegistrationDetailsEditor v-if="form.registrationDetails" v-model="form.registrationDetails" :cols="formColumns" :nature="form.nature" />
        </NTabPane>
        <NTabPane name="accessory" tab="附属物件">
          <div v-for="(item, index) in form.accessories" :key="index" class="accessory-row">
            <NInput v-model:value="item.kind" placeholder="类型" />
            <NInput v-model:value="item.name" placeholder="名称" />
            <NInput v-model:value="item.condition" placeholder="状况" />
            <NInputNumber v-model:value="item.quantity" :min="1" placeholder="数量" />
            <NInput v-model:value="item.unit" placeholder="单位" />
            <NInput v-model:value="item.remark" placeholder="备注" />
            <NButton type="error" text @click="removeAccessory(index)">删除</NButton>
          </div>
          <NButton type="primary" dashed block @click="addAccessory">新增附属物</NButton>
        </NTabPane>
      </NTabs>
    </fieldset>
  </HPageForm>
</template>

<script setup lang="ts">
import CollectionFieldLabel from './CollectionFieldLabel.vue';
import { useElementSize } from '@vueuse/core';
import CollectionDescriptionFields from './CollectionDescriptionFields.vue';
import RegistrationDetailsEditor from './RegistrationDetailsEditor.vue';
import { collectionNatureOptions, collectionNatureHint } from '../utils/collectionNature';
import { computed, reactive, ref, watch } from 'vue';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { catalogResult, readCatalog } from '../utils/catalogResponses';
import { cloneDeep, isEqual } from 'lodash-es';
import { useMessage } from 'naive-ui';
import { catalogApi } from '../api/catalog';
import { categoryApi } from '../api/category';
import WarehouseTreeSelect from './WarehouseTreeSelect.vue';
import type { CollectionCatalogContentVo, CollectionCatalogVo } from '../typings/collection';
import { centsToYuan, yuanFormToCents, yuanInputPrecision } from '../utils/moneyYuan';
import { catalogEntryRules } from '../utils/collectionEntryRules';

const props = defineProps<{ proposalMode?: boolean }>();
const emit = defineEmits<{ (e: 'saved'): void; (e: 'proposal', value: CollectionCatalogContentVo): void }>();
const message = useMessage();
const visible = ref(false);
const formBody = ref<HTMLElement>();
const { width: formWidth } = useElementSize(formBody);
const formColumns = computed(() => formWidth.value >= 640 ? 2 : 1);
const pageFormRef = ref<any>(null);

const catalog = ref<CollectionCatalogVo | null>(null);
const defaultContent: CollectionCatalogContentVo = {
  quantity: 1,
  unit: '件',
  registrationDetails: {},
  topicCategories: [],
  accessories: [],
};
const form = ref<CollectionCatalogContentVo>(cloneDeep(defaultContent));
const dimensions = reactive<Record<string, string | undefined>>({});
const categoryOptions = ref<{ label: string; value: string }[]>([]);
const historical = computed(() => !props.proposalMode && catalog.value?.historicalMigration === true);
const rules = computed(() => catalogEntryRules(form.value, historical.value));
function changeNature(value: CollectionCatalogContentVo['nature']) {
  form.value.nature = value;
  if (value === 'OTHER') form.value.level = 'UNRATED';
}


let windowId = 0;
const coverUploadHandler = ref<(file: { id?: string }) => void>(() => {});
const session = useCatalogOperation(clear);
const busy = session.busy;
const saving = computed(() => session.operation.value === 'save');
const submitting = computed(() => session.operation.value === 'submit');
function clear() {
  windowId++; visible.value = false; catalog.value = null; form.value = cloneDeep(defaultContent);
  Object.keys(dimensions).forEach(key => delete dimensions[key]); categoryOptions.value = []; coverUploadHandler.value = () => {};
}
watch(visible, show => { if (!show) session.reset(); }, { flush: 'sync' });
watch(() => props.proposalMode, () => session.reset());
function setContent(content: CollectionCatalogContentVo) {
  form.value = { ...cloneDeep(defaultContent), ...cloneDeep(content) };
  form.value.topicCategories ||= []; form.value.accessories ||= []; form.value.registrationDetails ||= {};
  Object.keys(dimensions).forEach(key => delete dimensions[key]);
  Object.assign(dimensions, cloneDeep(content.dimensions || {}));
}
async function open(row: CollectionCatalogVo) {
  if (!session.active.value) return;
  session.reset();
  if (!row.collectionId || !row.content || (!props.proposalMode && (!row.canEdit || !row.stateHash))) {
    message.error('当前编目不能编辑，请重新读取并核对资格'); return;
  }
  try { catalog.value = props.proposalMode ? cloneDeep(row) : readCatalog({ data: row }, row.collectionId); }
  catch (error: any) { message.error(error.message); return; }
  setContent(catalog.value.content); visible.value = true;
  const currentWindow = windowId;
  coverUploadHandler.value = file => {
    if (session.active.value && visible.value && windowId === currentWindow && !busy.value && typeof file.id === 'string') {
      form.value.coverImage = file.id;
      form.value.registrationDetails = { ...form.value.registrationDetails, coverIsFrontView: undefined };
    }
  };
  const ticket = session.begin('load'); if (ticket === null) return;
  try {
    const response: any = await categoryApi.list();
    if (!session.current(ticket) || !visible.value) return;
    const rows = response?.data?.list ?? response?.data;
    if (!Array.isArray(rows) || rows.some(row => !row || typeof row.name !== 'string' || typeof row.id !== 'string')) throw new Error('分类目录读取不完整，请重新打开编目');
    categoryOptions.value = rows.map(row => ({ label: row.name, value: row.id }));
  } catch (error: any) {
    if (session.current(ticket)) { session.reset(); message.error(error?.message || '编目目录读取失败'); }
  } finally { session.finish(ticket); }
}
function snapshot() {
  const content = cloneDeep(form.value);
  // Do not turn a missing archived block into an empty new block during form initialization.
  if (historical.value) content.registrationDetails = cloneDeep(catalog.value?.content.registrationDetails);
  const values = Object.fromEntries(Object.entries(dimensions).map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value]).filter(([, value]) => value));
  content.dimensions = Object.keys(values).length ? values : undefined;
  return content;
}
async function persist(submit: boolean) {
  if (!visible.value || busy.value || !session.active.value || !catalog.value?.collectionId || !catalog.value.stateHash || !catalog.value.canEdit) return;
  const target = cloneDeep(catalog.value), content = snapshot();
  const ticket = session.begin(submit ? 'submit' : 'save'); if (ticket === null) return;
  try {
    if (submit) {
      try { await pageFormRef.value?.validateForm(); }
      catch { return; }
    }
    if (!session.current(ticket) || !visible.value || !isEqual(content, snapshot())) return;
    const response = submit
      ? await catalogApi.saveAndSubmit(target.collectionId!, content, target.stateHash!)
      : await catalogApi.save(target.collectionId!, content, target.stateHash!);
    if (!session.current(ticket) || !visible.value) return;
    const result = catalogResult(response, target, submit ? 'SUBMITTED' : 'DRAFT', target.version! + (submit ? 1 : 0));
    catalog.value = result;
    if (submit) { visible.value = false; message.success('编目已保存并提交审核'); }
    else {
      if (isEqual(content, snapshot())) setContent(result.content);
      message.success('编目草稿已保存');
    }
    emit('saved');
  } catch (error: any) {
    if (session.current(ticket)) { session.reset(); message.error(error?.message || '编目办理失败，请核对实际结果'); }
  } finally { session.finish(ticket); }
}
async function saveDraft() {
  if (props.proposalMode) {
    if (!visible.value || busy.value || !session.active.value || !catalog.value?.collectionId) return;
    const value = snapshot(); visible.value = false; emit('proposal', value); return;
  }
  await persist(false);
}
async function saveAndSubmit() { if (!props.proposalMode) await persist(true); }
function close() { session.reset(); }

function addAccessory() {
  form.value.accessories ||= [];
  form.value.accessories.push({ quantity: 1, unit: '件' });
}

function removeAccessory(index: number) {
  form.value.accessories?.splice(index, 1);
}

defineExpose({ open, close });
</script>

<style scoped>
.form-hint {
  margin-bottom: 12px;
}
.catalog-form-body {
  height: 650px;
  overflow-y: auto;
  padding-right: 6px;
}
.dimension-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
}
.accessory-row {
  display: grid;
  grid-template-columns: 100px 1.3fr 1.3fr 110px 90px 1.5fr 52px;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}
</style>

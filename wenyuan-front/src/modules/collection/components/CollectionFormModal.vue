<template>
  <HPageForm
    v-model:show="visible"
    :title="form.id ? '编辑藏品' : '藏品登记'"
    :model="form"
    :rules="rules"
    :handle-submit="handleSubmit"
    :label-placement="formColumns === 1 ? 'top' : 'left'"
    label-width="112"
    require-mark-placement="right-hanging"
  >
    <div ref="formBody" class="collection-form-body">
      <NTabs class="collection-form-tabs" type="line" animated size="small">
        <NTabPane name="base" tab="基本信息">
          <NAlert v-if="form.id" type="info" :bordered="false" style="margin-bottom: 12px">
            名称、级别、来源和取得时间属于总账关键字段，已锁定；如需调整，请在藏品台账使用「关键字段更正」。
          </NAlert>
          <NGrid :cols="formColumns" :x-gap="16">
            <NFormItemGi
              label="总登记号"
              path="totalRegNo"
              :feedback="form.id ? '编号不可改，更正须走「留痕更正」' : undefined"
            >
              <NInput v-model:value="form.totalRegNo" :disabled="!!form.id" placeholder="留空由系统按馆连续编号" />
            </NFormItemGi>
            <NFormItemGi label="藏品性质" :span="formColumns" path="nature" ><template #label><CollectionFieldLabel label="藏品性质" :help="collectionNatureHint" /></template><NSelect :value="form.nature" :options="collectionNatureOptions" clearable @update:value="changeNature" /></NFormItemGi>
            <NFormItemGi label="分类号" path="classNo">
              <NInput v-model:value="form.classNo" placeholder="分类号" />
            </NFormItemGi>
            <NFormItemGi label="辅助编号" path="auxNo">
              <NInput v-model:value="form.auxNo" placeholder="原藏号 / 辅助号" />
            </NFormItemGi>
            <NFormItemGi label="名称" path="name">
              <NInput v-model:value="form.name" :disabled="!!form.id" placeholder="藏品名称" />
            </NFormItemGi>
            <NFormItemGi label="原名" path="originalName">
              <NInput v-model:value="form.originalName" placeholder="藏品原名" />
            </NFormItemGi>
            <NFormItemGi label="藏品分类" path="categoryId">
              <NSelect
                v-model:value="form.categoryId"
                :options="categoryOptions"
                filterable
                clearable
                placeholder="选择分类"
              />
            </NFormItemGi>
            <NGi :span="formColumns"><CollectionDescriptionFields v-model="form" :cols="formColumns" /></NGi>
            <NFormItemGi label="文物等级" path="level">
              <NText v-if="form.nature === 'OTHER'" depth="3">不适用文物等级</NText>
              <HDictSelect v-else
                v-model:value="form.level"
                type="col_relic_level"
                placeholder="先核实藏品性质"
                clearable
                :disabled="!!form.id || !form.nature"
              />
            </NFormItemGi>
            <NFormItemGi label="来源方式" path="source">
              <HDictSelect
                v-model:value="form.source"
                type="col_source"
                placeholder="来源"
                clearable
                :disabled="!!form.id"
              />
            </NFormItemGi>
            <NFormItemGi label="登记件套数" path="quantity">
              <NInputNumber v-model:value="form.quantity" :min="1" style="width: 100%" />
            </NFormItemGi>
            <NFormItemGi label="单位" path="unit">
              <NInput v-model:value="form.unit" placeholder="单位" />
            </NFormItemGi>
            <NFormItemGi label="实际数量" path="actualQuantity"><template #label><CollectionFieldLabel label="实际数量" help="成套或多分件藏品提交编目时须填写实际件数和单位；单件可沿用登记数量，已核实历史迁入不强制补填未知数量。" /></template><NInputNumber v-model:value="form.actualQuantity" :min="1" :precision="0" style="width:100%" /></NFormItemGi>
            <NFormItemGi label="实际数量单位" path="actualQuantityUnit"><NInput v-model:value="form.actualQuantityUnit" placeholder="如 件、只、册" /></NFormItemGi>
            <NFormItemGi label="取得时间范围" path="acquireDateRange">
              <NInput v-model:value="form.acquireDateRange" :disabled="!!form.id" placeholder="如 1949.10.1-1965" />
            </NFormItemGi>
            <NFormItemGi label="取得年度" path="acquireYear">
              <NInput v-model:value="form.acquireYear" :disabled="!!form.id" placeholder="如 2009" />
            </NFormItemGi>
            <NFormItemGi label="取得日期" path="acquireDate">
              <NDatePicker
                v-model:value="form.acquireDate"
                type="date"
                clearable
                style="width: 100%"
                :disabled="!!form.id"
              />
            </NFormItemGi>
          </NGrid>
        </NTabPane>

        <NTabPane name="description" tab="尺寸描述">
          <NGrid :cols="formColumns" :x-gap="16">
            <NFormItemGi label="质量范围" path="weightRange">
              <NInput v-model:value="form.weightRange" placeholder="如 0.01-1 kg" />
            </NFormItemGi>
            <NFormItemGi label="具体质量" path="weight">
              <NInput v-model:value="form.weight" placeholder="如 0.17" />
            </NFormItemGi>
            <NFormItemGi label="质量单位" path="weightUnit">
              <NInput v-model:value="form.weightUnit" placeholder="如 kg / g" />
            </NFormItemGi>
            <NFormItemGi label="估价（元）" path="valuation">
              <NInputNumber :value="centsToYuan(form.valuation)" @update:value="form.valuation = yuanFormToCents($event, form.valuation)" :min="0" :max="Math.floor(Number.MAX_SAFE_INTEGER / 100)" :precision="yuanInputPrecision(form.valuation)" style="width: 100%" />
            </NFormItemGi>
            <NFormItemGi label="完残程度" path="completeness">
              <HDictSelect v-model:value="form.completeness" type="col_completeness" placeholder="完残程度" clearable />
            </NFormItemGi>
            <NFormItemGi label="保存状态" path="preserveState">
              <HDictSelect v-model:value="form.preserveState" type="col_preserve" placeholder="保存状态" clearable />
            </NFormItemGi>
            <NFormItemGi label="来源说明" path="sourceDesc" :span="formColumns">
              <NInput v-model:value="form.sourceDesc" placeholder="发掘单位 / 捐赠人 / 调拨批文等" />
            </NFormItemGi>
            <NFormItemGi label="地域" path="region">
              <NInput v-model:value="form.region" placeholder="出土/流传地域" />
            </NFormItemGi>
            <NFormItemGi label="色泽" path="color">
              <NInput v-model:value="form.color" placeholder="色泽" />
            </NFormItemGi>
            <NFormItemGi label="容积" path="volume">
              <NInput v-model:value="form.volume" placeholder="容积数值" />
            </NFormItemGi>
            <NFormItemGi label="容积单位" path="volumeUnit">
              <NInput v-model:value="form.volumeUnit" placeholder="如 毫升 / 升" />
            </NFormItemGi>
            <NFormItemGi label="来源号" path="sourceNo">
              <NInput v-model:value="form.sourceNo" placeholder="来源号" />
            </NFormItemGi>
            <NFormItemGi label="国别" path="country">
              <NInput v-model:value="form.country" placeholder="如 中国" />
            </NFormItemGi>
            <NFormItemGi label="人文类型" path="culture">
              <NInput v-model:value="form.culture" placeholder="民族 / 人文类型" />
            </NFormItemGi>
            <NFormItemGi label="著者" path="author">
              <NInput v-model:value="form.author" placeholder="著者 / 创作者" />
            </NFormItemGi>
            <NFormItemGi label="版本" path="edition">
              <NInput v-model:value="form.edition" placeholder="版本信息" />
            </NFormItemGi>
            <NFormItemGi label="存卷" path="volumeNo">
              <NInput v-model:value="form.volumeNo" placeholder="存卷信息" />
            </NFormItemGi>
            <NFormItemGi label="尺寸" path="dimensions" :span="formColumns">
              <div class="dimension-grid" :style="formColumns === 1 ? { gridTemplateColumns: 'repeat(2, minmax(0, 1fr))' } : undefined">
                <NInput v-model:value="dimensionForm.height" placeholder="通高" />
                <NInput v-model:value="dimensionForm.mouth" placeholder="口径" />
                <NInput v-model:value="dimensionForm.bottom" placeholder="底径" />
                <NInput v-model:value="dimensionForm.belly" placeholder="腹径" />
                <NInput v-model:value="dimensionForm.length" placeholder="长" />
                <NInput v-model:value="dimensionForm.width" placeholder="宽" />
                <NInput v-model:value="dimensionForm.diameter" placeholder="直径" />
              </div>
            </NFormItemGi>
            <NFormItemGi label="尺寸单位" path="dimensionUnit"><NSelect v-model:value="form.dimensionUnit" :options="lengthUnits" clearable /></NFormItemGi>
            <NFormItemGi label="具体尺寸" :span="formColumns">
              <NInput v-model:value="specificDimensions" type="textarea" :rows="2" placeholder="如：通高12cm，口径8cm；或展开长30cm、宽20cm等补充尺寸说明" />
            </NFormItemGi>
            <NFormItemGi label="完残状况" path="completeStatus" :span="formColumns">
              <NInput
                v-model:value="form.completeStatus"
                type="textarea"
                :rows="2"
                placeholder="缺损、修补、锈蚀等具体状况"
              />
            </NFormItemGi>
            <NFormItemGi label="铭文款识" path="inscription" :span="formColumns">
              <NInput v-model:value="form.inscription" type="textarea" :rows="2" placeholder="铭文 / 款识" />
            </NFormItemGi>
            <NFormItemGi label="特征描述" path="feature" :span="formColumns">
              <NInput v-model:value="form.feature" type="textarea" :rows="2" placeholder="器形 / 纹饰 / 工艺特征" />
            </NFormItemGi>
            <NFormItemGi label="备注" path="remark" :span="formColumns">
              <NInput v-model:value="form.remark" type="textarea" :rows="2" placeholder="备注" />
            </NFormItemGi>
          </NGrid>
        </NTabPane>

        <NTabPane name="registration" tab="测量与流传著录">
          <RegistrationDetailsEditor v-if="form.registrationDetails" v-model="form.registrationDetails" :cols="formColumns" :nature="form.nature" />
        </NTabPane>
        <NTabPane name="media" tab="影像库位">
          <NGrid :cols="formColumns" :x-gap="16">
            <NFormItemGi label="当前库位" path="warehouseId">
              <WarehouseTreeSelect v-model:value="form.warehouseId" placeholder="选择库位" />
            </NFormItemGi>
            <NFormItemGi label="封面影像" path="coverImage" :span="formColumns">
              <HUploadInput
                v-model:value="form.coverImage"
                @upload-finish="form.coverImage = $event.id"
                placeholder="上传封面影像"
                clearable
              />
            </NFormItemGi>
          </NGrid>
        </NTabPane>
      </NTabs>
    </div>
  </HPageForm>
</template>

<script setup lang="ts">
import CollectionFieldLabel from './CollectionFieldLabel.vue';
import { useElementSize } from '@vueuse/core';
import CollectionDescriptionFields from './CollectionDescriptionFields.vue';
import RegistrationDetailsEditor from './RegistrationDetailsEditor.vue';
import { collectionNatureOptions, collectionNatureHint, usesRelicRules } from '../utils/collectionNature';
import { computed, reactive, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import type { FormRules } from 'naive-ui';
import { useMessage } from 'naive-ui';
import { collectionApi } from '../api/collection';
import { categoryApi } from '../api/category';
import { REGISTRATION_RECEIPT_HINT } from '../utils/accessionReceiptHints';
import WarehouseTreeSelect from './WarehouseTreeSelect.vue';
import type { CollectionVo } from '../typings/collection';
import { centsToYuan, yuanFormToCents, yuanInputPrecision } from '../utils/moneyYuan';
import { formatLocalDateValue, hasLocalDateValue } from '@/utils';
import { actualQuantityRules } from '../utils/collectionEntryRules';

const emit = defineEmits<{ (e: 'saved'): void }>();
const message = useMessage();

const visible = ref(false);
const formBody = ref<HTMLElement>();
const { width: formWidth } = useElementSize(formBody);
const formColumns = computed(() => formWidth.value >= 640 ? 2 : 1);

const defaultForm: CollectionVo = { quantity: 1, unit: '件', registrationDetails: {} };
const form = ref<CollectionVo>(cloneDeep(defaultForm));
const specificDimensions = ref('');
const dimensionForm = reactive<Record<string, string | undefined>>({
  height: undefined,
  mouth: undefined,
  bottom: undefined,
  belly: undefined,
  length: undefined,
  width: undefined,
  diameter: undefined,
});

const lengthUnits = ['mm', 'cm', 'm'].map(value => ({ value, label: value }));
const rules = computed<FormRules>(() => ({
  ...actualQuantityRules(form.value),
  name: { required: true, message: '请输入名称', trigger: ['blur', 'input'] },
  categoryId: { required: true, message: '请选择文物分类', trigger: ['change', 'blur'] },
  level: { required: usesRelicRules(form.value.nature), message: '请选择藏品级别', trigger: ['change', 'blur'] },
  quantity: { required: true, type: 'number', message: '请输入数量', trigger: ['blur', 'change'] },
  unit: { required: true, message: '请输入单位', trigger: ['blur', 'input'] },
  source: { required: true, message: '请选择来源方式', trigger: ['change', 'blur'] },
  acquireDateRange: {
    trigger: ['change', 'blur', 'input'],
    validator: () =>
      Boolean(form.value.acquireDateRange?.trim() || hasLocalDateValue(form.value.acquireDate)) ||
      new Error('请输入取得时间范围或选择取得日期'),
  },
}));
function changeNature(value: CollectionVo['nature']) {
  form.value.nature = value;
  if (!form.value.id && value === 'OTHER') form.value.level = 'UNRATED';
}

const categoryOptions = ref<{ label: string; value: string }[]>([]);
async function loadCategories() {
  if (categoryOptions.value.length) return;
  const resp: any = await categoryApi.list();
  const list: any[] = resp?.data?.list ?? resp?.data ?? [];
  categoryOptions.value = list.map((c) => ({ label: c.name, value: c.id }));
}
function open(row?: CollectionVo) {
  form.value = row ? { ...cloneDeep(defaultForm), ...cloneDeep(row) } : cloneDeep(defaultForm);
  form.value.registrationDetails ||= {};
  if (!form.value.acquireDateRange && hasLocalDateValue(form.value.acquireDate)) {
    form.value.acquireDateRange = formatLocalDateValue(form.value.acquireDate);
  }
  if (!form.value.acquireYear && hasLocalDateValue(form.value.acquireDate)) {
    form.value.acquireYear = formatLocalDateValue(form.value.acquireDate, 'YYYY');
  }
  const dimensions = row?.dimensions ?? {};
  specificDimensions.value = dimensions.extra?.description || '';
  Object.assign(dimensionForm, {
    height: dimensions.height,
    mouth: dimensions.mouth,
    bottom: dimensions.bottom,
    belly: dimensions.belly,
    length: dimensions.length,
    width: dimensions.width,
    diameter: dimensions.diameter,
  });
  loadCategories();
  visible.value = true;
}

function applyDimensions() {
  const dimensions = Object.fromEntries(
    Object.entries(dimensionForm)
      .map(([key, value]) => [key, value?.trim()])
      .filter(([, value]) => value),
  );
  const extra = { ...(form.value.dimensions?.extra || {}) };
  const description = specificDimensions.value.trim();
  if (description) extra.description = description;
  else delete extra.description;
  const result = { ...dimensions, ...(Object.keys(extra).length ? { extra } : {}) };
  form.value.dimensions = Object.keys(result).length ? result : undefined;
}

/** HPageForm 先校验再调用本方法，返回 Promise；成功后自动关闭。 */
async function handleSubmit() {
  applyDimensions();
  if (!form.value.acquireDateRange?.trim() && hasLocalDateValue(form.value.acquireDate)) {
    form.value.acquireDateRange = formatLocalDateValue(form.value.acquireDate);
  }
  if (!form.value.acquireYear?.trim() && hasLocalDateValue(form.value.acquireDate)) {
    form.value.acquireYear = formatLocalDateValue(form.value.acquireDate, 'YYYY');
  }
  const creating = !form.value.id;
  if (form.value.id) {
    await collectionApi.update(form.value.id, { ...form.value });
  } else {
    await collectionApi.create({ ...form.value });
  }
  message.success('保存成功');
  if (creating) message.info(REGISTRATION_RECEIPT_HINT, { duration: 6000 });
  emit('saved');
}

defineExpose({ open });
</script>

<style scoped>
.collection-form-body {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.collection-form-tabs {
  flex: 1;
  min-height: 0;
}

.collection-form-tabs :deep(.n-tabs-nav) {
  flex: none;
}

.collection-form-tabs :deep(.n-tabs-pane-wrapper) {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.dimension-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
}
</style>

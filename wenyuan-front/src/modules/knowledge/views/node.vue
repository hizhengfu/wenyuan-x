<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1300"
    :params="paramsForm"
    :fun-api-delete="del"
    :columns="columns"
    @add="handleAdd"
    @row-edit="handleEdit"
    @row-view="handleView"
  >
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="content_status" clearable filterable></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form
      v-model:show="formModel"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :ref-table="refPageTable"
      :disabled="formType === 'view'"
      :handle-submit="handleSubmit"
    >
      <n-grid x-gap="6">
        <n-grid-item span="18">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="关键字" path="keywords">
            <HKeywords v-model:value="form.keywords" placeholder="请输入关键字" clearable></HKeywords>
          </n-form-item>
          <n-grid x-gap="6">
            <n-grid-item span="8">
              <n-form-item label="分类" path="categoryId">
                <CategorySelect v-model:value="form.categoryId" placeholder="请选择分类" clearable></CategorySelect>
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="8">
              <n-form-item label="权重" path="weight">
                <n-input-number v-model:value="form.weight" placeholder="请输入权重" clearable></n-input-number>
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="8">
              <n-form-item label="监测对象" path="sourceId">
                <StationSelect v-model:value="form.sourceId" placeholder="请选择监测对象" clearable></StationSelect>
              </n-form-item>
            </n-grid-item>
          </n-grid>
        </n-grid-item>
        <n-grid-item span="6" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <HAvatar v-model:value="form.image"></HAvatar>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="内容" path="content">
        <n-input v-model:value="form.content" type="textarea" rows="10" :disabled="formType === 'view'"></n-input>
      </n-form-item>
      <n-form-item label="状态" path="status">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>

      <!-- 查看模式：展示结构化属性 -->
      <template v-if="formType === 'view' && viewAttrs.length > 0">
        <n-divider style="margin: 4px 0 12px">结构化属性</n-divider>
        <n-descriptions :column="2" bordered label-placement="left" size="small">
          <n-descriptions-item
            v-for="attr in viewAttrs"
            :key="attr.id"
            :label="attr.attrKey"
          >
            <n-tag
              v-if="attr.valueType === 'BOOLEAN'"
              :type="attr.attrValue === 'true' ? 'success' : 'default'"
              size="small"
            >{{ attr.attrValue }}</n-tag>
            <span v-else>{{ attr.attrValue }}</span>
          </n-descriptions-item>
        </n-descriptions>
      </template>
    </h-page-form>

    <!-- 节点属性管理抽屉 -->
    <n-drawer v-model:show="attrDrawerShow" width="640" placement="right">
      <n-drawer-content closable :native-scrollbar="false">
        <template #header>
          <span>「{{ attrNodeName }}」属性管理</span>
        </template>

        <!-- 属性列表 -->
        <n-data-table
          :columns="attrColumns"
          :data="attrList"
          :bordered="false"
          size="small"
          style="margin-bottom: 12px"
        >
        </n-data-table>

        <!-- 添加属性按钮 -->
        <n-button dashed block @click="handleAddAttr">
          <template #icon>
            <n-icon><icon-mdi-plus /></n-icon>
          </template>
          添加属性
        </n-button>

        <template #footer>
          <n-space justify="end">
            <n-button @click="attrDrawerShow = false">取消</n-button>
            <n-button type="primary" :loading="attrSaving" @click="handleSaveAttrs">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>
  </h-page-data-table>
</template>

<script lang="ts" setup>
import { add, del, edit, get, list } from '../api/node';
import { listByNode, saveAll } from '../api/nodeAttr';
import HKeywords from '@/components/form/field/keywords.vue';
import CategorySelect from '../components/form/field/CategorySelect.vue';
import StationSelect from '@/modules/monitor/components/form/field/StationSelect.vue';
import { ref, h } from 'vue';
import { cloneDeep } from 'lodash-es';
import { RouterLink } from 'vue-router';
import { NButton, NInput, NSelect, NIcon, useMessage, type DataTableColumns } from 'naive-ui';

const message = useMessage();

// ───── 主列表列定义 ─────────────────────────────────────────────
const columns = [
  { key: 'name', title: '名称', columnType: 'title', ellipsis: { tooltip: { style: { maxWidth: '600px' } } } },
  { key: 'image', title: '缩略图', columnType: 'image', width: '100px' },
  { key: 'weight', title: '权重', width: '100px', align: 'right' },
  { key: 'keywords', title: '关键字', ellipsis: { tooltip: true } },
  {
    key: 'graph',
    title: '图谱',
    align: 'center',
    width: '80px',
    render(row: any) {
      return h(
        RouterLink,
        { to: { path: '/knowledge/detail', query: { nodeId: row.id, title: `知识图谱「${row.name}」查看` } } },
        { default: () => '查看' },
      );
    },
  },
  {
    key: 'attr',
    title: '属性',
    align: 'center',
    width: '80px',
    render(row: any) {
      return h(
        NButton,
        {
          size: 'small',
          text: true,
          type: 'primary',
          onClick: () => handleOpenAttr(row),
        },
        { default: () => '管理' },
      );
    },
  },
  { key: 'status', title: '状态', dictType: 'content_status', width: '100px', align: 'center' },
];

// ───── 主表单 ────────────────────────────────────────────────────
const formModel = ref(false);
const refPageTable = ref();
const formTitle = ref();
const formType = ref('add');

const defaultForm = {
  id: '',
  sourceId: '',
  categoryId: '',
  name: '',
  image: '',
  weight: 0,
  keywords: '',
  content: '',
  status: 'NORMAL',
};
const form = ref(cloneDeep(defaultForm));

const rules = {
  name: { required: true, message: '请输入名称' },
  weight: { required: true, message: '请输入权重' },
  categoryId: { required: true, message: '请选择分类' },
  status: { required: true, message: '请选择状态' },
  content: { required: true, message: '请输入内容' },
};

const paramsForm = ref({ name: '', status: '' });

function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  formTitle.value = '添加节点';
  formModel.value = true;
}

function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  get(row.id).then((resp: any) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  formTitle.value = `编辑「${row.name}」`;
}

function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  get(row.id).then((resp: any) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  loadViewAttrs(row.id);
  formTitle.value = `查看「${row.name}」`;
}

function handleSubmit(data: any) {
  return formType.value === 'add' ? add(data) : edit(data);
}

// ───── 查看模式：属性只读展示 ────────────────────────────────────

const viewAttrs = ref<any[]>([])

function loadViewAttrs(nodeId: string) {
  viewAttrs.value = []
  if (!nodeId) return
  listByNode(nodeId).then((resp: any) => {
    viewAttrs.value = resp.data || []
  })
}

// ───── 节点属性抽屉 ──────────────────────────────────────────────

const attrDrawerShow = ref(false);
const attrNodeId = ref('');
const attrNodeName = ref('');
const attrSaving = ref(false);

/** 属性行的本地模型 */
interface AttrRow {
  id?: string;
  attrKey: string;
  attrValue: string;
  valueType: string;
}
const attrList = ref<AttrRow[]>([]);

/** 属性值类型选项 */
const valueTypeOptions = [
  { label: '文本', value: 'STRING' },
  { label: '数字', value: 'NUMBER' },
  { label: '日期', value: 'DATE' },
  { label: '布尔', value: 'BOOLEAN' },
];

/** 属性表格列（行内编辑） */
const attrColumns: DataTableColumns<AttrRow> = [
  {
    key: 'attrKey',
    title: '属性键',
    width: 160,
    render(row: AttrRow, index: number) {
      return h(NInput, {
        value: row.attrKey,
        size: 'small',
        placeholder: '如：朝代、材质',
        onUpdateValue: (v: string) => {
          attrList.value[index].attrKey = v;
        },
      });
    },
  },
  {
    key: 'attrValue',
    title: '属性值',
    render(row: AttrRow, index: number) {
      return h(NInput, {
        value: row.attrValue,
        size: 'small',
        placeholder: '请输入属性值',
        onUpdateValue: (v: string) => {
          attrList.value[index].attrValue = v;
        },
      });
    },
  },
  {
    key: 'valueType',
    title: '类型',
    width: 100,
    render(row: AttrRow, index: number) {
      return h(NSelect, {
        value: row.valueType,
        size: 'small',
        options: valueTypeOptions,
        onUpdateValue: (v: string) => {
          attrList.value[index].valueType = v;
        },
      });
    },
  },
  {
    key: 'action',
    title: '操作',
    width: 60,
    align: 'center',
    render(_row: AttrRow, index: number) {
      return h(
        NButton,
        {
          size: 'small',
          text: true,
          type: 'error',
          onClick: () => attrList.value.splice(index, 1),
        },
        { default: () => '删除' },
      );
    },
  },
];

/** 打开属性抽屉 */
function handleOpenAttr(row: any) {
  attrNodeId.value = row.id;
  attrNodeName.value = row.name;
  attrList.value = [];
  attrDrawerShow.value = true;
  listByNode(row.id).then((resp: any) => {
    attrList.value = (resp.data || []).map((a: any) => ({
      id: a.id,
      attrKey: a.attrKey,
      attrValue: a.attrValue,
      valueType: a.valueType ?? 'STRING',
    }));
  });
}

/** 添加空行 */
function handleAddAttr() {
  attrList.value.push({ attrKey: '', attrValue: '', valueType: 'STRING' });
}

/** 保存所有属性 */
function handleSaveAttrs() {
  const invalid = attrList.value.some((a) => !a.attrKey.trim() || !a.attrValue.trim());
  if (invalid) {
    message.warning('属性键和属性值不能为空');
    return;
  }
  attrSaving.value = true;
  saveAll(attrNodeId.value, attrList.value)
    .then(() => {
      message.success('属性保存成功');
      attrDrawerShow.value = false;
    })
    .finally(() => {
      attrSaving.value = false;
    });
}
</script>

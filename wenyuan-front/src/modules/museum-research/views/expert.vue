<template>
  <HPageDataTable
    ref="tableRef"
    :columns="columns"
    :fetch-data="expertApi.page"
    :fun-api-delete="expertApi.delete"
    :params="queryParams"
    row-key-field="id"
    @add="onAdd"
    @row-edit="onEdit"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="姓名 / 单位 / 成果" style="width: 210px" />
      </NFormItem>
      <NFormItem label="领域">
        <HDictSelect v-model:value="queryParams.field" type="research_direction" clearable placeholder="研究领域" style="width: 130px" />
      </NFormItem>
      <NFormItem label="地域">
        <NInput v-model:value="queryParams.region" clearable placeholder="省/市" style="width: 110px" />
      </NFormItem>
      <NFormItem label="职称">
        <HDictSelect v-model:value="queryParams.title" type="research_expert_title" clearable placeholder="职称" style="width: 140px" />
      </NFormItem>
    </template>

    <HPageForm
      v-model:show="formShow"
      :title="editVo.id ? '编辑专家' : '新增专家'"
      :model="editVo"
      :rules="rules"
      :ref-table="tableRef"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="90"
      width="680px"
    >
      <NFormItem label="姓名" path="name">
        <NInput v-model:value="editVo.name" style="width: 220px" />
      </NFormItem>
      <NFormItem label="评审账号" path="userId">
        <div style="width: 100%">
          <UserSelect v-model:value="editVo.userId" preserve-unlisted-values clearable placeholder="选择专家本人账号" />
          <div style="margin-top: 6px; color: #666">邀请评审前须绑定本馆有效账号；已有评审记录后不能直接更换或解除绑定。</div>
        </div>
      </NFormItem>
      <NFormItem label="性别" path="sex">
        <HDictSelect v-model:value="editVo.sex" type="sex_type" clearable style="width: 180px" />
      </NFormItem>
      <NFormItem label="所在单位" path="orgName">
        <NInput v-model:value="editVo.orgName" />
      </NFormItem>
      <NFormItem label="职称" path="title">
        <HDictSelect v-model:value="editVo.title" type="research_expert_title" clearable style="width: 220px" />
      </NFormItem>
      <NFormItem label="研究领域" path="field">
        <HDictSelect v-model:value="editVo.field" type="research_direction" multiple clearable placeholder="可多选" />
      </NFormItem>
      <NFormItem label="地域" path="region">
        <NInput v-model:value="editVo.region" placeholder="如：江西南昌" style="width: 220px" />
      </NFormItem>
      <NFormItem label="电话" path="telephone">
        <NInput v-model:value="editVo.telephone" style="width: 220px" />
      </NFormItem>
      <NFormItem label="邮箱" path="email">
        <NInput v-model:value="editVo.email" style="width: 260px" />
      </NFormItem>
      <NFormItem label="学术成果" path="achievements">
        <NInput v-model:value="editVo.achievements" type="textarea" :rows="3" placeholder="代表性论文/专著/项目" />
      </NFormItem>
      <NFormItem label="评审经验" path="reviewExperience">
        <NInput v-model:value="editVo.reviewExperience" type="textarea" :rows="2" />
      </NFormItem>
      <NFormItem label="馆内专家" path="internal">
        <NSwitch v-model:value="editVo.internal" />
      </NFormItem>
      <NFormItem label="启用" path="enabled">
        <NSwitch v-model:value="editVo.enabled" />
      </NFormItem>
      <NFormItem label="备注" path="remarks">
        <NInput v-model:value="editVo.remarks" type="textarea" :rows="2" />
      </NFormItem>
    </HPageForm>

    <HPageView v-model:show="viewShow" title="专家详情" width="660px">
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="姓名">{{ viewRow.name }}</NDescriptionsItem>
        <NDescriptionsItem label="评审账号">{{ viewRow.userId || '尚未绑定' }}</NDescriptionsItem>
        <NDescriptionsItem label="性别"><HDictText type="sex_type" :value="viewRow.sex" /></NDescriptionsItem>
        <NDescriptionsItem label="所在单位">{{ viewRow.orgName || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="职称"><HDictText type="research_expert_title" :value="viewRow.title" /></NDescriptionsItem>
        <NDescriptionsItem label="研究领域">
          <template v-if="viewRow.field">
            <HDictText
              v-for="v in viewRow.field.split(',').map((s) => s.trim()).filter(Boolean)"
              :key="v"
              type="research_direction"
              :value="v"
              style="margin-right: 8px"
            />
          </template>
          <template v-else>--</template>
        </NDescriptionsItem>
        <NDescriptionsItem label="地域">{{ viewRow.region || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="电话">{{ viewRow.telephone || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="邮箱">{{ viewRow.email || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="学术成果">{{ viewRow.achievements || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="评审经验">{{ viewRow.reviewExperience || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="馆内专家">{{ viewRow.internal ? '是' : '否' }}</NDescriptionsItem>
        <NDescriptionsItem label="状态">{{ viewRow.enabled === false ? '停用' : '启用' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { h, reactive, ref, resolveComponent } from 'vue';
import { NDescriptions, NDescriptionsItem, NFormItem, NInput, NSwitch, useMessage } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import { expertApi } from '../api/expert';
import type { ResExpertVo } from '../typings';

const HDictText = resolveComponent('HDictText');
const message = useMessage();

const tableRef = ref<any>(null);

const queryParams = reactive({
  keyword: null as string | null,
  field: null as string | null,
  region: null as string | null,
  title: null as string | null,
});

const formShow = ref(false);
const emptyVo: ResExpertVo = { internal: false, enabled: true };
const editVo = ref<ResExpertVo>({ ...emptyVo });

const rules = {
  name: { required: true, message: '请输入姓名', trigger: ['blur', 'input'] },
};

const columns: any[] = [
  { title: '姓名', key: 'name', columnType: 'title', minWidth: 100 },
  {
    title: '职称', key: 'title', width: 120,
    render: (r: ResExpertVo) => h(HDictText, { type: 'research_expert_title', value: r.title }),
  },
  { title: '所在单位', key: 'orgName', minWidth: 160, ellipsis: { tooltip: true } },
  {
    title: '研究领域', key: 'field', minWidth: 150,
    render: (r: ResExpertVo) => {
      const list = r.field?.split(',').map((s) => s.trim()).filter(Boolean) || [];
      if (!list.length) return '—';
      return h('span', {}, list.map((v) => h(HDictText, { type: 'research_direction', value: v, style: 'margin-right:6px' })));
    },
  },
  { title: '地域', key: 'region', width: 110 },
  { title: '电话', key: 'telephone', width: 120 },
  { title: '馆内', key: 'internal', width: 60, render: (r: ResExpertVo) => (r.internal ? '是' : '否') },
  {
    title: '启用',
    key: 'enabled',
    width: 70,
    render: (r: ResExpertVo) =>
      h(NSwitch, {
        value: r.enabled !== false,
        size: 'small',
        onUpdateValue: (val: boolean) => onToggle(r, val),
      }),
  },
];

const onAdd = () => {
  editVo.value = { ...cloneDeep(emptyVo) };
  formShow.value = true;
};

const onEdit = (row: ResExpertVo) => {
  editVo.value = cloneDeep(row);
  formShow.value = true;
};

const viewShow = ref(false);
const viewRow = ref<ResExpertVo | null>(null);
const onView = (row: ResExpertVo) => {
  viewRow.value = row;
  viewShow.value = true;
};

async function onToggle(row: ResExpertVo, enabled: boolean) {
  await expertApi.setEnabled(row.id!, enabled);
  message.success(enabled ? '已启用' : '已停用');
  tableRef.value?.handleQuery?.();
}

function handleSubmit() {
  return expertApi.save({ ...editVo.value, userId: editVo.value.userId?.trim() || '' });
}
</script>

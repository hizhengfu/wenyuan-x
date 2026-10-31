<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="page"
    :columns="columns"
    :params="paramsForm"
    :isAdd="false"
    :isEdit="false"
    @rowDelete="(row:any) => del(row.id)"
  >
    <template #queryForm>
      <n-form-item label="图名">
        <n-input v-model:value="paramsForm.title" placeholder="模糊搜索" clearable />
      </n-form-item>
      <n-form-item label="遗址">
        <WmsProjectSelect v-model:value="paramsForm.projectId" clearable filterable />
      </n-form-item>
    </template>
    <template #toolbar-extra>
      <n-button type="primary" @click="handleNew">
        <template #icon><n-icon><IconMdiPlus /></n-icon></template>
        新建排版
      </n-button>
    </template>

    <n-modal v-model:show="newShow" preset="card" style="width: 500px" title="新建图片排版">
      <n-form label-placement="left" label-width="80px">
        <n-form-item label="图名" required>
          <n-input v-model:value="newForm.title" />
        </n-form-item>
        <n-form-item label="遗址">
          <WmsProjectSelect v-model:value="newForm.projectId" clearable filterable />
        </n-form-item>
        <n-form-item label="幅面">
          <n-radio-group v-model:value="newForm.layout">
            <n-radio-button value="A4_PORTRAIT">A4 纵向</n-radio-button>
            <n-radio-button value="A4_LANDSCAPE">A4 横向</n-radio-button>
          </n-radio-group>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="newShow = false">取消</n-button>
          <n-button type="primary" :loading="loading" @click="doNew">下一步</n-button>
        </n-space>
      </template>
    </n-modal>
  </h-page-data-table>
</template>

<script lang="ts" setup>
import { h, ref } from 'vue';
import { useMessage, NButton } from 'naive-ui';
import { useRouter } from 'vue-router';
import { WmsProjectSelect } from '../../../components/form/field';
import { page, create, del } from '../../../api/imageLayout';

const message = useMessage();
const router = useRouter();
const refPageTable = ref();

const paramsForm = ref({ title: '', projectId: undefined });

const columns = [
  { key: 'seq', title: '序号', width: 60, align: 'center', render: (_r: any, i: number) => i + 1 },
  { key: 'thumbnailUrl', title: '缩略图', columnType: 'image', width: 90 },
  { key: 'title', title: '图名', width: 220, ellipsis: true },
  { key: 'projectName', title: '遗址名称', width: 150 },
  { key: 'layout', title: '幅面', width: 90 },
  { key: 'updateTime', title: '编辑时间', columnType: 'dateTime', width: 160 },
  { key: 'createTime', title: '创建时间', columnType: 'dateTime', width: 160 },
  {
    key: 'operate',
    type: 'operate',
    fixed: 'right',
    title: '操作',
    align: 'center',
    width: 180,
    render(row: any) {
      return [
        h(NButton, { size: 'tiny', type: 'primary', onClick: () => goEditor(row.id) }, { default: () => '编辑' }),
        h(NButton, { size: 'tiny', type: 'error', style: 'margin-left:6px', onClick: () => handleDelete(row.id) }, { default: () => '删除' }),
      ];
    },
  },
];

const newShow = ref(false);
const loading = ref(false);
const newForm = ref<{ title?: string; projectId?: string; layout: string }>({ layout: 'A4_PORTRAIT' });

function handleNew() {
  newForm.value = { layout: 'A4_PORTRAIT' };
  newShow.value = true;
}

async function doNew() {
  if (!newForm.value.title) {
    message.warning('请输入图名');
    return;
  }
  loading.value = true;
  try {
    const isLandscape = newForm.value.layout === 'A4_LANDSCAPE';
    const resp: any = await create({
      title: newForm.value.title,
      projectId: newForm.value.projectId,
      layout: newForm.value.layout,
      width: isLandscape ? 1123 : 794,
      height: isLandscape ? 794 : 1123,
    });
    newShow.value = false;
    router.push({ path: '/wms/research/imageLayoutEditor', query: { id: resp.data } });
  } catch (e: any) {
    message.error(e?.message || '创建失败');
  } finally {
    loading.value = false;
  }
}

function goEditor(id: string) {
  router.push({ path: '/wms/research/imageLayoutEditor', query: { id } });
}
async function handleDelete(id: string) {
  await del(id);
  message.success('已删除');
  refPageTable.value?.handleRefresh();
}
</script>

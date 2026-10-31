<template>
  <HDataIterator :title="'照片册页'" :fetchData="list" :params="paramsForm" size="small" :showHeader="false">
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
    </template>

    <template v-slot="{ list }">
      <div class="card-grid">
        <n-card
          hoverable
          size="small"
          :segmented="{
            content: true,
            footer: 'soft',
          }"
          v-for="(item, index) in list"
          :key="index"
        >
          <template #cover v-if="item.path">
            <n-image :src="fileUrl(item.path) + '?w=800&h=300'" :preview-src="fileUrl(item.path)"></n-image>
          </template>
          <template #header>
            {{ item.photographNo ? item.photographNo : '' }}:{{ item.name ? item.name : '' }}
          </template>
          <div style="display: flex; flex-direction: column">
            <n-table :single-line="false">
              <colgroup>
                <col style="width: 82px" />
                <col />
                <col style="width: 82px" />
                <col />
              </colgroup>
              <tr>
                <td>编码</td>
                <td colspan="3">{{ item.code ? item.code : '' }}</td>
              </tr>
              <tr>
                <td>照片号</td>
                <td>{{ item.photographNo ? item.photographNo : '' }}</td>
                <td>底片号</td>
                <td>{{ item.photographicPlateNo ? item.photographicPlateNo : '' }}</td>
              </tr>
              <tr>
                <td>摄影者</td>
                <td>{{ item.photographBy ? item.photographBy : '' }}</td>
                <td>拍摄时间</td>
                <td>{{ item.photographTime ? dayjs(item.photographTime).format('YYYY-MM-DD') : '' }}</td>
              </tr>
              <tr>
                <td>拍摄方位</td>
                <td colspan="3">{{ item.photographOrientation ? item.photographOrientation : '' }}</td>
              </tr>
              <tr>
                <td>文字说明</td>
                <td colspan="3">{{ item.remark ? item.remark : '' }}</td>
              </tr>
            </n-table>
          </div>
        </n-card>
      </div>
    </template>
  </HDataIterator>
</template>
<script lang="ts" setup>
import { defineProps, ref } from 'vue';
import { list } from '../../../api/fourth/fourthCulturalRelicPhotograph';
import { fileUrl } from '@/utils';
import dayjs from 'dayjs';
import Column from '@/modules/module/components/form/field/column.vue';

const props = defineProps({
  fourthCulturalRecordId: {
    type: String,
    required: true,
  },
});

/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  culturalRelicId: props.fourthCulturalRecordId,
});
</script>
<style lang="scss" scoped>
.card-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}
</style>

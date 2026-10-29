<template>
  <HDataIterator :title="'图纸册页'" :fetchData="list" :params="paramsForm" size="small" :showHeader="false">
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
            {{ item.name ? item.name : '' }}
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
                <td>图纸编号</td>
                <td colspan="3">{{ item.code ? item.code : '暂无数据' }}</td>
              </tr>
              <tr>
                <td>图号</td>
                <td>{{ item.drawingNo ? item.drawingNo : '暂无数据' }}</td>
                <td>比例</td>
                <td>{{ item.proportion ? item.proportion : '暂无数据' }}</td>
              </tr>
              <tr>
                <td>绘制人</td>
                <td>{{ item.drawingNo ? item.drawingBy : '暂无数据' }}</td>
                <td>绘制时间</td>
                <td>{{ item.drawingTime ? dayjs(item.drawingTime).format('YYYY-MM-DD') : '暂无数据' }}</td>
              </tr>
            </n-table>
          </div>
        </n-card>
      </div>
    </template>
  </HDataIterator>
</template>
<script lang="ts" setup>
import { ref, defineProps } from 'vue';
import { list, edit, add, del, get } from '../../../api/fourth/fourthCulturalRelicDrawing';
import { fileUrl } from '@/utils';
import dayjs from 'dayjs';

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

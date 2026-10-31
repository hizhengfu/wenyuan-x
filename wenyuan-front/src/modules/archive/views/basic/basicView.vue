<template>
  <HDataIterator :title="'基础档案'" :fetchData="list" :params="paramsForm" size="small" :showHeader="false">
    <template #queryForm>
      <n-form-item label="公布名称" path="publishName">
        <n-input v-model:value="paramsForm.publishName" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="公布编号" path="publishNo">
        <n-input v-model:value="paramsForm.publishNo" placeholder="请输入公布编号" clearable></n-input>
      </n-form-item>
      <!--      <n-form-item label="代码" path="publishCode">-->
      <!--        <n-input v-model:value="paramsForm.publishCode" placeholder="请输入代码" clearable></n-input>-->
      <!--      </n-form-item>-->
      <n-form-item label="公布时代" path="publishYear">
        <n-input v-model:value="paramsForm.publishYear" placeholder="请输入公布时代" clearable></n-input>
      </n-form-item>
      <n-form-item label="公布批次" path="publishBatch">
        <h-dict-select
          v-model:value="paramsForm.publishBatch"
          placeholder="请选择公布批次"
          clearable filterable
          type="basic_public_batch"
        ></h-dict-select>
      </n-form-item>
      <n-form-item label="区域选择" path="areaId">
        <CascadeArea v-model:value="paramsForm.areaId" filterable clearable :checkStrategy="'parent'"></CascadeArea>
      </n-form-item>
      <n-form-item label="公布级别" path="publishLevel">
        <h-dict-select
          v-model:value="paramsForm.publishLevel"
          type="third_cultural_relic_level"
          placeholder="请选择公布级别"
          clearable filterable
        ></h-dict-select>
      </n-form-item>
      <n-form-item label="公布类别" path="mainCategory">
        <h-dict-select
          v-model:value="paramsForm.mainCategory"
          type="third_cultural_relic_type"
          placeholder="请选择类别"
          clearable filterable
        ></h-dict-select>
      </n-form-item>
      <n-form-item label="类别明细" path="subCategory">
        <n-input v-model:value="paramsForm.subCategory" placeholder="请输入类别明细" clearable></n-input>
      </n-form-item>
    </template>
    <template #queryFormAppend>
      <n-button @click="handleToggleView">
        <n-icon size="20">
          <IconMdiViewGridOutline v-if="showType === 'card'" />
          <IconMdiViewList v-else />
        </n-icon>
      </n-button>
    </template>
    <template v-slot="{ list }">
      <template v-if="showType === 'card'">
        <n-flex>
          <n-card
            class="card"
            hoverable
            embedded
            header-style="padding: 10px;"
            v-for="(item, index) in list"
            :key="index"
            @click="handleView(item)"
          >
            <template #header>
              <n-space align="center">
                <span style="font-weight: 700">
                  {{ item.publishName }}{{ item.publishYear ? '-' + item.publishYear : '' }}
                </span>
              </n-space>
            </template>
            <div style="display: flex">
              <img :src="fileUrl(item.image) + '?w=340'" style="width: 170px; height: 170px" v-if="item.image" />
              <img src="../../assets/images/default.png" style="width: 170px; height: 170px" v-else />
              <div class="ml-5">
                <!--                <p>
                                  公布编号:
                                  {{ item.publishNo ? item.publishNo : '暂无数据' }}
                                </p>-->
                <p>
                  <b>公布时代:</b>
                  {{ item.publishYear ? item.publishYear : '暂无数据' }}
                </p>
                <p>
                  <b>公布批次:</b>
                  <h-dict-text type="basic_public_batch" v-model:value="item.publishBatch"></h-dict-text>
                </p>
                <p>
                  <b>公布类别:</b>
                  <h-dict-text v-model:value="item.mainCategory" type="third_cultural_relic_type"></h-dict-text>
                </p>
                <p>
                  <b>公布级别:</b>
                  <h-dict-text v-model:value="item.publishLevel" type="third_cultural_relic_level"></h-dict-text>
                </p>
                <p>
                  <b>公布地址:</b>
                  {{ item.publishAddress ? item.publishAddress : '暂无数据' }}
                </p>
              </div>
            </div>
          </n-card>
        </n-flex>
      </template>
      <template v-else-if="showType === 'list'">
        <n-table :bordered="false" :single-line="false">
          <thead>
            <tr>
              <th>公布名称</th>
              <th>公布编号</th>
              <th>归属地市</th>
              <th>公布时代</th>
              <th>公布批次</th>
              <th>公布类别</th>
              <th>公布级别</th>
              <th>公布地址</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in list">
              <td style="max-width: 180px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis">
                <n-tooltip placement="left">
                  <template #trigger>
                    <n-button text type="primary" @click="handleView(row)">
                      {{ row.publishName || '--' }}
                    </n-button>
                  </template>
                  <template #default>
                    <n-button text type="primary" @click="handleView(row)">
                      {{ row.publishName || '--' }}
                    </n-button>
                  </template>
                </n-tooltip>
              </td>
              <td style="max-width: 120px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis">
                <n-tooltip placement="left">
                  <template #trigger>
                    <span>{{ row.publishNo || '--' }}</span>
                  </template>
                  <template #default>
                    {{ row.publishNo || '--' }}
                  </template>
                </n-tooltip>
              </td>
              <td style="max-width: 120px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis">
                <n-tooltip placement="left">
                  <template #trigger>
                    <span>{{ row.areaName || '--' }}</span>
                  </template>
                  <template #default>
                    {{ row.areaName || '--' }}
                  </template>
                </n-tooltip>
              </td>
              <td style="max-width: 120px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis">
                <n-tooltip placement="left">
                  <template #trigger>
                    <span>{{ row.publishYear || '--' }}</span>
                  </template>
                  <template #default>
                    {{ row.publishYear || '--' }}
                  </template>
                </n-tooltip>
              </td>
              <td>
                <h-dict-text type="basic_public_batch" v-model:value="row.publishBatch"></h-dict-text>
              </td>
              <td style="max-width: 120px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis">
                <n-tooltip placement="left">
                  <template #trigger>
                    <h-dict-text v-model:value="row.mainCategory" type="third_cultural_relic_type"></h-dict-text>
                  </template>
                  <template #default>
                    <h-dict-text v-model:value="row.mainCategory" type="third_cultural_relic_type"></h-dict-text>
                  </template>
                </n-tooltip>
              </td>
              <td style="max-width: 220px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis">
                <n-tooltip placement="left">
                  <template #trigger>
                    <h-dict-text v-model:value="row.publishLevel" type="third_cultural_relic_level"></h-dict-text>
                  </template>
                  <template #default>
                    <h-dict-text v-model:value="row.publishLevel" type="third_cultural_relic_level"></h-dict-text>
                  </template>
                </n-tooltip>
              </td>
              <td style="max-width: 320px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis">
                <n-tooltip placement="left">
                  <template #trigger>
                    <span>{{ row.publishAddress || '--' }}</span>
                  </template>
                  <template #default>
                    {{ row.publishAddress || '--' }}
                  </template>
                </n-tooltip>
              </td>
            </tr>
          </tbody>
        </n-table>
      </template>
    </template>
    <template #other>
      <h-page-form
        v-model:show="pageViewShow"
        :closable="true"
        label-width="auto"
        :title="formTitle"
        :enable-cancel="false"
      >
        <CulturalBasicViewDetail :id="selectId"></CulturalBasicViewDetail>
      </h-page-form>
    </template>
  </HDataIterator>
</template>
<script lang="ts" setup>
import { list } from '../../api/basic/culturalRecordBasic.ts';
import { ref } from 'vue';
import { NButton, NIcon } from 'naive-ui';
import CulturalBasicViewDetail from '../../components/basic/CulturalBasicViewDetail.vue';
import CascadeArea from '@/modules/sys/components/form/field/CascadeArea.vue';
import { fileUrl } from '@/utils';

/**
 *  数据查看
 * */
const pageViewShow = ref(false);
const formTitle = ref();

const showType = ref('card');
/**
 * 查询表单
 * */
const paramsForm = ref({
  publishName: '',
  mainCategory: '',
  publishNo: '',
  publishYear: '',
  publishCode: '',
  publishBatch: null,
  subCategory: '',
  areaId: '',
  publishLevel: '',
});

const selectId = ref();

/**
 * 切换视图
 * */
function handleToggleView() {
  if (showType.value === 'card') {
    showType.value = 'list';
  } else {
    showType.value = 'card';
  }
}

/**
 * 查看事件
 * */
function handleView(item: any) {
  selectId.value = item.id;
  formTitle.value = `查看「${item.publishName}」`;
  pageViewShow.value = true;
}
</script>

<style lang="scss" scoped>
.card {
  max-height: 390px;
  // width: 32.3%;
  flex: 25%;
  cursor: pointer;
  overflow: scroll;
  margin-left: 7px;
}
</style>

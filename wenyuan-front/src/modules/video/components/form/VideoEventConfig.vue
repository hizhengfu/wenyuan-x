<template>
  <HDialogViewForm size="small" title="事件订阅配置">
    <h-form :rules="rules" :model="form" :handleSubmit="handleSubscribe" :enableTeleport="false" :enableCancel="false">
      <n-form-item
        feedback="常见编码：水位偏高：459292,水位偏低：459291,火点检测：192515，烟雾检测：192513，区域入侵：131588，视频遮挡：131330"
        path="eventTypes"
      >
        <template #label>
          事件编码
          <a
            href="https://open.hikvision.com/docs/docId?productId=5c67f1e2f05948198c909700&version=%2F29c78ef52ca842c7933bd2b8e051e9d0&tagPath=%E9%99%84%E5%BD%95-%E9%99%84%E5%BD%95D%20%E4%BA%8B%E4%BB%B6%E5%88%97%E8%A1%A8"
            target="_blank"
          >
            编码表查询
          </a>
        </template>
        <n-dynamic-tags v-model:value="form.eventTypes" />
      </n-form-item>
      <n-grid :x-gap="12" cols="24">
        <n-grid-item span="12">
          <n-form-item label="事件类型" path="subType">
            <n-select v-model:value="form.subType" :options="subTypeOptions" />
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="事件等级" path="eventLvl">
            <n-dynamic-tags v-model:value="form.eventLvl" />
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="接收地址" path="eventDest" feedback="默认接收地址：/app/video/hik/event">
        <n-input v-model:value="form.eventDest" />
      </n-form-item>
    </h-form>
    <n-divider></n-divider>
    <n-card
      size="small"
      :segmented="{
        content: 'soft',
        footer: 'soft',
      }"
    >
      <template #header>
        已订阅事件
        <n-button size="tiny" type="primary" @click="handleSyncEventHistory">同步历史事件</n-button>
      </template>
      <template v-for="event in subscribeList">
        <n-tag closable size="small" @close="handleClose(event.eventTypes)"
          >订阅地址:{{ event.eventDest }}，订阅事件{{ event.eventTypes }}
        </n-tag>
      </template>
      <n-empty v-if="!subscribeList"></n-empty>
    </n-card>
  </HDialogViewForm>
</template>
<script lang="ts" setup>
import { cancelEvent, createEvent, list, queryEvent } from '../../api/hik.ts';
import { ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';

const props = defineProps({
  serverId: { type: String, required: true },
});

//事件订阅列表
const subscribeList = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  eventTypes: [], //事件类型
  eventDest: '', //指定事件接收的地址
  subType: 0, //订阅类型
  eventLvl: [], //事件等级
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));
/**
 * 验证规则
 * */
const rules = {
  eventTypes: {
    required: true,
    message: '事件类型编码不能为空',
  },
  subType: {
    required: true,
    message: '订阅类型不能为空',
  },
};

const subTypeOptions = [
  {
    label: '订阅原始事件',
    value: 0,
  },
  {
    label: '联动事件',
    value: 1,
  },
  {
    label: '原始事件和联动事件',
    value: 2,
  },
];

/**
 * 同步历史事件
 * */
function handleSyncEventHistory() {
  const { serverId } = props;
  queryEvent(serverId, {});
}

/**
 * 获取订阅事件列表
 * */
function fetchEvents() {
  if (!props.serverId) return;
  subscribeList.value = [];
  list(props.serverId).then((resp: any) => {
    subscribeList.value = resp.data;
  });
}

/**
 * 订阅事件
 * */
function handleSubscribe() {
  const { serverId } = props;
  return createEvent(serverId, form.value).then((resp: any) => {
    if (resp.data) {
      fetchEvents();
    }
  });
}

/**
 * 删除订阅的事件
 * */
function handleClose(eventTypes: any[]) {
  const { serverId } = props;
  return cancelEvent(serverId, eventTypes).then((resp: any) => {
    if (resp.data) {
      fetchEvents();
    }
  });
}

watch(
  () => props.serverId,
  () => {
    form.value = cloneDeep(defaultForm);
    fetchEvents();
  },
  {
    immediate: true,
  },
);
</script>

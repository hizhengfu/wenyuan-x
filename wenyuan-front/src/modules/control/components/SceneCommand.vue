<template>
  <h-dialog-form
    :model="commandForm"
    v-bind="$attrs"
    inine
    :autoClose='false'
    :show-feedback="false"
    label-placement="left"
    :handleSubmit="handleSubmit"
    :contentStyle="{ padding: 0 }"
    style="min-height: 500px; position: relative"
  >
    <n-layout has-sider position="absolute">
      <n-layout-sider bordered content-style="padding:0 15px;">
        <n-layout-header bordered>
          <div class="py-3">
            <n-input v-model:value="pattern" placeholder="搜索设备" />
          </div>
        </n-layout-header>
        <n-tree
          block-line
          show-line
          ellipsis
          :data="refDeviceOptions"
          :pattern="pattern"
          :node-props="nodeProps"
          :default-expanded-keys="defaultExpandedKeys"
          label-field="name"
          key-field="id"
          expand-on-click
        />
      </n-layout-sider>
      <n-layout>
        <n-layout-header bordered>
          <div style="padding: 15px">指令配置(请选择场景中需要执行的指令，注意开关不要同时选择)</div>
        </n-layout-header>
        <n-layout-content content-style="padding: 15px;">
          <template v-if="commandForm.list.length > 0">
            <n-checkbox-group v-model:value="refSceneSelectDeviceCommand">
              <n-table striped bordered :single-line="false" size='small'>
                <thead>
                  <tr>
                    <th style="width: 120px">名称</th>
                    <th>默认值</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in commandForm.list">
                    <td>
                      <n-form-item>
                        <n-checkbox :value="item.commandId" :label="item.name" />
                      </n-form-item>
                    </td>
                    <td>
                      <n-form-item v-if="item.type > 1">
                        <n-input v-model:value="item.sceneDefaultValue" placeholder="请输入默认值"></n-input>
                      </n-form-item>
                    </td>
                  </tr>
                </tbody>
              </n-table>
            </n-checkbox-group>
          </template>
          <n-empty
            v-else
            title="请选择设备"
            :description="refSelectDeviceId ? '请配置设备指令' : '请先选择设备'"
          ></n-empty>
        </n-layout-content>
      </n-layout>
    </n-layout>
  </h-dialog-form>
</template>
<script lang="ts" setup>
import { ref, watch } from 'vue';
import { filter, includes } from 'lodash-es';
import { getSceneDeviceTree, getSceneDeviceCommand, saveSceneDeviceCommand } from '@/modules/control/api/scene.ts';
import { transformToTreeFormat } from '@/utils';
import { TreeOption } from 'naive-ui';

const props = defineProps({
  sceneId: { type: String, required: true },
});

const commandForm = ref<{ list: any[] }>({ list: [] });

const pattern = ref();
const refDeviceOptions = ref();
const refSelectDeviceId = ref();
const defaultExpandedKeys = ref();

const refSceneSelectDeviceCommand = ref<any[]>([]);

function loadSceneDeviceTree(sceneId: string) {
  getSceneDeviceTree(sceneId).then((resp: any) => {
    refDeviceOptions.value = transformToTreeFormat(resp.data || [], { pIdKey: 'parentId' });
    if (refDeviceOptions.value && refDeviceOptions.value.length > 0) {
      defaultExpandedKeys.value = [refDeviceOptions.value[0].id];
    }
  });
}

/**
 * 树形属性
 * */
function nodeProps({ option }: { option: TreeOption }) {
  return {
    onClick() {
      if (!option.children && !option.disabled) {
        refSelectDeviceId.value = option.id;
        loadSceneDeviceCommand(option.id as string);
      }
    },
  };
}

watch(
  () => props.sceneId,
  (sceneId) => {
    loadSceneDeviceTree(sceneId);
    commandForm.value.list = [];
    refSelectDeviceId.value = '';
    refSceneSelectDeviceCommand.value = [];
  },
  { immediate: true },
);

/**
 *  记载设备配置指令
 * */
function loadSceneDeviceCommand(deviceId: string) {
  const { sceneId } = props;
  refSceneSelectDeviceCommand.value = [];
  getSceneDeviceCommand({ deviceId: deviceId, sceneId: sceneId }).then((resp) => {
    let list = resp.data || [];
    if (list.length > 0) {
      let selectList: any[] = [];
      list.forEach((item: any) => {
        if (item.sceneCommandId) {
          selectList.push(item.sceneCommandId);
        }
      });
      refSceneSelectDeviceCommand.value = selectList;
    }

    commandForm.value.list = list;
  });
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  const { sceneId } = props;
  const list = filter(data.list, (item: any) => includes(refSceneSelectDeviceCommand.value, item.commandId));

  let result = { sceneId: sceneId, deviceId: refSelectDeviceId.value, list: list };
  return saveSceneDeviceCommand(result);
}
</script>

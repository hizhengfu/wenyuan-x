<template>
  <n-card title="Portal可视化设计" class="h-full portal-design" :segmented="{ content: true, footer: 'soft' }">
    <template #header-extra>
      <n-form label-placement="left" inline :show-feedback="false">
        <n-form-item>
          <n-tooltip>
            <template #trigger>
              <n-button
                type="success"
                tag="a"
                :href="'#/portal/view/view?id=' + sourceId + '&title=Portal预览'"
                target="_blank"
              >
                <template #icon>
                  <n-icon>
                    <IconMdiEye />
                  </n-icon>
                </template>
              </n-button>
            </template>
            点击查看
          </n-tooltip>
        </n-form-item>
        <n-form-item>
          <n-radio-group v-model:value="portalType" @update-value="handleChangePortalType">
            <n-radio-button label="角色" value="role" />
            <n-radio-button label="菜单" value="menu" />
          </n-radio-group>
        </n-form-item>
        <n-form-item>
          <n-tooltip>
            <template #trigger>
              <n-button type="primary" @click="layoutUpdatedEvent">
                <template #icon>
                  <IconMdiCheck></IconMdiCheck>
                </template>
                点击保存
              </n-button>
            </template>
            需点击保存后才能存储布局
          </n-tooltip>
        </n-form-item>
        <n-form-item>
          <n-select
            v-model:value="sourceId"
            v-if="portalType === 'role'"
            value-field="id"
            label-field="showName"
            filterable
            scrollable
            placeholder="请选择角色"
            :options="roleList"
          >
          </n-select>
          <n-select
            v-model:value="sourceId"
            v-if="portalType === 'menu'"
            value-field="id"
            label-field="name"
            filterable
            scrollable
            placeholder="请选择菜单"
            :options="rootMenus"
          >
          </n-select>
        </n-form-item>
        <n-form-item>
          <n-popover trigger="hover" :show-arrow="false" scrollable accordion placement="bottom">
            <template #trigger>
              <n-button>可选Portal</n-button>
            </template>
            <n-input v-model:value="portalFilter" placeholder="搜索" />
            <n-tree
              block-line
              :data="portals"
              :pattern="portalFilter"
              label-field="name"
              key-field="id"
              children-field="portals"
              selectable
              :node-props="portalNodeProps"
              expand-on-click
            />
          </n-popover>
        </n-form-item>
      </n-form>
    </template>
    <fieldset
      v-for="(group, index) in groups"
      class="group_border"
      :style="{ 'border-color': group.color }"
      :key="index"
    >
      <legend v-if="group.name" style="margin-left: 10px; font-size: 20px; font-weight: bolder; color: #111">
        {{ group.name }}
      </legend>
      <grid-layout
        :layout="group.portals"
        :colNum="12"
        :rowHeight="1"
        :is-draggable="true"
        :is-resizable="true"
        :auto-size="true"
        :vertical-compact="true"
        style="position: relative"
      >
        <template #gridItemContent="slotProps">
          <portal-content v-bind="slotProps.item" :group="group" isDesign :params="params"></portal-content>
          <div class="close">
            <span>{{ slotProps.item.name }} </span>
            <n-button type="error" circle size="small" @click="removePortal(group, slotProps.item.id)">
              <template #icon>
                <n-icon>
                  <IconMdiClose />
                </n-icon>
              </template>
            </n-button>
          </div>
        </template>
      </grid-layout>
    </fieldset>
    <n-empty v-if="!groups || groups.length === 0" description="还没有添加Portal" size="huge">
      <template #extra>
        <n-button size="small"> 选择Portal</n-button>
      </template>
    </n-empty>
  </n-card>
</template>
<script lang="ts" setup>
import '@noction/vue-draggable-grid/styles';
import { GridLayout } from '@/components/basic/vueDraggableGrid';
import PortalContent from '../../components/PortalContent.vue';
import { computed, ref, watchEffect } from 'vue';
import { create, del, list, load, updateLayout } from '../../api/portalTemplate';
import { list as getRoleList } from '../../../sys/api/role';
import { get } from '../../api/portalgroup';
import { each, findIndex } from 'lodash-es';
import { useAuthStore } from '@/store';
import { TreeOption } from 'naive-ui';
import { confirm } from '@/utils';
import { adopterPortal } from '../../utils';

const authStore = useAuthStore();

const loading = ref(false);
const portalType = ref('role');
const sourceId = ref('1');
const portalFilter = ref();
const groups = ref();
const roleList = ref();
const portals = ref();

const params = ref({});

const rootMenus = computed(() => {
  return authStore.menus
    ?.filter((item) => item.parentId === '0')
    .map((it) => {
      return { id: it.id, name: it.name };
    });
});

function portalNodeProps({ option }: { option: TreeOption }) {
  return {
    onClick() {
      if (option.portalId) {
        addPortal(option);
      }
    },
  };
}

function loadRole() {
  let params = { applicationId: '1', pageSize: 1000 };
  getRoleList(params).then((res) => {
    roleList.value = [];
    if (res.data.list) {
      each(res.data.list, (item) => {
        item.showName = item.remarks + '(' + item.name + ')';
        roleList.value.push(item);
      });
    }
    loadPortal();
    loadConfigPortal();
  });
}

function loadPortal() {
  //可选Portal列表
  let params = { sourceId: sourceId.value };
  list(params).then((res) => {
    portals.value = res.data || [];
  });
}

/**
 * 加载配置的Portal
 * */
function loadConfigPortal() {
  let params = { sourceId: sourceId.value, isGroup: true };
  loading.value = true;
  load(params)
    .then((res) => {
      groups.value = [];
      if (res.data) {
        res.data.forEach((i: any, k: number) => {
          let groupItem = {
            name: i.group.name,
            id: i.group.id || 'default',
            color: i.group.color || '',
            portals: [],
          };
          i.portals.forEach((item: any, index: number) => {
            // @ts-ignore
            groupItem.portals.push(adopterPortal(item, index));
          });
          groups.value.push(groupItem);
        });
      }
    })
    .finally(() => {
      loading.value = false;
    });
}

/**
 * 添加portal
 * */
function addPortal(portal: any) {
  create({
    portalId: portal.portalId,
    sourceId: sourceId.value,
    color: portal.color,
    backgroundColor: portal.backgroundColor,
    width: portal.w,
    height: portal.h,
    x: portal.x,
    y: portal.y,
  }).then((res) => {
    portal.portalId = portal.id;
    portal.id = res.data.id;
    let groupId = portal.groupId || 'default';
    let groupIndex = findIndex(groups.value, (item: any) => {
      return item.id === groupId;
    });
    if (groupIndex > -1) {
      let portals = groups.value[groupIndex].portals;
      portals.push(adopterPortal(portal, portals.length));
    } else {
      get(portal.groupId).then((res) => {
        let groupItem = {
          name: res.data.name,
          id: res.data.id || 'default',
          color: res.data.color || '',
          portals: [],
        };
        //@ts-ignore
        groupItem.portals.push(adopterPortal(portal, 0));
        groups.value.push(groupItem);
      });
    }
  });
  return false;
}

function layoutUpdatedEvent() {
  confirm({
    title: '请注意',
    content: '点击确认后将更新Portal布局',
    onPositiveClick: () => {
      let arr: any[] = [];
      groups.value.forEach((group: any) => {
        let temp: any[] = group.portals.map((portal: any) => {
          return { id: portal.id, x: portal.x, y: portal.y, w: portal.w, h: portal.h };
        });
        if (temp && temp.length > 0) {
          arr = arr.concat(temp);
        }
      });
      updateLayout(arr);
    },
  });
}

function changePortal(type: string) {
  loadPortal();
  loadConfigPortal();
}

/**
 * 移除portal，并触发删除事件以便对数据库进行处理
 * */
function removePortal(group: any, id?: string) {
  if (!id) return;
  let index = group?.portals.findIndex((item: any) => item.id === id);
  if (index > -1) {
    // eslint-disable-next-line vue/no-mutating-props
    group?.portals.splice(index, 1);
    del(id);
  }
}

function handleChangePortalType() {
  sourceId.value = '';
  groups.value = [];
}

watchEffect(() => {
  if (sourceId.value) {
    loadRole();
  }
});
</script>

<style lang="scss" scoped>
.group_border {
  border: 1px solid #cccccc;
  background-color: #fff;
  border-radius: 5px;
  margin: 5px;
}

.portal-design {
  :deep(.vue-resizable) {
    border: 1px solid hsl(208deg, 100%, 90%);
    background-color: #ffffff;
  }
}

.close {
  background-color: #ffffff;
  border: 1px solid #ececec;
  padding: 3px;
  position: absolute;
  top: 5px;
  right: 5px;
  display: flex;
  align-items: center;
}
</style>

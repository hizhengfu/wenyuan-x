<template>
  <div class="scope-picker">
    <n-tabs type="line" animated v-model:value="activeTab">
      <!-- 用户 / 角色 / 机构：所有用户维度通道（EMAIL / SMS / IN_APP / APP_PUSH / WX_*）通用，
           不依赖具体通道选择，永远显示 -->
      <n-tab-pane name="user" tab="按用户">
        <UserSelect
          multiple
          clearable
          filterable
          :value="model.userIds"
          @update:value="handleUpdate('userIds', $event)"
          placeholder="搜索用户姓名 / 登录名"
        />
      </n-tab-pane>
      <n-tab-pane name="role" tab="按角色">
        <RoleSelect
          multiple
          clearable
          filterable
          :value="model.roleIds"
          @update:value="handleUpdate('roleIds', $event)"
          placeholder="选择角色"
        />
      </n-tab-pane>
      <n-tab-pane name="group" tab="按机构">
        <GroupSelect
          multiple
          checkable
          :value="model.groupIds"
          @update:value="handleUpdate('groupIds', $event)"
          placeholder="选择机构（递归包含子机构）"
        />
      </n-tab-pane>

      <!-- 通道相关的"裸地址"或"群名"tab：仅在对应通道被选中时显示 -->
      <n-tab-pane v-if="showEmail" name="email" tab="邮箱地址">
        <n-dynamic-tags
          :value="model.emails"
          @update:value="handleUpdate('emails', $event)"
        />
        <n-text depth="3" style="font-size: 12px">
          直接填写邮箱地址（不查用户表）。仅 <n-tag size="tiny" type="info">EMAIL</n-tag> 通道生效；
          适合验证码 / 给非平台用户发邮件场景。
        </n-text>
      </n-tab-pane>
      <n-tab-pane v-if="showSms" name="phone" tab="手机号">
        <n-dynamic-tags
          :value="model.phones"
          @update:value="handleUpdate('phones', $event)"
        />
        <n-text depth="3" style="font-size: 12px">
          直接填写手机号（不查用户表）。仅 <n-tag size="tiny" type="info">SMS</n-tag> 通道生效。
        </n-text>
      </n-tab-pane>
      <n-tab-pane v-if="showBot" name="bot" tab="群机器人">
        <n-dynamic-tags
          :value="model.botNames"
          @update:value="handleUpdate('botNames', $event)"
        />
        <n-text depth="3" style="font-size: 12px">
          输入企业微信群机器人名称（与 sys_config NOTIFY_WX_BOT_WEBHOOKS 中的 name 对应）。
          仅 <n-tag size="tiny" type="success">WX_BOT</n-tag> 通道生效。
        </n-text>
      </n-tab-pane>
    </n-tabs>

    <n-alert :type="summary.empty ? 'warning' : 'info'" :show-icon="false" style="margin-top: 8px">
      <span v-if="summary.empty">未选择任何接收人</span>
      <span v-else>
        共选
        <n-tag size="small" type="info">{{ summary.roleCount }}</n-tag> 角色
        <n-tag size="small" type="info">{{ summary.groupCount }}</n-tag> 机构
        <n-tag size="small" type="info">{{ summary.userCount }}</n-tag> 用户
        <template v-if="summary.emailCount > 0">
          <n-tag size="small" type="success">{{ summary.emailCount }}</n-tag> 邮箱
        </template>
        <template v-if="summary.phoneCount > 0">
          <n-tag size="small" type="success">{{ summary.phoneCount }}</n-tag> 手机号
        </template>
        <template v-if="summary.botCount > 0">
          <n-tag size="small" type="success">{{ summary.botCount }}</n-tag> 群机器人
        </template>
        <span style="color: #999; margin-left: 8px">（实际接收人数将由后端去重展开后给出）</span>
      </span>
    </n-alert>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import RoleSelect from '@/modules/sys/components/form/field/RoleSelect.vue';
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import type { EnumMessageNotifyChannel, MessageNotifyTargetSpec } from '../typings/message';

const props = defineProps<{
  modelValue: MessageNotifyTargetSpec;
  /**
   * 当前已选通道。决定是否显示"邮箱地址 / 手机号 / 群机器人"裸地址 tab：
   *  - EMAIL → emails tab
   *  - SMS   → phones tab
   *  - WX_BOT→ botNames tab
   *
   * 不传或空数组：通道未确定，仅显示 user/role/group（永远适用的维度）。
   */
  channels?: Array<EnumMessageNotifyChannel | string>;
  defaultTab?: 'user' | 'role' | 'group' | 'email' | 'phone' | 'bot';
}>();
const emit = defineEmits<{
  (e: 'update:modelValue', value: MessageNotifyTargetSpec): void;
}>();

const model = computed<MessageNotifyTargetSpec>(() => ({
  userIds: props.modelValue.userIds || [],
  roleIds: props.modelValue.roleIds || [],
  groupIds: props.modelValue.groupIds || [],
  botNames: props.modelValue.botNames || [],
  emails: props.modelValue.emails || [],
  phones: props.modelValue.phones || [],
}));

const showEmail = computed(() => (props.channels || []).includes('EMAIL'));
const showSms = computed(() => (props.channels || []).includes('SMS'));
const showBot = computed(() => (props.channels || []).includes('WX_BOT'));

const activeTab = ref<string>(props.defaultTab || 'user');

/**
 * 当前 active tab 对应的通道被取消勾选时，自动跳回 "user" tab，
 * 避免出现"tab 不显示但仍是激活状态导致空白"的情况。
 */
watch(
  () => [showEmail.value, showSms.value, showBot.value, activeTab.value] as const,
  () => {
    if (activeTab.value === 'email' && !showEmail.value) activeTab.value = 'user';
    else if (activeTab.value === 'phone' && !showSms.value) activeTab.value = 'user';
    else if (activeTab.value === 'bot' && !showBot.value) activeTab.value = 'user';
  },
);

function handleUpdate(key: keyof MessageNotifyTargetSpec, value: string[]) {
  emit('update:modelValue', { ...model.value, [key]: value });
}

const summary = computed(() => {
  const roleCount = model.value.roleIds?.length || 0;
  const groupCount = model.value.groupIds?.length || 0;
  const userCount = model.value.userIds?.length || 0;
  const botCount = model.value.botNames?.length || 0;
  const emailCount = model.value.emails?.length || 0;
  const phoneCount = model.value.phones?.length || 0;
  return {
    roleCount,
    groupCount,
    userCount,
    botCount,
    emailCount,
    phoneCount,
    empty: roleCount + groupCount + userCount + botCount + emailCount + phoneCount === 0,
  };
});
</script>

<style scoped>
.scope-picker{
  flex: 1;
}
.scope-picker :deep(.n-tabs-pane-wrapper) {
  padding-top: 8px;
}
</style>

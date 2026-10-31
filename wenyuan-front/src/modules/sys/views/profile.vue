<template>
  <n-card class="profile-card" content-style="padding: 0; display: flex; min-height: 0; overflow: hidden">
    <div class="profile-content">
      <header class="profile-header">
        <div class="profile-avatar">
          <slot name="avatar" :form="userInfo">
            <HAvatarCropper v-model:value="userInfo.avatar" @update:value="handleUpdateAvatar" />
          </slot>
        </div>
        <div class="profile-identity">
          <div class="profile-name-row">
            <h1 class="profile-name">{{ userInfo.name || userInfo.username || '个人中心' }}</h1>
            <n-tag
              v-if="typeof userInfo.enabled === 'boolean'"
              :type="userInfo.enabled ? 'success' : 'error'"
              size="small"
              :bordered="false"
            >
              {{ userInfo.enabled ? '账户正常' : '账户已停用' }}
            </n-tag>
          </div>
          <div class="profile-meta">
            <span>登录账号：{{ userInfo.username || '—' }}</span>
            <span v-if="userInfo.createTime"> 注册于 <n-time :time="userInfo.createTime" format="yyyy-MM-dd" /> </span>
          </div>
        </div>
      </header>

      <n-tabs
        v-model:value="activeTab"
        type="line"
        size="medium"
        class="profile-tabs"
        :theme-overrides="{ tabGapMediumLine: '12px' }"
        animated
      >
        <n-tab name="settings">个人设置</n-tab>
        <n-tab name="recent">最近浏览</n-tab>
        <n-tab name="shares">我的分享</n-tab>
        <n-tab name="favorites">我的收藏</n-tab>
        <n-tab name="likes">我的点赞</n-tab>
      </n-tabs>

      <section v-show="activeTab === 'settings'" class="profile-settings" aria-label="个人设置">
        <section class="settings-section" aria-labelledby="profile-security-title">
          <div class="settings-heading">
            <h2 id="profile-security-title">账号安全</h2>
            <n-text depth="3">管理登录密码与验证邮箱</n-text>
          </div>
          <div class="setting-row">
            <div class="setting-copy">
              <h3>登录密码</h3>
              <n-text depth="3">建议使用字母、数字与符号组合的密码。</n-text>
            </div>
            <n-button secondary @click="passwordDialog = true">修改密码</n-button>
          </div>
          <div class="setting-row">
            <div class="setting-copy">
              <h3>邮箱验证</h3>
              <n-text depth="3">绑定邮箱后，可用于账号登录和密码重置。</n-text>
            </div>
            <n-button secondary @click="emailDialog = true">设置邮箱</n-button>
          </div>
        </section>

        <section class="settings-section" aria-labelledby="profile-preferences-title">
          <div class="settings-heading">
            <h2 id="profile-preferences-title">界面偏好</h2>
            <n-text depth="3">按使用习惯调整页面显示</n-text>
          </div>
          <div class="setting-row">
            <div class="setting-copy">
              <h3 id="profile-sidebar-label">折叠侧边栏</h3>
              <n-text depth="3">收起菜单，留出更多内容空间。</n-text>
            </div>
            <n-switch v-model:value="appStore.sideCollapse" aria-labelledby="profile-sidebar-label" />
          </div>
          <div class="setting-row">
            <div class="setting-copy">
              <h3 id="profile-table-label">表格尺寸</h3>
              <n-text depth="3">调整表格行高与内容间距。</n-text>
            </div>
            <n-radio-group v-model:value="appStore.tableSize" aria-labelledby="profile-table-label" size="small">
              <n-radio-button value="small" label="小" />
              <n-radio-button value="medium" label="中" />
              <n-radio-button value="large" label="大" />
            </n-radio-group>
          </div>
        </section>
      </section>

      <!-- 首次进入互动标签才加载；返回设置时暂停请求，重新进入仍保留各列表的分页。 -->
      <KeepAlive>
        <ProfileInteractions
          v-if="activeTab !== 'settings'"
          :kind="activeTab"
          :client="interactionClient"
          :resolve-page="interactionResolvePage"
        />
      </KeepAlive>
    </div>
    <edit-password v-model:show="passwordDialog" />
    <edit-email v-model:show="emailDialog" />
  </n-card>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import ProfileInteractions from '../components/ProfileInteractions.vue';
import type { InteractionClient, InteractionListKind } from '../api/interaction';
import type { InteractionPageResolver } from '../utils/interactionNavigation';
import EditPassword from '../components/form/EditPassword.vue';
import EditEmail from '../components/form/EditEmail.vue';
import { useAuthStore, useAppStore } from '@/store';
import { updateAvatar } from '../api/user';
import { HAvatarCropper } from '@/components/form/field/upload';

// 平台可保留自己的头像上传方式、交互产品分组与页面路由解析器。
defineProps<{ interactionClient?: InteractionClient; interactionResolvePage?: InteractionPageResolver }>();

const appStore = useAppStore();
const authStore = useAuthStore();
const userInfo = computed(() => authStore.getUserInfo || {});
const activeTab = ref<'settings' | InteractionListKind>('settings');
const passwordDialog = ref(false);
const emailDialog = ref(false);

/** 将裁剪得到的头像地址保存到当前登录用户。 */
function handleUpdateAvatar(url: string) {
  updateAvatar({ avatar: url });
}
</script>

<style scoped>
.profile-card {
  height: 100%;
  min-width: 0;
  min-height: 0;
  container-type: inline-size;
}
/* 从页面容器到表格保持完整的 flex 高度链，内容在各自区域内滚动。 */
.profile-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 24px;
  overflow: hidden;
}
.profile-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 20px;
  padding-bottom: 24px;
}
.profile-avatar {
  flex: 0 0 72px;
  width: 72px;
}
.profile-avatar :deep(.n-upload-file-list--grid) {
  grid-template-columns: 72px;
}
.profile-avatar :deep(.n-upload-trigger--image-card),
.profile-avatar :deep(.n-upload-file--image-card-type) {
  width: 72px;
  height: 72px;
}
.profile-identity {
  flex: 1;
  min-width: 0;
}
.profile-name-row,
.profile-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
}
.profile-name {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.profile-meta {
  margin-top: 8px;
  color: var(--n-text-color-3);
  font-size: 13px;
  overflow-wrap: anywhere;
}
.profile-tabs {
  flex-shrink: 0;
  margin-bottom: 16px;
}
.profile-settings {
  display: grid;
  flex: 1;
  min-height: 0;
  overflow: auto;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: start;
  gap: 24px;
}
.settings-section {
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
}
.settings-heading h2 {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
}
.settings-heading {
  padding-bottom: 20px;
}
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 0;
  border-top: 1px solid var(--n-border-color);
}
.setting-row:last-child {
  padding-bottom: 0;
}
.setting-row > :last-child {
  flex-shrink: 0;
}
.setting-copy {
  min-width: 0;
}
.setting-copy h3 {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 500;
}
.setting-copy :deep(.n-text),
.settings-heading :deep(.n-text) {
  font-size: 13px;
  line-height: 1.6;
}
@container (max-width: 900px) {
  .profile-settings {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }
}
@container (max-width: 540px) {
  .profile-content {
    padding: 16px;
  }
  .profile-header {
    align-items: flex-start;
    gap: 16px;
    padding-bottom: 16px;
  }
  .profile-name {
    font-size: 18px;
  }
  .profile-tabs {
    margin-bottom: 16px;
  }
  .settings-section {
    padding: 16px;
  }
  .setting-row {
    flex-wrap: wrap;
    gap: 12px;
  }
}
</style>

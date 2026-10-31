<template>
  <n-dropdown trigger="hover" key-field="path" :options="options" @select="handleSelect">
    <div class="flex flex-center">
      <n-avatar
        round
        size="medium"
        class="mr-2"
        v-if="avatar"
        :src="avatar"
        :fallback-src="emptyAvatar"
      />
      {{ userInfo?.name }}
    </div>
  </n-dropdown>
</template>
<script lang="ts" setup>
import {useAuthStore} from "@/store";
import emptyAvatar from '../../../assets/images/empty.png'
import {Component, computed, h} from 'vue';
import {DropdownOption, NIcon} from 'naive-ui';
import Home from '~icons/mdi/home'
import Logout from '~icons/mdi/logout'
import Cog from '~icons/mdi/cog'
import AccountCircle from '~icons/mdi/account-circle'
import {useRouter} from 'vue-router';
import {baseFilePath} from '@/utils/path';

const authStore = useAuthStore()
const userInfo = authStore.getUserInfo
const router = useRouter()

/**
 * 生成头像地址
 * */
const avatar = computed(() => {
  if (userInfo) {
    return baseFilePath + userInfo.avatar
  } else {
    return null
  }
})

const renderIcon = (icon: Component) => {
  return () => {
    return h(NIcon, {size: 'large'}, {
      default: () => h(icon)
    })
  }
}
const options = [
  {
    label: '回到首页',
    path: '/',
    icon: renderIcon(Home)
  },
  {
    label: '个人中心',
    path: "/sys/profile",
    icon: renderIcon(AccountCircle)
  },
  {
    label: '功能管理',
    path: "/manage/alarmEvent",
    menuId: 'mu-1',
    icon: renderIcon(Cog)
  },
  {
    label: '退出系统',
    path: "logout",
    icon: renderIcon(Logout)
  },
]

function handleSelect(key: string, option: DropdownOption) {
  if (key === 'logout') {
    authStore.logout()
  } else {
    if (option.menuId) {
      router.push({path: key, query: {title: option.label as string, menuId: option.menuId as string}})
    } else {
      router.push({path: key, query: {title: option.label as string}})
    }
  }

}
</script>

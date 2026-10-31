<template>
  <n-dropdown
      @mouseenter="$emit('mouseEnter')"
      @mouseleave="$emit('mouseLeave')"
      placement="bottom"
      key-field="path"
      :options="options" @select="handleSelect">
    <div class="flex flex-center text-nowrap">
      <n-avatar
          round
          size="small"
          class="mr-2"
          v-if="avatar"
          :src="avatar"
          :fallback-src="emptyAvatar"
      />
      <n-ellipsis style="max-width: 80px;">
        {{ userInfo?.name }}
      </n-ellipsis>
    </div>
  </n-dropdown>
</template>
<script lang="ts" setup>
import {useAuthStore} from '@/store';
import emptyAvatar from '../../assets/images/empty.png'
import {Component, computed, h} from 'vue';
import {DropdownOption, NIcon} from 'naive-ui';
import Home from '~icons/mdi/home'
import Logout from '~icons/mdi/logout'
import Cog from '~icons/mdi/cog'
import AccountCircle from '~icons/mdi/account-circle'
import {useRouter} from 'vue-router';
import {baseFilePath} from '@/utils/path.ts';

const authStore = useAuthStore()
const userInfo = authStore.getUserInfo
const router = useRouter()
const emits = defineEmits(['mouseEnter', 'mouseLeave'])
/**
 * 生成头像地址
 * */
const avatar = computed(() => {
  if (userInfo && userInfo.avatar) {
    return baseFilePath + userInfo.avatar + '?w=120'
  } else {
    return emptyAvatar
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
    path: '/sys/profile',
    icon: renderIcon(AccountCircle)
  },
  {
    label: '退出系统',
    path: 'logout',
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

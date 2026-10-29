<template>
  <n-form class="wrap-query-form" v-bind="$attrs">
    <div class="left">
      <slot></slot>
    </div>
    <template #right>
      <div class="right">
        <n-button
          type="info"
          @click="queryOverflow=!queryOverflow"
          icon-placement="right">
          <template #icon>
            <n-icon size="14">
              <IconMdiArrowDown/>
            </n-icon>
          </template>
          {{ queryOverflow ? '展开' : '收起' }}
        </n-button>
      </div>
    </template>
  </n-form>
</template>
<script lang="ts" setup>
import {NButton, NIcon} from 'naive-ui';
import {ref} from 'vue';

const queryOverflow = ref(false)

</script>
<style>
.wrap-query-form {
  display: flex;
  align-items: flex-start;
}

.wrap-query-form .left {
  flex: 1;
}

</style>

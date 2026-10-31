<template>
  <div v-if="show" class="datav-context-menu" :style="{ left: `${x}%`, top: `${y}%` }" @pointerdown.stop>
    <button type="button" @click="emit('front')">置于顶层</button>
    <button type="button" @click="emit('forward')">上移一层</button>
    <button type="button" @click="emit('backward')">下移一层</button>
    <button type="button" @click="emit('back')">置于底层</button>
  </div>
</template>

<script setup lang="ts">
defineProps<{ show: boolean; x: number; y: number }>();
const emit = defineEmits<{ front: []; forward: []; backward: []; back: [] }>();
</script>

<style scoped lang="scss">
.datav-context-menu {
  position: absolute;
  z-index: 100001;
  display: flex;
  width: 128px;
  flex-direction: column;
  overflow: hidden;
  padding: 5px;
  background: var(--datav-primary, #003867);
  border: 1px solid var(--datav-accent, #0095ff);
  border-radius: 5px;
  box-shadow: 0 8px 24px rgb(0 0 0 / 45%);

  button {
    height: 32px;
    padding: 0 12px;
    color: #e8f7ff;
    text-align: left;
    background: transparent;
    border: 0;
    border-radius: 3px;
    cursor: pointer;

    &:hover {
      color: #fff;
      background: var(--datav-accent-soft, rgb(0 149 255 / 35%));
    }
  }
}
</style>

<template>
  <n-form-item label="NEZ坐标" v-bind="$attrs">
    <n-input-group>
      <n-input-group-label>N</n-input-group-label>
      <n-input v-model:value="form.n" placeholder="请输入N坐标"></n-input>
      <n-input-group-label>E</n-input-group-label>
      <n-input v-model:value="form.e" placeholder="请输入E坐标"></n-input>
      <n-input-group-label>Z</n-input-group-label>
      <n-input v-model:value="form.z" placeholder="请输入Z坐标"></n-input>
    </n-input-group>
  </n-form-item>
</template>
<script lang="ts" setup>
import { computed, ref } from 'vue';

const props = defineProps({
  value: { type: String },
});

const form = ref({
  n: '',
  e: '',
  z: '',
});

const emit = defineEmits(['update:value']);
const value = computed({
  get() {
    let v = props.value?.split(',');
    if (v && v.length === 3) {
      form.value.n = v[0];
      form.value.e = v[1];
      form.value.z = v[2];
    }

    const { n, e, z } = form.value;
    return `${n},${e},${z}`;
  },
  set(value) {
    const { n, e, z } = form.value;
    let r = `${n},${e},${z}`;
    emit('update:value', r);
  },
});
</script>

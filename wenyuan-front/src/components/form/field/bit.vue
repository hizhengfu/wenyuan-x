<template>
  <n-flex class="bit-wrap">
    <n-checkbox
      :round="false"
      :checked-value="1"
      :unchecked-value="0"
      v-for="item in bitList"
      :label="item.label"
      :default-checked="item.value"
      @update:checked="
        (checked: number) => {
          handleToggle(item, checked);
        }
      "
    ></n-checkbox>
  </n-flex>
</template>
<script lang="ts" setup>
import { ref } from 'vue';

const props = defineProps({
  value: { type: String },
  len: { type: Number, default: 8 },
});

const emits = defineEmits(['update:value']);

const bitList = ref<{ label: string; value: number }[]>();

/**
 * 切换事件
 * */
function handleToggle(item: { label: string; value: number }, checked: number) {
  item.value = checked;
  let temp = bitList.value
    ?.map((item) => {
      return item.value;
    })
    .join('');
  if (temp) {
    emits('update:value', temp);
  }
}

// watch(
//   () => props.value,
//   (value) => {
//     if (value) {
//       value.split('').forEach((v, i) => {
//         if (bitList.value) {
//           console.log(11, value, v, i);
//           bitList.value[i].value = Number(v);
//         }
//       });
//     }
//   },
// );

function init() {
  const { len, value } = props;
  let valueList = value?.split('');
  let temp: { label: string; value: number }[] = [];
  for (let i = 0; i < len; i++) {
    if (valueList && valueList.length >= i) {
      console.log(22, len, valueList?.length, Number(valueList[i]));
      temp.push({ label: 'D' + i, value: Number(valueList[i]) });
    } else {
      temp.push({ label: 'D' + i, value: 0 });
    }
  }
  bitList.value = temp;
}

init();
</script>
<style lang="scss" scoped>
.bit-wrap {
  border: 1px solid var(--n-border-color);
  padding: 3px 6px;
}
</style>

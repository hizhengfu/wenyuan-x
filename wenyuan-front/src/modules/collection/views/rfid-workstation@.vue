<template>
  <RfidDesk standalone />
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import RfidDesk from './rfid-desk.vue';

export default defineComponent({
  components: { RfidDesk },
  setup() {
    return { meta: { title: 'RFID 工作台' } };
  },
});
</script>

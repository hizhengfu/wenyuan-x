import { ref } from 'vue';
import { defineStore } from 'pinia';

export const usePortalStore = defineStore('portal-store', () => {
  const params = ref({});

  function initialize(v: object) {
    params.value = v || {};
  }

  function update(v: object) {
    Object.assign(params.value, v);
  }

  return {
    params,
    initialize,
    update,
  };
});

import {defineStore} from 'pinia';
import {ref} from 'vue'

/**
 * 四有档案store
 * */
export const useFormStore = defineStore('four-store', () => {
  /**
   * 四有档案id
   * */
  const fourCulturalRecordId = ref()


  /**
   * 改
   * */
  function changeFourCulturalRecordId(id: string) {
    fourCulturalRecordId.value = id
  }

  return {fourCulturalRecordId, changeFourCulturalRecordId}
});


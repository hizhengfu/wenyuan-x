import {defineAsyncComponent} from 'vue'

const HChart = defineAsyncComponent(() =>
  import('./EChart.vue')
)

export {HChart}

<template>
  <div class="tabs-container">
    <n-tabs v-model:value="name" type="card" class="full-height-tabs">
      <n-tab-pane
        v-for="tab in activeTabs"
        :key="tab.name"
        :name="tab.name"
        :tab="tab.label"
        class="full-height-pane"
      >
        <component :is="tab.component" v-bind="tab.props" />
      </n-tab-pane>
    </n-tabs>
  </div>
</template>

<script lang="ts" setup>
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import ThirdView from '@/modules/archive/components/third/ThirdView.vue'
import FourView from '@/modules/archive/components/four/fileDetail.vue'
import fourthView from '@/modules/archive/components/fourth/FourthView.vue'
import CulturalBasicDetail from '@/modules/archive/components/basic/CulturalBasicDetail.vue'
import { list as twoWireList } from '@/modules/archive/api/basic/twowire'
import { hasProtectPlans } from '@/modules/archive/api/basic/protectPlan'
import { list as archiveDigitalsList } from '@/modules/monitor/api/archive/digital'
import { tree as videoTree } from '@/platforms/jiangxi/api/basic'
import { list as fireAlarmList } from '@/platforms/jiangxi/api/event/fireAlarm'
import { list as thunderAlarmList } from '@/platforms/jiangxi/api/event/thunderAlarm'

const TwoLinesView = defineAsyncComponent(() => import('@/platforms/jiangxi/components/widgets/lxfw/index.vue'))
const DigitalView = defineAsyncComponent(() => import('@/platforms/jiangxi/components/widgets/szh/index.vue'))
const ProtectPlanView = defineAsyncComponent(() => import('@/platforms/jiangxi/components/widgets/protectPlan/index.vue'))
const VideoView = defineAsyncComponent(() => import('@/platforms/jiangxi/components/widgets/videoView/index.vue'))
const FireAlarmView = defineAsyncComponent(() => import('@/platforms/jiangxi/components/commonInfo/fireAlarmList.vue'))
const ThunderAlarmView = defineAsyncComponent(
  () => import('@/platforms/jiangxi/components/commonInfo/thunderAlarmList.vue'),
)

const isJiangxiTarget = APP_TARGET === 'jiangxi'

const props = defineProps({
  detail: {
    type: Object,
    default: () => ({})
  }
})

type TabConfig = {
  name: string
  label: string
  component: any
  condition: () => boolean
  order: number
  props: () => Record<string, any>
}

const defaultExtraTabState = {
  twoLines: false,
  digital: false,
  protectPlan: false,
  video: false,
  fireAlarm: false,
  thunderAlarm: false,
}

const extraTabState = ref({ ...defaultExtraTabState })
let detailRequestId = 0

function resetExtraTabState() {
  extraTabState.value = { ...defaultExtraTabState }
}

async function loadExtraTabState(detail: Record<string, any>) {
  const currentRequestId = ++detailRequestId
  resetExtraTabState()

  if (!isJiangxiTarget || !detail?.id) {
    return
  }

  const nextState = { ...defaultExtraTabState }
  const tasks: Promise<void>[] = [
    twoWireList({ culturalRecordId: detail.id })
      .then((resp: any) => {
        nextState.twoLines = Array.isArray(resp?.data?.list) && resp.data.list.length > 0
      })
      .catch(() => {}),
    videoTree({ sourceId: detail.id })
      .then((resp: any) => {
        nextState.video = Array.isArray(resp?.data) && resp.data.length > 0
      })
      .catch(() => {}),
    archiveDigitalsList({ sourceId: detail.id, sourceType: 0 })
      .then((resp: any) => {
        nextState.digital = Array.isArray(resp?.data?.list) && resp.data.list.length > 0
      })
      .catch(() => {}),
    hasProtectPlans(detail.id)
      .then((visible) => {
        nextState.protectPlan = Boolean(detail.archiveProtectPlans) || visible
      })
      .catch(() => {
        nextState.protectPlan = Boolean(detail.archiveProtectPlans)
      }),
  ]

  if (detail.sourceId) {
    tasks.push(
      fireAlarmList({ sourceId: detail.sourceId })
        .then((resp: any) => {
          nextState.fireAlarm = Array.isArray(resp?.data?.list) && resp.data.list.length > 0
        })
        .catch(() => {}),
    )
    tasks.push(
      thunderAlarmList({ sourceId: detail.sourceId })
        .then((resp: any) => {
          nextState.thunderAlarm = Array.isArray(resp?.data?.list) && resp.data.list.length > 0
        })
        .catch(() => {}),
    )
  }

  await Promise.allSettled(tasks)

  if (currentRequestId === detailRequestId) {
    extraTabState.value = nextState
  }
}

const tabConfigs = computed<TabConfig[]>(() => [
  {
    name: 'basic',
    label: '基础数据',
    component: CulturalBasicDetail,
    condition: () => true,
    order: 1,
    props: () => ({
      id: props.detail.id
    })
  },
  {
    name: 'third',
    label: '三普数据',
    component: ThirdView,
    condition: () => props.detail && props.detail.thirdId,
    order: 2,
    props: () => ({
      thirdCulturalRecordId: props.detail.thirdId,
      title: props.detail.publishName
    })
  },
  {
    name: 'four',
    label: '四有档案',
    component: FourView,
    condition: () => props.detail && props.detail.fourId,
    order: 3,
    props: () => ({
      fourCulturalRecordId: props.detail.fourId,
      menuCode:'Fm',
      isInTab: true
    })
  },
  {
    name: 'fourth',
    label: '四普数据',
    component: fourthView,
    condition: () => props.detail && props.detail.fourthId,
    order: 4,
    props: () => ({
      fourthCulturalRecordId: props.detail.fourthId,
      title: props.detail.publishName
    })
  },
  {
    name: 'twoLines',
    label: '两线范围',
    component: TwoLinesView,
    condition: () => isJiangxiTarget && extraTabState.value.twoLines,
    order: 5,
    props: () => ({
      params: {
        infoData: props.detail,
      },
    }),
  },
  {
    name: 'digital',
    label: '数字化',
    component: DigitalView,
    condition: () => isJiangxiTarget && extraTabState.value.digital,
    order: 6,
    props: () => ({
      paramsdata: {
        id: props.detail.id,
      },
    }),
  },
  {
    name: 'protectPlan',
    label: '保护规划',
    component: ProtectPlanView,
    condition: () => isJiangxiTarget && extraTabState.value.protectPlan,
    order: 7,
    props: () => ({
      paramsdata: {
        id: props.detail.id,
      },
      showOperate: false,
      showToolbar: false,
      showHeader: false,
      queryShow: false,
    }),
  },
  {
    name: 'video',
    label: '监控视频',
    component: VideoView,
    condition: () => isJiangxiTarget && extraTabState.value.video,
    order: 8,
    props: () => ({
      params: {
        id: props.detail.id,
      },
    }),
  },
  {
    name: 'fireAlarm',
    label: '消防监测',
    component: FireAlarmView,
    condition: () => isJiangxiTarget && extraTabState.value.fireAlarm,
    order: 9,
    props: () => ({
      sourceId: props.detail.sourceId,
      showHeader: false,
      showOperate: false,
      showToolbar: false,
      showQueryResetButton: true,
    }),
  },
  {
    name: 'thunderAlarm',
    label: '防雷监测',
    component: ThunderAlarmView,
    condition: () => isJiangxiTarget && extraTabState.value.thunderAlarm,
    order: 10,
    props: () => ({
      sourceId: props.detail.sourceId,
      showHeader: false,
      showOperate: false,
      showToolbar: false,
      showQueryResetButton: true,
    }),
  },
])

const activeTabs = computed(() => {
  return tabConfigs.value
    .filter(tab => tab.condition())
    .sort((a, b) => a.order - b.order)
    .map(tab => ({
      ...tab,
      props: tab.props()
    }))
})

const name = ref('basic')

const updateActiveTab = () => {
  if (activeTabs.value.length > 0 && !activeTabs.value.find(tab => tab.name == name.value)) {
    name.value = activeTabs.value[0].name
  }
}

watch(
  () => props.detail,
  (detail) => {
    void loadExtraTabState(detail)
  },
  { immediate: true, deep: true },
)

watch(activeTabs, updateActiveTab, { immediate: true, deep: true })
</script>

<style lang="scss" scoped>
.tabs-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.full-height-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;

  :deep(.n-tabs-content) {
    flex: 1;
    height: 100%;
  }

  :deep(.n-tab-pane) {
    height: 100%;
  }
}

.full-height-pane {
  height: 100%;
}

// 确保子组件也能获得完整高度
:deep(.n-card),
:deep(.n-layout) {
  height: 100%;
}
</style>

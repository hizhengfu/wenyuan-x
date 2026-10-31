<template>
  <div class="biz-cards">
    <n-spin :show="loading">
      <!-- 空状态：所有业务类型都没有待办 -->
      <n-empty v-if="!loading && groups.length === 0" description="暂无待办任务" class="biz-cards__empty">
        <template #icon>
          <n-icon><HIcon icon="mdi:check-circle-outline" /></n-icon>
        </template>
      </n-empty>

      <!-- 卡片网格 -->
      <n-grid v-else :cols="cardCols" :x-gap="12" :y-gap="12" responsive="screen">
        <n-gi v-for="(g, idx) in groups" :key="g.businessType">
          <n-card
            class="biz-cards__card"
            :class="`biz-cards__card--${colorOf(idx)}`"
            hoverable
            size="small"
            @click="onCardClick(g)"
          >
            <div class="biz-cards__row">
              <div class="biz-cards__icon">
                <n-icon :size="32">
                  <HIcon :icon="g.icon || 'mdi:clipboard-text-outline'" />
                </n-icon>
              </div>
              <div class="biz-cards__info">
                <div class="biz-cards__title" :title="g.businessTypeName">
                  {{ g.businessTypeName }}
                </div>
                <div class="biz-cards__sub">
                  最近：{{ formatTime(g.latestTaskTime) }}
                </div>
              </div>
              <div class="biz-cards__count">
                <span class="biz-cards__count-num">{{ g.count }}</span>
                <span class="biz-cards__count-label">待办</span>
              </div>
            </div>
          </n-card>
        </n-gi>
      </n-grid>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, onMounted, ref, watch, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import HIcon from '@/components/basic/DynamicIcon.vue'
import { getTodoGroupByBusinessType } from '../../api/task'
import type { TodoBusinessTypeGroupVo } from '../../typings/flow'

const router = useRouter()

const loading = ref(false)
const groups = ref<TodoBusinessTypeGroupVo[]>([])

const cardCols = computed(() => 'xs:1 s:2 m:3 l:4 xl:5')

/** 6 色循环：与 StatisticsPanel 的色板保持一致 */
const PALETTE = ['blue', 'green', 'cyan', 'orange', 'purple', 'red'] as const
function colorOf(index: number): string {
  return PALETTE[index % PALETTE.length]
}

function formatTime(t?: string): string {
  if (!t) return '-'
  const d = dayjs(t)
  // 当天用相对时间，更直观
  if (d.isSame(dayjs(), 'day')) return `今日 ${d.format('HH:mm')}`
  return d.format('MM-DD HH:mm')
}

/** 点击卡片：优先使用后端给的 routePath；缺省时跳到 todo Tab 并带 businessType 过滤 */
function onCardClick(g: TodoBusinessTypeGroupVo) {
  if (g.routePath) {
    router.push(g.routePath)
    return
  }
  router.push({
    path: '/flow/task',
    query: {
      tab: 'todo',
      businessType: g.businessType,
      // 同时带上中文名，TodoPane 用于展示筛选 chip（用户也可能直接复制链接进入）
      businessTypeName: g.businessTypeName,
    },
  })
}

async function load() {
  loading.value = true
  try {
    const res = await getTodoGroupByBusinessType()
    const data = (res.data?.data ?? res.data ?? []) as TodoBusinessTypeGroupVo[]
    groups.value = Array.isArray(data) ? data : []
  } catch {
    groups.value = []
  } finally {
    loading.value = false
  }
}

// 响应任务中心 Tab 栏的"全局刷新"按钮（独立访问时 inject 为 null）
const refreshSignal = inject<Ref<number> | null>('flowTaskRefresh', null)
if (refreshSignal) {
  watch(refreshSignal, () => load())
}

onMounted(load)

defineExpose({ load })
</script>

<style scoped lang="scss">
.biz-cards {
  padding: 12px;

  &__empty {
    padding: 80px 0;
  }

  &__card {
    cursor: pointer;
    transition: transform 0.15s ease, box-shadow 0.15s ease;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    }
  }

  &__row {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__icon {
    width: 56px;
    height: 56px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    background: rgba(32, 128, 240, 0.1);
    color: #2080f0;
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
    line-height: 1.3;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__sub {
    margin-top: 4px;
    font-size: 12px;
    color: #999;
  }

  &__count {
    flex-shrink: 0;
    text-align: right;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
  }

  &__count-num {
    font-size: 24px;
    font-weight: 700;
    line-height: 1;
    color: #d03050;
  }

  &__count-label {
    margin-top: 2px;
    font-size: 12px;
    color: #999;
  }

  // 6 色：与 StatisticsPanel 风格一致
  &__card--blue &__icon {
    background: rgba(32, 128, 240, 0.1);
    color: #2080f0;
  }
  &__card--green &__icon {
    background: rgba(24, 160, 88, 0.1);
    color: #18a058;
  }
  &__card--cyan &__icon {
    background: rgba(24, 144, 255, 0.1);
    color: #1890ff;
  }
  &__card--orange &__icon {
    background: rgba(240, 160, 32, 0.1);
    color: #f0a020;
  }
  &__card--purple &__icon {
    background: rgba(144, 88, 192, 0.1);
    color: #9058c0;
  }
  &__card--red &__icon {
    background: rgba(208, 48, 80, 0.1);
    color: #d03050;
  }
}
</style>

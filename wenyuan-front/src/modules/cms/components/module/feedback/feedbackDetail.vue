<template>
  <div v-if="item">
    <div style="display: flex; flex-wrap: wrap;">
      <div
        v-for="(question, index) in item.question"
        :key="index"
        :style="{ display: 'flex', lineHeight: '200%', width: isFullWidth(question.title) ? '100%' : '50%' }"
      >
        <div
          v-if="!isTextarea(question.title)"
          style="font-weight: bold"
        >
          {{ question.title }}：
        </div>

        <div :style="{ width: isFullWidth(question.title) ? '100%' : '' }">
          <template v-if="question.type === 0">
            <n-radio-group v-model:value="question.value" :disabled="true">
              <n-radio
                v-for="option in question.option"
                :key="option"
                :label="option"
                :value="option"
              />
            </n-radio-group>
          </template>

          <template v-else-if="question.type === 1">
            <n-checkbox-group
              :value="splitCheckboxValue(question.value)"
              :disabled="true"
            >
              <n-checkbox
                v-for="option in question.option"
                :key="option"
                :label="option"
                :value="option"
              />
            </n-checkbox-group>
          </template>

          <template v-else-if="question.title === '内容' || question.title === '您的意见建议'">
            <n-input
              type="textarea"
              :value="question.value"
              :disabled="true"
              :placeholder="question.title"
              style="width: 100%; font-size: 14px"
              autosize
            />
          </template>

          <template v-else>
            <div style="text-indent: 0px">{{ question.value }}</div>
          </template>
        </div>
      </div>
    </div>

    <div style="line-height: 200%;">
      <span style="font-weight: bold">提交时间：</span>
      {{ formatDateTime(item.createTime) }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { loadQuestionAnswer } from '../../../api/feedback'

// props
const props = defineProps({
  id: {
    type: String,
    required: true
  }
})

const item:any = ref(null)

const isFullWidth = (title:any) => title == '内容' || title == '您的意见建议'
const isTextarea = (title:any) => ['内容', '您的意见建议', '标题'].includes(title)
function splitCheckboxValue  (value:any){
    return value ? value.split(',')  : [] }

const formatDateTime = (val:any) => {
  if (!val) return ''
  const date = new Date(val)
  return date.toLocaleString()
}

// 加载数据
const loadData = async () => {
  if (!props.id) return
  const res = await loadQuestionAnswer({ feedbackAnswerId: props.id })
  if(res&& res.data){
 item.value = res.data
  }
 
}

onMounted(loadData)
watch(() => props.id, loadData)
</script>

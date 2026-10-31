<template>
  <n-card
    :title="title"
    :segmented="{ content: true, footer: 'soft' }"
    :footer-style="{ display: 'flex', justifyContent: 'center', alignItems: 'center' }"
  >
    <template #header-extra>
      <n-space>
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button circle size="small" @click="handleAdd">
              <template #icon>
                <n-icon size="16px">
                  <IconMdiPlus />
                </n-icon>
              </template>
            </n-button>
          </template>
          添加
        </n-tooltip>

        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button circle size="small" @click="handlePreview">
              <template #icon>
                <n-icon size="16px">
                  <IconMdiEye />
                </n-icon>
              </template>
            </n-button>
          </template>
          预览
        </n-tooltip>
      </n-space>
    </template>

    <n-form ref="formRef" :model="formModel" :rules="rules">
      <div v-if="items.length === 0 && !loading" class="empty-state">
        <n-text depth="3">请添加问卷题目</n-text>
      </div>

      <!-- 使用draggable包装collapse -->
      <div v-if="items.length > 0">
        <draggable
          v-model="items"
          item-key="id"
          ghost-class="ghost-item"
          handle=".drag-handle"
        >
          <template #item="{ element: item, index }">
            <n-collapse
              class="collapse-item"
              :default-expanded-names="[item.id]"
              style="margin-bottom: 8px;"
            >
              <n-collapse-item
                :name="item.id"
                :title="item.title || `问题 ${index + 1}`"
              >
                <template #header-extra>
                  <n-space size="small" @click.stop>
                    <n-tooltip trigger="hover">
                      <template #trigger>
                        <n-button
                          size="tiny"
                          circle
                          class="drag-handle"
                          style="cursor: grab;"
                        >
                          <template #icon>
                            <n-icon size="12px">
                              <IconMdiDrag />
                            </n-icon>
                          </template>
                        </n-button>
                      </template>
                      拖动排序
                    </n-tooltip>

                    <n-tooltip trigger="hover">
                      <template #trigger>
                        <n-button
                          size="tiny"
                          circle
                          type="error"
                          @click="handleRemove(index)"
                        >
                          <template #icon>
                            <n-icon size="12px">
                              <IconMdiDelete />
                            </n-icon>
                          </template>
                        </n-button>
                      </template>
                      删除
                    </n-tooltip>
                  </n-space>
                </template>

                <n-space vertical size="medium">
                  <!-- 问题配置区 -->
                  <n-grid :cols="24" :x-gap="12">
                    <n-grid-item :span="12">
                      <n-form-item label="问卷题目" path="title" >
                        <n-input
                          v-model:value="item.title"
                          placeholder="请输入问题标题"
                        />
                      </n-form-item>
                    </n-grid-item>
                    <n-grid-item :span="6">
                      <n-form-item label="必填">
                        <n-switch v-model:value="item.required" />
                      </n-form-item>
                    </n-grid-item>
                    <n-grid-item :span="6">
                      <n-form-item label="题型" >
                        <!-- <n-select
                          v-model:value="item.type"
                          :options="feedbackTypeOptions"
                          placeholder="选择类型"
                        /> -->
                        <h-dict-select v-model:value="item.type" type="cms_feedback_input_type"></h-dict-select>
                      </n-form-item>
                    </n-grid-item>
                  </n-grid>

                  <!-- 预览区域 -->
                  <n-divider title-placement="left">
                    <n-text depth="2">预览</n-text>
                  </n-divider>

                  <!-- 单选/多选选项 -->
                  <div v-if="item.type == 0 || item.type == 1">
                    <n-grid :cols="24" :x-gap="12">
                      <n-grid-item :span="18">
                        <n-space v-if="item.option && item.option.length > 0">
                          <n-tag
                            v-for="(option, optionIndex) in item.option"
                            :key="optionIndex"
                            closable
                            @close="handleRemoveOption(item.option, optionIndex as number)"
                            class="option-tag"
                          >
                            {{ option }}
                          </n-tag>
                        </n-space>
                        <n-text v-else depth="3" class="empty-options">
                          请添加题目选项
                        </n-text>
                      </n-grid-item>
                      <n-grid-item :span="6">
                        <n-input
                          v-model:value="item.addOption"
                          placeholder="添加题目选项"
                          @keyup.enter="handleAddOption(item, item.addOption)"
                          size="small"
                        >
                          <template #suffix>
                            <n-button
                              size="tiny"
                              @click="handleAddOption(item, item.addOption)"
                              :disabled="!item.addOption?.trim()"
                              text
                            >
                              添加
                            </n-button>
                          </template>
                        </n-input>
                      </n-grid-item>
                    </n-grid>
                  </div>

                  <!-- 其他类型预览 -->
                  <template v-else>
                    <n-input
                      v-if="item.type === 3"
                      placeholder="请输入答案"
                      disabled
                    />
                    <n-input-number
                      v-else-if="item.type === 4"
                      placeholder="请输入数字"
                      disabled
                      style="width: 100%;"
                    />
                    <n-input
                      v-else-if="item.type === 5"
                      placeholder="请输入邮箱地址"
                      disabled
                    />
                    <n-input
                      v-else-if="item.type === 7"
                      placeholder="请输入验证码"
                      disabled
                      class="captcha-input"
                    >
                      <template #suffix>
                        <div class="captcha-placeholder">
                          <n-text depth="3" style="font-size: 12px;">验证码</n-text>
                        </div>
                      </template>
                    </n-input>
                    <n-input
                      v-else
                      type="textarea"
                      placeholder="请输入答案"
                      disabled
                      :autosize="{ minRows: 3, maxRows: 5 }"
                    />
                  </template>
                </n-space>
              </n-collapse-item>
            </n-collapse>
          </template>
        </draggable>
      </div>

      <n-button
        @click="handleAdd"
        dashed
        block
        style="margin-top: 16px;"
      >
        <template #icon>
          <n-icon>
            <IconMdiPlus />
          </n-icon>
        </template>
        添加新的问题
      </n-button>
    </n-form>

    <template #footer>
      <n-button
        v-if="items.length > 0"
        type="primary"
        :loading="loading"
        @click="handleSave"
      >
        <template #icon>
          <n-icon>
            <IconMdiPencil />
          </n-icon>
        </template>
        保存
      </n-button>
    </template>
  </n-card>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue'
import draggable from 'vuedraggable'
import { useRoute,useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import {saveQuestion,loadQuestion } from  '../../../api/feedback'

interface FeedbackItem {
  id: string
  title: string
  type: number
  required: boolean
  option?: string[]
  addOption?: string
}

const route = useRoute()
const router = useRouter()
const message = useMessage()
const idValue = ref()
const title = ref('题目配置')
const loading = ref(false)
const formRef = ref()
const formModel = ref({})
const items = ref<FeedbackItem[]>([])


const rules = ref({
  // title: [
  //   { required: true, message: '请输入问题标题', trigger: 'blur' }
  // ]
})

// 生成唯一ID
const generateId = () => {
  return Date.now().toString(36) + Math.random().toString(36).substr(2)
}

// 加载问题数据
const loadQuestions = async (id?: string) => {
  if (!id) {
    items.value = []
    title.value = '题目配置'
    return
  }
  idValue.value = id
 loadQuestion(id).then((resp:any)=>{
  if(resp && resp.data){
    title.value = '题目配置「' + resp.data.title + '」'
    items.value = resp.data.question || []
  }
 })
}

// 添加新问题
const handleAdd = () => {
  const newItem :any= {
    title: '',
    type: 3, // 默认单行文本
    required: false,
    option: []
  }
  items.value.push(newItem)
}

// 删除问题
const handleRemove = (index: number) => {
  items.value.splice(index, 1)
}

// 删除选项
const handleRemoveOption = (options: any, index: number) => {
  options.splice(index, 1)
}

// 添加选项
const handleAddOption = (item: FeedbackItem, value?: string) => {
  if (!item.option) {
    item.option = []
  }
  if (value && value.trim()) {
    const trimmedValue = value.trim()
    if (item.option.includes(trimmedValue)) {
      message.warning('不能添加重复选项')
      return
    }
    item.option.push(trimmedValue)
    item.addOption = ''
  }
}

// 预览
const handlePreview = () => {
  // 实现预览逻辑
  // message.info('预览功能')
router.push({path:'/cms/feedback/preview',query:{id: route.query?.id,title:'问卷预览'}})
}

// 保存
const handleSave = async () => {
  if (items.value.length === 0) {
    message.warning('请至少添加一个问题')
    return
  }

  // 验证必填字段
  for (const item of items.value) {
    if (!item.title || !item.title.trim()) {
      message.error('请填写问题标题')
      return
    }
    // if ((item.type === 0 || item.type === 1) && (!item.option || item.option.length === 0)) {
    //   message.error('单选/多选题请至少添加一个选项')
    //   return
    // }
  }

  loading.value = true

  saveQuestion({question:items.value,id:idValue.value}).finally(()=>{
    loading.value = false
  })

}

// 组件挂载时加载数据
onMounted(() => {
  const id = route.query?.id as string
  loadQuestions(id)
})
</script>

<style lang="scss" scoped>
.empty-state {
  padding: 40px 0;
  text-align: center;
}

.collapse-item {
  border: 1px solid var(--n-border-color);
  border-radius: 6px;

  &:hover {
    border-color: var(--n-border-color-hover);
  }
}

.ghost-item {
  opacity: 0.5;
  background: rgba(24, 160, 88, 0.1);
  border: 2px dashed rgba(24, 160, 88, 0.5);
  border-radius: 6px;
}

.drag-handle {
  cursor: grab;

  &:active {
    cursor: grabbing;
  }
}

.option-tag {
  margin: 2px;
}

.empty-options {
  display: block;
  padding: 8px 0;
}

.captcha-input {
  :deep(.n-input__suffix) {
    .captcha-placeholder {
      width: 60px;
      height: 24px;
      background: #f5f5f5;
      border: 1px solid #d9d9d9;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 2px;
    }
  }
}

// 折叠面板自定义样式
:deep(.n-collapse) {
  .n-collapse-item__header {
    padding: 12px 16px;

    .n-collapse-item__header-main {
      font-weight: 500;
    }
  }

  .n-collapse-item__content-wrapper {
    .n-collapse-item__content-inner {
      padding: 0 16px 16px 16px;
    }
  }
}
</style>

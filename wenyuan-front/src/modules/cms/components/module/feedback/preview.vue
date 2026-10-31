<template>
  <n-card 
    :title="title"
    :segmented="{ content: true, footer: 'soft' }"
    :footer-style="{ display: 'flex', justifyContent: 'center', alignItems: 'center' }"
  >
    <n-form 
      ref="formRef" 
      :model="formModel" 
      :rules="formRules"
      label-placement="top"
      require-mark-placement="right-hanging"
    >
      <n-space vertical size="large">
        <div v-for="(item, index) in items" :key="item.id || index" class="form-item-wrapper">
          <!-- 问题标题 -->
          <n-text tag="div" class="question-title">
            {{ item.title }}
            <n-text v-if="item.required" type="error">*</n-text>
          </n-text>

          <!-- 单选题 -->
          <div v-if="item.type === 0">
            <n-form-item 
              :path="`items.${index}.value`"
              :show-label="false"
              :show-require-mark="false"
            >
              <n-radio-group v-model:value="item.value">
                <n-grid :cols="getRadioColumns(item.option)" :x-gap="12" :y-gap="8">
                  <n-grid-item v-for="option in item.option" :key="option">
                    <n-radio :value="option" :label="option" />
                  </n-grid-item>
                </n-grid>
              </n-radio-group>
            </n-form-item>
          </div>

          <!-- 多选题 -->
          <div v-else-if="item.type === 1">
            <n-form-item 
              :path="`items.${index}.value`"
              :show-label="false"
              :show-require-mark="false"
            >
              <n-checkbox-group v-model:value="item.value">
                <n-grid :cols="getRadioColumns(item.option)" :x-gap="12" :y-gap="8">
                  <n-grid-item v-for="option in item.option" :key="option">
                    <n-checkbox :value="option" :label="option" />
                  </n-grid-item>
                </n-grid>
              </n-checkbox-group>
            </n-form-item>
          </div>

          <!-- 单行文本 -->
          <div v-else-if="item.type === 3">
            <n-form-item 
              :path="`items.${index}.value`"
              :show-label="false"
              :show-require-mark="false"
            >
              <n-input 
                v-model:value="item.value"
                placeholder="请输入答案"
              />
            </n-form-item>
          </div>

          <!-- 数字输入 -->
          <div v-else-if="item.type === 4">
            <n-form-item 
              :path="`items.${index}.value`"
              :show-label="false"
              :show-require-mark="false"
            >
              <n-input-number 
                v-model:value="item.value"
                placeholder="请输入数字"
                style="width: 100%;"
              />
            </n-form-item>
          </div>

          <!-- 邮箱输入 -->
          <div v-else-if="item.type === 5">
            <n-form-item 
              :path="`items.${index}.value`"
              :show-label="false"
              :show-require-mark="false"
            >
              <n-input 
                v-model:value="item.value"
                placeholder="请输入邮箱地址"
               
              />
            </n-form-item>
          </div>

        

          <!-- 验证码 -->
          <div v-else-if="item.type === 7">
            <n-form-item 
              :path="`items.${index}.value`"
              :show-label="false"
              :show-require-mark="false"
            >
               <h-field-captcha ref="refFieldCaptcha" v-model:value="item.value" :form="items"></h-field-captcha>
            </n-form-item>
          </div>

          <!-- 多行文本（默认） -->
          <div v-else>
            <n-form-item 
              :path="`items.${index}.value`"
              :show-label="false"
              :show-require-mark="false"
            >
              <n-input 
                v-model:value="item.value"
                type="textarea"
                placeholder="请输入答案"
                :autosize="{ minRows: 3, maxRows: 6 }"
              />
            </n-form-item>
          </div>
        </div>
      </n-space>

      <!-- 空状态 -->
      <div v-if="items.length === 0 && !loading" class="empty-state">
        <n-empty description="暂无表单内容" />
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <n-spin size="large">
          <template #description>
            加载中...
          </template>
        </n-spin>
      </div>
    </n-form>

    <template #footer>
      <n-button 
        v-if="items.length > 0"
        type="primary" 
        size="large"
        :loading="saving"
        @click="handleSave"
      >
        <template #icon>
          <n-icon>
            <IconMdiPencil />
          </n-icon>
        </template>
        提交答卷
      </n-button>
    </template>
  </n-card>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { FormItemRule, UploadFileInfo } from 'naive-ui'
import { loadQuestion,saveQuestionAnswer} from '../../../api/feedback'
import HFieldCaptcha from '@/modules/sys/components/form/field/captcha/index.vue'
const router = useRouter()

interface FeedbackItem {
  id?: string
  title: string
  type: number
  required: boolean
  option?: string[]
  value?: any
  fileList?: UploadFileInfo[]
}

interface FormModel {
  items: FeedbackItem[]
}

const route = useRoute()
const message = useMessage()

const title = ref('问卷预览')
const loading = ref(false)
const saving = ref(false)
const formRef = ref()
const items = ref<FeedbackItem[]>([])
const mockDatas =ref()

// 表单模型
const formModel = computed<FormModel>(() => ({
  items: items.value
}))

// 动态生成表单验证规则
const formRules = computed(() => {
  const rules: Record<string, FormItemRule[]> = {}
  
  items.value.forEach((item, index) => {
    if (item.required) {
      const fieldPath = `items.${index}.value`
      
      if (item.type === 0) {
        // 单选题
        rules[fieldPath] = [
          { required: true, message: `请选择${item.title}`, trigger: ['change', 'blur'] }
        ]
      } else if (item.type === 1) {
        // 多选题
        rules[fieldPath] = [
          { 
            required: true, 
            type: 'array', 
            min: 1,
            message: `请至少选择一个${item.title}的选项`, 
            trigger: ['change', 'blur'] 
          }
        ]
      } else if (item.type === 4) {
        // 数字
        rules[fieldPath] = [
          { required: true, type: 'number', message: `请输入${item.title}`, trigger: ['blur', 'change'] }
        ]
      } else if (item.type === 5) {
        // 邮箱
        rules[fieldPath] = [
          { required: true, message: `请输入${item.title}`, trigger: 'blur' },
          { type: 'email', message: '请输入有效的邮箱地址', trigger: ['blur', 'change'] }
        ]
      } else {
        // 文本类
        rules[fieldPath] = [
          { required: true, message: `请输入${item.title}`, trigger: 'blur' }
        ]
      }
    }
  })
  
  return rules
})

// 根据选项数量决定单选/多选的列数
const getRadioColumns = (options?: string[]) => {
  if (!options || options.length <= 2) return 2
  if (options.length <= 4) return 2
  return 3
}





// 加载问题数据
const loadQuestions = async (id?: string) => {
  if (!id) {
    items.value = []
    title.value = '问卷预览'
    return
  }

  loading.value = true

  const resp =await loadQuestion(id).finally(()=>loading.value=false)
   if(resp && resp.data){
mockDatas.value =resp.data
  title.value = `预览问卷「${mockDatas.value.title}」`
    items.value = mockDatas.value.question.map((item: any) => {
      // 初始化value
      if (item.type === 1) {
        // 多选题初始化为空数组
        item.value = []
      } else if (item.type === 4) {
        // 数字类型初始化为null
        item.value = null
      }  else {
        // 其他类型初始化为空字符串
        item.value = ''
      }
      
      return item
    })
   }else{
    mockDatas.value = {}
   }


  
  
 
}

// 保存表单数据
const handleSave = async () => {
 
    // 表单验证
    await formRef.value?.validate()
    
    saving.value = true
    
    // 处理提交数据
    const submitData = {
      id: route.query.id,
        uuid: mockDatas.value.uuid,
        title: mockDatas.value.title,
      question: items.value.map((item) => {
        const submitItem = { ...item }
        
        if (item.type === 1) {
          // 多选题将数组转为逗号分隔的字符串
          submitItem.value = Array.isArray(item.value) ? item.value.join(',') : item.value
        }
        
        // 移除不需要的字段
        delete submitItem.fileList
        return submitItem
      })
    }
    
    await saveQuestionAnswer(submitData).finally(()=>saving.value=false)
     router.push({path:'/cms/feedback/answerList', query:{id:route.query.id,title:'答卷管理'}})
    
   
}

// 组件挂载时加载数据
onMounted(() => {
  const id = route.query?.id as string
  loadQuestions(id)
})

// 监听路由变化
watch(
  () => route.query.id,
  (newId) => {
    if (newId !== route.query.id) {
      loadQuestions(newId as string)
    }
  }
)
</script>

<style lang="scss" scoped>
.form-item-wrapper {
  .question-title {
    font-size: 16px;
    font-weight: 500;
    margin-bottom: 12px;
    display: block;
    line-height: 1.4;
  }
}

.empty-state {
  padding: 60px 0;
  text-align: center;
}

.loading-state {
  padding: 60px 0;
  text-align: center;
}

.captcha-image {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  
  &:hover {
    opacity: 0.8;
  }
}

// 表单项间距调整
:deep(.n-form-item) {
  margin-bottom: 8px;
  
  .n-form-item-feedback-wrapper {
    min-height: 24px;
  }
}

// 单选框和多选框组样式
:deep(.n-radio-group),
:deep(.n-checkbox-group) {
  width: 100%;
  
  .n-radio,
  .n-checkbox {
    margin: 0;
    width: 100%;
    
    .n-radio__label,
    .n-checkbox__label {
      padding-left: 8px;
      word-break: break-all;
      line-height: 1.4;
    }
  }
}

// 文件上传样式
:deep(.n-upload) {
  width: 100%;
}

// 响应式调整
@media (max-width: 768px) {
  .form-item-wrapper {
    .question-title {
      font-size: 14px;
    }
  }
}
</style>
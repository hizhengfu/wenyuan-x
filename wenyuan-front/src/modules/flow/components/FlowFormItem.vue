<template>
  <n-form-item v-if="!isHidden" :label="label" :path="field" :rule="resolvedRule">
    <slot :disabled="isDisabled" />
  </n-form-item>
</template>

<script setup lang="ts">
import { inject, computed } from 'vue'
import type { FieldPerm } from '../typings/flow'

/**
 * 流程表单字段容器
 *
 * 配合 useFlowForm 使用，自动处理字段权限，布局由外层 n-grid + n-gi 负责。
 *
 * - HIDDEN   → 字段不渲染（v-if）
 * - READONLY → slot 收到 disabled=true
 * - REQUIRED → 自动注入 required 校验规则
 * - EDITABLE → 正常可编辑
 *
 * @example
 * ```vue
 * <n-gi>
 *   <FlowFormItem field="name" label="名称" v-slot="{ disabled }">
 *     <n-input v-model:value="inner.name" :disabled="disabled" />
 *   </FlowFormItem>
 * </n-gi>
 * ```
 */
const props = defineProps<{
  field: string
  label: string
  /** 自定义必填提示（默认：请输入 + label） */
  message?: string
  /** 自定义校验规则（优先级高于内置 required 规则） */
  rule?: Record<string, any> | Record<string, any>[]
}>()

// 由父表单 useFlowForm 通过 provide('flowPerm', perm) 注入
const getPerm = inject<(field: string) => FieldPerm>('flowPerm')

const p = computed<FieldPerm>(() => getPerm?.(props.field) ?? 'EDITABLE')

const isHidden = computed(() => p.value === 'HIDDEN')
const isDisabled = computed(() => p.value === 'READONLY')
const isRequired = computed(() => p.value === 'REQUIRED')

const resolvedRule = computed(() => {
  if (props.rule) return props.rule
  if (isRequired.value) return { required: true, message: props.message ?? `请输入${props.label}` }
  return undefined
})
</script>

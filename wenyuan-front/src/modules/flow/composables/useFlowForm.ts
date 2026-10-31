import { ref, computed, watch, provide } from 'vue'
import type { FieldPermItem, FlowFormProps, FieldPerm } from '../typings/flow'

/**
 * 业务 FlowForm.vue 公共逻辑 composable
 *
 * 负责内部数据同步和字段权限计算，通过 provide('flowPerm') 向
 * FlowFormItem 组件注入权限函数，模板无需直接调用权限函数。
 *
 * @example
 * ```vue
 * <template>
 *   <n-form ref="formRef" :model="inner" label-width="90px">
 *     <n-grid :cols="2" :x-gap="16">
 *       <n-gi>
 *         <FlowFormItem field="name" label="名称" v-slot="{ disabled }">
 *           <n-input v-model:value="inner.name" :disabled="disabled" />
 *         </FlowFormItem>
 *       </n-gi>
 *       <n-gi :span="2">
 *         <FlowFormItem field="content" label="内容" v-slot="{ disabled }">
 *           <n-input v-model:value="inner.content" type="textarea" :disabled="disabled" />
 *         </FlowFormItem>
 *       </n-gi>
 *     </n-grid>
 *   </n-form>
 * </template>
 *
 * <script setup lang="ts">
 * const props = defineProps<FlowFormProps>()
 * const emit = defineEmits(['update:formData'])
 * const { inner, formRef, validate } = useFlowForm(props, emit)
 * defineExpose({ validate })
 * </script>
 * ```
 */
export function useFlowForm(
  props: FlowFormProps,
  emit?: (event: 'update:formData', value: Record<string, any>) => void,
) {
  const formRef = ref<any>()

  // 内部可编辑副本，双向同步
  const inner = ref<Record<string, any>>({ ...(props.formData ?? {}) })

  watch(
    () => props.formData,
    (val) => {
      if (val === inner.value) return
      if (val) inner.value = { ...val }
    },
  )

  watch(inner, (val) => emit?.('update:formData', val), { deep: true })

  // ─── 字段权限 ─────────────────────────────────────────────

  const permMap = computed<Record<string, FieldPerm>>(() => {
    const map: Record<string, FieldPerm> = {}
    props.fieldPerms?.forEach((p: FieldPermItem) => {
      map[p.field] = p.perm
    })
    return map
  })

  /** 获取字段有效权限（view 模式下全部只读） */
  function perm(field: string): FieldPerm {
    if (props.task?.businessFormNotice) {
      const root = field.split(/[.\[]/, 1)[0]
      const parent = permMap.value[root]
      const effective = parent === 'HIDDEN' || parent === 'READONLY'
        ? parent : (permMap.value[field] ?? (permMap.value['*'] === 'HIDDEN' ? 'HIDDEN' : 'READONLY'))
      if (effective === 'HIDDEN') return 'HIDDEN'
      return props.mode === 'view' ? 'READONLY' : effective
    }
    if (props.mode === 'view') return 'READONLY'
    return permMap.value[field] ?? 'EDITABLE'
  }

  // 向 FlowFormItem 注入权限函数，无需在模板中逐字段传参
  provide('flowPerm', perm)

  // ─── 校验 ─────────────────────────────────────────────────

  async function validate(): Promise<Record<string, any>> {
    await formRef.value?.validate()
    if (props.task?.businessFormNotice) {
      return Object.fromEntries(Object.entries(inner.value).filter(([key]) => ['EDITABLE', 'REQUIRED'].includes(perm(key))))
    }
    return inner.value
  }

  return { inner, formRef, validate, perm }
}

<template>
  <NModal v-model:show="visible" preset="card" title="现场交接并执行出库" style="width: 620px">
    <NAlert type="warning" :bordered="false" style="margin-bottom: 14px">
      审批通过仅代表准予出库。请完成双人核对、现场交接和凭证签署后再执行。
    </NAlert>
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="120">
      <NFormItem label="出库前完残状况" path="conditionNote">
        <NInput v-model:value="form.conditionNote" type="textarea" :rows="3" placeholder="记录完残状况及与既有档案的比对结论" />
      </NFormItem>
      <NFormItem label="多角度状况照片" path="conditionPhotoIds">
        <HUploadInput v-model:value="form.conditionPhotoIds" @upload-finish="form.conditionPhotoIds = $event.id" clearable placeholder="上传正面、背面、细节等多角度照片" />
      </NFormItem>
      <NFormItem label="签署出库凭证" path="signedVoucherAttachmentIds">
        <HUploadInput v-model:value="form.signedVoucherAttachmentIds" @upload-finish="form.signedVoucherAttachmentIds = $event.id" clearable placeholder="上传双方签署后的出库凭证扫描件" />
      </NFormItem>
    </NForm>
    <template #footer>
      <NSpace justify="end">
        <NButton @click="visible = false">取消</NButton>
        <NButton type="primary" :loading="submitting" @click="submit">确认执行出库</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<script setup lang="ts">
import { validateFormSafely } from '../utils/formValidation';
import { reactive, ref } from 'vue';
import { useMessage, type FormInst, type FormRules } from 'naive-ui';
import type { CollectionOutboundExecutionVo } from '../typings/collection';

const props = defineProps<{ executeApi: (id: string, data: CollectionOutboundExecutionVo) => Promise<any> }>();
const emit = defineEmits<{ (event: 'success'): void }>();
const message = useMessage();
const visible = ref(false);
const submitting = ref(false);
const formRef = ref<FormInst | null>(null);
const targetId = ref('');
const form = reactive<CollectionOutboundExecutionVo>({});
const rules: FormRules = {
  conditionNote: { required: true, message: '请填写出库前完残状况', trigger: ['blur', 'input'] },
  conditionPhotoIds: { required: true, message: '请上传多角度状况照片', trigger: ['change', 'blur'] },
  signedVoucherAttachmentIds: { required: true, message: '请上传签署出库凭证', trigger: ['change', 'blur'] },
};

function open(id: string) {
  targetId.value = id;
  Object.assign(form, { conditionNote: undefined, conditionPhotoIds: undefined, signedVoucherAttachmentIds: undefined });
  visible.value = true;
}

async function submit() {
  if (!(await validateFormSafely(formRef.value))) return;
  submitting.value = true;
  try {
    await props.executeApi(targetId.value, { ...form });
    message.success('现场交接完成，已生成出库流水');
    visible.value = false;
    emit('success');
  } finally {
    submitting.value = false;
  }
}

defineExpose({ open });
</script>

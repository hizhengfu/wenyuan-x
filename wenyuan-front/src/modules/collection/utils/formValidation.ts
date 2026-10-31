/**
 * naive-ui NForm.validate() 校验失败时 reject 一个错误数组（控制台显示 pageerror「Array(1)」）。
 * 错误已由表单项就地提示，这里转成布尔值，调用方 `if (!(await validateFormSafely(ref.value))) return;`。
 * 没有表单实例时视为通过，与原来 `await ref.value?.validate()` 的行为一致。
 */
export async function validateFormSafely(form: { validate: (...args: any[]) => Promise<unknown> } | null | undefined) {
  if (!form || typeof form.validate !== 'function') return true;
  try {
    await form.validate();
    return true;
  } catch (error) {
    // 校验失败（错误数组 / undefined）只返回 false；其他运行时异常照常抛出，避免吞掉程序错误。
    if (error === undefined || Array.isArray(error)) return false;
    throw error;
  }
}

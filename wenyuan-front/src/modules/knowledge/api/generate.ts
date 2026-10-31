import request from '@/utils/request'

/**
 * 上传文件，AI 自动提取知识图谱草稿
 * @param generateNow true=立即触发AI提取；false=仅保存文件
 */
export function generateFromFile(formData: FormData, generateNow = true) {
  formData.append('generateNow', String(generateNow))
  return request({
    url: 'knowledge/generate/from-file',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 手动触发草稿的 AI 提取（PENDING / FAILED 状态）
 */
export function triggerGenerate(id: string) {
  return request({
    url: `knowledge/generate/${id}/generate`,
    method: 'post'
  })
}

/**
 * 获取草稿详情（含解析后的节点和关系列表）
 */
export function getDraft(id: string) {
  return request({
    url: 'knowledge/generate/' + id,
    method: 'get'
  })
}

/**
 * 获取当前用户草稿列表
 */
export function listDrafts(params?: { fileName?: string; status?: string; startDate?: string; endDate?: string }) {
  return request({
    url: 'knowledge/generate/list',
    method: 'get',
    params,
  })
}

/**
 * 确认草稿入库
 * @param id              草稿 ID
 * @param editedNodes     编辑后的节点 JSON 字符串（可选）
 * @param editedRelations 编辑后的关系 JSON 字符串（可选）
 * @param defaultCategoryId 默认分类 ID（可选）
 */
export function confirmDraft(
  id: string,
  editedNodes?: string,
  editedRelations?: string,
  defaultCategoryId?: string
) {
  return request({
    url: `knowledge/generate/${id}/confirm`,
    method: 'post',
    data: { editedNodes, editedRelations, defaultCategoryId }
  })
}

/**
 * 丢弃草稿（PENDING / FAILED / DONE 可丢弃）
 */
export function discardDraft(id: string) {
  return request({
    url: `knowledge/generate/${id}/discard`,
    method: 'post'
  })
}

/**
 * 强制重置：把卡在 PROCESSING / PENDING 的草稿打回 FAILED（运行态手动解卡，无需重启）
 */
export function resetDraft(id: string) {
  return request({
    url: `knowledge/generate/${id}/reset`,
    method: 'post'
  })
}

/**
 * 删除草稿记录及文件（仅 CONFIRMED / DISCARDED 状态）
 */
export function deleteDraft(id: string) {
  return request({
    url: `knowledge/generate/${id}`,
    method: 'post'
  })
}

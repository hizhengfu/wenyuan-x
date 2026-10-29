import request from '@/utils/request'

export function listProtectPlans(params: Record<string, any>) {
  return request({
    url: 'heritage/protect/plan/list',
    params,
    method: 'get'
  })
}

export async function hasProtectPlans(sourceId?: string) {
  if (!sourceId) {
    return false
  }

  try {
    const resp: any = await listProtectPlans({
      sourceId,
      pageNum: 1,
      pageSize: 1
    })

    return Array.isArray(resp?.data?.list) && resp.data.list.length > 0
  } catch (error) {
    return false
  }
}

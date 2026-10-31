import request from '@/utils/request.ts';

export function frontFileList(id: string) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/file/content/${id}`,
    method: 'get'
  })
}

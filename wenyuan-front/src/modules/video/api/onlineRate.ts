import request from '@/utils/request'
import { pick} from 'lodash-es'
// 不可移动文物视频在线率list
export function basicVideoOnlineStat(data: any) {
  return request({
    url: 'cultural/basic/videoOnlineStat',
   
    method: 'post',
    params:pick(data,['pageSize','pageNum','sortBy','sortDesc']),
     data
  })
}
//博物馆视频在线率
export function museumVideoOnlineStat(data: any) {
  return request({
    url: 'cultural/museum/videoOnlineStat',
 
    method: 'post',
        params:pick(data,['pageSize','pageNum','sortBy','sortDesc']),
      data
  })
}
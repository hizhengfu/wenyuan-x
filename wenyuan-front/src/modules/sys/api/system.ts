import request from '@/utils/request';

export function info() {
  return request({
    url: 'system/info',
    method: 'get',
  });
}

/**
 * 修改系统日志级别
 * @param loggerName
 * @param level
 * */
export function changeLogLevel(data: object) {
  return request({
    url: 'system/changeLogLevel',
    data: data,
    method: 'post',
  });
}

export function startStressTester() {
  return request({
    url: 'system/startStressTester',
    method: 'post',
  });
}
export function stopStressTester() {
  return request({
    url: 'system/stopStressTester',
    method: 'post',
  });
}

interface PinyinParams {
  str: string;
  abbr: boolean;
}

/**
 * 转换字符串伟拼音
 * */
export function pinyin(params: PinyinParams) {
  return request({
    url: 'sequence/pinyin',
    params,
    method: 'get',
  });
}

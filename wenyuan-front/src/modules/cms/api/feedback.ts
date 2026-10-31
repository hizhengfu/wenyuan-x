import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'cms/feedback/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `cms/feedback/${id}`,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'cms/feedback/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'cms/feedback/copy/' + id,
    method: 'post'
  })
}

export function del(id: string) {
  return request({
    url: 'cms/feedback/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'cms/feedback/update',
    method: 'post',
    data
  })
}

/**
 * 加载问卷题目
 * */
export function loadQuestion(id: string) {
  return request({
    url: 'cms/feedback/loadQuestion',
    method: 'get',
    params: {id: id}
  })
}

/**
 * 存储问卷题目
 * */
export function saveQuestion(data: object) {
  return request({
    url: 'cms/feedback/saveQuestion',
    method: 'post',
    data
  })
}

/**
 * 加载问卷题目
 * */
export function frontLoadQuestion(id: string) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/feedback/loadQuestion`,
    method: 'get',
    params: {id: id}
  })
}

/**
 * 存储答卷
 * */
export function saveQuestionAnswer(data: object) {
  return request({
    url: 'cms/feedback/saveQuestionAnswer',
    method: 'post',
    data
  })
}

/**
 * 存储答卷
 * */
export function frontSaveQuestionAnswer(data: object) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/feedback/saveQuestionAnswer`,
    method: 'post',
    data
  })
}

/**
 * 获取答卷列表
 * */
export function listQuestionAnswer(params: object) {
  return request({
    url: 'cms/feedback/listAnswer',
    method: 'get',
    params
  })
}

/**
 * 获取答卷详情
 * */
export function loadQuestionAnswer(params: object) {
  return request({
    url: 'cms/feedback/loadAnswer',
    method: 'get',
    params
  })
}

/**
 * 更新答卷回复
 * */
export function updateAnswerReply(data: object) {
  return request({
    url: 'cms/feedback/updateAnswerReply',
    method: 'post',
    data
  })
}

/**
 * 更新答卷状态
 * */
export function updateAnswerStatus(data: object) {
  return request({
    url: 'cms/feedback/updateAnswerStatus',
    method: 'post',
    data
  })
}

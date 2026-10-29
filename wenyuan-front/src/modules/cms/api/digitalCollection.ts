import request from '@/utils/request';

export function frontList(params: object) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/digitalCollection/list`,
    params: params,
    method: 'get',
  });
}

export function frontGet(id: string) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/digitalCollection/${id}`,
    method: 'get',
  });
}

/**
 * 地区统计
 * */
export function frontCascade(params: object) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/area/cascade`,
    params: params,
    method: 'get',
  })
}

/**
 * 收藏单位统计
 * */
export function frontCountCategoryGroup(categoryId: string) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/digitalCollection/count/group`,
    params: { categoryId: categoryId },
    method: 'get',
  });
}
/**
 * 收藏年代统计
 * */
export function frontCountCategoryYear(categoryId: string) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/digitalCollection/count/year`,
    params: { categoryId: categoryId },
    method: 'get',
  });
}

/**
 * 收藏单位统计
 * */
export function countCategoryGroup(categoryId: string) {
  return request({
    url: 'cms/digitalCollection/count/group',
    params: { categoryId: categoryId },
    method: 'get',
  });
}
/**
 * 收藏年代统计
 * */
export function countCategoryYear(categoryId: string) {
  return request({
    url: 'cms/digitalCollection/count/year',
    params: { categoryId: categoryId },
    method: 'get',
  });
}

export function list(params: object) {
  return request({
    url: 'cms/digitalCollection/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `cms/digitalCollection/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'cms/digitalCollection/create',
    method: 'post',
    data,
  });
}

export function del(id: string) {
  return request({
    url: 'cms/digitalCollection/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'cms/digitalCollection/update',
    method: 'post',
    data,
  });
}

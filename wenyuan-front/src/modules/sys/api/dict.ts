import request from '@/utils/request';
//字典缓存
let dictCaches = new Map();

/**
 * 清空数组
 * */
export function clearAll() {
  dictCaches.clear();
}

export function list(params: object) {
  return request({
    url: 'dict/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `dict/${id}`,
    method: 'get',
  });
}

/**
 * 前端访问api
 * */
export function frontType(type: string, isArray = false) {
  let key = `${type}-${isArray}`;
  if (dictCaches.has(key)) {
    const cached = dictCaches.get(key);
    // 如果缓存的是 Promise，则直接返回
    if (cached instanceof Promise) {
      return cached;
    }
    // 如果缓存的是已解析数据，则包装成 Promise 返回
    return Promise.resolve(cached);
  }

  const promise = request({
    url: `${APP_API_FRONT_PATH}/dict/type/${type}`,
    method: 'get',
    params: { isArray: isArray },
  })
    .then((res) => {
      dictCaches.set(key, res);
      return res;
    })
    .catch((error) => {
      // 失败时清掉本次缓存的 Promise（键为 `${type}-${isArray}`），下次重新请求
      dictCaches.delete(key);
    });

  dictCaches.set(key, promise);
  return promise;
}

/**
 * 字典类型
 * */
export function type(type: string, isArray = false) {
  let key = `${type}-${isArray}`;
  if (dictCaches.has(key)) {
    return Promise.resolve(dictCaches.get(key));
  }
  return request({
    url: `dict/type/${type}`,
    method: 'get',
    params: { isArray: isArray },
  }).then((res) => {
    dictCaches.set(key, res);
    return res;
  });
}

export function search(type: string) {
  return request({
    url: 'dict/search',
    params: { type: type },
    method: 'get',
  });
}

export function add(data: object) {
  dictCaches.clear();
  return request({
    url: 'dict/create',
    method: 'post',
    data,
  });
}

export function del(id: string) {
  dictCaches.clear();
  return request({
    url: 'dict/delete/' + id,
    method: 'post',
  });
}

export function delType(type: string) {
  dictCaches.clear();
  return request({
    url: 'dict/type/' + type,
    method: 'post',
  });
}

export function edit(data: object) {
  dictCaches.clear();
  return request({
    url: 'dict/update',
    method: 'post',
    data,
  });
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: 'dict/sort',
    method: 'post',
    data,
  });
}

/**
 * 文物分类（WW/T 0017—2013 附录D固定35类）只读 API
 * 后端路径：${apiPath}/collection/category；新增/修改/排序/删除后端一律拒绝，前端不再提供。
 */
import request from '@/utils/request';

const BASE = '/collection/category';

export const categoryApi = {
  /** 规范分类列表（按附录D顺序） */
  tree() {
    return request({ url: `${BASE}/tree`, method: 'get' });
  },
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  /** 全量列表（用于表单下拉；只需 COLLECTION_SELECT） */
  list() {
    return request({ url: BASE, method: 'get' });
  },
};

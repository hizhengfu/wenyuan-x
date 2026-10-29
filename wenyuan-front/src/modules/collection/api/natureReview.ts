import request from '@/utils/request';
export const natureReviewApi = {
  page: (page = 1, pageSize = 30) =>
    request({ url: '/collection/nature-review', method: 'get', params: { page, pageSize } }),
};

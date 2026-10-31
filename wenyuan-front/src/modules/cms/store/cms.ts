import {defineStore} from 'pinia';

export interface CmsState {
  isPublish: boolean,//是否发布模式
  siteId: string | null;//站点id
  categoryList: any[] | undefined;//栏目列表
}


export const useCmsStore = defineStore('cms-store', {
  state: (): CmsState => ({
    isPublish: false,
    siteId: '1',
    categoryList: undefined
  }),
  actions: {}
});

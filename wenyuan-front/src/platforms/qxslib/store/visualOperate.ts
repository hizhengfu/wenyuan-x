import {defineStore} from 'pinia'

export interface EventPoint {
  type?: 'PLANE' | 'PANORAMA',//点击地图类型
  scene?: string,//场景
  pagePoint?: { x: number, y: number },//鼠标点击位置
  mapPoint?: { x: number, y: number } //对应地图中的位置
}

export const useEventPointStore = defineStore('VISUAL-OPERATE', {
  state: (): EventPoint => ({type: undefined, pagePoint: undefined, mapPoint: undefined, scene: undefined}),
  actions: {
    clearAll() {
      this.type = undefined
      this.pagePoint = undefined
      this.mapPoint = undefined
      this.scene = undefined
    }
  }
})

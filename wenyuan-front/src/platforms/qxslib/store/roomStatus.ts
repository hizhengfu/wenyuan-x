import {defineStore} from 'pinia'

export interface RoomStatus {
  robot?: any[],//机器人
  people?: any[],//人流
  raster?: any[],//光栅
}

export const useRoomStatusStore = defineStore('ROOM-STATUS', {
  state: (): RoomStatus => ({robot: undefined, people: undefined, raster: undefined}),
  actions: {}
})

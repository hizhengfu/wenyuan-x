import {defineStore} from 'pinia'

export interface AlarmStatus {
  count: 0,//预警变化
}

export const useAlarmStatusStore = defineStore('ALARM-STATUS', {
  state: (): AlarmStatus => ({count: 0}),
  actions: {}
})

import { defineStore } from 'pinia'

export const useWeatherStore = defineStore('weather', {
  state: () => ({
    weatherInfos: {}  as any
  }),
  actions: {
    updateWeatherInfo(weatherId:any, info:any) {
      this.weatherInfos[weatherId] = info
    }
  }
})
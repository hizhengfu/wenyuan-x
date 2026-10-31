import {upperFirst} from 'lodash-es'
import {App} from 'vue'

const modules = import.meta.glob('./*.vue', {eager: true, import: 'default'})

/**
 * 注册通用组件
 * */
export default function setupFourthPageComponents(app: App) {
  app.use({
    install: (a, options) => {
      Object.entries(modules).forEach(([key, component]) => {
        let name = upperFirst(key.split('/').pop()?.replace('.vue', ''))
        if (name && !a.component(`fourthPage${name}`)) {
          //@ts-ignore
          // a.component(`Page${name}`, defineAsyncComponent(component))
          a.component(`fourthPage${name}`, component)
        }
      })
    }
  }, {})
}
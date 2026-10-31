import {upperFirst} from 'lodash-es'
import {App} from 'vue'

const modules = import.meta.glob('./*.vue', {eager: true, import: 'default'})

/**
 * 注册通用组件
 * */
export default function setupFourPageComponents(app: App) {

  app.use({
    install: (a, options) => {
      Object.entries(modules).forEach(([key, component]) => {
        let name = upperFirst(key.split('/').pop()?.replace('.vue', ''))
        if (name && !a.component(`FourPage${name}`)) {
          //@ts-ignore
          // a.component(`Page${name}`, defineAsyncComponent(component))
          a.component(`FourPage${name}`, component)
        }
      })
    }
  }, {})
}

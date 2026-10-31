import Websocket from '@/utils/websocket';


export function smartControl() {
  /**
   *
   * */
  const ws = new Websocket({destinationPath: '/app/smart/control/operate', subscribePath: '/topic/smart/control'})
  ws.connection((response: any) => {
    if ('data' === response.state) {
      //只处理智能控制的数据
      if (response.data && response.data.data && response.data.type === 9) {
        let data = response.data.data
        let message = '操作失败'
        if ('CLICK' === data.type && data.value) {
          let btn = document.querySelector('#' + data.value)
          if (btn) {
            //@ts-ignore
            btn.click();
            message = '操作成功'
          }
        } else if ('URL' === data.type && data.value) {
          location.href = data.value
          message = '操作成功'
        } else if ('DIRECTION' === data.type && data.value) {
          let arrowEvent;
          if (data.value === 'UP') {
            arrowEvent = new KeyboardEvent('keydown', {key: 'ArrowUp', keyCode: 38, code: 'ArrowUp', which: 38});
          } else if (data.value === 'DOWN') {
            arrowEvent = new KeyboardEvent('keydown', {key: 'ArrowDown', keyCode: 40, code: 'ArrowDown', which: 40});
          } else if (data.value === 'LEFT') {
            arrowEvent = new KeyboardEvent('keydown', {key: 'ArrowLeft', keyCode: 37, code: 'ArrowLeft', which: 37});
          } else if (data.value === 'RIGHT') {
            arrowEvent = new KeyboardEvent('keydown', {key: 'ArrowRight', keyCode: 39, code: 'ArrowRight', which: 39});
          }
          if (arrowEvent) {
            document.dispatchEvent(arrowEvent);
          }
          message = '操作成功'
        }
        ws.send({type: 'SMART_CONTROL', data: {message: message}})
      }
    }
  })
}


import SockJS from 'sockjs-client/dist/sockjs.min';
import Stomp from 'stompjs';
import { getToken } from './auth';

/**
 * socketPath websocket 路径
 * subscribePath 订阅路径
 * destinationPath 服务端接收路径 /app/xxx
 * 对应用户接收 /user/topic/notify 不区分用户接收 /topic/notify
 *
 * */
class websocket {
  constructor(options) {
    Object.assign(
      this,
      {
        socketPath: '/websocket',
        subscribePath: '',
        destinationPath: '',
        baseUrl: null, //api 路径
      },
      options,
    );

    //是否连接成功
    this.isConnection = false;
    //重连计数器
    this.connectionCount = 1;

    this.headers = {
      Authorization: getToken(),
    };
  }

  connection(callback) {
    const socketApi = `${this.baseUrl || APP_BASE_URL}${this.socketPath}`;

    const socket = new SockJS(socketApi);
    this.stompClient = Stomp.over(socket);
    this.stompClient.debug = null;
    this.stompClient.connect(
      this.headers,
      (frame) => {
        this.isConnection = true;
        this.connectionCount = 1;
        console.log('Websocket链接成功');
        callback && callback({ state: 'ready' });
        // if (this.destinationPath) {
        //     this.send('ping');
        // }
        this.stompClient.subscribe(this.subscribePath, (msg) => {
          let parsedData = msg.body;
          try {
            if (isString(msg.body)) {
              parsedData = JSON.parse(msg.body);
            }
          } catch (e) {
            console.error(`转换数据为json失败，${msg.body}，${e}`);
            parsedData = msg.body;
          }

          try {
            Promise.resolve().then(() => {
              // eslint-disable-next-line node/no-callback-literal
              callback && callback({ state: 'data', data: parsedData, });
            });
          } catch (e) {
            console.error(`执行WebSocket失败，${parsedData},${e}`);
          }
        });
      },
      () => {
        this.isConnection = false;
        //指数退避防止大量的请求
        let delay = 1000 * Math.pow(2, this.connectionCount);
        delay = Math.min(delay, 30000); // 限制最大延迟时间
        window.setTimeout(() => {
          console.error(`Websocket第${this.connectionCount}次尝试重连`);
          this.connectionCount++;
          this.connection(callback);
        }, delay);
      },
    );
  }

  send(data) {
    if (this.stompClient) {
      if (!this.isConnection) {
        console.log('暂未接入，请等待！');
      } else {
        this.stompClient.send(this.destinationPath, {}, JSON.stringify(data));
      }
    }
  }

  disconnect() {
    console.log(`断开Websocket链接，订阅地址：${this.subscribePath}`);
    this.isConnection = false;
    this.connectionCount = 0;
    if (this.stompClient != null) {
      this.stompClient.disconnect();
    }
  }
}

export default websocket;

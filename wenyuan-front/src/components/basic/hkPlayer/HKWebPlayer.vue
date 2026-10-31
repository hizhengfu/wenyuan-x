<template>
  <div class="hk-web-player-container">
    <div :id="playerId" ref="refPlayer" class="hk-web-player"></div>
    <div
      v-if="loadSuccess != 1"
      style="
        position: absolute;
        top: 0;
        bottom: 0;
        left: 0;
        right: 0;
        display: flex;
        align-items: center;
        justify-content: center;
      "
    >
      <n-result
        v-if="loadSuccess === 2"
        status="warning"
        title="系统未检测到插件，请点击链接下载并安装，安装完成后刷新界面。"
      >
        <template #footer>
          <n-button
            size="large"
            tag="a"
            href="./hkplayer/win32/VideoWebPlugin.exe"
            target="_blank"
            download="VideoWebPlugin.exe"
            >点击下载插件
          </n-button>
        </template>
      </n-result>
      <n-result v-else-if="loadSuccess === 0" status="info" title="正在初始化视频插件，请稍候！">
        <template #footer>
          <n-button
            size="large"
            tag="a"
            href="./hkplayer/win32/VideoWebPlugin.exe"
            target="_blank"
            download="VideoWebPlugin.exe"
            >点击下载插件
          </n-button>
        </template>
      </n-result>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { uuid, notification } from '@/utils';
import { JSEncrypt } from 'jsencrypt';
import { WebControl } from '../../../../resource/hik/win32/web-control.esm.min';
import { useEventBus, useWindowScroll } from '@vueuse/core';
import { inject, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch, watchEffect } from 'vue';
import { list as fetchServerConfig } from '@/modules/video/api/serverConfig.ts';

if (!ENABLE_HKPLAYER) {
  throw Error('请在config配置文件中设置enable_hkplayer为true');
}
/**
 * 加载是否成功
 * */
const loadSuccess = ref(0);
/**
 * 界面缩放比例
 * */
const pageScale = inject('PAGE-SCALE', ref(1));
/**
 * 平台授权信息列表
 * */
let authInfoList: HikAuthInfo[] = [];

const props = defineProps({
  split: { type: String, default: '3x3' }, // 分栏
  videoCodeList: { type: Array as any },
});

const emits = defineEmits(['onLoad', 'ready', 'onWndResize', 'onWndDoubleClick']);

const playerId = 'player-' + uuid();
const refPlayer = ref();
const playerReady = ref(false); // 播放器是否初始化完成

const player = ref();

// 声明公用变量
let initCount = 0;
let pubKey = '';

// 屏幕宫格
let splitWindow = ref(props.split);

// 当前选中窗口，默认第一个
const currentWndIndex = ref(0);

let oWebControl: any = null;

function initWebControl() {
  oWebControl = new WebControl({
    szPluginContainer: playerId, // 指定容器id
    iServicePortStart: 15900, // 指定起止端口号，建议使用该值
    iServicePortEnd: 15900,
    szClassId: '23BF3B0A-2C56-4D97-9C03-0CB103AA8F11', // 用于IE10使用ActiveX的clsid
    cbConnectSuccess: function () {
      // 创建WebControl实例成功
      oWebControl
        .JS_StartService('window', {
          // WebControl实例创建成功后需要启动服务
          dllPath: './VideoPluginConnect.dll', // 值"./VideoPluginConnect.dll"写死
        })
        .then(
          function () {
            // 启动插件服务成功
            console.log('启动插件服务成功');
            // 设置消息回调
            msgCallback();

            //JS_CreateWnd创建视频播放窗口，宽高可设定
            const { width, height } = getContentWindowSize();
            oWebControl.JS_CreateWnd(playerId, width, height).then(function () {
              init(); // 创建播放实例成功后初始化
            });
            loadSuccess.value = 1;
            emits('onLoad', true);
          },
          function () {
            // 启动插件服务失败
            loadSuccess.value = 2;
            emits('onLoad', false);
            console.log('启动插件服务失败');
          },
        );
    },
    cbConnectError: function () {
      // 创建WebControl实例失败
      console.log('创建WebControl实例失败');
      oWebControl = null;
      // @ts-ignore
      reWakeWebControl(WebControl);
    },
    cbConnectClose: function (bNormalClose: any) {
      // 异常断开：bNormalClose = false
      // JS_Disconnect正常断开：bNormalClose = true
      console.log('异常断开false, 正常断开true: ', bNormalClose);
      // if (!bNormalClose) {
      // 异常断开时再重启
      oWebControl = null;
      setTimeout(() => {
        // @ts-ignore
        reWakeWebControl(WebControl);
      }, 1000);
      // }
    },
  });

  player.value = oWebControl;
  // playerReady.value = true
}

//初始化
function init() {
  // const {appkey, appSecret, ip, port} = props
  getPubKey(() => {
    let playMode = 0; //初始播放模式：0-预览，1-回放
    let snapDir = 'D:\\SnapDir'; //抓图存储路径
    let videoDir = 'D:\\VideoDir'; //紧急录像或录像剪辑存储路径
    let layout = splitWindow.value; //playMode指定模式的布局
    let enableHTTPS = 1; //是否启用HTTPS协议与综合安防管理平台交互，这里总是填1
    let encryptedFields = 'secret'; //加密字段，默认加密领域为secret
    let showToolbar = 0; //是否显示工具栏，0-不显示，非0-显示
    let showSmart = 0; //是否显示智能信息（如配置移动侦测后画面上的线框），0-不显示，非0-显示
    let buttonIDs = '256,257,258,259,260,512,513,515,516,517,768,769'; //自定义工具条按钮
    // let buttonIDs = "";                                                           //不显示工具条按钮

    player.value
      .JS_RequestInterface({
        funcName: 'init',
        argument: JSON.stringify({
          // appkey: appkey,                            //API网关提供的appkey
          // secret: setEncrypt(appSecret),                            //API网关提供的secret
          // ip: ip,                                    //API网关IP地址
          playMode: playMode, //播放模式（决定显示预览还是回放界面）
          // port: port,                                //端口
          snapDir: snapDir, //抓图存储路径
          videoDir: videoDir, //紧急录像或录像剪辑存储路径
          layout: layout, //布局
          enableHTTPS: enableHTTPS, //是否启用HTTPS协议
          // encryptedFields: encryptedFields,          //加密字段
          showIntelligent: 0,
          showToolbar: showToolbar, //是否显示工具栏
          toolBarButtonIDs: '4099',
          showSmart: showSmart, //是否显示智能信息
          buttonIDs: buttonIDs,
        }),
      })
      .then((oData: any) => {
        playerReady.value = true;
        const { width, height } = getContentWindowSize();
        player.value.JS_Resize(width, height); // 初始化后resize一次，规避firefox下首次显示窗口后插件窗口未与DIV窗口重合问题
        loadServerConfig().then(() => {
          // 监听 视频列表变化
          startPlayList(props.videoCodeList);

          // 初始化完成之后回调
          emits('ready', player.value, { width, height });
        });
      });
  });
}

/**
 * 反初始化
 * */
function unInit() {
  player.value?.JS_RequestInterface({
    funcName: 'uninit',
  });
}

/**
 * 多平台时候设置平台信息
 * */
interface HikAuthInfo {
  appkey: string;
  authUuid: string;
  ip: string;
  port: number;
  secret: string | boolean;
  encryptedFields: string;
}

//获取公钥
function getPubKey(callback: Function) {
  player.value
    .JS_RequestInterface({
      funcName: 'getRSAPubKey',
      argument: JSON.stringify({
        keyLength: 1024,
      }),
    })
    .then(function (oData: any) {
      if (oData.responseMsg.data) {
        pubKey = oData.responseMsg.data;
        callback();
      }
    });
}

//RSA加密
function setEncrypt(value: any) {
  let encrypt = new JSEncrypt();
  encrypt.setPublicKey(pubKey);
  return encrypt.encrypt(value);
}

/**
 * 获取窗口宽度
 * */
function getContentWindowSize(scale?: number) {
  let s = scale || pageScale.value;
  if (refPlayer.value) {
    return {
      width: s * refPlayer.value.clientWidth,
      height: s * refPlayer.value.clientHeight,
    };
  }
  return { width: 1000, height: 600 };
}

// 窗口变化事件
function resize(scale: number) {
  if (player.value) {
    const { width, height } = getContentWindowSize(scale);
    player.value.JS_Resize(width, height);
    // setWndCover();
    emits('onWndResize', { width, height });
  }
}

watchEffect(() => {
  resize(pageScale.value);
});

/**
 * 重新唤醒WebControl实例
 * @param oWebControl
 * 如果 VideoWebPlugin.exe 安装后，若 WebControl.exe 未启动或进程被结束，创建 WebControl 实例时 cbConnectError 会被执行，
 * 在此可以使用 WebControl.JS_WakeUp("VideoWebPlugin://")来启动程序。
 * 上述代码片段中，如果创建 WebControl 实例失败，则尝试 3 次
 */
function reWakeWebControl(WebControl: any) {
  console.log('插件未启动，正在尝试启动，请稍候...');
  WebControl.JS_WakeUp('VideoWebPlugin://');
  initCount++;
  if (initCount < 3) {
    setTimeout(function () {
      initWebControl();
    }, 3000);
  } else {
    loadSuccess.value = 2;
    console.log('插件启动失败，请检查插件是否安装！');
  }
}

const { x, y } = useWindowScroll();

// 设置窗口裁剪，当因滚动条滚动导致窗口需要被遮住的情况下需要JS_CuttingPartWindow部分窗口
function setWndCover() {
  let playWndDom: any = document.querySelector('.hk-web-player-container');
  let oDivRect = playWndDom.getBoundingClientRect();

  const { width, height } = getContentWindowSize();

  let iCoverLeft = oDivRect.left < 0 ? Math.abs(oDivRect.left) : 0;
  let iCoverTop = oDivRect.top < 0 ? Math.abs(oDivRect.top) : 0;
  let iCoverRight = oDivRect.right - x.value > 0 ? Math.round(oDivRect.right - x.value) : 0;
  let iCoverBottom = oDivRect.bottom - y.value > 0 ? Math.round(oDivRect.bottom - y.value) : 0;

  iCoverLeft = iCoverLeft > width ? width : iCoverLeft;
  iCoverTop = iCoverTop > height ? height : iCoverTop;
  iCoverRight = iCoverRight > width ? width : iCoverRight;
  iCoverBottom = iCoverBottom > height ? height : iCoverBottom;

  player.value.JS_RepairPartWindow(0, 0, width + 1, height); // 多1个像素点防止还原后边界缺失一个像素条
  if (iCoverLeft != 0) {
    player.value.JS_CuttingPartWindow(0, 0, iCoverLeft, height);
  }
  if (iCoverTop != 0) {
    player.value.JS_CuttingPartWindow(0, 0, width + 1, iCoverTop); // 多剪掉一个像素条，防止出现剪掉一部分窗口后出现一个像素条
  }
  if (iCoverRight != 0) {
    player.value.JS_CuttingPartWindow(width - iCoverRight, 0, iCoverRight, height);
  }
  if (iCoverBottom != 0) {
    player.value.JS_CuttingPartWindow(0, height - iCoverBottom, width, iCoverBottom);
  }
}

/**
 * 视频播放预览
 * @param videoCode 视频监控点编号
 * @param windowIndex 播放窗口序号，-1或未指定-空闲窗口预览（不管成功与失败，预览后会自动跳到下一个空闲窗口） 0-选中窗口预览（预览后不会自动跳到下一个空闲窗口） 大于 0为由 wndId 指定的窗口上播放（最大窗口数由获取当前布局查询得到，预览后不会跳到下一个空闲窗口），若超出当前布局的窗口序号，则返回失败
 * 注：播放某个窗口的视频时，会自动默认该窗口为选中状态
 */
function startPlay(videoCode: string, serverId: string) {
  player.value
    ?.JS_RequestInterface({
      funcName: 'startPreview',
      argument: JSON.stringify({
        authUuid: serverId,
        // cameraIndexCode 监控点编号
        cameraIndexCode: videoCode,
        // streamMode 主子码流标识，0-主码流 1-子码流
        streamMode: 0,
        // transMode 传输协议，0-UDP 1-TCP
        transMode: 1,
        // gpuMode 是否开启 GPU 硬解，不建议开启，0-不开启 1-开启
        gpuMode: 0,
        // wndId 播放窗口序号
        wndId: currentWndIndex.value,
      }),
    })
    .then((data: any) => {
      /*if (data.responseMsg.code > 0) {
    notification({description: data.responseMsg.message})
  }*/
      console.log('视频播放预览', data);
    });
}

/**
 * 根据监控点编号批量视频预览
 * @param videoList 示例: [{ cameraIndexCode: "c633ef048fe141e1ac6dbeb36aaf21d3", gpuMode: 0, streamMode: 0, transMode: 1, wndId: 1 }, { cameraIndexCode: "c633ef048fe141e1ac6dbeb36aaf21d3", gpuMode: 0, streamMode: 0, transMode: 1, wndId: 2 }]
 * 注：wndId：取值范围 1~最大窗口数（最大窗口数由获取当前布局查询得到，回放后不会跳到下一个空闲窗口），由 wndId 指定的窗口上播放，若超出当前布局的窗口序号，则返回失败
 */
function startPlayList(videoList: any[]) {
  let tempList = videoList?.map((item: any, index: number) => {
    return {
      authUuid: item.serverId,
      cameraIndexCode: item.code,
      gpuMode: 0,
      streamMode: 0,
      transMode: 1,
      wndId: ++index,
    };
  });
  let len: number = 9;
  const { split } = props;
  if (split?.indexOf('+') !== -1) {
    let temp = split?.split('+');
    len = parseInt(temp[0]) + parseInt(temp[1]);
  } else {
    let temp = split?.split('x');
    len = parseInt(temp[0]) * parseInt(temp[1]);
  }
  // 需要对视频列表数据进行宫格个数截取，否则视频数超过宫格数则会报错
  tempList = tempList?.splice(0, len) || [];
  //当播放列表无数据时，关闭所有正在播放的内容
  if (tempList.length == 0) {
    stopAllPlay();
    return;
  }

  if (player.value) {
    //防止播放列表不一致,存在视频没有全部替换的情况
    stopAllPlay()?.then(() => {
      player.value
        .JS_RequestInterface({
          funcName: 'startMultiPreviewByCameraIndexCode',
          argument: JSON.stringify({
            list: tempList,
          }),
        })
        .then((data: any) => {
          console.log('批量视频预览信息', data);
        });
    });
  }
}

/**
 * 重新播放原视频列表
 */
function rePlayerList() {
  startPlayList(props.videoCodeList);
}

/**
 * 停止所有视频预览
 */
function stopAllPlay() {
  return player.value
    ?.JS_RequestInterface({
      funcName: 'stopAllPreview',
    })
    .then((oData: any) => {
      console.log('停止所有视频预览', oData);
    });
}

/**
 * 对指定窗口停止播放
 */
function stopPlay(currentWndIndex: number) {
  return player.value
    ?.JS_RequestInterface({
      funcName: 'stopMultiPlay',
      argument: JSON.stringify({
        list: [
          { wndId: currentWndIndex },
          // { wndId: 2 },
        ],
      }),
    })
    .then((oData: any) => {
      console.log('对指定窗口停止播放', oData);
    });
}

/**
 * 设置当前布局
 * @param layout 布局，默认'2x2'
 */
function setLayout(layout: string) {
  return player.value
    ?.JS_RequestInterface({
      funcName: 'setLayout',
      argument: JSON.stringify({
        layout: layout, // 布局
      }),
    })
    .then((oData: any) => {
      // console.log('设置当前布局', oData);
      const { width, height } = getContentWindowSize();
      player.value.JS_Resize(width, height); // 初始化后resize一次，规避firefox下首次显示窗口后插件窗口未与DIV窗口重合问题
    });
}

/**
 * 消息回调
 */
function msgCallback() {
  player.value?.JS_SetWindowControlCallback({
    cbIntegrationCallBack: (oData: any) => {
      // oData 是封装的视频 web 插件回调消息的消息体
      let msgData = oData.responseMsg;
      // console.log('消息回调', msgData);
      /**
       * 选中窗口消息
       * type: 1
       * msg: {wndId: 窗口序号, result: 正在播放, cameraIndexCode: 当前监控点编号}
       */
      if (msgData.type == 1) {
        currentWndIndex.value = msgData.msg.wndId;
      }
      /**
       * 预览/回放播放消息
       * type: 2
       * msg: {
       *    cameraIndexCode: 当前监控点编号
       *    wndId: 窗口序号
       *    result:
       *      0x0300(768)-开始播放
       *      0x0301(769)-播放失败（指发起请求失败）
       *      0x0302(770)-播放异常（指取流或解码过程中的异常）
       *      0x0330(816)-播放结束
       * }
       */
      if (msgData.type == 2) {
        if (msgData.msg.result == 769 || msgData.msg.result == 770) {
          // 发起请求失败的情况处理，让其在该窗口重连该视频 (无需进行额外手动操作，web插件会自动重连)
          console.log('视频连接失败，正在为您重连中...');
          // startPlay(msgData.msg.cameraIndexCode, msgData.msg.wndId)
        }
      }
      /**
       * 播放窗口双击事件消息
       * type: 7
       * msg: {
       *   cameraIndexCode: 当前监控点编号
       *   wndId: 窗口序号
       *   wndAction: 窗口大小变化状态 0-还原 1-放大
       *   result: 窗口中的播放状态 0x0100(256)-正在播放 0x0200(512)-空闲
       * }
       */
      if (msgData.type == 7) {
        emits('onWndDoubleClick', msgData.msg);
        // switch (msgData.msg.wndAction) {
        //   case 0:
        //     console.log('退出全屏');
        //     break
        //   case 1:
        //     console.log('点击全屏');
        //     break
        // }
      }
    },
  });
}

/**
 * 视频窗口进入全屏
 */
function fullScreen() {
  player.value
    ?.JS_RequestInterface({
      funcName: 'setFullScreen',
    })
    .then((oData: any) => {
      console.log(oData);
    });
}

/**
 * 视频窗口退出全屏
 */
function exitFullScreen() {
  player.value
    ?.JS_RequestInterface({
      funcName: 'exitFullScreen',
    })
    .then((oData: any) => {
      console.log(oData);
    });
}

/**
 * 隐藏部分窗口
 * 功能：使播放器某部分变成缺口，以用于显示原浏览器页面上被视频插件遮挡住的东西
 * 注：本方法并不能使播放器层级降低，播放器仍会浮在浏览器之上
 * @param iLeft 距离插件窗口左边距
 * @param iTop 距离插件窗口上边距
 * @param iWidth 需要扣除的宽度
 * @param iHeight 需要扣除的高度
 */
function cuttingPartWindow(iLeft: number, iTop: number, iWidth: number, iHeight: number) {
  player.value?.JS_CuttingPartWindow(iLeft, iTop, iWidth, iHeight); // oWebControl 为 WebControl 的对象
}

/**
 * 显示部分窗口
 * 功能：和上面隐藏部分窗口功能相反
 * @param iLeft 距离插件窗口左边距
 * @param iTop 距离插件窗口上边距
 * @param iWidth 需要显示的宽度
 * @param iHeight 需要显示的高度
 */
function repairPartWindow(iLeft: number, iTop: number, iWidth: number, iHeight: number) {
  player.value?.JS_RepairPartWindow(iLeft, iTop, iWidth, iHeight); // oWebControl 为 WebControl 的对象
}

/**
 * 销毁播放实例
 * 注：在 JS_Disconnect 断开与插件服务的连接时，插件内部会销毁实例，无需重复手动销毁
 * 在关闭网页时需要销毁播放实例，从 V1.1.0 开始，关闭网页时可不需销毁，视频 web 插件内部会自动销毁实例
 */
function destroyWnd() {
  // player.value.JS_RequestInterface({
  //   funcName: "destroyWnd",
  // }).then((oData: any) => {
  //   console.log('销毁播放实例', oData);
  // })
  player.value?.JS_DestroyWnd().then(
    function () {
      // oWebControl 为 WebControl 的对象
      // 销毁插件窗口成功
      console.log('销毁插件窗口成功');
    },
    function () {
      // 销毁插件窗口失败
      console.log('销毁插件窗口失败');
    },
  );
}

/**
 * 断开插件服务连接
 * 注：在 JS_Disconnect 断开与插件服务的连接时，插件内部会销毁实例，无需重复调用destroyWnd手动销毁
 */
function disconnect() {
  player.value?.JS_Disconnect().then(
    function () {
      // 断开与插件服务连接成功
      console.log('断开与插件服务连接成功');
    },
    function () {
      // 断开与插件服务连接失败
      console.log('断开与插件服务连接失败');
    },
  );
}

/**
 * 关闭视频插件并清理相关内存
 */
function closeHkPlayer() {
  player.value?.JS_HideWnd(); // 先让窗口隐藏，规避可能的插件窗口滞后于浏览器消失问题
  disconnect(); //断开插件服务连接
  // 清理内存
  // player.value = null
}

/**
 * 从服务端获取参数配置
 * */
function loadServerConfig() {
  return new Promise((resolve, reject) => {
    fetchServerConfig({ pageSize: -1, status: '0' }).then((resp: any) => {
      if (resp.data && resp.data.list) {
        authInfoList = [];
        resp.data.list.forEach((item: any) => {
          let authInfo = {
            appkey: item.appKey,
            authUuid: item.id,
            ip: item.ip,
            port: item.port,
            secret: setEncrypt(item.appSecret),
            // secret: item.appSecret,
            encryptedFields: 'secret',
          };
          authInfoList.push(authInfo);
        });
        if (authInfoList.length > 0) {
          player.value
            .JS_RequestInterface({
              funcName: 'setAuthInfo',
              argument: {
                list: authInfoList,
              },
            })
            .then((data: any) => {
              console.log('设置平台信息', data);
              resolve(true);
            });
        }
      }
    });
  });
}

watch(
  () => props.split,
  (newVal) => {
    console.log(newVal, '000000监听宫格');

    if (playerReady.value) {
      const { split, videoCodeList } = props;
      console.log('--------------监听宫格--------------');
      // 监听 宫格变化
      splitWindow.value = split;
      setLayout(splitWindow.value);
    }
  },
  { immediate: true },
);
watch(
  () => playerReady.value,
  (newVal) => {
    if (newVal) {
      const { split, videoCodeList } = props;
      console.log('--------------监听宫格--------------');
      // 监听 宫格变化
      splitWindow.value = split;
      setLayout(splitWindow.value);
    }
  },
);

watch(
  () => props.videoCodeList,
  (newVal) => {
    if (playerReady.value) {
      const { videoCodeList } = props;
      console.log('--------------监听视频列表--------------', videoCodeList);
      // 监听 视频列表变化
      startPlayList(videoCodeList);
    }
  },
);

const playerActive = ref(true); // 播放器是否激活，默认激活

onActivated(() => {
  if (!playerActive.value) {
    console.log('海康激活');
    // 重新激活
    initWebControl();
    playerActive.value = true;
  }
});

onDeactivated(() => {
  // console.log('海康失活');
  if (player.value) {
    closeHkPlayer();
    // 将激活状态改为false
    playerActive.value = false;
    // JS_DestroyWnd 和JS_Disconnect 中会反初始化插件，这里无需调 JS_RequestInterface/uninit 反初始化。
  }
});

onMounted(() => {
  if (!oWebControl) {
    initWebControl();
  }
});
onUnmounted(() => {
  if (player.value) {
    closeHkPlayer();
    // 将激活状态改为false
    playerActive.value = false;
  }
});

let lastPath = '';
const navBarEventBus = useEventBus<String>('NAV-BAR-EVENT');
navBarEventBus.on((path) => {
  if (lastPath !== path) {
    stopAllPlay();
  }
});

defineExpose({
  fullScreen,
  exitFullScreen,
  startPlay,
  rePlayerList,
  stopPlay,
  stopAllPlay,
  destroyWnd,
  cuttingPartWindow,
  repairPartWindow,
  resize,
  closeHkPlayer,
});
</script>

<style lang="scss" scoped>
.hk-web-player-container,
.hk-web-player {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.hk-web-player-container {
  position: relative;
}
</style>

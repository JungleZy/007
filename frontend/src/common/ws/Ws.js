import {PubSub} from "../utils/PubSub.js";
import useNotification from "../mixin/useNotification";
import { wsUrl } from '../http/endpoint.js'

export const wsCode = {
  FLOOR_CONTENT_DATA: "1000", // 报底中的报文数据传输指令
  FLOOR_CONTENT_DATA_OVER: "1001", // 报底中的报文数据传输完毕指令
  SEND_TELEGRAM_TRAIN_LOG: 2001,
  SEND_TELEGRAM_TRAIN_FLOOR_CONTENT: 3001,
  NOTIFICATION_NEW_TRAIN: '200',
  NOTIFICATION_TRAIN_RESULT: '201'
}

export class Ws {
  constructor() {
    if (!Ws.instance) {
      this.userInfo = JSON.parse(window.localStorage.getItem('userInfo'))
      this.flag = true;
      this.socket = null;
      this.reconnectTimer = null;
      this.retryCount = 0;
      this.callback = null;
      this.url = wsUrl(`/websocket/${this.userInfo.id}`)
      this.un = useNotification()
      Ws.instance = this;
    }
    return Ws.instance;

  }

  static getInstance() {
    if (!this.instance) {
      return this.instance = new Ws();
    }
    return this.instance;
  }

  async run(callback = this.callback) {
    this.callback = callback || this.callback
    this.socket = new WebSocket(this.url);
    this.socket.onopen = () => {
      this.flag = true;
      this.retryCount = 0;
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    };
    this.socket.onclose = () => {
      if (this.flag) this.reconnect();
    };
    this.socket.onerror = () => {};
    this.socket.onmessage = event => {
      const data = JSON.parse(event.data);
      switch (data.code + "") {
        case wsCode.FLOOR_CONTENT_DATA:
          PubSub.publish(wsCode.FLOOR_CONTENT_DATA, JSON.parse(data.data));
          break;
        case wsCode.FLOOR_CONTENT_DATA_OVER:
          PubSub.publish(wsCode.FLOOR_CONTENT_DATA_OVER, true);
          break;
        case wsCode.NOTIFICATION_NEW_TRAIN:
          PubSub.publish(wsCode.NOTIFICATION_NEW_TRAIN, data);
          this.un.notificationNewTrain(data)
          break;
        case wsCode.NOTIFICATION_TRAIN_RESULT:
          PubSub.publish(wsCode.NOTIFICATION_TRAIN_RESULT, data.map);
          break;
        case data.code + '':
          PubSub.publish('18002', data);
          break;
      }
    };
  }

  sendData(code, data) {
    if (this.socket?.readyState !== WebSocket.OPEN) return
    this.socket.send(JSON.stringify({
      code: code,
      data: data
    }));
  }

  reconnect() {
    if (!this.flag) return
    clearTimeout(this.reconnectTimer)
    const delay = Math.min(30000, 1000 * (2 ** this.retryCount++)) + Math.floor(Math.random() * 250)
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      if (this.flag) this.run().then()
    }, delay)
  }

  static shutdown() {
    if (!Ws.instance) return
    Ws.instance.flag = false
    clearTimeout(Ws.instance.reconnectTimer)
    Ws.instance.reconnectTimer = null
    if (Ws.instance.socket) Ws.instance.socket.close()
    Ws.instance.socket = null
    Ws.instance = null
  }
}
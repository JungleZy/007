import {PubSub} from "../utils/PubSub.js";
import useNotification from "../mixin/useNotification";

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
      this.url = `${window.wsUrl}/websocket/${this.userInfo.id}`;
      this.socket = null;
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

  async run() {
    this.socket = new WebSocket(this.url);
    this.socket.onopen = (e) => {
      this.flag = true;
    };
    this.socket.onclose = (e) => {
      this.reconnect();
    };
    this.socket.onerror = (e) => {
    };
    this.socket.onmessage = (e) => {
      const data = JSON.parse(e.data);
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
    this.socket.send(JSON.stringify({
      code: code,
      data: data
    }));
  }

  reconnect() {
    const that = this;
    if (this.flag) {
      setTimeout(() => {
        that.run().then();
      }, 3000)
    }
  }
}
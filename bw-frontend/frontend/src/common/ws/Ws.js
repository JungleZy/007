import {PubSub} from "../utils/PubSub.js";
import useNotification from "../mixin/useNotification";
import { wsUrl } from '../http/endpoint.js'
import SocketConnection from './SocketConnection.js'

export const wsCode = {FLOOR_CONTENT_DATA:"1000", FLOOR_CONTENT_DATA_OVER:"1001", SEND_TELEGRAM_TRAIN_LOG:2001, SEND_TELEGRAM_TRAIN_FLOOR_CONTENT:3001, NOTIFICATION_NEW_TRAIN:'200', NOTIFICATION_TRAIN_RESULT:'201'}
export class Ws {
  constructor() {
    if (!Ws.instance) {
      this.userInfo=JSON.parse(window.localStorage.getItem('userInfo')); this.callback=null
      this.url=wsUrl(`/websocket/${this.userInfo.id}`); this.un=useNotification(); this.connection=new SocketConnection(); Ws.instance=this
    } return Ws.instance
  }
  static getInstance(){ return this.instance || (this.instance=new Ws()) }
  async run(callback=this.callback){ this.callback=callback||this.callback; this.connection.connect(this.url, event=>{const data=JSON.parse(event.data); switch(data.code+''){case wsCode.FLOOR_CONTENT_DATA:PubSub.publish(wsCode.FLOOR_CONTENT_DATA,JSON.parse(data.data));break;case wsCode.FLOOR_CONTENT_DATA_OVER:PubSub.publish(wsCode.FLOOR_CONTENT_DATA_OVER,true);break;case wsCode.NOTIFICATION_NEW_TRAIN:PubSub.publish(wsCode.NOTIFICATION_NEW_TRAIN,data);this.un.notificationNewTrain(data);break;case wsCode.NOTIFICATION_TRAIN_RESULT:PubSub.publish(wsCode.NOTIFICATION_TRAIN_RESULT,data.map);break;default:PubSub.publish('18002',data)}},()=>{}) }
  sendData(code,data){this.connection.send(JSON.stringify({code,data}))}
  reconnect(){this.connection.reconnect(this.connection.generation)}
  static shutdown(){if(!Ws.instance)return;Ws.instance.connection.close();Ws.instance=null}
}

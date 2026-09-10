import SocketConnection from '../../../../common/ws/SocketConnection.js'
export const UnionWsCode = {
  GET_UNION_INFO: 0, GET_ROOM_INFO: 1, USER_JOIN: 2, USER_EXIT: 3,
  USER_LIST: 10, ROOM_LIST: 11, ROOM_USER_BROADCAST: 111, ROOM_STATUS_CHANGE: 112,
  ADD_ROOM: 12, ADD_ROOM_SUCCESS: 120, ADD_ROOM_FAIL: 120, UPDATE_ROOM_INFO: 121,
  JOIN_ROOM: 13, JOIN_ROOM_SUCCESS: 130, JOIN_ROOM_FAIL: 131, EXIT_ROOM: 14,
  EXIT_ROOM_SUCCESS: 140, EXIT_ROOM_FAIL: 141, REMOVE_ROOM: 15, REMOVE_ROOM_SUCCESS: 150,
  REMOVE_ROOM_FAIL: 151, REMOVE_ROOM_BROADCAST: 152, UPDATE_ROOM: 16,
  UPDATE_ROOM_USER: 17, UPDATE_ROOM_USER_BROADCAST: 171, SEAT_INSPECT: 18,
  SEAT_INSPECT_ACCEPT: 181, SEAT_INSPECT_REPLY: 182, SEAT_INSPECT_BROADCAST: 183,
  ROOM_MESSAGE: 20
}
import { wsUrl } from '../../../../common/http/endpoint.js'
export default class UnionWs {
  constructor(){if(!UnionWs.instance){this.userInfo=JSON.parse(window.localStorage.getItem('userInfo'));this.url=wsUrl(`/websocketUnion/${this.userInfo.id}`);this.callback=null;this.connection=new SocketConnection()}return UnionWs.instance||(UnionWs.instance=this)}
  static getInstance(){return this.instance||(this.instance=new UnionWs())}
  run(callback=this.callback){this.callback=callback||this.callback;this.connection.connect(this.url,event=>{try{this.callback?.(JSON.parse(event.data))}catch(e){}},()=>{})}
  get isOpen(){return this.connection.isOpen}
  get socket(){return this.connection.socket}
  getUnionInfo(){this.sendData(UnionWsCode.GET_UNION_INFO)}
  getRoomInfo(data){this.sendData(UnionWsCode.GET_ROOM_INFO,data)}
  sendData(code,data){this.connection.send(JSON.stringify({code,data}))}
  sendReceiveData(code,sendUser,receiveUser,data){this.connection.send(JSON.stringify({code,sendUser,receiveUser,data}))}
  reconnect(){this.connection.reconnect(this.connection.generation)}
  static shutdown(){if(!UnionWs.instance)return;UnionWs.instance.connection.close();UnionWs.instance=null}
}

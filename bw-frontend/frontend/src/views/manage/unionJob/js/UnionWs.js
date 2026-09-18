import SocketConnection from '../../../../common/ws/SocketConnection.js'
import { PubSub } from '../../../../common/utils/PubSub.js'
export const UnionWsCode = {
  GET_UNION_INFO: 0, GET_ROOM_INFO: 1, USER_JOIN: 2, USER_EXIT: 3,
  USER_LIST: 10, ROOM_LIST: 11, ROOM_USER_BROADCAST: 111, ROOM_STATUS_CHANGE: 112,
  ADD_ROOM: 12, ADD_ROOM_SUCCESS: 120, ADD_ROOM_FAIL: 122, UPDATE_ROOM_INFO: 121,
  JOIN_ROOM: 13, JOIN_ROOM_SUCCESS: 130, JOIN_ROOM_FAIL: 131, EXIT_ROOM: 14,
  EXIT_ROOM_SUCCESS: 140, EXIT_ROOM_FAIL: 141, REMOVE_ROOM: 15, REMOVE_ROOM_SUCCESS: 150,
  REMOVE_ROOM_FAIL: 151, REMOVE_ROOM_BROADCAST: 152, UPDATE_ROOM: 16,
  UPDATE_ROOM_USER: 17, UPDATE_ROOM_USER_BROADCAST: 171, SEAT_INSPECT: 18,
  SEAT_INSPECT_ACCEPT: 181, SEAT_INSPECT_REPLY: 182, SEAT_INSPECT_BROADCAST: 183,
  ROOM_MESSAGE: 20
}
import { wsUrl } from '../../../../common/http/endpoint.js'
export default class UnionWs {
  constructor(){if(!UnionWs.instance){this.userInfo=JSON.parse(window.localStorage.getItem('userInfo'));this.url=wsUrl(`/websocketUnion/${this.userInfo.id}`);this.connection=new SocketConnection()}return UnionWs.instance||(UnionWs.instance=this)}
  static getInstance(){return this.instance||(this.instance=new UnionWs())}
  /**
   * 建连并把服务端帧按 code 广播给订阅者。分发规格固定在此处（而非由页面传入回调），
   * 因为大厅页与房间页订阅的是同一批 code，两页各写一份回调必然漂移。
   *
   * 载荷发 `frame.data`：后端 ResponseModel.data 是 String（见 ws/model/ResponseModel.java），
   * 两页 15 处订阅者里 13 处直接 `JSON.parse(data)`、2 处不读载荷，没有一处期待对象。
   * 注意失败帧（ADD_ROOM_FAIL 等）走 `new ResponseModel(code)` 单参构造，Gson 不序列化 null，
   * 故其 data 为 undefined —— 失败帧的订阅者不得 parse。
   */
  run(){
    // 大厅页与房间页共用同一单例：已在连接（或正在重连）时直接复用。
    // connect() 内部先 close() 再 open()，重复调用会让服务端看到一次断开+重连，
    // 进而向同房间其他人广播一次退出/加入，产生假的用户进出提示。
    if(this.connection.active) return
    this.connection.connect(this.url, event=>{
      let frame
      try{
        frame = JSON.parse(event.data)
      }catch(e){
        console.error('UnionWs 帧解析失败:', e, event.data)
        return
      }
      PubSub.publish(frame.code, frame.data)
    }, ()=>{})
  }
  get isOpen(){return this.connection.isOpen}
  get socket(){return this.connection.socket}
  getUnionInfo(){this.sendData(UnionWsCode.GET_UNION_INFO)}
  getRoomInfo(data){this.sendData(UnionWsCode.GET_ROOM_INFO,data)}
  sendData(code,data){this.connection.send(JSON.stringify({code,data}))}
  sendReceiveData(code,sendUser,receiveUser,data){this.connection.send(JSON.stringify({code,sendUser,receiveUser,data}))}
  reconnect(){this.connection.reconnect(this.connection.generation)}
  static shutdown(){if(!UnionWs.instance)return;UnionWs.instance.connection.close();UnionWs.instance=null}
}

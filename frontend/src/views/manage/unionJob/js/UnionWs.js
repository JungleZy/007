import { PubSub } from '../../../../common/utils/PubSub.js'
import { wsUrl } from '../../../../common/http/endpoint.js'

export const UnionWsCode = {
  GET_UNION_INFO: 0,
  GET_ROOM_INFO: 1,
  USER_JOIN: 2,
  USER_EXIT: 3,
  USER_LIST: 10,
  ROOM_LIST: 11,
  ROOM_USER_BROADCAST: 111,
  ROOM_STATUS_CHANGE: 112,
  ADD_ROOM: 12,
  ADD_ROOM_SUCCESS: 120,
  ADD_ROOM_FAIL: 120,
  UPDATE_ROOM_INFO: 121,
  JOIN_ROOM: 13,
  JOIN_ROOM_SUCCESS: 130,
  JOIN_ROOM_FAIL: 131,
  EXIT_ROOM: 14,
  EXIT_ROOM_SUCCESS: 140,
  EXIT_ROOM_FAIL: 141,
  REMOVE_ROOM: 15,
  REMOVE_ROOM_SUCCESS: 150,
  REMOVE_ROOM_FAIL: 151,
  REMOVE_ROOM_BROADCAST: 152,
  UPDATE_ROOM: 16,
  UPDATE_ROOM_USER: 17,
  UPDATE_ROOM_USER_BROADCAST: 171,
  SEAT_INSPECT: 18,
  SEAT_INSPECT_ACCEPT: 181,
  SEAT_INSPECT_REPLY: 182,
  SEAT_INSPECT_BROADCAST: 183,
  ROOM_MESSAGE: 20
}

export default class UnionWs {
  constructor() {
    if (!UnionWs.instance) {
      this.userInfo = JSON.parse(window.localStorage.getItem('userInfo'))
      this.flag = true
      this.url = wsUrl(`/websocketUnion/${this.userInfo.id}`)
      this.socket = null
      this.isOpen = false
      UnionWs.instance = this
    }
    return UnionWs.instance
  }

  static getInstance() {
    if (!this.instance) {
      return (this.instance = new UnionWs())
    }
    return this.instance
  }

  run(callback) {
    this.socket = new WebSocket(this.url)
    this.socket.onopen = e => {
      this.flag = true
      this.isOpen = true
    }
    this.socket.onclose = e => {
      this.reconnect()
    }
    this.socket.onerror = e => {}
    this.socket.onmessage = e => {
      const data = JSON.parse(e.data)
      callback(data)
    }
  }

  getUnionInfo() {
    this.socket.send(
      JSON.stringify({
        code: UnionWsCode.GET_UNION_INFO
      })
    )
  }

  getRoomInfo(data) {
    this.socket.send(
      JSON.stringify({
        code: UnionWsCode.GET_ROOM_INFO,
        data: data
      })
    )
  }

  sendData(code, data) {
    this.socket.send(
      JSON.stringify({
        code: code,
        data: data
      })
    )
  }

  sendReceiveData(code, sendUser, receiveUser, data) {
    this.socket.send(
      JSON.stringify({
        code: code,
        sendUser: sendUser,
        receiveUser: receiveUser,
        data: data
      })
    )
  }

  reconnect() {
    const that = this
    if (this.flag) {
      setTimeout(() => {
        that.run()
      }, 3000)
    }
  }

  exit() {
    this.flag = false
    this.socket.close()
  }
}

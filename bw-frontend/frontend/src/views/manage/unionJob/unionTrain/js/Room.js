import { onMounted } from 'vue'
import { useStore } from 'vuex'
import { PubSub } from '../../../../../common/utils/PubSub'
export default class Room {
  constructor() {
    this.ws = null
    this.wsOnline = false
    this.devOnline = false
    this.wsFlag = true
  }

  connect(callback) {
    try {
      const store = useStore()
      wsOnline.value = store.state.online.wsOnline
      devOnline.value = store.state.online.devOnline
      PubSub.subscribe('message', e => {
        callback(JSON.parse(e.data).data)
      })
    } catch (e) {}
  }

  close() {
    this.wsFlag = false
    this.ws.close()
  }
}

export default function publicSocket()  {
  let ws = null
  let timer = null
  let flag = true
  let url ,fun,open
  const ws_connect =  (src,onMessage,onOpen=false) => {
    url = src
    fun = onMessage
    open = onOpen
    ws = new WebSocket(`${window.wsUrl}${src}`);
    ws.onopen = (e) => {
      if(onOpen){
        onOpen(e)
      }
      console.log('-----连接成功----')
    }
    ws.onerror = (e) => {}
    ws.onclose = (e) => {
      if(timer!=null){
        clearTimeout(timer)
      }
      if(flag){
        reconnect()
      }else {
        console.log('-----关闭连接----')
      }
    }
    ws.onmessage = (e) => {
      onMessage(e)
    }
  }
  const reconnect = () => {
    timer = setTimeout(() => {
      console.log("-----正在重连----")
      ws_connect(url,fun,open)
    }, 1000)
  }
  const closeWebSocket = () => {
    if (ws) {
      flag = false
      ws.close()
    }
  }
  const sendMessage = (obj)=>{
    if(ws.readyState==1){
      ws.send(JSON.stringify(obj))
    }
  }
  return{
    ws_connect,
    sendMessage,
    closeWebSocket
  }
}
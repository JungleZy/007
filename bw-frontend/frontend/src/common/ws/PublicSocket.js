import SocketConnection from './SocketConnection.js'
const activeConnections=new Set()
export const closePublicSockets=()=>{for(const close of [...activeConnections])close()}
export default function publicSocket(){
  const connection=new SocketConnection(); let flag=true; let url; let fun; let open
  const ws_connect=(src,onMessage,onOpen=false)=>{flag=true;url=src;fun=onMessage;open=onOpen;activeConnections.add(closeWebSocket);connection.connect(`${window.wsUrl}${src}`,event=>fun?.(event),event=>open?.(event))}
  const closeWebSocket=()=>{flag=false;activeConnections.delete(closeWebSocket);connection.close()}
  const sendMessage=obj=>connection.send(JSON.stringify(obj))
  return {ws_connect,sendMessage,closeWebSocket}
}

import {PubSub} from '../PubSub'
import {onUnmounted} from 'vue'

export default function operationMorseVoice() {
  const operation = (data)=>{
    PubSub.publish('receiveSendData',data)
  }

  onUnmounted(()=>{
    operation({type:'stop'})
  })
  return{
    operation
  }
}
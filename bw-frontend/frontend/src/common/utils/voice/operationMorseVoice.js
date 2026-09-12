import {audioOperation} from '../MorseVoice'
import {onUnmounted} from 'vue'

export default function operationMorseVoice() {
  let active = true
  const operation = command => active ? audioOperation(command) : false
  operation({type: 'configure', data: {criterion: 83, ratio: {dot: 1, dash: 3, gap: 1, word: 3, suite: 5, leaf: 7}, frequency: 1000, volume: 1}})

  onUnmounted(()=>{
    operation({type:'stop'})
    active = false
  })
  return{
    operation,
    ensureReady: async () => (await operation({type: 'init'})) && active
  }
}
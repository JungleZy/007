import {onUnmounted} from 'vue'
import {PubSub} from './PubSub'
import operationMorseVoice from './voice/operationMorseVoice'
import {calculateTiming, DEFAULT_RATIO} from './voice/MorseVoiceHighPerformance'

export default function ElectronMorse() {
  const {operation} = operationMorseVoice()
  let playing = false
  let messageType = 'short'
  let rate = 15
  let timingType = messageType
  const subscription = PubSub.subscribe('receiveProcessData', event => {
    if (event.status === 'finish' || ['stopped', 'failure'].includes(event.type)) playing = false
  })
  const changeCriterion = (speed, type = messageType) => {
    // A measured speed may still be zero before the first complete character.
    if (!Number.isFinite(Number(speed)) || Number(speed) <= 0) return
    if (Number(speed) === rate && type === messageType) return
    messageType = type
    operation({type: 'configure', data: {...calculateTiming({rate: speed, unit: 'groups', type}), frequency: 1000, volume: 1, model: true}})
    rate = Number(speed)
    timingType = type
  }
  const clear = () => {
    playing = false
    operation({type: 'stop'})
  }
  const changePattern = type => { messageType = type === 1 ? 'letter' : type === 2 ? 'mix' : 'short' }
  const voiceCode = ({numType = messageType, code}) => {
    if (!code?.length) return false
    const type = messageType === 'letter' ? 'letter' : numType === 'short' ? 'short' : messageType === 'mix' ? 'mix' : 'long'
    if (type !== timingType) {
      operation({type: 'configure', data: calculateTiming({rate, unit: 'groups', type})})
      timingType = type
    }
    const accepted = operation({type: playing ? 'addCode' : 'message', data: {numType, data: code}})
    if (accepted) playing = true
    return accepted
  }
  operation({type: 'configure', data: {...calculateTiming({rate: 15, unit: 'groups', type: messageType, ratio: DEFAULT_RATIO}), frequency: 1000, volume: 1}})
  onUnmounted(() => {
    PubSub.unsubscribe(subscription)
    clear()
  })
  return {changeCriterion, clear, voiceCode, changePattern}
}

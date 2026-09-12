import {onUnmounted} from 'vue'
import {PubSub} from './PubSub'
import operationMorseVoice from './voice/operationMorseVoice'
import {createMorseController} from './morseController.js'

export default function ElectronMorse() {
  const {operation} = operationMorseVoice()
  const controller = createMorseController({operation})
  const subscription = PubSub.subscribe('receiveProcessData', controller.handleProcessEvent)
  onUnmounted(() => {
    PubSub.unsubscribe(subscription)
    controller.clear()
  })
  const {changeCriterion, clear, voiceCode, changePattern} = controller
  return {changeCriterion, clear, voiceCode, changePattern}
}

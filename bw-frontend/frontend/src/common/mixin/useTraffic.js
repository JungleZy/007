import { ref, onUnmounted } from 'vue'
import {traffic} from '../../config/pinia/index.js'
import {deepClone} from "../utils/Utils";
import Voice from "../utils/MorseVoice";
import ElectronMorse from "../utils/ElectronMorse";
const trafficStore = traffic.useTrafficStore()
const trafficDataStore = traffic.useTrafficDataStore()
const trafficAudioStore = traffic.useTrafficAudioStore()
const wsOnline = ref(false)
const devOnline = ref(false)
const knockData = ref({})
const voiceFreq = ref(1000)

export default function (openVoice = false, handleHandKeysData = false) {
  const {addCode,changeCriterion,clear,changePattern,voiceCode} =ElectronMorse()
  trafficStore.$subscribe((mutation, state) => {
    wsOnline.value = state.linkStatus
    devOnline.value = state.devStatus
    if (handleHandKeysData) {
      if (openVoice && devOnline.value) {
        trafficAudioStore.$patch({
          audioStatus: true
        })
      }
    }
  })
  if (handleHandKeysData) {
    if (trafficStore.linkStatus && trafficStore.devStatus) {
      trafficAudioStore.$patch({
        audioStatus: true
      })
    }
    trafficDataStore.$subscribe((mutation, state) => {
      if(state.message.d[0]){
        addCode(state)
      }
      handleHandKeysData(deepClone(state.message))
    })
  }
  onUnmounted(() => {
    clear()
    if (trafficAudioStore.audioStatus) {
      trafficAudioStore.$patch({
        audioStatus: false
      })
    }
  })
  return {
    wsOnline,
    devOnline,
    knockData,
    changeCriterion,
    changePattern,
    voiceCode
  }
}

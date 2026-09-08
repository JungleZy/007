import { onMounted, onUnmounted, ref, watch } from 'vue'
import useMorse from '../../../../../../common/mixin/useMorse'

export default function (trainData, currBaoWen, currBaoWenIndex, handKeyBoardBoxRef) {
  const { morseCode } = useMorse()

  const initWordCodeInfo = data => {
    let bwArr = [],
      bd = null,
      num = 0
    for (let bdId in data) {
      for (let d = 0; d < trainData.value.baoDiList.length; d++) {
        if (trainData.value.baoDiList[d].id === bdId) {
          bd = trainData.value.baoDiList[d]
          bd.baoWenList = []
          bwArr = []
          num = 0
          data[bdId].map((bw, w) => {
            if (typeof bw.moresKey === 'string') {
              if (bw.moresKey.length > 1) {
                bw.moresKey = JSON.parse(bw.moresKey)
              } else {
                bw.moresKey = bw.moresKey
              }
            }
            if (typeof bw.moresValue === 'string') {
              bw.moresValue = JSON.parse(bw.moresValue)
            }
            if (typeof bw.moresTime === 'string') {
              bw.moresTime = JSON.parse(bw.moresTime)
            }
            if (bw.moresTime.length === 0) {
              bw.moresTime = [[], [], [], []]
            }
            if (bw.moresValue.some((val, v) => val.join('') !== '' && val.join('') !== morseCode['mix'][bw.moresKey[v]].value)) {
              num++
            }
            if (trainData.value.nowFloorId === bdId && bw.moresValue.every(v => v.length === 0) && currBaoWenIndex.value < 0) {
              currBaoWenIndex.value = w
            }
            bwArr.push({ id: bw.id, key: bw.moresKey, val: bw.moresValue, time: bw.moresTime })
          })
          bd.baoWenList = bwArr
          bd.errNumber = num > 0 ? num : -1
          if ((!trainData.value.nowFloorId && d === 0) || trainData.value.nowFloorId === bdId) {
            currBaoWen.value = bd
            setTimeout(() => {
              if (handKeyBoardBoxRef.value && currBaoWenIndex.value >= 0) {
                handKeyBoardBoxRef.value.children[currBaoWenIndex.value].scrollIntoView(false)
              }
            }, 1000)
          }
          break
        }
      }
    }
  }

  return {
    initWordCodeInfo
  }
}

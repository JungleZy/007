import { provide, ref, onMounted } from 'vue'
import { numberKey, letterKey } from '../../../../../../components/preJob/telexTrain/js/enum.js'

export default function () {
  const content = ref('')
  const trainData = ref({
    type: 0
  })
  onMounted(() => {
    initContent(numberKey, 10)
  })
  provide('content', content)
  const selectType = () => {
    if (trainData.value.type == 0) {
      initContent(numberKey, 10)
    } else if (trainData.value.type == 1) {
      initContent(letterKey, 26)
    } else {
      let arr = [...numberKey, ...letterKey]
      initContent(arr, 36)
    }
  }
  //生成随机报文
  const initContent = (keyboard, num) => {
    let ctAll = []
    for (let i = 0; i < 100; i++) {
      let ct = ''
      for (let j = 0; j < 4; j++) {
        const mat = Math.floor(Math.random() * num)
        ct += keyboard[mat].text2 ? keyboard[mat].text2 : keyboard[mat].text
      }
      ctAll.push(ct.toString())
    }
    let str = ''
    for (let i = 0; i < 10; i++) {
      let arr = ctAll.splice(0, 10)
      let str2 = ''
      arr.forEach(item => {
        str2 = str2 + ' ' + item
      })
      str += `<p>${str2}</p>`
    }
    content.value = str
  }
  return {
    selectType,
    content,
    trainData
  }
}

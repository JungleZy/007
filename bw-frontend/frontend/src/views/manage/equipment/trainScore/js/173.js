import { message } from 'ant-design-vue'

export default function equipment_173(formData, dpData) {
  //type=false 还未结束训练
  const initData_173 = (formData, dpData, type = false) => {
    formData.value.data.forEach((item, index) => {
      const num = index + 2 //联络文件对应初始位置
      let num2 = 2 //联络文件对应初始位置
      if (index == 1) {
        num2 = 5
      } else if (index == 2) {
        num2 = 4
      } else if (index > 2) {
        num2 = 3
      }
      for (let i in item) {
        dpData.value[num][num2].value = item[i].value
        if (type) {
          dpData.value[num][num2].equipmentValue = item[i].equipmentValue
        }
        num2++
      }
    })
  }
  const endTrain_173 = (formData, dpData, messages, scoreList) => {
    formData.value.data.forEach((item, index) => {
      const xdData = messages.param.find(i => i['信道'] == item['信道'].value)
      // const xdData = messages.paramList[index+1]
      if (xdData) {
        for (let i in item) {
          item[i].equipmentValue = xdData[i]
          if (i != '信道') {
            rightORwrong(formData, i, index)
          }
        }
      }
      if (channelNumber(item)) {
        message.success('可通过信道' + item['信道'].value + '进行呼叫')
      }
    })
    initData_173(formData, dpData, true)
    if (scoreList) {
      formData.value.score = 0
      for (let item in formData.value) {
        if (Object.prototype.toString.call(formData.value[item]) === '[object Object]') {
          for (let j in formData.value[item]) {
            formData.value[item][j].score = addScore(scoreList, j, formData.value[item][j])
            formData.value.score = Number(formData.value.score) + Number(formData.value[item][j].score)
          }
        } else if (Array.isArray(formData.value[item])) {
          for (let js in formData.value[item]) {
            for (let j in formData.value[item][js]) {
              formData.value[item][js][j].score = addScore(scoreList, j, formData.value[item][js][j])
              formData.value.score = Number(formData.value.score) + Number(formData.value[item][js][j].score)
            }
          }
        }
      }
    } else {
      formData.value.score = ((100 / formData.value.number) * (formData.value.number - formData.value.errorNumber)).toFixed(2)
    }
  }
  //判断对错
  const rightORwrong = (formData, str, index) => {
    if (formData.value.data[index][str].equipmentValue != formData.value.data[index][str].value) {
      formData.value.errorNumber++
    }
  }
  //判断信道通联
  const channelNumber = e => {
    let bool = true
    for (let i in e) {
      if (e[i].value != e[i].equipmentValue) {
        bool = false
      }
    }
    return bool
  }
  const addScore = (scoreList, name, obj) => {
    let score = 0
    if (obj.value == obj.equipmentValue) {
      scoreList.value.forEach(e => {
        if (e.value.indexOf(name)&& name!="信道") {
          score = e.score
        }
      })
    }
    return score
  }
  //表格头部
  const tpHeader = ['信道', '网络模式', '工作模式', '频率表', '网号', '传输密钥', '信息密钥']
  const dpHeader = ['信道', '网络模式', '工作模式', '频率(MHz)', '传输密钥', '工作密钥']
  const titleHeader_173 = type => {
    if (type == 1) {
      return dpHeader
    } else {
      return tpHeader
    }
  }
  return {
    initData_173,
    endTrain_173,
    titleHeader_173
  }
}

import { message } from 'ant-design-vue'

export default function equipment_125W_400W(formData, dpData) {
  //自动控制在表格中的位置
  const zdkzAddress = {
    网络参数号: [11, 1],
    本台地址: [11, 2],
    网络地址: [11, 3],
    呼叫信道表号: [11, 4],
    呼叫信道信道: [11, 5],
    业务信道表号: [11, 6],
    业务信道信道: [11, 7],
    低速报信道表号: [11, 8],
    低速报信道信道: [11, 9],
    跳频频率表: [11, 10]
  }
  //跳频在表格中位置
  const tpAddress = {
    网号: [26, 7],
    参数号: [26, 8],
    频率表号: [26, 9],
    密钥号: [26, 10],
    中心频率: [26, 11]
  }
  //自动呼号位置
  const zdhhAddress = {
    单台地址: [3, 5],
    网络地址: [3, 6]
    // '跳频台号':[3,7],
    // '低速报地址':[3,8],
  }
  const initData_125W_400W = (formData, tableData, tableData2, type = false) => {
    // tableData2.value[0][0].value = '半双工信道编程表(频率 MHz 工种为LSB)'
    let i = 3
    let i2 = 0
    //绑定信道频率
    formData.value.frequency.forEach((v, index) => {
      if (index == 20) {
        i = 3
        i2 = 3
      }
      if (index == 40) {
        i = 3
        i2 = 6
      }
      tableData2.value[i][i2].value = v['信道号'].value
      if (v['发频率'] || v['收频率']) {
        //400W
        tableData2.value[i][i2 + 1].value = v['收频率'] ? v['收频率'].value : ''
        tableData2.value[i][i2 + 2].value = v['发频率'] ? v['发频率'].value : ''
      } else {
        //125W
        tableData2.value[i][i2 + 1].value = v['频率'].value
      }

      if (type) {
        if (v['发频率'] || v['收频率']) {
          tableData2.value[i][i2 + 1].equipmentValue = v['收频率'] ? v['收频率'].equipmentValue : ''
          tableData2.value[i][i2 + 2].equipmentValue = v['发频率'] ? v['发频率'].equipmentValue : ''
        } else {
          tableData2.value[i][i2 + 1].equipmentValue = v['频率'].equipmentValue
        }
      }
      i++
    })
    //自动控制参数
    if (formData.value.zdkz) {
      for (let i in zdkzAddress) {
        if (formData.value.zdkz[i]) {
          tableData.value[zdkzAddress[i][0]][zdkzAddress[i][1]].value = formData.value.zdkz[i].value
          if (type) {
            tableData.value[zdkzAddress[i][0]][zdkzAddress[i][1]].equipmentValue = formData.value.zdkz[i].equipmentValue
          }
        }
      }
    }
    //跳频
    if (formData.value.tp) {
      for (let i in tpAddress) {
        if (formData.value.tp[i]) {
          tableData.value[tpAddress[i][0]][tpAddress[i][1]].value = formData.value.tp[i].value
          if (type) {
            tableData.value[tpAddress[i][0]][tpAddress[i][1]].equipmentValue = formData.value.tp[i].equipmentValue
          }
        }
      }
    }
    //自动呼号
    if (formData.value.zdhh) {
      for (let i in zdhhAddress) {
        if (formData.value.zdhh[i]) {
          tableData.value[zdhhAddress[i][0]][zdhhAddress[i][1]].value = formData.value.zdhh[i].value
          if (type) {
            tableData.value[zdhhAddress[i][0]][zdhhAddress[i][1]].equipmentValue = formData.value.zdhh[i].equipmentValue
          }
        }
      }
    }
  }
  const endTrain_400W = (formData, tableData, tableData2, messages, trainName, scoreList) => {
    console.log(formData, tableData, tableData2, messages, trainName, scoreList)
    let equipmentData = messages
    if (trainName.indexOf('控制器') > -1) {
      equipmentData = messages.paramList[0]
      formData.value.zdkz['业务信道信道'].equipmentValue = equipmentData['业务信道表号'][0]['业务信道']
      formData.value.zdkz['业务信道表号'].equipmentValue = equipmentData['业务信道表号'][0]['业务信道表号']

      formData.value.zdkz['低速报信道信道'].equipmentValue = equipmentData['低速报信道表号'][0]['低速报信道']
      formData.value.zdkz['低速报信道表号'].equipmentValue = equipmentData['低速报信道表号'][0]['低速报信道表号']

      formData.value.zdkz['呼叫信道信道'].equipmentValue = equipmentData['呼叫信道表号'][0]['呼叫信道']
      formData.value.zdkz['呼叫信道表号'].equipmentValue = equipmentData['呼叫信道表号'][0]['呼叫信道表号']

      formData.value.zdkz['网络参数号'].equipmentValue = equipmentData['网络参数号']
      formData.value.zdkz['本台地址'].equipmentValue = equipmentData['本台地址']
      formData.value.zdkz['网络地址'].equipmentValue = equipmentData['网络地址']
      formData.value.zdkz['跳频频率表'].equipmentValue = equipmentData['当前跳频表']
      rightORwrong(formData, 'zdkz', '业务信道信道')
      rightORwrong(formData, 'zdkz', '呼叫信道信道')
      rightORwrong(formData, 'zdkz', '低速报信道信道')
      rightORwrong(formData, 'zdkz', '网络参数号')
      rightORwrong(formData, 'zdkz', '本台地址')
      rightORwrong(formData, 'zdkz', '网络地址')
      rightORwrong(formData, 'zdkz', '跳频频率表')

      formData.value.zdhh['单台地址'].equipmentValue = equipmentData['自动呼叫单台地址']
      formData.value.zdhh['网络地址'].equipmentValue = equipmentData['自动呼叫网络地址']
      // formData.value.zdhh['跳频台号'].equipmentValue = equipmentData['自动呼叫跳频台号']
      // formData.value.zdhh['低速报地址'].equipmentValue = equipmentData['自动呼叫低速报地址']
      rightORwrong(formData, 'zdhh', '单台地址')
      rightORwrong(formData, 'zdhh', '网络地址')
      // rightORwrong(formData,'zdhh','跳频台号')
      // rightORwrong(formData,'zdhh','低速报地址')

      formData.value.tp['参数号'].equipmentValue = equipmentData['网络参数号']
      formData.value.tp['密钥号'].equipmentValue = equipmentData['密钥号']
      formData.value.tp['网号'].equipmentValue = equipmentData['网络参数号']
      formData.value.tp['频率表号'].equipmentValue = equipmentData['自动呼叫跳频台号']
      rightORwrong(formData, 'tp', '参数号')
      rightORwrong(formData, 'tp', '密钥号')
      rightORwrong(formData, 'tp', '网号')
      rightORwrong(formData, 'tp', '频率表号')
    }
    formData.value.frequency.forEach((v, index) => {
      equipmentData['信道号List'].forEach(item => {
        if (item['信道'] == v['信道号'].value) {
          v['发频率'] ? (v['发频率'].equipmentValue = item['发频率']) : ''
          v['收频率'] ? (v['收频率'].equipmentValue = item['收频率']) : ''
          v['信道号'] ? (v['信道号'].equipmentValue = item['信道']) : ''
        }
      })
      if (v['发频率']) {
        rightORwrong(formData, 'frequency', '发频率', index + 1)
      }
      if (v['收频率']) {
        rightORwrong(formData, 'frequency', '收频率', index + 1)
      }
      if (channelNumber(v)) {
        message.success('可通过信道' + v['信道号'].value + '进行呼叫')
      }
    })
    initData_125W_400W(formData, tableData, tableData2, true)
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

  const endTrain_125W = (formData, tableData, tableData2, messages, scoreList) => {
    const equipmentData = messages.param
    formData.value.zdkz['业务信道表号'].equipmentValue = formData.value.zdkz['业务信道表号'].value
    equipmentData['业务信道'].forEach(item => {
      if (item['业务信道表号'] == formData.value.zdkz['业务信道表号'].value) {
        formData.value.zdkz['业务信道信道'].equipmentValue = item['业务信道表下信道List'].toString(',')
      }
    })

    formData.value.zdkz['呼叫信道表号'].equipmentValue = formData.value.zdkz['呼叫信道表号'].value
    equipmentData['呼叫信道'].forEach(item => {
      if (item['呼叫信道表号'] == formData.value.zdkz['呼叫信道表号'].value) {
        formData.value.zdkz['呼叫信道信道'].equipmentValue = item['呼叫信道表下信道'].toString(',')
      }
    })

    formData.value.zdkz['低速报信道表号'].equipmentValue = formData.value.zdkz['低速报信道表号'].value
    equipmentData['低速报信道'].forEach(item => {
      if (item['低速信道表号'] == formData.value.zdkz['低速报信道表号'].value) {
        formData.value.zdkz['低速报信道信道'].equipmentValue = item['信道号']
      }
    })

    // formData.value.zdkz['业务信道信道'].equipmentValue =equipmentData['业务信道表号'][0]['业务信道']
    // formData.value.zdkz['业务信道表号'].equipmentValue =equipmentData['业务信道表号'][0]['业务信道表号']
    //
    // formData.value.zdkz['低速报信道信道'].equipmentValue =equipmentData['低速报信道表号'][0]['低速报信道']
    // formData.value.zdkz['低速报信道表号'].equipmentValue =equipmentData['低速报信道表号'][0]['低速报信道表号']
    //
    // formData.value.zdkz['呼叫信道信道'].equipmentValue =equipmentData['呼叫信道表号'][0]['呼叫信道']
    // formData.value.zdkz['呼叫信道表号'].equipmentValue =equipmentData['呼叫信道表号'][0]['呼叫信道表号']
    //
    formData.value.zdkz['网络参数号'].equipmentValue = equipmentData['网络参数号']
    formData.value.zdkz['本台地址'].equipmentValue = equipmentData['本台地址']
    formData.value.zdkz['网络地址'].equipmentValue = equipmentData['网络地址']
    formData.value.zdkz['跳频频率表'].equipmentValue = equipmentData['跳频频率表']
    rightORwrong(formData, 'zdkz', '业务信道信道')
    rightORwrong(formData, 'zdkz', '呼叫信道信道')
    rightORwrong(formData, 'zdkz', '低速报信道信道')
    rightORwrong(formData, 'zdkz', '网络参数号')
    rightORwrong(formData, 'zdkz', '本台地址')
    rightORwrong(formData, 'zdkz', '网络地址')
    rightORwrong(formData, 'zdkz', '跳频频率表')

    formData.value.zdhh['单台地址'].equipmentValue = equipmentData['自动呼叫单台地址']
    formData.value.zdhh['网络地址'].equipmentValue = equipmentData['自动呼叫网络地址']
    // formData.value.zdhh['跳频台号'].equipmentValue = equipmentData['自动呼叫跳频台号']
    // formData.value.zdhh['低速报地址'].equipmentValue = equipmentData['自动呼叫低速报地址']
    rightORwrong(formData, 'zdhh', '单台地址')
    rightORwrong(formData, 'zdhh', '网络地址')
    // rightORwrong(formData,'zdhh','跳频台号')
    // rightORwrong(formData,'zdhh','低速报地址')

    formData.value.tp['网号'].equipmentValue = equipmentData['本台网号']
    formData.value.tp['参数号'].equipmentValue = equipmentData['跳频参数号']
    formData.value.tp['频率表号'].equipmentValue = equipmentData['跳频表号']
    formData.value.tp['密钥号'].equipmentValue = equipmentData['秘钥号']
    formData.value.tp['中心频率'].equipmentValue = Number(equipmentData['跳频中心频率'])
    rightORwrong(formData, 'tp', '网号')
    rightORwrong(formData, 'tp', '参数号')
    rightORwrong(formData, 'tp', '频率表号')
    rightORwrong(formData, 'tp', '密钥号')
    rightORwrong(formData, 'tp', '中心频率')

    formData.value.frequency.forEach((v, index) => {
      equipmentData['半双工信道编程表'][0]['定频信道频率List'].forEach(item => {
        if (item['信道号'] == v['信道号'].value) {
          v['发频率'] ? (v['发频率'].equipmentValue = item['信道发送频率']) : ''
          v['收频率'] ? (v['收频率'].equipmentValue = item['信道接收频率']) : ''
          v['信道号'] ? (v['信道号'].equipmentValue = item['信道']) : ''
        }
      })
      rightORwrong(formData, 'frequency', '发频率', index + 1)
      rightORwrong(formData, 'frequency', '收频率', index + 1)
      if (channelNumber(v)) {
        message.success('可通过信道' + v['信道号'].value + '进行呼叫')
      }
    })
    initData_125W_400W(formData, tableData, tableData2, true)
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
  const rightORwrong = (formData, type, str, index) => {
    if (index) {
      if (formData.value[type][index - 1][str].equipmentValue != formData.value[type][index - 1][str].value) {
        formData.value.errorNumber++
      }
    } else {
      if (formData.value[type][str].equipmentValue != formData.value[type][str].value) {
        formData.value.errorNumber++
      }
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
  //表格头部
  const Header = ['自动呼号', '单台地址', '半双工信道编程表(频率 MHz 工种为LSB)', '网络参数号', '本台地址', '网络地址', '呼叫信道', '表号', '信道', '业务信道', '低速报信道', '跳频\n频率表', '网号', '参数号', '频率表号', '密钥号', '中心频率']
  const plHeader = ['半双工信道编程表(频率 MHz 工种为LSB)']
  const titleHeader = formData => {
    if (formData.value.zdkz) {
      return Header
    } else {
      return plHeader
    }
  }
  const addScore = (scoreList, name, obj) => {
    let score = 0
    if (obj.value == obj.equipmentValue) {
      scoreList.value.forEach(e => {
        if (e.value.indexOf(name)>-1 ) {
          score = e.score
        }
      })
    }
    return score
  }
  return {
    initData_125W_400W,
    endTrain_400W,
    endTrain_125W,
    titleHeader
  }
}

import { message } from 'ant-design-vue'

export default function equipment_134A(formData, Data) {
  //type=false 还未结束训练  trainType 训练类型
  const initData_134A = (formData, Data, type = false, trainType = 1) => {
    if (trainType == 1) {
      initData_dp(formData, Data, type)
    } else if (trainType == 2) {
      initData_tp(formData, Data, type)
    } else if (trainType == 4) {
      initData_zsy(formData, Data, type)
    }
  }
  //初始话定频
  const initData_dp = (formData, Data, type) => {
    formData.value.dp.forEach((item, index) => {
      const num = index + 1 //联络文件对应初始位置
      let num2 = 2 //联络文件对应初始位置
      if (index == 1) {
        num2 = 4
      } else if (index == 2) {
        num2 = 3
      } else if (index == 3) {
        num2 = 3
      }
      for (let i in item) {
        if (i == '功率') {
          Data.value[11][6].value = item[i].value
        } else {
          Data.value[num][num2].value = item[i].value
        }

        if (type) {
          if (i == '功率') {
            Data.value[11][6].equipmentValue = item[i].equipmentValue
          } else {
            Data.value[num][num2].equipmentValue = item[i].equipmentValue
          }
        }
        num2++
      }
    })
  }
  //初始化跳频
  const initData_tp = (formData, Data, type) => {
    const tpAddress = {
      网号: [1, 3], //0-2
      参数号: [1, 4],
      表号: [1, 5],
      中心频率: [1, 6],
      密钥号: [1, 7],
      密钥: [1, 8]
    }
    for (let i in tpAddress) {
      Data.value[tpAddress[i][0]][tpAddress[i][1]].value = formData.value.tp[i].value
      if (type) {
        Data.value[tpAddress[i][0]][tpAddress[i][1]].equipmentValue = formData.value.tp[i].equipmentValue
      }
    }
  }
  //初始化自适应
  const initData_zsy = (formData, Data, type) => {
    //分组在表格位置2 11，3  9
    Data.value[2][11].value = formData.value.zsy['分组'][0]['分组'].value
    Data.value[2][12].value = formData.value.zsy['分组'][0]['信道号'].value.toString()
    Data.value[3][9].value = formData.value.zsy['分组'][1]['分组'].value
    Data.value[3][10].value = formData.value.zsy['分组'][1]['信道号'].value.toString()
    if (type) {
      Data.value[2][11].equipmentValue = formData.value.zsy['分组'][0]['分组'].equipmentValue
      Data.value[2][12].equipmentValue = formData.value.zsy['分组'][0]['信道号'].equipmentValue.toString()
      Data.value[3][9].equipmentValue = formData.value.zsy['分组'][1]['分组'].equipmentValue
      Data.value[3][10].equipmentValue = formData.value.zsy['分组'][1]['信道号'].equipmentValue.toString()
    }
    //单台地址起始位置13 1
    let n = 13,
      m = 1
    formData.value.zsy['单台地址'].forEach((item, index) => {
      if (index == 5) {
        n = 15
        m = 1
      }
      Data.value[n][m].value = item['单台地址'].value
      Data.value[n + 1][m].value = item['信道号'].value.toString() == '' ? null : item['信道号'].value.toString()
      if (type) {
        Data.value[n][m].equipmentValue = item['单台地址'].equipmentValue
        Data.value[n + 1][m].equipmentValue = item['信道号'].equipmentValue.toString()
      }
      m++
    })

    //信道常频起始位置 2 5
    let xd_n = 2,
      xd_m = 5
    formData.value.zsy['信道'].forEach((item, index) => {
      if (index == 10) {
        xd_n = 2
        xd_m = 8
      }
      if (index == 0) {
        xd_m = 5
      }
      if (index < 10 && index > 0) {
        xd_m = 3
      }
      if (index > 10) {
        xd_m = 6
      }
      Data.value[xd_n][xd_m].value = item['信道'].value
      Data.value[xd_n][xd_m + 1].value = item['常频'].value
      if (type) {
        Data.value[xd_n][xd_m].equipmentValue = item['信道'].equipmentValue
        Data.value[xd_n][xd_m + 1].equipmentValue = item['常频'].equipmentValue
      }
      xd_n++
    })

    //自适应网络编程表
    Data.value[14][6].value = formData.value.zsy['网号'].value
    Data.value[14][7].value = formData.value.zsy['网信道'].value.toString()
    Data.value[14][8].value = formData.value.zsy['网成员'].value.toString()
    if (type) {
      Data.value[14][6].equipmentValue = formData.value.zsy['网号'].equipmentValue
      Data.value[14][7].equipmentValue = formData.value.zsy['网信道'].equipmentValue.toString()
      Data.value[14][8].equipmentValue = formData.value.zsy['网成员'].equipmentValue.toString()
    }
  }

  const endTrain_134A = (formData, Data, messages, trainType, scoreList) => {
    if (trainType == 1) {
      endTrain_dp(formData, messages, scoreList)
    } else if (trainType == 2) {
      endTrain_tp(formData, messages, scoreList)
    } else if (trainType == 4) {
      endTrain_zsy(formData, messages, scoreList)
    }
    initData_134A(formData, Data, true, trainType)
  }
  //定频结束
  const endTrain_dp = (formData, messages, scoreList) => {
    console.log(formData, messages, scoreList)
    formData.value.dp.forEach((item, index) => {
      const xdData = messages.paramList.find(i => i['信道'] == item['信道'].value)
      if (xdData) {
        for (let i in item) {
          if (item[i]) {
            if (i == '收频' || i == '发频') {
              item[i].equipmentValue = Number(xdData[i]) / 10000
            } else {
              item[i].equipmentValue = xdData[i]
            }
            if (i !== '功率') {
              rightORwrong(formData, 'dp', i, index + 1)
            }
          }
        }
      }
      if (channelNumber(item)) {
        message.success('可通过信道' + item['信道'].value + '进行呼叫')
      }
    })
    // if (formData.value.dp[0]['功率'].equipmentValue != formData.value.dp[0]['功率'].value) {
    //   formData.value.errorNumber++
    // }
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
  //跳频结束
  const endTrain_tp = (formData, messages, scoreList) => {
    for (let item in formData.value.tp) {
      const xdData = messages['param跳频List'].find(i => i['参数号'] == formData.value.tp['参数号'].value)
      if (xdData) {
        if (xdData[item]) {
          formData.value.tp[item].equipmentValue = xdData[item]
          rightORwrong(formData, 'tp', item)
        }
      }
    }
    if (channelNumber(formData.value.tp)) {
      message.success('可通过跳频模式进行呼叫')
    }
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
  //自适应结束
  const endTrain_zsy = (formData, messages, scoreList) => {
    console.log(formData, messages, scoreList)
    const messageData = messages['param自适应']
    formData.value.zsy['分组'].forEach((item, index) => {
      const xdData = messageData['信道组'].find(i => i['分组'] == item['分组'].value)
      if (xdData) {
        item['分组'].equipmentValue = xdData['分组']
        item['信道号'].equipmentValue = xdData['信道号']
        if (item['分组'].equipmentValue != item['分组'].value) {
          formData.value.errorNumber++
        }
        if (item['信道号'].equipmentValue != item['信道号'].value) {
          formData.value.errorNumber++
        }
      } else {
        formData.value.errorNumber += 2
      }
    })
    formData.value.zsy['单台地址'].forEach((item, index) => {
      const xdData = messageData['单台地址编程'].find(i => i['单台地址'] == item['单台地址'].value)
      if (xdData) {
        item['单台地址'].equipmentValue = xdData['单台地址']
        item['信道号'].equipmentValue = xdData['单台信道']
        if (item['单台地址'].equipmentValue != item['单台地址'].value) {
          formData.value.errorNumber++
        }
        if (item['信道号'].equipmentValue != item['信道号'].value) {
          formData.value.errorNumber++
        }
      } else {
        formData.value.errorNumber += 2
      }
    })

    if (messageData['网络编程'].length == 0) {
      formData.value.errorNumber += 3
    }
    messageData['网络编程'].forEach(item => {
      if (item['网号'] && formData.value.zsy['网号'].value == item['网号']) {
        formData.value.zsy['网号'].equipmentValue = item['网号']
        formData.value.zsy['网信道'].equipmentValue = item['网信道']
        formData.value.zsy['网成员'].equipmentValue = item['网成员']
        if (formData.value.zsy['网号'].equipmentValue != formData.value.zsy['网号'].value) {
          formData.value.errorNumber++
        }
        if (formData.value.zsy['网信道'].equipmentValue != formData.value.zsy['网信道'].value) {
          formData.value.errorNumber++
        }
        if (formData.value.zsy['网成员'].equipmentValue != formData.value.zsy['网成员'].value) {
          formData.value.errorNumber++
        }
      } else {
        formData.value.errorNumber += 3
      }
    })
    formData.value.zsy['信道'].forEach((item, index) => {
      const xdData = messageData['工作频率'].find(i => i['信道号'] == item['信道'].value)
      if (xdData) {
        item['信道'].equipmentValue = xdData['信道号']
        item['常频'].equipmentValue = xdData['工作频率']
        if (item['信道'].equipmentValue != item['信道'].value) {
          formData.value.errorNumber++
        }
        if (item['常频'].equipmentValue != item['常频'].value) {
          formData.value.errorNumber++
        }
      } else {
        formData.value.errorNumber += 2
      }
      if (channelNumber(item)) {
        message.success('可通过信道' + item['信道'].value + '进行呼叫')
      }
    })
    if (scoreList) {
      formData.value.score = 0

      for (let item in formData.value['zsy']['信道']){
        formData.value['zsy']['信道'][item]['信道'].score = addScore(scoreList,"信道",formData.value['zsy']['信道'][item]['信道'])
        formData.value.score = Number(formData.value.score) + Number(formData.value['zsy']['信道'][item]['信道'].score)

        formData.value['zsy']['信道'][item]['常频'].score = addScore(scoreList,"常频",formData.value['zsy']['信道'][item]['常频'])
        formData.value.score = Number(formData.value.score) + Number(formData.value['zsy']['信道'][item]['常频'].score)
      }
      for (let item in formData.value['zsy']['分组']){
        formData.value['zsy']['分组'][item]['信道号'].score = addScore(scoreList,"信道号",formData.value['zsy']['分组'][item]['信道号'])
        formData.value.score = Number(formData.value.score) + Number(formData.value['zsy']['分组'][item]['信道号'].score)

        // formData.value['zsy']['分组'][item]['分组'].score = addScore(scoreList,"分组",formData.value['zsy']['分组'][item]['分组'])
        // formData.value.score = Number(formData.value.score) + Number(formData.value['zsy']['分组'][item]['分组'].score)

      }
      for (let item in formData.value['zsy']['单台地址']){
        formData.value['zsy']['单台地址'][item]['信道号'].score = addScore(scoreList,"单台信道",formData.value['zsy']['单台地址'][item]['信道号'])
        formData.value.score = Number(formData.value.score) + Number(formData.value['zsy']['单台地址'][item]['信道号'].score)

        // formData.value['zsy']['单台地址'][item]['单台地址'].score = addScore(scoreList,"单台地址",formData.value['zsy']['单台地址'][item]['单台地址'])
        // formData.value.score = Number(formData.value.score) + Number(formData.value['zsy']['单台地址'][item]['单台地址'].score)

      }
      formData.value['zsy']['网信道'].score = addScore(scoreList,"网信道",formData.value['zsy']['网信道'])
      formData.value.score = Number(formData.value.score) + Number(formData.value['zsy']['网信道'].score)

      // formData.value['zsy']['网号'].score = addScore(scoreList,"网号",formData.value['zsy']['网号'])
      // formData.value.score = Number(formData.value.score) + Number(formData.value['zsy']['网号'].score)

      formData.value['zsy']['网成员'].score = addScore(scoreList,"网成员",formData.value['zsy']['网成员'])
      formData.value.score = Number(formData.value.score) + Number(formData.value['zsy']['网成员'].score)
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
    } else if (formData.value[type][str].equipmentValue != formData.value[type][str].value) {
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
    if(Array.isArray(obj.value )){
      if (obj.value.toString() == obj.equipmentValue.toString()) {
        scoreList.value.forEach(e => {
          if (e.value.indexOf(name)>-1&& name!="信道") {
            score = e.score
            obj.score = e.score
          }
        })
      }
    }else {
      if (obj.value == obj.equipmentValue) {
        scoreList.value.forEach(e => {
          if (e.value.indexOf(name)>-1&& name!="信道") {
            score = e.score
            obj.score = e.score
          }
        })
      }
    }

    return score
  }
  //表格头部
  const dpHeader = ['信道', '收频(MHz)', '发频(MHz)', '工作方式']
  const tpHeader = ['网号', '参数号', '表号', '中心频率', '密钥号', '密钥']
  // const zsyHeader = ['单台地址', '单台信道', '信道', '常频', '分组', '信道号', '网号', '网信道', '网成员']
  const zsyHeader = ['单台信道', '常频', '信道号', '网信道', '网成员']
  const titleHeader_134A = type => {
    if (type == 1) {
      return dpHeader
    } else if (type == 2) {
      return tpHeader
    } else {
      return zsyHeader
    }
  }
  return {
    initData_134A,
    endTrain_134A,
    titleHeader_134A
  }
}

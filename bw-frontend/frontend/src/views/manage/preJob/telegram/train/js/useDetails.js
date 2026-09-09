import { onMounted, onUnmounted, ref, createVNode, watch, nextTick, markRaw } from 'vue'
import { useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
import useMorse from '../../../../../../common/mixin/useMorse.js'
import { partTimeFormatInfo, sum } from '../../../../../../common/utils/Utils.js'
import { PubSub } from '../../../../../../common/utils/PubSub.js'
import { Ws, wsCode } from '../../../../../../common/ws/Ws.js'
import { startTelegramTrain, pauseTelegramTrain, endTelegramTrain, saveFloorContent, getTelegramTrainLog } from '../../../../../../common/api/TelegramApi.js'
import * as echarts from 'echarts'
import { log } from '@antv/g2plot/lib/utils/invariant.js'
import { last } from 'ramda'
import {getFloorContentByFloor} from "../../../../../../common/api/TelegramApi";

export default function (handKeyValue, diffTime, trainData, patStandard, loading, title, handleBaoWenKeyInfo, handKeyWidth, wsOnline, devOnline, wpmTOmm,findLastPage) {
  let lineChart = null
  const nowTime = ref({ h1: 0, h2: 0, m1: 0, m2: 0, s1: 0, s2: 0 })
  const trainTimer = ref(null)
  const logsContainerRef = ref(null)
  const handKeyBoardBoxRef = ref(null)
  const trainBaoDiBoxRef = ref(null)
  const focusTrainThumbRef = ref(null)
  const currBaoWen = ref({ baoWenList: [] })
  const currBaoDiIndex = ref(-1)
  const editBaoDiIndex = ref(-1)
  const lastBaoDiIndex = ref(-1)
  const currBaoWenIndex = ref(-1)
  const isEditBaoWen = ref(false)
  const numberCodeType = ref('mix')
  const modifyBaoDiIds = ref([])
  const resSustainTime = ref('00：00：00')
  const handKeyLogs = ref([])
  const isTrainFocusMode = ref(true)
  const v_index = ref(-1)
  const timeArr = ref([])

  const chartData = ref([])
  const currChart = ref(-2)
  const consumTime = ref({
    dot: { av: 0, min: 0, max: 0 },
    line: { av: 0, min: 0, max: 0 },
    gap: { av: 0, min: 0, max: 0 }
  })
  const proportion = ref({
    dot: 1,
    line: 3,
    interval: 3,
    gap: 5
  })
  const trendLogData = ref([])
  const initFloat = ref(50)

  const route = useRoute()
  const { morseCode } = useMorse()
  let wsBackend = new Ws()

  PubSub.subscribe('send_handKeyTrainPage', e => {
    if (trainData.value.status === 1) {
      Modal.confirm({
        class: 'init_modal_style',
        content: '当前练习还未结束，是否结束练习？',
        icon: () => createVNode(ExclamationCircleOutlined),
        okType: 'danger',
        okText: () => '结束',
        cancelText: () => '取消',
        maskClosable: true,
        onOk: () => {
          endExerciseInfo('go')
        }
      })
    } else {
      PubSub.publish('callback_handKeyTrainPage', true)
    }
  })

  onUnmounted(() => {
    PubSub.unsubscribe('send_handKeyTrainPage')
    wsBackend = null
  })

  /**
   * 训练时间转换显示
   */
  const initTrainTimeInfo = () => {
    trainTimer.value = setInterval(() => {
      if (!(trainData.value.time.sustainTime && trainData.value.time.sustainTime > 0)) {
        trainData.value.time.sustainTime = 0
      }
      trainData.value.time.sustainTime += 1000
      timeAreaShow(trainData.value.time.sustainTime)
    }, 1000)
  }

  /**
   * 时间区域显示
   */
  const timeAreaShow = t => {
    let h, m, s
    h = Math.floor((t / 1000 / 60 / 60) % 24)
    m = Math.floor((t / 1000 / 60) % 60)
    s = Math.floor((t / 1000) % 60)
    h = h > 9 ? h + '' : '0' + h
    m = m > 9 ? m + '' : '0' + m
    s = s > 9 ? s + '' : '0' + s
    nowTime.value.h1 = h.substring(0, 1) * 1
    nowTime.value.h2 = h.substring(1, 2) * 1
    nowTime.value.m1 = m.substring(0, 1) * 1
    nowTime.value.m2 = m.substring(1, 2) * 1
    nowTime.value.s1 = s.substring(0, 1) * 1
    nowTime.value.s2 = s.substring(1, 2) * 1
  }

  /**
   * 动态处理后端推送的Morse码
   * @param code
   * @param diffTime
   * @param gapTime
   */
  const codeNum = ref(0) //本次拍发的电码数量
  /**
   * 根据报底的ID获取报文信息
   */
  const getFloorContentInfo = (page) => {
    getFloorContentByFloor({trainId: trainData.value.trainId,pageNumber:page>0?page:0})
    // return new Promise((resolve, reject) => {
    //   getFloorContentByFloorIdAsync({
    //     trainId: trainData.value.trainId
    //   }).then(res => {
    //     if (res.code !== 200) {
    //       message.error(res.message)
    //     }
    //   })
    // })
  }
  const initMorseCodeInfo = (code, diffTime, gapTime) => {
    if (currBaoWenIndex.value < 0) {
      currBaoWenIndex.value = 0
    }
    if (editBaoDiIndex.value < 0) {
      editBaoDiIndex.value = 0
    }
    if (lastBaoDiIndex.value < 0) {
      lastBaoDiIndex.value = 0
    }
    if (editBaoDiIndex.value >= trainData.value.baoDiList.length) {
      return false
    }
    let trainBD = trainData.value.baoDiList[editBaoDiIndex.value],
      trainLBD = trainData.value.baoDiList[lastBaoDiIndex.value],
      trainBW = trainData.value.baoDiList[editBaoDiIndex.value].baoWenList[currBaoWenIndex.value],
      successNumber = 0
    trainData.value.status = 1
    // trainData.value.errorNumber = 0
    if (lastBaoDiIndex.value > 0) {
      for (let i = 0; i <= lastBaoDiIndex.value; i++) {
        trainData.value.baoDiList[i].disabled = false
        // if (i !== editBaoDiIndex.value) {
        //   trainData.value.errorNumber += trainData.value.baoDiList[i].errNumber > 0 ? trainData.value.baoDiList[i].errNumber : 0
        // }
      }
    }
    trainBD.currEdit = true
    if (modifyBaoDiIds.value.indexOf(trainBD.id) === -1) {
      modifyBaoDiIds.value.push(trainBD.id)
    }
    if (trainData.value.nowFloorId !== trainLBD.id) {
      trainData.value.nowFloorId = trainLBD.id
    }
    //词组
    if (trainData.value.type > 10) {
      if (code === -1) {
        let gap = gapTime[1] - gapTime[0];
        handKeyLogs.value.push({val: 2, time: gapTime});
        if (gap > patStandard.value.interval.min * (1 + initFloat.value / 100)) {
          v_index.value++;
        }
        if (gap > patStandard.value.gap.min * (1 + initFloat.value / 100)) {
          let BW = trainBD.baoWenList[currBaoWenIndex.value]
          let state = BW.val.every((item, idx) => {
            return item.join('') == morseCode[numberCodeType.value][BW.key[idx]].value
          })

          if (BW.val[0].length > 0 && !state) {
            trainBD.errNumber++
            trainData.value.errorNumber++
          } else {
            successNumber++
            trainData.value.successNumber++
          }

          wsBackend.sendData(wsCode.SEND_TELEGRAM_TRAIN_FLOOR_CONTENT, {
            id: trainBW.id,
            moresValue: JSON.stringify(trainBW.val),
            moresTime: JSON.stringify(trainBW.time)
          })
          if (trainBD.baoWenList.length > currBaoWenIndex.value) {
            currBaoWenIndex.value++
          }else {
            findLastPage()
          }
          v_index.value = 0
          isEditBaoWen.value = true
          if( currBaoWenIndex.value>70&&editBaoDiIndex.value < trainData.value.baoDiList.length-1&&trainData.value.baoDiList[editBaoDiIndex.value+1].baoWenList.length===0){
            getFloorContentInfo(editBaoDiIndex.value+1)
          }
          if (currBaoWenIndex.value === trainBD.baoWenList.length) {
            editBaoDiIndex.value++
            if (editBaoDiIndex.value > lastBaoDiIndex.value) {
              lastBaoDiIndex.value++
            }
            if (editBaoDiIndex.value < trainData.value.baoDiList.length) {
              currBaoWen.value = trainData.value.baoDiList[editBaoDiIndex.value]
              currBaoWenIndex.value = 0
              currBaoDiIndex.value++
              currBaoWen.value.baoWenList[0].val = [[], [], [], []]
              if (currBaoDiIndex.value < trainData.value.baoDiList.length) {
                trainBaoDiBoxRef.value.children[currBaoDiIndex.value].scrollIntoView(false)
              }
            } else {
              message.success('您已经训练完毕啦')
              endExerciseInfo()
            }
          }
          if (currBaoWenIndex.value + 1 < trainBD.baoWenList.length) {
            handKeyBoardBoxRef.value.children[currBaoWenIndex.value + 1].scrollIntoView(false)
            focusTrainThumbRef.value.children[currBaoWenIndex.value + 1].scrollIntoView(false)
          }
        }
      }
      if (code > -1) {
        if (isEditBaoWen.value) {
          trainBW.val = [[], [], [], []]
          trainBW.time = [[], [], [], []]
        }
        v_index.value = v_index.value < 0 ? 0 : v_index.value
        v_index.value = v_index.value > 3 ? 3 : v_index.value
        if (trainBW.val.length == 0) {
          trainBW.val = [[], [], [], []]
        }
        trainBW.val[v_index.value].push(code)
        trainBW.time[v_index.value].push(diffTime)
        handKeyLogs.value.push({ val: code, time: diffTime })
        logsContainerRef.value.scrollTop = logsContainerRef.value.scrollHeight
        isEditBaoWen.value = false
        // trainBD.errNumber = 0
        // successNumber = 0
        // for (let BW of trainBD.baoWenList) {
        //   if (BW.val[0].length > 0 && BW.val.some((v, x) => {})) {
        //     trainBD.errNumber++
        //     trainData.value.errorNumber++
        //   } else if (BW.val[3].length > 0) {
        //     successNumber++
        //   }
        // }
        if (trainBW.val[v_index.value].length > 5) {
          trainBW.val[v_index.value].shift()
          trainBW.time[v_index.value].shift()
        }
      }

      if (code >= 0) {
        if (code === 0) {
          timeArr.value.push(parseInt(diffTime[1] - diffTime[0]))
        } else if (code === 1) {
          timeArr.value.push(parseInt((diffTime[1] - diffTime[0]) / 3))
        }

        if (trainData.value.successNumber + trainData.value.errorNumber) {
          trainData.value.accuracy = ((trainData.value.successNumber / (trainData.value.successNumber + trainData.value.errorNumber)) * 100).toFixed(1)
        }
        // trainData.value.totalKnockNumber++
        trainData.value.speed = Math.floor((trainData.value.successNumber + trainData.value.errorNumber) / (trainData.value.time.sustainTime / (60 * 1000)))
        if (wpmTOmm.value) {
          // trainData.value.speed = Number(trainData.value.totalKnockNumber / (trainData.value.time.sustainTime / 1000 / 60)).toFixed(2)
          trainData.value.speed = isNaN(trainData.value.speed) || trainData.value.speed == Infinity ? 0 : trainData.value.speed
        } else {
          trainData.value.speed = parseFloat(1200 / (sum(timeArr.value) / timeArr.value.length)).toFixed(2)
        }
      }
    } else {
      //单词
      if (code === -1) {
        let gap = gapTime[1] - gapTime[0]
        handKeyLogs.value.push({ val: 2, time: gapTime })
        if (gap > patStandard.value.interval.min * (1 + initFloat.value / 100)) {
          let BW = trainBD.baoWenList[currBaoWenIndex.value]
          if (BW.val.length > 0 && BW.val.join('') != morseCode[numberCodeType.value][BW.key].value) {
            trainBD.errNumber++
            trainData.value.errorNumber++
          } else if (BW.val.length > 0 && BW.val.join('') == morseCode[numberCodeType.value][BW.key].value) {
            successNumber++
            trainData.value.successNumber++
          }
          codeNum.value++ //切换电码，电码数量+1
          wsBackend.sendData(wsCode.SEND_TELEGRAM_TRAIN_FLOOR_CONTENT, {
            id: trainBW.id,
            moresValue: JSON.stringify(trainBW.val),
            moresTime: JSON.stringify(trainBW.time)
          })

          if (trainBD.baoWenList.length > currBaoWenIndex.value) {
            currBaoWenIndex.value++
          }
          isEditBaoWen.value = true
          if( currBaoWenIndex.value>70&&editBaoDiIndex.value < trainData.value.baoDiList.length-1&&trainData.value.baoDiList[editBaoDiIndex.value+1].baoWenList.length===0){
            getFloorContentInfo(editBaoDiIndex.value+1)
          }
          if (currBaoWenIndex.value === trainBD.baoWenList.length) {
            editBaoDiIndex.value++
            if (editBaoDiIndex.value > lastBaoDiIndex.value) {
              lastBaoDiIndex.value++
            }
            if (editBaoDiIndex.value < trainData.value.baoDiList.length) {
              currBaoWen.value = trainData.value.baoDiList[editBaoDiIndex.value]
              currBaoWenIndex.value = 0
              currBaoDiIndex.value++
              currBaoWen.value.baoWenList[0].val = []
              nextTick(()=>{
                if(trainBaoDiBoxRef.value.children[currBaoDiIndex.value]){
                  trainBaoDiBoxRef.value.children[currBaoDiIndex.value].scrollIntoView(false)
                }
              })
            } else {
              message.success('您已经训练完毕啦')
              endExerciseInfo()
            }
          }
          if (currBaoWenIndex.value + 1 < trainBD.baoWenList.length) {
            handKeyBoardBoxRef.value.children[currBaoWenIndex.value + 1].scrollIntoView(false)
            focusTrainThumbRef.value.children[currBaoWenIndex.value + 1].scrollIntoView(false)
          }
        }
      }
      if (code > -1) {
        if (isEditBaoWen.value) {
          trainBW.val = []
          trainBW.time = []
        }
        trainBW.val.push(code)
        trainBW.time.push(diffTime)
        handKeyLogs.value.push({ val: code, time: diffTime })
        nextTick(()=>{
          logsContainerRef.value.scrollTop = logsContainerRef.value.scrollHeight
        })
        isEditBaoWen.value = false
      }

      if (trainBW.val.length > 5) {
        trainBW.val.shift()
        trainBW.time.shift()
      }

      if (code >= 0) {
        if (code === 0) {
          timeArr.value.push(parseInt(diffTime[1] - diffTime[0]))
        } else if (code === 1) {
          timeArr.value.push(parseInt((diffTime[1] - diffTime[0]) / 3))
        }
        trainData.value.totalKnockNumber++
        if (trainData.value.successNumber + trainData.value.errorNumber) {
          trainData.value.accuracy = ((trainData.value.successNumber / (trainData.value.successNumber + trainData.value.errorNumber)) * 100).toFixed(1)
        }

        trainData.value.speed = Math.floor((trainData.value.successNumber + trainData.value.errorNumber) / (trainData.value.time.sustainTime / (60 * 1000)))

        if (wpmTOmm.value) {
          // trainData.value.speed = Number(trainData.value.totalKnockNumber / (trainData.value.time.sustainTime / 1000 / 60)).toFixed(2)
          trainData.value.speed = isNaN(trainData.value.speed) || trainData.value.speed == Infinity ? 0 : trainData.value.speed
        } else {
          trainData.value.speed = parseFloat(1200 / (sum(timeArr.value) / timeArr.value.length)).toFixed(2)
        }
      }
    }
    // handKeyLogs.value = handKeyLogs.value.splice(handKeyLogs.value.length-30,handKeyLogs.value.length)
    handKeyValue.value = null
  }

  /**
   * 修改报文手键选中码
   * @param bw
   * @param w
   */
  const editHandKeyInfo = (bw, w) => {
    if (trainData.value.status !== 1) return false
    currBaoWenIndex.value = w
    isEditBaoWen.value = true
    bw.val = []
    bw.time = []
    trainData.value.baoDiList.map((bd, d) => {
      if (bd.id === currBaoWen.value.id) {
        editBaoDiIndex.value = d
      }
    })
  }

  /**
   * 处理接口提交的练习数据
   */
  const handleUpPortData = type => {
    let floors = [],
      bwArr = [],
      errorNum = 0,
      accuracy = 0
    trainData.value.baoDiList.map(bd => {
      bwArr = []
      if (modifyBaoDiIds.value.indexOf(bd.id) > -1) {
        bd.baoWenList.map(bw => {
          if (trainData.value.type < 10) {
            if (bw.val.join('') !== morseCode[numberCodeType.value][bw.key].value) {
              errorNum++
            }
            if (type === 'end' && bw.time.length > 0) {
              trainData.value.status = 3
            }
          } else {
            if (!bw.val.every((v, x) => v.join('') === morseCode['mix'][bw.key[x]].value)) {
              errorNum++
            }
          }
          bwArr.push({
            id: bw.id,
            moresValue: JSON.stringify(bw.val),
            moresTime: JSON.stringify(bw.time)
          })
        })
        floors.push({ floorContents: bwArr })
      }
    })
    accuracy = (((trainData.value.totalNumber - errorNum) / trainData.value.totalNumber) * 100).toFixed(1)
    if (type === 'end') {
      return {
        train: {
          id: trainData.value.trainId,
          errorNumber: errorNum,
          accuracy: accuracy,
          totalKnockNumber: trainData.value.totalKnockNumber,
          speed: trainData.value.speed,
          nowFloorId: trainData.value.nowFloorId,
          sustainTime: trainData.value.time.sustainTime
        },
        trainFloors: floors
      }
    } else {
      return {
        train: {
          id: trainData.value.trainId,
          errorNumber: trainData.value.errorNumber,
          accuracy: trainData.value.accuracy,
          totalKnockNumber: trainData.value.totalKnockNumber,
          speed: trainData.value.speed,
          nowFloorId: trainData.value.nowFloorId,
          sustainTime: trainData.value.time.sustainTime
        },
        trainFloors: floors
      }
    }
  }

  /**
   * 开始练习
   */
  const beginExerciseInfo = () => {
    if (trainData.value.status < 0) return false
    if (!wsOnline.value && trainData.value.status === 0) {
      message.error('报训软件未连接!')
      return false
    }
    if (!devOnline.value && trainData.value.status === 0) {
      message.error('电子键设备未连接！')
      return false
    }
    startTelegramTrain({
      train: {
        id: trainData.value.trainId
      }
    }).then(res => {
      if (res.code === 200) {
        trainData.value.status = res.data.status
        trainData.value.time.startTime = parseInt(res.data.startTime)
        title.value = '本次练习正在进行，当前总耗时'
        initTrainTimeInfo()
      } else {
        message.error(res.message)
      }
    })
  }

  /**
   * 暂停练习
   */
  const pauseExerciseInfo = () => {
    if (trainData.value.status !== 1) return false
    clearInterval(trainTimer.value)
    let result = handleUpPortData('pause')
    loading.value = true
    trainData.value.status = 2
    pauseTelegramTrain(result).then(res => {
      if (res.code === 200) {
        trainData.value.status = res.data.status
        PubSub.publish('callback_handKeyTrainPage', true)
      } else {
        message.error(res.message)
      }
    })
  }

  /**
   * 结束练习
   */
  const endExerciseInfo = go => {
    if (trainData.value.status < 1 || trainData.value.status > 2) return false
    let result = handleUpPortData('end')
    getTrainSendRecordLog()
    loading.value = true
    endTelegramTrain(result).then(res => {
      loading.value = false
      if (res.code === 200) {
        trainData.value.status = 3
        if (go === 'go') {
          PubSub.publish('callback_handKeyTrainPage', true)
        } else {
          // localStorage.setItem('handKeyTrainMode', 'classicMode');
          location.reload()
        }
      } else {
        message.error(res.message)
      }
    })
  }

  /**
   * 切换报底
   * @param bd
   * @param index
   */
  const selectedBaoDiInfo = (bd, index) => {
    if (bd.disabled) return false
    currBaoWen.value = bd
    currBaoDiIndex.value = index
    getFloorContentInfo(index)
    // handKeyBoardBoxRef.value.children[0].scrollIntoView(false)
    localStorage.setItem('handKeyTrainMode', 'classicMode')
  }

  /**
   * 切换练习模式
   */
  const switchTrainMode = () => {
    currBaoDiIndex.value = editBaoDiIndex.value
    currBaoWen.value = trainData.value.baoDiList[currBaoDiIndex.value]
    setTimeout(() => {
      let boxWidth = handKeyBoardBoxRef.value.clientWidth - 8
      handKeyWidth.value = (boxWidth % 120) / parseInt(boxWidth / 120) + 112
    }, 100)
  }

  /**
   * 获取项目结束后的练习发报记录日志
   */
  const getTrainSendRecordLog = () => {
    getTelegramTrainLog({
      id: trainData.value.trainId
    }).then(res => {
      if (res.code === 200 && res.data && res.data.length > 0) {
        for (let log of res.data) {
          chartData.value.push({
            name: (log.type === 0 || log.type === 10 ? '点' : log.type === 1 || log.type === 11 ? '划' : '间隔') + chartData.value.length,
            key: log.type,
            type: '用时',
            value: parseInt(log.value)
          })
        }
        handleChartData(chartData.value)
      }
    })
  }

  /**
   * 处理拍发配置数据
   */
  const handlePatDeployData = () => {
    patStandard.value.line.min = patStandard.value.dot.max + 1
    patStandard.value.line.max = patStandard.value.dot.max * proportion.value.line
    patStandard.value.interval.min = patStandard.value.dot.max + 1
    patStandard.value.interval.max = patStandard.value.dot.max * proportion.value.interval
    patStandard.value.gap.min = patStandard.value.dot.max * proportion.value.interval + 1
    patStandard.value.gap.max = patStandard.value.dot.max * proportion.value.gap
  }

  /**
   * 字码耗时图表切换
   * @param type
   * @returns {boolean}
   */
  const seeTrainChart = type => {
    if (currChart.value === type) return false
    let arr = []
    currChart.value = type
    if (type > -2) {
      if (type >= 2) {
        arr = chartData.value.filter(item => item.key === 2 || item.key === 3)
      } else {
        arr = chartData.value.filter(item => item.key === type || type < 0)
      }

      if (lineChart) {
        let data = [],
          xTxt = []
        arr.map(item => {
          xTxt.push(item.name)
          data.push(Number(item.value))
        })
        lineChart.setOption({
          xAxis: {
            data: xTxt
          },
          series: [
            {
              name: 'column',
              data: data
            }
          ]
        })
      } else {
        handleAchievementChart(chartData.value)
      }
    }
  }

  /**
   * 拍发态势数据处理--词组
   */
  const handleKeyTrendData = () => {
    let t_arr = [],
      arr = [],
      _arr = [],
      pat = patStandard.value
    if (trainData.value.type > 10) {
      chartData.value.forEach((item, index, ar) => {
        _arr.push(item)
        if (item.value > pat.interval.min && (item.key === 2 || item.key === 12)) {
          if (arr.length > 3) {
            for (let val of _arr) {
              arr[3].push(val)
            }
          } else {
            arr.push(_arr)
          }
          _arr = []
        }
        if (trendLogData.value[t_arr.length] && ((item.value > pat.gap.min && (item.key === 2 || item.key === 12)) || index === ar.length - 1)) {
          trendLogData.value[t_arr.length].list = arr
          t_arr.push(arr)
          arr = []
        }
      })
    } else {
      chartData.value.forEach((item, index, ar) => {
        arr.push(item)

        if ((item.value > pat.interval.min && (item.key === 2 || item.key === 12)) || index === ar.length - 1) {
          trendLogData.value[t_arr.length].list = arr
          t_arr.push(arr)
          arr = []
        }
      })
    }
  }

  /**
   * 图表数据处理
   * @param data
   */
  const handleChartData = data => {
    let dot = [],
      line = [],
      gap = []
    data.map(item => {
      if (item.key === 0 && !isNaN(item.value)) {
        dot.push(item.value)
      }
      if (item.key === 1 && !isNaN(item.value)) {
        line.push(item.value)
      }
      if ((item.key === 2 || item.key === 3) && !isNaN(item.value)) {
        gap.push(item.value)
      }
    })
    dot = dot.sort((a, b) => a - b)
    line = line.sort((a, b) => a - b)
    gap = gap.sort((a, b) => a - b)
    if (dot.length > 0) {
      consumTime.value.dot.av = Math.floor(sum(dot) / dot.length)
      consumTime.value.dot.min = dot[0]
      consumTime.value.dot.max = dot[dot.length - 1]
    }
    if (line.length > 0) {
      consumTime.value.line.av = Math.floor(sum(line) / line.length)
      consumTime.value.line.min = line[0]
      consumTime.value.line.max = line[line.length - 1]
    }
    if (gap.length > 0) {
      consumTime.value.gap.av = Math.floor(sum(gap) / gap.length)
      consumTime.value.gap.min = gap[0]
      consumTime.value.gap.max = gap[gap.length - 1]
    }
  }

  /**
   * 初始化渲染字码耗时图表
   * @param chartData
   */
  const handleAchievementChart = chartData => {
    let data = [],
      xTxt = []
    chartData.map(item => {
      xTxt.push(item.name)
      data.push(Number(item.value))
    })

    nextTick(() => {
      let xian = document.getElementById('handKeyChart')
      xian.removeAttribute('_echarts_instance_')
      lineChart = markRaw(echarts.init(xian))
      let option = {
        color: ['#6ebdff'],
        grid: {
          containLabel: true,
          top: 20,
          left: 0,
          right: 6,
          bottom: 40
        },
        toolbox: {
          feature: {
            dataZoom: {
              yAxisIndex: false
            }
          }
        },
        tooltip: {
          show: true,
          backgroundColor: 'rgba(29, 65, 88, .9)',
          padding: [5, 10],
          textStyle: { color: '#6ebdff', fontSize: 12 },
          formatter: '<div class="tooltipItem"><div>用时：</div><div>{c0} ms</div></div>'
        },
        dataZoom: [{ type: 'inside' }, { type: 'slider', height: 20, bottom: 12 }],
        xAxis: {
          type: 'category',
          data: xTxt,
          boundaryGap: true,
          silent: false,
          axisLine: { show: true, lineStyle: { color: '#354971' } },
          axisLabel: { show: false },
          axisTick: { alignWithLabel: true }
        },
        yAxis: {
          type: 'value',
          axisLine: { lineStyle: { color: '#7b90af' } },
          splitLine: {
            show: true,
            lineStyle: { color: ['#1f2b46'], type: 'dashed' }
          }
        },
        series: [
          {
            name: 'column',
            type: 'bar',
            data: data,
            barMaxWidth: 20,
            large: true,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#4c7595' },
                { offset: 1, color: '#9fc7d7' }
              ])
            }
          }
        ]
      }
      lineChart.setOption(option)
    })
  }

  return {
    handKeyBoardBoxRef,
    trainBaoDiBoxRef,
    focusTrainThumbRef,
    lastBaoDiIndex,
    nowTime,
    currBaoWen,
    currBaoDiIndex,
    editBaoDiIndex,
    currBaoWenIndex,
    morseCode,
    numberCodeType,
    resSustainTime,
    timeAreaShow,
    pauseExerciseInfo,
    initTrainTimeInfo,
    initMorseCodeInfo,
    editHandKeyInfo,
    beginExerciseInfo,
    endExerciseInfo,
    selectedBaoDiInfo,
    handKeyLogs,
    logsContainerRef,
    modifyBaoDiIds,
    isTrainFocusMode,
    switchTrainMode,
    getTrainSendRecordLog,
    handleAchievementChart,
    handleChartData,
    lineChart,
    chartData,
    proportion,
    consumTime,
    currChart,
    handlePatDeployData,
    seeTrainChart,
    trendLogData,
    handleKeyTrendData,getFloorContentInfo
  }
}

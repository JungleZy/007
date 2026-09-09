import { message, Modal } from 'ant-design-vue'
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { getDisturbCodeTrainData, reportIntoTrainRoom, getDisturbCodeTrainUserList, uploadUnionTrainResult, updateTrainRoomDispose, findUserPageBaoWenInfo } from '../../../../../common/api/UnionApi.js'
import {wsUrl} from '../../../../../common/http/endpoint.js'
import { useRoute } from 'vue-router'
import Voice from '../../../../../common/utils/MorseVoice'
import useMorse from '../../../../../common/mixin/useMorse'
import operationMorseVoice from "../../../../../common/utils/voice/operationMorseVoice";
import {PubSub} from "../../../../../common/utils/PubSub";

export default function train() {
  const trainTimeRef = ref(null)
  const trainTimer = ref(null)
  const totalTime = ref(0)
  const route = useRoute()
  const userInfo = ref(JSON.parse(window.localStorage.getItem('userInfo')))
  const { dots, morseCode } = useMorse()
  const trainData = ref(null)
  const allBaoWen = ref({})
  const joinTrainUser = ref([])
  const WSConnect = ref(false)
  const student = ref({
    road: '0',
    msg: null
  })
  const symbol = ref({
    start: [1, 0, 0, 0, 1],
    end: [0, 1, 0, 1, 0]
  })
  const disturbList = ref([
    {
      type: 1,
      name: '白噪音',
      url: '/006/noise/noise0.wav',
      volume: 60,
      checked: false,
      buffer: null,
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 2,
      name: '俄语',
      url: '/006/noise/noise1.wav',
      volume: 60,
      checked: false,
      buffer: null,
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 3,
      name: '日语',
      url: '/006/noise/noise2.wav',
      volume: 60,
      checked: false,
      buffer: null,
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 4,
      name: '英语',
      url: '/006/noise/noise3.wav',
      volume: 60,
      checked: false,
      buffer: null,
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 5,
      name: '战场音',
      url: '/006/noise/noise4.wav',
      volume: 60,
      checked: false,
      buffer: null,
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    },
    {
      type: 6,
      name: '防空警报',
      url: '/006/noise/noise5.wav',
      volume: 60,
      checked: false,
      buffer: null,
      ctx: null,
      xhr: null,
      gain: null,
      source: null
    }
  ])
  const userConfirmResult = ref(false)
  const fillInResult = ref(false)
  const trainResult = ref({
    visible: false,
    user: null,
    curr: 1,
    existPage: 0,
    res: {}
  })
  const playTips = ref({
    visible: false
  })
  const storage = ref({
    playCodeIndex: 0,
    playPage: 1,
    totalTime: 0
  })
  const playAgain = ref(false)
  const examinerStatus = ref('online')
  const cacheDispose = ref({
    mainSignal: {},
    noiseDisturb: {},
    codeDisturb: {}
  })
  const ban = ref(true)
  let storeTimer = null
  let storefFlag = false
  let roomId = null
  let ws = null
  let voice1 = new Voice(),
    voice2 = new Voice(),
    voice3 = new Voice(),
    voice4 = new Voice(),
    voice5 = new Voice(),
    voice6 = new Voice()
  const allCode = [] //播报
  const {operation} = operationMorseVoice()
  PubSub.subscribe('receiveProcessData',res=>{
    if(res.status==='finish'){
      message.success('播报已结束')
    }
  })
  onMounted(() => {
    if (route.query.id && route.query.id !== '') {
      roomId = route.query.id
      findTrainDataInfo()
      if (window.localStorage.getItem('dispose' + roomId)) {
        storage.value = JSON.parse(window.localStorage.getItem('dispose' + roomId))
      }
    }
  })

  onBeforeUnmount(() => {
    PubSub.unsubscribe('receiveProcessData')
    WSConnect.value = false
    if (trainData.value.status == 1) {
      storage.value.totalTime = totalTime.value
      storage.value.playCodeIndex = trainData.value.playCodeIndex
      storage.value.playPage = trainData.value.currPag
      window.localStorage.setItem('dispose' + roomId, JSON.stringify(storage.value))
      voice1.clear()
      voice2.clear()
      voice3.clear()
      voice4.clear()
      voice5.clear()
      voice6.clear()
      disturbList.value.map(item => {
        if (item.ctx) {
          changeAudioPlay(item, false)
        }
      })
      if (trainTimer.value) {
        clearInterval(trainTimer.value)
      }
    }
    if (ws) {
      ws.close()
      ws = null
    }
    disturbList.value.map(item => {
      if (item.gain != null) {
        item.source.stop(0)
        item.source = null
        item.gain = null
      }
      item.ctx = null
      item.xhr = null
    })
  })
  window.onbeforeunload = function (event) {
    WSConnect.value = false
    if (trainData.value.status == 1) {
      storage.value.totalTime = totalTime.value
      storage.value.playCodeIndex = trainData.value.playCodeIndex
      storage.value.playPage = trainData.value.currPag
      window.localStorage.setItem('dispose' + roomId, JSON.stringify(storage.value))
      voice1.clear()
      voice2.clear()
      voice3.clear()
      voice4.clear()
      voice5.clear()
      voice6.clear()
      disturbList.value.map(item => {
        if (item.ctx) {
          changeAudioPlay(item, false)
        }
      })
      if (trainTimer.value) {
        clearInterval(trainTimer.value)
      }
    }
    if (ws) {
      ws.close()
      ws = null
    }
  }
  /**
   * 初始化websocket连接
   */
  const initWebSocket = () => {
    if (ws) return false
    ws = new WebSocket(wsUrl(`/simulation/${userInfo.value.id}/${route.query.id}`))
    ws.onopen = e => {
      WSConnect.value = true
    }
    ws.onclose = () => {
      ws = null
      // initWebSocket()
    }
    ws.onmessage = e => {
      console.log(e)
      let res = JSON.parse(e.data),
        data = JSON.parse(res.data),
        body
      if (trainData.value.creatUser) {
        // 老师收到推送
        switch (data.topic) {
          case 'online': {
            if (trainData.value.status < 2) {
              if (!joinTrainUser.value.some(user => user.id == data.body.id)) {
                joinTrainUser.value.push({
                  id: data.body.id,
                  userImg: data.body.userImg,
                  userName: data.body.userName,
                  channel: data.body.channel
                })
              }
            }
            break
          }
          case 'offline': {
            if (trainData.value.status < 2) {
              joinTrainUser.value = joinTrainUser.value.filter(user => user.id != data.body.id)
            }
            break
          }
          case 'select': {
            joinTrainUser.value.map(user => {
              if (user.id == data.body.id) {
                user.channel = data.body.road
              }
            })
            break
          }
          case 'result': {
            findTrainUserListInfo()
            findTrainDataInfo()
            break
          }
        }
      } else {
       // try {
       //   res.data = JSON.parse(res.data)
       //   data.body = JSON.parse(data.body)
       // }catch (e) {
       //
       // }
        // 学生收到推送
        if (data.topic == 'online') {
          message.success('考官已回来')
          examinerStatus.value = 'online'
        } else if (data.topic == 'offline') {
          message.error('考官暂时离开')
          examinerStatus.value = 'offline'
        } else {
          body = data.body
          if (body.status == 1) {
            trainData.value['status'] = body.status
            trainTimer.value = setInterval(() => {
              totalTime.value++
            }, 1000)
          }
          if (body.status == 2) {
            clearInterval(trainTimer.value)
            window.localStorage.removeItem('dispose' + roomId)
            location.reload()
          }
          if (body.type == '1') {
            // 主信号
            switch (body.item) {
              case 'rate': {
                operation({type:'changeCriterion',data:countCriterion(body.dispose.rate)})
                // voice.changeCriterion(countCriterion(body.dispose.rate))
                break
              }
              case 'volume': {
                operation({type:'changeVolume',data:parseFloat(body.dispose.volume) / 100})
                // voice.changeVolume(body.dispose.volume)
                break
              }
              case 'fre': {
                operation({type:'changeFrequency',data:body.dispose.fre})
                // voice.changeFre(body.dispose.fre)
                break
              }
              default: {
                // 按钮操作响应
                if (body.dispose.status == 1) {
                  if (playAgain.value) {
                    againPlayDisturb()
                  } else {
                    changePlayDisposeInfo(body)
                  }
                  if(student.value.msg.status == 2){
                    console.log('继续')
                    operation({type:'continue'})
                  }else {
                    console.log('开始')
                    playTrainCodeInfo()
                  }

                } else if (body.dispose.status == 2) {
                  message.success('播报已暂停')
                  operation({type:'pause'})
                } else {
                  message.success('播报已结束')
                  operation({type:'stop'})
                }
              }
            }
            student.value.msg = body.dispose
          } else if (body.type == '2') {
            // 噪音干扰
            switch (body.check) {
              case 'volume': {
                disturbList.value.map(item => {
                  if (body.dispose[item.type] && item.gain) {
                    item.volume = body.dispose[item.type]
                    item.gain.gain.value = Number(item.volume / 100)
                  }
                })
                break
              }
              case 'on': {
                disturbList.value.map(item => {
                  if (body.dispose[item.type]) {
                    item.volume = body.dispose[item.type]
                    changeAudioPlay(item, true)
                  }
                })
                break
              }
              case 'off': {
                disturbList.value.map(item => {
                  if (body.dispose[item.type]) {
                    changeAudioPlay(item, false)
                  }
                })
                break
              }
            }
          } else if (body.type == '3') {
            // 电码干扰
            if (body.item == 'rate') {
              switch (body.dispose.type) {
                case '1': {
                  voice1.changeCriterion(countCriterion(body.dispose.rate))
                  break
                }
                case '2': {
                  voice2.changeCriterion(countCriterion(body.dispose.rate))
                  break
                }
                case '3': {
                  voice3.changeCriterion(countCriterion(body.dispose.rate))
                  break
                }
                case '4': {
                  voice4.changeCriterion(countCriterion(body.dispose.rate))
                  break
                }
                case '5': {
                  voice5.changeCriterion(countCriterion(body.dispose.rate))
                  break
                }
                case '6': {
                  voice6.changeCriterion(countCriterion(body.dispose.rate))
                  break
                }
              }
            } else if (body.item == 'volume') {
              switch (body.dispose.type) {
                case '1': {
                  voice1.changeVolume(body.dispose.volume)
                  break
                }
                case '2': {
                  voice2.changeVolume(body.dispose.volume)
                  break
                }
                case '3': {
                  voice3.changeVolume(body.dispose.volume)
                  break
                }
                case '4': {
                  voice4.changeVolume(body.dispose.volume)
                  break
                }
                case '5': {
                  voice5.changeVolume(body.dispose.volume)
                  break
                }
                case '6': {
                  voice6.changeVolume(body.dispose.volume)
                  break
                }
              }
            } else if (body.item == 'fre') {
              switch (body.dispose.type) {
                case '1': {
                  voice1.changeFre(body.dispose.fre)
                  break
                }
                case '2': {
                  voice2.changeFre(body.dispose.fre)
                  break
                }
                case '3': {
                  voice3.changeFre(body.dispose.fre)
                  break
                }
                case '4': {
                  voice4.changeFre(body.dispose.fre)
                  break
                }
                case '5': {
                  voice5.changeFre(body.dispose.fre)
                  break
                }
                case '6': {
                  voice6.changeFre(body.dispose.fre)
                  break
                }
              }
            } else {
              // 按钮操作响应
              if (body.dispose.status == 1) {
                playTrainCodeDisturbInfo(body)
              } else {
                switch (body.dispose.type) {
                  case '1':
                    voice1.clear()
                    break
                  case '2':
                    voice2.clear()
                    break
                  case '3':
                    voice3.clear()
                    break
                  case '4':
                    voice4.clear()
                    break
                  case '5':
                    voice5.clear()
                    break
                  case '6':
                    voice6.clear()
                    break
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * 获取训练房数据信息
   */
  const findTrainDataInfo = () => {
    getDisturbCodeTrainData({
      roomId: Number(route.query.id)
    }).then(res => {
      if (res.code === 200) {
        // res.data.content = JSON.parse(res.data.content);
        res.data.mainSignal = JSON.parse(res.data.mainSignal)
        res.data.interferenceSignal = JSON.parse(res.data.interferenceSignal)
        if (res.data.setting && res.data.setting != '') {
          res.data.setting = JSON.parse(res.data.setting)
          cacheDispose.value.mainSignal = res.data.setting.mainSignal
          cacheDispose.value.noiseDisturb = res.data.setting.noiseDisturb
          cacheDispose.value.codeDisturb = res.data.setting.codeDisturb
        }
        trainData.value = res.data
        trainData.value['status'] = trainData.value.stats
        if(res.data.isCable===1){
          trainData.value['pag'] = res.data.pageCount
        }else {
          trainData.value['pag'] = Math.ceil(trainData.value.bwCount / 100)
        }
        trainData.value['currPag'] = window.localStorage.getItem('dispose' + roomId) && trainData.value.stats == 1 ? storage.value.playPage : 1
        trainData.value['creatUser'] = userInfo.value.id == res.data.id
        trainData.value['playCodeIndex'] = window.localStorage.getItem('dispose' + roomId) && trainData.value.stats == 1 ? storage.value.playCodeIndex : 0
        findTrainBaoWenInfo(userInfo.value.id, trainData.value['currPag'])
        // if (trainData.value['pag'] > 1 && trainData.value['currPag'] < trainData.value['pag']) {
        //   setTimeout(() => {
        //     findTrainBaoWenInfo(userInfo.value.id, trainData.value['currPag'] + 1)
        //   }, 500)
        // }
        if (trainData.value.creatUser == '1' && (trainData.value.existPageNumber == null || trainData.value.existPageNumber == 0)) {
          if (trainData.value.status < 2) {
            initWebSocket()
          }
          initDisposeInfo()
          if (trainData.value.status == 1) {
            totalTime.value = storage.value.totalTime ? storage.value.totalTime : 0
            trainTime()
            ban.value = false
          }
        } else {
          if (res.data.existPageNumber != null && res.data.existPageNumber > Math.ceil(trainData.value.bwCount / 100)) {
            trainData.value['pag'] = res.data.existPageNumber
          }
          if (trainData.value.status < 2) {
            reportIntoTrainRoom({ roomId: Number(route.query.id) }).then(res => {
              initWebSocket()
            })
          }
          if (trainData.value.status == 1 && window.localStorage.getItem('dispose' + roomId)) {
            playTips.value.visible = true
          }
        }
        findTrainUserListInfo()
        handlePlayCodeDisturbData()
        if (trainData.value.stats < 2) {
          getDisturbAudioSource()
        }
      }
    })
  }

  /**
   * 根据页码查询用户的报文信息
   * @param userId
   * @param pag
   */
  const findTrainBaoWenInfo = (userId, pag) => {
    findUserPageBaoWenInfo({
      roomId: Number(route.query.id),
      userId: userId,
      pageNumber: pag
    }).then(res => {
      if (res.code === 200) {
        allBaoWen.value[pag + ''] = res.data
        if (pag == trainData.value['currPag']) {
          trainData.value.content = res.data.pageVos
        }
        res.data.pageVos.forEach(item=>{
          allCode.push(...item.key.split(''))
          allCode.push(' ')
        })
        if(pag<trainData.value['pag']){
          allCode.push('/')
          allCode.push(' ')
          findTrainBaoWenInfo(userId,pag+1)
        }else {
          message.success({content:'报底加载完毕！',key:'noticeOk'})
          allCode.unshift(' ')
          allCode.unshift('#')
          allCode.push('!')
        }
        // if (trainData.value.status < 2) {
        //   handlePlayCodeData(pag, res.data)
        // }
      }
    })
  }

  /**
   *获取参训人员列表信息
   */
  const findTrainUserListInfo = () => {
    getDisturbCodeTrainUserList({
      roomId: Number(route.query.id)
    }).then(res => {
      if (res.code === 200) {
        res.data.map(user => {
          if (user.userStatus == 1) {
            if (userInfo.value.id == user.id) {
              userConfirmResult.value = true
            }
          }
          if (userInfo.value.id == user.id) {
            student.value.road = user.channel
            if (cacheDispose.value.mainSignal[user.channel + ''] && cacheDispose.value.mainSignal[user.channel + ''] != null) {
              student.value.msg = cacheDispose.value.mainSignal[user.channel + '']
            } else {
              student.value.msg = trainData.value.mainSignal[user.channel + '']
            }
            if (trainData.value.status == 2 && !trainData.value.creatUser) {
              trainResult.value.user = user
            }
          }
        })
        if (!userConfirmResult.value && trainData.value.status == 2 && trainData.value.creatUser != '1') {
          initWebSocket()
        }
        joinTrainUser.value = res.data
        if (trainData.value.status == 2) {
          nextTick(() => {
            if (trainTimeRef.value) {
              trainTimeRef.value.autoSetTimeAdd(trainData.value.totalTime)
            }
          })
        }
      }
    })
  }

  /**
   * 初始化加载干扰音频实例
   */
  const getDisturbAudioSource = () => {
    disturbList.value.map(item => {
      item.ctx = new (AudioContext || window.webkitAudioContext)()
      item.xhr = new XMLHttpRequest()
      item.xhr.open('GET', window.fileUrl + item.url, true)
      item.xhr.responseType = 'arraybuffer'
      item.xhr.onload = () => {
        item.ctx.decodeAudioData(item.xhr.response, buffer => {
          item.buffer = buffer
        })
      }
      item.xhr.send()
    })
  }

  /**
   * 教员端训练配置赋值
   */
  const initDisposeInfo = () => {
    if (JSON.stringify(cacheDispose.value.mainSignal) != '{}') {
      for (let x in cacheDispose.value.mainSignal) {
        if (cacheDispose.value.mainSignal[x] != null) {
          trainData.value.mainSignal[x] = cacheDispose.value.mainSignal[x]
        }
      }
    }
    if (JSON.stringify(cacheDispose.value.codeDisturb) != '{}') {
      for (let y in cacheDispose.value.codeDisturb) {
        if (cacheDispose.value.codeDisturb[y] != null) {
          trainData.value.interferenceSignal[y] = cacheDispose.value.codeDisturb[y]
        }
      }
    }
    if (JSON.stringify(cacheDispose.value.noiseDisturb) != '{}') {
      disturbList.value.map(item => {
        if (cacheDispose.value.noiseDisturb[item.type] && cacheDispose.value.noiseDisturb[item.type] != null) {
          item.checked = true
          item.volume = cacheDispose.value.noiseDisturb[item.type]
        }
      })
    }
  }

  /**
   * 训练用时
   */
  const trainTime = () => {
    trainTimer.value = setInterval(() => {
      totalTime.value++
      if (trainTimeRef.value) {
        trainTimeRef.value.autoSetTimeAdd(totalTime.value)
      }
    }, 1000)
  }

  /**
   * 计算码率的点标准时长
   * @param rate
   * @returns {number}
   */
  const countCriterion = rate => {
    let criterion = 0
    let type = trainData.value.bwType == 1 ? 'short' : trainData.value.bwType == 2 ? 'long' : trainData.value.bwType == 3 ? 'letter' : 'mix'
    criterion = parseInt(((400 / rate) * 60 * 1000) / dots[type])
    return criterion
  }

  /**
   * 处理报文播报的数据
   */
  const handlePlayCodeData = (pag, _data) => {
    if (pag == 1) {
      _data.pageCode.push(...symbol.value.start)
    }
    _data.pageCode.push(4)
    let keyArr = [],
      codeArr = [],
      type = trainData.value.bwType == 1 ? 'short' : 'mix'
    _data.pageVos.map((item, x) => {
      keyArr = item.key.split('')
      keyArr.map((key, k) => {
        codeArr = morseCode[type][key].value.split('').map(c => Number(c))
        _data.pageCode.push(...codeArr)
        if (k < keyArr.length - 1) {
          _data.pageCode.push(2)
        }
      })
      if (x < _data.pageVos.length - 1) {
        _data.pageCode.push(3)
      }
    })
    if (pag == trainData.value['pag']) {
      _data.pageCode.push(4)
      _data.pageCode.push(...symbol.value.end)
    }
  }

  /**
   * 处理训练电码干扰的电码转换
   */
  const handlePlayCodeDisturbData = () => {
    let item
    for (let i in trainData.value.interferenceSignal) {
      item = trainData.value.interferenceSignal[i]
      item['code'] = []
      item.cont.map((key, x) => {
        key.value.map((c, y) => {
          c.map((k, z) => {
            item['code'].push(parseInt(k))
            if (y === key.value.length - 1 && z === c.length - 1) {
              item['code'].push(3)
            } else if (z === c.length - 1) {
              item['code'].push(2)
            }
          })
        })
      })
    }
  }

  /**
   * 播报报文电码
   */
  const playTrainCodeInfo = () => {
    if (playTips.value.visible) return false
    operation({type:"message",data:{
      numType:trainData.value.bwType == 1 ? 'short' : 'mix',
        data:allCode
      }})
    // return
    // let arr = allBaoWen.value[trainData.value.currPag].pageCode.filter((item, i) => i >= trainData.value.playCodeIndex)
    // voice.clear(() => {
    //   voice.play(arr, res => {
    //     trainData.value.playCodeIndex++
    //     if (res == arr.length - 1 && trainData.value.currPag == trainData.value.pag) {
    //       message.success('播报已结束')
    //     }
    //     if (res == arr.length - 1 && trainData.value.currPag < trainData.value.pag) {
    //       trainData.value.currPag++
    //       trainData.value.playCodeIndex = 0
    //       playTrainCodeInfo()
    //       if (!allBaoWen.value[trainData.value.currPag + 1 + ''] && trainData.value.currPag < trainData.value.pag) {
    //         findTrainBaoWenInfo(userInfo.value.id, trainData.value.currPag + 1)
    //       }
    //     }
    //   })
    // })
  }

  /**
   * 播报电码干扰信号
   */
  const playTrainCodeDisturbInfo = body => {
    switch (body.dispose.type) {
      case '1': {
        voice1.changeVolume(body.dispose.volume)
        voice1.changeFre(body.dispose.fre)
        voice1.changeCriterion(countCriterion(body.dispose.rate))
        voice1.clear(() => {
          voice1.play(body.dispose.code, res => {
            if (res == body.dispose.code.length - 1) {
              playTrainCodeDisturbInfo(body)
            }
          })
        })
        break
      }
      case '2': {
        voice2.changeVolume(body.dispose.volume)
        voice2.changeFre(body.dispose.fre)
        voice2.changeCriterion(countCriterion(body.dispose.rate))
        voice2.clear(() => {
          voice2.play(body.dispose.code, res => {
            if (res == body.dispose.code.length - 1) {
              playTrainCodeDisturbInfo(body)
            }
          })
        })
        break
      }
      case '3': {
        voice3.changeVolume(body.dispose.volume)
        voice3.changeFre(body.dispose.fre)
        voice3.changeCriterion(countCriterion(body.dispose.rate))
        voice3.clear(() => {
          voice3.play(body.dispose.code, res => {
            if (res == body.dispose.code.length - 1) {
              playTrainCodeDisturbInfo(body)
            }
          })
        })
        break
      }
      case '4': {
        voice4.changeVolume(body.dispose.volume)
        voice4.changeFre(body.dispose.fre)
        voice4.changeCriterion(countCriterion(body.dispose.rate))
        voice4.clear(() => {
          voice4.play(body.dispose.code, res => {
            if (res == body.dispose.code.length - 1) {
              playTrainCodeDisturbInfo(body)
            }
          })
        })
        break
      }
      case '5': {
        voice5.changeVolume(body.dispose.volume)
        voice5.changeFre(body.dispose.fre)
        voice5.changeCriterion(countCriterion(body.dispose.rate))
        voice5.clear(() => {
          voice5.play(body.dispose.code, res => {
            if (res == body.dispose.code.length - 1) {
              playTrainCodeDisturbInfo(body)
            }
          })
        })
        break
      }
      case '6': {
        voice6.changeVolume(body.dispose.volume)
        voice6.changeFre(body.dispose.fre)
        voice6.changeCriterion(countCriterion(body.dispose.rate))
        voice6.clear(() => {
          voice6.play(body.dispose.code, res => {
            if (res == body.dispose.code.length - 1) {
              playTrainCodeDisturbInfo(body)
            }
          })
        })
        break
      }
    }
  }

  /**
   * 改变报文播报配置项
   * @param body
   */
  const changePlayDisposeInfo = body => {
    operation({type:'changeVolume',data:parseFloat(body.dispose.volume) / 100})
    operation({type:'changeFrequency',data:body.dispose.fre})
    operation({type:'changeCriterion',data:countCriterion(body.dispose.rate)})
    // voice.changeVolume(body.dispose.volume)
    // voice.changeFre(body.dispose.fre)
    // voice.changeCriterion(countCriterion(body.dispose.rate))
  }

  /**
   * 切换分页
   * @param type
   */
  const pageTurn = type => {
    if (type == 'next') {
      if (trainData.value.currPag == trainData.value.pag) {
        return false
      }
      trainData.value.currPag++
    } else {
      if (trainData.value.currPag == 1) {
        return false
      }
      trainData.value.currPag--
    }
    if (allBaoWen.value[trainData.value.currPag + '']) {
      trainData.value.content = allBaoWen.value[trainData.value.currPag + ''].pageVos
    }
    if (!allBaoWen.value[trainData.value.currPag + 1 + ''] && trainData.value.currPag < trainData.value.pag) {
      findTrainBaoWenInfo(userInfo.value.id, trainData.value.currPag + 1)
    }
  }

  /**
   * 发送学员选择路报信息WebSocket
   * @param item
   */
  const selectRoadInfo = item => {
    student.value.road = item.type
    student.value.msg = item
    let obj = {
      topic: 'select',
      body: {
        road: item.type,
        id: userInfo.value.id
      }
    }
    ws.send(JSON.stringify(obj))
  }

  /**
   * 发送训练配置信息WebSocket
   * @param type
   * @param body
   */
  const sendTrainDisposeInfo = (type, body) => {
    teacherStoreTrainDispose()
    if (trainData.value.status > 0) {
      let obj = {
        topic: type,
        body: body
      }
      ws.send(JSON.stringify(obj))
    } else if (trainData.value.status == 0 && body.type != 2) {
      message.error('训练还未开始，请开启训练！')
    }
  }

  /**
   * 开启训练
   */
  const openTrainInfo = () => {
    trainData.value.status = 1
    trainTime()
    sendTrainDisposeInfo('begin', { status: 1 })
    disturbList.value.map(item => {
      if (item.checked) {
        changeDisturbItem(item, '')
      }
    })
    ban.value = false
  }

  /**
   * 结束训练
   */
  const closeTrainInfo = () => {
    for (const key in trainData.value.mainSignal) {
      changeMainSignalStatus(trainData.value.mainSignal[key], 3)
    }
    trainData.value.status = 2
    window.localStorage.removeItem('dispose' + roomId)
    clearInterval(trainTimer.value)
    sendTrainDisposeInfo('end', { status: 2, totalTime: totalTime.value })
    ban.value = true
  }

  /**
   * 更新主信号配置信息
   * @param item
   * @param status  0 - 未开始；1 - 播报中；2 - 已暂停；3 - 已结束
   */
  const changeMainSignalStatus = (item, status) => {
    item.status = status
    cacheDispose.value.mainSignal[item.type] = item
    sendTrainDisposeInfo(item.type, { type: '1', dispose: item, item: '' })
  }

  /**
   * 更新主信号单独每一项配置
   * @param item
   * @param type
   * @returns {boolean}
   */
  const changeMainSignalItem = (item, type) => {
    if (item.status != 1) return false

    cacheDispose.value.mainSignal[item.type] = item
    sendTrainDisposeInfo(item.type, { type: '1', dispose: item, item: type })
  }

  /**
   * 更新噪音干扰每一项的单独配置
   * @param item
   * @param type
   */
  const changeDisturbItem = (item, type) => {
    let obj = {}
    obj[item.type] = item.volume
    if (type == 'volume') {
      if (item.checked) {
        obj[item.type] = item.volume
        cacheDispose.value.noiseDisturb[item.type] = item.volume
        sendTrainDisposeInfo('0', { type: '2', dispose: obj, check: 'volume' })
      }
    } else {
      cacheDispose.value.noiseDisturb[item.type] = item.checked ? item.volume : null
      sendTrainDisposeInfo('0', { type: '2', dispose: obj, check: item.checked ? 'on' : 'off' })
    }
  }

  /**
   * 开始干扰/停止干扰
   * @param item
   * @param status
   */
  const changeAudioPlay = (item, status) => {
    if (status) {
      if (item.buffer == null) {
        item.xhr.onload = () => {
          item.ctx.decodeAudioData(item.xhr.response, buffer => {
            item.buffer = buffer
            item.source = item.ctx.createBufferSource()
            item.source.buffer = item.buffer
            item.source.loop = true
            item.gain = item.ctx.createGain()
            item.gain.gain.value = Number(item.volume / 100)
            item.gain.connect(item.ctx.destination)
            item.source.connect(item.gain)
            item.source.start(0)
          })
        }
      } else {
        item.source = item.ctx.createBufferSource()
        item.source.buffer = item.buffer
        item.source.loop = true
        item.gain = item.ctx.createGain()
        item.gain.gain.value = Number(item.volume / 100)
        item.gain.connect(item.ctx.destination)
        item.source.connect(item.gain)
        item.source.start(0)
      }
    } else if (item.source) {
      item.source.stop(0)
      item.source = null
      item.gain = null
    }
  }

  /**
   * 更新电码干扰配置信息
   * @param item
   * @param status  0 - 未开始；1 - 干扰中
   */
  const changeCodeDisturbStatus = (item, status) => {
    item.status = status
    cacheDispose.value.codeDisturb[item.type] = status == 1 ? item : null
    sendTrainDisposeInfo('0', { type: '3', dispose: item, item: '' })
  }

  /**
   * 更新电码干扰信号单独每一项配置
   * @param item
   * @param type
   * @returns {boolean}
   */
  const changeCodeDisturbItem = (item, type) => {
    if (item.status != 1) return false
    cacheDispose.value.codeDisturb[item.type] = item
    sendTrainDisposeInfo('0', { type: '3', dispose: item, item: type })
  }

  /**
   * 学生填写训练抄手结果
   */
  const fillInTrainResult = res => {
    let arr,
      _res = []
    res.map(pag => {
      arr = []
      pag.map(row => {
        row.map(col => {
          arr.push(col[0])
        })
      })
      _res.push(JSON.stringify(arr))
    })
    uploadUnionTrainResult({
      userId: userInfo.value.id,
      roomId: route.query.id,
      contentValue: _res
    }).then(res => {
      trainData.value.totalTime = res.data.totalTime
      findTrainUserListInfo()
      if (res.code == 200) {
        sendTrainDisposeInfo('result', { id: userInfo.value.id })
        userConfirmResult.value = true
        findTrainBaoWenInfo(userInfo.value.id, 1)
        if (_res.length > trainData.value['pag']) {
          trainData.value['pag'] = _res.length
        }
        if (trainData.value['pag'] > 1 && trainData.value['currPag'] < trainData.value['pag']) {
          setTimeout(() => {
            findTrainBaoWenInfo(userInfo.value.id, 2)
          }, 500)
        }
      }
    })
  }

  /**
   * 查看训练抄收结果
   * @param user
   */
  const seeTrainResult = user => {
    trainResult.value.user = user
    trainResult.value.curr = 1
    trainResult.value.res = {}
    trainResult.value.existPage = trainData.value['pag']
    if (user.existPageNumber > trainData.value['pag']) {
      trainResult.value.existPage = user.existPageNumber
    }
    findUserTrainBaoWenInfo(user.id, 1)
    if (trainResult.value.existPage > 1 && trainResult.value.curr < trainResult.value.existPage) {
      findUserTrainBaoWenInfo(user.id, 2)
    }
  }

  /**
   * 根据页码查询学员的报文信息
   * @param userId
   * @param pag
   */
  const findUserTrainBaoWenInfo = (userId, pag) => {
    findUserPageBaoWenInfo({
      roomId: Number(route.query.id),
      userId: userId,
      pageNumber: pag
    }).then(res => {
      if (res.code === 200) {
        trainResult.value.res[pag + ''] = res.data
        if (pag == 1) {
          trainResult.value.visible = true
        }
      }
    })
  }

  /**
   * 弹窗切换分页
   * @param type
   */
  const modelPageTurn = type => {
    if (type == 'next') {
      if (trainResult.value.curr == trainResult.value.existPage) {
        return false
      }
      trainResult.value.curr++
    } else {
      if (trainResult.value.curr == 1) {
        return false
      }
      trainResult.value.curr--
    }
    if (!trainResult.value.res[trainResult.value.curr + 1 + ''] && trainResult.value.curr < trainResult.value.existPage) {
      findUserTrainBaoWenInfo(trainResult.value.user.id, trainResult.value.curr + 1)
    }
  }

  /**
   * 学员回来继续抄收
   * @param codeIndex
   * @param currPag
   */
  const againPlayCode = (codeIndex, currPag) => {
    playTips.value.visible = false
    playAgain.value = true
    trainData.value.playCodeIndex = codeIndex
    trainData.value.currPag = currPag
    if (student.value.msg.status == 1) {
      againPlayDisturb()
      playTrainCodeInfo()
    }
  }

  /**
   * 播报干扰
   */
  const againPlayDisturb = () => {
    playAgain.value = false
    disturbList.value.map(item => {
      if (cacheDispose.value.noiseDisturb[item.type] && cacheDispose.value.noiseDisturb[item.type] != null) {
        item.checked = true
        item.volume = cacheDispose.value.noiseDisturb[item.type]
        changeAudioPlay(item, true)
      }
    })
    if (JSON.stringify(cacheDispose.value.codeDisturb) != '{}') {
      for (let y in cacheDispose.value.codeDisturb) {
        if (cacheDispose.value.codeDisturb[y] != null) {
          playTrainCodeDisturbInfo({ dispose: cacheDispose.value.codeDisturb[y] })
        }
      }
    }

    operation({type:'changeVolume',data:parseFloat(student.value.msg.volume) / 100})
    operation({type:'changeFrequency',data:student.value.msg.fre})
    operation({type:'changeCriterion',data:countCriterion(student.value.msg.rate)})
    // voice.changeVolume(student.value.msg.volume)
    // voice.changeFre(student.value.msg.fre)
    // voice.changeCriterion(countCriterion(student.value.msg.rate))
  }

  /**
   * 教员存储训练配置信息
   */
  const teacherStoreTrainDispose = () => {
    if (storefFlag) {
      storefFlag = false
      clearTimeout(storeTimer)
      storeTimer = null
    } else {
      storefFlag = true
      storeTimer = setTimeout(() => {
        storefFlag = false
        clearTimeout(storeTimer)
        storeTimer = null

        updateTrainRoomDispose({
          roomId: roomId,
          setting: JSON.stringify(cacheDispose.value)
        }).then()
      }, 1000)
    }
  }

  return {
    trainTimeRef,
    WSConnect,
    trainData,
    disturbList,
    student,
    joinTrainUser,
    userConfirmResult,
    fillInResult,
    trainResult,
    playTips,
    storage,
    examinerStatus,
    ban,
    cacheDispose,
    openTrainInfo,
    closeTrainInfo,
    pageTurn,
    selectRoadInfo,
    changeMainSignalStatus,
    changeCodeDisturbStatus,
    changeMainSignalItem,
    changeDisturbItem,
    changeCodeDisturbItem,
    fillInTrainResult,
    seeTrainResult,
    againPlayCode,
    allBaoWen,
    modelPageTurn
  }
}

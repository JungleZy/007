import { onMounted, onUnmounted, ref, watch, nextTick, createVNode } from 'vue'
import { sum, deepClone } from '../../../../../../common/utils/Utils.js'
import useMorse from '../../../../../../common/mixin/useMorse.js'
import { message, Modal } from 'ant-design-vue'
import { PubSub } from '../../../../../../common/utils/PubSub'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { getGradingRuleById } from '../../../../../../common/api/GradingRuleApi.js'
import { getPostTelegramMsgBody, beginPostTelegramTrain, resetPostTelegramTrain, savePostTelegramContent, finishPostTelegramTrain } from '../../../../../../common/api/TelegramApi.js'
import { timeFormatInfo } from '../../../../../../common/utils/Utils'
import countPatStandard from './patStandard.js'

export default function (trainData, patStandard, initFloat, wsOnline, devOnline, loading,messageBodyList) {
  const patKeyBoxRef = ref(null) // 字码和词组展示区域的容器
  const patValBoxRef = ref(null) // 拍发电码展示区域的容器
  const trainTimeRef = ref(null) // 训练时间展示区域的容器
  const trainTimer = ref(null) // 记录训练时长的计时器
  const cachePatCode = ref([]) // 缓存电码 - 还未转换成字码的电码集合
  const cachePatLogs = ref([]) // 缓存拍发记录 - 还未转换成字码的电码集合
  const cacheKey = ref([]) // 缓存字码 - 还未转换成词组的字码集合
  const cacheKeyCode = ref([]) // 缓存电码 - 还未转换成词组的电码码集合
  const logsPatStandardCode = ref([]) // 待转换拍发基准值的数据集合
  const cachePatKey = ref([]) // 缓存字码 - 还未翻页提交的字码集合
  const currPatKeyIndex = ref(-1) // 正在拍发的电报纸字码的下标
  const showPatCodeLog = ref([])//展示出来的拍发记录
  const { morseCode, codeKey, dots } = useMorse()
  const { countPatStandardInfo, countAverageStandard } = countPatStandard()
  const router = useRouter()
  const scorePath = ref('')
  const errorText = ref('')
  const speedUnit = ref(true)
  const patNumber = ref(0)
  const initSymbol = ref({
    // 拍发特殊符号的电码值
    machine: '0001,0001,0001', // 试机操作符号
    start: '10001', // 开始符号
    end: '01010', // 结束符号
    turn: '00,00,00', // 翻页符号
    alter: '001100', // 改错符号-当前组
    next: '001011' // 改错符号-前一组
  })
  const antiShakeStatus = ref(true)
  const oldPatStandard = ref({
    dot: 0,
    line: 0,
    c_gap: 0,
    w_gap: 0,
    g_gap: 0
  });
  const pagePatStandard = ref([]);
  const pageHandleIndex = ref(0);
  const finishPatLogs = ref([]);
  const wordTimer = ref(null);
  const groupTimer = ref(null);
  const textTimer = ref(null);
  const firstStandard = ref(false);
  const alter = ref(0);
  const startStatus = ref(false);
  router.getRoutes().forEach(r => {
    if (r.name === 'PatTrainScore') {
      scorePath.value = r.path
    }
  })

  PubSub.subscribe('send_handKeyPostTrainPage', e => {
    if (trainData.value.status === 1) {
      Modal.confirm({
        class: 'init_modal_style',
        content: '当前训练还未结束，是否结束训练？',
        icon: () => createVNode(ExclamationCircleOutlined),
        okType: 'danger',
        okText: () => '结束',
        cancelText: () => '取消',
        maskClosable: true,
        onOk: () => {
          statisticsTelegraphData('end')
          // resetTrainInfo() 结束重置训练
        }
      })
    } else {
      PubSub.publish('callback_handKeyPostTrainPage', true)
    }
  })

  //空格开始
  const keyDownStart = (key)=>{
    if (trainData.value.status === 0 && key.keyCode == 32) {
      beginExerciseInfo();
    }
  }
  onMounted(() => {
    window.addEventListener('beforeunload', e => {
      if (trainData.value.status === 1) {
        resetTrainInfo()
      }
    })

    patKeyBoxRef.value.addEventListener('mousewheel', e => {
      if (e.deltaY > 0) {
        patKeyBoxRef.value.scrollLeft += 100
      } else {
        patKeyBoxRef.value.scrollLeft -= 100
      }
    })

    patValBoxRef.value.addEventListener('mousewheel', e => {
      if (e.deltaY > 0) {
        patValBoxRef.value.scrollLeft += 100
      } else {
        patValBoxRef.value.scrollLeft -= 100
      }
    });

    window.addEventListener('keydown', keyDownStart)
  });

  onUnmounted(() => {
    PubSub.unsubscribe("send_handKeyPostTrainPage");
    clearInterval(trainTimer.value);
    if (trainData.value.status === 1) {
      resetTrainInfo();
    }
    window.removeEventListener('beforeunload', e => {});
    window.removeEventListener("keydown", keyDownStart)
    console.log(111111)
  });

  /**
   * 获取规则评分偏移量
   * @param id
   */
  const getScoreOffsetInfo = id => {
    getGradingRuleById({ id: id }).then(res => {
      if (res.code === 200) {
        let rule = JSON.parse(res.data.content)
        initFloat.value = rule.skew
        speedUnit.value = rule.wpm.type
      }
    })
  }

  /**
   * 获取电报纸的报文内容
   * @param index
   * @param type
   */
  const getPostTrainKeyInfo = (index, type) => {
    getPostTelegramMsgBody({
      id: trainData.value.trainId,
      floorNumber: index
    }).then(res => {
      if (res.code === 200) {
         let print = []
        trainData.value.telegraph[type] = res.data.messageKey
        res.data.messageKey.forEach(key => {
          print.push(JSON.parse(key.moresKey))
        })
        messageBodyList.value.push(print)
        console.log(messageBodyList.value);
      }
    })
  }

  /**
   * 训练时间转换显示
   */
  const initTrainTimeInfo = () => {
    trainTimer.value = setInterval(() => {
      if (!(trainData.value.validTime && trainData.value.validTime > 0)) {
        trainData.value.validTime = 0
      }
      trainData.value.validTime += 1000
      if (!speedUnit.value) {
        console.log(patNumber.value);
        let type = trainData.value.type == 1 ? 'letter' : trainData.value.type == 2 ? 'mix' : trainData.value.codeSort ? 'long' : 'short'
        trainData.value.speed = ((400 * 60 * 1000 * patNumber.value) / (trainData.value.validTime * dots[type])).toFixed(2)
      }
      timeAreaShow(trainData.value.validTime)
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
    trainTimeRef.value.h = h
    trainTimeRef.value.m = m
    trainTimeRef.value.s = s
  }

  /**
   * 切换电报纸
   * @param type
   */
  const switchTelegram = type => {
    if ((type === 'prev' && trainData.value.floorNow <= 1) || (type === 'next' && trainData.value.floorNow >= trainData.value.pag)) return false
    if (type === 'prev') {
      trainData.value.floorNow--
      trainData.value.telegraph.next = deepClone(trainData.value.telegraph.curr)
      trainData.value.telegraph.curr = deepClone(trainData.value.telegraph.prev)
      if (trainData.value.floorNow === 1) {
        trainData.value.telegraph.prev = null
      } else {
        getPostTrainKeyInfo(trainData.value.floorNow - 1, 'prev')
      }
    }
    if (type === 'next') {
      trainData.value.floorNow++
      trainData.value.telegraph.prev = deepClone(trainData.value.telegraph.curr)
      trainData.value.telegraph.curr = deepClone(trainData.value.telegraph.next)
      if (trainData.value.floorNow === trainData.value.pag) {
        trainData.value.telegraph.next = null
      } else {
        getPostTrainKeyInfo(trainData.value.floorNow + 1, 'next')
      }
    }
  }

  /**
   * 处理接收的电报code
   * @param val
   * @param diffTime
   * @param gapTime
   */
  const handleReceiveKeyCode = (val,diffTime,gapTime) => {
    if (loading.value || trainData.value.status == 2) return false;
    let gap = 0,diff = 0;
    if (val === -1) {
      gap = gapTime[1]-gapTime[0];
      if (gap < patStandard.value.codeGap*(1 + initFloat.value/100)) {
        patNumber.value ++;
        trainData.value.countPatSpeedCode.code.push(gap);
      } else if (gap < patStandard.value.wordGap*(1 + initFloat.value/100)) {
        patNumber.value += 3;
        trainData.value.countPatSpeedCode.word.push(gap);
      } else if (gap < patStandard.value.groupGap*(1 + initFloat.value/100)) {
        patNumber.value += 5;
        trainData.value.countPatSpeedCode.group.push(gap);
      }
      if (trainData.value.process === 3) {
        finishPatLogs.value[finishPatLogs.value.length - 1].patLogs.push({
          name: '间隔' + finishPatLogs.value[finishPatLogs.value.length - 1].patLogs.length,
          key: 2,
          value: gap
        })
        cachePatLogs.value.push({
          name: '间隔',
          key: 2,
          value: gap
        })
      }
      if (trainData.value.patCodeLog.length > 0) {
        trainData.value.patCodeLog[trainData.value.patCodeLog.length - 1].gap = gap
      }
      if (cachePatCode.value.length > 0) {
        cachePatCode.value[cachePatCode.value.length - 1].gap = gap
      }
      if (logsPatStandardCode.value.length > 0) {
        logsPatStandardCode.value[logsPatStandardCode.value.length - 1].gap = gap
      }
    } else {
      diff = diffTime[1]-diffTime[0];
      if (diff > 10 && diff <= patStandard.value.dot*(1 + initFloat.value/100)) {
        trainData.value.countPatSpeedCode.dot.push(diff);
      } else if (diff <= patStandard.value.line*(1 + initFloat.value/100)) {
        trainData.value.countPatSpeedCode.line.push(diff);
      }
      patNumber.value += val == 0 ? 1 : 3
      if (trainData.value.process === 3) {
        finishPatLogs.value[finishPatLogs.value.length - 1].patLogs.push({
          name: (val === 0 ? '点' : '划') + finishPatLogs.value[finishPatLogs.value.length - 1].patLogs.length,
          key: val,
          value: diff
        })
        cachePatLogs.value.push({
          name: val === 0 ? '点' : '划',
          key: val,
          value: diff
        })
      }
      /*if (!firstStandard.value && currPatKeyIndex.value == 2) {
        firstStandard.value = true;
        pageResetPatStandard()
      }*/
      // 记录整个训练拍发电码数据
      trainData.value.patCodeLog.push({
        code: val,
        diff: diff,
        gap: 0
      })
      //取出需要展示的拍发记录
      if(trainData.value.patCodeLog.length>70){
         trainData.value.patCodeLog.shift()
      }
      showPatCodeLog.value = trainData.value.patCodeLog
      // 记录还没编译成字码的电码数据
      cachePatCode.value.push({
        code: val,
        diff: diff,
        gap: 0
      })
      if ([1, -1, 3].indexOf(trainData.value.process) > -1) {
        // 记录需要调整拍发基准值的电码数据
        logsPatStandardCode.value.push({
          code: val,
          diff: diff,
          gap: 0
        })
      }
    }
    if (trainData.value.process === -1) {
      trainData.value.process = 1
    }
    if (trainData.value.process === -2) {
      trainData.value.process = 2
    }
    // 验证试机操作配置基准值的拍发手法
    if ((trainData.value.process === 1 || trainData.value.process === -1) && cachePatCode.value.length === 12) {
      verifyMachineSymbol()
    }

    // 验证开始符号的拍发
    if ((trainData.value.process === 2 || trainData.value.process === -2) && cachePatCode.value.length >= 5) {
      // initStartSymbolPat()
      verifyStartSymbol()
    }
    if (wordTimer.value) {
      clearTimeout(wordTimer.value)
      wordTimer.value = null
    }
    if (groupTimer.value) {
      clearTimeout(groupTimer.value)
      groupTimer.value = null
    }
    if (trainData.value.process === 3 && val !== -1) {
      if (cachePatCode.value.length > 0) {
        // 编译成单个字码
        if (!wordTimer.value) {
          wordTimer.value = setTimeout(() => {
            codeCompileKeyInfo('word')
            clearTimeout(wordTimer.value)
            wordTimer.value = null
          }, (patStandard.value.codeGap * (1 + initFloat.value / 100)))
        }

        // 编译成当个词组
        if (!groupTimer.value) {
          groupTimer.value = setTimeout(() => {
            codeCompileKeyInfo('group');
            clearTimeout(groupTimer.value);
            groupTimer.value = null;
          },(patStandard.value.codeGap*(3 + initFloat.value/100)));
        }
      }
    }
    nextTick(() => {
      patValBoxRef.value.scrollLeft = patValBoxRef.value.scrollWidth
    })
  }

  /**
   * 验证试机操作电码
   */
  const verifyMachineSymbol = () => {
    let lineCode,
        lineCode_index = []
    if (initSymbol.value.machine.indexOf(',') > -1) {
      lineCode = initSymbol.value.machine.replace(/,/g, '')
    } else {
      lineCode = initSymbol.value.machine
    }
    lineCode.split('').map((item, i) => {
      if (item === '1') {
        lineCode_index.push(i)
      }
    })
    let dot = cachePatCode.value.filter((item, i) => lineCode_index.indexOf(i) === -1),
        line = cachePatCode.value.filter((item, i) => lineCode_index.indexOf(i) > -1),
        dt = dot.sort((x, y) => x.diff - y.diff),
        lt = line.sort((x, y) => x.diff - y.diff),
        d_median = dt[4].diff,
        l_median = lt[1].diff,
        dg = dot.sort((x, y) => x.gap - y.gap),
        lg = line.sort((x, y) => x.gap - y.gap),
        dg_median = dg[4].gap,
        lg_median = parseInt((lg[1].gap + lg[2].gap) / 2)

    if (
        dt[0].diff >= d_median * (1 - initFloat.value / 100) &&
        d_median * (1 + initFloat.value / 100) >= dt[8].diff &&
        lt[0].diff >= l_median * (1 - initFloat.value / 100) &&
        l_median * (1 + initFloat.value / 100) >= lt[2].diff &&
        dg[0].gap >= dg_median * (1 - initFloat.value / 100) &&
        dg_median * (1 + initFloat.value / 100) >= dg[8].gap &&
        lg[1].gap >= lg_median * (1 - initFloat.value / 100) &&
        lg_median * (1 + initFloat.value / 100) >= lg[2].gap
    ) {
      initPatStandard(dot, line)
    } else {
      trainData.value.process = -1
      cachePatCode.value = []
      if (dt[0].diff < d_median * (1 - initFloat.value / 100)) {
        errorText.value = '试机操作【点】拍发时长小于平均值的偏移量，请重新拍发'
      } else if (dt[8].diff > d_median * (1 + initFloat.value / 100)) {
        errorText.value = '试机操作【点】拍发时长大于平均值的偏移量，请重新拍发'
      } else if (lt[0].diff < l_median * (1 - initFloat.value / 100)) {
        errorText.value = '试机操作【划】拍发时长大于平均值的偏移量，请重新拍发'
      } else if (lt[2].diff > l_median * (1 + initFloat.value / 100)) {
        errorText.value = '试机操作【划】拍发时长大于平均值的偏移量，请重新拍发'
      } else if (dg[0].gap < dg_median * (1 - initFloat.value / 100)) {
        errorText.value = '试机操作【电码间隔】时长小于平均值的偏移量，请重新拍发'
      } else if (dg[8].gap > dg_median * (1 + initFloat.value / 100)) {
        errorText.value = '试机操作【电码间隔】时长大于平均值的偏移量，请重新拍发'
      } else if (lg[1].gap < lg_median * (1 - initFloat.value / 100)) {
        errorText.value = '试机操作【字码间隔】时长小于平均值的偏移量，请重新拍发'
      } else if (lg[2].gap > lg_median * (1 + initFloat.value / 100)) {
        errorText.value = '试机操作【字码间隔】时长大于平均值的偏移量，请重新拍发'
      } else {
        errorText.value = '试机操作电码拍发无效，请重新拍发'
      }
    }
  }

  /**
   * 验证开始操作电码
   */
  const verifyStartSymbol = () => {
    let lineCode = '',
        lineCode_index = []
    if (initSymbol.value.start.indexOf(',') > -1) {
      lineCode = initSymbol.value.start.replace(/,/g, '')
    } else {
      lineCode = initSymbol.value.start
    }
    lineCode.split('').map((item, i) => {
      if (item === '1') {
        lineCode_index.push(i)
      }
    });
    cachePatCode.value = cachePatCode.value.filter((item,i) => i >= cachePatCode.value.length - 5)
    let dot = cachePatCode.value.filter((item,i) => lineCode_index.indexOf(i)===-1),
        line = cachePatCode.value.filter((item,i) => lineCode_index.indexOf(i)>-1),
        dt = dot.map(item=>item.diff).sort((x,y) => x-y),
        lt = line.map(item=>item.diff).sort((x,y) => x-y),
        d_median = parseInt(sum(dt)/dt.length),
        l_median = parseInt(sum(lt)/lt.length),
        dg = dot.map(item=>item.gap).sort((x,y) => x-y),
        dg_median = parseInt(sum(dg)/dg.length);

    if ((dt[0] >= d_median*(1 - initFloat.value/100) && d_median*(1 + initFloat.value/100) >= dt[dt.length-1]) &&
        (lt[0] >= l_median*(1 - initFloat.value/100) && l_median*(1 + initFloat.value/100) >= lt[lt.length-1]) &&
        (dg[0] >= dg_median*(1 - initFloat.value/100) && dg_median*(1 + initFloat.value/100) >= dg[dg.length-1])) {
      if (l_median > d_median*2 && lt[0] > d_median*2) {
        patStandard.value.dot = d_median;
        patStandard.value.line = l_median;
        patStandard.value.codeGap = dg_median>d_median?dg_median:d_median;
        patStandard.value.wordGap = parseInt(dg_median * 3);
        patStandard.value.groupGap = parseInt(dg_median * 5);

        trainData.value.process = 3;
        currPatKeyIndex.value = 0;
        trainData.value.patKeyVal = [];
        trainData.value.patKeyVal.push(['开始']);
        trainData.value.patKeyVal.push([]);
        trainData.value.validTime = 0;
        patNumber.value = 0;
        startStatus.value = true;
        oldPatStandard.value = {
          dot: patStandard.value.dot,
          line: patStandard.value.line,
          c_gap: patStandard.value.codeGap,
          w_gap: patStandard.value.wordGap,
          g_gap: patStandard.value.groupGap
        }
        finishPatLogs.value.push({
          dot: patStandard.value.dot,
          line: patStandard.value.line,
          codeGap: patStandard.value.codeGap,
          wordGap: patStandard.value.wordGap,
          groupGap: patStandard.value.groupGap,
          offSize: initFloat.value,
          patLogs: []
        })
        cachePatCode.value = [];
        if (trainTimer.value) {
          clearInterval(trainTimer.value);
        }
        initTrainTimeInfo();
      } else {
        trainData.value.process = -2;
      }
    } else {
      trainData.value.process = -2;
    }
  }

  /**
   * 验证拍发试机符的点划比
   * @param dot
   * @param line
   */
  const initPatStandard = (dot, line) => {
    let d_t = 0,
        l_t = 0,
        i_t = 0,
        g_t = 0,
        scale
    dot.map(item => {
      d_t += parseInt(item.diff)
      i_t += parseInt(item.gap)
    })
    line.map(item => {
      l_t += parseInt(item.diff)
      g_t += parseInt(item.gap)
    })
    scale = parseInt(l_t / line.length) / parseInt(d_t / dot.length)
    if (scale >= 2) {
      trainData.value.process = 2
      cachePatCode.value = []
      logsPatStandardCode.value = []
      finishPatLogs.value = []
      beginTrainInfo()
    } else {
      errorText.value = '拍发点、划比例太小，请重新拍发'
      trainData.value.process = -1
      cachePatCode.value = []
    }
  }

  /**
   * 处理电报正文拍发的基准值
   */
  const resetPatStandard = () => {
    let rePat = countAverageStandard(oldPatStandard,pagePatStandard);

    finishPatLogs.value.push({
      dot: rePat.dot,
      line: rePat.line,
      codeGap: rePat.codeGap,
      wordGap: rePat.wordGap,
      groupGap: rePat.groupGap,
      offSize: initFloat.value,
      patLogs: []
    })
    oldPatStandard.value = {
      dot: rePat.dot,
      line: rePat.line,
      c_gap: rePat.codeGap,
      w_gap: rePat.wordGap,
      g_gap: rePat.groupGap,
    };
  };

  /**
   * 电报纸正文内拍发基准值校准
   */
  const pageResetPatStandard = turn => {
    let arr = logsPatStandardCode.value.filter((item, i) => i >= pageHandleIndex.value)
    pageHandleIndex.value = logsPatStandardCode.value.length

    patStandard.value = countPatStandardInfo(arr, patStandard, initFloat)

    pagePatStandard.value.push({
      dot: patStandard.value.dot,
      line: patStandard.value.line,
      codeGap: patStandard.value.codeGap,
      wordGap: patStandard.value.wordGap,
      groupGap: patStandard.value.groupGap,
      offSize: initFloat.value
    })

    if (turn === 'turn') {
      resetPatStandard()
    }
  }

  /**
   * 电码信号转码成字符
   */
  let numI = 0
  const codeCompileKeyInfo = type => {
    let code = cachePatCode.value.map(item => item.code).join(''),
        short = (trainData.value.type===1?'letter':trainData.value.type===2?'mix':trainData.value.codeSort?'long':'short'),
        codeInit = codeKey[short],
        len = trainData.value.patKeyVal.length,
        lastPatKey;
    if (currPatKeyIndex.value == -1) {
      code = code.slice(-5,code.length)
    }
    if (code === initSymbol.value.start && trainData.value.floorNow == 1 && currPatKeyIndex.value <= 2) {
      trainData.value.patKeyVal = [];
      trainData.value.patKeyVal.push(['开始']);
      trainData.value.patKeyVal.push([]);
      cachePatCode.value = [];
      currPatKeyIndex.value = 0;
      cachePatKey.value = [];
      trainData.value.telegraph.curr[0].moresValue = [];
      trainData.value.telegraph.curr[0].moresTime = [];
      trainData.value.telegraph.curr[0].patKeys = [];
      trainData.value.telegraph.curr[0].patLogs = [];
      trainData.value.telegraph.curr[1].moresValue = [];
      trainData.value.telegraph.curr[1].moresTime = [];
      trainData.value.telegraph.curr[1].patKeys = [];
      trainData.value.telegraph.curr[1].patLogs = [];
      trainData.value.validTime = 0;
      patNumber.value = 0;
      startStatus.value = true;
    }
    else if (code === initSymbol.value.start && trainData.value.floorNow > 1 && currPatKeyIndex.value == -1) {
      trainData.value.patKeyVal.push(['开始']);
      trainData.value.patKeyVal.push([]);
      cachePatCode.value = [];
      currPatKeyIndex.value = 0;
      cachePatKey.value = [];
      trainData.value.telegraph.curr[0].moresValue = [];
      trainData.value.telegraph.curr[0].moresTime = [];
      trainData.value.telegraph.curr[0].patKeys = [];
      trainData.value.telegraph.curr[0].patLogs = [];
      startStatus.value = true;
    }
    else if (type === 'word' && startStatus.value) {
      if (alter.value ===3) {
        alter.value = 0;
      }
      cacheKeyCode.value.push(code);
      let cacheArr1 = cacheKeyCode.value.filter((ii,i) => i>=cacheKeyCode.value.length-1)
      let cacheArr2 = cacheKeyCode.value.filter((ii,i) => i>=cacheKeyCode.value.length-2)
      let cacheArr3 = cacheKeyCode.value.filter((ii,i) => i>=cacheKeyCode.value.length-3)
      if (cacheArr1.join(',') === initSymbol.value.end) {
        statisticsTelegraphData('autoEnd');
        return false;
      }
      console.log(cacheKeyCode.value);
      if (cacheArr3.join(',') === initSymbol.value.turn||cacheArr3.join(',') ==='00,0000'||cacheArr3.join(',') ==='0000,00') {//句号翻页

        //去掉翻页#号
        trainData.value.telegraph.curr[currPatKeyIndex.value].patKeys = trainData.value.telegraph.curr[currPatKeyIndex.value].patKeys.splice(0,trainData.value.telegraph.curr[currPatKeyIndex.value].patKeys.length-2)
        // 去掉翻页展示框中最后一组的 #
        let endRow = trainData.value.patKeyVal[trainData.value.patKeyVal.length-1]
        let arr = endRow[endRow.length-1].split('')
        arr = arr.splice(0,arr.length-2)
        let s = ''
        arr.forEach(item => {
          s+=item
        })
        endRow[endRow.length-1] = s
        //
        if (trainData.value.floorNow == trainData.value.pag) {
          statisticsTelegraphData('autoEnd');
          return false;
        } else {
          statisticsTelegraphData('turn');
          return false;
        }
      }

      if (code === initSymbol.value.alter) {
        alter.value = 3;
        cacheKey.value.push('?');
        updateMoresKeyInfo('?')
      } else if (cacheArr2.join(',') === initSymbol.value.next) {
        alter.value = 1;
        // cacheKey.value.pop();
        // 删除上一组编译错误数据
        // trainData.value.telegraph.curr[currPatKeyIndex.value].patKeys.pop()
        // trainData.value.patKeyVal.pop()
        // trainData.value.patKeyVal[trainData.value.patKeyVal.length-1].pop()
        // debugger
        // debugger
        cacheKey.value.push('/');
        // "/"改错出现在每一组的开头下标减2
        if(cacheKey.value[0]==='/'){
          currPatKeyIndex.value -=2;
          numI = 2
        }else {
          currPatKeyIndex.value --
        }
        // currPatKeyIndex.value--
        if(currPatKeyIndex.value<0){
          currPatKeyIndex.value = 0
        }
        if(cacheKey.value.length===1){
          // trainData.value.patKeyVal[trainData.value.patKeyVal.length-1].pop()
        }
        updateMoresKeyInfo('?','error')
        console.log(trainData.value.telegraph.curr);
      } else {
        cacheKey.value.push(codeInit[code]!=undefined&&code!=initSymbol.value.start?codeInit[code]:'#');
        updateMoresKeyInfo(codeInit[code]!=undefined&&code!=initSymbol.value.start?codeInit[code]:'#')
      }

      lastPatKey = trainData.value.patKeyVal[len-1];
      if (cacheKey.value.length === 1) {
        lastPatKey.push(cacheKey.value.join(''));
        cachePatKey.value.push(cacheKey.value.join(''));
      } else {
        lastPatKey[lastPatKey.length - 1] = cacheKey.value.join('')
        cachePatKey.value[cachePatKey.value.length - 1] = cacheKey.value.join('')
      }
    }
    else if (type === 'group' && startStatus.value) {
      if (cacheKey.value.length >= 3 || currPatKeyIndex.value % 2 == 0) {
        pageResetPatStandard()
      }
      if (alter.value === 2) {
        updateMoresKeyInfo('?')
        alter.value = 0;
      }

      lastPatKey = trainData.value.patKeyVal[len-1];
      if (alter.value===1) {
        alter.value = 2;
        // 改错前一组恢复下标变量赋值
        if(numI===0){
          numI = 1
        }
      }

      if (lastPatKey.length > 1) {
        lastPatKey[lastPatKey.length - 1] = cacheKey.value.join('');
      }
      if (cachePatKey.value.length > 1) {
        cachePatKey.value[cachePatKey.value.length - 1] = cacheKey.value.join('');
      }
      if (alter.value === 0) {
        if (cachePatKey.value.length > 0) {
          // 改错前一组恢复下标
          // if(numI===1){
          //   currPatKeyIndex.value+=1;
          //   numI = 0
          // }else if(numI === 2){
          //   // currPatKeyIndex.value+=2;
          //   numI = 0
          // }else {
          //   currPatKeyIndex.value ++;
          // }
          currPatKeyIndex.value ++;
          trainData.value.telegraph.curr[currPatKeyIndex.value].patKeys=[]
        }
        cacheKey.value = [];
        cacheKeyCode.value = [];
      }
      if (alter.value ===3) {
        alter.value = 0;
      }
    }
    if (patKeyBoxRef.value) {
      patKeyBoxRef.value.scrollTop = patKeyBoxRef.value.scrollHeight
    }
  }

  /**
   * 更新待提交的字码数据
   */
  const updateMoresKeyInfo = (key,type=' ') => {
    console.log(key,type,currPatKeyIndex.value);
    let item = trainData.value.telegraph.curr[currPatKeyIndex.value];
    let code = cachePatCode.value.map(c => c.code);
    let diff = cachePatCode.value.map(c => c.diff);
    if (item) {
      if (typeof item.patKeys === 'string' && item.patKeys != '#') {
        item.patKeys = JSON.parse(item.patKeys);
      }
      if (typeof item.moresValue === 'string') {
        item.moresValue = JSON.parse(item.moresValue);
      }
      if (typeof item.patLogs === 'string') {
        item.patLogs = JSON.parse(item.patLogs);
      }
      if (typeof item.moresTime === 'string') {
        item.moresTime = JSON.parse(item.moresTime);
      }

      item.patKeys.push(key);
      item.moresValue.push(code);
      item.patLogs.push(cachePatLogs.value);
      item.moresTime.push(diff);
    } else if (currPatKeyIndex.value > 99) {
      trainData.value.telegraph.curr.push({
        moresKey: '#',
        patKeys: [key],
        moresValue: [code],
        patLogs: [cachePatLogs.value],
        moresTime: [diff],
      })
    }

    cachePatCode.value = [];
    cachePatLogs.value = [];
  }

  /**
   * 开始预练习
   */
  const beginExerciseInfo = () => {
    if(!wsOnline.value) {message.error('报训软件未连接!'); return false;}
    if(!devOnline.value) {message.error('电子键设备未连接！'); return false;}
    trainData.value.process = 2;
    beginTrainInfo();
  };

  /**
   * 重拍试机操作
   */
  const resetPatMachine = () => {
    trainData.value.process = 1
    cachePatCode.value = []
    trainData.value.patCodeLog = []
  }

  /**
   * 开始训练
   */
  const beginTrainInfo = () => {
    beginPostTelegramTrain({
      id: trainData.value.trainId
    }).then(res => {
      if (res.code === 200) {
        trainData.value.startTime = res.data.startTime
        trainData.value.status = res.data.status
      } else {
        message.error(res.message)
      }
    })
  }

  /**
   * 结束训练
   * @param type
   */
  const finishTrainInfo = type => {
    let turnLen = initSymbol.value.turn.split(',').length,
        endLen = initSymbol.value.end.split(',').length
    finishPatLogs.value = finishPatLogs.value.filter(item => item.patLogs.length > 0)
    finishPatLogs.value.map((item, i) => {
      if (i < finishPatLogs.value.length - 1) {
        item.patLogs = item.patLogs.filter((code, c) => c < item.patLogs.length - (turnLen * 2 - 1))
      }
      if (type === 'autoEnd' && i === finishPatLogs.value.length - 1) {
        item.patLogs = item.patLogs.filter((code, c) => c < item.patLogs.length - (endLen * 2 - 1))
      }
    })
    loading.value = true
    finishPostTelegramTrain(  {
      id: trainData.value.trainId,
      validTime: trainData.value.validTime,
      finishInfo: finishPatLogs.value,
      speed:trainData.value.speed?trainData.value.speed+'':''
    }).then(res => {
      loading.value = false
      if (res.code === 200) {
        trainData.value.status = 2
        clearInterval(trainTimer.value)
        router.push({ path: scorePath.value, query: { id: trainData.value.trainId } })
      } else {
        message.error(res.message)
      }
    })
  }

  /**
   * 统计每页电报纸报文内容的正确性
   * @param type
   */
  const statisticsTelegraphData = (type) => {
    let short = (trainData.value.type===0&&!trainData.value.codeSort?'short':'mix'),
        countObj = trainData.value.countPatSpeedCode,codeArr = '',codeArr1 = '',codeArr3 = '',error = 0,
        total = trainData.value.messageNumber;

    trainData.value.telegraph.curr.map((item,ci) => {
      if (typeof item.moresKey === 'string' && item.moresKey != '#') {
        item.moresKey = JSON.parse(item.moresKey);
      }
      if (typeof item.patKeys === 'string') {
        item.patKeys = JSON.parse(item.patKeys)
      }
      if (typeof item.moresValue === 'string') {
        item.moresValue = JSON.parse(item.moresValue)
      }
      if (typeof item.patLogs === 'string') {
        item.patLogs = JSON.parse(item.patLogs)
      }
      if (typeof item.moresTime === 'string') {
        item.moresTime = JSON.parse(item.moresTime)
      }
      if (item.moresKey === '#' || item.moresValue.length !== item.moresKey.length || item.moresKey.some((key, k) => item.moresValue[k].join('') !== morseCode[short][key].value)) {
        error++
      }
      if (typeof item.moresKey !== 'string') {
        item.moresKey = JSON.stringify(item.moresKey)
      }
      codeArr = item.moresValue.map(k => k.join(''));
      codeArr1 = codeArr.filter((subI,i) => i>=codeArr.length-1);
      codeArr3 = codeArr.filter((subI,i) => i>=codeArr.length-3);
      if (codeArr3.join(',') === initSymbol.value.turn) {
        item.moresValue = item.moresValue.filter((ii,i) => i<item.moresValue.length-3);
        item.moresTime = item.moresTime.filter((ii,i) => i<item.moresValue.length-3);
        item.patKeys = item.patKeys.filter((ii,i) => i<item.moresValue.length-3);
        item.patLogs = item.patLogs.filter((ii,i) => i<item.moresValue.length-3);
      }
      if (codeArr1.join(',') === initSymbol.value.end) {
        item.moresValue = item.moresValue.filter((ii,i) => i<item.moresValue.length-1);
        item.moresTime = item.moresTime.filter((ii,i) => i<item.moresValue.length-1);
        item.patKeys = item.patKeys.filter((ii,i) => i<item.moresValue.length-1);
        item.patLogs = item.patLogs.filter((ii,i) => i<item.moresValue.length-1);
      }

    });
    error = (error>100?100:error);
    trainData.value.errorNumber += error;
    if (total >= trainData.value.errorNumber) {
      trainData.value.accuracy = parseFloat((total - trainData.value.errorNumber) / total).toFixed(2)
    } else {
      trainData.value.accuracy = '0.00'
    }
    countObj.WPM = (sum(countObj.dot) / countObj.dot.length + sum(countObj.line) / (countObj.line.length * 3) + sum(countObj.code) / countObj.code.length + sum(countObj.word) / (countObj.word.length * 3) + sum(countObj.group) / (countObj.group.length * 5)) / 5
    if (speedUnit.value) {
      trainData.value.speed = parseFloat(1200 / countObj.WPM).toFixed(2)
    }

    pageResetPatStandard('turn')
    savePatTelegraphBody(type)
  }

  /**
   * 保存一页电报纸拍发内容
   * @param type
   */
  const savePatTelegraphBody = type => {
    trainData.value.telegraph.curr.map(item => {
      item.moresTime = JSON.stringify(item.moresTime)
      item.moresValue = JSON.stringify(item.moresValue)
      item.patKeys = JSON.stringify(item.patKeys)
      item.patLogs = JSON.stringify(item.patLogs)
    })
    let finish = finishPatLogs.value[trainData.value.floorNow - 1]
    if (trainData.value.floorNow > 1) {
      let turnLen = initSymbol.value.turn.split(',').length
      finish.patLogs = finish.patLogs.filter((code, c) => c < finish.patLogs.length - (turnLen * 2 - 1))
    }
    savePostTelegramContent({
      trainId: trainData.value.trainId,
      floorNumber: trainData.value.floorNow,
      messageBody: trainData.value.telegraph.curr,
      validTime: trainData.value.validTime,
      errorNumber: trainData.value.errorNumber,
      speed: trainData.value.speed,
      accuracy: trainData.value.accuracy,
      standard: pagePatStandard.value,
      finishInfo: JSON.stringify(finish)
    }).then(res => {
      if (res.code === 200) {
        pagePatStandard.value = [{
          dot: patStandard.value.dot,
          line: patStandard.value.line,
          codeGap: patStandard.value.codeGap,
          wordGap: patStandard.value.wordGap,
          groupGap: patStandard.value.groupGap,
          offSize: initFloat.value,
        }];
        finishPatLogs.value[trainData.value.floorNow - 1].patLogs = [{
          name:'点',
          key:0,
          value:0
        }]
        startStatus.value = false;
        if (type == 'turn') {
          if (trainData.value.patKeyVal.length > 0) {
            trainData.value.patKeyVal.push(['句号']);
            trainData.value.patKeyVal.push([]);
            currPatKeyIndex.value = -1;
            cachePatKey.value = [];
            if (trainData.value.floorNow < trainData.value.pag) {
              switchTelegram('next')
            }
          }
        } else {
          trainData.value.patKeyVal.push(['完结']);
          finishTrainInfo(type);
        }
      } else {
        message.error(res.message)
      }
    })
  }

  /**
   * 退出页面重置训练
   */
  const resetTrainInfo = () => {
    if (trainData.value.status !== 1) return false
    trainData.value.status = 0
    resetPostTelegramTrain({
      id: trainData.value.trainId
    }).then(res => {
      if (res.code === 200) {
        trainData.value.status = 0
        trainData.value.errorNumber = 1
        trainData.value.speed = '0'
        trainData.value.accuracy = '0'
        finishPatLogs.value = []
        PubSub.publish('callback_handKeyPostTrainPage', true)
      } else {
        message.error(res.message)
      }
    })
  }

  return {
    patKeyBoxRef,
    patValBoxRef,
    trainTimeRef,
    initSymbol,
    errorText,
    speedUnit,
    currPatKeyIndex,
    getPostTrainKeyInfo,
    switchTelegram,
    handleReceiveKeyCode,
    beginExerciseInfo,
    resetPatMachine,
    timeAreaShow,
    statisticsTelegraphData,
    getScoreOffsetInfo,
    resetTrainInfo,
    showPatCodeLog
  }
}

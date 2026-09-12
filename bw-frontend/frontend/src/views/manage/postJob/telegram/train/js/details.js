import { onMounted, onUnmounted, ref, watch, nextTick, createVNode } from 'vue'
import { sum, deepClone } from '../../../../../../common/utils/Utils.js'
import useMorse from '../../../../../../common/mixin/useMorse.js'
import { message, Modal } from 'ant-design-vue'
import { PubSub } from '../../../../../../common/utils/PubSub'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { getGradingRuleById } from '../../../../../../common/api/GradingRuleApi.js'
import { getPostTelegramMsgBody, beginPostTelegramTrain, savePostTelegramContent, finishPostTelegramTrain } from '../../../../../../common/api/TelegramApi.js'
import { timeFormatInfo } from '../../../../../../common/utils/Utils'
import countPatStandard from './patStandard.js'
import useConfirmedSubmission from '../../../../../../common/mixin/useConfirmedSubmission'
import useTrainingCapture from '../../../../../../common/mixin/useTrainingCapture'

export default function (trainData, patStandard, initFloat, wsOnline, devOnline, loading,messageBodyList) {
  const submission = useConfirmedSubmission()
  const capture = useTrainingCapture()
  const snapshotKey = () => `personal-handkey:pending:${trainData.value.trainId}`
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
  const { morseCode, codeKey } = useMorse()
  const { countPatStandardInfo, countAverageStandard } = countPatStandard()
  const router = useRouter()
  const scorePath = ref('')
  const errorText = ref('')
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
    clearTimeout(wordTimer.value)
    clearTimeout(groupTimer.value)
    clearTimeout(textTimer.value)
    controlCandidates = []
    capture.close()
    clearInterval(trainTimer.value)
    PubSub.unsubscribe('send_handKeyPostTrainPage')
    window.removeEventListener('keydown', keyDownStart)
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
      }
    })
  }

  /**
   * 获取电报纸的报文内容
   * @param index
   * @param type
   */
  const getPostTrainKeyInfo = (index, type) => {
    capture.close()
    const saved = type === 'curr' ? submission.loadSnapshot(snapshotKey()) : null
    if (saved) index = saved.payload.floorNumber
    return submission.run(async request => {
      const response = await request(config => getPostTelegramMsgBody({id: trainData.value.trainId, floorNumber: index}, config))
      trainData.value.telegraph[type] = saved?.payload.attempt === response.data.attempt ? deepClone(saved.payload.messageBody) : response.data.messageKey
      if (type === 'curr') {
        trainData.value.floorNow = index
        capture.bind(response.data)
        if (trainData.value.status === 1) {
          trainData.value.process = 2
          if (!saved) capture.open()
        }
      }
      messageBodyList.value.push(response.data.messageKey.map(item => item.moresKey === '#' ? ['#'] : JSON.parse(item.moresKey)))
      if (saved) submission.defer(() => savePatTelegraphBody(saved.type), true)
    })
  }

  /**
   * 训练时间转换显示
   */
  const initTrainTimeInfo = () => {
    clearInterval(trainTimer.value)
    trainTimer.value = setInterval(() => {
      if (!(trainData.value.validTime && trainData.value.validTime > 0)) {
        trainData.value.validTime = 0
      }
      trainData.value.validTime = capture.elapsed()
      trainData.value.speed = trainData.value.validTime > 0
        ? (patNumber.value * 60000 / trainData.value.validTime).toFixed(1) : '0'
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
    if ([1, 3].includes(trainData.value.status)) return
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
  const handleReceiveKeyCode = (val,diffTime,gapTime,event = {}) => {
    if (trainData.value.status !== 1) return
    if (submission.pending() && !event.captureSpan) event = Object.freeze({...event, captureSpan: capture.stamp(event.startedAt ?? event.receivedAt, event.receivedAt)})
    if (submission.defer(() => handleReceiveKeyCode(val, diffTime, gapTime, event))) return
    if (event.captureSpan) {
      capture.recordQueued(event.captureSpan)
      capture.open()
    } else capture.open(event.startedAt ?? event.receivedAt)
    if (val === -1) {
      const gap = gapTime[1] - gapTime[0]
      if (gap > patStandard.value.codeGap * (1 + initFloat.value / 100)) codeCompileKeyInfo('word')
      if (gap > patStandard.value.codeGap * (3 + initFloat.value / 100)) codeCompileKeyInfo('group')
      if (submission.pending()) return
    }
    if (loading.value || trainData.value.status == 2) return false;
    let gap = 0,diff = 0;
    if (val === -1) {
      gap = gapTime[1]-gapTime[0];
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
  let controlCandidates = []
      const patterns = [
        ['start', ['10001']], ['end', ['01010']], ['alter', ['001100']],
        ['next', ['001011']], ['next', ['0010', '11']],
        ['turn', ['000000']], ['turn', ['00', '00', '00']],
        ['turn', ['00', '0000']], ['turn', ['0000', '00']]
      ]
  const codeCompileKeyInfo = type => {
    if (submission.busy.value || submission.error.value) return
    const code = cachePatCode.value.map(item => item.code).join('')
    const alphabet = trainData.value.type === 1 ? 'letter' : trainData.value.type === 2 ? 'mix' : trainData.value.codeSort ? 'long' : 'short'
    if (type === 'word' && code) {
      const match = patterns.find(([, parts]) => {
        if (parts[parts.length - 1] !== code || parts.length > controlCandidates.length + 1) return false
        return parts.slice(0, -1).every((part, i) => controlCandidates[controlCandidates.length - parts.length + 1 + i].code === part)
      })
      if (match) {
        const [action, parts] = match
        const prior = controlCandidates.slice(controlCandidates.length - parts.length + 1)
        const correctionSignal = prior.flatMap(item => item.signal).concat(cachePatCode.value)
        const correctionLogs = prior.flatMap(item => item.logs).concat(cachePatLogs.value)
        for (let i = prior.length - 1; i >= 0; i--) prior[i].undo()
        if (prior.length) currPatKeyIndex.value = prior[0].groupIndex
        controlCandidates = []
        cachePatCode.value = []
        cachePatLogs.value = []
        if (action === 'start') {
          if (!startStatus.value || currPatKeyIndex.value < 0) {
            startStatus.value = true
            currPatKeyIndex.value = 0
            cacheKey.value = []
            trainData.value.patKeyVal.push(['开始'], [])
          }
          return
        }
        if (!startStatus.value) return
        if (action === 'end' || action === 'turn') {
          statisticsTelegraphData(action === 'end' || trainData.value.floorNow === trainData.value.pag ? 'autoEnd' : 'turn')
          return
        }
        // Corrections operate on existing groups, including the first group.
        const occupied = cacheKey.value.length > 0
        currPatKeyIndex.value = Math.max(0, currPatKeyIndex.value - (action === 'next' ? (occupied ? 1 : 2) : (occupied ? 0 : 1)))
        cacheKey.value = []
        cachePatCode.value = correctionSignal
        cachePatLogs.value = correctionLogs
        updateMoresKeyInfo('?')
        trainData.value.patKeyVal.push([action === 'next' ? '/' : '?'], [])
        return
      }
      if (!startStatus.value) { cachePatCode.value = []; cachePatLogs.value = []; return }
      const key = codeKey[alphabet][code] ?? '#'
      const groupIndex = Math.max(0, currPatKeyIndex.value)
      currPatKeyIndex.value = groupIndex
      const keys = cacheKey.value
      const keyIndex = keys.length
      keys.push(key)
      const page = trainData.value.telegraph.curr
      const before = page[groupIndex]
      const valueIndex = before ? (typeof before.patKeys === 'string' ? JSON.parse(before.patKeys).length : before.patKeys.length) : 0
      const signal = cachePatCode.value
      const logs = cachePatLogs.value
      updateMoresKeyInfo(key)
      const characters = Array.from(key).filter(character => !/[\s?.。]/u.test(character)).length
      patNumber.value += characters
      const item = page[groupIndex]
      let row = trainData.value.patKeyVal[trainData.value.patKeyVal.length - 1]
      if (!row) { row = []; trainData.value.patKeyVal.push(row) }
      const rowIndex = keyIndex === 0 ? row.length : row.length - 1
      row[rowIndex] = keys.join('')
      controlCandidates.push({code, groupIndex, signal, logs, undo: () => {
        // Remove only this token's own provisional entry, never unrelated '#'.
        for (const field of ['patKeys', 'moresValue', 'moresTime', 'patLogs']) item[field].splice(valueIndex, 1)
        patNumber.value -= characters
        keys.splice(keyIndex, 1)
        row[rowIndex] = keys.join('')
      }})
      if (controlCandidates.length > 2) controlCandidates.shift()
    } else if (type === 'group' && startStatus.value && cacheKey.value.length) {
      pageResetPatStandard()
      currPatKeyIndex.value++
      cacheKey.value = []
    }
    if (patKeyBoxRef.value) patKeyBoxRef.value.scrollTop = patKeyBoxRef.value.scrollHeight
  }

  /**
   * 更新待提交的字码数据
   */
  const updateMoresKeyInfo = (key,type=' ') => {
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
    } else {
      trainData.value.telegraph.curr[currPatKeyIndex.value] = {
        moresKey: '#', patKeys: [key], moresValue: [code],
        patLogs: [cachePatLogs.value], moresTime: [diff]
      }
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
  const beginTrainInfo = () => submission.run(async request => {
    const current = await request(config => getPostTelegramMsgBody({id: trainData.value.trainId, floorNumber: trainData.value.floorNow}, config))
    const response = await request(config => beginPostTelegramTrain({id: trainData.value.trainId, attempt: current.data.attempt}, config))
    capture.bind({...current.data, serverElapsedMs: response.data.serverElapsedMs, attempt: response.data.attempt, protocolVersion: response.data.protocolVersion})
    capture.open()
    trainData.value.startTime = response.data.startTime
    trainData.value.status = response.data.status
    const saved = submission.loadSnapshot(snapshotKey())
    if (saved) {
      message.warning('存在尚未确认的提交，请使用结束/重试提交，不会重新覆盖原始记录')
    }
  })

  /**
   * 结束训练
   * @param type
   */
  const finishTrainInfo = async request => {
    await request(config => finishPostTelegramTrain({id: trainData.value.trainId, attempt: capture.metadata.value.attempt}, config))
    trainData.value.status = 2
    clearInterval(trainTimer.value)
    router.push({path: scorePath.value, query: {id: trainData.value.trainId}})
  }

  /**
   * 统计每页电报纸报文内容的正确性
   * @param type
   */
  const statisticsTelegraphData = type => {
    if (submission.error.value || submission.busy.value) return submission.retry()
    if (cachePatCode.value.length) codeCompileKeyInfo('word')
    if (submission.error.value || submission.busy.value) return
    return savePatTelegraphBody(type)
  }

  /**
   * 保存一页电报纸拍发内容
   * @param type
   */
  const savePatTelegraphBody = type => {
    clearTimeout(wordTimer.value)
    clearTimeout(groupTimer.value)
    const saved = submission.loadSnapshot(snapshotKey())
    const pageNumber = saved?.payload.floorNumber ?? trainData.value.floorNow
    let payload = saved?.payload
    if (saved) type = saved.type
    if (!payload) {
      const messageBody = deepClone(trainData.value.telegraph.curr)
      for (const item of messageBody) {
        for (const field of ['moresTime', 'moresValue', 'patKeys', 'patLogs', 'moresKey']) {
          if (typeof item[field] !== 'string') item[field] = JSON.stringify(item[field])
        }
      }
      payload = {trainId: trainData.value.trainId, floorNumber: pageNumber, messageBody,
        ...capture.snapshot(), standard: deepClone(pagePatStandard.value.length ? pagePatStandard.value : [{...patStandard.value, offSize: initFloat.value}]),
        finishInfo: JSON.stringify(finishPatLogs.value[pageNumber - 1] ?? {...patStandard.value, offSize: initFloat.value, patLogs: []})}
    }
    submission.saveSnapshot(snapshotKey(), {type, payload})
    let uploaded = false
    return submission.run(async request => {
      if (payload.attempt !== capture.metadata.value?.attempt) throw new Error('训练轮次已变化，旧记录不会提交到新轮次')
      if (!uploaded) {
        await request(config => savePostTelegramContent(payload, config))
        uploaded = true
      }
      if (type === 'turn' && pageNumber < trainData.value.pag) {
        startStatus.value = false
        const next = await request(config => getPostTelegramMsgBody({id: trainData.value.trainId, floorNumber: pageNumber + 1}, config))
        const previous = trainData.value.telegraph.curr
        capture.bind(next.data)
        capture.open()
        trainData.value.telegraph.curr = next.data.messageKey
        trainData.value.telegraph.prev = previous
        currPatKeyIndex.value = -1
        cacheKey.value = []
        cachePatKey.value = []
        cacheKeyCode.value = []
        cachePatCode.value = []
        cachePatLogs.value = []
        controlCandidates = []
        trainData.value.patKeyVal.push(['句号'], [])
        trainData.value.floorNow = pageNumber + 1
        pagePatStandard.value = []
        finishPatLogs.value[pageNumber] = {...patStandard.value, offSize: initFloat.value, patLogs: []}
      } else {
        await finishTrainInfo(request)
      }
      submission.clearSnapshot(snapshotKey())
    })
  }


  return {
    submissionError: submission.error, submissionBusy: submission.busy, retrySubmit: submission.retry,
    patKeyBoxRef,
    patValBoxRef,
    trainTimeRef,
    initSymbol,
    errorText,
    currPatKeyIndex,
    getPostTrainKeyInfo,
    switchTelegram,
    handleReceiveKeyCode,
    beginExerciseInfo,
    resetPatMachine,
    timeAreaShow,
    statisticsTelegraphData,
    getScoreOffsetInfo,
    showPatCodeLog
  }
}

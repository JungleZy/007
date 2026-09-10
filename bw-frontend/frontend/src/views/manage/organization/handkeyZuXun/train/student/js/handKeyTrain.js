import { onMounted, onUnmounted, ref, watch, nextTick, inject } from 'vue'
import {sum,deepClone} from "../../../../../../../common/utils/Utils.js";
import useMorse from "../../../../../../../common/mixin/useMorse.js";
import {message, Modal} from "ant-design-vue";
import {useRouter} from "vue-router";
import PublicSocket from '../../../../../../../common/ws/PublicSocket.js'
import {getGradingRuleById} from "../../../../../../../common/api/GradingRuleApi.js";
import {
  getHandKeyZuXunPageNumber,saveHandKeyZuXunData,finishHandKeyZuXunTrain,resetHandKeyZuXunTrain,startTrainUser
} from "../../../../../../../common/api/handkeyZuXun.js";
import countPatStandard from "./patStandard.js";

export default function (trainData,patStandard,initFloat,wsOnline,devOnline,loading,emits) {
  const patKeyBoxRef = ref(null); // 字码和词组展示区域的容器
  const patValBoxRef = ref(null); // 拍发电码展示区域的容器
  const trainTimeRef = ref(null); // 训练时间展示区域的容器
  const trainTimer = ref(null); // 记录训练时长的计时器
  const cachePatCode = ref([]); // 缓存电码 - 还未转换成字码的电码集合
  const cachePatLogs = ref([]); // 缓存拍发记录 - 还未转换成字码的电码集合
  const cacheKey = ref([]); // 缓存字码 - 还未转换成词组的字码集合
  const cacheKeyCode = ref([]); // 缓存电码 - 还未转换成词组的电码码集合
  const logsPatStandardCode = ref([]); // 待转换拍发基准值的数据集合
  const cachePatKey = ref([]); // 缓存字码 - 还未翻页提交的字码集合
  const showPatCodeLog = ref([]); // 展示的拍发记录
  const currPatKeyIndex = ref(-1); // 正在拍发的电报纸字码的下标
  const {morseCode,codeKey,dots} = useMorse();
  const {countPatStandardInfo,countAverageStandard} = countPatStandard();
  const {ws_connect,sendMessage,closeWebSocket} = PublicSocket();
  const router = useRouter();
  const scorePath = ref('');
  const errorText = ref('');
  const patNumber = ref(0);
  let isFirstKey = true
  const initSymbol = ref({ // 拍发特殊符号的电码值
    machine: '0001,0001,0001', // 试机操作符号
    start: '10001', // 开始符号
    end: '01010', // 结束符号
    turn: '00,00,00', // 翻页符号
    alter: '001100', // 改错符号-当前组
    next: '001011', // 改错符号-前一组
  });
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
  const userInfo = JSON.parse(localStorage.getItem('userInfo'))
  const readyPat = ref(false);
  const patUser = ref({});
  const patWsData = ref({topic: 'pat', id: userInfo.id, log: {code: 0, diff: 0, gap: 0 }})
  const startStatus = ref(false);
  router.getRoutes().forEach(r => {
    if (r.name === 'HandKeyZuXunTrain') {
      scorePath.value = r.path;
    }
  });

  onMounted(() => {
    patKeyBoxRef.value.addEventListener('mousewheel', e => {
      if (e.deltaY > 0) {
        patKeyBoxRef.value.scrollLeft += 100;
      } else {
        patKeyBoxRef.value.scrollLeft -= 100;
      }
    });

    patValBoxRef.value.addEventListener('mousewheel', e => {
      if (e.deltaY > 0) {
        patValBoxRef.value.scrollLeft += 100;
      } else {
        patValBoxRef.value.scrollLeft -= 100;
      }
    });
  });

  onUnmounted(() => {
    clearInterval(trainTimer.value);
    closeWebSocket();
  });

  /**
   * 获取规则评分偏移量
   * @param id
   */
  const getScoreOffsetInfo = (id) => {
    getGradingRuleById({id: id}).then(res => {
      if (res.code === 200) {
        let rule = JSON.parse(res.data.content);
        initFloat.value = rule.skew;
      }
    })
  };

  /**
   * 获取电报纸的报文内容
   * @param page
   */
  const getPostTrainKeyInfo = (page) => {
    getHandKeyZuXunPageNumber({
      id: trainData.value.trainId,
      userId: userInfo.id,
      floorNumber: page
    }).then(res => {
      if (res.code === 200) {
        trainData.value.telegraph[page-1] = res.data.messageKey;
      }
    })
  };

  /**
   * 训练时间转换显示
   */
  const initTrainTimeInfo = () => {
    if (!trainData.value.validTime) {
      trainData.value.validTime = 0;
    }
    trainTimer.value = setInterval(() => {
      trainData.value.validTime += 1;
      let type = (trainData.value.type==1?'letter':trainData.value.type==2?'mix':(trainData.value.codeSort?'long':'short'));
      trainData.value.speed = ((400 * 60 * 1000 * patNumber.value) / (trainData.value.validTime * 1000 * dots[type])).toFixed(2);
      timeAreaShow(trainData.value.validTime * 1000);
    },1000);
  };

  /**
   * 时间区域显示
   */
  const timeAreaShow = (t) => {
    let h,m,s;
    h = Math.floor(t / 1000 / 60 / 60 % 24);
    m = Math.floor(t / 1000 / 60 % 60);
    s = Math.floor(t / 1000 % 60);
    h = h > 9 ? h + "" : '0' + h;
    m = m > 9 ? m + "" : '0' + m;
    s = s > 9 ? s + "" : '0' + s;
    trainTimeRef.value.h = h;
    trainTimeRef.value.m = m;
    trainTimeRef.value.s = s;
  };

  /**
   * 切换电报纸
   * @param type
   */
  const switchTelegram = (type) => {
    if ((type === 'prev' && trainData.value.floorNow <= 1) ||
        (type === 'next' && trainData.value.floorNow >= trainData.value.pag)) return false;
    if (type === 'prev') {
      trainData.value.floorNow --;
    }
    if (type === 'next') {
      trainData.value.floorNow ++;
    }
    if (!trainData.value.telegraph[trainData.value.floorNow + 1] && trainData.value.floorNow < trainData.value.pag) {
      getPostTrainKeyInfo(trainData.value.floorNow + 1);
    }
  };

  /**
   * 重拍开始符
   */
  const resetPatStart = () => {
    trainData.value.process = 1;
    cachePatCode.value = [];
    logsPatStandardCode.value = [];
    finishPatLogs.value = [];
  }

  /**
   * 处理接收的电报code
   * @param val
   * @param diffTime
   * @param gapTime
   */
  const handleReceiveKeyCode = (val,diffTime,gapTime) => {
    if (loading.value || patUser.value.userStatus == 3) return false;
    if (val === -1) {
      let gap = gapTime[1]-gapTime[0];
      if (gap < patStandard.value.codeGap*(1 + initFloat.value/100)) {
        patNumber.value ++;
      } else if (gap < patStandard.value.wordGap*(1 + initFloat.value/100)) {
        patNumber.value += 3;
      } else if (gap < patStandard.value.groupGap*(1 + initFloat.value/100)) {
        patNumber.value += 5;
      }
      if (trainData.value.process === 2) {
        finishPatLogs.value[finishPatLogs.value.length-1].patLogs.push({
          name: '间隔'+finishPatLogs.value[finishPatLogs.value.length-1].patLogs.length,
          key: 2,
          value: gap
        });
        cachePatLogs.value.push({
          name: '间隔',
          key: 2,
          value: gap
        })
      }
      if (trainData.value.patCodeLog.length > 0) {
        trainData.value.patCodeLog[trainData.value.patCodeLog.length-1].gap = gap;
        patWsData.value.log.gap = gap
      }
      if (cachePatCode.value.length > 0) {
        cachePatCode.value[cachePatCode.value.length-1].gap = gap;
      }
      if (logsPatStandardCode.value.length > 0) {
        logsPatStandardCode.value[logsPatStandardCode.value.length-1].gap = gap;
      }
    } else {
      let diff = diffTime[1]-diffTime[0];
      patNumber.value += (val == 0 ? 1 : 3);
      if (trainData.value.process === 2) {
        finishPatLogs.value[finishPatLogs.value.length-1].patLogs.push({
          name: (val===0?'点':'划')+finishPatLogs.value[finishPatLogs.value.length-1].patLogs.length,
          key: val,
          value: diff
        });
        cachePatLogs.value.push({
          name: (val===0?'点':'划'),
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
      });
      //取出需要展示的拍发记录
      if(trainData.value.patCodeLog.length>70){
        trainData.value.patCodeLog.shift()
      }
      showPatCodeLog.value = trainData.value.patCodeLog
      patWsData.value.log.code = val
      patWsData.value.log.diff = diff

      sendMessage(patWsData.value)
      // 记录还没编译成字码的电码数据
      cachePatCode.value.push({
        code: val,
        diff: diff,
        gap: 0
      });
      if ([1,-1,2].indexOf(trainData.value.process)>-1) {
        // 记录需要调整拍发基准值的电码数据
        logsPatStandardCode.value.push({
          code: val,
          diff: diff,
          gap: 0
        });
      }
    }
    if (trainData.value.process === -1) {
      trainData.value.process = 1;
    }

    // 验证开始符号的拍发
    if ((trainData.value.process === 1 || trainData.value.process === -1) && cachePatCode.value.length >= 5) {
      verifyStartSymbol();
    }
    if(wordTimer.value) {clearTimeout(wordTimer.value);wordTimer.value=null;}
    if(groupTimer.value) {clearTimeout(groupTimer.value);groupTimer.value=null;}
    if (trainData.value.process === 2 && val !== -1) {
      if(isFirstKey){
        isFirstKey = false
        startTrainUser(trainData.value.trainId)
      }
      if (cachePatCode.value.length > 0) {
        // 编译成单个字码
        if (!wordTimer.value) {
          wordTimer.value = setTimeout(() => {
            codeCompileKeyInfo('word');
            clearTimeout(wordTimer.value);
            wordTimer.value = null;
          },(patStandard.value.codeGap*(1 + initFloat.value/100)));
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
    nextTick(()=>{
      patValBoxRef.value.scrollLeft = patValBoxRef.value.scrollWidth;
    })
  };

  /**
   * 验证开始操作电码
   */
  const verifyStartSymbol = () => {
    let lineCode = '',lineCode_index = [];
    if (initSymbol.value.start.indexOf(',') > -1) {
      lineCode = initSymbol.value.start.replace(/,/g,'');
    } else {
      lineCode = initSymbol.value.start;
    }
    lineCode.split('').map((item,i) => {
      if(item === '1') {
        lineCode_index.push(i);
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
        patStandard.value.codeGap = dg_median;
        patStandard.value.wordGap = parseInt(dg_median * 3);
        patStandard.value.groupGap = parseInt(dg_median * 5);

        trainData.value.process = 2;
        currPatKeyIndex.value = 0;
        trainData.value.patKeyVal = [];
        trainData.value.patKeyVal.push(['开始']);
        trainData.value.patKeyVal.push([]);
        trainData.value.validTime = 0;
        startStatus.value = true;

        oldPatStandard.value = {
          dot: patStandard.value.dot,
          line: patStandard.value.line,
          c_gap: patStandard.value.codeGap,
          w_gap: patStandard.value.wordGap,
          g_gap: patStandard.value.groupGap,
        };
        finishPatLogs.value.push({
          dot: patStandard.value.dot,
          line: patStandard.value.line,
          codeGap: patStandard.value.codeGap,
          wordGap: patStandard.value.wordGap,
          groupGap: patStandard.value.groupGap,
          offSize: initFloat.value,
          patLogs: [],
        })
        cachePatCode.value = [];

        // if (trainTimer.value) {
        //   clearInterval(trainTimer.value);
        // }
        // initTrainTimeInfo()
      } else {
        trainData.value.process = -1;
        // cachePatCode.value = [];
        errorText.value = '拍发点、划无效，请重新拍发';
      }
    } else {
      trainData.value.process = -1;
      // cachePatCode.value = [];
      if (dt[0] < d_median*(1 - initFloat.value/100)) {
        errorText.value = '开始符【点】拍发时长小于平均值的偏移量，请重新拍发';
      } else if (dt[dt.length-1] > d_median*(1 + initFloat.value/100)) {
        errorText.value = '开始符【点】拍发时长大于平均值的偏移量，请重新拍发';
      } else if (lt[0] < l_median*(1 - initFloat.value/100)) {
        errorText.value = '开始符【划】拍发时长大于平均值的偏移量，请重新拍发';
      } else if (lt[lt.length-1] > l_median*(1 + initFloat.value/100)) {
        errorText.value = '开始符【划】拍发时长大于平均值的偏移量，请重新拍发';
      } else if (dg[0] < dg_median*(1 - initFloat.value/100)) {
        errorText.value = '开始符【电码间隔】时长小于平均值的偏移量，请重新拍发';
      } else if (dg[dg.length-1] > dg_median*(1 + initFloat.value/100)) {
        errorText.value = '开始符【电码间隔】时长大于平均值的偏移量，请重新拍发';
      } else{
        errorText.value = '开始符拍发无效，请重新拍发';
      }
    }
  };

  /**
   * 处理电报正文拍发的基准值
   */
  const resetPatStandard = () => {
    patStandard.value = countAverageStandard(oldPatStandard,pagePatStandard,patStandard);

    finishPatLogs.value.push({
      dot: patStandard.value.dot,
      line: patStandard.value.line,
      codeGap: patStandard.value.codeGap,
      wordGap: patStandard.value.wordGap,
      groupGap: patStandard.value.groupGap,
      offSize: initFloat.value,
      patLogs: [],
    });
    oldPatStandard.value = {
      dot: patStandard.value.dot,
      line: patStandard.value.line,
      c_gap: patStandard.value.codeGap,
      w_gap: patStandard.value.wordGap,
      g_gap: patStandard.value.groupGap,
    };
  };

  /**
   * 电报纸正文内拍发基准值校准
   */
  const pageResetPatStandard = (turn) => {
    let arr = logsPatStandardCode.value.filter((item, i) => i>=pageHandleIndex.value);
    pageHandleIndex.value = logsPatStandardCode.value.length;

    patStandard.value = countPatStandardInfo(arr,patStandard,initFloat);

    pagePatStandard.value.push({
      dot: patStandard.value.dot,
      line: patStandard.value.line,
      codeGap: patStandard.value.codeGap,
      wordGap: patStandard.value.wordGap,
      groupGap: patStandard.value.groupGap,
      offSize: initFloat.value,
    });

    if (turn === 'turn') {
      resetPatStandard();
    }
  };

  /**
   * 电码信号转码成字符
   */
  let numI = 0
  const codeCompileKeyInfo = (type) => {
    let code = cachePatCode.value.map(item => item.code).join(''),
        short = (trainData.value.type===1?'letter':trainData.value.type===2?'mix':trainData.value.codeSort?'long':'short'),
        codeInit = codeKey[short],
        len = trainData.value.patKeyVal.length,
        lastPatKey;

    if (currPatKeyIndex.value == -1) {
      startStatus.value = false;
      code = code.slice(-5,code.length)
    }
    if (code === initSymbol.value.start && trainData.value.floorNow == 1 && currPatKeyIndex.value <= 2)
    {
      trainData.value.patKeyVal = [];
      trainData.value.patKeyVal.push(['开始']);
      trainData.value.patKeyVal.push([]);
      cachePatCode.value = [];
      cachePatKey.value = [];
      cacheKey.value = [];
      currPatKeyIndex.value = 0;
      trainData.value.validTime = 0;
      startStatus.value = true;
      trainData.value.telegraph[trainData.value.floorNow - 1][0].moresValue = [];
      trainData.value.telegraph[trainData.value.floorNow - 1][0].moresTime = [];
      trainData.value.telegraph[trainData.value.floorNow - 1][0].patKeys = [];
      trainData.value.telegraph[trainData.value.floorNow - 1][0].patLogs = [];
      trainData.value.telegraph[trainData.value.floorNow - 1][1].moresValue = [];
      trainData.value.telegraph[trainData.value.floorNow - 1][1].moresTime = [];
      trainData.value.telegraph[trainData.value.floorNow - 1][1].patKeys = [];
      trainData.value.telegraph[trainData.value.floorNow - 1][1].patLogs = [];
    } else if (code === initSymbol.value.start && trainData.value.floorNow > 1 && currPatKeyIndex.value == -1)
    {
      trainData.value.patKeyVal.push(['开始']);
      trainData.value.patKeyVal.push([]);
      cachePatCode.value = [];
      cachePatKey.value = [];
      cacheKey.value = [];
      currPatKeyIndex.value = 0;
      startStatus.value = true;
      trainData.value.telegraph[trainData.value.floorNow - 1][0].moresValue = [];
      trainData.value.telegraph[trainData.value.floorNow - 1][0].moresTime = [];
      trainData.value.telegraph[trainData.value.floorNow - 1][0].patKeys = [];
      trainData.value.telegraph[trainData.value.floorNow - 1][0].patLogs = [];
    } else if (type === 'word' && startStatus.value)
    {
      cacheKeyCode.value.push(code);

      let cacheArr1 = cacheKeyCode.value.filter((ii,i) => i>=cacheKeyCode.value.length-1)
      let cacheArr2 = cacheKeyCode.value.filter((ii,i) => i>=cacheKeyCode.value.length-2)
      let cacheArr3 = cacheKeyCode.value.filter((ii,i) => i>=cacheKeyCode.value.length-3)
      if (cacheArr1.join(',') === initSymbol.value.end) {
        statisticsTelegraphData('autoEnd');
        return false;
      }
      if (cacheArr3.join(',') === initSymbol.value.turn||cacheArr3.join(',') ==='00,0000'||cacheArr3.join(',') ==='0000,00') {
        if (trainData.value.floorNow == trainData.value.pag) {
          statisticsTelegraphData('autoEnd');
          return false;
        } else {
          statisticsTelegraphData('turn');
          return false;
        }
      }

      if (code === initSymbol.value.alter)
      {
        if (cacheKey.value.legnth == 0) {
          currPatKeyIndex.value --;
        }
        alter.value = 3;
        cacheKey.value.push('?');
        updateMoresKeyInfo('?')
      } else if (cacheArr2.join(',') === initSymbol.value.next) {
        alter.value = 1;
        // cacheKey.value.pop();
        console.log(cacheKey.value);
        cacheKey.value.push('/');
        if (cacheKey.value.legnth == 0||cacheKey.value[0]==='/') {
          currPatKeyIndex.value -= 2;
        } else {
          currPatKeyIndex.value --;
        }

        if(cacheKey.value.length===1){
          // trainData.value.patKeyVal[trainData.value.patKeyVal.length-1].pop()
        }
        updateMoresKeyInfo('?')
      } else if (code == '000000') {
        statisticsTelegraphData('turn');
        return false;
        /*trainData.value.patKeyVal.push(['句号']);
        trainData.value.patKeyVal.push([]);
        currPatKeyIndex.value = -1;
        cachePatKey.value = [];
        cacheKey.value = [];
        cacheKeyCode.value = [];
        cachePatCode.value = [];
        cachePatLogs.value = [];*/
      } else {
        cacheKey.value.push(codeInit[code]!=undefined&&code!=initSymbol.value.start?codeInit[code]:'#');
        updateMoresKeyInfo(codeInit[code]!=undefined&&code!=initSymbol.value.start?codeInit[code]:'#')
      }

      lastPatKey = trainData.value.patKeyVal[len-1];
      if (cacheKey.value.length === 1 || lastPatKey.length == 0) {
        lastPatKey.push(cacheKey.value.join(''));
        cachePatKey.value.push(cacheKey.value.join(''));
      } else {
        lastPatKey[lastPatKey.length-1] = cacheKey.value.join('');
        cachePatKey.value[cachePatKey.value.length - 1] = cacheKey.value.join('');
      }
    } else if (type === 'group' && startStatus.value)
    {
      if (cacheKey.value.length >= 3 || currPatKeyIndex.value % 2 == 0) {
        pageResetPatStandard()
      }
      if (alter.value === 2) {
        updateMoresKeyInfo('?')
        alter.value = 0;
        // 改错前一组恢复下标变量赋值
        if(numI===0){
          numI = 1
        }
      }
      lastPatKey = trainData.value.patKeyVal[len-1];
      if (alter.value===1) {
        alter.value = 2;
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
          //   currPatKeyIndex.value+=2;
          //   numI = 0
          // }else if(numI === 2){
          //   currPatKeyIndex.value+=2;
          //   numI = 0
          // }else {
          //   currPatKeyIndex.value ++;
          // }
          currPatKeyIndex.value ++;
          console.log(trainData.value);
          trainData.value.telegraph[trainData.value.floorNow-1][currPatKeyIndex.value].patKeys=[]
        }
        cacheKey.value = [];
        cacheKeyCode.value = [];
      }
      if (alter.value ===3) {
        alter.value = 0;
      }
    }
    if (patKeyBoxRef.value) {
      patKeyBoxRef.value.scrollTop = patKeyBoxRef.value.scrollHeight;
    }
  };

  /**
   * 更新待提交的字码数据
   */
  const updateMoresKeyInfo = (key) => {
    let item = trainData.value.telegraph[trainData.value.floorNow - 1][currPatKeyIndex.value];
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
      trainData.value.telegraph[trainData.value.floorNow - 1].push({
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
   * 结束训练
   * @param type
   */
  const finishTrainInfo = (type) => {
    let turnLen = initSymbol.value.turn.split(',').length,
        endLen = initSymbol.value.end.split(',').length;

    if (type != 'end') {
      patUser.value.isFinish = 1
      sendMessage({ topic: 'finish', id: userInfo.id })
    }

    finishPatLogs.value = finishPatLogs.value.filter(item => item.patLogs.length > 0);
    finishPatLogs.value.map((item,i) => {
      if (i < finishPatLogs.value.length - 1) {
        item.patLogs = item.patLogs.filter((code,c) => c < (item.patLogs.length - (turnLen*2 - 1)))
      }
      if (type === 'autoEnd' && i === finishPatLogs.value.length - 1) {
        item.patLogs = item.patLogs.filter((code,c) => c < (item.patLogs.length - (endLen*2 - 1)))
      }
    });
    loading.value = true;
    finishHandKeyZuXunTrain({
      id: trainData.value.trainId,
      userId: userInfo.id,
      validTime: trainData.value.validTime,
      finishInfo: finishPatLogs.value
    }).then(res => {
      loading.value = false;
      if (res.code === 200) {} else {
        message.error(res.message);
      }
      router.push({path: scorePath.value, query: {id: trainData.value.trainId,status: 2}})
      emits('changeStatus')
    });
  };

  /**
   * 统计每页电报纸报文内容的正确性
   * @param type
   */
  const statisticsTelegraphData = (type) => {
    let short = (trainData.value.type===0&&!trainData.value.codeSort?'short':'mix'),
        codeArr = '',codeArr1 = '',codeArr3 = '',error = 0, total = trainData.value.messageNumber;

    trainData.value.telegraph[trainData.value.floorNow - 1].map(item => {
      if (typeof item.moresKey === 'string' && item.moresKey !== '#') {
        item.moresKey = JSON.parse(item.moresKey);
      }
      if (typeof item.patKeys === 'string') {
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
      if (item.moresKey === '#' || item.moresValue.length !== item.moresKey.length ||
          item.moresKey.some((key,k) => item.moresValue[k].join('') !== morseCode[short][key].value)) {
        error ++;
      }
      if (typeof item.moresKey !== 'string') {
        item.moresKey = JSON.stringify(item.moresKey);
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
      trainData.value.accuracy = parseFloat((total-trainData.value.errorNumber)/total).toFixed(2);
    } else {
      trainData.value.accuracy = '0.00';
    }
    pageResetPatStandard('turn');
    savePatTelegraphBody(type);
  };

  /**
   * 保存一页电报纸拍发内容
   * @param type
   */
  const savePatTelegraphBody = (type) => {
    trainData.value.telegraph[trainData.value.floorNow - 1].map(item => {
      item.moresTime = JSON.stringify(item.moresTime);
      item.moresValue = JSON.stringify(item.moresValue);
      item.patKeys = JSON.stringify(item.patKeys);
      item.patLogs = JSON.stringify(item.patLogs);
    });
    let finish = finishPatLogs.value[trainData.value.floorNow - 1];
    if (trainData.value.floorNow > 1) {
      let turnLen = initSymbol.value.turn.split(',').length;
      finish.patLogs = finish.patLogs.filter((code,c) => c < (finish.patLogs.length - (turnLen*2 - 1)))
    }
    saveHandKeyZuXunData({
      trainId: trainData.value.trainId,
      userId: userInfo.id,
      floorNumber: trainData.value.floorNow,
      messageBody: trainData.value.telegraph[trainData.value.floorNow - 1],
      validTime: trainData.value.validTime,
      errorNumber: trainData.value.errorNumber,
      speed: trainData.value.speed,
      accuracy: trainData.value.accuracy,
      standard: pagePatStandard.value,
      finishInfo: JSON.stringify(finish)
    }).then(res => {
      if (res.code === 200) {
        // finishPatLogs.value[trainData.value.floorNow - 1].patLogs = []
        pagePatStandard.value = [{
          dot: patStandard.value.dot,
          line: patStandard.value.line,
          codeGap: patStandard.value.codeGap,
          wordGap: patStandard.value.wordGap,
          groupGap: patStandard.value.groupGap,
          offSize: initFloat.value,
        }];
        if (type == 'turn') {
          if (trainData.value.patKeyVal.length > 0) {
            trainData.value.patKeyVal.push(['句号']);
            trainData.value.patKeyVal.push([]);
            currPatKeyIndex.value = -1;
            cachePatKey.value = [];
            if (trainData.value.floorNow < trainData.value.pag) {
              switchTelegram('next');
            }
          }
        } else {
          trainData.value.patKeyVal.push(['完结']);
          clearInterval(trainTimer.value);
          finishTrainInfo(type);
        }
      } else {
        message.error(res.message);
      }
    });
  };

  /**
   * 重置训练
   */
  const resetTrainInfo = () => {
    resetHandKeyZuXunTrain({
      id: trainData.value.trainId
    }).then(res => {});
  };

  const connectWebsocket = () => {
    const url =`/generalTickerPat/${userInfo.id}/${trainData.value.trainId}/0`
    ws_connect(url, receiveWebSocketMessage)
  }

  const cutTime = ref(5)
  const cutTimer = ref(null)
  const receiveWebSocketMessage = (e) => {
    let data = JSON.parse(JSON.parse(e.data).data);
    // console.log(data)
    if(data.topic == 'online'){
      message.success('教员已回来！')
    }
    else if (data.topic == 'offline') {
      message.error('教员已离开！')
    }
    else if (data.topic == 'begin') {
      message.success("训练已开始，请开始训练！")
      cutTimer.value = setInterval(()=>{
        cutTime.value--
        if(cutTime.value==0){
          clearInterval(cutTimer.value)
          cutTimer.value = "begin"
          if (trainTimer.value) {
            clearInterval(trainTimer.value);
          }
          initTrainTimeInfo()

          trainData.value.floorNow = 1;
          trainData.value.status = 1;
          trainData.value.process = 1;
          trainData.value.validTime = 0;
          cachePatCode.value = [];
          logsPatStandardCode.value = [];
          finishPatLogs.value = [];
        }
      },1000)


    }
    else if (data.topic == 'end') {
      trainData.value.status = 2;
      clearInterval(trainTimer.value)
      if (patUser.value.isFinish != 1) {
        statisticsTelegraphData('end')
      } else {
        router.push({path: scorePath.value, query: {id: trainData.value.trainId,status: 2}})
        emits('changeStatus')
      }
    }
  }

  const readyTrainPat = (type) => {
    if(!wsOnline.value) {message.error('报训软件未连接!'); return false;}
    if(!devOnline.value) {message.error('电子键设备未连接！'); return false;}
    readyPat.value = true;
    if (type == 1) {
      const saved = JSON.parse(window.localStorage.getItem('handKeyZuXun'+trainData.value.trainId) || 'null')
      if (saved) {
        trainData.value.floorNow = saved.patPage
        trainData.value.validTime = saved.time
        trainData.value.speed = saved.speed
        currPatKeyIndex.value = saved.patKeyIndex
      }
    } else {
      cachePatCode.value = [];
      logsPatStandardCode.value = [];
      finishPatLogs.value = [];
      trainData.value.floorNow = 1;
      currPatKeyIndex.value = -1;
      trainData.value.validTime = 0;
      trainData.value.errorNumber = 0;
      trainData.value.accuracy = '0';
      trainData.value.speed = 0;
      if (trainData.value.status == 1) {
        trainData.value.process = 1;
        cutTimer.value = "begin"
        resetTrainInfo();
      }
    }
    sendMessage({topic: 'ready', id: userInfo.id})
  }

  return {
    patKeyBoxRef, patValBoxRef, trainTimeRef, initSymbol, errorText, currPatKeyIndex,readyPat,patUser,getPostTrainKeyInfo,
    switchTelegram, resetPatStart,handleReceiveKeyCode, timeAreaShow, getScoreOffsetInfo,readyTrainPat,connectWebsocket,
    initTrainTimeInfo,cutTime,cutTimer,showPatCodeLog
  }
}
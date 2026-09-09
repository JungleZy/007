import Voice from "../utils/MorseVoice";
import {ref,onUnmounted} from "vue";
import {PubSub} from "./PubSub";
import operationMorseVoice from "../../common/utils/voice/operationMorseVoice";
import useMorse from '../../common/mixin/useMorse.js'

export default function ElectronMorse() {
  const voiceFreq = ref(1000)
  const { dots } = useMorse()
  const voiceType = ref('stop')//高性能音频状态
  // let voice = new Voice({ fre: voiceFreq.value })
  let voice = {clear:()=>{},changeCriterion:()=>{}}
  let code = [
    {key:'1', text:'1', _code: [0,1,2],letterCode:[0,2], code: '21'},
    {key:'2', text:'2', _code: [0,0,1,2],letterCode:[0,0,2], code: '22'},
    {key:'3', text:'3', _code: [0,0,0,1,1,2], letterCode:[1,2],code: '23'},
    {key:'4', text:'4', _code: [0,0,0,0,1,2],letterCode:[1,0,2], code: '24'},
    {key:'5', text:'5', _code: [0,0,0,0,0,2],letterCode:[0,1,2], code: '25'},
    {key:'6', text:'6', _code: [1,0,0,0,0,2], letterCode:[0],code: '31'},
    {key:'7', text:'7', _code: [1,1,0,0,0,2], letterCode:[0,0],code: '32'},
    {key:'8', text:'8', _code: [1,0,0,2], letterCode:[1],code: '33'},
    {key:'9', text:'9', _code: [1,0,2], letterCode:[1,0],code: '34'},
    {key:'0', text:'0', _code: [1,2], letterCode:[0,1],code: '35'},
    {key:'enter', text:'', _code: [3], letterCode:[3],code: '41'},
    {key:'?', text:'?', _code:[0, 0, 1, 1, 0, 0,2],letterCode:[0,0,0], code: '42'},
    {key:'FM', text:'FM', _code: [1, 0, 0, 1, 0,2], letterCode:[1,1],code: '43'},
    {key:'start', text:'开始', _code: [1,0,0,0,1,4], letterCode:[1,1],code: '44'},
    {key:'period', text:'句号', _code: [0,0,2,0,0,2,0,0,4], letterCode:[0,1,1],code: '45'}
  ]
  let isNumber = 0// 0为数 1为码字码
  let codeAll = []
  let isFlag = true
  let speedRate = 60
  let pattern = 'F1'
  let isF3 = false
  const {operation} = operationMorseVoice()
  const addCode = (state)=>{
    if(state.message.d[0]===12){
      pattern = 'F1'
    }else if(state.message.d[0]===13){
      pattern = 'F2'
    }else if(state.message.d[0]===14){
      isF3 = true
      pattern = 'F2'
    }
    let isOnen = true //判断是否有对应的按键字码
    code.forEach(item=>{
      if(item.code==state.message.d[0]){
        isOnen = false
        if(pattern==='F1'||(pattern==="F2"&&isF3==true)){
          isF3 = false
          codeAll.push(item._code)
        }else {
          codeAll.push(item.letterCode)
        }

      }
    })
    if(isOnen){
      return
    }
    if(isFlag){
      if (codeAll[0] && codeAll[0].length > 0) {
        playVoice(codeAll[0])
      }
      isFlag = false
    }
  }
  const playVoice = (arr) => {
    return
    if(voice){
      voice.changeCriterion(parseInt(((1 / speedRate) * 60 * 1000) / 12))
    }
    // voice.changeCriterion(parseInt(1200 / speedRate))
    voice.clear(() => {
      let key = 0
      voice.play(arr, res => {
        key++
        if(key==arr.length){
          codeAll.shift()
          if (codeAll.length!=0){
            playVoice(codeAll[0])
          }else {
            isFlag = true
          }
        }
      })
    })
  }
  const changeCriterion = (speed)=>{
    let cri = ((400 / (speed)) * 60 * 1000) / dots['short']
    operation({type:'changeCriterion',data:parseInt(cri)})
  }
  const clear = ()=>{
    if(voice){
      voice.clear()
    }
  }
  const changePattern = (data)=>{
    isNumber = data
  }
  setTimeout( () => {
    PubSub.subscribe('receiveProcessData',res=>{
      // console.log(res);
      if(res.type==='playing'){
      }
      if (res.status==='finish') {
        voiceType.value = 'stop'
      }
    })
  },3000)
  setTimeout(()=>{
    operation({type:'changeFrequency',data:1000})
    operation({type:'changeRatio',data: {
        dot: 1, // 比例 点长度
        dash: 3, // 比例 划长度
        gap: 1, // 比例 点划间隔
        word: 3, // 比例 词间隔
        suite: 5, // 比例 组间隔
        leaf: 7 // 比例 电报纸间隔
      }})
  },1000)
  onUnmounted(() => {
    PubSub.unsubscribe('receiveProcessData')
  })


  const voiceCode = (revicedata)=>{
    // console.log("revicedata");
    // console.log(revicedata);
    // console.log("revicedata");
    if(voiceType.value==='stop'){
      voiceType.value = 'playing'
      operation({type:'message',data:{
          numType:revicedata.numType,
          data:revicedata.code
        }})
    }else {
      operation({type:'addCode',data:{
          data:revicedata.code,
          numType:revicedata.numType,
        }})
    }
  }
  return{
    addCode,
    changeCriterion,
    clear,
    voiceCode,
    changePattern
  }
}
import MorseVoiceOld from "./voice/MorseVoiceOld";
import MorseVoiceNew from "./voice/MorseVoiceHighPerformance";
import {PubSub} from './PubSub'
const forwardTable = {
  letter: {
    A: [0, 1],
    B: [1, 0, 0, 0],
    C: [1, 0, 1, 0],
    D: [1, 0, 0],
    E: [0],
    F: [0, 0, 1, 0],
    G: [1, 1, 0],
    H: [0, 0, 0, 0],
    I: [0, 0],
    J: [0, 1, 1, 1],
    K: [1, 0, 1],
    L: [0, 1, 0, 0],
    M: [1, 1],
    N: [1, 0],
    O: [1, 1, 1],
    P: [0, 1, 1, 0],
    Q: [1, 1, 0, 1],
    R: [0, 1, 0],
    S: [0, 0, 0],
    T: [1],
    U: [0, 0, 1],
    V: [0, 0, 0, 1],
    W: [0, 1, 1],
    X: [1, 0, 0, 1],
    Y: [1, 0, 1, 1],
    Z: [1, 1, 0, 0]
  },
  long: {
    0: [1, 1, 1, 1, 1],
    1: [0, 1, 1, 1, 1],
    2: [0, 0, 1, 1, 1],
    3: [0, 0, 0, 1, 1],
    4: [0, 0, 0, 0, 1],
    5: [0, 0, 0, 0, 0],
    6: [1, 0, 0, 0, 0],
    7: [1, 1, 0, 0, 0],
    8: [1, 1, 1, 0, 0],
    9: [1, 1, 1, 1, 0]
  },
  short: {
    0: [1],
    1: [0, 1],
    2: [0, 0, 1],
    3: [0, 0, 0, 1, 1],
    4: [0, 0, 0, 0, 1],
    5: [0, 0, 0, 0, 0],
    6: [1, 0, 0, 0, 0],
    7: [1, 1, 0, 0, 0],
    8: [1, 0, 0],
    9: [1, 0]
  }
}
const keyCodes = {
  A: [0, 1],
  B: [1, 0, 0, 0],
  C: [1, 0, 1, 0],
  D: [1, 0, 0],
  E: [0],
  F: [0, 0, 1, 0],
  G: [1, 1, 0],
  H: [0, 0, 0, 0],
  I: [0, 0],
  J: [0, 1, 1, 1],
  K: [1, 0, 1],
  L: [0, 1, 0, 0],
  M: [1, 1],
  N: [1, 0],
  O: [1, 1, 1],
  P: [0, 1, 1, 0],
  Q: [1, 1, 0, 1],
  R: [0, 1, 0],
  S: [0, 0, 0],
  T: [1],
  U: [0, 0, 1],
  V: [0, 0, 0, 1],
  W: [0, 1, 1],
  X: [1, 0, 0, 1],
  Y: [1, 0, 1, 1],
  Z: [1, 1, 0, 0],
  0: [1, 1, 1, 1, 1],
  1: [0, 1, 1, 1, 1],
  2: [0, 0, 1, 1, 1],
  3: [0, 0, 0, 1, 1],
  4: [0, 0, 0, 0, 1],
  5: [0, 0, 0, 0, 0],
  6: [1, 0, 0, 0, 0],
  7: [1, 1, 0, 0, 0],
  8: [1, 1, 1, 0, 0],
  9: [1, 1, 1, 1, 0],
  '?': [0, 0, 1, 1, 0, 0],
  '/': [1, 0, 0, 1, 0],
  '<': [1, 0, 1, 1, 0, 1],
  '>': [1, 0, 1, 1, 0, 1],
  '。': [0, 0, 2, 0, 0, 2, 0, 0],
  '.': [0, 1, 0, 1, 0, 1]
}
const NUM_TYPE = {
  LONG: 'long',
  SHORT: 'short'
}
// let osc;
export default function MorseVoice(type='old',data) {
  const protocol = document.location.protocol
  const hostname = document.location.hostname
  let morseVoice
  if(type==='new'){
    morseVoice = new MorseVoiceNew()
    morseVoice.init(e=>{
      console.log(e)
    })
    morseVoice.on('message',e=>{
      // console.log(e);
      PubSub.publish('receiveProcessData',e)
    })
    PubSub.subscribe('receiveSendData',data=>{
      switch (data.type) {
        case 'message':
          morseVoice.play(data.data)
          break;
        case 'addCode':
          morseVoice.addCode(data.data)
          break;
        case 'pause':
          morseVoice.pause()
          break;
        case 'stop':
          morseVoice.stop()
          break;
        case 'continue':
          morseVoice.continue()
          break;
        case 'changeFrequency':
          morseVoice.changeFrequency(data.data)
          break;
        case 'changeVolume':
          morseVoice.changeVolume(data.data)
          break;
        case 'changeCriterion':
          morseVoice.changeCriterion(data.data)
          break;
        case 'changeRatio':
          morseVoice.changeRatio(data.data)
          break;
        case 'model':
          morseVoice.changeModel(data.data)
          break;
        case 'pressed':
          morseVoice.changePressed(data.data)
          break
      }
    })
  }else {
    morseVoice = new MorseVoiceOld()
  }
  return morseVoice
}
export { forwardTable, NUM_TYPE }

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
const sharedVoice = new MorseVoiceNew()
sharedVoice.on('message', data => PubSub.publishSync('receiveProcessData', data))

export function audioOperation(command) {
  const {type, data} = command
  switch (type) {
    case 'configure': sharedVoice.configure(data); return true
    case 'init': return sharedVoice.init()
    case 'ready': return sharedVoice.isReady()
    case 'message': return sharedVoice.play(data)
    case 'addCode': return sharedVoice.addCode(data)
    case 'pause': return sharedVoice.pause()
    case 'stop': return sharedVoice.stop()
    case 'continue': return sharedVoice.continue()
    case 'changeFrequency': return sharedVoice.changeFrequency(data)
    case 'changeVolume': return sharedVoice.changeVolume(data)
    case 'changeCriterion': return sharedVoice.changeCriterion(data)
    case 'changeRatio': return sharedVoice.changeRatio(data)
    case 'model': return sharedVoice.changeModel(data)
    case 'pressed': return sharedVoice.changePressed(data)
  }
}

// Subscribe before the permission component mounts so cold-start settings are never lost.
PubSub.subscribe('receiveSendData', audioOperation)

export default function MorseVoice(type = 'old') {
  return type === 'new' ? sharedVoice : new MorseVoiceOld()
}
export { forwardTable, NUM_TYPE }

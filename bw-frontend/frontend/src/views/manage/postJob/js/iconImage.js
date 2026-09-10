
import labTypeHJ from '../../../../assets/HJ/train/lab-type.png'
import labNumHJ from '../../../../assets/HJ/train/lab-num.png'
import labSpeedHJ from '../../../../assets/HJ/train/lab-speed.png'
import topBgHJ from '../../../../assets/HJ/postTrain/top.png'
import keyBgHJ from '../../../../assets/HJ/postTrain/keys.png'
import prevHJ from '../../../../assets/HJ/postTrain/prev.png'
import nextHJ from '../../../../assets/HJ/postTrain/next.png'
import laberrHJ from '../../../../assets/HJ/train/lab-err.png'
import labCheckHJ from '../../../../assets/HJ/train/lab-accuracy.png'
import textBgHJ from '../../../../assets/HJ/postTrain/machineTextBg.png'
import receiveBgHJ from '../../../../assets/HJ/receive/receiveBg.png'
import thumeHJ from '../../../../assets/HJ/postTrain/thume-manual.png'
import addNextHJ from '../../../../assets/HJ/postTrain/addNext.png'

import labTypeHJJ from '../../../../assets/HJJ/train/new-lab-type.png'
import labNumHJJ from '../../../../assets/HJJ/train/lab-number.png'
import labSpeedHJJ from '../../../../assets/HJJ/train/new-lab-speed.png'
import topBgHJJ from '../../../../assets/HJJ/postTrain/top.png'
import keyBgHJJ from '../../../../assets/HJJ/postTrain/keys.png'
import prevHJJ from '../../../../assets/HJJ/postTrain/prev.png'
import nextHJJ from '../../../../assets/HJJ/postTrain/next.png'
import laberrHJJ from '../../../../assets/HJJ/postTrain/hanzi/error.png'
import labCheckHJJ from '../../../../assets/HJ/postTrain/hanzi/check.png'
import textBgHJJ from '../../../../assets/HJJ/postTrain/machineTextBg.png'
import receiveBgHJJ from '../../../../assets/HJJ/receive/receiveBg.png'
import thumeHJJ from '../../../../assets/HJJ/postTrain/thume-manual.png'
import addNextHJJ from '../../../../assets/HJJ/postTrain/addNext.png'



import labTypeLJ from '../../../../assets/LJ/train/lab-type.png'
import labNumLJ from '../../../../assets/LJ/train/lab-num.png'
import labSpeedLJ from '../../../../assets/LJ/train/lab-speed.png'
import topBgLJ from '../../../../assets/LJ/postTrain/top.png'
import keyBgLJ from '../../../../assets/LJ/postTrain/keys.png'
import prevLJ from '../../../../assets/LJ/postTrain/prev.png'
import nextLJ from '../../../../assets/LJ/postTrain/next.png'
import laberrLJ from '../../../../assets/LJ/postTrain/hanzi/error.png'
import labCheckLJ from '../../../../assets/LJ/postTrain/hanzi/check.png'
import textBgLJ from '../../../../assets/LJ/postTrain/machineTextBg.png'
import receiveBgLJ from '../../../../assets/LJ/receive/receiveBg.png'
import thumeLJ from '../../../../assets/LJ/postTrain/thume-manual.png'
import addNextLJ from '../../../../assets/LJ/postTrain/addNext.png'

import labTypeKJ from '../../../../assets/KJ/train/new-lab-type.png'
import labNumKJ from '../../../../assets/KJ/train/lab-number.png'
import labSpeedKJ from '../../../../assets/KJ/train/new-lab-speed.png'
import topBgKJ from '../../../../assets/KJ/postTrain/top.png'
import keyBgKJ from '../../../../assets/KJ/postTrain/keys.png'
import prevKJ from '../../../../assets/KJ/postTrain/prev.png'
import nextKJ from '../../../../assets/KJ/postTrain/next.png'
import laberrKJ from '../../../../assets/KJ/postTrain/hanzi/error.png'
import labCheckKJ from '../../../../assets/KJ/postTrain/hanzi/check.png'
import textBgKJ from '../../../../assets/KJ/postTrain/machineTextBg.png'
import receiveBgKJ from '../../../../assets/KJ/receive/receiveBg.png'
import thumeKJ from '../../../../assets/KJ/postTrain/thume-manual.png'
import addNextKJ from '../../../../assets/KJ/postTrain/addNext.png'


export default function iconImage(){
  let labType,labNum,labSpeed,topBg,keyBg,prev,next,laberr,labCheck,textBg,receiveBg,thume,addNext
  const interfaceStyle = window.interfaceStyle
  if(interfaceStyle==='HJ'){
    labType = labTypeHJ
    labNum = labNumHJ
    labSpeed = labSpeedHJ
    topBg = topBgHJ
    keyBg = keyBgHJ
    prev = prevHJ
    next = nextHJ
    laberr = laberrHJ
    labCheck = labCheckHJ
    textBg = textBgHJ
    receiveBg = receiveBgHJ
    thume = thumeHJ
    addNext = addNextHJ

  }else if(interfaceStyle==='HJJ'){
    labType = labTypeHJJ
    labNum = labNumHJJ
    labSpeed = labSpeedHJJ
    topBg = topBgHJJ
    keyBg = keyBgHJJ
    prev = prevHJJ
    next = nextHJJ
    laberr = laberrHJJ
    labCheck = labCheckHJJ
    textBg = textBgHJJ
    receiveBg = receiveBgHJJ
    thume = thumeHJJ
    addNext = addNextHJJ
  }else if(interfaceStyle==='KJ'){
    labType = labTypeKJ
    labNum = labNumKJ
    labSpeed = labSpeedKJ
    topBg = topBgKJ
    keyBg = keyBgKJ
    prev = prevKJ
    next = nextKJ
    laberr = laberrKJ
    labCheck = labCheckKJ
    textBg = textBgKJ
    receiveBg = receiveBgKJ
    thume = thumeKJ
    addNext = addNextKJ
  }else {
    labType = labTypeLJ
    labNum = labNumLJ
    labSpeed = labSpeedLJ
    topBg = topBgLJ
    keyBg = keyBgLJ
    prev = prevLJ
    next = nextLJ
    laberr = laberrLJ
    labCheck = labCheckLJ
    textBg = textBgLJ
    receiveBg = receiveBgLJ
    thume = thumeLJ
    addNext = addNextLJ
  }
  return{
    labType,labNum,labSpeed,topBg,keyBg,prev,next,laberr,labCheck,textBg,receiveBg,thume,addNext
  }
}

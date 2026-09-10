

export default function equipment_171(formData){
  const dp = {
    '信道':'',//00-19
    '网络模式':'',//战斗网CNR   分组网PRN 数据链LINK-地空   数据链LINK-地面 兼容体制-121- 无线分组  兼容体制-121- 点点数传  兼容体制-96G
    '工作模式':'定频',//兼容模式下为定明和定密 其余为定频
    '频率':'',//30.000MHz 到 87.975MHz
    '传输密匙':'',//（战斗网CNR   分组网PRN 数据链LINK-地空   数据链LINK-地面）0-19 （兼容体制-121- 无线分组  兼容体制-121- 点点数传） 无 （兼容体制-96G）00-09
    '信息密匙':'',//（战斗网CNR   分组网PRN 数据链LINK-地空   数据链LINK-地面）0-19 （兼容体制-121- 无线分组  兼容体制-121- 点点数传） 无 （兼容体制-96G）00-07
    '接入方式':'无',//（  分组网PRN  兼容体制-121- 无线分组  兼容体制-96G）CSMA或TDMA  其余无
  }
  const wlms = ["战斗网CNR","分组网PRN","数据链LINK-地空","数据链LINK-地面","兼容体制-121-无线分组","兼容体制-121-点点数传","兼容体制-96G"]
  const jrfs = ["CSMA","TDMA"]
  const gzms = ["定明","定密"]
  //生成定频数据
  const dp_171 = ()=>{
    const dparr = []
    for (let i=0;i<4;i++){
      dp["信道"] = Math.ceil(Math.random()*18)
      dp['信道'] = dp['信道']<10?"0"+dp['信道']:dp['信道']
      const num = Math.round(Math.random()*6)
      dp['网络模式'] = wlms[num]

      switch (num) {
        case 0:
          dp['工作模式'] = "定频"
          break
        case 1:
          dp['工作模式'] = "定频"
          break
        case 2:
          dp['工作模式'] = "定频"
          break
        case 3:
          dp['工作模式'] = "定频"
          break
        case 4:
          dp['工作模式'] = "定密"
          break
        case 5:
          dp['工作模式'] = Math.round(Math.random()*1)
          dp['工作模式'] = gzms[dp['工作模式']]
          break
        case 6:
          dp['工作模式'] = "定密"
          break
      }

      const khz = 0.025 * Math.ceil(Math.random()*40)
      dp["频率"] = (Math.ceil(Math.random()*(87-30)+30) + khz).toFixed(3)
      if(dp["频率"]>87.975){
        dp["频率"] = 87.975
      }
      if(num<4){
        dp['传输密匙'] = Math.round(Math.random()*19)
        dp['传输密匙'] = dp['传输密匙']<10?"0"+dp['传输密匙']:dp['传输密匙']
        dp['信息密匙'] = Math.round(Math.random()*19)
        dp['信息密匙'] = dp['信息密匙']<10?"0"+dp['信息密匙']:dp['信息密匙']
      }else if(num<6){
        dp['传输密匙'] = '无'
        dp['信息密匙'] = '无'
      }else {
        dp['传输密匙'] = '0'+Math.round(Math.random()*9)
        dp['信息密匙'] = '0'+Math.round(Math.random()*7)
      }
      if(num==1||num==4||num==6){
        dp['接入方式'] = Math.round(Math.random()*1)
        dp['接入方式'] = jrfs[dp['接入方式']]
      }else {
        dp['接入方式'] = "无"
      }
      const newDP = JSON.parse(JSON.stringify(dp))
      for (let i in newDP){
        newDP[i] = {value:newDP[i],equipmentValue:''}
      }
      dparr.push(newDP)
    }
    const n = Math.ceil(Math.random()*16)
    dparr.forEach((item,index)=>{
      item["信道"] = n+index
      item['信道'] = item['信道']
      item['信道'] = {value: item['信道'],equipmentValue:''}
    })
    const obj = {}
    //需要判断对错的数量
    obj.number = 4 * 6
    obj.errorNumber = 0
    obj.score = 0
    obj.dp = dparr
    return obj
  }
  return{
    dp_171
  }
}


export default function equipment_121C(formData){
  const dp = {
    //121C设备只有联络文件这几个参数
    '信道':'',//0-9
    '网络模式':'TBR_121C',
    '工作模式':'定频明话',//兼容模式下为定明和定密 其余为定频
    '频率':'',//30.000MHz 到 87.975MHz
    // '传输密钥':'',//0-9
  }
  const tp = {
    "信道":'',//0-9
    '网络模式':'TBR_121C',
    "工作模式":'跳频密话',
    "频率表号":'0',//0-9
    "网号":'',//0-9
    "密钥":'',//0-9
  }
  const wlms = ["战斗网CNR","分组网PRN","数据链LINK-地空","数据链LINK-地面","兼容体制-121-无线分组","兼容体制-121-点点数传","兼容体制-96G"]
  const gzms = ["定明","定密","数话",'低数据通信']
  //生成定频数据
  const dp_121C = ()=>{
    const dparr = []
    for (let i=0;i<1;i++){
      const khz = 0.025 * Math.ceil(Math.random()*40)
      dp["频率"] = (Math.ceil(Math.random()*(87-30)+30) + khz).toFixed(3)
      if(dp["频率"]>87.975){
        dp["频率"] = 87.975
      }
      // const num = Math.round(Math.random()*3)
      // dp['传输密钥'] =Math.round(Math.random()*9)
      const newDP = JSON.parse(JSON.stringify(dp))
      for (let i in newDP){
        newDP[i] = {value:newDP[i],equipmentValue:''}
      }
      dparr.push(newDP)
    }
    const n = Math.ceil(Math.random()*5)
    dparr.forEach((item,index)=>{
      item["信道"] = n+index
      item['信道'] = item['信道']
      item['信道'] = {value: item['信道'],equipmentValue:''}
    })
    const obj = {}
    //需要判断对错的数量
    obj.number = 4 * 4
    obj.errorNumber = 0
    obj.score = 0
    obj.dp = dparr
    return obj
  }
  //生成跳频数据
  const tp_121C = ()=>{
    const tparr = []
    for (let i=0;i<1;i++){
      tp['信道'] = Math.round(Math.random()*9)
      tp['密钥'] = Math.round(Math.random()*9)
      tp['频率表号'] = Math.round(Math.random()*9)
      tp['网号'] = Math.round(Math.random()*255)
      tp['网号'] = tp['网号']<10?"00"+tp['网号'] :tp['网号']<100?"0"+tp['网号'] :tp['网号']
      const newDP = JSON.parse(JSON.stringify(tp))
      for (let i in newDP){
        newDP[i] = {value:newDP[i],equipmentValue:''}
      }
      tparr.push(newDP)
    }
    const n = Math.ceil(Math.random()*5)
    tparr.forEach((item,index)=>{
      item["信道"] = n+index
      item['信道'] = item['信道']
      item['信道'] = {value: item['信道'],equipmentValue:''}
    })
    const obj = {}
    //需要判断对错的数量
    obj.number = 4 * 4
    obj.errorNumber = 0
    obj.score = 0
    obj.tp = tparr
    return obj
  }
  const generateData_121C = (type)=>{
    let data
    if(type==1){
      data = dp_121C()
    }else {
      data =  tp_121C()
    }
    return data
  }
  return{
    generateData_121C
  }
}
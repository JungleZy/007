

export default function equipment_173(){
  const dp = {
    '信道':'',//00-19
    '网络模式':'',//分组网 数据链 升空
    '工作模式':'定频',//兼容模式下为定明和定密 其余为定频
    '频率':'',//225MHz 到 512MHz  步进50khz
    '传输密钥':'',//0-19
    '工作密钥':'',//0-19
  }
  const tp = {
    '信道':'',//00-19
    '网络模式':'',//分组网 数据链 升空
    '工作模式':'跳频',//兼容模式下为定明和定密 其余为定频
    '表号':'',//0-19
    '网号':'',//0-255
    '传输密钥':'',//0-19
    '工作密钥':'',//0-19
  }
  const wlms = ["分组网","数据链","升空"]
  //生成定频数据
  const data_173 = (number)=>{
    const dparr = []
    let data
    if (number==1){
      data = dp
    }else {
      data = tp
    }
    for (let i=0;i<1;i++){
      data["信道"] = Math.ceil(Math.random()*18)
      data['信道'] = data['信道']<10?"0"+data['信道']:data['信道']
      const num = Math.round(Math.random()*2)
      data['网络模式'] = wlms[num]
      if (number==1){
        const khz = 0.05 * Math.ceil(Math.random()*20)
        data["频率"] = Math.round(Math.random()*(512-225)+225) + khz
        if(data["频率"]>512){
          data["频率"] = 512
        }
      }else {
        tp["表号"] = Math.round(Math.random()*19)
        tp["网号"] = Math.round(Math.random()*255)
      }

      data["传输密钥"] = Math.round(Math.random()*19)
      data["工作密钥"] = Math.round(Math.random()*19)
      const newDP = JSON.parse(JSON.stringify(data))
      for (let i in newDP){
        newDP[i] = {value:newDP[i],equipmentValue:''}
      }
      dparr.push(newDP)
    }

    const n = Math.ceil(Math.random()*12)
    dparr.forEach((item,index)=>{
      item["信道"] = n+index
      item['信道'] = item['信道']
      item['信道'] = {value: item['信道'],equipmentValue:''}
    })
    const obj = {}
    //需要判断对错的数量
    obj.number =( 4+number) * 7
    obj.errorNumber = 0
    obj.score = 0
    obj.data = dparr
    return obj
  }
  return{
    data_173
  }
}


export default function equipment_125W(formData){
  const zdkz={
    '网络参数号':'',//00-99
    '本台地址':'',
    '网络地址':'',
    '呼叫信道表号':'',
    '呼叫信道信道':'',
    '业务信道表号':'',
    '业务信道信道':'',
    '低速报信道表号':'',
    '低速报信道信道':'',
    '跳频频率表':'',
  }
  const tp = {
    '网号':'',//0-2
    '参数号':'',//00-10
    '频率表号':'',//00-11
    '密钥号':'',//0-9
    '中心频率':'',//1.6MHz-30MHz
  }
  //生成自动控制数据
  const assembleZDKZ_125W = ()=>{
    //     '网络参数号':'',//00-99
//     '本台地址':'',//001-998
//     '网络地址':'',//001-998
//     '呼叫信道表号':'',00-99
//     '呼叫信道信道':'',000-999
//     '业务信道表号':'',00-99
//     '业务信道信道':'',000-999
//     '低速报信道表号':'',00-99   476  380 304 266
//     '低速报信道信道':'',000-999
//     '跳频频率表':'',0-3
    zdkz['网络参数号'] = Math.floor(Math.random()*99)
    zdkz['网络参数号'] = zdkz['网络参数号']<10?"0"+zdkz['网络参数号']:zdkz['网络参数号']
    zdkz['本台地址'] = Math.ceil(Math.random()*998)
    switch (true) {
      case zdkz['本台地址']<10:
        zdkz['本台地址'] =  "00"+zdkz['本台地址']
        break;
      case zdkz['本台地址']<100:
        zdkz['本台地址'] = "0"+zdkz['本台地址']
        break;
      case zdkz['本台地址']>99:
        zdkz['本台地址'] = ""+zdkz['本台地址']
    }
    zdkz['网络地址'] = Math.ceil(Math.random()*998)
    switch (true) {
      case zdkz['网络地址']<10:
        zdkz['网络地址'] = "00"+zdkz['网络地址']
        break;
      case zdkz['网络地址']<100:
        zdkz['网络地址'] = "0"+zdkz['网络地址']
        break;
      case zdkz['网络地址']>99:
        zdkz['网络地址'] = ""+zdkz['网络地址']
        break
    }
    zdkz['呼叫信道表号'] = Math.floor(Math.random()*99)
    zdkz['呼叫信道表号'] = zdkz['呼叫信道表号']<10?"0"+zdkz['呼叫信道表号']:zdkz['呼叫信道表号']

    zdkz['呼叫信道信道'] = Math.floor(Math.random()*999)
    switch (true) {
      case zdkz['呼叫信道信道']<10:
        zdkz['呼叫信道信道'] = "00"+zdkz['呼叫信道信道']
        break;
      case zdkz['呼叫信道信道']<100:
        zdkz['呼叫信道信道'] = "0"+zdkz['呼叫信道信道']
        break;
      case zdkz['呼叫信道信道']>99:
        zdkz['呼叫信道信道'] = ""+zdkz['呼叫信道信道']
        break
    }
    zdkz['业务信道表号'] = Math.floor(Math.random()*99)
    zdkz['业务信道表号'] = zdkz['业务信道表号']<10?"0"+zdkz['业务信道表号']:zdkz['业务信道表号']
    zdkz['业务信道信道'] = Math.floor(Math.random()*999)
    switch (true) {
      case zdkz['业务信道信道']<10:
        zdkz['业务信道信道'] = "00"+zdkz['业务信道信道']
        break;
      case zdkz['业务信道信道']<100:
        zdkz['业务信道信道'] = "0"+zdkz['业务信道信道']
        break;
      case zdkz['业务信道信道']>99:
        zdkz['业务信道信道'] = ""+zdkz['业务信道信道']
        break
    }

    zdkz['低速报信道表号'] = Math.floor(Math.random()*99)
    zdkz['低速报信道表号'] = zdkz['低速报信道表号']<10?"0"+zdkz['低速报信道表号']:zdkz['低速报信道表号']
    zdkz['低速报信道信道'] = Math.floor(Math.random()*999)
    switch (true) {
      case zdkz['低速报信道信道']<10:
        zdkz['低速报信道信道'] = "00"+zdkz['低速报信道信道']
        break;
      case zdkz['低速报信道信道']<100:
        zdkz['低速报信道信道'] = "0"+zdkz['低速报信道信道']
        break;
      case zdkz['低速报信道信道']>99:
        zdkz['低速报信道信道'] = ""+zdkz['低速报信道信道']
        break
    }

    zdkz['跳频频率表'] = Math.floor(Math.random()*3)

    for (let i in zdkz){
      zdkz[i] = {value:zdkz[i],equipmentValue:''}
    }
    return zdkz
  }
  //生成跳频数据
  const assembleTP_125W = ()=>{
    tp["网号"] = Math.floor(Math.random()*2)
    tp["参数号"] = Math.floor(Math.random()*10)
    tp['参数号'] = tp['参数号']<10?"0"+tp['参数号']:tp['参数号']

    tp["频率表号"] = Math.floor(Math.random()*11)
    tp['频率表号'] = tp['频率表号']<10?"0"+tp['频率表号']:tp['频率表号']
    tp["密钥号"] = Math.floor(Math.random()*9)
    tp["中心频率"] = (Math.random()*(30-1.6)+1.6).toFixed(1)
    for (let i in tp){
      tp[i] = {value:tp[i],equipmentValue:''}
    }
    return tp
  }
  //生成信道频率
  const frequency_125W = ()=>{
    let arr = []
    const num = Math.floor(Math.random()*50)
    for (let i=1;i<num+1;i++){
      arr.push({
        "信道号":{value:i,equipmentVlaue:''},
        "频率":{value:(Math.random()*(30-20)+20).toFixed(6),equipmentValue:''}
      })
    }
    return arr
  }
  return{
    assembleZDKZ_125W,
    assembleTP_125W,
    frequency_125W
  }
}
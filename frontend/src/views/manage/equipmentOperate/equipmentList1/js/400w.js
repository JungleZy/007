


export default function equipmentFore(){
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
  const assembleZDKZ_400w = ()=>{
//     '网络参数号':'',//00-99
//     '本台地址':'',//网络参数号+000-200
//     '网络地址':'',//网络参数号+000-200
//     '呼叫信道表号':'',00-99
//     '呼叫信道信道':'',00-64
//     '业务信道表号':'',00-99
//     '业务信道信道':'',00-64
//     '低速报信道表号':'',00-99
//     '低速报信道信道':'',0-1
//     '跳频频率表':'',0-2

    zdkz['网络参数号'] = Math.floor(Math.random()*99)
    zdkz['网络参数号'] = zdkz['网络参数号']<10?"0"+zdkz['网络参数号']:zdkz['网络参数号']
    zdkz['本台地址'] = Math.floor(Math.random()*200)
    switch (true) {
      case zdkz['本台地址']<10:
        zdkz['本台地址'] =  zdkz['网络参数号']+"00"+zdkz['本台地址']
        break;
      case zdkz['本台地址']<100:
        zdkz['本台地址'] =  zdkz['网络参数号']+"0"+zdkz['本台地址']
        break;
      case zdkz['本台地址']>99:
        zdkz['本台地址'] =  zdkz['网络参数号']+""+zdkz['本台地址']
    }
    zdkz['网络地址'] = Math.floor(Math.random()*200)
    switch (true) {
      case zdkz['网络地址']<10:
        zdkz['网络地址'] =  zdkz['网络参数号']+"00"+zdkz['网络地址']
        break;
      case zdkz['网络地址']<100:
        zdkz['网络地址'] =  zdkz['网络参数号']+"0"+zdkz['网络地址']
        break;
      case zdkz['网络地址']>99:
        zdkz['网络地址'] =  zdkz['网络参数号']+""+zdkz['网络地址']
        break
    }
    zdkz['呼叫信道表号'] = Math.floor(Math.random()*99)
    zdkz['呼叫信道表号'] = zdkz['呼叫信道表号']<10?"0"+zdkz['呼叫信道表号']:zdkz['呼叫信道表号']
    zdkz['呼叫信道信道'] = Math.floor(Math.random()*64)
    zdkz['呼叫信道信道'] = zdkz['呼叫信道信道']<10?"0"+zdkz['呼叫信道信道']:zdkz['呼叫信道信道']

    zdkz['业务信道表号'] = Math.floor(Math.random()*99)
    zdkz['业务信道表号'] = zdkz['业务信道表号']<10?"0"+zdkz['业务信道表号']:zdkz['业务信道表号']
    zdkz['业务信道信道'] = Math.floor(Math.random()*64)
    zdkz['业务信道信道'] = zdkz['业务信道信道']<10?"0"+zdkz['业务信道信道']:zdkz['业务信道信道']

    zdkz['低速报信道表号'] = Math.floor(Math.random()*99)
    zdkz['低速报信道表号'] = zdkz['低速报信道表号']<10?"0"+zdkz['低速报信道表号']:zdkz['低速报信道表号']
    zdkz['低速报信道信道'] = Math.floor(Math.random())

    zdkz['跳频频率表'] = Math.floor(Math.random()*2)

    for (let i in zdkz){
      zdkz[i] = {value:zdkz[i],equipmentValue:''}
    }
    return zdkz
  }
  //生成信道频率
  const frequency_400W = ()=>{
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
    assembleZDKZ_400w,
    frequency_400W
  }
}
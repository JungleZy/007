


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
  const tp = {
    '网号':'',//0-99
    '参数号':'',//00-99
    '频率表号':'',//暂时不知 暂用00-11
    '密钥号':'',//6位数字最大999999
    // '中心频率':'',//1.6MHz-30MHz
  }
  const zdhh = {
    '单台地址':'',//3到15位数字或者大写字母
    '网络地址':'',//3到15位数字或者大写字母
    // '跳频台号':'',//0-2
    // '低速报地址':'',//000-999
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
    zdkz['本台地址'] = Math.floor(Math.random()*999)
    switch (true) {
      case zdkz['本台地址']<10:
        zdkz['本台地址'] =  "00"+zdkz['本台地址']
        break;
      case zdkz['本台地址']<100:
        zdkz['本台地址'] =  "0"+zdkz['本台地址']
        break;
      case zdkz['本台地址']>99:
        zdkz['本台地址'] =  ""+zdkz['本台地址']
    }
    zdkz['网络地址'] = Math.floor(Math.random()*999)
    switch (true) {
      case zdkz['网络地址']<10:
        zdkz['网络地址'] =  "00"+zdkz['网络地址']
        break;
      case zdkz['网络地址']<100:
        zdkz['网络地址'] =  "0"+zdkz['网络地址']
        break;
      case zdkz['网络地址']>99:
        zdkz['网络地址'] =  ""+zdkz['网络地址']
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
  const frequency_400W = (type)=>{
    let arr = []
    const num = Math.floor(Math.random()*(50-1)+1)
    for (let i=1;i<2;i++){
      const xdh = i<10?"00"+i:"0"+i
      const s = (Math.random()*(29.999999-2)+2).toFixed(6)
      const f = (Math.random()*(29.999999-2)+2).toFixed(6)
      let obj =  {
        "信道号":{value:xdh,equipmentValue:''},
        "收频率":{value:s<10?'0'+s:s,equipmentValue:''},
        "发频率":{value:f<10?'0'+f:f,equipmentValue:''}
      }
      if(type==0){
      delete obj['发频率']
      }else if(type==1){
       delete obj['收频率']
      }
      arr.push(obj)
    }
    return arr
  }
  //生成跳频数据
  const assembleTP_400W = ()=>{
    tp["网号"] = Math.round(Math.random()*99)
    tp['网号'] = tp['网号']<10?"0"+tp['网号']:tp['网号']
    tp["参数号"] = Math.round(Math.random()*99)
    tp['参数号'] = tp['参数号']<10?"0"+tp['参数号']:tp['参数号']
    tp["密钥号"] = Math.floor(Math.random()*(999999-100000)+100000)
    tp["频率表号"] = Math.floor(Math.random()*11)
    tp['频率表号'] = tp['频率表号']<10?"0"+tp['频率表号']:tp['频率表号']
    for (let i in tp){
      tp[i] = {value:tp[i],equipmentValue:''}
    }
    return tp
  }
  //生成自动呼号数据
  const assembleZDHH_400W = ()=>{
    zdhh['低速报地址'] = Math.floor(Math.random()*999)
    switch (true) {
      case zdhh['低速报地址']<10:
        zdhh['低速报地址'] =  "00"+zdhh['低速报地址']
        break;
      case zdhh['低速报地址']<100:
        zdhh['低速报地址'] =  "0"+zdhh['低速报地址']
        break;
      case zdhh['低速报地址']>99:
        zdhh['低速报地址'] =  ""+zdhh['低速报地址']
    }
    const n = Math.ceil(Math.random()*(15-3)+3)
    const m = Math.ceil(Math.random()*(15-3)+3)
    zdhh["单台地址"]="";
    for (let i=0;i<n;i++){
      const n = Math.round(Math.random())
      if(n==0){
        zdhh["单台地址"]+= Math.round(Math.random()*9)
      }else {
        zdhh["单台地址"]+= String.fromCharCode(65+Math.ceil(Math.random()*25))
      }
    }
    zdhh["网络地址"]='';
    for (let i=0;i<m;i++){
      const n = Math.round(Math.random())
      if(n==0){
        zdhh["网络地址"]+= Math.round(Math.random()*9)
      }else {
        zdhh["网络地址"]+= String.fromCharCode(65+Math.ceil(Math.random()*25))
      }
    }
    // zdhh["跳频台号"] = Math.round(Math.random()*2)
    for (let i in zdhh){
      zdhh[i] = {value:zdhh[i],equipmentValue:''}
    }
    return zdhh
  }

  //生成数据
  const generateData_400W = (type=2)=>{
    const data = {}
    //信道号 频率
    data.frequency = frequency_400W(type)
    //需要判断对错的数量  zdhh 4  zdkz 7 tp 4
    data.number = data.frequency.length*2
    data.errorNumber = 0
    data.score = 0
    if(type==2){
      //生成自动控制数据
      data.zdkz = assembleZDKZ_400w()
      //生成跳频数据
      data.tp = assembleTP_400W()
      //生成自动呼号数据
      data.zdhh = assembleZDHH_400W()
      //zdhh 4  zdkz 10 tp 4
      data.number = 4 + 10 + 4 + data.frequency.length*2

      data.tp['网号'] = data.zdkz['网络参数号']
      data.tp['参数号'] = data.zdkz['网络参数号']
      // data.tp['频率表号'] = data.zdhh['跳频台号']
    }
    return data
  }
  return{
    generateData_400W
  }
}
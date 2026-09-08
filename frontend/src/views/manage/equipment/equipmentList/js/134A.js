

export default function equipment_134A(formData){

  const power = ["5W","20W"]
  const gzfs = ["上边带","下边带",'边带报']
  //生成定频数据
  const dp_134A = ()=>{
    const dparr = []
    const p = Math.round(Math.random())
    for (let i=0;i<4;i++){
      const dp = {
        '信道':'',//信道1-8，0是人工信道
        '收频':'',//工作频率（1.6～29.9999MHz间）
        '发频':'',//工作频率（1.6～29.9999MHz间）
        '工作方式':'',//上边带（USB）、下边带（LSB）、边带报（CW）
        '功率':'',//L（或H）表示小功率5W（或大功率20W）
      }
      const n = Math.round(Math.random())
      dp["信道"] = i
      dp["收频"] = (Math.random()*(29.9999-1.6)+1.6).toFixed(4)
      dp["发频"] = dp["收频"]
      dp['功率'] = power[p]
      const m = Math.round(Math.random()*2)
      dp['工作方式'] = gzfs[n]
      for (let i in dp){
        dp[i] = {value:dp[i],equipmentValue:''}
      }
      dparr.push(dp)
    }
    const obj = {}
    //需要判断对错的数量
    obj.number = 12+1
    obj.errorNumber = 0
    obj.score = 0
    obj.dp = dparr
    return obj
  }
  const tp_134A = ()=>{
    const tpData = {
      '网号':"",//0-2
      '参数号':"",//0-8
      '表号':"",//00-02
      '中心频率':"",//00000-99999
      '密钥号':"",//0-9
      '密钥':""//十位数，小于4294967296
    }
    tpData['网号'] = Math.round(Math.random()*2)
    tpData['参数号'] = Math.round(Math.random()*8)
    tpData['表号'] ="0" + Math.round(Math.random()*2)
    tpData['中心频率'] = (Math.round(Math.random()*99999)).toString()
    const num = 5-tpData['中心频率'].length
    let str = ""
    for (let i=0;i<num;i++){
      str+="0"
    }
    tpData['中心频率'] = str + tpData['中心频率']
    tpData['密钥号'] = Math.round(Math.random()*9)
    tpData['密钥'] = (Math.round(Math.random()*42)).toString()
    for (let i=0;i<8;i++){
      tpData['密钥']+= Math.round(Math.random()*9)
    }
    if(tpData['密钥']>4294967296){
      tpData['密钥'] = 4294967296
    }
    for (let i in tpData){
      tpData[i] = {value:tpData[i],equipmentValue:''}
    }


    const obj = {}
    //需要判断对错的数量
    obj.number = 6
    obj.errorNumber = 0
    obj.score = 0
    obj.tp = tpData
    //需要判断对错的数量
    // tpData.number = 6
    // tpData.errorNumber = 0
    // tpData.score = 0

    return obj
  }
  const zsy_134A = ()=>{
    // {
    //   "单台地址":100-299,
    //   "单台信道":0-99,
    //   "网号":300-319,
    //   "网信道":0-99,
    //   "分组":1-9,
    //   "信道号":0-99,
    //   "信道号":0-99,
    //   "信道":0-99,
    //   "常频":00000-99999,
    // }
    const zsyData = {
      "单台地址":[],//100-299
      "分组":[],//1-9
      "信道":[],//0-99
      "网号":'',//300-319
      "网信道":[],//0-99
      "网成员":[],//100-299
    }

    const xdNumber = 6//信道最多个数
    while (zsyData['单台地址'].length<10){
      const obj = {
        '单台地址':Math.round(Math.random()*(299-100)+100),
        '信道号':[]//0-99
      }
      if( zsyData['单台地址'].every(item=>item['单台地址']!=obj['单台地址'])){
        const n = Math.round(Math.random()*(xdNumber-1)+1)
        while (obj['信道号'].length<n){
          let xd = Math.round(Math.random()*99)
          xd = xd<10?'0'+xd:xd
          if( obj['信道号'].every(item=>item!=xd)){
            obj['信道号'].push(xd)
          }
        }
        obj['信道号'].sort((a,b)=>a-b)
        zsyData['单台地址'].push(obj)
      }
    }
    zsyData['单台地址'].sort((a,b)=>a['单台地址']-b['单台地址'])

    while (zsyData['分组'].length<2){
      const obj = {
        '分组':Math.round(Math.random()*(8-1)+1),
        '信道号':[]//0-99
      }
      if( zsyData['分组'].every(item=>item['分组']!=obj['分组'])){
        const n = Math.round(Math.random()*(xdNumber-1)+1)
        while (obj['信道号'].length<n){
          let xd = Math.round(Math.random()*99)
          xd = xd<10?'0'+xd:xd
          if( obj['信道号'].every(item=>item!=xd)){
            obj['信道号'].push(xd)
          }
        }
        obj['信道号'].sort((a,b)=>a-b)
        zsyData['分组'].push(obj)
      }
    }
    zsyData['分组'].sort((a,b)=>a['分组']-b['分组'])

    while (zsyData['信道'].length<20){
      const xd = Math.round(Math.random()*99)
      const obj = {
        '信道':xd<10?"0"+xd:xd,
        '常频':''//00000-99999
      }
      if( zsyData['信道'].every(item=>item['信道']!=obj['信道'])){
        obj['常频'] = Math.round(Math.random()*999999).toString()
        if (obj['常频'].length<6){
          for (let i=0;i<(6-obj['常频'].length);i++){
            obj['常频'] = '0' + obj['常频']
          }
        }
        zsyData['信道'].push(obj)
      }
    }
    zsyData['信道'].sort((a,b)=>a['信道']-b['信道'])

    while (zsyData['网成员'].length<xdNumber){
      const obj = Math.round(Math.random()*(299-100)+100)
      if( zsyData['网成员'].every(item=>item['网成员']!=obj)){
        zsyData['网成员'].push(obj)
      }
    }
    zsyData['网成员'].sort((a,b)=>a-b)
    let newList=Array.from(new Set(zsyData['网成员']))
    zsyData['网成员']=JSON.parse(JSON.stringify(newList))
    while (zsyData['网信道'].length<xdNumber){
      let obj = Math.round(Math.random()*99)
      obj = obj<10?'0'+obj:obj
      if( zsyData['网信道'].every(item=>item['网信道']!=obj)){
        zsyData['网信道'].push(obj)
      }
    }
    zsyData['网信道'].sort((a,b)=>a-b)
    let newListS=Array.from(new Set(zsyData['网信道']))
    zsyData['网信道']=JSON.parse(JSON.stringify(newListS))

    zsyData['单台地址'].forEach((item)=>{
      for (let i in item){
        item[i] = {value:item[i],equipmentValue:''}
      }
    })
    zsyData['分组'].forEach((item)=>{
      for (let i in item){
        item[i] = {value:item[i],equipmentValue:''}
      }
    })
    zsyData['信道'].forEach((item)=>{
      for (let i in item){
        item[i] = {value:item[i],equipmentValue:''}
      }
    })
    zsyData['网成员'] = {value:zsyData['网成员'],equipmentValue:''}
    zsyData['网信道'] = {value:zsyData['网信道'],equipmentValue:''}
    zsyData['网号'] = {value:Math.round(Math.random()*(319-300)+300),equipmentValue:''}

    const obj = {}
    //需要判断对错的数量  10 + 3 + 2 + zsyData['信道']
    obj.number = 20 + 3 + 4 + zsyData['信道'].length*2
    obj.errorNumber = 0
    obj.score = 0
    obj.zsy = zsyData
    return obj

  }
  return{
    dp_134A,
    tp_134A,
    zsy_134A
  }
}
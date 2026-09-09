export const analyzeChannel=(value1,value2)=> {
    value1.forEach((item,index)=>{
        item.forEach((v,ind)=>{
            value2.forEach(param=>{
                if (v.xdValues===param.indexs){
                    v.params=param.receptionChananel
                }
                if (v.xdValuef===param.indexs){
                    v.params=param.sendChananel
                }
            })
        })
    })
    return value1
}

export const netIP=(value1,value2)=>{
    value1.forEach((item,index)=>{
        let serialNumber=0
        item.forEach((v,ind)=>{
            if (v.isParameter==='网路地址'){
                v.params=value2[0].networkdress
            }
            if (v.isParameter==='序号'){
                serialNumber=v.value
            }
            if (v.isParameter==='单台地址'){
                v.params=value2[serialNumber].dressname
            }
        })
    })
    return value1
}
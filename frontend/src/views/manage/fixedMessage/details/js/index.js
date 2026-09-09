import {ref,onMounted,onBeforeUnmount,nextTick} from "vue";
import {useRoute,useRouter} from 'vue-router'
import {message} from 'ant-design-vue'
import {getCableType,getCableFloorAllByID,saveMesage,getCableAllByID} from '../../../../../common/api/CableApi'
import * as mammoth from "mammoth";
import { isUploadSizeAllowed, uploadSizeMessage } from '../../../../../common/utils/uploadLimits.js'

export default function details(){
  const route = useRoute()
  const router = useRouter()
  const pageAllData = ref([])//所有页数据
  const pageData = ref([])//单页数据
  const cableType = ref([])
  const activePage = ref(0)//选中页报文
  const focusIndex = ref(0)

  const messageData = ref({
    title:'',//标题
    typeId:'',//类型id
    scope:0,//用途
    codeType:0,//报底类型
    codeSort:0,//长短码
    remark:''//备注
  })
  onMounted(()=>{
    if(route.query.id){
      getCableAllByID({id:route.query.id}).then(res=>{
        console.log(res)
        messageData.value.id = res.data.id
        messageData.value.title = res.data.title
        messageData.value.typeId = res.data.typeId
        messageData.value.scope = res.data.scope
        messageData.value.codeType = res.data.codeType
        messageData.value.codeSort = res.data.codeSort
        messageData.value.remark = res.data.remark
      })
      getCableFloorAllByID({id:route.query.id}).then(res=>{
        res.data.forEach((item,index)=>{
          const codeAll = []
          for (let i = 0; i < 10; i++) {
            codeAll.push([[''], [''], [''], [''], [''], [''], [''], [''], [''], ['']])
          }
          item.forEach((key,i)=>{
            let str = ''
            key.forEach(s=>str+=s)
            codeAll[Math.floor(i/10)][i%10] = [str]
          })
          pageAllData.value.push(codeAll)
        })
        selectPage(0)
      })
    }else {
      for (let i = 0; i < 10; i++) {
        pageData.value.push([[''], [''], [''], [''], [''], [''], [''], [''], [''], ['']])
      }
      pageAllData.value.push(pageData.value)
    }
    getCableType().then(res=>{
      cableType.value = res.data
    })
  })
  onBeforeUnmount(() => {
    window.removeEventListener('keydown', keyDownStart)
  })
  //删除报底
  const deletePage = (index)=>{
    if(pageAllData.value.length===1){
      pageAllData.value = []
      pageData.value = []
      for (let i = 0; i < 10; i++) {
        pageData.value.push([[''], [''], [''], [''], [''], [''], [''], [''], [''], ['']])
      }
      pageAllData.value.push(pageData.value)
    }else{
      pageAllData.value.splice(index,1)
      selectPage(0)
    }

  }
  const keyDownStart = e=>{
    if(e.code === 'Space'||e.key==='Enter') {
      if(e.preventDefault){
        e.preventDefault()
      }else {
        window.event.returnValue == false
      }
    }
    if(e.key==='Enter'&&focusIndex.value%10===9&&focusIndex.value!==99){
      focusIndex.value++
      const dom = document.querySelectorAll('.keyCol')
      nextTick(()=>{
        dom[focusIndex.value].focus()
      })
    }
    if(e.code === 'Space'&&focusIndex.value%10!==9){
      focusIndex.value++
      const dom = document.querySelectorAll('.keyCol')
      nextTick(()=>{
        dom[focusIndex.value].focus()
      })
    }
  }
  const getFocus = (index)=>{
    focusIndex.value = index
  }
  //添加报底
  const addPageMessage = ()=>{
    console.log(pageData.value)
    let isFalseCode = regLastPage()
    if(isFalseCode){
      message.error('不规范报底请核对后在新增！')
      return
    }
    const arr = []
    for (let i = 0; i < 10; i++) {
      arr.push([[''], [''], [''], [''], [''], [''], [''], [''], [''], ['']])
    }
    pageAllData.value.push(arr)
    selectPage(pageAllData.value.length-1)

  }
  const regFun = (text)=>{
    let isFalse = false
    if(messageData.value.codeType===0){
      isFalse =/^[0-9 \r\n\t]+$/.test(text)
    }else if(messageData.value.codeType===1){
      isFalse = /^[a-zA-Z \r\n\t]+$/.test(text)
    }else {
      isFalse = /^[a-zA-Z0-9 \r\n\t]+$/.test(text)
    }
    return isFalse
  }
  //选择报底
  const selectPage = (index)=>{
    activePage.value = index
    pageData.value = pageAllData.value[index]
  }
  //返回
  const goBack = ()=>{
    router.back()
  }
  //提交报文信息
  const commitMessage = ()=>{
    let isFalseCode = regLastPage()
    if(isFalseCode){
      message.error('不规范报底请核对后在提交！')
      return
    }
    if(messageData.value.title.trim()===''){
      message.error('请填写报底名称！')
      return
    }
    if(messageData.value.typeId===""){
      message.error('请选择所属报底！')
      return
    }
    const arr = []
    pageAllData.value.forEach(v=>{
      const onePage = []
      v.forEach(item=>{
        item.forEach(code=>{
          if(code[0].trim()) onePage.push((code[0].trim()).split(""))
        })
      })
      arr.push(onePage)
    })
    messageData.value.floors = arr
    saveMesage(messageData.value).then(res=>{
      if(res.code===200){
        message.success('提交成功！')
        goBack()
      }else {
        message.error('提交失败！')
      }
    })
  }
  //导入报底
  const uploadChange = async (e)=>{
    console.log(e)
    if (!e.file) return false
    let i = e.file.name.lastIndexOf('.')
    let str = ''
    let name = ''
    name = e.file.name.substr(0, i)
    if (!isUploadSizeAllowed(e.file)) {
      message.error(uploadSizeMessage())
      if (e.onError) e.onError(new Error(uploadSizeMessage()))
      return false
    }
    let reader = new FileReader()
    if (e.file.type === 'text/plain') {
      reader.readAsText(e.file,'UTF-8')
      reader.onload = (ev) => {
        str = ev.target.result
        console.log(JSON.stringify(str))
        regTest(str)
      }
    } else {
      reader.readAsArrayBuffer(e.file)
      reader.onload = (ev) => {
        console.log(e);
        mammoth.extractRawText({arrayBuffer: ev.target.result}).then(res => {
          str = res.value
          console.log(JSON.stringify(str))
          regTest(str)
        })
      }
    }

    e.file.status = 'done';
    e.onSuccess()
    /*if(e.file.response){
      data.value.name = e.file.response.data.name
      data.value.content = e.file.response.data.content
    }*/
  }
  //导入正则验证
  const regTest = (text)=>{
    console.log(text);
    if(messageData.value.codeType===0){
      //数码
      const isTrue = /^[0-9 \r\n\t]+$/.test(text)
      if(isTrue){
        formatMessage(text)
      }else {
        message.error('导入失败，请确认导入报底格式是否为数码！')
        return
      }
    }else if(messageData.value.codeType===1){
      //字码
      const isTrue = /^[a-zA-Z \r\n\t]+$/.test(text)
      if(isTrue){
        formatMessage(text)
      }else {
        message.error('导入失败，请确认导入报底格式是否为字码！')
        return
      }
    }else {
      const isTrue = /^[a-zA-Z0-9 \r\n\t]+$/.test(text)
      if(isTrue){
        formatMessage(text)
      }else {
        message.error('导入失败，请确认导入报底格式！')
        return
      }
    }
  }
  //格式化导入的报底
  const formatMessage = (text)=>{
    let str = text.replaceAll(' ','')
     str = str.replaceAll('\r\n','')
     str = str.replaceAll('\n','')
     str = str.replaceAll('\t','')
    str = str.toUpperCase()
    const arr = str.split('')
    const codeAll = []
    let groupCode = ''
    arr.forEach((item,index)=>{
      groupCode+=item
      if(index%4===3){
        codeAll.push(groupCode)
        groupCode = ''
      }
    })
    let pageCodeAll = []
    let pageCode = []
    let lineCode = []
    codeAll.forEach((item,index)=>{
      lineCode.push([item])
      if(index%10===9&&index>0){
        pageCode.push(JSON.parse(JSON.stringify(lineCode)))
        lineCode = []
      }
      if(index%100===99&&index>0){
        pageCodeAll.push(JSON.parse(JSON.stringify(pageCode)))
        pageCode = []
      }
    })
    if(lineCode.length>0){
      pageCode.push(lineCode)
    }
    if(pageCode.length>0){
      pageCodeAll.push(pageCode)
    }
    pageCodeAll.forEach((item,index)=>{
      const codeAll = []
      for (let i = 0; i < 10; i++) {
        codeAll.push([[''], [''], [''], [''], [''], [''], [''], [''], [''], ['']])
      }
      item.forEach((line,i)=>{
        line.forEach((key,index)=>{
          codeAll[i][index] = key
        })
      })
      if(pageData.value[0][0][0]===''&&index===0){
        pageData.value = codeAll
        pageAllData.value[pageAllData.value.length-1] = codeAll
      }else {
        pageAllData.value.push(codeAll)
      }
    })
  }
  //新增、提交报底验证最后一页报底格式 只能为4个一组 且前后不能重复出现
  const regLastPage = ()=>{
    let isFalseCode = false
    pageAllData.value[pageAllData.value.length-1].forEach(line=>{
      line.forEach((item,index)=>{
        if(index>0&&(line[index][0]===line[index-1][0])&&line[index][0]!==''){
          message.error('相邻报底，不能出现重复报文！')
          isFalseCode = true
        }
        if(item[0]===''||item[0].length!==4){
          isFalseCode = true
        }
        if(isFalseCode===false){
          isFalseCode = !regFun(item[0])
        }
      })
    })
    return isFalseCode
  }
  return{
    pageData,messageData,cableType,commitMessage,deletePage,pageAllData,addPageMessage,selectPage,activePage,goBack,getFocus,uploadChange,keyDownStart
  }
}
import { message, Modal } from 'ant-design-vue'
import { ref, nextTick, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import moment from 'moment'
import 'moment/dist/locale/zh-cn.js'
import { deleteTheoryKnowledgeQuestionLevelById, saveTheoryKnowledgeQuestion, findAllQuestionByLevelId, deleteTheoryKnowledgeQuestion, downloadTemplate,exportQuestionBank } from '../../../../../../common/api/TheoryQuestionBankApi'
import { deepClone } from '../../../../../../common/utils/Utils.js'
import { listSort } from '../../../../../../components/test/nodeTree/listSort'
import axios from 'axios'
import { saveAs } from 'file-saver'
import * as mammoth from "mammoth";
import { Document, Packer, Paragraph, TextRun } from "docx";
export default function knowledgeTabel(selecttreeA, roomtest, topicType, emit, activeList, activeKnowledge) {
  // onMounted(()=>{
  //   radio()
  //   multiSelect()
  //   blanks()
  //   judge()
  // })
  const knowledgeShow = ref(false)
  const columns = ref([
    {
      title: '标题',
      dataIndex: 'topic',
      align: 'center',
      key: 'topic',
      ellipsis: true
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      align: 'center',
      key: 'createTime',
      width: 160,
      ellipsis: true,
      slots: { customRender: 'createTime' }
    },
    {
      title: '创建人',
      dataIndex: 'createUserName',
      align: 'center',
      width: 160,
      key: 'createUserName'
    },
    {
      title: '类型',
      dataIndex: 'types',
      align: 'center',
      width: 80,
      key: 'types'
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      align: 'center',
      slots: { customRender: 'action' }
    }
  ])
  const findAllQuestionId = ref('')
  const listData = ref([])
  const searchStr = ref('')
  const tableLoading = ref(true)
  const difficulty = ref(undefined)
  const params = ref({ type: '1', topic: '', options: [], answer: '', analysis: '' })
  const typeCheckList = ref([
    { name: '单选', id: '1' },
    { name: '多选', id: '2' },
    { name: '判断', id: '3' },
    { name: '填空', id: '4' },
    { name: '简答', id: '5' }
  ])
  const openModel = ref({})
  const knowledgeId = ref('-1')
  const son = ref(null)
  let flag = false
  const dg = tree => {
    for (let i in tree) {
      if (tree[i].children) {
        dg(tree[i].children)
      } else if (tree[i].type) {
        flag = true
      }
    }
  }
  const takeNew = () => {
    dg(selecttreeA.value)
    if (!flag) {
      message.error('请至少建立一个知识节点!')
      return
    }
    params.value = { type: '1', topic: '', options: [], answer: '', analysis: '' }
    knowledgeShow.value = true
  }
  const takeNoTestVisible = () => {
    Modal.confirm({
      title: () => '您确定退出么?',
      content: () => '暂未保存!',
      // icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      onOk() {
        knowledgeShow.value = false
      }
    })
  }
  const bornTest = () => {
    let flag = false

    const item = roomtest.value.params
    if (item.topic == '') {
      message.error('您的题目没有输入完全!请检查后提交!')
      flag = true
    }
    if (item.type == 1) {
      if (item.answer == '') {
        message.error('您的答案暂未填写!请检查后提交!')
        flag = true
      }
    } else if (item.type == 2) {
      if (item.answer.length == 0) {
        message.error('您的答案暂未填写!请检查后提交!')
        flag = true
      }
    } else if (item.type == 3) {
      if (item.answer == '') {
        message.error('您的答案暂未填写!请检查后提交!')
        flag = true
      }
    } else if (item.type == 4) {
      if (item.answer == []) {
        message.error('您的答案暂未填写!请检查后提交!')
        flag = true
      } else if (item.answer.length != 0) {
        item.answer.forEach((val, i) => {
          if (val == '') {
            message.error('您的答案暂未填写!请检查后提交!')
            flag = true
          }
        })
      }
    } else if (item.type == 5) {
      if (item.answer == '') {
        message.error('您的答案暂未填写!请检查后提交!')
        flag = true
      }
    }
    if (!roomtest.value.selectTruelyValue) {
      message.error('请选择该题的知识节点!请检查后提交!')
      flag = true
    }
    if (flag) return
    let obj = deepClone(item)
    obj.options = JSON.stringify(obj.options)
    obj.answer = JSON.stringify(obj.answer)
    obj.levelId = roomtest.value.selectTruelyValue
    saveTheoryKnowledgeQuestion(obj).then(res => {
      if (res.code == 200) {
        message.success('提交成功!')
        knowledgeShow.value = false
        findAllQuestion(findAllQuestionId.value == -1 ? findAllQuestionId.value : { key: findAllQuestionId.value })
      } else {
        message.success('提交失败!')
      }
    })
  }
  const activeAction = ref({})
  let selectID = -1 //导出题库时用
  const findAllQuestion = e => {
    activeAction.value = e
    selectID = e == -1 ? e : e.value
    knowledgeId.value = e == -1 ? e : e.parentId == 1 ? e.parentId : e.value
    tableLoading.value = true
    findAllQuestionId.value = e == -1 ? 1 : e.key
    const data = {
      levelId: findAllQuestionId.value,
      type: topicType ? topicType : ''
    }
    getQuestion(data)
    // findAllQuestionByLevelId(data).then(res => {
    //   if (res.code===200) {
    //     let list=res.data;
    //     for (let j of list) {
    //       j.types=typeCheckList.value[Number(j.type)-1].name;
    //       j.options=JSON.parse(j.options);
    //       j.isClick=false;
    //       if (activeList) {
    //         activeList.forEach(item=>{
    //           if (j.id==item) {
    //             j.isClick=true;
    //           }
    //         })
    //       }
    //     }
    //     listData.value=list;
    //     tableLoading.value=false;
    //   }
    // })
  }
  const getQuestion = obj => {
    findAllQuestionByLevelId(obj).then(res => {
      if (res.code === 200) {
        let list = res.data
        for (let j of list) {
          j.types = typeCheckList.value[Number(j.type) - 1].name
          j.options = JSON.parse(j.options)
          j.isClick = false
          if (activeList) {
            activeList.forEach(item => {
              if (j.id == item) {
                j.isClick = true
              }
            })
          }
        }
        listData.value = listSort(list)
        tableLoading.value = false
      }
    })
  }
  const serchQuestion = e => {
    const data = {
      levelId: '',
      type: '',
      name: ''
    }
    if (searchStr.value) {
      data.name = searchStr.value
    } else {
      delete data.name
    }
    if (findAllQuestionId.value) {
      data.levelId = findAllQuestionId.value
    } else {
      delete data.levelId
    }
    if (difficulty.value) {
      data.type = difficulty.value
    } else {
      data.type = topicType ? topicType : ''
    }
    if (e === 'delete') {
      searchStr.value = ''
      data.type = ''
      difficulty.value = undefined
    }
    getQuestion(data)
  }
  const deleteTheoryKnowledge = e => {
    Modal.confirm({
      title: () => '确定删除该题么',
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      onOk() {
        deleteTheoryKnowledgeQuestion({
          id: e
        }).then(res => {
          if (res.code === 200) {
            message.success('删除成功')
            findAllQuestion(findAllQuestionId.value == -1 ? findAllQuestionId.value : { key: findAllQuestionId.value })
          }
        })
      }
    })
  }
  const queryModal = e => {
    let obj = deepClone(e)
    obj.answer = JSON.parse(obj.answer)
    obj.type = String(obj.type)
    params.value = obj
    knowledgeShow.value = true
  }
  const toView = e => {
    let obj = deepClone(e)
    obj.answer = JSON.parse(obj.answer)
    obj.type = String(obj.type)
    if (obj.type == 1 || obj.type == 3) {
      obj.answer = String(obj.answer)
    }
    if (obj.type == 4) {
      obj.topic = obj.topic.split('$_$').join('(___)')
    }
    openModel.value.value = obj
    openModel.value.bool = true
    nextTick(() => {
      son.value.analyzeTheTopic()
    })
  }
  const handlePrevious = () => {
    const index = listData.value.findIndex(item => item.id == openModel.value.value.id)
    if (index == 0) {
      message.error('已到第一题！')
      return
    }
    toView(listData.value[index - 1])
  }
  const handleNext = () => {
    const index = listData.value.findIndex(item => item.id == openModel.value.value.id)
    if (index == listData.value.length - 1) {
      message.error('已到最后一题！')
      return
    }
    toView(listData.value[index + 1])
  }
  const addQuestion = (e, bool) => {
    let obj = deepClone(e)
    obj.answer = JSON.parse(obj.answer)
    obj.type = String(obj.type)
    if (obj.type == 1 || obj.type == 3) {
      obj.answer = String(obj.answer)
    }
    if (obj.type == 4) {
      obj.topic = obj.topic.split('$_$').join('(___)')
    }
    if (bool) {
      e.isClick = true
    } else {
      e.isClick = false
    }
    emit('clickActive', obj, bool)
  }
  //单选
  let num = 0
  let arrObj = []
  let type = true
  //单选
  const radio = (arr)=>{
    let  obj = {
      analysis: "",
      answer: "",
      levelId:activeAction.value.key,
      options: [],
      topic: "",
      type: "1"
    }
    obj.topic = arr[num].substring(arr[num].indexOf("、")+1,arr[num].length)
    const answerStr = arr[num+2].substring(arr[num+2].indexOf("：")+1,arr[num+2].length).replace("\r","")
    switch (answerStr) {
      case "A":
        obj.answer = "0"
        break;
      case "B":
        obj.answer = "1"
        break;
      case "C":
        obj.answer = "2"
        break;
      case "D":
        obj.answer = "3"
        break;
    }
    const options=[]
    options.push(arr[num+1].substring(arr[num+1].indexOf("A"),arr[num+1].indexOf("B")).trim())
    options.push(arr[num+1].substring(arr[num+1].indexOf("B"),arr[num+1].indexOf("C")).trim())
    options.push(arr[num+1].substring(arr[num+1].indexOf("C"),arr[num+1].indexOf("D")).trim())
    options.push(arr[num+1].substring(arr[num+1].indexOf("D"),arr[num+1].length).trim())
    // arr[num-1].split("   ")
    for (let i in options){
      obj.options.push({
        value:i,
        label:options[i].substring(options[i].indexOf("、")+1,options[i].length)
      })
    }
    obj = deepClone(obj)
    obj.answer = JSON.stringify(obj.answer)
    obj.options = JSON.stringify(obj.options)
    arrObj.push(obj)
    num+=3
  }
  //多选
  const multiSelect = (arr)=>{
    const obj = {
      analysis: "",
      answer: "",
      levelId: activeAction.value.key,
      options: [],
      topic: "",
      type: "2"
    }
    obj.topic = arr[num].substring(arr[num].indexOf("、")+1,arr[num].length)
    const answerStr = arr[num+2].substring(arr[num+2].indexOf("：")+1,arr[num+2].length).replace("\r","")
    const answerArr = answerStr.split("")
    obj.answer = []
    for (let v of answerArr){
      switch (v) {
        case "A":
          obj.answer.push("0")
          break;
        case "B":
          obj.answer.push("1")
          break;
        case "C":
          obj.answer.push("2")
          break;
        case "D":
          obj.answer.push("3")
          break;
      }
    }
    const options=[]
    // arr[num-1].split("   ")
    options.push(arr[num+1].substring(arr[num+1].indexOf("A"),arr[num+1].indexOf("B")).trim())
    options.push(arr[num+1].substring(arr[num+1].indexOf("B"),arr[num+1].indexOf("C")).trim())
    options.push(arr[num+1].substring(arr[num+1].indexOf("C"),arr[num+1].indexOf("D")).trim())
    options.push(arr[num+1].substring(arr[num+1].indexOf("D"),arr[num+1].length).trim())
    for (let i in options){
      obj.options.push({
        value:i,
        label:options[i].substring(options[i].indexOf("、")+1,options[i].length)
      })
    }
    obj.answer = JSON.stringify(obj.answer)
    obj.options = JSON.stringify(obj.options)
    arrObj.push(obj)
    num+=3
  }
  //填空
  const blanks = (arr)=>{
    const obj = {
      analysis: "",
      answer: [],
      levelId: activeAction.value.key,
      options: [],
      topic: "",
      type: "4"
    }
    obj.topic = arr[num].substring(arr[num].indexOf("、")+1,arr[num].length)
    obj.topic = obj.topic.replace("（）","$_$")
    const answerStr = arr[num+1].substring(arr[num+1].indexOf("：")+1,arr[num+1].length).replace("\r","")
    obj.answer.push(answerStr)
    obj.answer = JSON.stringify(obj.answer)
    obj.options = JSON.stringify(obj.options)
    arrObj.push(obj)
    num+=2
  }
  //简答
  const short = (arr)=>{
    const obj = {
      analysis: "",
      answer: [],
      levelId: activeAction.value.key,
      options: [],
      topic: "",
      type: "5"
    }
    obj.topic = arr[num].substring(arr[num].indexOf("、")+1,arr[num].length)
    const answerStr = arr[num+1].substring(arr[num+1].indexOf("：")+1,arr[num+1].length).replace("\r","")
    obj.answer.push(answerStr)
    obj.answer = JSON.stringify(obj.answer)
    obj.options = JSON.stringify(obj.options)
    arrObj.push(obj)
    num+=2
  }
  //判断
  const judge = (arr)=>{
    const obj = {
      analysis: "",
      answer: "",
      levelId: activeAction.value.key,
      options: [{
        id:"1",
        name:"对"
      },
        {
          id:"2",
          name:"错"
        }
      ],
      topic: "",
      type: "3"
    }
    obj.topic = arr[num].substring(arr[num].indexOf("、")+1,arr[num].length)
    const answerStr = arr[num+1].substring(arr[num+1].indexOf("：")+1,arr[num+1].length).replace("\r","")
    switch (answerStr) {
      case "对":
        obj.answer = "1"
        break;
      case "错":
        obj.answer = "2"
        break;
    }
    obj.answer = JSON.stringify(obj.answer)
    obj.options = JSON.stringify(obj.options)
    arrObj.push(obj)
    num+=2
    // for (let v of arrObj){
    //   saveTheoryKnowledgeQuestion(v)
    // }
    // console.log(arrObj)
  }
  const uploadKnowledge = () => {}
  // 处理批量上传文件的数据
  const uploadDataHandle = (str)=>{
    num = 0
    type = true
    const arrStr = ["一、单项选择题","二、不定项选择题","三、判断题","四、填空题","五、简答题"]
    if(str){
      str = str.replaceAll("\nB","B")
      str = str.replaceAll("\nC","C")
      str = str.replaceAll("\nD","D")
      str = str.replaceAll("\r","")
      const arr = str.split("\n")
      arrStr.forEach(str=>{
        const index = arr.findIndex(item=>item.indexOf(str)>-1)
        if(index>-1){
          arr.splice(index,1)
        }
      })
      //删除空字符串
      for (let i=0;i<arr.length;i++){
        if (arr[i]==""){
          arr.splice(i,1)
          i--
        }
      }
      do{
        let answerStr,answerStr2, topicStr
        if(arr[num+2]){
          answerStr =  (arr[num+2].substring(arr[num+2].indexOf("：")+1,arr[num+2].length)).replaceAll("\r","")
        }
        answerStr2 = arr[num+1].substring(arr[num+1].indexOf("：")+1,arr[num+1].length).replaceAll("\r","")
        topicStr = arr[num].substring(arr[num].indexOf("、")+1,arr[num].length).replaceAll("\r","")
        const reg = /^[A-Z]/
        if(reg.test(answerStr)&&answerStr.length<5&&/^[A-Z]+$/i.test(answerStr)){
          if(arr[num+2].indexOf("答案")==-1){
            message.error("关键字"+arr[num]+"临近几题格式有误！")
            // console.log(arr[num])
            num = arr.length
            return;
          }
          if(answerStr.length==1){
            radio(arr)
          }else {
            multiSelect(arr)
          }
        }else {
          if(arr[num+1].indexOf("答案")==-1){
            message.error("关键字"+arr[num]+"临近几题格式有误！")
            // console.log(arr[num])
            num = arr.length
            return;
          }
          if(answerStr2.length==1){
            judge(arr)
          }else {
            if (topicStr.indexOf('（）') > -1) {
              blanks(arr)
            } else {
              short(arr)
            }
          }
        }
      }while (num<=arr.length-1)
      if(arrObj.length!=0){
        for (let v of arrObj){
          saveTheoryKnowledgeQuestion(v)
        }
      }
      setTimeout(()=>{
        findAllQuestion( activeAction.value)
        message.success("上传成功！")
      },2000)
    }
  }
  const uploadChange = async (e)=>{
    // return
    // uploadDataHandle(e)
    if (!e.file) return false
    let reader = new FileReader()
    reader.readAsArrayBuffer(e.file)
    reader.onload = (ev) => {
      mammoth.extractRawText({arrayBuffer: ev.target.result, preserveWhiteSpace: true}).then(res => {
        // console.log(JSON.stringify(res.value));
        arrObj=[]
        uploadDataHandle(res.value)
      })
    }
    e.file.status = 'done';
    e.onSuccess()
  }
  const exportTemplate1 = type => {
    if (type == 1 && selectID == -1) {
      message.error('请先选择要导出的题库！')
      return
    }
    const url = type == 0 ? 'http://' + window.httpUrl + '/api/theoryKnowledgeQuestion/exportTemplate' : 'http://' + window.httpUrl + '/api/theoryKnowledgeQuestion/exportQuestionByLevelId'
    axios({
      // 用axios发送post请求
      method: 'POST',
      url,
      // data: formData, // 参数
      responseType: 'blob', // 表明返回服务器返回的数据类型
      headers: {
        token: localStorage.getItem('token'),
        deviceId: localStorage.getItem('deviceId')
      },
      data: {
        levelId: selectID
      }
    }).then(res => {
      // 处理返回的文件流
      const filename = type == 0 ? '模板' : activeAction.value.title
      let blob = new Blob([res.data], { type: 'application/force-download' }) //type是文件类，详情可以参阅blob文件类型
      // 创建新的URL并指向File对象或者Blob对象的地址
      const blobURL = window.URL.createObjectURL(blob)
      // 创建a标签，用于跳转至下载链接
      const tempLink = document.createElement('a')
      tempLink.style.display = 'none'
      tempLink.href = blobURL
      tempLink.setAttribute('download', filename + '.docx')
      // 兼容：某些浏览器不支持HTML5的download属性
      if (typeof tempLink.download === 'undefined') {
        tempLink.setAttribute('target', '_blank')
      }
      // 挂载a标签
      document.body.appendChild(tempLink)
      tempLink.click()
      document.body.removeChild(tempLink)
      // 释放blob URL地址
      window.URL.revokeObjectURL(blobURL)
    })
    // const str =
    //     `<div style='font-weight: bold;font-size: 16px;line-height: 40px'>一、单项选择题\n</div>
    //      <div style='font-size: 13px;line-height: 22px'>1、无线电话务联络回答时，回答守听对方信号强度（）次，询问守听这里信号强度1次。</div>
    //      <div style='font-size: 13px;line-height: 22px'>&nbsp;A、1-3&nbsp;&nbsp;B、1、0&nbsp;&nbsp;C、2、0&nbsp;&nbsp;D、3、0</div>
    //      <div style='font-size: 13px;line-height: 22px'>&nbsp;答案：B</div>
    //      <div style='font-weight: bold;font-size: 16px;line-height: 40px'>二、不定项选择题\n</div>
    //      <div style='font-size: 13px;line-height: 22px'>1、如有数份同等级电报应按哪些方式处理（）。</div>
    //      <div style='font-size: 13px;line-height: 22px'>&nbsp;D、4A、时间的先后&nbsp;&nbsp;B、号数的顺序&nbsp;&nbsp;C、通信方向的主次&nbsp;&nbsp;D、上级的指示</div>
    //      <div style='font-size: 13px;line-height: 22px'>&nbsp;答案：ABCD</div>
    //      <div style='font-weight: bold;font-size: 16px;line-height: 40px'>三、判断题\n</div>
    //      <div style='font-size: 13px;line-height: 22px'>1、接力转话是边收边译逐句转出。</div>
    //      <div style='font-size: 13px;line-height: 22px'>&nbsp;答案：错</div>
    //      <div style='font-weight: bold;font-size: 16px;line-height: 40px'>四、填空题\n</div>
    //      <div style='font-size: 13px;line-height: 22px'>2、自动通信网通报程序与工作方法：办报手续发报办理中，电报发出后，在打印的报头第一行右侧填写给收据的（）。</div>
    //      <div style='font-size: 13px;line-height: 22px'>&nbsp;答案：时间和签名</div>`
    // // const bolb = new Blob([str],{type:"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"})表格
    // const bolb = new Blob(["str"],{type:"application/vnd.openxmlformats-officedocument.wordprocessingml.document"})
    // saveAs(bolb,"配置.docx")
  }
  const exportTemplate = (type)=>{
    if(type==1&&selectID==-1){
      message.error("请先选择要导出的题库！")
      return
    }
    if (type == 0) {
      const tempLink = document.createElement('a')
      tempLink.style.display = 'none'
      tempLink.href = window.fileUrl+'/006/题库-模板.docx'
      document.body.appendChild(tempLink)
      tempLink.click()
      document.body.removeChild(tempLink)
    } else {
      exportQuestionBank({
        levelId:selectID
      }).then(res => {
        if (res.code === 200) {
          let arr = [],filterArr = [];
          for (let i=1;i<=5;i++) {
            filterArr = res.data.filter(item => item.type==i)
            if (filterArr.length > 0) {
              arr.push(filterArr)
            }
          }
          if (arr.length > 0) {
            handleExportWordDataInfo(arr)
          }
        }
      })
    }
  }

  const handleExportWordDataInfo = (arr) => {
    let docChild = [];
    let arrStr = ["一、单项选择题","二、不定项选择题","三、判断题","四、填空题","五、简答题"]
    let arrOpt = ["A","B","C","D"]
    arr.forEach(item => {
      // 添加题所属类型
      docChild.push(
          new Paragraph({
            spacing: { before: 160 },
            children: [
              new TextRun({
                text: arrStr[item[0].type-1],
                bold: true,
                size: 26,
                color: '#000000'
              })
            ]
          })
      )
      item.forEach((topic, t) => {
        // 添加题目的标题
        docChild.push(
            new Paragraph({
              spacing: { before: 80 },
              text: (t+1)+'、'+topic.topic
            })
        )
        // 添加选择题选项
        topic.options = JSON.parse(topic.options);
        let optStr = '\r';
        if (topic.type == 1 || topic.type == 2) {
          topic.options.forEach(opt => {
            optStr += '    '+arrOpt[parseInt(opt.value)]+'、'+opt.label
          })
          docChild.push(
              new Paragraph({
                text: optStr
              })
          )
        }
        // 添加答案
        topic.answer = JSON.parse(topic.answer);
        let answerStr = '    答案：'
        if (topic.type == 1) {
          answerStr += arrOpt[parseInt(topic.answer)]
        } else if (topic.type == 2) {
          topic.answer.forEach(ans => {
            answerStr += arrOpt[parseInt(ans)]
          })
        } else if (topic.type == 3) {
          answerStr += (topic.answer=='1'?'对':'错')
        } else if (topic.type == 4) {
          answerStr += topic.answer.join('；')
        } else if (topic.type == 5) {
          answerStr += topic.answer
        }
        docChild.push(
            new Paragraph({
              text: answerStr
            })
        )
      })
    })

    const doc = new Document({
      sections: [{ children: docChild }]
    })
    Packer.toBlob(doc).then(blob => {
      const tempLink = document.createElement('a')
      tempLink.style.display = 'none'
      tempLink.href = URL.createObjectURL(blob)
      tempLink.download = activeAction.value.title + '.docx'
      document.body.appendChild(tempLink)
      tempLink.click()
      document.body.removeChild(tempLink)
    })
  }
  return {
    typeCheckList,
    tableLoading,
    difficulty,
    listData,
    columns,
    knowledgeShow,
    params,
    openModel,
    searchStr,
    knowledgeId,
    son,
    exportTemplate,
    uploadChange,
    takeNew,
    takeNoTestVisible,
    bornTest,
    findAllQuestion,
    deleteTheoryKnowledge,
    queryModal,
    toView,
    addQuestion,
    serchQuestion,
    handlePrevious,
    handleNext
  }
}

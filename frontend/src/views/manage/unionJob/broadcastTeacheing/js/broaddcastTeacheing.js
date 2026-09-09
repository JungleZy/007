import {ref, onMounted, createVNode} from 'vue'
import moment from 'moment'
import {letterKey, numberKey} from '../../../../../components/preJob/telexTrain/js/enum'
import {getAllStudent, addRoom, findRoom,addRoomZuXun,findRoomZuXun,deleteTrain} from '../../../../../common/api/broaddcastTeacheingApi'
import {apiSimulationRouterFindPage} from '../../../../../common/api/UserApi'

import {message, Modal} from 'ant-design-vue'
import {useRoute} from 'vue-router'
import useMorse from '../../../../../common/mixin/useMorse.js'
import {saveHeader} from "../../../../../common/api/ReceiveApi";
import {ExclamationCircleOutlined} from "@ant-design/icons-vue";

export default function broaddcastTeacheing(selectCable) {
  const addDrillModal = ref(false) //弹框
  const {baseCode, morseCode} = useMorse()
  const route = useRoute()
  const trainData = ref({
    type: 0,
    count: 100,
    mode: 0,
    ruleId: ''
  })
  const isHeader = ref(false)
  const messageHeader = ref({
    nr:'11',
    plb:'55',
    date:'',
    year:'',
    remaks:''
  })
  const checked = ref()
  const columns = ref([
    {
      title: '训练名称',
      dataIndex: 'name',
      key: 'name',
      align: 'center',
      slots: {customRender: 'name'}
    },
    {
      title: '报底类型',
      dataIndex: 'bdType',
      key: 'bdType',
      align: 'center',
      slots: {customRender: 'bdType'}
    },
    {
      title: '报文类型',
      dataIndex: 'bwType',
      key: 'bwType',
      align: 'center',
      slots: {customRender: 'bwType'}
    },
    {
      title: '报文组数',
      dataIndex: 'bwCount',
      key: 'bwCount',
      align: 'center',
      slots: {customRender: 'bwCount'}
    },
    {
      title: '创建人',
      dataIndex: 'user',
      key: 'user',
      align: 'center',
      slots: {customRender: 'user'}
    },
    {
      title: '状态',
      dataIndex: 'stats',
      key: 'stats',
      width: 120,
      align: 'center',
      slots: {customRender: 'stats'}
    },
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      align: 'center',
      width: 120,
      slots: {customRender: 'action'}
    }
  ])
  const cacheData = ref([])
  const tableData = ref([])
  const totalPage = ref(1)
  const currPage = ref(1)
  const isRandom = ref(true)
  const isAverage = ref(false)
  const formData = ref({
    roomName: (route.name==='ReceiveZuXunList'?'收报组训-':'通播教学-') + moment().format('YYMMDDhhmmss'),
    bdType: 1,
    bwType: 1,
    bwCount: 100,
    mainSignal: 60,
    isShort: false,
    content: [],
    isCable: 0,
    startPage: 1,
    isStartSign:true
  })
  let self = ref('')
  self.value = [JSON.parse(localStorage.getItem('userInfo')).id]

  /**
   * 新增训练
   * @returns {boolean}
   */
  const addTelexTrain = () => {
    if(isHeader.value){
      const reg = /^[0-9]+$/
      if (messageHeader.value.nr==='') {
        message.error('请输入报头号数！')
        return
      }
      if (messageHeader.value.plb==='') {
        message.error('请输入报头等级！')
        return
      }
      if(!reg.test(messageHeader.value.year)){
        message.error('请检查报头年月！')
        return
      }
      if(!reg.test(messageHeader.value.date)){
        message.error('请检查报头时分！')
        return
      }
      if (messageHeader.value.remaks!=='') {
        const reg = /^[A-Za-z0-9 ]+$/
        if(!reg.test(messageHeader.value.remaks)){
          message.error('请输数字加字母组合的附注！')
          return
        }
      }
    }
    if (formData.value.roomName == '') {
      message.error('请填写训练名称后提交')
      return false
    } else if (formData.value.bwCount == '') {
      message.error('请填写报文组数后提交')
      return false
    } else if (checkUser.value.length == 0) {
      message.error('请选择训练人员后提交')
      return false
    }
    // formData.value.content = generateBaoWenInfo();
    let bwType = formData.value.bwType
    if (formData.value.bwType == 1 && !formData.value.isShort) {
      bwType = 1
    } else if (formData.value.bwType == 1 && formData.value.isShort) {
      bwType = 2
    }
    const data = {
      roomName: formData.value.roomName,
      sendUserList: self.value,
      receiveUserList: checkUserId.value,
      content: '',
      // bdType: formData.value.bdType,
      bdType: isAverage.value?1:2,
      mainSignal: formData.value.mainSignal + '',
      bwType: bwType,
      bwCount:  formData.value.bwCount,
      isRandom: isRandom.value ? 1 : 0,
      isCable: formData.value.isCable,
      cableId: formData.value.cableId,
      startPage: formData.value.startPage,
      isStartSign: formData.value.isStartSign? 1 : 0,
      // isAverage:isAverage.value
    }
    if(route.name==='ReceiveZuXunList'){
      addRoomZuXun(data).then(res => {
        addAction(res)
      })
    }else {
      addRoom(data).then(res => {
        addAction(res)
      })
    }
  }
  const addAction = (res)=>{
    if (res.code == 200) {
      if(isHeader.value){
        const header = generateMessageHeader(res.data)
        saveHeader({trainId:res.data.id,content:header}).then(res=>{
          if(res.code!==200){
            message.error('报头生成失败！')
          }
        })
      }
      message.success('添加成功')
      cancelTrainModal()
      findRoomInfo()
    }
  }
  const findRoomInfo = () => {
    if(route.name==='ReceiveZuXunList'){
      findRoomZuXun().then(res => {
        tableLoading.value = false
        if (res.code === 200) {
          cacheData.value = res.data
          tableData.value = res.data.filter((item, i) => i < 10)
          totalPage.value = Math.ceil(cacheData.value.length / 10)
        }
      })
    }else {
      findRoom().then(res => {
        tableLoading.value = false
        if (res.code === 200) {
          cacheData.value = res.data
          tableData.value = res.data.filter((item, i) => i < 10)
          totalPage.value = Math.ceil(cacheData.value.length / 10)
        }
      })
    }
  }

  const tableLoading = ref(false)
  //关闭弹框
  const cancelTrainModal = () => {
    isHeader.value = false
    messageHeader.value = {
      nr:'11',
      plb:'55',
      date:'',
      year:'',
      remaks:''
    }
    addDrillModal.value = false
    isRandom.value = true
    isAverage.value = false
    checkUser.value = []
    checked.value = false
    formData.value = {
      roomName: (route.name==='ReceiveZuXunList'?'收报组训-':'通播教学-') + moment().format('YYMMDDhhmmss'),
      bdType: 1,
      bwType: 1,
      bwCount: 100,
      mainSignal: 60,
      isShort: false,
      content: [],
      isCable: 0,
      startPage: 1,
      isStartSign:true
    }
  }
  let userList = ref([])
  let checkUser = ref([])
  let checkUserId = ref([])

  onMounted(() => {
    const info = JSON.parse(localStorage.getItem('userInfo'))
    getAllStudent().then(res => {
      if (res.code !== 200 || !Array.isArray(res.data)) return
      res.data.forEach((item, i) => {
        if (res.data[i].id == self.value[0]) {
          res.data.splice(i, 1)
        }
      })
      userList.value = res.data.filter(item => item.id != info.id)
    })
    findRoomInfo()
  })
  const changeListPage = pag => {
    if (pag < 1) return false
    currPage.value = pag
    tableData.value = cacheData.value.filter((item, i) => i >= (pag - 1) * 10 && i < pag * 10)
  }

  const changeChecked = () => {
    if (checked.value) {
      checkUser.value = userList.value.map((item, idx) => item.userName + idx)
      checkUserId.value = userList.value.map((item, idx) => item.id)
    } else {
      checkUser.value = []
    }
  }

  const changeUserList = (a, item) => {
    checkUserId.value = item.map(i => i.key)
  }
  const isHeaderChange = ()=>{
    if(isHeader.value){
      const now = new Date();
      const month = String(now.getMonth() + 1).padStart(2, '0')
      const date = String(now.getDate()).padStart(2, '0')
      const hours =  String(now.getHours()).padStart(2, '0')
      const minutes = String(now.getMinutes()).padStart(2, '0')
      messageHeader.value.year = `${month}${date}`
      messageHeader.value.date = `${hours}${minutes}`
    }
  }
  //生成报头数据
  const generateMessageHeader = (data)=>{
    let header = `NR${messageHeader.value.nr} CK${formData.value.bwCount} ${messageHeader.value.plb} ${messageHeader.value.year.replaceAll("0","T")} ${messageHeader.value.date.replaceAll("0","T")}`
    if(messageHeader.value.remaks)header+=' RMKS '+messageHeader.value.remaks
    console.log(header);
    return header.toUpperCase()

  }
  const deleteModel = (v)=>{
    Modal.confirm({
      class: 'init_modal_style',
      content: '是否删除该训练？',
      icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      maskClosable: true,
      onOk: () => {
        deleteHistory(v)
      }
    })
  }
  const deleteHistory = (v)=>{
    deleteTrain({roomId:v.id}).then(res=>{
      if(res.code === 200) {
        message.success('删除成功！')
        findRoomInfo()
      }else {
        message.error('删除失败！')
      }
    })
  }
  return {
    isHeader,
    messageHeader,
    columns,
    tableData,
    cacheData,
    totalPage,
    currPage,
    tableLoading,
    addDrillModal,
    cancelTrainModal,
    changeListPage,
    trainData,
    addTelexTrain,
    userList,
    checkUser,
    formData,
    isRandom,
    isAverage,
    checked,
    changeChecked,
    changeUserList,
    isHeaderChange,
    generateMessageHeader,
    deleteModel
  }
}

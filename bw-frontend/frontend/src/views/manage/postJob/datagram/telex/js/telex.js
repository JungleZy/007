import {message, Modal} from 'ant-design-vue'
import moment from 'moment'
import {ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject} from 'vue'
import {findAll, saveTelexTrain,deleteDataGramList} from '../../../../../../common/api/TelegramApi.js'
import {numberKey, letterKey} from '../../../../../../components/preJob/telexTrain/js/enum.js'
import {useRouter, useRoute} from 'vue-router'
import * as gr from '../../../../../../common/api/GradingRuleApi'

export default function telegramList(addDrillModal) {
  const columns = ref([
    {
      title: '训练名称',
      dataIndex: 'name',
      key: 'name',
      align: 'center',
      slots: {customRender: 'title'}
    }, {
      title: '报文',
      dataIndex: 'isCable',
      key: 'isCable',
      // width: 120,
      align: "center",
      slots: {customRender: 'isCable'},
    }, {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      // width: 120,
      align: 'center',
      slots: {customRender: 'type'}
    }, {
      title: '组数',
      dataIndex: 'groupNumber',
      key: 'groupNumber',
      // width: 120,
      align: 'center',
      slots: {customRender: 'totalNumber'}
    }, {
      title: '用时',
      dataIndex: 'validTime',
      key: 'validTime',
      align: 'center',
      // width: 130,
      slots: {customRender: 'duration'}
    }, {
      title: '码率',
      dataIndex: 'totalSpeed',
      key: 'totalSpeed',
      // width: 120,
      align: 'center',
      slots: {customRender: 'totalSpeed'}
    }, {
      title: '评分',
      dataIndex: 'score',
      key: 'score',
      // width: 120,
      align: 'center',
      slots: {customRender: 'score'}
    }, {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      align: 'center',
      slots: {customRender: 'status'}
    }, {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      align: 'center',
      width: 120,
      slots: {customRender: 'action'}
    }
  ])
  const tableData = ref([])
  const tableList = ref([])
  const currTablePage = ref(0)
  const tableLoading = ref(true)
  const router = useRouter()
  const route = useRoute()
  const trainData = ref({
    type: 0,
    isCable: 0,
    cableId: null,
    startPage: 1,
    count: 100,
    mode: 0,
    ruleId: '',
    patType: 0
  })
  const page = ref({
    pageAll:0,
    currentPage:1,
    row:10,
    totalNumber:0
  })
  const content = ref([])
  onMounted(() => {
    init()
  })
  const init = () => {
    findAll({page:page.value.currentPage,rows:page.value.row,trainType:0}).then(res => {
      tableLoading.value = false
      if (res.code === 200) {
        // tableData.value = res.data.filter(item => item.totalNumber>0);
        res.data.data.forEach(item => {
          let hour = Math.floor((item.duration / 60 / 60) % 24)
          let min = Math.floor((item.duration / 60) % 60)
          let sec = Math.floor(item.duration % 60)
          item.duration = (hour > 10 ? hour : '0' + hour) + ':' + (min > 10 ? min : '0' + min) + ':' + (sec > 10 ? sec : '0' + sec)
          item.speed = item.status === 3 ? item.speed : null
        })
        page.value.pageAll = res.data.totalPage
        page.value.totalNumber = res.data.totalNumber
        tableList.value = res.data.data
        // selectTablePage(1)
      } else {
        message.error(res.message)
      }
    })
  }
  const deleteHistory = (v)=>{
    deleteDataGramList(v.id).then(res=>{
      if(res.data===true){
        message.success('删除成功！')
        init()
      }else {
        message.error('删除失败!')
      }

    })
  }
  const selectType = () => {
    if (trainData.value.type == 0) {
      initContent(numberKey, 10)
    } else if (trainData.value.type == 1) {
      initContent(letterKey, 26)
    } else {
      let arr = [...numberKey, ...letterKey]
      initContent(arr, 36)
    }
  }
  //生成随机报文
  const initContent = (keyboard, num) => {
    let ctAll = []
    for (let i = 0; i < trainData.value.count; i++) {
      let ct = []
      for (let j = 0; j < 4; j++) {
        const mat = Math.floor(Math.random() * num)
        ct.push(keyboard[mat].text2 ? keyboard[mat].text2 : keyboard[mat].text)
      }
      ctAll.push({
        value: null,
        text: ct
      })
    }
    content.value = ctAll
  }
  /**
   * 列表分页切换
   * @param pag
   */
  const selectTablePage = pag => {
    if (pag < 1) return false
    else if (pag > page.value.pageAll) return false
    page.value.currentPage = pag
    init()
  }
  //新增连贯训练
  const addTelexTrain = () => {
    addDrillModal.value = false
    // selectType()
    const data = {
      // content:JSON.stringify(content.value),
      content: '',
      name: trainData.value.name,
      isCable: trainData.value.isCable,
      cableId: trainData.value.cableId,
      startPage: trainData.value.startPage,
      type: trainData.value.type,
      groupNumber: trainData.value.count,
      ruleId: trainData.value.ruleId,
      trainType: 0,
      patType: trainData.value.patType,
    }
    saveTelexTrain(data).then(res => {
      if (res.code == 200) {
        message.success('生成训练成功！')
        router.push({
          path: route.matched[4].path + '/postJobTelexTrain',
          query: {
            id: res.data.id
          }
        })
      } else {
        message.error('生成训练失败！')
      }
    })
  }
  const rule = ref([])
  const getRule = () => {
    gr.getGradingRuleListByType({type: 2}).then(res => {
      rule.value = res.data
      const list = res.data.filter(item => {
        return item.isDefault == 0
      })
      res.data.forEach(item=>{
        if(item.isDefault===0){
          trainData.value.ruleId = item.id
        }
      })
    })
  }
  return {
    columns,
    tableData,
    tableList,
    currTablePage,
    selectTablePage,
    tableLoading,
    selectType,
    initContent,
    trainData,
    addTelexTrain,
    rule,
    getRule,
    deleteHistory,
    page
  }
}

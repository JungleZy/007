import { message, Modal } from 'ant-design-vue'
import moment from 'moment'
import { ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject } from 'vue'
import { wordingAdd, listPage, apiPostTrainGlobalRuleAddRule, apiPostTrainGlobalRuleDeleteById, apiPostTrainGlobalRuleType,deleteList } from '../../../../../../common/api/postWording'
import { useRouter, useRoute } from 'vue-router'

export default function wordList() {
  const columns = ref([
    {
      title: '训练名称',
      dataIndex: 'name',
      key: 'name',
      align: 'center',
      slots: { customRender: 'title' }
    },
    {
      title: '报文类型',
      dataIndex: 'type',
      key: 'type',
      // width: 120,
      align: 'center',
      slots: { customRender: 'type' }
    },
    {
      title: '用时',
      dataIndex: 'duration',
      key: 'duration',
      align: 'center',
      // width: 120,
      slots: { customRender: 'duration' }
    },
    {
      title: '码率',
      dataIndex: 'speed',
      key: 'speed',
      // width: 120,
      align: 'center',
      slots: { customRender: 'speed' }
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      align: 'center',
      slots: { customRender: 'status' }
    },
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      align: 'center',
      width: 120,
      slots: { customRender: 'action' }
    }
  ])
  const tableData = ref([])
  const tableList = ref([])
  const currTablePage = ref(0)
  const tableLoading = ref(true)
  const router = useRouter()
  const route = useRoute()
  const formData = ref({
    trainType: 0,
    type: 0,
    number: 200,
    speed: 60,
    disturb: [],
    name: ''
  })
  const content = ref([])
  const basicDeployData = ref([])
  const gradingRuleModal = ref(false)
  onMounted(() => {
    init()
  })
  const init = () => {
    listPage().then(res => {
      tableLoading.value = false
      if (res.code === 200) {
        const list = res.data.reverse()
        tableData.value = list
        tableList.value = list
        selectTablePage(1)
      } else {
        message.error(res.message)
      }
    })
  }
  const deleteHistory = (v)=>{
    deleteList(v.id).then(res=>{
      if(res.data===true){
        message.success('删除成功！')
        init()
      }else {
        message.error('删除失败!')
      }

    })
  }
  const article = ref([])
  const getArticle = () => {
    getArticleList({ type: 0 }).then(res => {
      trainData.value.wordId = res.data[0].id
      article.value = res.data
    })
  }
  /**
   * 列表分页切换
   * @param pag
   */
  const selectTablePage = pag => {
    if (pag === '-' && currTablePage.value === 1) return false
    else if (pag === '+' && currTablePage.value === Math.ceil(tableData.value.length / 10)) return false
    else if (pag === currTablePage.value) return false

    if (pag === '-') {
      currTablePage.value--
    } else if (pag === '+') {
      currTablePage.value++
    } else {
      currTablePage.value = pag
    }
    tableList.value = []
    tableList.value = tableData.value.filter((item, i) => i >= (currTablePage.value - 1) * 10 && i < currTablePage.value * 10)
  }
  //新增训练
  const addWordTrain = () => {
    let time = moment().format('YYMMDDhhmmss')
    let name = ''
    let type = 0
    let number = formData.value.number
    if (!formData.value.number) {
      message.error('数量不能为空')
    }
    if (!formData.value.speed) {
      message.error('播报码率不能为空')
    }
    switch (formData.value.type) {
      case 0:
        formData.value.name = formData.value.trainType == 0 ? '听报-单词训练-' + time : '用语/含义-用语填空训练-' + time
        break
      case 1:
        formData.value.name = formData.value.trainType == 0 ? '听报-语句训练-' + time : '用语/含义-含义填空训练-' + time
        break
    }
    wordingAdd(formData.value).then(res => {
      const data = {
        id: res.data.id
      }
      router.push({
        path: route.matched[4].path + '/wordingTrain',
        query: data
      })
    })
  }

  //添加类型
  const addGradeType = data => {
    apiPostTrainGlobalRuleAddRule(data).then(res => {
      if (res.code == 200) {
        message.success('保存成功！')
        gradingRuleModal.value = false
      } else {
        message.error('保存失败！')
      }
    })
  }
  //获取列表
  const getGradeTypeList = data => {
    apiPostTrainGlobalRuleType(data).then(res => {
      const list = res.data ?? []
      basicDeployData.value = list.map(item => {
        const obj = {
          ...item,
          start: Number(item.accuracy.split('~')[0]),
          end: Number(item.accuracy.split('~')[1])
        }
        return obj
      })
    })
  }
  //删除类型
  const deleteGradeType = id => {
    apiPostTrainGlobalRuleDeleteById({ id: id, type: 0 }).then(res => {})
  }
  return {
    columns,
    tableData,
    tableList,
    currTablePage,
    selectTablePage,
    tableLoading,
    formData,
    addWordTrain,
    article,
    getArticle,
    addGradeType,
    getGradeTypeList,
    deleteGradeType,
    basicDeployData,
    gradingRuleModal,
    deleteHistory
  }
}

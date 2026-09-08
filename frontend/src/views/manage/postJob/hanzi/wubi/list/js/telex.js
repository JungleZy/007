import { message, Modal } from 'ant-design-vue'
import wubi from 'qq-wubi'
import moment from 'moment'
import { ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject } from 'vue'
import { listPage, hanziAdd, getArticleList,deleteList } from '../../../../../../../common/api/postHanZi.js'
import { numberKey, letterKey } from '../../../../../../../components/preJob/telexTrain/js/enum.js'
import { useRouter, useRoute } from 'vue-router'

export default function telegramList(addDrillModal) {
  const columns = ref([
    {
      title: '训练名称',
      dataIndex: 'name',
      key: 'name',
      align: 'center',
      slots: { customRender: 'title' }
    },
    {
      title: '训练类型',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      align: 'center',
      slots: { customRender: 'type' }
    },
    {
      title: '训练用时',
      dataIndex: 'duration',
      key: 'duration',
      align: 'center',
      slots: { customRender: 'duration' }
    },
    {
      title: '码率',
      dataIndex: 'speed',
      key: 'speed',
      width: 120,
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
  const trainData = ref({
    type: null,
    mode: 0
  })
  const content = ref([])
  onMounted(() => {
    init()
  })
  const init = () => {
    listPage({ type: 1 }).then(res => {
      tableLoading.value = false
      if (res.code === 200) {
        // tableData.value = res.data.filter(item => item.totalNumber>0);
        res.data.forEach(item => {
          let hour = Math.floor((item.duration / 60 / 60) % 24)
          let min = Math.floor((item.duration / 60) % 60)
          let sec = Math.floor(item.duration % 60)
          item.duration = (hour > 10 ? hour : '0' + hour) + ':' + (min > 10 ? min : '0' + min) + ':' + (sec > 10 ? sec : '0' + sec)
        })
        tableList.value = res.data
        tableData.value = res.data
        selectTablePage(1)
      } else {
        message.error(res.message)
      }
    })
  }
  const selectType = () => {}
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
  const addTelexTrain = () => {
    addDrillModal.value = false
    selectType()
    let time = moment().format('YYMMDDhhmmss')
    let name = ''
    let type = 0
    switch (trainData.value.type) {
      case 0:
        name = '文章训练-' + time
        type = 0
        break
      case 1:
        name = '词组训练-军语词组-' + time
        type = 1
        break
    }
    const data = { type, name }
    if (type === 0) {
      data.wordId = trainData.value.wordId
    }
    hanziAdd(data).then(res => {
      const data = {
        id: res.data.id
      }
      router.push({
        path: route.matched[4].path + '/postWuBiPractice',
        query: data
      })
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
  return {
    columns,
    tableData,
    tableList,
    currTablePage,
    selectTablePage,
    tableLoading,
    selectType,
    trainData,
    addTelexTrain,
    article,
    getArticle,
    deleteHistory
  }
}

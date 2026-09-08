import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import useMorse from '../../../../../../../common/mixin/useMorse.js'
import { partTimeFormatInfo } from '../../../../../../../common/utils/Utils.js'
import { getTelexTrainByID, apiPostTelexPatTrainGetPage } from '../../../../../../../common/api/TelegramApi.js'
import {endPatDetail, getDatagramZuXunPageNumber,getPatValue} from '../../../../../../../common/api/datagramZuXun'
import { deepClone } from '../../../../../../../common/utils/Utils.js'
import * as echarts from 'echarts'

export default function telegramList(patHairTrendBoxRef,selfId) {
  const loading = ref(true)
  const route = useRoute()
  const userInfo = JSON.parse(localStorage.getItem('userInfo'))
  const scoreData = ref({
    trainId: '',
    currPage: 1,
    errorNumber: 0,
    speed: 0,
    accuracy: 0,
    validTime: 0,
    preContent: [],
    content: [],
    nextContent: [],
    change: 0,
    name:""
  })
  const page = ref({
    pageAll: 0,
    current: 1
  })
  const situaData = ref([])
  const testData = ref([])
  onMounted(() => {
    if (selfId == '') {
      selfId = userInfo.id
    }
    if (route.query.id && route.query.id !== '') {
      scoreData.value.trainId = route.query.id
      endPatDetail({
        trainId: scoreData.value.trainId,
        userId: selfId,
        pageNumber:1
      }).then(res => {
        loading.value = false
        if (res.code === 200) {
          scoreData.value.name = res.data.title
          page.value.pageAll = res.data.totalNumber
          scoreData.value.speed = res.data.speed
          scoreData.value.accuracy = res.data.accuracy
          scoreData.value.score = res.data.score
          scoreData.value.duration = res.data.duration
          const messageData = formatData(res.data.content)
          scoreData.value.content = messageData.filter(item=>item.pageNumber===1&&item.sort>-1)
          scoreData.value.nextContent = messageData.filter(item=>item.pageNumber===2&&item.sort>-1)
          testData.value = res.data.content.filter(item=>item.sort==-1)
          testData.value.sort((a,b)=>a.pageNumber-b.pageNumber)
          scoreData.value.deductInfo = JSON.parse(res.data.deductInfo);
          scoreData.value.ruleContent = JSON.parse(res.data.ruleContent);
          if(res.data.isCable===1){
            page.value.pageAll = res.data.pageCount
          }else {
            page.value.pageAll = Math.ceil(res.data.totalNumber / 100)
          }
        }
      })
    }
  })
  const pageTurn = async num => {
    if (num == 1) {
      if (page.value.current == page.value.pageAll) {
        return false
      }
      page.value.current++
      scoreData.value.preContent = deepClone(scoreData.value.content)
      scoreData.value.content = deepClone(scoreData.value.nextContent)
    } else {
      if (page.value.current == 1) {
        return false
      }
      page.value.current--
      scoreData.value.nextContent = deepClone(scoreData.value.content)
      scoreData.value.content = deepClone(scoreData.value.preContent)
    }
    let number = page.value.current + num
    if (number > page.value.pageAll || number < 1) return false
    const res = await getDatagramZuXunPageNumber({
      pageNumber: number,
      userId: selfId,
      trainId: scoreData.value.trainId
    })
    const data = res.data.messageVO.filter(item=>item.sort>-1)
    if (num == 1) {
      scoreData.value.nextContent = formatData(data)
      testData.value.push(res.data.messageVO[0])
    } else {
      scoreData.value.preContent = formatData(data)
    }
    return false
  }
  //格式化数据
  const formatData = (data)=>{
    let arr = []
    data.forEach(item => {
      let a = arr[arr.length-1]
      if(arr.length==0||arr[arr.length-1].sort!==item.sort){
        if(a?.list){
          a.list.sort((a,b)=>a[0]-b[0])
          let str = []
          a.list.forEach(item=>{
            str.push(item)
          })
          a.value = str
        }
        arr.push(item)
      }else {
        if(a.list===undefined){
          a.list = [a.value.split(',')]
          a.list.push(item.value.split(','))
        }else{
          a.list.push(item.value.split(','))
        }
      }
    })
    return arr
  }

  //获取指定分页低报
  const getPostTelexPatTrainGetPage = num => {
    // setTimeout(() => {
    //   computedCharts()
    // }, 1000)
    let number = page.value.current + num
    if (number > page.value.pageAll || number < 1) return
    getDatagramZuXunPageNumber({
      pageNumber: number,
      userId: selfId,
      trainId: scoreData.value.trainId
    }).then(res => {
      if (num == 1) {
        scoreData.value.nextContent = res.data.messageVO.filter(item=>item.sort>-1)
        testData.value.push(res.data.messageVO[0])
      } else {
        scoreData.value.preContent = res.data.messageVO.filter(item=>item.sort>-1)
      }
    })
  }
  const showChart = ref('total')

  /**
   * 点击查看当前报文拍发用时明细
   * @param key
   * @param i
   */
  const seeCurrKeysHairTrend = (key, i) => {
    let idTxt = 'ht_' + page.value.current + '_' + (i + 1) + '_' + key.key
    nextTick(() => {
      let ht = ref(document.getElementById(idTxt))
      patHairTrendBoxRef.value.scrollTo({ left: ht.value.offsetLeft - 80, behavior: 'smooth' })
    })
  }
  return {
    scoreData,
    loading,
    page,
    testData,
    pageTurn,
    showChart,
    situaData,
    seeCurrKeysHairTrend
  }
}

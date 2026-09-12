import { ref, onMounted, onBeforeMount, onBeforeUnmount, provide, nextTick } from 'vue'
import moment from 'moment'
import { findTheoryKnowledgeExamById, teacherStartTheoryKnowledgeExam, finishSelfTesting, studentSaveExamRealtimeContont, findExamUser } from '../../../../../../../common/api/TestApi'
import { PubSub } from '../../../../../../../common/utils/PubSub.js'
import { Ws } from '../../../../../../../common/ws/Ws'
import { deepClone } from '../../../../../../../common/utils/Utils.js'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
export default function startTest(countDown) {
  const route = useRoute()
  const router = useRouter()
  const questions = ref(null)
  const activeUser = ref(null)
  const testTime = ref(null)
  const timeout = ref(null)
  const timer = ref(30)
  const times = ref(false)
  const handeCommit = ref(false)
  const clearAnswer = ref(false)
  const questionNull = ref(null)
  const isShow = ref(true)
  const userList = ref([])
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo')))
  const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
  const active = ref(null)
  const fileUrl = ref(window.fileUrl)
  const bankList = ref([
    {
      name: '单选题',
      key: 'singleChoice'
    },
    {
      name: '多选题',
      key: 'multipleChoice'
    },
    {
      name: '判断题',
      key: 'judge'
    },
    {
      name: '填空题',
      key: 'completion'
    },
    {
      name: '简答题',
      key: 'shortAnswer'
    }
  ])
  const examID = ref(null)
  const questionsOld = ref(null)
  onBeforeUnmount(() => {
    if (userRole.id == 2) {
      clearAnswer.value = true
    }
  })
  onMounted(() => {
    examID.value = route.query.id
    initPaper()
  })
  onBeforeUnmount(() => {})
  //初始化考卷信息
  const initPaper = () => {
    findTheoryKnowledgeExamById({ id: route.query.id }).then(res => {
      if (res.data.exam.state == 1) {
        teacherStartTheoryKnowledgeExam({ examId: route.query.id, type: '2' })
      }
      testTime.value = res.data.exam.duration
      let diffTime = moment().diff(moment(res.data.exam['start_time']), 'second')
      res.data.paper.completion = JSON.parse(res.data.paper.completionList)
      res.data.paper.judge = JSON.parse(res.data.paper.judgeList)
      res.data.paper.multipleChoice = JSON.parse(res.data.paper.multipleChoiceList)
      res.data.paper.singleChoice = JSON.parse(res.data.paper.singleChoiceList)
      res.data.paper.shortAnswer = JSON.parse(res.data.paper.shortAnswer)
      userList.value = res.data.user
      let itemQ = res.data.paper
      if (itemQ['completion'] && itemQ['completion'].length > 0) {
        itemQ['completion'].forEach(item => {
          item.answer = JSON.parse(item.answer)
          item.options = JSON.parse(item.options)
        })
      }
      if (itemQ['judge'] && itemQ['judge'].length > 0) {
        itemQ['judge'].forEach(item => {
          item.answer = JSON.parse(item.answer)
          item.options = JSON.parse(item.options)
        })
      }
      if (itemQ['multipleChoice'] && itemQ['multipleChoice'].length > 0) {
        itemQ['multipleChoice'].forEach(item => {
          item.answer = JSON.parse(item.answer)
          item.options = JSON.parse(item.options)
        })
      }
      if (itemQ['singleChoice'] && itemQ['singleChoice'].length > 0) {
        itemQ['singleChoice'].forEach(item => {
          item.answer = JSON.parse(item.answer)
          item.options = JSON.parse(item.options)
        })
      }
      if (itemQ['shortAnswer'] && itemQ['shortAnswer'].length > 0) {
        itemQ['shortAnswer'].forEach(item => {
          item.answer = item.answer
        })
      }
      questions.value = itemQ
      questions.noFirst = false
      questionsOld.value = deepClone(questions.value)
      let time = Number(res.data.exam.duration) * 60
      if (res.data.exam.state == 1) {
        countDown.value.autoSetTimeNew(time)
      } else {
        if (diffTime > time) {
          countDown.value.autoSetTimeNew(0)
        } else {
          countDown.value.autoSetTimeNew(time - diffTime)
        }
      }
    })
  }
  const commitTest = () => {
    handeCommit.value = true
    //学生交卷：只送 examId + content，总分与逐题 teacherScore 一律由服务端按试卷快照重算
    const con = organizeAnwser()
    const data = {
      examId: route.query.id,
      content: JSON.stringify(con)
    }
    finishSelfTesting(data).then(res => {
      if (res.code == 200) {
        router.push({
          path: route.matched[4].path + '/studentGradeDetails',
          query: {
            id: res.data.id
          }
        })
      }
    })

    // router.go(-1)
  }
  const organizeAnwser = () => {
    let type = ['completion', 'judge', 'multipleChoice', 'singleChoice', 'shortAnswer']
    let content = {
      completion: [],
      judge: [],
      multipleChoice: [],
      singleChoice: [],
      shortAnswer: []
    }
    for (let v of type) {
      questions.value[v].forEach(item => {
        content[v].push({
          id: item.id,
          answer: item.answer,
          isAnswer: item.isAnswer
        })
      })
    }
    return content
  }
  const studentAnswer = con => {
    let type = ['completion', 'judge', 'multipleChoice', 'singleChoice', 'shortAnswer']
    let content = {
      completion: [],
      judge: [],
      multipleChoice: [],
      singleChoice: [],
      shortAnswer: []
    }
    for (let v of type) {
      questions.value[v].forEach(item => {
        con[v].forEach(c => {
          if (c.id == item.id) {
            item.answer = c.answer
            item.isAnswer = c.isAnswer
          }
        })
      })
    }
  }
  // 清楚答案
  const clearAnswers = () => {
    let type = ['completion', 'judge', 'multipleChoice', 'singleChoice', 'shortAnswer']
    for (let v of type) {
      questions.value[v].forEach(item => {
        if (item.type == 1 || item.type == 3 || item.type == 5) {
          item.answer = ' '
        } else {
          for (let i in item.answer) {
            item.answer[i] = ''
          }
        }
      })
    }
  }
  //获取实时学员信息
  const getStudentInfo = (e, index) => {
    if (active.value == index) {
      active.value = null
      questions.value = deepClone(questionsOld.value)
      return
    } else {
      active.value = index
    }
    activeUser.value = e['user_id']
    findExamUser({
      examId: route.query.id,
      userId: e['user_id']
    }).then(res => {
      clearAnswers()
      const content = JSON.parse(res.data.content)
      if (content && content !== 'null') {
        studentAnswer(content)
      }
    })
  }
  //学员提交实时答案
  const realTimeAnwser = () => {
    const con = organizeAnwser()
    const data = {
      examId: route.query.id,
      content: JSON.stringify(con)
    }
    // studentSaveExamRealtimeContont(data)
  }
  provide('realTimeAnwser', { realTimeAnwser })
  return {
    userInfo,
    fileUrl,
    questions,
    bankList,
    userList,
    userRole,
    testTime,
    active,
    isShow,
    clearAnswer,
    commitTest,
    getStudentInfo
  }
}

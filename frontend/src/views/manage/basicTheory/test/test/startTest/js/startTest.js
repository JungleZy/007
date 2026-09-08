import { ref, onMounted, onBeforeMount, onBeforeUnmount, provide, nextTick } from 'vue'
import moment from 'moment'
import { findTheoryKnowledgeExamById, teacherStartTheoryKnowledgeExam, studentChangeExamState, studentSaveExamRealtimeContont, findExamUser } from '../../../../../../../common/api/TestApi'
import { PubSub } from '../../../../../../../common/utils/PubSub.js'
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
  const paperLoding = ref(false)
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
  PubSub.subscribe('18002', data => {
    if (data.map) {
      userList.value.forEach(item => {
        if (data.map.student) {
          if (item.id === data.map.student.id) {
            item.state = data.map.student.state
          }
        }
      })
      if (data.map && data.map.student && data.map.student.userId == activeUser.value) {
        clearAnswers()
        if (data.map.student.content) {
          const content = JSON.parse(data.map.student.content)
          nextTick(() => {
            if (content && content !== 'null') {
              studentAnswer(content)
            }
          })
        }
      }
      if (data.map && userRole.value.id == 2 && data.map.exam && data.map.exam.state == 3) {
        times.value = false
        interval(0, data.map.exam.id)
        timeout.value = setTimeout(() => {
          clearInterval(interval)
          if (data.map.exam.id === route.query.id) {
            commitTest()
          }
        }, 31000)
      }
    }
  })
  onMounted(() => {
    examID.value = route.query.id
    paperLoding.value = true
    initPaper()
  })
  onBeforeUnmount(() => {
    if (userRole.value.id == 2) {
      clearAnswer.value = true
    }
    if (userRole.value.id == 2 && !handeCommit.value) {
      //学生交卷
      const con = organizeAnwser()
      const data = {
        examId: examID.value,
        type: '1',
        userId: userInfo.value.id,
        content: JSON.stringify(con)
      }
      studentChangeExamState(data)
    }
  })
  const interval = (time, id) => {
    if (id === route.query.id) {
      let key = 'un'
      if (time < 31) {
        setTimeout(() => {
          if (times.value) return
          if (route.name === 'StartTest') {
            message.error({ content: '考试即将结束,' + (30 - time) + '秒后自动提交答案', key, duration: timer.value })
          }
          time++
          interval(time, id)
        }, 1000)
      } else {
        if (route.name === 'StartTest') {
          message.success({ content: '考试已结束,答案已提交', key, duration: timer.value })
        }
      }
    }
  }
  //初始化考卷信息
  const initPaper = () => {
    findTheoryKnowledgeExamById({ id: route.query.id }).then(res => {
      if (res.data.exam.state == 1) {
        teacherStartTheoryKnowledgeExam({ examId: route.query.id, type: '2' })
      }
      testTime.value = res.data.exam.duration
      let diffTime = moment().diff(moment(res.data.exam['start_time']), 'second')
      if (diffTime < 0) {
        diffTime = 0
      }
      res.data.paper.completion = JSON.parse(res.data.paper.completionList)
      res.data.paper.judge = JSON.parse(res.data.paper.judgeList)
      res.data.paper.multipleChoice = JSON.parse(res.data.paper.multipleChoiceList)
      res.data.paper.singleChoice = JSON.parse(res.data.paper.singleChoiceList)
      res.data.paper.shortAnswer = JSON.parse(res.data.paper.shortAnswer)
      userList.value = res.data.user
      let itemQ = res.data.paper
      if (itemQ['completion'].length > 0) {
        itemQ['completion'].forEach(item => {
          item.answer = JSON.parse(item.answer)
          item.options = JSON.parse(item.options)
        })
      }
      if (itemQ['judge'].length > 0) {
        itemQ['judge'].forEach(item => {
          item.answer = JSON.parse(item.answer)
          item.options = JSON.parse(item.options)
        })
      }
      if (itemQ['multipleChoice'].length > 0) {
        itemQ['multipleChoice'].forEach(item => {
          item.answer = JSON.parse(item.answer)
          item.options = JSON.parse(item.options)
        })
      }
      if (itemQ['singleChoice'].length > 0) {
        itemQ['singleChoice'].forEach(item => {
          item.answer = JSON.parse(item.answer)
          item.options = JSON.parse(item.options)
        })
      }
      questions.value = itemQ
      questions.noFirst = false
      questionsOld.value = deepClone(questions.value)
      let time = Number(res.data.exam.duration) * 60
      if (res.data.exam.state == 1) {
        countDown.value.autoSetTimeNew(time, userRole.value.id)
      } else {
        if (diffTime > time) {
          countDown.value.autoSetTimeNew(0, userRole.value.id)
        } else {
          countDown.value.autoSetTimeNew(time - diffTime, userRole.value.id)
        }
      }
      if (userRole.value.id == 2) {
        questions.value['completion'].sort(() => Math.random() - 0.5)
        questions.value['judge'].sort(() => Math.random() - 0.5)
        questions.value['multipleChoice'].sort(() => Math.random() - 0.5)
        questions.value['singleChoice'].sort(() => Math.random() - 0.5)
        questions.value['shortAnswer'].sort(() => Math.random() - 0.5)
        const data = {
          examId: route.query.id,
          type: '2',
          userId: userInfo.value.id,
          content: ''
        }
        for (let v of res.data.user) {
          if (v['user_id'] == userInfo.value.id) {
            if (v.content == null) {
              data.content = v.content
              studentChangeExamState(data)
            } else {
              // questions.value = JSON.parse(v.content)
              studentAnswer(JSON.parse(v.content))
              questions.value.noFirst = true
              studentChangeExamState(data)
            }
          }
        }
      }
      paperLoding.value = false
    })
  }
  const commitTest = () => {
    if (userRole.value.id == 2) {
      handeCommit.value = true
      //学生交卷
      const con = organizeAnwser()
      const data = {
        examId: route.query.id,
        type: '3',
        userId: userInfo.value.id,
        content: JSON.stringify(con)
      }
      studentChangeExamState(data).then(res => {
        if (res.code == 200) {
          router.go(-1)
        }
      })
      clearTimeout(timeout.value)
      times.value = true
      timer.value = 3
      interval(31)
    } else {
      //老师结束考试
      teacherStartTheoryKnowledgeExam({ examId: route.query.id, type: '3' }).then(res => {
        if (res.code == 200) {
          message.success('提交成功')
          router.go(-1)
        }
      })
    }
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
      userId: userInfo.value.id,
      content: JSON.stringify(con)
    }
    studentSaveExamRealtimeContont(data)
  }
  provide('realTimeAnwser', { realTimeAnwser })
  provide('changeShortAnswerScore', '')
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
    paperLoding,
    commitTest,
    getStudentInfo
  }
}
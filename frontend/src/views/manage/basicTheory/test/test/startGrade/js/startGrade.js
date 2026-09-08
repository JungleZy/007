import { message, Modal } from 'ant-design-vue'
import { ref, nextTick, provide, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import moment from 'moment'
import 'moment/dist/locale/zh-cn.js'
import { deepClone } from '../../../../../../../common/utils/Utils.js'
import { findTheoryKnowledgeExamById, teacherUploadScore, teacherStartTheoryKnowledgeExam, getExamineAnalyse } from '../../../../../../../common/api/TestApi'
export default function startGrade() {
  const questions = ref(null)
  const questionNull = ref(null)
  const route = useRoute()
  const router = useRouter()
  const students = ref()
  const isShow = ref(true)
  const testExam = ref(null)
  const analyseData = ref(null)
  const activeUser = ref()
  const fileUrl = ref(window.fileUrl)
  const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo')))
  const examState = ref(null)
  const endGrade = () => {
    if (userRole.value.id == 2) {
      router.go(-1)
      return false
    }
    if (!students.value.every(item => item.isCommitScore)) {
      Modal.confirm({
        title: () => '还有学员答案没有提交?',
        content: () => '是否提交所有学员答案!',
        // icon: () => createVNode(ExclamationCircleOutlined),
        okType: 'danger',
        okText: () => '确定',
        cancelText: () => '取消',
        onOk() {
          const data = {
            examId: route.query.id,
            list: []
          }
          students.value.forEach(item => {
            if (!item.isCommitScore) {
              activeUser.value = item
              countScore()
              item.isCommitScore = true
              // data.list[item['user_id']] = item.score
            }
          })
          data.list = students.value
          teacherUploadScore(data).then(res => {
            if (res.code == 200) {
              teacherStartTheoryKnowledgeExam({ examId: route.query.id, type: '4' }).then(r => {
                if (r.code == 200) {
                  router.go(-1)
                }
              })
            }
          })
        }
      })
    } else {
      teacherStartTheoryKnowledgeExam({ examId: route.query.id, type: '4' }).then(r => {
        if (r.code == 200) {
          router.go(-1)
        }
      })
    }
  }
  //初始化考卷信息
  const initPaper = () => {
    findTheoryKnowledgeExamById({ id: route.query.id }).then(res => {
      examState.value = res.data.exam.state
      testExam.value = res.data.exam
      testExam.value.userLen = res.data.user.length
      res.data.paper.completion = JSON.parse(res.data.paper.completionList)
      res.data.paper.judge = JSON.parse(res.data.paper.judgeList)
      res.data.paper.multipleChoice = JSON.parse(res.data.paper.multipleChoiceList)
      res.data.paper.singleChoice = JSON.parse(res.data.paper.singleChoiceList)
      res.data.paper.shortAnswer = JSON.parse(res.data.paper.shortAnswer)
      let itemQ = res.data.paper
      if (itemQ['completion'] && itemQ['completion'].length > 0) {
        itemQ['completion'].forEach(item => {
          item.answer = JSON.parse(item.answer)
          item.options = JSON.parse(item.options)
        })
      }
      if (itemQ['judge'] && itemQ['judge'].length > 0) {
        itemQ['judge'].forEach(item => {
          item.answer = `${item.answer}`
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
          item.answer = `${item.answer}`
          item.options = JSON.parse(item.options)
        })
      }
      if (itemQ['shortAnswer'] && itemQ['shortAnswer'].length > 0) {
        itemQ['shortAnswer'].forEach(item => {
          item.answer = item.answer
          item.teacherScore = 0
        })
      }
      // questions.value = itemQ
      questionNull.value = itemQ
      questions.value = deepClone(questionNull.value)

      res.data.user.forEach(item => {
        item.content = JSON.parse(item.content)
        item.changeState = false
      })
      students.value = res.data.user
      if (userRole.value.id == 2) {
        res.data.user.forEach(item => {
          if (item['user_id'] == userInfo.value.id) {
            selelctStu(item)
          }
        })
      } else {
        selelctStu(res.data.user[0])
      }
    })

    getExamineAnalyse({ examId: route.query.id }).then(res => {
      if (res.code === 200) {
        analyseData.value = res.data
      }
    })
  }
  const selelctStu = item => {
    activeUser.value = item
    //计算分数
    countScore()
    isShow.value = false
    nextTick(() => {
      isShow.value = true
    })
    if (!item.content) {
      let type = ['completion', 'judge', 'multipleChoice', 'singleChoice', 'shortAnswer']
      for (let v of type) {
        questions.value[v].forEach(item => {
          if (item.type == 2) {
            item.correctAnswer = []
          } else {
            item.correctAnswer = ' '
          }
        })
      }
    } else {
      studentAnswer(item.content)
    }
    if (item['start_time'] == null) {
      testExam.value.testime = 0
    } else {
      testExam.value.testime = dayjs(item['end_time']).diff(dayjs(item['start_time']), 'minute')
    }
  }
  const studentAnswer = con => {
    let type = ['completion', 'judge', 'multipleChoice', 'singleChoice', 'shortAnswer']
    for (let v of type) {
      if (questions.value[v]) {
        questions.value[v].forEach(item => {
          if (con.completion || con.judge || con.multipleChoice || con.shortAnswer || con.singleChoice) {
            con[v].forEach(c => {
              if (c.id == item.id) {
                item.correctAnswer = c.answer
                if (c.teacherScore) {
                  item.teacherScore = c.teacherScore
                }
              }
            })
          } else {
            if (item.type == 2) {
              item.correctAnswer = []
            } else {
              item.correctAnswer = ' '
            }
          }
        })
      }
    }
  }
  const commitScore = () => {
    activeUser.value.isCommitScore = true
    const data = {
      examId: activeUser.value['exam_id'],
      list: [activeUser.value]
    }
    // data.list[activeUser.value['user_id']] = activeUser.value.score
    teacherUploadScore(data).then(res => {
      if (res.code === 200) {
        message.success('提交成功')
      }
    })
  }
  const goback = () => {
    router.go(-1)
  }
  //子组件修改简答题评分
  const changeShortAnswerScore = item => {
    activeUser.value.content.shortAnswer.forEach(v => {
      if (v.id === item.id) {
        v.teacherScore = item.teacherScore
      }
    })
    questions.value.shortAnswer.forEach(v => {
      if (v.id === item.id) {
        v.teacherScore = item.teacherScore
      }
    })
    countScore()
    // selelctStu(activeUser.value)
  }
  //
  const countScore = () => {
    //计算分数
    let content = activeUser.value.content
    if (activeUser.value.content) {
      content.score = 0
      for (let i in content.completion) {
        questionNull.value['completion'].forEach(item => {
          if (item.id == content.completion[i].id) {
            if (content.completion[i].answer.toString() == item.answer.toString()) {
              content.score = Number(item.score) + content.score
            }
          }
        })
      }
      for (let i in content.judge) {
        questionNull.value['judge'].forEach(item => {
          if (item.id == content.judge[i].id) {
            if (content.judge[i].answer == item.answer) {
              content.score = Number(item.score) + content.score
            }
          }
        })
      }
      for (let i in content.multipleChoice) {
        questionNull.value['multipleChoice'].forEach(item => {
          if (item.id == content.multipleChoice[i].id) {
            item.answer = item.answer.sort((a, b) => a - b)
            if (content.multipleChoice[i].answer.toString() == item.answer.toString()) {
              content.score = Number(item.score) + content.score
            }
          }
        })
      }
      for (let i in content.singleChoice) {
        questionNull.value['singleChoice'].forEach(item => {
          if (item.id == content.singleChoice[i].id) {
            if (content.singleChoice[i].answer == item.answer) {
              content.score = Number(item.score) + content.score
            }
          }
        })
      }
      for (let i in content.shortAnswer) {
        questionNull.value['shortAnswer'].forEach(item => {
          if (item.id == content.shortAnswer[i].id) {
            if (content.shortAnswer[i].answer.trim() == item.answer.trim()) {
              content.shortAnswer[i].teacherScore = Number(item.score)
              content.score = Number(item.score) + content.score
            } else {
              content.shortAnswer[i].teacherScore = content.shortAnswer[i].teacherScore ? content.shortAnswer[i].teacherScore : 0
              content.score = content.shortAnswer[i].teacherScore + content.score
            }
          }
        })
      }
      activeUser.value.score = content.score
      delete content.score
    } else {
      activeUser.value.score = 0
    }
  }
  provide('realTimeAnwser', '')
  provide('changeShortAnswerScore', changeShortAnswerScore)
  return {
    questions,
    fileUrl,
    students,
    isShow,
    activeUser,
    userRole,
    examState,
    testExam,
    commitScore,
    initPaper,
    analyseData,
    endGrade,
    goback,
    selelctStu
  }
}

import { ref, onMounted, onBeforeUnmount, provide, nextTick, computed } from 'vue'
import moment from 'moment'
import { findTheoryKnowledgeExamById, teacherStartTheoryKnowledgeExam, studentChangeExamState, studentSaveExamRealtimeContont, findExamUser } from '../../../../../../../common/api/TestApi'
import { PubSub } from '../../../../../../../common/utils/PubSub.js'
import { deepClone } from '../../../../../../../common/utils/Utils.js'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'

export default function startTest(countDown) {
  const route = useRoute()
  const router = useRouter()
  const examId = route.query.id
  const questions = ref(null)
  const testTime = ref(null)
  const paperLoding = ref(true)
  const isShow = ref(true)
  const userList = ref([])
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo')))
  const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
  const active = ref(null)
  const activeUser = ref(null)
  const fileUrl = ref(window.fileUrl)
  const clearAnswer = ref(false)
  const terminal = ref(false)
  const submitting = ref(false)
  const entered = ref(false)
  const terminalMessage = ref('')
  const paperVersion = ref(0)
  const answerLocked = computed(() => terminal.value || submitting.value || !entered.value)
  const bankList = ref([
    { name: '单选题', key: 'singleChoice' },
    { name: '多选题', key: 'multipleChoice' },
    { name: '判断题', key: 'judge' },
    { name: '填空题', key: 'completion' },
    { name: '简答题', key: 'shortAnswer' }
  ])
  let originalPaper
  let disposed = false
  let confirmedRead

  const restoreAnswer = content => {
    const answers = content ? JSON.parse(content) : {}
    for (const { key } of bankList.value) {
      for (const question of questions.value[key]) {
        const saved = answers?.[key]?.find(answer => answer.id === question.id)
        question.answer = saved ? saved.answer : (Array.isArray(question.answer) ? question.answer.map(() => '') : '')
        question.isAnswer = saved?.isAnswer ?? false
      }
    }
    questions.value.noFirst = true
    paperVersion.value++
  }

  const showConfirmedAnswer = async () => {
    terminal.value = true
    entered.value = false
    countDown.value?.clearInterval()
    terminalMessage.value = '考试或答卷已结束，正在读取服务器最后确认的答卷。本地未确认内容不能再提交。'
    if (confirmedRead) return confirmedRead
    confirmedRead = (async () => {
      try {
        const response = await findExamUser({ examId, userId: userInfo.value.id })
        if (disposed) return
        if (response.code !== 200 || !response.data) throw new Error('未取得服务器答卷')
        if (questions.value) restoreAnswer(response.data.content)
        terminalMessage.value = '考试或答卷已结束；当前显示服务器最后确认的答卷，本地未确认内容不能再提交。'
      } catch (error) {
        if (!disposed) {
          isShow.value = false
          terminalMessage.value = '考试已结束，服务器答卷读取失败。已停止作答，请重新进入查看；本地未确认内容不能再提交。'
          message.error(terminalMessage.value)
        }
      } finally {
        confirmedRead = null
      }
    })()
    return confirmedRead
  }

  const subscription = PubSub.subscribe('18002', data => {
    const exam = data.map?.exam
    const student = data.map?.student
    if (exam?.id === examId && Number(exam.state) >= 3) {
      if (userRole.value.id == 2) void showConfirmedAnswer()
      else {
        terminal.value = true
        countDown.value?.clearInterval()
      }
    }
    if (student?.examId !== examId || !questions.value) return
    const member = userList.value.find(item => item.id === student.id)
    if (member) member.state = student.state
    if (student.userId === activeUser.value) restoreAnswer(student.content)
  })

  const initPaper = async () => {
    try {
      let response = await findTheoryKnowledgeExamById({ id: examId })
      if (response.code !== 200 || disposed) return
      if (Number(response.data.exam.state) === 1) {
        const started = await teacherStartTheoryKnowledgeExam({ examId, type: '2' })
        if (disposed || (started.code !== 200 && started.code !== 208)) return
        response = await findTheoryKnowledgeExamById({ id: examId })
        if (response.code !== 200 || disposed) return
      }
      const { exam, paper, user } = response.data
      testTime.value = exam.duration
      userList.value = user
      for (const { key } of bankList.value) {
        paper[key] = JSON.parse(paper[key === 'shortAnswer' ? key : key + 'List'])
        if (key !== 'shortAnswer') {
          paper[key].forEach(question => {
            question.answer = JSON.parse(question.answer)
            question.options = JSON.parse(question.options)
          })
        }
      }
      originalPaper = deepClone(paper)
      questions.value = paper
      if (userRole.value.id == 2) {
        const member = user.find(item => item.user_id === userInfo.value.id)
        restoreAnswer(member?.content)
        if (terminal.value || Number(exam.state) >= 3 || Number(member?.state) >= 3) {
          await showConfirmedAnswer()
          return
        }
        const entry = await studentChangeExamState({ examId, type: '2' })
        if (disposed) return
        if (entry.code === 208) {
          await showConfirmedAnswer()
          return
        }
        if (entry.code !== 200 || terminal.value) return
        restoreAnswer(entry.data.student.content)
        entered.value = true
      } else if (Number(exam.state) >= 3) {
        terminal.value = true
        return
      }
      await nextTick()
      if (disposed || terminal.value) return
      const elapsed = Math.max(0, moment().diff(moment(exam.start_time), 'second'))
      countDown.value?.autoSetTimeNew(Math.max(0, Number(exam.duration) * 60 - elapsed), userRole.value.id)
    } catch (error) {
      if (!disposed) message.error('试卷加载失败，未开始作答，请重新进入')
    } finally {
      paperLoding.value = false
    }
  }

  const organizeAnwser = () => Object.fromEntries(bankList.value.map(({ key }) => [key,
    questions.value[key].map(({ id, answer, isAnswer }) => ({ id, answer, isAnswer }))
  ]))

  const commitTest = async () => {
    if (terminal.value) {
      router.go(-1)
      return
    }
    if (submitting.value || !questions.value || (userRole.value.id == 2 && !entered.value)) return
    submitting.value = true
    try {
      const response = userRole.value.id == 2
        ? await studentChangeExamState({ examId, type: '3', content: JSON.stringify(organizeAnwser()) })
        : await teacherStartTheoryKnowledgeExam({ examId, type: '3' })
      if (response.code === 200) {
        terminal.value = true
        entered.value = false
        countDown.value?.clearInterval()
        message.success(userRole.value.id == 2 ? '答卷已由服务器确认提交' : '考试已结束')
        router.go(-1)
      } else if (response.code === 208 && userRole.value.id == 2) {
        await showConfirmedAnswer()
      }
    } catch (error) {
      message.error('提交未获确认，请检查网络；当前答案仍保留')
    } finally {
      submitting.value = false
    }
  }

  const realTimeAnwser = async () => {
    if (disposed || answerLocked.value || userRole.value.id != 2) return
    try {
      const response = await studentSaveExamRealtimeContont({ examId, content: JSON.stringify(organizeAnwser()) })
      if (response.code === 208 && !disposed) await showConfirmedAnswer()
    } catch (error) {
      if (!disposed && !terminal.value) message.error('实时答案未获服务器确认，请检查网络')
    }
  }

  const getStudentInfo = async (student, index) => {
    if (active.value === index) {
      active.value = null
      activeUser.value = null
      questions.value = deepClone(originalPaper)
      paperVersion.value++
      return
    }
    active.value = index
    activeUser.value = student.user_id
    try {
      const response = await findExamUser({ examId, userId: student.user_id })
      if (response.code === 200 && activeUser.value === student.user_id) restoreAnswer(response.data.content)
    } catch (error) {
      message.error('读取学员答卷失败')
    }
  }

  onMounted(initPaper)
  onBeforeUnmount(() => {
    disposed = true
    PubSub.unsubscribe(subscription)
    countDown.value?.clearInterval()
    if (userRole.value.id == 2 && entered.value && !terminal.value && !submitting.value && questions.value) {
      void studentChangeExamState({ examId, type: '1', content: JSON.stringify(organizeAnwser()) })
        .catch(() => message.error('离场答案未获服务器确认'))
    }
  })
  provide('realTimeAnwser', { realTimeAnwser })
  provide('changeShortAnswerScore', '')
  return { userInfo, fileUrl, questions, bankList, userList, userRole, testTime, active, isShow,
    clearAnswer, paperLoding, commitTest, getStudentInfo, answerLocked, terminal, terminalMessage, paperVersion }
}

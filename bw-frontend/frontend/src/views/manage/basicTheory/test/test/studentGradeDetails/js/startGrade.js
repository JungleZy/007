import { message } from 'ant-design-vue'
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { findTheoryKnowledgeExamById } from '../../../../../../../common/api/TestApi'

export default function startGrade() {
  const questions = ref(null)
  const activeUser = ref(null)
  const testExam = ref(null)
  const fileUrl = ref(window.fileUrl)
  const route = useRoute()
  const router = useRouter()
  const userInfo = JSON.parse(localStorage.getItem('userInfo'))

  const initPaper = async () => {
    try {
      const response = await findTheoryKnowledgeExamById({ id: route.query.id })
      if (response.code !== 200) return
      const { exam, paper, user } = response.data
      const member = user.find(item => item.user_id === userInfo.id)
      if (Number(member?.state) !== 4 || (Number(member.is_self_testing) !== 0 && Number(exam.state) !== 4)) {
        message.warning('考试和本人阅卷完成后才能复盘')
        return
      }
      const content = member.content ? JSON.parse(member.content) : {}
      for (const key of ['singleChoice', 'multipleChoice', 'judge', 'completion', 'shortAnswer']) {
        paper[key] = JSON.parse(paper[key === 'shortAnswer' ? key : key + 'List'])
        for (const question of paper[key]) {
          const standard = key === 'shortAnswer' ? question.answer : JSON.parse(question.answer)
          const submitted = content[key]?.find(answer => answer.id === question.id)
          question.correctAnswer = key === 'singleChoice' || key === 'judge' ? String(standard) : standard
          question.answer = submitted ? submitted.answer : key === 'multipleChoice' ? []
            : key === 'completion' ? standard.map(() => '') : ''
          if (key !== 'shortAnswer') question.options = JSON.parse(question.options)
          question.teacherScore = submitted?.teacherScore ?? 0
        }
      }
      activeUser.value = member
      testExam.value = exam
      questions.value = paper
    } catch (error) {
      message.error('复盘加载失败，请重新进入')
    }
  }
  const goback = () => router.push(route.matched[4].path + '/theoryTestGrade')
  return { initPaper, questions, activeUser, testExam, fileUrl, goback }
}

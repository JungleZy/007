import { message, Modal } from 'ant-design-vue'
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import moment from 'moment'
import 'moment/dist/locale/zh-cn.js'
import { findAllTheoryKnowledgeExam, findAllTheoryKnowledgeExamUser ,deleteTheoryKnowledgeExam} from '../../../../../../../common/api/TestApi'
import { findAllTheoryKnowledgeQuestionLevel } from '../../../../../../../common/api/TheoryQuestionBankApi'
import { listSort } from '../../../../../../../components/test/nodeTree/listSort'
export default function knowledgeTabel() {
  const listData = ref([])
  const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
  const testPaper = () => {
    //学员
    if (userRole.value.id == 2) {
      findAllTheoryKnowledgeExamUser({ state: false }).then(res => {
        if (res.code === 200) {
          res.data.exam = res.data.exam.sort((a, b) => b.create_time - a.create_time)
          listData.value = res.data
        }
      })
    } else {
      //教员
      findAllTheoryKnowledgeExam({ state: false }).then(res => {
        if (res.code === 200) {
          listData.value = listSort(res.data)
        }
      })
    }
  }
  const deleteTest = (d)=>{
    deleteTheoryKnowledgeExam({examId:d.id}).then(res => {
      if(res.code === 200){
        testPaper()
        message.success('删除成功！')
      }else {
        message.error(res.message)
      }
    })
  }
  return {
    listData,
    userRole,
    testPaper,
    deleteTest
  }
}

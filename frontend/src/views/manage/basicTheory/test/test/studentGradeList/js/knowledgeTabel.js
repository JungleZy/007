import { message, Modal } from 'ant-design-vue'
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import moment from 'moment'
import 'moment/dist/locale/zh-cn.js'
import { findAllTheoryKnowledgeExam, listPageSelfTesting } from '../../../../../../../common/api/TestApi'
import { findAllTheoryKnowledgeQuestionLevel } from '../../../../../../../common/api/TheoryQuestionBankApi'
import { listSort } from '../../../../../../../components/test/nodeTree/listSort'
export default function knowledgeTabel() {
  const listData = ref([])
  const userRole = ref(JSON.parse(localStorage.getItem('userRole')))
  const testPaper = () => {
    listPageSelfTesting({}).then(res => {
      if (res.code === 200) {
        listData.value = listSort(res.data)
      }
    })
  }
  return {
    listData,
    userRole,
    testPaper
  }
}

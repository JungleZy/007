import {analyzeTotal} from '../../../../../../common/api/StudyManage_classHours.js'
import {ref} from "vue";

let totalData = ref({
  userEntity: {
    userName: '',
    // 入伍时间
    bday: '',
    email: '',
    phone: '',
  },
  // 参加考试
  examNum: '',
  // 及格次数
  passNum: '',
  // 已学课件
  swfNum: '',
  // 学习时长
  studyTime: '',
  // 易错题
  errorTopic: [],
  // 学习总时长
  theoryTime: '',
  // 已得总学分
  totalCredit: '',
  // 最高分
  theoryTestMaxCredit: '',
  // 最低分
  theoryTestMinCredit: '',
  // 平均分
  theoryTestAvgCredit: ''
})
export default function totalApi() {
  let getTotal = () => {
    analyzeTotal().then(res => {
      if (res.data) {
        totalData.value = res.data
      }
    })
  }
  return {
    totalData,
    getTotal
  }
}
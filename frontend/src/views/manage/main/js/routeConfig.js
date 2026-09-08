import { ref } from 'vue'

export default function () {
  const routePaths = ref([
    {
      path: '/preview/dashboard',
      children: [
        '/preview/basicTheoretical',
        '/preview/basicSkill',
        '/preview/equipmentOperation',
        '/preview/networkUsing',
        '/preview/systemManage/structure',
        '/preview/systemManage/basic/menu',
        '/preview/systemManage/basic/role',
        '/preview/systemManage/basic/equipmentManage'
      ]
    },
    {
      path: '/preview/basicTheoretical',
      children: [
        '/preview/basicTheoretical/theoryStudy/basicTheory/basicTheoryManage',
        '/preview/basicTheoretical/theoryStudy/basicTheory/basicTheoryList',
        '/preview/basicTheoretical/theoryTest/questionBank',
        '/preview/basicTheoretical/theoryTest/paperBank',
        '/preview/basicTheoretical/theoryTest/theoryTest/theoryTestList',
        '/preview/basicTheoretical/theoryTest/theoryTest/theoryTestGrade',
        '/preview/basicTheoretical/studyManagement/classHoursManagement',
        '/preview/basicTheoretical/studyManagement/scoreStatistics',
        '/preview/basicTheoretical/studyManagement/comprehensiveAnalyze'
      ]
    },
    {
      path: '/preview/basicSkill',
      children: [
        '/preview/basicSkill/preJob/receive/receiveExplain',
        '/preview/basicSkill/preJob/telegram/focusExplain',
        '/preview/basicSkill/preJob/datagram/datagramExplain',
        '/preview/basicSkill/preJob/hanzi/hanziExplain',
        '/preview/basicSkill/preJob/receive/receiveTeaching',
        '/preview/basicSkill/preJob/telegram/demonstrationTeaching',
        '/preview/basicSkill/preJob/datagram/datagramTeaching',
        '/preview/basicSkill/preJob/hanzi/hanziTeaching',
        '/preview/basicSkill/preJob/receive/receivePractise',
        '/preview/basicSkill/preJob/telegram/handKeyPat',
        '/preview/basicSkill/preJob/telegram/patExam',
        '/preview/basicSkill/preJob/datagram/telexPat',
        '/preview/basicSkill/preJob/ditto/wording',
        '/preview/basicSkill/preJob/ditto/militaryTerm',
        '/preview/basicSkill/preJob/hanzi/pylist',
        '/preview/basicSkill/preJob/hanzi/wblist',
        '/preview/basicSkill/postJob/receive/receivePostPractise',
        '/preview/basicSkill/postJob/telegram/handKeyPostJob',
        '/preview/basicSkill/postJob/telegram/examPostList',
        '/preview/basicSkill/postJob/datagram/telexPost',
        '/preview/basicSkill/postJob/datagram/datagramPost',
        '/preview/basicSkill/postJob/ditto/wording',
        '/preview/basicSkill/postJob/ditto/militaryTerm',
        '/preview/basicSkill/postJob/hanzi/postPinYin',
        '/preview/basicSkill/postJob/hanzi/postEnglish',
        '/preview/basicSkill/postJob/hanzi/postWuBi',
        '/preview/basicSkill/postJob/hanzi/articleManage'
      ]
    }
  ])
  const lineDevicePaths = ref([
    '/preview/basicSkill/preJob/telegram/handKeyBasicTrain',
    '/preview/basicSkill/preJob/telegram/handKeyTrain',
    '/preview/basicSkill/preJob/telegram/examBasicExam',
    '/preview/basicSkill/preJob/telegram/examComplexTrain',
    '/preview/basicSkill/postJob/telegram/handKeyPostJobTrain',
    '/preview/basicSkill/postJob/telegram/examPostJobTrain'
  ])
  return {
    routePaths,
    lineDevicePaths
  }
}

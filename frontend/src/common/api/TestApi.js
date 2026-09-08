import axios from '../http/axios.js'
export const saveTheoryKnowledgeTest = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledgeTest/saveTheoryKnowledgeTest",
    data
  })
}
export const getTestBySwfId = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledgeTest/getTestBySwfId",
    data
  })
}
export const getByKnowledgeSwfIdAndEnable = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledgeTest/getByKnowledgeSwfIdAndEnable",
    data
  })
}
export const getTestContentByUserIdAndKnowledgeSwfId = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledgeTest/getTestContentByUserIdAndKnowledgeSwfId",
    data
  })
}
export const saveUserKnowledgeSwfTestContent = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledgeTest/saveUserKnowledgeSwfTestContent",
    data
  })
}
export const savetheoryKnowledgeExam = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledgeExam/savetheoryKnowledgeExam",
    data
  })
}
export const saveTheoryKnowledgeExamSelfTesting = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledgeExam/saveTheoryKnowledgeExamSelfTesting",
    data
  })
}
export const saveTheoryKnowledgeRecord = (data) => {
   return axios({
      method: "post",
      url: "/api/theoryKnowledge/saveTheoryKnowledgeRecord",
      data
   })
}
export const deleteThroyKnowledgeById = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledge/deleteThroyKnowledgeById ",
    data
  })
}
export const saveTestPaper = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledgeTestPaper/saveTestPaper",
    data
  })
}
export const findAllTestPaper = () => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledgeTestPaper/findAllTestPaper",
  })
}
export const findTestPaperByLevelIdAndName = (data) => {
  return axios({
    method: "post",
    data,
    url: "/api/theoryKnowledgeTestPaper/findTestPaperByLevelIdAndName",
  })
}
export const findAllTheoryKnowledgeExam = (data) => {
  return axios({
    method: "post",
    data,
    url: "/api/theoryKnowledgeExam/findAllTheoryKnowledgeExam",
  })
}
export const studentChangeExamState = (data) => {
  return axios({
    method: "post",
    data,
    url: "/api/theoryKnowledgeExam/studentChangeExamState",
  })
}
export const finishSelfTesting = (data) => {
  return axios({
    method: "post",
    data,
    url: "/api/theoryKnowledgeExam/finishSelfTesting",
  })
}
export const studentSaveExamRealtimeContont = (data) => {
  return axios({
    method: "post",
    data,
    url: "/api/theoryKnowledgeExam/studentSaveExamRealtimeContont",
  })
}
export const findAllTheoryKnowledgeExamUser = (data) => {
  return axios({
    method: "post",
    data,
    url: "/api/theoryKnowledgeExamUser/findAllTheoryKnowledgeExamUser",
  })
}
export const listPageSelfTesting = (data) => {
  return axios({
    method: "post",
    data,
    url: "/api/theoryKnowledgeExam/listPageSelfTesting",
  })
}
export const findExamUser = (data) => {
  return axios({
    method: "post",
    data,
    url: "/api/theoryKnowledgeExamUser/findExamUser",
  })
}
export const findTheoryKnowledgeExamById = (data) => {
  return axios({
    method: "post",
    data,
    url: "/api/theoryKnowledgeExam/findTheoryKnowledgeExamById",
  })
}
export const teacherUploadScore = (data) => {
  return axios({
    method: "post",
    data,
    url: "/api/theoryKnowledgeExamUser/teacherUploadScore",
  })
}
export const teacherStartTheoryKnowledgeExam = (data) => {
  return axios({
    method: "post",
    data,
    url: "/api/theoryKnowledgeExam/teacherStartTheoryKnowledgeExam",
  })
}
export const findTestPaperById = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledgeTestPaper/findTestPaperById",
    data
  })
}
export const getExamineAnalyse = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledgeExam/examineAnalyse",
    data
  })
}

export const deleteTestPaper = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledgeTestPaper/deleteTestPaper",
    data
  })
}

export const deleteTheoryKnowledgeExam = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledgeExam/deleteTheoryKnowledgeExam",
    data
  })
}
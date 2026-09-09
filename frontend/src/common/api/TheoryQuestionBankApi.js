import axios from '../http/axios.js'

export const findAllTheoryKnowledgeQuestionLevel = () => {
   return axios({
      method: "post",
      url: "/api/theoryKnowledgeQuestion/findAllTheoryKnowledgeQuestionLevel"
   })
}
export const saveTheoryKnowledgeQuestionLevel = (data) => {
   return axios({
      method: "post",
      url: "/api/theoryKnowledgeQuestion/saveTheoryKnowledgeQuestionLevel",
      data
   })
}
export const deleteTheoryKnowledgeQuestionLevelById = (data) => {
   return axios({
      method: "post",
      url: "/api/theoryKnowledgeQuestion/deleteTheoryKnowledgeQuestionLevelById",
      data
   })
}
export const saveTheoryKnowledgeQuestion = (data) => {
   return axios({
      method: "post",
      url: "/api/theoryKnowledgeQuestion/saveTheoryKnowledgeQuestion",
      data
   })
}
export const saveBatch = (data) => {
   return axios({
      method: "post",
      url: "/api/theoryKnowledgeQuestion/saveBatch",
      data
   })
}
export const exportTemplate = () => {
   return axios({
      method: "post",
      url: "/api/theoryKnowledgeQuestion/exportTemplate"
   })
}
export const findAllQuestionByLevelId = (data) => {
   return axios({
      method: "post",
      url: "/api/theoryKnowledgeQuestion/findAllQuestionByLevelId",
      data
   })
}
export const deleteTheoryKnowledgeQuestion = (data) => {
   return axios({
      method: "post",
      url: "/api/theoryKnowledgeQuestion/deleteTheoryKnowledgeQuestion",
      data
   })
}
export const exportQuestionBank = (data) => {
  return axios({
    method: 'post',
    url: '/api/theoryKnowledgeQuestion/exportQuestionByLevelId',
    data
  })
}

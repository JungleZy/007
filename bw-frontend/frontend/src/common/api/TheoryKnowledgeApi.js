import axios from '../http/axios.js'

export const getBasicTheory = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledge/getBasicTheory",
    data
  })
}
export const getBasicTheoryOpen = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledge/getBasicTheoryOpen",
    data
  })
}
export const listPageClassify = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledge/listPageClassify",
    data
  })
}
export const removeClassify = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledge/removeClassify",
    data
  })
}
export const editClassify = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledge/addClassify",
    data
  })
}

export const saveTheoryKnowledge = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledge/saveTheoryKnowledge",
    data
  })
}
export const getById = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledge/getById",
    data
  })
}
export const getByIdAndToken = (data) => {
  return axios({
    method: "post",
    url: "/api/theoryKnowledge/getByIdAndToken",
    data
  })
}

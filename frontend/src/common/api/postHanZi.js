import axios from '../http/axios.js'

export const hanziAdd = (data) => {
  return axios({
    method: "post",
    url: "/api/postEnteringExercise/add",
    data
  })
};
export const listPage = (data) => {
  return axios({
    method: "post",
    url: "/api/postEnteringExercise/listPage",
    data
  })
};
export const getById = (data) => {
  return axios({
    method: "post",
    url: "/api/postEnteringExercise/getById",
    data
  })
};
export const begin = (data) => {
  return axios({
    method: "post",
    url: "/api/postEnteringExercise/begin",
    data
  })
};
export const hanziFinish = (data) => {
  return axios({
    method: "post",
    url: "/api/postEnteringExercise/finish",
    data
  })
};
export const addPostArticle = (data) => {
  return axios({
    method: "post",
    url: "/api/postEnteringExerciseWordStock/add",
    data
  })
};
export const getArticleList = (data) => {
  return axios({
    method: "post",
    url: "/api/postEnteringExerciseWordStock/listPage",
    data
  })
};
export const deleteArticleByID = (data) => {
  return axios({
    method: "post",
    url: "/api/postEnteringExerciseWordStock/delete",
    data
  })
};
export const deleteList = (data) => {
  return axios({
    method: "get",
    url: `/api/postEnteringExercise/delete?trainId=${data}`,
  })
};
import axios from '../http/axios.js'

export const addTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTelexPat/add",
    data
  })
}

export const findDatagramList = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTelexPat/findAll",
    data
  })
}
export const getDatagramDetail = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTelexPat/detail",
    data
  })
}
export const getDatagramStatistics = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTelexPat/statistics",
    data
  })
}
export const updateTrainStatus = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTelexPat/updateTrainStatus",
    data
  })
}
export const getDatagramZuXunPageNumber = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTelexPat/getPage",
    data
  })
}
export const uploadDatagramResult = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTelexPat/uploadResult",
    data
  })
}
export const finishDatagramZuXun = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTelexPat/finish",
    data
  })
}
export const getPatValue = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTelexPat/getPatValue",
    data
  })
}

export const endPatDetail = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTelexPat/patDetail",
    data
  })
}

export const startTrainUser = (data) => {
  return axios({
    method: "get",
    url: `/api/generalTelexPat/startTrain?trainId=${data}`,
  })
}
export const deleteTrain = (data) => {
  return axios({
    method: "get",
    url: "/api/generalTelexPat/delete",
    data
  })
}
import axios from '../http/axios.js'

export const findAll = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTickerPatTrain/findAll",
    data,
  })
}
export const addTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTickerPatTrain/add",
    data,
  })
}
export const getHandKeyZuXunDetails = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTickerPatTrain/detail",
    data
  })
}
export const resetHandKeyZuXunStatistics = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTickerPatTrain/statistics",
    data
  })
}
export const updateHandKeyTrainStatus = (data) => {
  return axios({
    method: "post",
    url: "/api/socket/generalTickerPatTrain/updateTrainStatus",
    data
  })
}
export const getHandKeyZuXunPageNumber = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTickerPatTrain/findPage",
    data
  })
}
export const saveHandKeyZuXunData = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTickerPatTrain/uploadResult",
    data
  })
}
export const finishHandKeyZuXunTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTickerPatTrain/finish",
    data
  })
}
export const resetHandKeyZuXunTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/generalTickerPatTrain/reset",
    data
  })
}
export const startTrainUser = (data) => {
  return axios({
    method: "get",
    url: `/api/generalTickerPatTrain/startTrain?trainId=${data}`,
  })
}
export const deleteTrain = (data) => {
  return axios({
    method: "get",
    url: "/api/generalTickerPatTrain/delete",
    data
  })
}
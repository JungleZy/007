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
export const getHandKeyZuXunPageNumber = (data, config) => { return axios({method: "post",
url: "/api/generalTickerPatTrain/findPage",
data, config}) }
export const saveHandKeyZuXunData = (data, config) => { return axios({method: "post",
url: "/api/generalTickerPatTrain/uploadResult",
data, config}) }
export const finishHandKeyZuXunTrain = (data, config) => { return axios({method: "post",
url: "/api/generalTickerPatTrain/finish",
data, config}) }
export const resetHandKeyZuXunTrain = (data, config) => { return axios({method: "post",
url: "/api/generalTickerPatTrain/reset",
data, config}) }
export const startTrainUser = ({trainId, attempt}, config) => {
  return axios({method: 'get', url: '/api/generalTickerPatTrain/startTrain', data: {trainId, attempt}, config})
}
export const deleteTrain = (data) => {
  return axios({
    method: "get",
    url: "/api/generalTickerPatTrain/delete",
    data
  })
}
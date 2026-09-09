import axios from '../http/axios.js'

export const getElectronKeyZuXunList = (data) => {
  return axios({
    method: "post",
    url: "/api/generalKeyPat/findAll",
    data
  })
}
export const saveElectronKeyZuXunTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/generalKeyPat/add",
    data
  })
}
export const finishElectronKeyZuXun = (data) => {
  return axios({
    method: "post",
    url: "/api/generalKeyPat/finish",
    data
  })
}
export const getElectronKeyZuXunPageNumber = (data) => {
  return axios({
    method: "post",
    url: "/api/generalKeyPat/getPage",
    data
  })
}
export const uploadElectronKeyZuXunPatResult = (data) => {
  return axios({
    method: "post",
    url: "/api/generalKeyPat/uploadResult",
    data
  })
}
export const resetElectronKeyZuXunTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/generalKeyPat/reset",
    data
  })
}
export const updateElectronKeyPatTrainDetails = (data) => {
  return axios({
    method: "post",
    url: "/api/generalKeyPat/patDetail",
    data
  })
}
export const getElectronKeyZuXunDetails = (data) => {
  return axios({
    method: "post",
    url: "/api/generalKeyPat/detail",
    data
  })
}
export const resetElectronKeyZuXunStatistics = (data) => {
  return axios({
    method: "post",
    url: "/api/generalKeyPat/statistics",
    data
  })
}
export const updateElectronKeyTrainStatus = (data) => {
  return axios({
    method: "post",
    url: "/api/generalKeyPat/updateTrainStatus",
    data
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
export const startTrainUser = (data) => {
  return axios({
    method: "get",
    url: `/api/generalKeyPat/startTrain?trainId=${data}`,
    data
  })
}
export const deleteTrain = (data) => {
  return axios({
    method: "get",
    url: `/api/generalKeyPat/delete`,
    data
  })
}
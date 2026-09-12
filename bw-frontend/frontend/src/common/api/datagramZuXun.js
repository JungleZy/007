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

// 学员开始拍发：attempt 是服务端下发的训练轮次，缺它服务端一律拒绝（轮次栅栏）
export const startTrainUser = (data) => {
  return axios({
    method: "get",
    url: `/api/generalTelexPat/startTrain?trainId=${data.trainId}&attempt=${data.attempt}`,
  })
}
// 服务端用 @RestQuery 读 trainId：GET 带 body 在浏览器里根本发不出去，必须放 query
export const deleteTrain = (data) => {
  return axios({
    method: "get",
    url: `/api/generalTelexPat/delete?trainId=${data.trainId}`,
  })
}
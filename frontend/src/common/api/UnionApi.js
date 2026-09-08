import axios from '../http/axios.js'

export const addDisturbCodeTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/routerRoomContent/addRoomAndContent",
    data
  })
}

export const getDisturbCodeAllTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/routerRoomContent/findAlls",
    data
  })
}

export const getDisturbCodeTrainData = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/routerRoomContent/findById",
    data
  })
}

export const reportIntoTrainRoom = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/routerRoomContent/addStudent",
    data
  })
}

export const getDisturbCodeTrainUserList = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/routerRoomContent/findTrainUser",
    data
  })
}

export const editDisturbTrainRoomStatus = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/routerRoomContent/editStatus",
    data
  })
}

export const uploadUnionTrainResult = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/routerRoomContent/uploadResult",
    data
  })
}

export const updateTrainRoomDispose = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/routerRoomContent/saveSetting",
    data
  })
}

export const apiSimulationRouterFindPage = (data) => {
  return axios({
    method: "get",
    url: '/api/simulation/router/findPage',
    data
  })
}

export const findUserPageBaoWenInfo = (data) => {
  return axios({
    method: "get",
    url: "/api/simulation/router/findPage",
    data
  })
}

export const deleteTrain = (data) => {
  return axios({
    method: "get",
    url: "/api/simulation/routerRoomContent/delete",
    data
  })
}

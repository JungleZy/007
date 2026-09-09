import { log } from '@antv/g2plot/lib/utils/invariant.js'
import axios from '../http/axios.js'

export const userLogin = (data) => {
  return axios({
    method: "post",
    url: "/api/user/login",
    data,
  })
}
export const addSignin = (data) => {
  return axios({
    method: "post",
    url: "/api/user/signin",
    data,
  })
}
export const userSignIn = (data) => {
  return axios({
    method: "post",
    url: "/api/user/signin",
    data
  })
}
export const addUser = (data) => {
  return axios({
    method: "post",
    url: "/api/user/addUser",
    data
  })
}
export const userLoginOut = () => {
  return axios({
    method: "post",
    url: "/api/user/userOut",
  })
}
export const getUserInfo = () => {
  return axios({
    method: "post",
    url: "/api/user/getUsersByToken"
  })
}
export const getUserById = (data) => {
  return axios({
    method: "post",
    url: "/api/user/getUserById",
    data
  })
}
export const getUserAndRoleById = (data) => {
  return axios({
    method: "post",
    url: "/api/user/getUserAndRoleById",
    data
  })
}
export const verifyPassword = (data) => {
  return axios({
    method: "post",
    url: "/api/user/verifyPassword",
    data
  })
}
export const getUserAll = () => {
  return axios({
    method: "post",
    url: "/api/user/getUserDirectory"
  })
}
export const getAllStudent = () => {
  return axios({
    method: "post",
    url: "/api/user/findAllStu"
  })
}
export const getAllTeacher = () => {
  return axios({
    method: "post",
    url: "/api/user/findAllTeacher"
  })
}
export const getUserInfoAllByStatusDesc = () => {
  return axios({
    method: "post",
    url: "/api/user/getUserInfoAllByStatusDesc"
  })
}
export const changePassword = (data) => {
  return axios({
    method: "post",
    url: "/api/user/changePassword",
    data
  })
}
export const importUser = (data) => {
  return axios({
    method: "post",
    url: "/api/user/importUser",
    data
  })
}
export const apiSimulationRouterAddRoom = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/router/addRoom",
    data
  })
}

export const apiSimulationRouterFindRoom = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/router/findRoom",
    data
  })
}

export const getRoomUserList = (roomId) => {
  return axios({
    method: "get",
    url: `/api/simulation/router/getRoomUserList/${roomId}`,
  })
}

export const apiSimulationRouterRoomDetail = (data) => {
  return axios({
    method: "get",
    url: '/api/simulation/router/getRoomDetail',
    data
  })
}

export const apiSimulationRouterRoomChannels = (data) => {
  return axios({
    method: "get",
    url: '/api/simulation/router/getRoomChannels',
    data
  })
}

export const apiSimulationRouterChangeChannel = (data) => {
  return axios({
    method: "post",
    url: '/api/simulation/router/changeChannel',
    data
  })
}

export const apiSimulationRouterSendFinish = (data) => {
  return axios({
    method: "post",
    url: '/api/simulation/router/sendFinish',
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

export const deleteTrain = (data) => {
  return axios({
    method: "get",
    url: '/api/simulation/router/delete',
    data
  })
}


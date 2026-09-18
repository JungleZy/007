import axios from '../http/axios.js'

export const userLogin = (data) => {
  return axios({
    method: "post",
    url: "/api/user/login",
    data,
    config: {skipErrorToast: true}
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
export const userLoginOut = () => {
  return axios({
    method: "post",
    url: "/api/user/userOut",
    config: {timeout: 5000, skipErrorToast: true}
  })
}
export const getUserInfo = () => {
  return axios({
    method: "post",
    url: "/api/user/getUsersByToken"
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
export const changePassword = (data) => {
  return axios({
    method: "post",
    url: "/api/user/changePassword",
    data,
    config: {skipErrorToast: true}
  })
}
// 有意保留，当前无前端调用点：luckysheet 版员工在线导入已于 f34c1c5 整链下线，
// 但后端 POST /api/user/importUser 按决策保留（它挂着两条活的测试契约：
// PasswordMigrationTest 断言批量导入写入新密码哈希、AdminAuthorizationTest 断言非管理员得 207），
// 日后若重做导入 UI（xlsx 已是在用依赖）可直接复用。
// 死代码扫除请勿删除本导出 —— 单删前端会把后端端点变成孤儿（AGENTS.md 红线 5）。
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


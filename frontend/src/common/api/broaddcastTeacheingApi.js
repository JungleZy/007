import axios from '../http/axios.js'

export const getAllStudent = () => {
  return axios({
    method: "post",
    url: "/api/user/getAllUser"
  })
}
export const addRoom = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/report/addRoom",
    data
  })
}
export const addRoomZuXun = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/recept/addRoom",
    data
  })
}
export const findRoom = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/report/findRoom",
    data
  })
}
export const findRoomZuXun = (data) => {
  return axios({
    method: "post",
    url: "/api/simulation/recept/findRoom",
    data
  })
}
export const getRoomDetail = (data) => {
  return axios({
    method: "get",
    url: "/api/simulation/report/getRoomDetail",
    data
  })
}
export const deleteTrain = (data) => {
  return axios({
    method: "get",
    url: "/api/simulation/recept/delete",
    data
  })
}


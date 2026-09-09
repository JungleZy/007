import axios from '../http/axios.js'

export const getRoleAll = (data) => {
  return axios({
    method: "post",
    url: "/api/role/getRoleAll",
  })
}
export const getRoleById = (data) => {
  return axios({
    method: "get",
    url: "/api/role/getRoleById",
    data
  })
}
export const getRoleMenusInfo = (data) => {
  return axios({
    method: "get",
    url: "/api/role/getRoleMenusInfo",
    data
  })
}
export const addRole = (data) => {
  return axios({
    method: "post",
    url: "/api/role/addRole",
    data
  })
}
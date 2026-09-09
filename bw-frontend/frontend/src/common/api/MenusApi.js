import axios from '../http/axios.js'

export const getMenusAll = (data) => {
  return axios({
    method: "get",
    url: "/api/menus/getMenusAll",
  })
}
export const getMenuById = (data) => {
  return axios({
    method: "get",
    url: "/api/menus/getMenuById",
    data
  })
}
export const addMenu = (data) => {
  return axios({
    method: "post",
    url: "/api/menus/addMenu",
    data
  })
}

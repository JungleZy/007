import axios from '../http/axios.js'

export const getAllUser = data => {
  return axios({
    method: 'post',
    url: '/api/user/getAllUser',
    data
  })
}
export const getUserAndRoleById = data => {
  return axios({
    method: 'post',
    url: '/api/user/getUserAndRoleById',
    data
  })
}

export const getRoleAll = data => {
  return axios({
    method: 'post',
    url: '/api/role/getRoleAll',
    data
  })
}
export const addUserRole = data => {
  return axios({
    method: 'post',
    url: '/api/user/addUserRole',
    data
  })
}
export const saveUser = data => {
  return axios({
    method: 'post',
    url: '/api/user/saveUser',
    data
  })
}
export const resetPassword = (data) => {
  return axios({
    method: "get",
    url: "/api/user/resetPassword",
    data
  })
}

export const deleteUser = (data) => {
  return axios({
    method: "get",
    url: "/api/user/delete",
    data
  })
}
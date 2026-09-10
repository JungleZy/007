import axios from '../http/axios.js'

export const getEquipmentAll = (data) => {
  return axios({
    method: "post",
    url: "/api/equipmentDevice/listPage",
  })
}
export const deleteEquipmentById = (data) => {
  return axios({
    method: "post",
    url: "/api/equipmentDevice/delete",
    data
  })
}
export const addEquipments = (data) => {
  return axios({
    method: "post",
    url: "/api/equipmentDevice/add",
    data
  })
}
export const editEquipments = (data) => {
  return axios({
    method: "post",
    url: "/api/equipmentDevice/update",
    data
  })
}
export const addTrain = (data) => {
  return axios({
    method: "post",
    url: "/api/equipmentTrain/add",
    data
  })
}
export const getDetails = (data) => {
  return axios({
    method: "post",
    url: "/api/equipmentTrain/detail",
    data
  })
}
export const listPage = (data) => {
  return axios({
    method: "post",
    url: "/api/equipmentTrain/listPage",
    data
  })
}
export const generalGroupNetRule = (data) => {
  return axios({
    method: "post",
    url: "/api/generalGroupNetRule/save",
    data
  })
}
export const generalGroupNetRuleFindAll = () => {
  return axios({
    method: "post",
    url: "/api/generalGroupNetRule/findAll",
  })
}
export const generalGroupNetRuleDeleteById = (data) => {
  return axios({
    method: "post",
    url: "/api/generalGroupNetRule/deleteById",
    data
  })
}